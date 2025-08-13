package com.nimbusrun.webhook;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.util.concurrent.*;

@Service
public class KafkaService {

    private static final Logger log = LoggerFactory.getLogger(KafkaService.class);

    public static void main(String[] args) {
        KafkaService ks = new KafkaService("test-topic", "localhost:9092");
        ks.send("");
    }
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final ThreadPoolExecutor tpe;
    private final String kafakaTopic;
    private final String kafakaBroker;

    public KafkaService(@Value("${kafka.webhookTopic}") String kafakaTopic, @Value("${kafka.broker}")String kafakaBroker){
        this.kafakaTopic = kafakaTopic;
        this.kafakaBroker = kafakaBroker;
        tpe = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        tpe.execute(this::send);
    }



    public void receive(String payload) {
        log.debug("Adding to Payload");
        queue.add(payload);
    }

    public void send () {
        while(true){
            try{
                String payload;
            while((payload = queue.poll()) != null){
                send(payload);
            }
            Thread.sleep(100);
            }catch (Exception e){
                log.error(e.getMessage());
            }
        }
    }

    public void send(String payload){

        String key = "meh";
        log.debug("%s sending payload: %s".formatted(kafakaTopic, payload));

        // Configure producer properties
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafakaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");

        // Create the Kafka producer
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {

            // Create a producer record
            ProducerRecord<String, String> record = new ProducerRecord<>(kafakaTopic, key, payload);

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
