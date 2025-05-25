package com.hathoute.kubernetes.operator.openhands.crd;

import lombok.Data;

@Data
public class LLMSpec {
  private String modelName;
  private String apiKey;
  private String baseUrl;
}
