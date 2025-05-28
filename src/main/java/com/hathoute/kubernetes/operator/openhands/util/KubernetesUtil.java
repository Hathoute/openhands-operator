package com.hathoute.kubernetes.operator.openhands.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hathoute.kubernetes.operator.openhands.crd.LLMResource;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.KubernetesClient;

public final class KubernetesUtil {

  private KubernetesUtil() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static LLMResource getModelForTask(final KubernetesClient client,
      final LLMTaskResource task) {
    final var namespace = task.getMetadata().getNamespace();
    final var llmName = task.getSpec().getLlmName();

    return getNamespacedObject(client, LLMResource.APIVERSION, LLMResource.KIND, namespace, llmName,
        LLMResource.class);
  }

  private static <T extends HasMetadata & Namespaced> T getNamespacedObject(
      final KubernetesClient client, final String apiVersion, final String kind,
      final String namespace, final String name, final Class<T> type) {
    final var item = client.genericKubernetesResources(apiVersion, kind)
                           .inNamespace(namespace)
                           .withName(name)
                           .get();
    if (item == null) {
      return null;
    }

    try {
      final var mapper = new ObjectMapper();
      final var serialized = mapper.writeValueAsString(item);
      return mapper.readValue(serialized, type);
    } catch (final JsonProcessingException e) {
      throw new IllegalStateException(
          "Could not convert GenericKubernetesResource to '%s'".formatted(type.getName()), e);
    }
  }

  public static String apiGroup(final String apiVersion) {
    return apiVersion.substring(0, apiVersion.lastIndexOf('/'));
  }
}
