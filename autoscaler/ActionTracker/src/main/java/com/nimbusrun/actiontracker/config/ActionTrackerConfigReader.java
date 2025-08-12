package com.nimbusrun.actiontracker.config;

import com.nimbusrun.Utils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
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
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static java.awt.SystemColor.info;

@Configuration
@Slf4j
public class ActionTrackerConfigReader {
    public static final String NIMBUS_RUN_CONFIGURATION_FILE_ENV = "NIMBUS_RUN_CONFIGURATION_FILE";
    public static final String KAFKA_KEY = "kafka";
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
            throw new RuntimeException();// unreachable
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
        Yaml yaml = new Yaml();
        Map<String, Object> configs = yaml.load(Files.newInputStream(Paths.get(configPath)));
        return buildBaseConfig(configs);
    }

    public static void notNull(Supplier<Object> supplier, String name, Consumer<String> whatToDo){
        if(supplier.get() == null){
            whatToDo.accept(name);
        }
    }
    public static List<String> validateBaseConfig(ActionTrackerConfig baseConfig){
        List<String> errors = new ArrayList<>();
        Consumer<String> addToErrorConsumer = (name) -> errors.add("%s missing configuration");
        notNull(baseConfig::getName,NAME_KEY,addToErrorConsumer);
        notNull(baseConfig::getKafkaConfig,KAFKA_KEY,addToErrorConsumer);
        if(baseConfig.getKafkaConfig() != null){
            UnaryOperator<String> combineName = (name)-> KAFKA_KEY+"."+name;
            ActionTrackerConfig.KafkaConfig k = baseConfig.getKafkaConfig();
            notNull(k::getBroker, combineName.apply(KAFKA_BROKER_KEY), addToErrorConsumer);
            notNull(k::getRetryTopic, combineName.apply(KAFKA_RETRY_TOPIC_KEY), addToErrorConsumer);
            notNull(k::getWebhookTopic, combineName.apply(KAFKA_WEBHOOK_TOPIC_KEY), addToErrorConsumer);
            notNull(k::getConsumerGroupId, combineName.apply(KAFKA_CONSUMER_GROUP_ID_KEY), addToErrorConsumer);
        }
        return errors;
    }
    private static ActionTrackerConfig buildBaseConfig(Map<String, Object> map){
        ActionTrackerConfig config = new ActionTrackerConfig();
        config.setName(map.get(NAME_KEY).toString());

        if(Utils.mapContainsKeyAndNotNullValue(map,LOG_LEVEL_KEY)) {
            config.setLogLevel(ActionTrackerConfig.LogLevel.fromStr(map.get(LOG_LEVEL_KEY).toString()));
        }
        if(Utils.mapContainsKeyAndNotNullValue(map,KAFKA_KEY)) {
            config.setKafkaConfig(buildKafkaConfig((Map<String, String>) map.get(KAFKA_KEY)));
        }
        return config;
    }

    private static ActionTrackerConfig.KafkaConfig buildKafkaConfig(Map<String, String> map){
        ActionTrackerConfig.KafkaConfig config = new ActionTrackerConfig.KafkaConfig();
        BiConsumer<String,Consumer<String>> con = (key, consumer)->{
            if(Utils.mapContainsKeyAndNotNullValue(map, key)){
                consumer.accept(map.get(key));
            }
        };
        con.accept(KAFKA_BROKER_KEY, config::setBroker);
        con.accept(KAFKA_RETRY_TOPIC_KEY, config::setRetryTopic);
        con.accept(KAFKA_WEBHOOK_TOPIC_KEY, config::setWebhookTopic);
        con.accept(KAFKA_CONSUMER_GROUP_ID_KEY, config::setConsumerGroupId);
        return config;
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
