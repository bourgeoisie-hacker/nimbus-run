package com.nimbusrun.autoscaler;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Slf4j
public class Test {
    public static void main(String[] args) throws IOException {
        Map<String,String> map = new HashMap<>();
        map.put("blah", null);
        System.out.println(map.containsKey("blah"));
//        listRegions("massive-dynamo-342018");
        String payload = Files.readString(Paths.get("webhooks-examples/test-workflow-job-queued-gcp.json"));
        System.out.println("al da-adsf5".matches("^[a-z0-9-]+$"));
        List<String> ff = new ArrayList<>();
        System.out.println(ff.stream().noneMatch(fff-> false));

        LoadingCache<Integer,Integer> loadingCache = Caffeine.newBuilder().maximumSize(20).expireAfterWrite(1, TimeUnit.SECONDS).build(key->new Random().nextInt());
        Cache<Integer,Integer> cache = Caffeine.newBuilder().maximumSize(20).expireAfterWrite(1, TimeUnit.HOURS).build();
        System.out.println(loadingCache.get(4));
        System.out.println(loadingCache.get(4));
        System.out.println(cache.get(4,key->new Random().nextInt()));
        System.out.println(cache.get(4,key->new Random().nextInt()));

        sendRetry(payload.replace("\n",""), "webhook");
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
