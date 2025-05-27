package com.hathoute.kubernetes.operator.openhands.reconciler;

import com.hathoute.kubernetes.operator.openhands.AbstractSpringOperatorTest;
import com.hathoute.kubernetes.operator.openhands.TestUtil;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus.State;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

import static com.hathoute.kubernetes.operator.openhands.TestFixtures.LLM_RESOURCE;
import static com.hathoute.kubernetes.operator.openhands.TestFixtures.LLM_TASK_NAME;
import static com.hathoute.kubernetes.operator.openhands.TestFixtures.LLM_TASK_RESOURCE;
import static com.hathoute.kubernetes.operator.openhands.TestFixtures.WORKING_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class LLMTaskReconcilerTest extends AbstractSpringOperatorTest {

  @Test
  void should_fail_when_llm_definition_is_not_found() {
    kubernetesClient.resource(LLM_TASK_RESOURCE).inNamespace(WORKING_NAMESPACE).create();

    await().pollInterval(1, TimeUnit.SECONDS)
           .atMost(3, TimeUnit.SECONDS)
           .until(() -> getTaskObject(LLMTaskReconcilerTest::extractState) == State.FAILED);

    assertThat(podsInNamespace(WORKING_NAMESPACE)).isEmpty();
    
    final var errorMessage = getTaskObject(LLMTaskReconcilerTest::extractErrorReason);
    assertThat(errorMessage).isEqualTo("Could not find LLM definition of 'local-model'");
  }

  @Test
  void should_not_reconcile_tasks_from_unwatched_namespaces() {
    final var otherNamespace = "unwatched";
    final var namespace = new NamespaceBuilder().withMetadata(
        new ObjectMetaBuilder().withName(otherNamespace).build()).build();
    kubernetesClient.namespaces().resource(namespace).create();
    kubernetesClient.resource(LLM_RESOURCE).inNamespace(otherNamespace).create();
    kubernetesClient.resource(LLM_TASK_RESOURCE).inNamespace(otherNamespace).create();

    TestUtil.waitFor(1, ChronoUnit.SECONDS);
    assertThat(getTaskObject(LLMTaskReconcilerTest::extractState)).isNull();
    assertThat(podsInNamespace(otherNamespace)).isEmpty();
  }

  @Test
  void should_correctly_manage_states() {
    kubernetesClient.resource(LLM_RESOURCE).inNamespace(WORKING_NAMESPACE).create();
    kubernetesClient.resource(LLM_TASK_RESOURCE).inNamespace(WORKING_NAMESPACE).create();

    await().pollInterval(1, TimeUnit.SECONDS)
           .atMost(3, TimeUnit.SECONDS)
           .until(() -> !podsInNamespace(WORKING_NAMESPACE).isEmpty());

    final var pods = podsInNamespace(WORKING_NAMESPACE);
    assertThat(pods).hasSize(1);
    final var pod = pods.getFirst();
    assertThat(pod.getMetadata().getName()).isEqualTo("llm-task-" + LLM_TASK_NAME);

    // Simulate pod phases
    verifyPodStateTriggersReconciliation("Pending", LLMTaskStatus.State.QUEUED);
    verifyPodStateTriggersReconciliation("Running", LLMTaskStatus.State.RUNNING);
    verifyPodStateTriggersReconciliation("Succeeded", LLMTaskStatus.State.SUCCEEDED);
    verifyPodStateTriggersReconciliation("Failed", LLMTaskStatus.State.FAILED);
    verifyPodStateTriggersReconciliation("Unknown", LLMTaskStatus.State.FAILED);
  }

  private Pod getPod() {
    return kubernetesClient.pods()
                           .inNamespace(WORKING_NAMESPACE)
                           .list()
                           .getItems()
                           .stream()
                           .findFirst()
                           .orElseThrow(() -> new IllegalStateException("Could not find pod"));
  }

  private void verifyPodStateTriggersReconciliation(final String podPhase,
      final LLMTaskStatus.State state) {
    // Get the latest state of the Pod
    final var pod = getPod();

    TestUtil.setPodState(kubernetesClient, pod, podPhase);
    await().pollInterval(500, TimeUnit.MILLISECONDS)
           .atMost(2, TimeUnit.SECONDS)
           .until(() -> state == getTaskObject(LLMTaskReconcilerTest::extractState));
  }

  private <T> T getTaskObject(final Function<GenericKubernetesResource, T> extractor) {
    return kubernetesClient.genericKubernetesResources(LLMTaskResource.APIVERSION,
                               LLMTaskResource.KIND)
                           .inNamespace(WORKING_NAMESPACE)
                           .list()
                           .getItems()
                           .stream()
                           .findFirst()
                           .map(extractor)
                           .orElse(null);
  }

  private static LLMTaskStatus.State extractState(final GenericKubernetesResource resource) {
    final var statusMap = (Map<String, Object>) resource.getAdditionalProperties().get("status");
    final var state = (String) statusMap.get("state");
    return state != null ? LLMTaskStatus.State.valueOf(state) : null;
  }

  private static String extractErrorReason(final GenericKubernetesResource resource) {
    final var statusMap = (Map<String, Object>) resource.getAdditionalProperties().get("status");
    return (String) statusMap.get("errorReason");
  }

  private List<Pod> podsInNamespace(final String namespace) {
    return kubernetesClient.pods().inNamespace(namespace).list().getItems();
  }
}