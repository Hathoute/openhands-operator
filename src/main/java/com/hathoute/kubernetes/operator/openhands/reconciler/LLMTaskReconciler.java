package com.hathoute.kubernetes.operator.openhands.reconciler;

import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskStatus;
import com.hathoute.kubernetes.operator.openhands.resource.LLMTaskPodResource;
import com.hathoute.kubernetes.operator.openhands.resource.LLMTaskRoleBindingResource;
import com.hathoute.kubernetes.operator.openhands.resource.LLMTaskRoleResource;
import com.hathoute.kubernetes.operator.openhands.resource.LLMTaskServiceAccountResource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Workflow(dependents = {
    @Dependent(name = "llmtaskrole", type = LLMTaskRoleResource.class, reconcilePrecondition = LLMTaskWithServiceAccountCondition.class),
    @Dependent(name = "llmtaskserviceaccount", type = LLMTaskServiceAccountResource.class, reconcilePrecondition = LLMTaskWithServiceAccountCondition.class),
    @Dependent(type = LLMTaskRoleBindingResource.class, reconcilePrecondition = LLMTaskWithServiceAccountCondition.class, dependsOn = {
        "llmtaskrole", "llmtaskserviceaccount"}),
    @Dependent(type = LLMTaskPodResource.class)}, explicitInvocation = true, handleExceptionsInReconciler = true)
@ControllerConfiguration
@Component
public class LLMTaskReconciler implements Reconciler<LLMTaskResource>, Cleaner<LLMTaskResource> {
  private static final Logger LOGGER = LoggerFactory.getLogger(LLMTaskReconciler.class);

  public static final String SELECTOR = "app.kubernetes.io/managed-by=openhands-operator";
  public static final Map<String, String> SELECTOR_LABEL = Map.of("app.kubernetes.io/managed-by",
      "openhands-operator");

  private final PrimaryResourceValidator<LLMTaskResource> llmTaskValidator;

  public LLMTaskReconciler(final PrimaryResourceValidator<LLMTaskResource> llmTaskValidator) {
    this.llmTaskValidator = llmTaskValidator;
  }

  @Override
  public UpdateControl<LLMTaskResource> reconcile(final LLMTaskResource resource,
      final Context<LLMTaskResource> context) {
    LOGGER.info("Reconciling LLMTask {}", resource.getMetadata().getName());
    final var failureOpt = llmTaskValidator.validateOrErrorStatus(resource, context);
    if (failureOpt.isPresent()) {
      // Model does not exist
      return UpdateControl.patchStatus(failureOpt.get());
    }

    // Model exists, reconcile dependent resources before we continue.
    context.managedWorkflowAndDependentResourceContext().reconcileManagedWorkflow();

    final var podResource = context.getSecondaryResource(Pod.class).orElseThrow();

    final var previousState = fromTask(resource);
    final var state = fromPod(podResource);
    if (previousState == state) {
      LOGGER.debug("LLMTask {}: no state change detected (current={})",
          resource.getMetadata().getName(), state);
      return UpdateControl.noUpdate();
    }

    LOGGER.info("Changing state of LLMTask {} from {} to {}", resource.getMetadata().getName(),
        previousState, state);

    final var patched = new LLMTaskResource();
    patched.setMetadata(new ObjectMetaBuilder().withName(resource.getMetadata().getName())
                                               .withNamespace(resource.getMetadata().getNamespace())
                                               .build());

    final var status = new LLMTaskStatus();
    status.setState(state);
    patched.setStatus(status);

    return UpdateControl.patchStatus(patched);
  }

  private static LLMTaskStatus.State fromTask(final LLMTaskResource task) {
    return task.getStatus() != null ? task.getStatus().getState() : null;
  }

  private static LLMTaskStatus.State fromPod(final Pod pod) {
    final var status = pod.getStatus();
    if (status == null) {
      return LLMTaskStatus.State.QUEUED;
    }

    return switch (status.getPhase()) {
      case "Pending" -> LLMTaskStatus.State.QUEUED;
      case "Running" -> LLMTaskStatus.State.RUNNING;
      case "Failed", "Unknown" -> LLMTaskStatus.State.FAILED;
      case "Succeeded" -> LLMTaskStatus.State.SUCCEEDED;
      case null, default -> throw new IllegalStateException("Unknown pod status: " + status);
    };
  }

  @Override
  public DeleteControl cleanup(final LLMTaskResource resource,
      final Context<LLMTaskResource> context) {
    // We need to explicitely cleanup since "explicitInvocation = true"
    context.managedWorkflowAndDependentResourceContext().cleanupManageWorkflow();
    return DeleteControl.defaultDelete();
  }
}