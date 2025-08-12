package com.nimbusrun.actiontracker.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.util.concurrent.Future;

@Service
@Slf4j
public class KafkaService {
    private final String retryTopic;
    public KafkaService(@Value("${kafka.retryTopic}") String retryTopic){
        this.retryTopic = retryTopic;
    }
    /**
     * Receives messages from the Kafka topic.
     * This method is called automatically by Spring Kafka when a message is received on the specified topic.
     *
     * @param message The message received from Kafka
     */
    @KafkaListener(topics = "${kafka.topic}", groupId = "${kafka.consumerGroupId}")
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

    public void sendRetry(String payload){

        String key = "moreMeh";

        // Configure producer properties
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, retryTopic);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");

        // Create the Kafka producer
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {

            // Create a producer record
            ProducerRecord<String, String> record = new ProducerRecord<>(retryTopic, key, payload);

            // Send the message asynchronously
            Future<RecordMetadata> future = producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("Error while producing: " + exception.getMessage());
                    //TODO create counter here to track errors
                } else {
                    log.debug("Message sent to topic=%s, partition=%d, offset=%d%n",
                            metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
            future.get();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}