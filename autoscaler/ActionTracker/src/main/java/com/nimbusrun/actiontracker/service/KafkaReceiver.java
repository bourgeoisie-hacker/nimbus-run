package com.nimbusrun.actiontracker.service;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KafkaReceiver {

    /**
     * Receives messages from the Kafka topic.
     * This method is called automatically by Spring Kafka when a message is received on the specified topic.
     *
     * @param message The message received from Kafka
     */
    @KafkaListener(topics = "${kafka.topic}", groupId = "${kafka.group-id}")
    public void receive(String message) {
        try {
            log.info("Received message: {}", message);
            
            // Parse the message as JSON
            JSONObject json = new JSONObject(message);
            
            // Check if this is a workflow job or workflow run event
            if (json.has("workflow_job")) {
                processWorkflowJob(json);
            } else if (json.has("workflow_run")) {
                processWorkflowRun(json);
            } else {
                log.warn("Received message is not a workflow job or workflow run event");
            }
        } catch (Exception e) {
            log.error("Error processing Kafka message", e);
        }
    }
    
    /**
     * Process a workflow job event
     * 
     * @param json The JSON object containing the workflow job event
     */
    private void processWorkflowJob(JSONObject json) {
        JSONObject workflowJob = json.getJSONObject("workflow_job");
        String status = workflowJob.getString("status");
        String conclusion = workflowJob.optString("conclusion", null);
        String name = workflowJob.getString("name");
        long id = workflowJob.getLong("id");
        
        log.info("Workflow Job: id={}, name={}, status={}, conclusion={}", 
                id, name, status, conclusion);
        
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

    // store github workflow job/run in redis
    // check status of job if its running
    // if job hasn't started in x time then send a retry to kafka queue. I should make another queue for that
}