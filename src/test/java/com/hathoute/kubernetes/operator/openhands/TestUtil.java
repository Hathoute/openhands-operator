package com.hathoute.kubernetes.operator.openhands;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.time.Duration;
import java.time.temporal.TemporalUnit;

public final class TestUtil {

  private TestUtil() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static void setPodState(final KubernetesClient client, final Pod pod, final String phase) {
    final var podMetadata = pod.getMetadata();
    final var patchedPod = new PodBuilder(pod).editStatus().withPhase(phase).endStatus().build();
    client.pods().inNamespace(podMetadata.getNamespace()).resource(patchedPod).patchStatus();
  }

  public static void waitFor(final int duration, final TemporalUnit timeUnit) {
    try {
      // Simple thread sleep, no need for complex logic...
      Thread.sleep(Duration.of(duration, timeUnit));
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
