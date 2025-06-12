package com.hathoute.kubernetes.operator.openhands.reporter;
import com.hathoute.kubernetes.operator.openhands.TestFixtures;
import com.hathoute.kubernetes.operator.openhands.reporter.Health.Status;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

class TaskReporterClientTest {

  private ClientAndServer mockServer;
  private TaskReporterClient testee;

  @BeforeEach
  void setup() {
    mockServer = ClientAndServer.startClientAndServer(9080);
    testee = new TaskReporterClient("http://localhost:" + mockServer.getPort());
  }

  @AfterEach
  void teardown() {
    mockServer.stop();
  }

  @Test
  void should_return_running_status_when_reporter_is_healthy() {
    mockServer.when(request().withMethod("GET").withPath("/health"))
              .respond(response().withStatusCode(200)
                                 .withBody(TestFixtures.REPORTER_HEALTH_RESPONSE_OK));

    final var actual = testee.health();

    assertThat(actual).isEqualTo(Status.RUNNING);
  }

  @Test
  void should_return_stopped_status_when_runtime_exits() {
    mockServer.when(request().withMethod("GET").withPath("/health"))
              .respond(response().withStatusCode(200)
                                 .withBody(TestFixtures.REPORTER_HEALTH_RESPONSE_EXIT));

    final var actual = testee.health();

    assertThat(actual).isEqualTo(Status.STOPPED);
  }

  @Test
  void should_return_unknown_status_on_error() {
    mockServer.when(request().withMethod("GET").withPath("/health"))
              .respond(response().withStatusCode(503).withBody("Service Unavailable"));

    final var actual = testee.health();

    assertThat(actual).isEqualTo(Status.UNKNOWN);
  }

  @Test
  void should_return_events() throws IOException {
    mockServer.when(request().withMethod("GET").withPath("/events"))
              .respond(
                  response().withStatusCode(200).withBody(TestFixtures.REPORTER_EVENTS_SUCCESS));

    final var actual = testee.getEvents();

    assertThat(actual).hasSize(7);
    assertThat(actual).map(a -> a.get("id")).containsExactly(0, 1, 2, 3, 4, 5, 6);
  }

  @Test
  void should_throw_exception_on_events_error() {
    mockServer.when(request().withMethod("GET").withPath("/events"))
              .respond(response().withStatusCode(503).withBody("Service Unavailable"));

    assertThatThrownBy(() -> testee.getEvents()).isInstanceOf(IllegalStateException.class)
                                                .hasMessageContaining("Received status code 503");
  }

  @Test
  void should_return_true_when_shutdown_is_ok() {
    mockServer.when(request().withMethod("POST").withPath("/shutdown"))
              .respond(response().withStatusCode(200));

    assertThat(testee.shutdown()).isTrue();
  }

  @Test
  void should_return_false_when_shutdown_is_nok() {
    mockServer.when(request().withMethod("POST").withPath("/shutdown"))
              .respond(response().withStatusCode(503));

    assertThat(testee.shutdown()).isFalse();
  }
}