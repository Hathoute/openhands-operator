package com.hathoute.kubernetes.operator.openhands;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorApplicationTest extends AbstractSpringOperatorTest {

  @ParameterizedTest
  @ValueSource(strings = {"llmtasks.com.hathoute.kubernetes", "llms.com.hathoute.kubernetes"})
  void should_return_crd() {
    final var actual = kubernetesClient.apiextensions()
                                       .v1()
                                       .customResourceDefinitions()
                                       .withName("llmtasks.com.hathoute.kubernetes")
                                       .get();

    assertThat(actual).isNotNull();
  }
}
