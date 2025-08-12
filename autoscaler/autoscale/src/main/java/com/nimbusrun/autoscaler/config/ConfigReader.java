package com.nimbusrun.autoscaler.config;

import com.nimbusrun.compute.ActionPool;
import com.nimbusrun.compute.Compute;
import com.nimbusrun.compute.ComputeConfigResponse;
import com.nimbusrun.compute.ComputeType;
import com.nimbusrun.compute.Constants;
import com.nimbusrun.compute.Utils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@Configuration
@Slf4j
public class ConfigReader {
    public static final String NIMBUS_RUN_CONFIGURATION_FILE_ENV = "NIMBUS_RUN_CONFIGURATION_FILE";
    public static final String NIMBUS_RUN_CONFIG_FILE = "nimbus.run.config.file";
    public static final String COMPUTE_KEY = "compute";
    public static final String KAFKA_KEY = "kafka";
    public static final String KAFKA_BROKER_KEY = "broker";
    public static final String GITHUB_KEY = "github";
    public static final String NAME_KEY = "name";
    public static final String LOG_LEVEL_KEY = "logLevel";
    public static final String COMPUTE_TYPE = "computeType";
    public static final String KAFKA_RETRY_TOPIC_KEY = "retryTopic";
    public static final String KAFKA_WEBHOOK_TOPIC_KEY = "webhookTopic";
    public static final String GIT_GROUP_NAME_KEY = "groupName";
    public static final String GITHUB_ORGANIZATION_NAME_KEY = "organizationName";
    public static final String KAFKA_CONSUMER_GROUP_ID_KEY = "consumerGroupId";
    @Getter
    private final BaseConfig baseConfig;
    private final Map<String, ActionPool> actionPoolMap = new HashMap<>();
    public ConfigReader(Compute compute, ApplicationArguments arguments) {
        BaseConfig baseConfig1 = null;
        try {

            ConfigurationFileInfo info = new ConfigurationFileInfo(arguments);
            if(!info.getErrors().isEmpty()){
                info.getErrors().forEach(log::error);
                System.exit(1);
            }
            baseConfig1 = readConfig(info.getConfigPath().toString());
            if (baseConfig1.getCompute()==null) {
                log.error("missing %s configuration".formatted(COMPUTE_KEY));
                System.exit(1);
            }

            ComputeConfigResponse response = compute.receiveComputeConfigs((Map<String, Object>) baseConfig1.getCompute(), baseConfig1.getName());
            if( response.warrnings() != null && !response.warrnings().isEmpty()){
                response.warrnings().forEach(e-> log.warn("Compute configuration warning: {}", e));
            }
            if (response.errors() != null && !response.errors().isEmpty()) {
                response.errors().forEach(e-> log.error("Compute configuration error: {}", e));
                System.exit(1);
            }
            List<String> validateActionPoolErrors = validateActionPool(response.actionPools());

            if(!validateActionPoolErrors.isEmpty()){
                log.error("Validation errors for action pools:\n" + String.join("\n\t", validateActionPoolErrors));
                System.exit(1);
            }
            actionPoolMap.putAll(response.actionPools().stream().collect(Collectors.toMap(ActionPool::getName, Function.identity())));
        }catch (Exception e){
            Utils.excessiveErrorLog("Error setting up Configurations occurred due to %s.".formatted(e.getMessage()), e, log);
            System.exit(1);
        }
        this.baseConfig = baseConfig1;
    }
    public static BaseConfig readConfig(String configPath) throws IOException {
        Yaml yaml = new Yaml();
        Map<String, Object> configs = yaml.load(Files.newInputStream(Paths.get(configPath)));
        return buildBaseConfig(configs);
    }


    private static List<String> validateActionPool(List<ActionPool> actionPools){
        List<String> errors = new ArrayList<>();
        Map<String,Long> nameHistogram = actionPools.stream().collect(Collectors.groupingBy(ActionPool::getName, Collectors.counting()));
        nameHistogram.keySet().stream().filter(k->nameHistogram.get(k) > 1).forEach((k)->errors.add("Duplicate name for %s in action pool".formatted(k)));
        long defaultCount = actionPools.stream().filter(ActionPool::isDefault).count();
        if(defaultCount > 1){
            errors.add("More than one default action pool");
        }
        return errors;
    }

