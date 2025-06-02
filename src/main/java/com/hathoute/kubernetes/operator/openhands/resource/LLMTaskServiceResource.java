package com.hathoute.kubernetes.operator.openhands.resource;

import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.hathoute.kubernetes.operator.openhands.config.OpenHandsConfig.OPENHANDS_RUNTIME_REPORTER_PORT;
import static com.hathoute.kubernetes.operator.openhands.config.OpenHandsConfig.OPENHANDS_RUNTIME_REPORTER_PORT_NAME;
import static com.hathoute.kubernetes.operator.openhands.reconciler.LLMTaskReconciler.SELECTOR;
import static com.hathoute.kubernetes.operator.openhands.reconciler.LLMTaskReconciler.SELECTOR_LABEL;

@KubernetesDependent(informer = @Informer(labelSelector = SELECTOR))
public class LLMTaskServiceResource extends
    CRUDKubernetesDependentResource<Service, LLMTaskResource> {
  private static final Logger LOGGER = LoggerFactory.getLogger(LLMTaskServiceResource.class);

  public LLMTaskServiceResource() {
    super(Service.class);
  }

  @Override
  protected Service desired(final LLMTaskResource primary, final Context<LLMTaskResource> context) {
    LOGGER.debug("Reconciling LLMTaskServiceResource of primary {}", primary);

    final var meta = primary.getMetadata();
    return new ServiceBuilder().editMetadata()
                               .withName(resourceName(meta.getName()))
                               .withNamespace(meta.getNamespace())
                               .withLabels(SELECTOR_LABEL)
                               .endMetadata()
                               .editSpec()
                               .withSelector(SELECTOR_LABEL)
                               .addNewPort()
                               .withProtocol("TCP")
                               .withPort(OPENHANDS_RUNTIME_REPORTER_PORT)
                               .withNewTargetPort(OPENHANDS_RUNTIME_REPORTER_PORT_NAME)
                               .endPort()
                               .endSpec()
                               .build();
  }

  public static String resourceName(final String primaryName) {
    return "llmtask-%s".formatted(primaryName);
  }
}
