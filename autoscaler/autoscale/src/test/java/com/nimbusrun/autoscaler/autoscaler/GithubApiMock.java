package com.nimbusrun.autoscaler.autoscaler;

import com.nimbusrun.autoscaler.github.GithubServiceApi;
import com.nimbusrun.autoscaler.github.orm.listDelivery.DeliveryRecord;
import com.nimbusrun.autoscaler.github.orm.runner.Runner;
import com.nimbusrun.autoscaler.github.orm.runnergroup.ListRunnerGroup;
import com.nimbusrun.compute.ListInstanceResponse.Instance;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apache.hc.core5.http.ProtocolException;

public class GithubApiMock implements GithubServiceApi {

  private final String runnerGroupName;
  private final Integer runnerGroupId;
  private final ComputeMock computeMock;
  private final Set<String> deleteItems = new ConcurrentSkipListSet<>();
  public GithubApiMock(
      String runnerGroupName, ComputeMock computeMock) {
    this.computeMock = computeMock;
    this.runnerGroupName = runnerGroupName;
    this.runnerGroupId = 1;
  }


  @Override
  public Optional<String> generateRunnerToken() {
    return Optional.empty();
  }

  @Override
  public String getOrganization() {
    return "";
  }

  @Override
  public Integer getRunnerGroupId() {
    return this.runnerGroupId;
  }

  @Override
  public String getRunnerGroupName() {
    return this.runnerGroupName;
  }

  @Override
  public String getWebhookId() {
    return null;
  }

  @Override
  public boolean isReplayFailedDeliverOnStartup() {
    return false;
  }

  @Override
  public List<ListRunnerGroup> fetchRunnerGroups() {
    return List.of();
  }

  @Override
  public boolean isJobQueued(String runUrl) {
    return false;
  }

  @Override
  public List<Runner> listRunnersInGroup() {
    List<Runner> runners = computeMock.listAllComputeInstances().values().stream().flatMap(i->i.instances().stream())
        .map(Instance::getInstanceName).filter(i->!deleteItems.contains(i)).map(i-> new Runner(Objects.hash(i),i,"linux", "running", true, new ArrayList<>())).toList();
    return runners;
  }

  @Override
  public List<Runner> listRunnersInGroup(String groupId) {
    return List.of();
  }

  @Override
  public boolean deleteRunner(String runnerId) {
    return false;
  }

  @Override
  public List<DeliveryRecord> listDeliveries()
      throws ProtocolException, GeneralSecurityException, IOException {
    return List.of();
  }

  @Override
  public boolean reDeliveryFailures(String deliveryId) {
    return false;
  }

  public void addDeletedRunner(String runnerName){
    deleteItems.add(runnerName);
  }
}
