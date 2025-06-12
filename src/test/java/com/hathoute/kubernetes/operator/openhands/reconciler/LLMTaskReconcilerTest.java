package com.hathoute.kubernetes.operator.openhands.reconciler;

import com.hathoute.kubernetes.operator.openhands.TestFixtures;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus;
import com.hathoute.kubernetes.operator.openhands.reporter.TaskReportingService;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedWorkflowAndDependentResourceContext;
import io.javaoperatorsdk.operator.processing.event.source.inbound.SimpleInboundEventSource;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus.State.FAILED;
import static com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus.State.QUEUED;
import static com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus.State.RUNNING;
import static com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus.State.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LLMTaskReconcilerTest {

  @Mock
  private PrimaryResourceValidator<LLMTaskResource> validator;
  @Mock
  private Context<LLMTaskResource> context;
  @Mock
  private ManagedWorkflowAndDependentResourceContext managedWorkflowAndDependentResourceContext;
  @Mock
  private TaskReportingService taskReportingService;
  @Mock
  private SimpleInboundEventSource<LLMTaskResource> reporterEventSource;
  private LLMTaskReconciler reconciler;

  @BeforeEach
  void setup() {
    lenient().when(context.managedWorkflowAndDependentResourceContext())
             .thenReturn(managedWorkflowAndDependentResourceContext);
    reconciler = new LLMTaskReconciler(validator, taskReportingService, reporterEventSource);
  }

  @AfterEach
  void teardown() {
    verifyNoMoreInteractions(validator, context, managedWorkflowAndDependentResourceContext,
        taskReportingService, reporterEventSource);
  }

  @Test
  void should_register_reporter_event_source() {
    final var eventSourceContext = (EventSourceContext<LLMTaskResource>) mock(
        EventSourceContext.class);
    final var eventSources = reconciler.prepareEventSources(eventSourceContext);
    assertThat(eventSources).containsExactly(reporterEventSource);
  }

  @Test
  void should_patch_status_when_validation_fails() throws Exception {
    final var input = TestFixtures.YAML_MAPPER.readValue(TestFixtures.LLM_TASK_RESOURCE,
        LLMTaskResource.class);
    final var failed = new LLMTaskResource();
    final var status = new LLMTaskStatus();
    status.setState(FAILED);
    status.setErrorReason("Validation failed");
    failed.setStatus(status);
    when(validator.validateOrErrorStatus(any(), any())).thenReturn(Optional.of(failed));

    final var result = reconciler.reconcile(input, context);

    assertStatus(result, status);
  }

  @Test
  void should_return_no_update_if_state_did_not_change() throws Exception {
    final var input = TestFixtures.YAML_MAPPER.readValue(TestFixtures.LLM_TASK_RESOURCE,
        LLMTaskResource.class);
    final var existingStatus = new LLMTaskStatus();
    existingStatus.setState(RUNNING);
    input.setStatus(existingStatus);

    final var pod = new Pod();
    final var podStatus = new PodStatus();
    podStatus.setPhase("Running");
    pod.setStatus(podStatus);

    when(validator.validateOrErrorStatus(any(), any())).thenReturn(Optional.empty());
    when(context.getSecondaryResource(Pod.class)).thenReturn(Optional.of(pod));

    final var result = reconciler.reconcile(input, context);

    assertThat(result.isNoUpdate()).isTrue();
    verify(context).getSecondaryResource(Pod.class);
    verify(context).managedWorkflowAndDependentResourceContext();
    verify(managedWorkflowAndDependentResourceContext).reconcileManagedWorkflow();
    verify(taskReportingService).addTask(input);
  }

  @Test
  void should_patch_status_when_pod_state_changes() throws Exception {
    final var input = TestFixtures.YAML_MAPPER.readValue(TestFixtures.LLM_TASK_RESOURCE,
        LLMTaskResource.class);
    final var oldStatus = new LLMTaskStatus();
    oldStatus.setState(QUEUED);
    input.setStatus(oldStatus);

    final var pod = new Pod();
    final var podStatus = new PodStatus();
    podStatus.setPhase("Succeeded");
    pod.setStatus(podStatus);

    when(validator.validateOrErrorStatus(any(), any())).thenReturn(Optional.empty());
    when(context.getSecondaryResource(Pod.class)).thenReturn(Optional.of(pod));

    final var result = reconciler.reconcile(input, context);

    verify(context).getSecondaryResource(Pod.class);
    verify(context).managedWorkflowAndDependentResourceContext();
    verify(managedWorkflowAndDependentResourceContext).reconcileManagedWorkflow();
    verify(taskReportingService).addTask(input);

    final var expectedStatus = new LLMTaskStatus();
    expectedStatus.setState(SUCCEEDED);
    assertStatus(result, expectedStatus);
  }

  @Test
  void should_return_queued_if_pod_has_no_status() throws Exception {
    final var input = TestFixtures.YAML_MAPPER.readValue(TestFixtures.LLM_TASK_RESOURCE,
        LLMTaskResource.class);
    final var pod = new Pod(); // no status set

    when(validator.validateOrErrorStatus(any(), any())).thenReturn(Optional.empty());
    when(context.getSecondaryResource(Pod.class)).thenReturn(Optional.of(pod));

    final var result = reconciler.reconcile(input, context);

    verify(context).getSecondaryResource(Pod.class);
    verify(context).managedWorkflowAndDependentResourceContext();
    verify(managedWorkflowAndDependentResourceContext).reconcileManagedWorkflow();
    verify(taskReportingService).addTask(input);

    final var expectedStatus = new LLMTaskStatus();
    expectedStatus.setState(QUEUED);
    assertStatus(result, expectedStatus);
  }

  @Test
  void cleanup_correctly_cleanup_resources() {
    final var input = new LLMTaskResource();
    final var result = reconciler.cleanup(input, context);

    verify(context).managedWorkflowAndDependentResourceContext();
    verify(managedWorkflowAndDependentResourceContext).cleanupManageWorkflow();
    verify(taskReportingService).removeTask(input);
    assertThat(result.isRemoveFinalizer()).isTrue();
  }

  private static void assertStatus(final UpdateControl<LLMTaskResource> result,
      final LLMTaskStatus status) {
    assertThat(result.isPatchStatus()).isTrue();
    assertThat(result.getResource()).isNotEmpty();
    final var actualStatus = result.getResource().get().getStatus();
    assertThat(actualStatus).usingRecursiveComparison().isEqualTo(status);
  }
}
