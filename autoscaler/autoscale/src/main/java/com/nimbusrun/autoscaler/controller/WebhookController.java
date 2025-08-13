package com.nimbusrun.autoscaler.controller;

import com.nimbusrun.Constants;
import com.nimbusrun.Utils;
import com.nimbusrun.autoscaler.autoscaler.Autoscaler;
import com.nimbusrun.github.GithubActionJob;
import com.nimbusrun.github.WebhookVerifier;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Profile(Constants.STANDALONE_PROFILE_NAME)
@RestController
@RequestMapping("/webhook")
@Slf4j
public class WebhookController {

  private final Autoscaler autoscaler;
  private final String secret;
  public WebhookController(Autoscaler autoscaler, @Value("${github.webhookSecret:#{null}}") String secret) {
    this.secret = secret;
    this.autoscaler = autoscaler;
  }

  @PostMapping()
  public ResponseEntity<Object> webhook(@RequestBody String payload, @RequestHeader(value = WebhookVerifier.SECRET_HEADER, required = false) String signature) {
    try {
      JSONObject json = new JSONObject(payload);
      log.debug("Recieved: \n" + payload.replace("\n","\n\t"));

      if(secret != null && !WebhookVerifier.verifySignature(payload.getBytes(),secret, signature)){

        return new ResponseEntity<>(HttpStatusCode.valueOf(201));
      }
      if (!json.has("workflow_job")) {
        return new ResponseEntity<>(HttpStatusCode.valueOf(201));
      }

      GithubActionJob githubActionJob = GithubActionJob.fromJson(json);
      autoscaler.receive(githubActionJob);
    }catch (Exception e){
      Utils.excessiveErrorLog("Problem in the webhook", e,log);
    }
    return new ResponseEntity<>(HttpStatusCode.valueOf(201));
  }
}