package com.hathoute.kubernetes.operator.openhands;

public final class TestFixtures {

  public static final String WORKING_NAMESPACE = "llm-tasks-ns";
  public static final String LLM_TASK_NAME = "fix-issue-1234";
  public static final String LLM_TASK_APIVERSION = "com.hathoute.kubernetes/v1alpha1";
  public static final String LLM_TASK_KIND = "LLMTask";
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
        prompt: "This is the prompt for the task"
        llm:
          modelName: model-group/model-name-v1
          apiKey: very-secret-api-key
      """;
}
