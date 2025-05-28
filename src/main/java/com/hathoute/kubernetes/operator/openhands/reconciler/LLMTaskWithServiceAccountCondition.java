package com.hathoute.kubernetes.operator.openhands.reconciler;

import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskSpec;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskSpec.LLMPod;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskSpec.LLMPodServiceAccount;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

import static java.util.Optional.ofNullable;

public class LLMTaskWithServiceAccountCondition<R> implements Condition<R, LLMTaskResource> {
  @Override
  public boolean isMet(final DependentResource<R, LLMTaskResource> dependentResource,
      final LLMTaskResource primary, final Context<LLMTaskResource> context) {
    return ofNullable(primary.getSpec()).map(LLMTaskSpec::getPod)
                                        .map(LLMPod::getServiceAccount)
                                        .map(LLMPodServiceAccount::isCreate)
                                        .orElse(false);
  }
}
