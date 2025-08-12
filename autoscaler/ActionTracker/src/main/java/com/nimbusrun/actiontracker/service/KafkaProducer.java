package com.nimbusrun.actiontracker.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.Future;

@Slf4j
@Component
public class KafkaProducer {

    private final String retryTopic;
    private final String broker;

    public KafkaProducer(@Value("${kafka.retryTopic}") String retryTopic, @Value("${kafka.broker}") String broker){
        this.retryTopic = retryTopic;
        this.broker = broker;
    }
    public void sendRetry(String payload){

        String key = "moreMeh";

        // Configure producer properties
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, this.broker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");

        // Create the Kafka producer
        try (org.apache.kafka.clients.producer.KafkaProducer<String, String> producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props)) {

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
