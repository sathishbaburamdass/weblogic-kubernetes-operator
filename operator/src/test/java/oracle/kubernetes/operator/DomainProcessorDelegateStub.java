// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.VersionInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.jetbrains.annotations.NotNull;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.JobWatcher.getFailedReason;
import static oracle.kubernetes.operator.JobWatcher.isFailed;
import static oracle.kubernetes.operator.ProcessingConstants.DELEGATE_COMPONENT_NAME;

/**
 * A test stub for processing domains in unit tests.
 */
public abstract class DomainProcessorDelegateStub implements DomainProcessorDelegate {
  public static final VersionInfo TEST_VERSION_INFO = new VersionInfo().major("1").minor("18").gitVersion("0");
  public static final KubernetesVersion TEST_VERSION = new KubernetesVersion(TEST_VERSION_INFO);

  private final FiberTestSupport testSupport;
  private boolean waitedForIntrospection;
  private boolean mayRetry = false;

  public DomainProcessorDelegateStub(FiberTestSupport testSupport) {
    this.testSupport = testSupport;
  }

  public static DomainProcessorDelegateStub createDelegate(KubernetesTestSupport testSupport) {
    return createStrictStub(DomainProcessorDelegateStub.class, testSupport);
  }

  public void setMayRetry(boolean mayRetry) {
    this.mayRetry = mayRetry;
  }

  public boolean waitedForIntrospection() {
    return waitedForIntrospection;
  }

  @Override
  public boolean isNamespaceRunning(String namespace) {
    return true;
  }

  @Override
  public PodAwaiterStepFactory getPodAwaiterStepFactory(String namespace) {
    return new PassthroughPodAwaiterStepFactory();
  }

  @Override
  public JobAwaiterStepFactory getJobAwaiterStepFactory(String namespace) {
    return new TestJobAwaiterStepFactory();
  }

  @Override
  public KubernetesVersion getKubernetesVersion() {
    return TEST_VERSION;
  }

  @Override
  public FiberGate createFiberGate() {
    return testSupport.createFiberGate();
  }

  @Override
  public boolean mayRetry(@NotNull DomainPresenceInfo domainPresenceInfo) {
    return mayRetry;
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable command, long initialDelay, long delay, TimeUnit unit) {
    return testSupport.scheduleWithFixedDelay(command, initialDelay, delay, unit);
  }

  @Override
  public void runSteps(Step firstStep) {
    testSupport.runSteps(firstStep);
  }

  @Override
  public void runSteps(Packet packet, Step firstStep, Runnable completionCallback) {
    testSupport.runSteps(packet, firstStep);
  }

  @Override
  public void addToPacket(Packet packet) {
    packet.getComponents().put(DELEGATE_COMPONENT_NAME, Component.createFor(CoreDelegate.class, this));
  }

  @Override
  public File getDeploymentHome() {
    return new File("/deployment");
  }

  private static class PassthroughPodAwaiterStepFactory implements PodAwaiterStepFactory {
    @Override
    public Step waitForReady(V1Pod pod, Step next) {
      return next;
    }

    @Override
    public Step waitForReady(String podName, Step next) {
      return next;
    }

    @Override
    public Step waitForDelete(V1Pod pod, Step next) {
      return next;
    }
  }

  private class TestJobAwaiterStepFactory implements JobAwaiterStepFactory {
    @Override
    public Step waitForReady(V1Job job, Step next) {
      if (isFailed(job) && "DeadlineExceeded".equals(getFailedReason(job))) {
        return new Step() {
          @Override
          public oracle.kubernetes.operator.work.NextAction apply(Packet packet) {
            return doTerminate(new JobWatcher.DeadlineExceededException(job), packet);
          }
        };
      } else {
        waitedForIntrospection = true;
        return next;
      }
    }
  }
}
