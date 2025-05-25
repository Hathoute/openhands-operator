package com.hathoute.kubernetes.operator.openhands.crd;

import lombok.Data;

@Data
public class LLMTaskStatus {

  private State state;
  private String errorReason;

  public enum State {
    QUEUED, RUNNING, SUCCEEDED, FAILED
  }
}
