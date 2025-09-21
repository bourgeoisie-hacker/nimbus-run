package com.nimbusrun.autoscaler.github;

import com.nimbusrun.autoscaler.github.orm.listDelivery.DeliveryRecord;
import com.nimbusrun.autoscaler.github.orm.runner.Runner;
import com.nimbusrun.autoscaler.github.orm.runnergroup.ListRunnerGroup;
import com.nimbusrun.compute.GithubApi;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;
import org.apache.hc.core5.http.ProtocolException;

public interface GithubServiceApi extends GithubApi {

  // --- Lombok @Getter fields become public methods ---
  String getOrganization();
  Integer getRunnerGroupId();
  String getRunnerGroupName();
  String getWebhookId();
  boolean isReplayFailedDeliverOnStartup();

  // --- Public methods declared in the class ---
  List<ListRunnerGroup> fetchRunnerGroups();

  Optional<String> generateRunnerToken();

  // From GithubApi (declared here for completeness)
  boolean isJobQueued(String runUrl);

  List<Runner> listRunnersInGroup();

  List<Runner> listRunnersInGroup(String groupId);

  boolean deleteRunner(String runnerId);

  List<DeliveryRecord> listDeliveries()
      throws ProtocolException, GeneralSecurityException, IOException;

  boolean reDeliveryFailures(String deliveryId);
}
