package com.hathoute.kubernetes.operator.openhands.config;

import java.util.List;

public class OpenHandsConfig {

  private OpenHandsConfig() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static final String OPENHANDS_RUNTIME_IMAGE = "docker.all-hands.dev/all-hands-ai/runtime:0.39-nikolaik";
  public static final String OPENHANDS_CONTAINER_NAME = "openhands";

  public static final String OPENHANDS_MODEL_NAME_ENV_VAR = "LLM_MODEL";
  public static final String OPENHANDS_MODEL_APIKEY_ENV_VAR = "LLM_API_KEY";
  public static final String OPENHANDS_MODEL_BASEURL_ENV_VAR = "LLM_BASE_URL";
  public static final String OPENHANDS_PROMPT_ENV_VAR = "OPENHANDS_PROMPT";
  public static final List<String> OPENHANDS_CONTAINER_ARGS = List.of("poetry", "run", "python",
      "-m", "openhands.core.main", "-t", "$%s".formatted(OPENHANDS_PROMPT_ENV_VAR));

}
