package com.nimbusrun.actiontracker.config;

import com.nimbusrun.Utils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@Configuration
@Slf4j
public class ActionTrackerConfigReader {
    public static final String NIMBUS_RUN_CONFIGURATION_FILE_ENV = "NIMBUS_RUN_CONFIGURATION_FILE";
    public static final String KAFKA_KEY = "kafka";
    public static final String GITHUB_KEY = "github";
    public static final String GITHUB_GROUP_NAME_KEY = "groupName";
    public static final String KAFKA_BROKER_KEY = "broker";
    public static final String NAME_KEY = "name";
    public static final String LOG_LEVEL_KEY = "logLevel";

    public static final String KAFKA_RETRY_TOPIC_KEY = "retryTopic";
    public static final String KAFKA_WEBHOOK_TOPIC_KEY = "webhookTopic";
    public static final String KAFKA_CONSUMER_GROUP_ID_KEY = "consumerGroupId";

    @Getter
    private final ActionTrackerConfig actionTrackerConfig;
    public ActionTrackerConfigReader() {
        try {
            this.actionTrackerConfig = readConfig();
        }catch (Exception e){
            Utils.excessiveErrorLog("Error setting up Configurations occurred due to %s.".formatted(e.getMessage()), e, log);
            System.exit(1);
            throw new RuntimeException();// unreachable but compiler shuts up
        }
    }

    public static ActionTrackerConfig readConfig() throws IOException {
        String configLocation = System.getenv(NIMBUS_RUN_CONFIGURATION_FILE_ENV);

        if(configLocation == null){
            log.error("Environment Variable %s not set".formatted(NIMBUS_RUN_CONFIGURATION_FILE_ENV));
            System.exit(1);
        }
        if(Files.notExists(Paths.get(configLocation)) || !Files.isRegularFile(Paths.get(configLocation))){
            log.error("Config file {} either does not exist or is not a regular file", configLocation);
            System.exit(1);
        }
        ActionTrackerConfig actionTrackerConfig = readConfig(configLocation);
        List<String> errors = validateBaseConfig(actionTrackerConfig);
        if(!errors.isEmpty()){
            errors.forEach(log::error);
            System.exit(1);
        }
        return actionTrackerConfig;
    }
    public static ActionTrackerConfig readConfig(String configPath) throws IOException {
        return createBaseConfig(Files.readString(Paths.get(configPath)));
    }

    public static void notNull(Supplier<Object> supplier, String name, Consumer<String> whatToDo){
        if(supplier.get() == null){
            whatToDo.accept(name);
        }
    }
    public static List<String> validateBaseConfig(ActionTrackerConfig baseConfig){
        List<String> errors = new ArrayList<>();
        Consumer<String> addToErrorConsumer = (name) -> errors.add("%s missing configuration".formatted(name));
        notNull(baseConfig::getName,NAME_KEY,addToErrorConsumer);
        notNull(baseConfig::getKafka,KAFKA_KEY,addToErrorConsumer);
        if(baseConfig.getKafka() != null){
            UnaryOperator<String> combineName = (name)-> KAFKA_KEY+"."+name;
            ActionTrackerConfig.KafkaConfig k = baseConfig.getKafka();
            notNull(k::getBroker, combineName.apply(KAFKA_BROKER_KEY), addToErrorConsumer);
            notNull(k::getRetryTopic, combineName.apply(KAFKA_RETRY_TOPIC_KEY), addToErrorConsumer);
            notNull(k::getWebhookTopic, combineName.apply(KAFKA_WEBHOOK_TOPIC_KEY), addToErrorConsumer);
            notNull(k::getConsumerGroupId, combineName.apply(KAFKA_CONSUMER_GROUP_ID_KEY), addToErrorConsumer);
        }else{
            errors.add("missing kafak configurations");
        }
        if(baseConfig.getGithub() != null){
            UnaryOperator<String> combineName = (name)-> GITHUB_KEY+"."+name;
            ActionTrackerConfig.GithubConfig g = baseConfig.getGithub();
            notNull(g::getGroupName, combineName.apply(GITHUB_GROUP_NAME_KEY), addToErrorConsumer);
        }else{
            errors.add("missing github configurations");
        }
        return errors;
    }

    public static ActionTrackerConfig createBaseConfig(String configStr){

        PropertyUtils propertyUtils = new PropertyUtils();
        propertyUtils.setSkipMissingProperties(true);
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setEnumCaseSensitive(false);
        Constructor constructor = new Constructor(ActionTrackerConfig.class,loaderOptions );
        constructor.setPropertyUtils(propertyUtils);
        Yaml yaml = new Yaml(constructor);

        return yaml.load(configStr);
    }
}
