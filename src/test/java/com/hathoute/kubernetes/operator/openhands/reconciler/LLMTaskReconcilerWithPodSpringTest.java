package com.hathoute.kubernetes.operator.openhands.reconciler;

import com.hathoute.kubernetes.operator.openhands.AbstractSpringOperatorTest;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleRef;
import io.fabric8.kubernetes.api.model.rbac.Subject;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static com.hathoute.kubernetes.operator.openhands.TestFixtures.LLM_PROMPT;
import static com.hathoute.kubernetes.operator.openhands.TestFixtures.LLM_RESOURCE;
import static com.hathoute.kubernetes.operator.openhands.TestFixtures.LLM_TASK_NAME;
import static com.hathoute.kubernetes.operator.openhands.TestFixtures.WORKING_NAMESPACE;
import static com.hathoute.kubernetes.operator.openhands.TestFixtures.taskWithPodSpec;
import static com.hathoute.kubernetes.operator.openhands.TestFixtures.taskWithSvcAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class LLMTaskReconcilerWithPodSpringTest extends AbstractSpringOperatorTest {

  @Test
  void should_override_pod_with_user_pod() {
    kubernetesClient.resource(LLM_RESOURCE).inNamespace(WORKING_NAMESPACE).create();
    final var podSpec = new PodSpecBuilder().addNewContainer()
                                            .withName("sidecar")
                                            .withImage("busybox:latest")
                                            .endContainer()
                                            .addNewContainer()
                                            .withName("openhands")
                                            .withImage(
                                                "my-private-artifactory/private-repo/openhands-runtime:0.3.0")
                                            .endContainer()
                                            .addNewImagePullSecret("registry")
                                            .build();
    final var taskWithPod = taskWithPodSpec(podSpec);
    kubernetesClient.resource(taskWithPod).inNamespace(WORKING_NAMESPACE).create();

    await().pollInterval(1, TimeUnit.SECONDS)
           .atMost(3, TimeUnit.SECONDS)
           .until(() -> !podsInNamespace(WORKING_NAMESPACE).isEmpty());

    final var actualPodSpec = getPod().getSpec();
    assertThat(actualPodSpec.getContainers()).map(Container::getName)
                                             .containsExactly("openhands", "sidecar");
    assertThat(actualPodSpec.getContainers()).map(Container::getImage)
                                             .containsExactly(
                                                 "my-private-artifactory/private-repo/openhands-runtime:0.3.0",
                                                 "busybox:latest");
    assertThat(actualPodSpec.getImagePullSecrets()).map(LocalObjectReference::getName)
                                                   .containsExactly("registry");

    final var container = actualPodSpec.getContainers().getFirst();
    assertThat(container.getEnv()).map(EnvVar::getName)
                                  .contains("LLM_MODEL", "LLM_API_KEY", "OPENHANDS_PROMPT");
    assertThat(container.getEnv()).filteredOn(e -> e.getName().equals("OPENHANDS_PROMPT"))
                                  .map(EnvVar::getValue)
                                  .containsExactly(LLM_PROMPT);
  }

  @Test
  void should_create_service_account_when_requested() {
    kubernetesClient.resource(LLM_RESOURCE).inNamespace(WORKING_NAMESPACE).create();
    final var dataReader = new PolicyRuleBuilder().withApiGroups("")
                                                  .withResources("configmap", "secret")
                                                  .withVerbs("get", "watch", "list")
                                                  .build();
    final var podWriter = new PolicyRuleBuilder().withApiGroups("")
                                                 .withResources("pod")
                                                 .withVerbs("*")
                                                 .build();
    final var taskWithSvcAcc = taskWithSvcAccount(dataReader, podWriter);
    kubernetesClient.resource(taskWithSvcAcc).inNamespace(WORKING_NAMESPACE).create();

    await().pollInterval(1, TimeUnit.SECONDS)
           .atMost(3, TimeUnit.SECONDS)
           .until(() -> !podsInNamespace(WORKING_NAMESPACE).isEmpty());

    final var serviceAccounts = kubernetesClient.serviceAccounts()
                                                .inNamespace(WORKING_NAMESPACE)
                                                .list()
                                                .getItems();
    assertThat(serviceAccounts).hasSize(1);
    final var svcAcc = serviceAccounts.getFirst();
    assertThat(svcAcc.getMetadata().getName()).contains(LLM_TASK_NAME);

    final var roles = kubernetesClient.rbac()
                                      .roles()
                                      .inNamespace(WORKING_NAMESPACE)
                                      .list()
                                      .getItems();
    assertThat(roles).hasSize(1);
    final var role = roles.getFirst();
    assertThat(role.getMetadata().getName()).contains(LLM_TASK_NAME);
    assertThat(role.getRules()).usingRecursiveFieldByFieldElementComparator()
                               .containsExactly(dataReader, podWriter);

    final var roleBindings = kubernetesClient.rbac()
                                             .roleBindings()
                                             .inNamespace(WORKING_NAMESPACE)
                                             .list()
                                             .getItems();
    assertThat(roleBindings).hasSize(1);
    final var roleBinding = roleBindings.getFirst();
    assertThat(roleBinding.getMetadata().getName()).contains(LLM_TASK_NAME);
    assertThat(roleBinding.getRoleRef()).usingRecursiveComparison()
                                        .isEqualTo(new RoleRef("rbac.authorization.k8s.io", "Role",
                                            role.getMetadata().getName()));
    assertThat(roleBinding.getSubjects()).usingRecursiveFieldByFieldElementComparator()
                                         .containsExactly(new Subject("", "ServiceAccount",
                                             svcAcc.getMetadata().getName(), WORKING_NAMESPACE));

    final var pod = getPod();
    assertThat(pod.getSpec().getServiceAccountName()).isEqualTo(svcAcc.getMetadata().getName());
    assertThat(pod.getSpec().getServiceAccount()).isNull();
  }

}
