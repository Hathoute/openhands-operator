package com.hathoute.kubernetes.operator.openhands.util;

import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus.State;
import com.hathoute.kubernetes.operator.openhands.reporter.Event;
import java.util.List;
import java.util.Map;

import static java.lang.Boolean.parseBoolean;

public final class OpenHandsUtil {

  private OpenHandsUtil() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static LLMTaskStatus statusFromEvents(final List<Event> events) {
    if (events.size() < 2) {
      return errorStatus("Could not extract events from runtime");
    }

    final var lastEvent = events.getLast();
    final var agentState = extractAgentStateOrErrorReason(lastEvent);
    if (agentState == null) {
      return errorStatus("Could not extract agent state from events");
    }

    if (agentState.isRight()) {
      return status(State.FAILED, agentState.right());
    }

    final var finishAction = events.get(events.size() - 2);
    final var finalThought = extractFinalThought(finishAction);
    if (finalThought == null) {
      return errorStatus("Could not extract final thought from events");
    }

    if (finalThought.isRight()) {
      return status(State.FAILED, finalThought.right());
    } else {
      return status(State.SUCCEEDED, finalThought.left());
    }
  }

  private static LLMTaskStatus status(final State state, final String reason) {
    final var status = new LLMTaskStatus();
    status.setState(state);
    status.setMessage(reason);
    return status;
  }

  private static LLMTaskStatus errorStatus(final String reason) {
    final var status = new LLMTaskStatus();
    status.setState(LLMTaskStatus.State.ERROR);
    status.setErrorReason(reason);
    return status;
  }

  // TODO: replace this manual parsing with jackson json type info
  private static Either<String, String> extractAgentStateOrErrorReason(final Event event) {
    if (!"environment".equals(event.source())) {
      return null;
    }

    final var raw = event.raw();
    final var observation = raw.get("observation");
    if (!"agent_state_changed".equals(observation)) {
      return null;
    }

    final var extras = (Map<String, Object>) raw.get("extras");
    final var agentState = extras.get("agent_state");
    if ("finished".equals(agentState)) {
      return Either.left((String) agentState);
    }

    final var reason = extras.get("reason");
    return Either.right((String) reason);
  }

  private static Either<String, String> extractFinalThought(final Event event) {
    if (!"agent".equals(event.source())) {
      return null;
    }

    final var raw = event.raw();
    final var action = raw.get("action");
    if (!"finish".equals(action)) {
      return null;
    }

    final var args = (Map<String, Object>) raw.get("args");
    final var taskCompleted = parseBoolean((String) args.get("task_completed"));
    final var finalThought = (String) args.get("final_thought");

    if (taskCompleted) {
      return Either.left(finalThought);
    } else {
      return Either.right(finalThought);
    }
  }

}
