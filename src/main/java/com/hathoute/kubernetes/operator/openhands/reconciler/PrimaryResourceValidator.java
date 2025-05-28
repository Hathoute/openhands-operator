package com.hathoute.kubernetes.operator.openhands.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.Optional;

@FunctionalInterface
public interface PrimaryResourceValidator<T extends HasMetadata> {
  Optional<T> validateOrErrorStatus(final T resource, final Context<T> context);
}
