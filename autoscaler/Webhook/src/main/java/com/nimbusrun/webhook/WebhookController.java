package com.nimbusrun.webhook;

import com.nimbusrun.github.WebhookVerifier;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("webhook")
@Slf4j
public class WebhookController {

  private final KafkaService kakfaService;
  private final String webhookSecret;

  public WebhookController(KafkaService kafkaService, @Value("${github.webhookSecret:#{null}}") String webhookSecret) {
    this.kakfaService = kafkaService;
    this.webhookSecret = webhookSecret;
  }

  @PostMapping()
  public ResponseEntity<Object> webhook(@RequestBody String payload, @RequestHeader(value = WebhookVerifier.SECRET_HEADER, required = false) String signature) {
    try {
      JSONObject json = new JSONObject(payload);
      log.debug("Recieved: \n" + payload.replace("\n","\n\t"));

      if(webhookSecret != null && !WebhookVerifier.verifySignature(payload.getBytes(), webhookSecret, signature)){
        log.debug("Failed to verified webhook "+ payload);
        return new ResponseEntity<>(HttpStatusCode.valueOf(201));
      }
      if (!json.has("workflow_job") && !json.has("workflow_run")) {
        log.debug("Doesn't have workflow_(job|run) "+ payload);

        return new ResponseEntity<>(HttpStatusCode.valueOf(201));
      }
      kakfaService.receive(payload);
    }catch (Exception e){
      log.debug("problem in the webhook",e);
    }
    return new ResponseEntity<>(HttpStatusCode.valueOf(201));
  }
}