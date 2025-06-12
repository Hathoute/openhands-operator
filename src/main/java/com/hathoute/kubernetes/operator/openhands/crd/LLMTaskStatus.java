package com.hathoute.kubernetes.operator.openhands.crd;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class LLMTaskStatus {

  public enum State {
    QUEUED, RUNNING, SUCCEEDED, FAILED, ERROR
  }

  private State state;
  private String message;
  private String errorReason;

  @Override
  public String toString() {
    final var formattedMsg = message != null ? ", message='%s'".formatted(message) : "";
    final var formattedError =
        errorReason != null ? ", errorReason='%s'".formatted(errorReason) : "";
    return "LLMTaskStatus(state='%s'%s%s)".formatted(state, formattedMsg, formattedError);
  }
}
