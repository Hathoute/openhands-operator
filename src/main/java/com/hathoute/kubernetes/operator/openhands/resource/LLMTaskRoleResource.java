package com.hathoute.kubernetes.operator.openhands.resource;

import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskSpec;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskSpec.LLMPod;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskSpec.LLMPodServiceAccount;
import com.hathoute.kubernetes.operator.openhands.reconciler.LLMTaskReconciler;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.hathoute.kubernetes.operator.openhands.reconciler.LLMTaskReconciler.SELECTOR;
import static java.util.Optional.ofNullable;

@KubernetesDependent(informer = @Informer(labelSelector = SELECTOR))
public class LLMTaskRoleResource extends CRUDKubernetesDependentResource<Role, LLMTaskResource> {
  private static final Logger LOGGER = LoggerFactory.getLogger(LLMTaskRoleResource.class);

  public LLMTaskRoleResource() {
    super(Role.class);
  }

  @Override
  protected Role desired(final LLMTaskResource primary, final Context<LLMTaskResource> context) {
    LOGGER.debug("Reconciling LLMTaskRoleResource of primary {}", primary);

    final var serviceAccountRules = getRules(primary);
    final var meta = primary.getMetadata();
    return new RoleBuilder().editMetadata()
                            .withName(resourceName(meta.getName()))
                            .withNamespace(meta.getNamespace())
                            .withLabels(LLMTaskReconciler.SELECTOR_LABEL)
                            .endMetadata()
                            .addAllToRules(serviceAccountRules)
                            .build();
  }

  private static List<PolicyRule> getRules(final LLMTaskResource primary) {
    return ofNullable(primary).map(CustomResource::getSpec)
                              .map(LLMTaskSpec::getPod)
                              .map(LLMPod::getServiceAccount)
                              .map(LLMPodServiceAccount::getRules)
                              .orElseThrow(() -> new IllegalStateException(
                                  "Rules cannot be null when attempting to reconcile Role"));
  }

  private static String resourceName(final String primaryName) {
    return "llmtask-%s".formatted(primaryName);
  }
}
