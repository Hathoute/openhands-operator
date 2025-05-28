package com.hathoute.kubernetes.operator.openhands.resource;

import com.hathoute.kubernetes.operator.openhands.crd.LLMSpec;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskSpec;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskSpec.LLMPod;
import com.hathoute.kubernetes.operator.openhands.reconciler.LLMTaskWithServiceAccountCondition;
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
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import static com.hathoute.kubernetes.operator.openhands.reconciler.LLMTaskReconciler.SELECTOR_LABEL;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;

@KubernetesDependent(informer = @Informer(labelSelector = SELECTOR))
public class LLMTaskPodResource extends CRUDKubernetesDependentResource<Pod, LLMTaskResource> {
  private static final Logger LOGGER = LoggerFactory.getLogger(LLMTaskPodResource.class);

  private static final String COMPONENT = "llm-task";
  private static final LLMTaskWithServiceAccountCondition<Pod> SVC_ACC_VERIFIER = new LLMTaskWithServiceAccountCondition<>();

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
    final var serviceAccountOpt = serviceAccountName(primary, context);
    final var spec = buildPodSpec(primary.getSpec(), modelSpec, serviceAccountOpt);

    return new PodBuilder().withMetadata(meta).withSpec(spec).build();
  }

  private static ObjectMeta fromPrimary(final LLMTaskResource primary) {
    final var meta = primary.getMetadata();
    return new ObjectMetaBuilder().withNamespace(meta.getNamespace())
                                  .withName(podName(meta.getName()))
                                  .withLabels(SELECTOR_LABEL)
                                  .build();
  }

  private static PodSpec buildPodSpec(final LLMTaskSpec taskSpec, final LLMSpec modelSpec,
      final Optional<String> serviceAccountName) {
    final var podTemplate = getPodSpec(taskSpec);
    final var podBuilder = podTemplate.edit();

    serviceAccountName.ifPresent(podBuilder::withServiceAccountName);

    final var containerOpt = podTemplate.getContainers()
                                        .stream()
                                        .filter(c -> OPENHANDS_CONTAINER_NAME.equals(c.getName()))
                                        .findFirst();
    containerOpt.ifPresent(podBuilder::removeFromContainers);

    final var containerBuilder = containerOpt.map(Container::edit).orElseGet(ContainerBuilder::new);
    buildContainer(taskSpec, modelSpec, containerBuilder);
    // Guarantee that the first container in the pod spec is always openhands
    podBuilder.addToContainers(0, containerBuilder.build());
    podBuilder.withRestartPolicy("Never");

    return podBuilder.build();
  }

  private static void buildContainer(final LLMTaskSpec taskSpec, final LLMSpec modelSpec,
      final ContainerBuilder containerBuilder) {
    containerBuilder.withName(OPENHANDS_CONTAINER_NAME);

    addToEnv(containerBuilder, OPENHANDS_MODEL_NAME_ENV_VAR, modelSpec.getModelName());
    addToEnv(containerBuilder, OPENHANDS_MODEL_APIKEY_ENV_VAR, modelSpec.getApiKey());
    addToEnv(containerBuilder, OPENHANDS_MODEL_BASEURL_ENV_VAR, modelSpec.getBaseUrl());
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

  private Optional<String> serviceAccountName(final LLMTaskResource primary,
      final Context<LLMTaskResource> context) {
    // Even if this dependent resource runs before LLMTaskServiceAccountResource, the
    // service account will eventually be created.
    // Cannot set LLMTaskServiceAccountResource as a dependency of LLMTaskPodResource
    // because doing so will block the creation of Pod when service account is not needed.
    if (SVC_ACC_VERIFIER.isMet(this, primary, context)) {
      final var primaryName = primary.getMetadata().getName();
      return Optional.of(LLMTaskServiceAccountResource.resourceName(primaryName));
    }
    return empty();
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

  private static PodSpec getPodSpec(final LLMTaskSpec taskSpec) {
    return ofNullable(taskSpec.getPod()).map(LLMPod::getSpec).orElseGet(PodSpec::new);
  }

  private static String podName(final String taskName) {
    return "%s-%s".formatted(COMPONENT, taskName);
  }
}
