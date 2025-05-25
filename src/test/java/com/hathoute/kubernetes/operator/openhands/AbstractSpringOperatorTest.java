package com.hathoute.kubernetes.operator.openhands;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.springboot.starter.test.EnableMockOperator;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = {OperatorSpringApplication.class, TestConfig.class})
@EnableMockOperator(crdPaths = "classpath:META-INF/fabric8/llmtasks.com.hathoute.kubernetes-v1.yml")
public abstract class AbstractSpringOperatorTest {

  @Autowired
  protected KubernetesClient kubernetesClient;

  @AfterEach
  void teardown() {
    kubernetesClient.genericKubernetesResources(TestFixtures.LLM_TASK_APIVERSION,
        TestFixtures.LLM_TASK_KIND).inAnyNamespace().delete();
    kubernetesClient.pods().inAnyNamespace().delete();

    await().atMost(5, TimeUnit.SECONDS)
           .until(
               () -> kubernetesClient.genericKubernetesResources(TestFixtures.LLM_TASK_APIVERSION,
                   TestFixtures.LLM_TASK_KIND).list().getItems().isEmpty());
  }
}
