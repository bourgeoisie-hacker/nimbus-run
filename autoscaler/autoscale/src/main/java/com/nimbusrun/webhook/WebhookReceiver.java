package com.nimbusrun.webhook;

import com.nimbusrun.github.GithubActionJob;

public interface WebhookReceiver {
    public boolean receive(GithubActionJob githubActionJob);
    default String receiverName(){
        return this.getClass().getSimpleName();
    }
}
