package com.hathoute.kubernetes.operator.openhands;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorApplicationTest extends AbstractSpringOperatorTest {

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