    public static void notNull(Supplier<Object> supplier, String name, Consumer<String> whatToDo){
        if(supplier.get() == null){
            whatToDo.accept(name);
        }
    }
    public static List<String> validateBaseConfig(BaseConfig baseConfig){
        List<String> errors = new ArrayList<>();
        Consumer<String> addToErrorConsumer = (name) -> errors.add("%s missing configuration");
        notNull(baseConfig::getName,NAME_KEY,addToErrorConsumer);
        notNull(baseConfig::getCompute,COMPUTE_KEY,addToErrorConsumer);
        notNull(baseConfig::getComputeType,COMPUTE_TYPE,addToErrorConsumer);
        notNull(baseConfig::getGithubConfig,GITHUB_KEY,addToErrorConsumer);
        notNull(baseConfig::getKafkaConfig,KAFKA_KEY,addToErrorConsumer);
        if(baseConfig.getKafkaConfig() != null){
            UnaryOperator<String> combineName = (name)-> KAFKA_KEY+"."+name;
            BaseConfig.KafkaConfig k = baseConfig.getKafkaConfig();
            notNull(k::getBroker, combineName.apply(KAFKA_BROKER_KEY), addToErrorConsumer);
            notNull(k::getRetryTopic, combineName.apply(KAFKA_RETRY_TOPIC_KEY), addToErrorConsumer);
            notNull(k::getWebhookTopic, combineName.apply(KAFKA_WEBHOOK_TOPIC_KEY), addToErrorConsumer);
            notNull(k::getConsumerGroupId, combineName.apply(KAFKA_CONSUMER_GROUP_ID_KEY), addToErrorConsumer);
        }

        if(baseConfig.getGithubConfig() != null){
            UnaryOperator<String> combineName = (name)-> GITHUB_KEY+"."+name;
            BaseConfig.GithubConfig g = baseConfig.getGithubConfig();
            notNull(g::getGroupName, combineName.apply(GIT_GROUP_NAME_KEY), addToErrorConsumer);
            notNull(g::getOrganizationName, combineName.apply(GITHUB_ORGANIZATION_NAME_KEY), addToErrorConsumer);
        }
        return errors;
    }
    public Map<String, ActionPool> getActionPoolMap(){
        return Collections.unmodifiableMap(this.actionPoolMap);
    }
    private static BaseConfig buildBaseConfig(Map<String, Object> map){
        BaseConfig config = new BaseConfig();
        config.setName(map.get(NAME_KEY).toString());
        config.setLogLevel(BaseConfig.LogLevel.fromStr(map.get(LOG_LEVEL_KEY).toString()));
        config.setKafkaConfig(buildKafkaConfig((Map<String, String>) map.get(KAFKA_KEY)));
        config.setGithubConfig(buildGithubConfig((Map<String, String>) map.get(GITHUB_KEY)));
        if(Constants.mapContainsKeyAndNotNullValue(map,COMPUTE_TYPE)){
            config.setComputeType(ComputeType.computeTypeValueFor(map.get(COMPUTE_TYPE).toString()));
        }
        if(Constants.mapContainsKeyAndNotNullValue(map, COMPUTE_KEY)){
            config.setCompute(map.get(COMPUTE_KEY));
        }
        return config;
    }
    private static BaseConfig.KafkaConfig buildKafkaConfig(Map<String, String> map){
        BaseConfig.KafkaConfig config = new BaseConfig.KafkaConfig();
        config.setBroker(map.get(KAFKA_BROKER_KEY));
        config.setRetryTopic(map.get(KAFKA_RETRY_TOPIC_KEY));
        config.setWebhookTopic(map.get(KAFKA_WEBHOOK_TOPIC_KEY));
        config.setConsumerGroupId(map.get(KAFKA_CONSUMER_GROUP_ID_KEY));
        return config;
    }
    private static BaseConfig.GithubConfig buildGithubConfig(Map<String,String> map){
        BaseConfig.GithubConfig config = new BaseConfig.GithubConfig();
        config.setGroupName(map.get(GIT_GROUP_NAME_KEY));
        config.setOrganizationName(map.get(GITHUB_ORGANIZATION_NAME_KEY));
        return config;
    }
}
