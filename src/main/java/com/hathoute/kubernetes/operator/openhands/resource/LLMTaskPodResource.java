package com.hathoute.kubernetes.operator.openhands.resource;

import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskSpec;
import io.fabric8.kubernetes.api.model.*;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import java.util.Map;

import static com.hathoute.kubernetes.operator.openhands.config.OpenHandsConfig.*;
import static com.hathoute.kubernetes.operator.openhands.reconciler.LLMTaskReconciler.SELECTOR;
import static java.util.Objects.requireNonNullElseGet;

@KubernetesDependent(informer = @Informer(labelSelector = SELECTOR))
public class LLMTaskPodResource extends CRUDKubernetesDependentResource<Pod, LLMTaskResource> {
  private static final String COMPONENT = "llm-task";

  public LLMTaskPodResource() {
    super(Pod.class);
  }

  @Override
  protected Pod desired(final LLMTaskResource primary, final Context<LLMTaskResource> context) {
    final var meta = fromPrimary(primary);
    final var spec = buildPodSpec(primary.getSpec());

    return new PodBuilder()
        .withMetadata(meta)
        .withSpec(spec)
        .build();
  }

  private static ObjectMeta fromPrimary(final LLMTaskResource primary) {
    final var meta = primary.getMetadata();
    return new ObjectMetaBuilder()
        .withNamespace(meta.getNamespace())
        .withName(podName(meta.getName()))
        .withLabels(
            Map.of(SELECTOR, "%s/%s".formatted(meta.getNamespace(), meta.getName()),
                "app.kubernetes.io/managed-by", "openhands-operator")
        )
        .build();
  }

  private static PodSpec buildPodSpec(final LLMTaskSpec taskSpec) {
    final var podTemplate = requireNonNullElseGet(taskSpec.getPodSpec(), PodSpec::new);
    final var podBuilder = podTemplate.edit();

    final var containerOpt = podTemplate.getContainers()
        .stream()
        .filter(c -> OPENHANDS_CONTAINER_NAME.equals(c.getName()))
        .findFirst();
    containerOpt.ifPresent(podBuilder::removeFromContainers);

    final var containerBuilder = containerOpt.map(Container::edit).orElseGet(ContainerBuilder::new);
    buildContainer(taskSpec, containerBuilder);
    podBuilder.addToContainers(0, containerBuilder.build());

    return podBuilder.build();
  }

  private static void buildContainer(final LLMTaskSpec taskSpec, final ContainerBuilder containerBuilder) {
    final var llmSpec = taskSpec.getLlm();
    containerBuilder.addToEnv(env(OPENHANDS_MODEL_NAME_ENV_VAR, llmSpec.getModelName()));
    containerBuilder.addToEnv(env(OPENHANDS_MODEL_APIKEY_ENV_VAR, llmSpec.getApiKey()));
    containerBuilder.addToEnv(env(OPENHANDS_MODEL_BASEURL_ENV_VAR, llmSpec.getBaseUrl()));
    containerBuilder.addToEnv(env(OPENHANDS_PROMPT_ENV_VAR, taskSpec.getPrompt()));

    if (!containerBuilder.hasImage()) {
      containerBuilder.withImage(OPENHANDS_RUNTIME_IMAGE);
    }

    if (!containerBuilder.hasArgs()) {
      containerBuilder.withArgs(OPENHANDS_CONTAINER_ARGS);
    }
  }

  private static EnvVar env(final String name, final String value) {
    return new EnvVarBuilder()
        .withName(name)
        .withValue(value)
        .build();
  }

  private static String podName(final String taskName) {
    return "%s-%s".formatted(COMPONENT, taskName);
  }
}
