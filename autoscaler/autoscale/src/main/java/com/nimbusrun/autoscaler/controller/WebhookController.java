package com.nimbusrun.autoscaler.controller;

import com.nimbusrun.autoscaler.autoscaler.Autoscaler;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Profile("web")
@RestController
@RequestMapping("/webhook")
@Slf4j
public class WebhookController {

  private final Autoscaler autoscaler;
  public WebhookController(Autoscaler autoscaler) {
    this.autoscaler = autoscaler;
  }

  @PostMapping()
  public ResponseEntity<Object> webhook(@RequestBody String payload, @RequestHeader(WebhookVerifier.SECRET_HEADER) String signature) {
    try {
      JSONObject json = new JSONObject(payload);
      log.debug("Recieved: \n" + payload.replace("\n","\n\t"));

      if(WebhookVerifier.verifySignature(payload.getBytes(),"test", signature)){

        return new ResponseEntity<>(HttpStatusCode.valueOf(201));
      }
      if (!json.has("workflow_job")) {
        return new ResponseEntity<>(HttpStatusCode.valueOf(201));
      }
      autoscaler.receive(json);
    }catch (Exception e){
      log.debug("problem in the webhook",e);
    }
    return new ResponseEntity<>(HttpStatusCode.valueOf(201));
  }
}