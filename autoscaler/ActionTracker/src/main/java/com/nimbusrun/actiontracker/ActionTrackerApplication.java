package com.nimbusrun.actiontracker;

import com.nimbusrun.actiontracker.config.ActionTrackerConfig;
import com.nimbusrun.actiontracker.config.ActionTrackerConfigReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.Arrays;
@Slf4j
@SpringBootApplication
public class ActionTrackerApplication {

    public static void main(String[] args) throws IOException {
        setSystemProperties();
        SpringApplication.run(ActionTrackerApplication.class, args);
    }

    public static void setSystemProperties() throws IOException {
        ActionTrackerConfig config = ActionTrackerConfigReader.readConfig();
        System.setProperty("spring.config.location", System.getenv(ActionTrackerConfigReader.NIMBUS_RUN_CONFIGURATION_FILE_ENV));
        System.setProperty("spring.application.name", config.getName());
        setLogLevel(config);
    }
    public static void setLogLevel(ActionTrackerConfig config){
        String dependenciesLogLevel = "warn";
        String applicationLogLevels = "info";
        if(config.getLogLevel() != null && config.getLogLevel() != ActionTrackerConfig.LogLevel.N_A){
            if(config.getLogLevel() == ActionTrackerConfig.LogLevel.UNKNOWN){
                String logLevels = String.join("| ", Arrays.stream(ActionTrackerConfig.LogLevel.values()).map(ActionTrackerConfig.LogLevel::getLevel).toList());
                log.warn("log level improperly set. Please use values {}", logLevels);
            }
            else if(ActionTrackerConfig.LogLevel.VERBOSE == config.getLogLevel()){
                dependenciesLogLevel = "debug";
                applicationLogLevels = "debug";
            }else {
                applicationLogLevels = config.getLogLevel().getLevel();
            }

        }
        System.setProperty("logging.level.root", dependenciesLogLevel);
        System.setProperty("logging.level.com.nimbusrun", applicationLogLevels);

    }
}
