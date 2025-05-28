package com.hathoute.kubernetes.operator.openhands.resource;

import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import com.hathoute.kubernetes.operator.openhands.reconciler.LLMTaskReconciler;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.hathoute.kubernetes.operator.openhands.reconciler.LLMTaskReconciler.SELECTOR;

@KubernetesDependent(informer = @Informer(labelSelector = SELECTOR))
public class LLMTaskServiceAccountResource extends
    CRUDKubernetesDependentResource<ServiceAccount, LLMTaskResource> {
  private static final Logger LOGGER = LoggerFactory.getLogger(LLMTaskServiceAccountResource.class);

  public LLMTaskServiceAccountResource() {
    super(ServiceAccount.class);
  }

  @Override
  protected ServiceAccount desired(final LLMTaskResource primary,
      final Context<LLMTaskResource> context) {
    LOGGER.debug("Reconciling LLMTaskServiceAccountResource of primary {}", primary);

    final var meta = primary.getMetadata();
    return new ServiceAccountBuilder().editMetadata()
                                      .withName(resourceName(meta.getName()))
                                      .withNamespace(meta.getNamespace())
                                      .withLabels(LLMTaskReconciler.SELECTOR_LABEL)
                                      .endMetadata()
                                      .build();
  }

  public static String resourceName(final String primaryName) {
    return "llmtask-%s".formatted(primaryName);
  }
}
