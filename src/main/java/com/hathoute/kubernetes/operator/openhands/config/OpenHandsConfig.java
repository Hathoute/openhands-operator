package com.hathoute.kubernetes.operator.openhands.config;

import java.time.Duration;
import java.util.Map;

public final class OpenHandsConfig {

  private OpenHandsConfig() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static final String OPENHANDS_RUNTIME_IMAGE = "docker.all-hands.dev/all-hands-ai/runtime:0.39-nikolaik";
  public static final String OPENHANDS_CONTAINER_NAME = "openhands";

  public static final Duration OPENHANDS_RUNTIME_HEALTH_TIMEOUT = Duration.ofSeconds(30);
  public static final int OPENHANDS_RUNTIME_REPORTER_PORT = 8080;
  public static final String OPENHANDS_RUNTIME_REPORTER_PORT_NAME = "http";

  public static final String OPENHANDS_MODEL_NAME_ENV_VAR = "LLM_MODEL";
  public static final String OPENHANDS_MODEL_APIKEY_ENV_VAR = "LLM_API_KEY";
  public static final String OPENHANDS_MODEL_BASEURL_ENV_VAR = "LLM_BASE_URL";
  public static final String OPENHANDS_PROMPT_ENV_VAR = "OPENHANDS_PROMPT";
  public static final String OPENHANDS_REPORTER_LISTEN_PORT_ENV_VAR = "REPORTER_LISTEN_PORT";

  public static final String OPENHANDS_PRESCRIPT_BEFORE_COMMAND = "IMAGE_WORKING_DIR=$(pwd) && cd /workspace";
  public static final String OPENHANDS_PRESCRIPT_AFTER_COMMAND = "cd $IMAGE_WORKING_DIR";
  public static final String OPENHANDS_CONTAINER_COMMAND = "./entrypoint.sh \"$%s\"".formatted(
      OPENHANDS_PROMPT_ENV_VAR);
  public static final String OPENHANDS_POSTSCRIPT_COMMAND = "cd /workspace";

  public static final Map<String, String> OPENHANDS_ADDITIONAL_ENV_VARS = Map.of("RUNTIME", "local",
      "SANDBOX_USER_ID", "1000", "SANDBOX_VOLUMES", "/workspace:/workspace:rw");
}
