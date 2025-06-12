package com.hathoute.kubernetes.operator.openhands.reconciler;

import com.hathoute.kubernetes.operator.openhands.TestFixtures;
import com.hathoute.kubernetes.operator.openhands.crd.LLMResource;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus;
import com.hathoute.kubernetes.operator.openhands.util.KubernetesUtil;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class LLMTaskValidatorTest {

  private LLMTaskValidator validator;
  private Context<LLMTaskResource> context;
  private KubernetesClient client;

  @BeforeEach
  void setup() {
    validator = new LLMTaskValidator();
    context = mock(Context.class);
    client = mock(KubernetesClient.class);
    when(context.getClient()).thenReturn(client);
  }

  @Test
  void test_validate_fails_when_llm_does_not_exist() {
    try (final var kubernetesUtil = mockStatic(KubernetesUtil.class)) {
      kubernetesUtil.when(() -> KubernetesUtil.getModelForTask(eq(client), any())).thenReturn(null);

      final var resource = TestFixtures.YAML_MAPPER.readValue(TestFixtures.LLM_TASK_RESOURCE,
          LLMTaskResource.class);
      final var result = validator.validateOrErrorStatus(resource, context);

      assertThat(result).isPresent();
      assertThat(result.get().getStatus()).isNotNull()
                                          .extracting(s -> s.getState().toString(),
                                              LLMTaskStatus::getErrorReason)
                                          .containsExactly("FAILED",
                                              "Could not find LLM definition of '%s'".formatted(
                                                  resource.getSpec().getLlmName()));
    } catch (final Exception e) {
      throw new AssertionError("Unexpected exception", e);
    }
  }

  @Test
  void test_validate_fails_when_service_account_created_without_rules() {
    try (final var kubernetesUtil = mockStatic(KubernetesUtil.class)) {
      kubernetesUtil.when(() -> KubernetesUtil.getModelForTask(eq(client), any()))
                    .thenReturn(new LLMResource()); // simulate model exists

      final var yaml = TestFixtures.taskWithSvcAccount(); // no rules provided
      final var resource = TestFixtures.YAML_MAPPER.readValue(yaml, LLMTaskResource.class);

      final var result = validator.validateOrErrorStatus(resource, context);

      assertThat(result).isPresent();
      assertThat(result.get().getStatus()).isNotNull()
                                          .extracting(s -> s.getState().toString(),
                                              LLMTaskStatus::getErrorReason)
                                          .containsExactly("FAILED",
                                              "Expecting non-empty rules when create serviceAccount is true");
    } catch (final Exception e) {
      throw new AssertionError("Unexpected exception", e);
    }
  }

  @Test
  void test_validate_fails_when_pod_overrides_service_account() {
    try (final var kubernetesUtil = mockStatic(KubernetesUtil.class)) {
      kubernetesUtil.when(() -> KubernetesUtil.getModelForTask(eq(client), any()))
                    .thenReturn(new LLMResource()); // simulate model exists

      final var podSpec = new PodSpec();
      podSpec.setServiceAccountName("overridden-account");
      final var yaml = TestFixtures.taskWithPodSpec(podSpec);
      final var resource = TestFixtures.YAML_MAPPER.readValue(yaml, LLMTaskResource.class);

      final var result = validator.validateOrErrorStatus(resource, context);

      assertThat(result).isPresent();
      assertThat(result.get().getStatus()).isNotNull()
                                          .extracting(s -> s.getState().toString(),
                                              LLMTaskStatus::getErrorReason)
                                          .containsExactly("FAILED",
                                              "pod.spec should not override serviceAccount or serviceAccountName");
    } catch (final Exception e) {
      throw new AssertionError("Unexpected exception", e);
    }
  }

  @Test
  void test_validate_passes_when_all_checks_pass() {
    try (final var kubernetesUtil = mockStatic(KubernetesUtil.class)) {
      kubernetesUtil.when(() -> KubernetesUtil.getModelForTask(eq(client), any()))
                    .thenReturn(new LLMResource()); // simulate model exists

      final var rule = new PolicyRule();
      rule.setApiGroups(List.of(""));
      rule.setResources(List.of("pods"));
      rule.setVerbs(List.of("get", "list"));

      final var yaml = TestFixtures.taskWithSvcAccount(rule);
      final var resource = TestFixtures.YAML_MAPPER.readValue(yaml, LLMTaskResource.class);

      final var result = validator.validateOrErrorStatus(resource, context);

      assertThat(result).isEmpty();
    } catch (final Exception e) {
      throw new AssertionError("Unexpected exception", e);
    }
  }
}
