package com.nimbusrun.webhook;

import com.google.common.annotations.VisibleForTesting;
import com.nimbusrun.Utils;
import com.nimbusrun.github.GithubActionJob;
import com.nimbusrun.github.GithubActionRun;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WebhookObservable {

  private final BlockingDeque<GithubActionJob> githubActionJobs = new LinkedBlockingDeque<>();
  private final BlockingDeque<GithubActionRun> githubActionRuns = new LinkedBlockingDeque<>();
  private final ApplicationContext context;
  private final ExecutorService webhookProcessors = Executors.newFixedThreadPool(2);

  public WebhookObservable(ApplicationContext context) {
    this.context = context;
    webhookProcessors.execute(this::processGithubJobs);
    webhookProcessors.execute(this::processGithubRuns);
  }

  @VisibleForTesting
  private boolean processGithubJobs() {
    while (true) {
      try {
        GithubActionJob gj;
        while ((gj = this.githubActionJobs.poll(1, TimeUnit.MINUTES)) != null) {
          log.debug("Evaluating Github Action Workflow Job for further processing %s".formatted(
              gj.simpleDescription()));
          for (WebhookReceiver receiver : context.getBeansOfType(WebhookReceiver.class).values()) {
            try {
              receiver.receive(gj);
            } catch (Exception e) {
              log.error("Failed to to send github job payload to receiver %s".formatted(
                  receiver.receiverName()), e);
            }
          }
        }
      } catch (Exception e) {
        log.error("Failed to to send github job payload to all receivers");
      }
    }
  }
  @VisibleForTesting
  private boolean processGithubRuns() {
    while (true) {
      try {
        GithubActionRun gr;
        while ((gr = this.githubActionRuns.poll(1, TimeUnit.MINUTES)) != null) {
          log.debug("Evaluating Github Action Workflow Job for further processing %s".formatted(
              gr.simpleDescription()));
          for (WebhookReceiver receiver : context.getBeansOfType(WebhookReceiver.class).values()) {
            try {
              receiver.receive(gr);
            } catch (Exception e) {
              log.error("Failed to to send github run payload to receiver %s".formatted(
                  receiver.receiverName()), e);
            }
          }
        }
      } catch (Exception e) {
        log.error("Failed to to send github run payload to all receivers");
      }
    }
  }

  /**
   * Pass off the load to a list for later processing so that the http connection with github can be
   * closed as soon as possible
   *
   * @param message
   */
  public void receive(String message) {
    try {
      // Parse the message as JSON
      JSONObject json = new JSONObject(message);

      // Check if this is a workflow job or workflow run event
      if (json.has("workflow_job")) {
        githubActionJobs.offer(GithubActionJob.fromJson(json));
      } else if (json.has("workflow_run")) {
        githubActionRuns.offer(GithubActionRun.fromJson(json));
      } else {
        log.warn("Received message is not a workflow job or workflow run event");
      }
    } catch (Exception e) {
      Utils.excessiveErrorLog("Error processing message", e, log);
    }
  }
}
