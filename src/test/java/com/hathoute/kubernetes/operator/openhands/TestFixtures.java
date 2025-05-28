package com.hathoute.kubernetes.operator.openhands;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskSpec.LLMPod;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskSpec.LLMPodServiceAccount;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;

public final class TestFixtures {

  public static final ObjectMapper MAPPER = new ObjectMapper(
      new YAMLFactory()).setSerializationInclusion(Include.NON_NULL);

  public static final String WORKING_NAMESPACE = "llm-tasks-ns";
  public static final String LLM_TASK_NAME = "fix-issue-1234";
  public static final String LLM_PROMPT = "This is the prompt for the task";
  public static final String LLM_TASK_RESOURCE = """
      apiVersion: com.hathoute.kubernetes/v1alpha1
      kind: LLMTask
      metadata:
        annotations:
          kubernetes.hathoute.com/user: user1
        labels:
          app.kubernetes.io/managed-by: llm-api
        name: fix-issue-1234
      spec:
        prompt: "%s"
        llmName: "local-model"
      """.formatted(LLM_PROMPT);
  public static final String LLM_RESOURCE = """
      apiVersion: com.hathoute.kubernetes/v1alpha1
      kind: LLM
      metadata:
        annotations:
          kubernetes.hathoute.com/user: user1
        labels:
          app.kubernetes.io/managed-by: llm-api
        name: local-model
      spec:
        modelName: model-group/model-name-v1
        apiKey: very-secret-api-key
      """;

  public static String taskWithPodSpec(final PodSpec podSpec) {
    return taskResource(t -> {
      final var llmPod = new LLMPod();
      llmPod.setSpec(podSpec);
      t.getSpec().setPod(llmPod);
    });
  }

  public static String taskWithSvcAccount(final PolicyRule... rules) {
    return taskResource(t -> {
      final var svcAcc = new LLMPodServiceAccount();
      svcAcc.setCreate(true);
      svcAcc.setRules(Arrays.stream(rules).toList());
      final var llmPod = new LLMPod();
      llmPod.setServiceAccount(svcAcc);
      t.getSpec().setPod(llmPod);
    });
  }

  private static String taskResource(final Consumer<LLMTaskResource> modifier) {
    return taskResource(e -> {
      modifier.accept(e);
      return e;
    });
  }

  private static String taskResource(final Function<LLMTaskResource, LLMTaskResource> modifier) {
    try {
      final var resource = MAPPER.readValue(LLM_TASK_RESOURCE, LLMTaskResource.class);
      final var modified = modifier.apply(resource);
      return MAPPER.writeValueAsString(modified);
    } catch (final IOException e) {
      throw new IllegalStateException("Failed to serialize/deserialize LLMTaskResource", e);
    }
  }
}
