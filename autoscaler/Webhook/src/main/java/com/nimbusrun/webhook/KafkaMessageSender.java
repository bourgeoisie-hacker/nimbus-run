package com.nimbusrun.webhook;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.Properties;
import java.util.concurrent.Future;


public class KafkaMessageSender {

    public static void main(String[] args) {
        String bootstrapServers = "localhost:9092"; // Update with your Kafka broker(s)
        String topic = "test-topic";
        String key = "example-key";
        String value = "Hello from Java Kafka Producer!";

        // Configure producer properties
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");

        // Create the Kafka producer
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {

            // Create a producer record
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);

            // Send the message asynchronously
            Future<RecordMetadata> future = producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    System.err.println("Error while producing: " + exception.getMessage());
                } else {
                    System.out.printf("Message sent to topic=%s, partition=%d, offset=%d%n",
                            metadata.topic(), metadata.partition(), metadata.offset());
                }
            });

            // Optional: Wait for the send to complete (sync mode)
            future.get();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
