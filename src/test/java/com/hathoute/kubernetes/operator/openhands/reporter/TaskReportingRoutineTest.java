package com.hathoute.kubernetes.operator.openhands.reporter;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import com.hathoute.kubernetes.operator.openhands.reporter.Health.Status;
import com.hathoute.kubernetes.operator.openhands.reporter.TaskReportingRoutine.State;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.inbound.SimpleInboundEventSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hathoute.kubernetes.operator.openhands.config.OpenHandsConfig.OPENHANDS_RUNTIME_HEALTH_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskReportingRoutineTest {

  private static final ResourceID RESOURCE_ID = new ResourceID("llmtask", "my-namespace");

  @Mock
  private TaskReporterClient client;
  @Mock
  private SimpleInboundEventSource<LLMTaskResource> eventSource;
  private Instant now;
  private TaskReportingRoutine testee;

  @BeforeEach
  void setup() {
    now = Instant.now();
    testee = new TaskReportingRoutine(RESOURCE_ID, client, eventSource, () -> now);
  }

  @AfterEach
  void teardown() {
    verifyNoMoreInteractions(client, eventSource);
  }

  @Test
  void should_set_initial_state_to_waiting() {
    assertThat(testee.getCurrentState()).isEqualTo(State.WAITING);
    verify(eventSource).propagateEvent(RESOURCE_ID);
  }

  @Test
  void should_stay_in_waiting_when_health_is_unknown() {
    when(client.health()).thenReturn(Status.UNKNOWN);
    testee.run();
    assertThat(testee.getCurrentState()).isEqualTo(State.WAITING);
    // initial state propagate event
    verify(eventSource, times(1)).propagateEvent(RESOURCE_ID);
  }

  @Test
  void should_switch_to_stopped_when_health_is_unknown() {
    // Given
    when(client.health()).thenReturn(Status.UNKNOWN);
    testee.run();

    // When
    now = now.plus(OPENHANDS_RUNTIME_HEALTH_TIMEOUT.plus(1, ChronoUnit.SECONDS));
    testee.run();
    assertThat(testee.getCurrentState()).isEqualTo(State.STOPPED);
    // initial state propagate event + stopped event
    verify(eventSource, times(2)).propagateEvent(RESOURCE_ID);
  }

  @Test
  void should_switch_to_running_when_health_is_ok() {
    when(client.health()).thenReturn(Status.RUNNING);
    testee.run();
    assertThat(testee.getCurrentState()).isEqualTo(State.RUNNING);
    // initial state propagate event + running event
    verify(eventSource, times(2)).propagateEvent(RESOURCE_ID);
  }
}