package com.nimbusrun.actiontracker.service;

import com.nimbusrun.Utils;
import com.nimbusrun.github.GithubActionJob;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KafkaReceiver {
    private final TrackService trackService;
    public KafkaReceiver(TrackService trackService){
        this.trackService = trackService;
    }
    /**
     * Receives messages from the Kafka topic.
     * This method is called automatically by Spring Kafka when a message is received on the specified topic.
     *
     * @param message The message received from Kafka
     */
    @KafkaListener(topics = "${kafka.webhookTopic}", groupId = "${kafka.consumerGroupId}")
    public void receive(String message) {
        try {
            log.info("Received message: {}", message);
            
            // Parse the message as JSON
            JSONObject json = new JSONObject(message);
            
            // Check if this is a workflow job or workflow run event
            if (json.has("workflow_job")) {
                processWorkflowJob(json);
            } else if (json.has("workflow_run")) {
                //Nada
            } else {
                log.warn("Received message is not a workflow job or workflow run event");
            }
        } catch (Exception e) {
            Utils.excessiveErrorLog("Error processing Kafka message", e, log);
            log.debug("Error processing Kafka message: " + message);
        }
    }
    
    /**
     * Process a workflow job event
     * 
     * @param json The JSON object containing the workflow job event
     */
    private void processWorkflowJob(JSONObject json) {
        GithubActionJob gj = GithubActionJob.fromJson(json);
        
        log.debug("Workflow Job Received: id={}, name={}, status={}, conclusion={}",
                gj.getId(), gj.getName(), gj.getStatus().getStatus(), gj.getConclusion());
        
        this.trackService.receiveJob(gj);
    }

}