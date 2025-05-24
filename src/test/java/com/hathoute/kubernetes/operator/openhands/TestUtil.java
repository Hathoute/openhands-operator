package com.hathoute.kubernetes.operator.openhands;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

public final class TestUtil {

  private TestUtil() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static void setPodState(final KubernetesClient client, final Pod pod, final String phase) {
    final var podMetadata = pod.getMetadata();
    final var patchedPod = new PodBuilder(pod)
        .editStatus()
        .withPhase(phase)
        .endStatus()
        .build();
    client.pods().inNamespace(podMetadata.getNamespace()).resource(patchedPod).patchStatus();
  }
}
