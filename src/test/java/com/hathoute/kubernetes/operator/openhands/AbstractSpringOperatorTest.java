package com.hathoute.kubernetes.operator.openhands;

import com.hathoute.kubernetes.operator.openhands.crd.LLMResource;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.springboot.starter.test.EnableMockOperator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static com.hathoute.kubernetes.operator.openhands.TestFixtures.WORKING_NAMESPACE;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = {OperatorSpringApplication.class, TestConfig.class})
@ActiveProfiles("test")
@EnableMockOperator(crdPaths = {
    "classpath:META-INF/fabric8/llmtasks.com.hathoute.kubernetes-v1.yml",
    "classpath:META-INF/fabric8/llms.com.hathoute.kubernetes-v1.yml"})
public abstract class AbstractSpringOperatorTest {

  @Autowired
  protected KubernetesClient kubernetesClient;

  @AfterEach
  void teardown() {
    kubernetesClient.genericKubernetesResources(LLMResource.APIVERSION, LLMResource.KIND)
                    .inAnyNamespace()
                    .delete();
    kubernetesClient.genericKubernetesResources(LLMTaskResource.APIVERSION, LLMTaskResource.KIND)
                    .inAnyNamespace()
                    .delete();
    kubernetesClient.pods().inAnyNamespace().delete();

    await().atMost(5, TimeUnit.SECONDS)
           .until(() -> kubernetesClient.genericKubernetesResources(LLMResource.APIVERSION,
               LLMResource.KIND).list().getItems().isEmpty());
  }

  protected List<Pod> podsInNamespace(final String namespace) {
    return kubernetesClient.pods().inNamespace(namespace).list().getItems();
  }

  protected Pod getPod() {
    final var pods = podsInNamespace(WORKING_NAMESPACE);
    if (pods.size() != 1) {
      throw new IllegalStateException("Expecting 1 pod, found " + pods.size());
    }
    return pods.getFirst();
  }
}
