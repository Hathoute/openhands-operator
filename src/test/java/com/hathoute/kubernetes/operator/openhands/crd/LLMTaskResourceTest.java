package com.hathoute.kubernetes.operator.openhands.crd;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.hathoute.kubernetes.operator.openhands.TestFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LLMTaskResourceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper(
      new YAMLFactory()).setSerializationInclusion(Include.NON_NULL);

  @Test
  void should_use_correct_llmtask_resource() throws JsonProcessingException {
    // Test that the LLMTask used in TestFixtures is valid
    final var nodes = MAPPER.readValue(TestFixtures.LLM_TASK_RESOURCE, Object.class);

    final var llmTask = MAPPER.readValue(TestFixtures.LLM_TASK_RESOURCE, LLMTaskResource.class);
    // non-mapped nodes are lost in this convertion
    final var serialized = MAPPER.writeValueAsString(llmTask);
    final var actual = MAPPER.readValue(serialized, Object.class);

    assertThat(actual).isEqualTo(nodes);
  }

  @Test
  void should_use_correct_llm_resource() throws JsonProcessingException {
    // Test that the LLM used in TestFixtures is valid
    final var nodes = MAPPER.readValue(TestFixtures.LLM_RESOURCE, Object.class);

    final var llm = MAPPER.readValue(TestFixtures.LLM_RESOURCE, LLMResource.class);
    // non-mapped nodes are lost in this convertion
    final var serialized = MAPPER.writeValueAsString(llm);
    final var actual = MAPPER.readValue(serialized, Object.class);

    assertThat(actual).isEqualTo(nodes);
  }
}