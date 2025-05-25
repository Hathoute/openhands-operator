package com.hathoute.kubernetes.operator.openhands.crd;

import io.fabric8.kubernetes.api.model.PodSpec;
import lombok.Data;

@Data
public class LLMTaskSpec {
  private String llmName;

  private String preScript;
  private String postScript;
  private String prompt;

  private PodSpec podSpec;
}
