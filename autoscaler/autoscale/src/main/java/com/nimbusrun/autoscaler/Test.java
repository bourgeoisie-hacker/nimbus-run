package com.nimbusrun.autoscaler;

import com.google.cloud.compute.v1.Region;
import com.google.cloud.compute.v1.RegionsClient;
import com.google.cloud.compute.v1.Zone;
import com.google.cloud.compute.v1.ZonesClient;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Future;

@Slf4j
public class Test {
    public static void main(String[] args) throws IOException {
        Map<String,String> map = new HashMap<>();
        map.put("blah", null);
        System.out.println(map.containsKey("blah"));
//        listRegions("massive-dynamo-342018");
        String payload = Files.readString(Paths.get("webhooks-examples/test-workflow-job-queued.json"));
        sendRetry(payload, "webhook");
    }

    public static void listRegions(String projectId) throws IOException {

        try (ZonesClient zonesClient = ZonesClient.create()) {
            for (Zone zone : zonesClient.list(projectId).iterateAll()) {
                String regionName = zone.getRegion().substring(zone.getRegion().lastIndexOf("/")+1);
                System.out.printf("Region: %s, Zone: %s (status: %s)%n", regionName, zone.getName() , zone.getStatus());
            }
        }
    }

    public static void sendRetry(String payload, String topic){
        String bootstrapServers = "localhost:9092"; // Update with your Kafka broker(s)
        String key = "moreMeh";

        // Configure producer properties
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");

        // Create the Kafka producer
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {

            // Create a producer record
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);

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
