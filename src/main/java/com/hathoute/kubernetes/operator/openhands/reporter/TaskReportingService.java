package com.hathoute.kubernetes.operator.openhands.reporter;

import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import com.hathoute.kubernetes.operator.openhands.reporter.TaskReportingRoutine.State;
import com.hathoute.kubernetes.operator.openhands.resource.LLMTaskServiceResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.inbound.SimpleInboundEventSource;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static com.hathoute.kubernetes.operator.openhands.config.OpenHandsConfig.OPENHANDS_RUNTIME_REPORTER_PORT;

@Service
public class TaskReportingService {
  private static final Logger LOGGER = LoggerFactory.getLogger(TaskReportingService.class);

  private final Map<ResourceID, TaskReportingRoutine> watchedTasks = new HashMap<>();
  private final Map<ResourceID, TaskReportingRoutine> stoppedTasks = new HashMap<>();
  private final Map<ResourceID, CompletableFuture<Void>> routineFutures = new HashMap<>();
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  private final SimpleInboundEventSource<LLMTaskResource> eventSource;
  private final Supplier<Instant> startTime;

  public TaskReportingService(final SimpleInboundEventSource<LLMTaskResource> eventSource,
      final Supplier<Instant> startTime) {
    this.eventSource = eventSource;
    this.startTime = startTime;
  }

  public synchronized void addTask(final LLMTaskResource task) {
    final var meta = task.getMetadata();
    final var taskId = new ResourceID(meta.getName(), meta.getNamespace());
    if (watchedTasks.containsKey(taskId) || stoppedTasks.containsKey(taskId)) {
      // Ignore already existing
      return;
    }

    LOGGER.info("Adding task {} to watched tasks", taskId);
    final var serviceUrl = serviceUrl(taskId);
    final var client = new TaskReporterClient(serviceUrl);
    final var routine = new TaskReportingRoutine(taskId, client, eventSource, startTime);
    watchedTasks.put(taskId, routine);
    routineFutures.put(taskId, CompletableFuture.completedFuture(null));
  }

  public synchronized void removeTask(final LLMTaskResource task) {
    final var meta = task.getMetadata();
    final var taskId = new ResourceID(meta.getName(), meta.getNamespace());
    if (!watchedTasks.containsKey(taskId) && !stoppedTasks.containsKey(taskId)) {
      LOGGER.warn("Requesting to unwatch a non watched task '{}'", task);
      return;
    }

    LOGGER.info("Removing task {} from watched tasks", taskId);

    watchedTasks.remove(taskId);
    stoppedTasks.remove(taskId);
    routineFutures.remove(taskId);
  }

  public synchronized TaskReportingRoutine getRoutine(final LLMTaskResource task) {
    final var meta = task.getMetadata();
    final var taskId = new ResourceID(meta.getName(), meta.getNamespace());
    if (watchedTasks.containsKey(taskId)) {
      return watchedTasks.get(taskId);
    }
    if (stoppedTasks.containsKey(taskId)) {
      return stoppedTasks.get(taskId);
    }

    throw new NoSuchElementException("No TaskReportingRoutine of task '%s'".formatted(task));
  }

  @Scheduled(fixedRate = 2, timeUnit = TimeUnit.SECONDS)
  synchronized void callRoutines() {
    LOGGER.debug("Starting scheduled task reporting routines for {} watched tasks",
        watchedTasks.size());

    final var stoppedTaskIds = new HashSet<ResourceID>();
    for (final var entry : this.watchedTasks.entrySet()) {
      final var taskId = entry.getKey();
      final var routine = entry.getValue();

      if (skipRoutine(taskId, routine, stoppedTaskIds)) {
        continue;
      }

      LOGGER.debug("Starting a new task reporting routine for '{}'", taskId);
      final var completable = CompletableFuture.runAsync(routine, executor);
      routineFutures.put(taskId, completable);
    }

    if (stoppedTaskIds.isEmpty()) {
      return;
    }

    LOGGER.debug("{} task reporting routines are marked as stopped", stoppedTasks.size());
    for (final var taskId : stoppedTaskIds) {
      stoppedTasks.put(taskId, watchedTasks.remove(taskId));
    }
  }

  private boolean skipRoutine(final ResourceID taskId, final TaskReportingRoutine routine,
      final Set<ResourceID> stoppedTasks) {
    final var previousRun = routineFutures.get(taskId);
    if (!previousRun.isDone()) {
      LOGGER.debug("Reporting routine for task '{}' is still running, skipping...", taskId);
      return true;
    }

    if (previousRun.isCompletedExceptionally()) {
      LOGGER.warn("Previous reporting routine for task '{}' completed with exception:", taskId,
          previousRun.exceptionNow());
    }

    if (routine.getCurrentState() == State.STOPPED) {
      LOGGER.info("Reporting routine for task '{}' is marked as stopped, will stop watching it",
          taskId);
      handleStoppedTask(taskId);
      stoppedTasks.add(taskId);
      return true;
    }
    return false;
  }

  private void handleStoppedTask(final ResourceID taskId) {
    eventSource.propagateEvent(taskId);
  }

  private static String serviceUrl(final ResourceID taskName) {
    final var serviceName = LLMTaskServiceResource.resourceName(taskName.getName());
    final var serviceNamespace = taskName.getNamespace()
                                         .orElseThrow(() -> new IllegalStateException(
                                             "Expecting ResourceId '%s' to have a namespace".formatted(
                                                 taskName)));
    //noinspection HttpUrlsUsage
    return "http://%s.%s.svc.cluster.local:%d".formatted(serviceName, serviceNamespace,
        OPENHANDS_RUNTIME_REPORTER_PORT);
  }
}
