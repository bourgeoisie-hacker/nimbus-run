package com.nimbusrun.webhook;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/webhook")
@Slf4j
public class WebhookController {

  private final KafkaService kakfaService;

  public WebhookController(KafkaService kafkaService) {
    this.kakfaService =kafkaService;
  }

  @PostMapping()
  public ResponseEntity<Object> webhook(@RequestBody String payload, @RequestHeader(WebhookVerifier.SECRET_HEADER) String signature) {
    try {
      JSONObject json = new JSONObject(payload);
      log.debug("Recieved: \n" + payload.replace("\n","\n\t"));

      if(WebhookVerifier.verifySignature(payload.getBytes(),"test", signature)){

        return new ResponseEntity<>(HttpStatusCode.valueOf(201));
      }
      if (!json.has("workflow_job") && !json.has("workflow_run")) {
        return new ResponseEntity<>(HttpStatusCode.valueOf(201));
      }
      kakfaService.receive(payload);
    }catch (Exception e){
      log.debug("problem in the webhook",e);
    }
    return new ResponseEntity<>(HttpStatusCode.valueOf(201));
  }
}