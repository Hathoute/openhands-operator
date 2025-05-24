package com.hathoute.kubernetes.operator.openhands;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.springboot.starter.test.EnableMockOperator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnableMockOperator(crdPaths = "classpath:META-INF/fabric8/llmtasks.com.hathoute.kubernetes-v1.yml")
class OperatorApplicationTest {

  @Autowired
  KubernetesClient kubernetesClient;

  @Test
  void should_return_llmtask_crd() {
    final var actual = kubernetesClient.apiextensions()
        .v1()
        .customResourceDefinitions()
        .withName("llmtasks.com.hathoute.kubernetes")
        .get();

    assertThat(actual).isNotNull();
  }
}
