package com.hathoute.kubernetes.operator.openhands.reporter;

import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import com.hathoute.kubernetes.operator.openhands.reporter.Health.Status;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.inbound.SimpleInboundEventSource;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.hathoute.kubernetes.operator.openhands.config.OpenHandsConfig.OPENHANDS_RUNTIME_HEALTH_TIMEOUT;

public class TaskReportingRoutine implements Runnable {
  private static final Logger LOGGER = LoggerFactory.getLogger(TaskReportingRoutine.class);

  public enum State {
    WAITING, RUNNING, STOPPING, STOPPED
  }

  private final ResourceID resourceId;
  private final TaskReporterClient client;
  private final SimpleInboundEventSource<LLMTaskResource> eventSource;
  private final Supplier<Instant> timeService;

  @Getter
  private State currentState;
  @Getter
  private List<Event> events = List.of();
  private Instant startTime;

  TaskReportingRoutine(final ResourceID resourceId, final TaskReporterClient client,
      final SimpleInboundEventSource<LLMTaskResource> eventSource,
      final Supplier<Instant> timeService) {
    this.resourceId = resourceId;
    this.client = client;
    this.eventSource = eventSource;
    this.timeService = timeService;

    setState(State.WAITING);
  }

  @Override
  public void run() {
    if (startTime == null) {
      startTime = timeService.get();
    }

    switch (currentState) {
      case WAITING:
        processWaitingState();
        break;
      case RUNNING:
        processRunningState();
        break;
      case STOPPING:
        processStoppingState();
        break;
      case STOPPED:
        break;
    }
  }

  private void processWaitingState() {
    if (startTime.plus(OPENHANDS_RUNTIME_HEALTH_TIMEOUT).isBefore(timeService.get())) {
      // We timed out, set the state to STOPPED (shutdown() requests will also fail)
      setState(State.STOPPED);
      return;
    }

    final var nextState = switch (client.health()) {
      case RUNNING -> State.RUNNING;
      case STOPPED -> State.STOPPING;
      case UNKNOWN -> {
        LOGGER.debug("Task '{}' is not yet ready, will stay in waiting state", resourceId);
        yield State.WAITING;
      }
    };

    setState(nextState);
  }

  private void processRunningState() {
    if (client.health() != Status.RUNNING) {
      // Health failed, stop retrieving the events
      setState(State.STOPPING);
    }

    final List<Map<String, Object>> fetchedEvents;
    try {
      fetchedEvents = client.getEvents();
    } catch (final IOException e) {
      LOGGER.warn("Failed to get events for task {}, will retry in the next attempt", resourceId,
          e);
      return;
    }

    LOGGER.debug("Retrieved {} events for task {}", events.size(), resourceId);
    final var previousEvents = events;
    this.events = fetchedEvents.stream().map(TaskReportingRoutine::parseEvent).toList();
    if (previousEvents.size() < events.size()) {
      notifyNewEvents();
    }
  }

  private void processStoppingState() {
    if (!client.shutdown()) {
      LOGGER.warn("Could not shutdown the reporting server for task {}", resourceId);
      return;
    }

    setState(State.STOPPED);
  }

  private void notifyNewEvents() {
    eventSource.propagateEvent(resourceId);
  }

  private void setState(final State state) {
    if (currentState == state) {
      return;
    }

    LOGGER.debug("State changed from '{}' to '{}' for task '{}'", currentState, state, resourceId);
    currentState = state;
    notifyNewEvents();
  }

  private static Event parseEvent(final Map<String, Object> eventMap) {
    final var id = (Integer) eventMap.get("id");
    final var isoTimestamp = (String) eventMap.get("timestamp");
    final var source = (String) eventMap.get("source");
    final var message = (String) eventMap.get("message");
    return new Event(id, Instant.parse(isoTimestamp + "Z"), source, message, eventMap);
  }
}
