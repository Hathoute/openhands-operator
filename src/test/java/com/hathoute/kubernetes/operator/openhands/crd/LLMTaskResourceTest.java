package com.hathoute.kubernetes.operator.openhands.crd;

import com.hathoute.kubernetes.operator.openhands.AbstractSpringOperatorTest;
import org.junit.jupiter.api.Test;

import static com.hathoute.kubernetes.operator.openhands.TestFixtures.LLM_TASK_RESOURCE;
import static com.hathoute.kubernetes.operator.openhands.TestFixtures.WORKING_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

class LLMTaskResourceTest extends AbstractSpringOperatorTest {

  @Test
  void should_find_handler_to_create_task() {
    final var createdObject = kubernetesClient.resource(LLM_TASK_RESOURCE)
                                              .inNamespace(WORKING_NAMESPACE)
                                              .create();

    assertThat(createdObject.getKind()).isEqualTo("LLMTask");

    final var actualMetadata = createdObject.getMetadata();
    assertThat(actualMetadata).isNotNull();
    assertThat(actualMetadata.getName()).isEqualTo("fix-issue-1234");
    assertThat(actualMetadata.getNamespace()).isEqualTo(WORKING_NAMESPACE);

    final var actualObjectList = kubernetesClient.genericKubernetesResources(
        "com.hathoute.kubernetes/v1alpha1", "LLMTask").inNamespace(WORKING_NAMESPACE).list();
    assertThat(actualObjectList.getItems()).hasSize(1);
    final var actualObject = actualObjectList.getItems().getFirst();

    assertThat(actualObject.getAdditionalProperties()).containsKey("spec");
    final var objectSpec = actualObject.getAdditionalProperties().get("spec");

    assertThat(objectSpec).asInstanceOf(MAP)
                          .containsEntry("prompt", "This is the prompt for the task");
  }
}