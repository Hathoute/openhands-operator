package com.hathoute.kubernetes.operator.openhands.resource;

import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleRef;
import io.fabric8.kubernetes.api.model.rbac.Subject;
import io.fabric8.kubernetes.client.utils.ApiVersionUtil;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.hathoute.kubernetes.operator.openhands.reconciler.LLMTaskReconciler.SELECTOR;
import static com.hathoute.kubernetes.operator.openhands.reconciler.LLMTaskReconciler.SELECTOR_LABEL;

@KubernetesDependent(informer = @Informer(labelSelector = SELECTOR))
public class LLMTaskRoleBindingResource extends
    CRUDKubernetesDependentResource<RoleBinding, LLMTaskResource> {
  private static final Logger LOGGER = LoggerFactory.getLogger(LLMTaskRoleBindingResource.class);

  public LLMTaskRoleBindingResource() {
    super(RoleBinding.class);
  }

  @Override
  protected RoleBinding desired(final LLMTaskResource primary,
      final Context<LLMTaskResource> context) {
    LOGGER.debug("Reconciling LLMTaskRoleBindingResource of primary {}", primary);

    final var role = context.getSecondaryResource(Role.class)
                            .orElseThrow(() -> new IllegalStateException(
                                "Expecting Role to be present before reconciling RoleBinding"));
    final var serviceAccount = context.getSecondaryResource(ServiceAccount.class)
                                      .orElseThrow(() -> new IllegalStateException(
                                          "Expecting ServiceAccount to be present before reconciling RoleBinding"));

    final var meta = primary.getMetadata();
    return new RoleBindingBuilder().editMetadata()
                                   .withName(resourceName(meta.getName()))
                                   .withNamespace(meta.getNamespace())
                                   .withLabels(SELECTOR_LABEL)
                                   .endMetadata()
                                   .withRoleRef(of(role))
                                   .withSubjects(List.of(of(serviceAccount)))
                                   .build();
  }

  private static RoleRef of(final Role role) {
    final var apiGroup = ApiVersionUtil.apiGroup(role, null);
    return new RoleRef(apiGroup, role.getKind(), role.getMetadata().getName());
  }

  private static Subject of(final ServiceAccount serviceAccount) {
    final var meta = serviceAccount.getMetadata();
    return new Subject("", serviceAccount.getKind(), meta.getName(), meta.getNamespace());
  }

  private static String resourceName(final String primaryName) {
    return "llmtask-%s".formatted(primaryName);
  }
}
