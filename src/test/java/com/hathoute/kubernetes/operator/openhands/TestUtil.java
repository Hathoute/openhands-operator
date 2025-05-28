package com.hathoute.kubernetes.operator.openhands;

import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.time.Duration;
import java.time.temporal.TemporalUnit;
import java.util.Map;

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

  public static LLMTaskStatus.State extractState(final GenericKubernetesResource resource) {
    final var statusMap = (Map<String, Object>) resource.getAdditionalProperties().get("status");
    final var state = (String) statusMap.get("state");
    return state != null ? LLMTaskStatus.State.valueOf(state) : null;
  }

  public static String extractErrorReason(final GenericKubernetesResource resource) {
    final var statusMap = (Map<String, Object>) resource.getAdditionalProperties().get("status");
    return (String) statusMap.get("errorReason");
  }
}
