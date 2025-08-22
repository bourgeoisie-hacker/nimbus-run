package com.nimbusrun.autoscaler.kafka;
import com.nimbusrun.Constants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Profile("!"+ Constants.STANDALONE_PROFILE_NAME)
@Slf4j
@Component
public class KafkaTopicCreator {
    private String webhookTopic;
    private String retryTopic;
    private boolean shouldCreateTopics;
    private String broker;
    public KafkaTopicCreator(@Value("${kafka.webhookTopic}") String webhookTopic,
                             @Value("${kafka.broker}") String broker,
                             @Value("${kafka.retryTopic}")String retryTopic,
                             @Value("${kafka.shouldCreateTopics:false}") boolean shouldCreateTopics){
        this.webhookTopic = webhookTopic;
        this.retryTopic = retryTopic;
        this.shouldCreateTopics = shouldCreateTopics;
        this.broker = broker;
    }

    @PostConstruct
    public void tryCreateTopic(){
        if(shouldCreateTopics){
            if(!topicExists(broker, webhookTopic)){
                create(broker, webhookTopic);
            }
            if(!topicExists(broker, retryTopic)){
                create(broker, retryTopic);
            }
        }
    }

    public static boolean topicExists(String bootstrapServers, String topicName) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (AdminClient adminClient = AdminClient.create(props)) {
            Set<String> topicNames = adminClient.listTopics().names().get();
            return topicNames.contains(topicName);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to check topic existence", e);
            System.exit(1);
        }
        return false;
    }

    public static void create(String bootstrapServers, String topicName){
        int numPartitions = 2;
        short replicationFactor = 1; // Should not exceed your broker count

        // Admin client config
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (AdminClient adminClient = AdminClient.create(props)) {
            NewTopic newTopic = new NewTopic(topicName, numPartitions, replicationFactor);
            adminClient.createTopics(Collections.singleton(newTopic)).all().get();
            log.info("Topic created: " + topicName);
        } catch (Exception  e) {
            log.error("Failed to create Kafka Topic",e);
            System.exit(1);
        }
    }


}
