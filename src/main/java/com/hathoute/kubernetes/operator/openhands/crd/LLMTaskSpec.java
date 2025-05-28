package com.hathoute.kubernetes.operator.openhands.crd;

import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import java.util.List;
import lombok.Data;

@Data
public class LLMTaskSpec {
  private String llmName;

  private String preScript;
  private String postScript;
  private String prompt;

  private LLMPod pod;

  @Data
  public static class LLMPod {
    private LLMPodServiceAccount serviceAccount;
    private PodSpec spec;
  }

  @Data
  public static class LLMPodServiceAccount {
    private boolean create;
    private List<PolicyRule> rules;
  }
}
