package com.nimbusrun.webhook;

import com.nimbusrun.github.GithubActionJob;
import com.nimbusrun.github.GithubActionRun;

public interface WebhookReceiver {

  boolean receive(GithubActionJob githubActionJob);

  default String receiverName() {
    return this.getClass().getSimpleName();
  }

 default boolean receive(GithubActionRun githubActionRun){
    return false;
 }
}
