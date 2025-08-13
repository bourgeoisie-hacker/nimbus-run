package com.nimbusrun.autoscaler.kafka;

import com.nimbusrun.Constants;
import com.nimbusrun.autoscaler.autoscaler.Autoscaler;
import com.nimbusrun.github.GithubActionJob;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("!"+Constants.STANDALONE_PROFILE_NAME)
public class KafkaReceiver {

    private final Autoscaler autoscaler;

    public KafkaReceiver(Autoscaler autoscaler){
        this.autoscaler = autoscaler;
    }
    /**
     * Receives messages from the Kafka topic.
     * This method is called automatically by Spring Kafka when a message is received on the specified topic.
     *
     * @param message The message received from Kafka
     */
    @KafkaListener(topics = "${kafka.webhookTopic}", groupId = "${kafka.consumerGroupId}")
    public void receiveWebhook(String message) {
        try {
            log.info("Received message: {}", message);
            
            // Parse the message as JSON
            JSONObject json = new JSONObject(message);
            
            // Check if this is a workflow job or workflow run event
            if (json.has("workflow_job")) {
                GithubActionJob gj = GithubActionJob.fromJson(json);
                log.debug("Workflow Job: id={}, name={}, status={}, conclusion={}",
                        gj.getId(), gj.getName(), gj.getStatus().getStatus(), gj.getConclusion());
                autoscaler.receive(gj);
            } else if (json.has("workflow_run")) {
                processWorkflowRun(json);
            } else {
                log.warn("Received message is not a workflow job or workflow run event");
            }
        } catch (Exception e) {
            log.error("Error processing Kafka message for:\n %s\n".formatted(message.indent(4)), e);
        }
    }/**
     * Receives messages from the Kafka topic.
     * This method is called automatically by Spring Kafka when a message is received on the specified topic.
     *
     * @param message The message received from Kafka
     */
    @KafkaListener(topics = "${kafka.retryTopic}", groupId = "${kafka.consumerGroupId}")
    public void receiveRetry(String message) {
        try {
            log.info("Received message: {}", message);

            // Parse the message as JSON
            JSONObject json = new JSONObject(message);

            // Check if this is a workflow job or workflow run event
            if (json.has("workflow_job")) {
                GithubActionJob gj = GithubActionJob.fromJson(json);

                log.debug("Retry Workflow Job: id={}, name={}, status={}, conclusion={}",
                        gj.getId(), gj.getName(), gj.getStatus().getStatus(), gj.getConclusion());
                autoscaler.receiveRetry(gj);
            } else if (json.has("workflow_run")) {
                processWorkflowRun(json);
            } else {
                log.warn("Received message is not a workflow job or workflow run event");
            }
        } catch (Exception e) {
            log.error("Error processing Kafka message for:\n %s\n".formatted(message.indent(4)), e);
        }
    }
    
    /**
     * Process a workflow job event
     * 
     * @param json The JSON object containing the workflow job event
     */
    private void processWorkflowJob(JSONObject json) {

        // TODO: Implement tracking logic for workflow jobs
    }
    
    /**
     * Process a workflow run event
     * 
     * @param json The JSON object containing the workflow run event
     */
    private void processWorkflowRun(JSONObject json) {
        JSONObject workflowRun = json.getJSONObject("workflow_run");
        String status = workflowRun.getString("status");
        String conclusion = workflowRun.optString("conclusion", null);
        String name = workflowRun.getString("name");
        long id = workflowRun.getLong("id");
        
        log.info("Workflow Run: id={}, name={}, status={}, conclusion={}", 
                id, name, status, conclusion);
        
        // TODO: Implement tracking logic for workflow runs
    }

}