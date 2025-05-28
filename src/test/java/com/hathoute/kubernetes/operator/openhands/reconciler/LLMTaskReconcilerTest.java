package com.hathoute.kubernetes.operator.openhands.reconciler;

import com.hathoute.kubernetes.operator.openhands.TestFixtures;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedWorkflowAndDependentResourceContext;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus.State.FAILED;
import static com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus.State.QUEUED;
import static com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus.State.RUNNING;
import static com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus.State.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LLMTaskReconcilerTest {

  private PrimaryResourceValidator<LLMTaskResource> validator;
  private Context<LLMTaskResource> context;
  private ManagedWorkflowAndDependentResourceContext managedWorkflowAndDependentResourceContext;
  private LLMTaskReconciler reconciler;

  @BeforeEach
  void setup() {
    validator = mock(PrimaryResourceValidator.class);
    managedWorkflowAndDependentResourceContext = mock(
        ManagedWorkflowAndDependentResourceContext.class);
    context = mock(Context.class);
    when(context.managedWorkflowAndDependentResourceContext()).thenReturn(
        managedWorkflowAndDependentResourceContext);
    reconciler = new LLMTaskReconciler(validator);
  }

  @AfterEach
  void teardown() {
    verifyNoMoreInteractions(context);
    verifyNoMoreInteractions(managedWorkflowAndDependentResourceContext);
  }

  @Test
  void should_patch_status_when_validation_fails() throws Exception {
    final var input = TestFixtures.MAPPER.readValue(TestFixtures.LLM_TASK_RESOURCE,
        LLMTaskResource.class);
    final var failed = new LLMTaskResource();
    final var status = new LLMTaskStatus();
    status.setState(FAILED);
    status.setErrorReason("Validation failed");
    failed.setStatus(status);
    when(validator.validateOrErrorStatus(any(), any())).thenReturn(Optional.of(failed));

    final var result = reconciler.reconcile(input, context);

    assertStatus(result, status);
  }

  @Test
  void should_return_no_update_if_state_did_not_change() throws Exception {
    final var input = TestFixtures.MAPPER.readValue(TestFixtures.LLM_TASK_RESOURCE,
        LLMTaskResource.class);
    final var existingStatus = new LLMTaskStatus();
    existingStatus.setState(RUNNING);
    input.setStatus(existingStatus);

    final var pod = new Pod();
    final var podStatus = new PodStatus();
    podStatus.setPhase("Running");
    pod.setStatus(podStatus);

    when(validator.validateOrErrorStatus(any(), any())).thenReturn(Optional.empty());
    when(context.getSecondaryResource(Pod.class)).thenReturn(Optional.of(pod));

    final var result = reconciler.reconcile(input, context);

    assertThat(result.isNoUpdate()).isTrue();
    verify(context).getSecondaryResource(Pod.class);
    verify(context).managedWorkflowAndDependentResourceContext();
    verify(managedWorkflowAndDependentResourceContext).reconcileManagedWorkflow();
  }

  @Test
  void should_patch_status_when_pod_state_changes() throws Exception {
    final var input = TestFixtures.MAPPER.readValue(TestFixtures.LLM_TASK_RESOURCE,
        LLMTaskResource.class);
    final var oldStatus = new LLMTaskStatus();
    oldStatus.setState(QUEUED);
    input.setStatus(oldStatus);

    final var pod = new Pod();
    final var podStatus = new PodStatus();
    podStatus.setPhase("Succeeded");
    pod.setStatus(podStatus);

    when(validator.validateOrErrorStatus(any(), any())).thenReturn(Optional.empty());
    when(context.getSecondaryResource(Pod.class)).thenReturn(Optional.of(pod));

    final var result = reconciler.reconcile(input, context);

    verify(context).getSecondaryResource(Pod.class);
    verify(context).managedWorkflowAndDependentResourceContext();
    verify(managedWorkflowAndDependentResourceContext).reconcileManagedWorkflow();

    final var expectedStatus = new LLMTaskStatus();
    expectedStatus.setState(SUCCEEDED);
    assertStatus(result, expectedStatus);

  }

  @Test
  void should_return_queued_if_pod_has_no_status() throws Exception {
    final var input = TestFixtures.MAPPER.readValue(TestFixtures.LLM_TASK_RESOURCE,
        LLMTaskResource.class);
    final var pod = new Pod(); // no status set

    when(validator.validateOrErrorStatus(any(), any())).thenReturn(Optional.empty());
    when(context.getSecondaryResource(Pod.class)).thenReturn(Optional.of(pod));

    final var result = reconciler.reconcile(input, context);

    verify(context).getSecondaryResource(Pod.class);
    verify(context).managedWorkflowAndDependentResourceContext();
    verify(managedWorkflowAndDependentResourceContext).reconcileManagedWorkflow();

    final var expectedStatus = new LLMTaskStatus();
    expectedStatus.setState(QUEUED);
    assertStatus(result, expectedStatus);
  }

  @Test
  void cleanup_should_invoke_workflow_cleanup() {
    final var input = new LLMTaskResource();
    final var result = reconciler.cleanup(input, context);

    verify(context).managedWorkflowAndDependentResourceContext();
    verify(managedWorkflowAndDependentResourceContext).cleanupManageWorkflow();
    assertThat(result.isRemoveFinalizer()).isTrue();
  }

  private static void assertStatus(final UpdateControl<LLMTaskResource> result,
      final LLMTaskStatus status) {
    assertThat(result.isPatchStatus()).isTrue();
    assertThat(result.getResource()).isNotEmpty();
    final var actualStatus = result.getResource().get().getStatus();
    assertThat(actualStatus).usingRecursiveComparison().isEqualTo(status);
  }
}
