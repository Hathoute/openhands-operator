package com.hathoute.kubernetes.operator.openhands.resource;

import com.hathoute.kubernetes.operator.openhands.crd.LLMSpec;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskSpec;
import com.hathoute.kubernetes.operator.openhands.util.KubernetesUtil;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import static com.hathoute.kubernetes.operator.openhands.config.OpenHandsConfig.OPENHANDS_ADDITIONAL_ENV_VARS;
import static com.hathoute.kubernetes.operator.openhands.config.OpenHandsConfig.OPENHANDS_CONTAINER_COMMAND;
import static com.hathoute.kubernetes.operator.openhands.config.OpenHandsConfig.OPENHANDS_CONTAINER_NAME;
import static com.hathoute.kubernetes.operator.openhands.config.OpenHandsConfig.OPENHANDS_MODEL_APIKEY_ENV_VAR;
import static com.hathoute.kubernetes.operator.openhands.config.OpenHandsConfig.OPENHANDS_MODEL_BASEURL_ENV_VAR;
import static com.hathoute.kubernetes.operator.openhands.config.OpenHandsConfig.OPENHANDS_MODEL_NAME_ENV_VAR;
import static com.hathoute.kubernetes.operator.openhands.config.OpenHandsConfig.OPENHANDS_POSTSCRIPT_COMMAND;
import static com.hathoute.kubernetes.operator.openhands.config.OpenHandsConfig.OPENHANDS_PRESCRIPT_COMMAND;
import static com.hathoute.kubernetes.operator.openhands.config.OpenHandsConfig.OPENHANDS_PROMPT_ENV_VAR;
import static com.hathoute.kubernetes.operator.openhands.config.OpenHandsConfig.OPENHANDS_RUNTIME_IMAGE;
import static com.hathoute.kubernetes.operator.openhands.reconciler.LLMTaskReconciler.SELECTOR;
import static java.util.Objects.requireNonNullElseGet;

@KubernetesDependent(informer = @Informer(labelSelector = SELECTOR))
public class LLMTaskPodResource extends CRUDKubernetesDependentResource<Pod, LLMTaskResource> {
  private static final Logger LOGGER = LoggerFactory.getLogger(LLMTaskPodResource.class);

  private static final String COMPONENT = "llm-task";

  public LLMTaskPodResource() {
    super(Pod.class);
  }

  @Override
  protected Pod desired(final LLMTaskResource primary, final Context<LLMTaskResource> context) {
    final var model = KubernetesUtil.getModelForTask(context.getClient(), primary);
    if (model == null) {
      final var modelName = primary.getSpec().getLlmName();
      LOGGER.warn("Could not find model {} for task {}", modelName,
          primary.getMetadata().getName());
    }
    final var modelSpec = model != null ? model.getSpec() : null;

    final var meta = fromPrimary(primary);
    final var spec = buildPodSpec(primary.getSpec(), modelSpec);

    return new PodBuilder().withMetadata(meta).withSpec(spec).build();
  }

  private static ObjectMeta fromPrimary(final LLMTaskResource primary) {
    final var meta = primary.getMetadata();
    return new ObjectMetaBuilder().withNamespace(meta.getNamespace())
                                  .withName(podName(meta.getName()))
                                  .withLabels(
                                      Map.of("app.kubernetes.io/managed-by", "openhands-operator"))
                                  .build();
  }

  private static PodSpec buildPodSpec(final LLMTaskSpec taskSpec,
      final @Nullable LLMSpec modelSpec) {
    final var podTemplate = requireNonNullElseGet(taskSpec.getPodSpec(), PodSpec::new);
    final var podBuilder = podTemplate.edit();

    final var containerOpt = podTemplate.getContainers()
                                        .stream()
                                        .filter(c -> OPENHANDS_CONTAINER_NAME.equals(c.getName()))
                                        .findFirst();
    containerOpt.ifPresent(podBuilder::removeFromContainers);

    final var containerBuilder = containerOpt.map(Container::edit).orElseGet(ContainerBuilder::new);
    buildContainer(taskSpec, modelSpec, containerBuilder);
    podBuilder.addToContainers(0, containerBuilder.build());
    podBuilder.withRestartPolicy("Never");

    return podBuilder.build();
  }

  private static void buildContainer(final LLMTaskSpec taskSpec, final @Nullable LLMSpec modelSpec,
      final ContainerBuilder containerBuilder) {
    containerBuilder.withName(OPENHANDS_CONTAINER_NAME);

    if (modelSpec != null) {
      addToEnv(containerBuilder, OPENHANDS_MODEL_NAME_ENV_VAR, modelSpec.getModelName());
      addToEnv(containerBuilder, OPENHANDS_MODEL_APIKEY_ENV_VAR, modelSpec.getApiKey());
      addToEnv(containerBuilder, OPENHANDS_MODEL_BASEURL_ENV_VAR, modelSpec.getBaseUrl());
    }

    addToEnv(containerBuilder, OPENHANDS_PROMPT_ENV_VAR, taskSpec.getPrompt());
    OPENHANDS_ADDITIONAL_ENV_VARS.forEach((k, v) -> addToEnv(containerBuilder, k, v));

    if (!containerBuilder.hasImage()) {
      LOGGER.debug("Setting container image to {}", OPENHANDS_RUNTIME_IMAGE);
      containerBuilder.withImage(OPENHANDS_RUNTIME_IMAGE);
    }

    if (!containerBuilder.hasArgs()) {
      final var command = buildCommand(taskSpec);
      LOGGER.debug("Setting command to {}", command);
      containerBuilder.withArgs("bash", "-c", command);
    }
  }

  private static void addToEnv(final ContainerBuilder containerBuilder, final String name,
      final String value) {
    if (containerBuilder.hasMatchingEnv(e -> name.equals(e.getName()))) {
      // Do not log value since it might be a secret (apiKey)
      LOGGER.debug("EnvVar {} overriden by user.", name);
      return;
    }

    LOGGER.debug("Setting EnvVar {}", name);
    containerBuilder.addToEnv(new EnvVarBuilder().withName(name).withValue(value).build());
  }

  private static String buildCommand(final LLMTaskSpec taskSpec) {
    final var builder = new StringBuilder();
    if (!StringUtils.isEmpty(taskSpec.getPreScript())) {
      builder.append(OPENHANDS_PRESCRIPT_COMMAND).append("\n");
      builder.append(taskSpec.getPreScript()).append("\n");
    }

    builder.append(OPENHANDS_CONTAINER_COMMAND).append("\n");

    if (!StringUtils.isEmpty(taskSpec.getPostScript())) {
      builder.append(OPENHANDS_POSTSCRIPT_COMMAND).append("\n");
      builder.append(taskSpec.getPostScript()).append("\n");
    }

    return builder.toString();
  }

  private static String podName(final String taskName) {
    return "%s-%s".formatted(COMPONENT, taskName);
  }
}
