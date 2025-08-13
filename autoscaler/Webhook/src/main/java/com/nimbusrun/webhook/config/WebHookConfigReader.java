package com.nimbusrun.webhook.config;

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
public class WebHookConfigReader {
    public static final String NIMBUS_RUN_CONFIGURATION_FILE_ENV = "NIMBUS_RUN_CONFIGURATION_FILE";
    public static final String KAFKA_KEY = "kafka";
    public static final String KAFKA_BROKER_KEY = "broker";
    public static final String NAME_KEY = "name";

    public static final String KAFKA_WEBHOOK_TOPIC_KEY = "webhookTopic";

    @Getter
    private final WebHookConfig webHookConfig;
    public WebHookConfigReader() {
        try {
            this.webHookConfig = readConfig();
        }catch (Exception e){
            Utils.excessiveErrorLog("Error setting up Configurations occurred due to %s.".formatted(e.getMessage()), e, log);
            System.exit(1);
            throw new RuntimeException();// unreachable
        }
    }

    public static WebHookConfig readConfig() throws IOException {
        String configLocation = System.getenv(NIMBUS_RUN_CONFIGURATION_FILE_ENV);

        if(configLocation == null){
            log.error("Environment Variable %s not set".formatted(NIMBUS_RUN_CONFIGURATION_FILE_ENV));
            System.exit(1);
        }
        if(Files.notExists(Paths.get(configLocation)) || !Files.isRegularFile(Paths.get(configLocation))){
            log.error("Config file {} either does not exist or is not a regular file", configLocation);
            System.exit(1);
        }
        WebHookConfig actionTrackerConfig = readConfig(configLocation);
        List<String> errors = validateBaseConfig(actionTrackerConfig);
        if(!errors.isEmpty()){
            errors.forEach(log::error);
            System.exit(1);
        }
        return actionTrackerConfig;
    }
    public static WebHookConfig readConfig(String configPath) throws IOException {
        return createBaseConfig(Files.readString(Paths.get(configPath)));
    }

    public static void notNull(Supplier<Object> supplier, String name, Consumer<String> whatToDo){
        if(supplier.get() == null){
            whatToDo.accept(name);
        }
    }
    public static List<String> validateBaseConfig(WebHookConfig baseConfig){
        List<String> errors = new ArrayList<>();
        Consumer<String> addToErrorConsumer = (name) -> errors.add("%s missing configuration");
        notNull(baseConfig::getName,NAME_KEY,addToErrorConsumer);
        notNull(baseConfig::getKafka,KAFKA_KEY,addToErrorConsumer);
        if(baseConfig.getKafka() != null){
            UnaryOperator<String> combineName = (name)-> KAFKA_KEY+"."+name;
            WebHookConfig.KafkaConfig k = baseConfig.getKafka();
            notNull(k::getBroker, combineName.apply(KAFKA_BROKER_KEY), addToErrorConsumer);
            notNull(k::getWebhookTopic, combineName.apply(KAFKA_WEBHOOK_TOPIC_KEY), addToErrorConsumer);
        }else{
            errors.add("missing kafak configurations");
        }
        return errors;
    }

    public static WebHookConfig createBaseConfig(String configStr){

        PropertyUtils propertyUtils = new PropertyUtils();
        propertyUtils.setSkipMissingProperties(true);
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setEnumCaseSensitive(false);
        Constructor constructor = new Constructor(WebHookConfig.class,loaderOptions );
        constructor.setPropertyUtils(propertyUtils);
        Yaml yaml = new Yaml(constructor);

        return yaml.load(configStr);
    }
}
