package com.hathoute.kubernetes.operator.openhands.reconciler;

import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskSpec;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskSpec.LLMPod;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus.State;
import com.hathoute.kubernetes.operator.openhands.util.KubernetesUtil;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.Optional;
import org.springframework.stereotype.Component;

import static java.util.Optional.empty;
import static java.util.Optional.of;

@Component
public class LLMTaskValidator implements PrimaryResourceValidator<LLMTaskResource> {

  @Override
  public Optional<LLMTaskResource> validateOrErrorStatus(final LLMTaskResource resource,
      final Context<LLMTaskResource> context) {
    return validateLlmExists(resource, context).or(
                                                   () -> validateRulesIfServiceAccountCreate(resource))
                                               .or(() -> validateServiceAccountNameNotOverriden(
                                                   resource));
  }

  private static Optional<LLMTaskResource> validateLlmExists(final LLMTaskResource resource,
      final Context<LLMTaskResource> context) {
    final var llm = KubernetesUtil.getModelForTask(context.getClient(), resource);
    if (llm == null) {
      return ofError(resource,
          "Could not find LLM definition of '%s'".formatted(resource.getSpec().getLlmName()));
    }

    return Optional.empty();
  }

  private static Optional<LLMTaskResource> validateRulesIfServiceAccountCreate(
      final LLMTaskResource resource) {
    final var svcAccountOpt = of(resource).map(CustomResource::getSpec)
                                          .map(LLMTaskSpec::getPod)
                                          .map(LLMPod::getServiceAccount);
    if (svcAccountOpt.isEmpty()) {
      return empty();
    }

    final var svcAccount = svcAccountOpt.get();
    if (!svcAccount.isCreate()) {
      return empty();
    }

    final var rules = svcAccount.getRules();
    if (rules == null || rules.isEmpty()) {
      return ofError(resource, "Expecting non-empty rules when create serviceAccount is true");
    }

    return empty();
  }

  private static Optional<LLMTaskResource> validateServiceAccountNameNotOverriden(
      final LLMTaskResource resource) {
    final var podSpecOpt = of(resource).map(CustomResource::getSpec)
                                       .map(LLMTaskSpec::getPod)
                                       .map(LLMPod::getSpec);
    if (podSpecOpt.isEmpty()) {
      return empty();
    }

    final var podSpec = podSpecOpt.get();
    if (podSpec.getServiceAccount() != null || podSpec.getServiceAccountName() != null) {
      return ofError(resource, "pod.spec should not override serviceAccount or serviceAccountName");
    }

    return empty();
  }

  private static Optional<LLMTaskResource> ofError(final LLMTaskResource primary,
      final String errorMsg) {
    final var patched = new LLMTaskResource();
    patched.setMetadata(primary.getMetadata());
    final var status = new LLMTaskStatus();
    status.setState(State.FAILED);
    status.setErrorReason(errorMsg);
    patched.setStatus(status);
    return of(patched);
  }
}
