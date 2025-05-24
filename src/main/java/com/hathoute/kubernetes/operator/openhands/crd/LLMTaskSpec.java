package com.hathoute.kubernetes.operator.openhands.crd;

import io.fabric8.kubernetes.api.model.PodSpec;
import lombok.Data;

@Data
public class LLMTaskSpec {
  private LLMSpec llm;
  private String prompt;
  private PodSpec podSpec;

  @Data
  public static class LLMSpec {
    private String modelName;
    private String apiKey;
    private String baseUrl;
  }
}
