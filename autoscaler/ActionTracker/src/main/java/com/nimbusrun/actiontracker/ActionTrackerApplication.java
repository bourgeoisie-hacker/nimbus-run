package com.nimbusrun.actiontracker;

import com.nimbusrun.actiontracker.config.ActionTrackerConfig;
import com.nimbusrun.actiontracker.config.ActionTrackerConfigReader;
import com.nimbusrun.logging.LogLevel;
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
        setManagementSettings();
        setLogLevel(config);
    }
    public static void setManagementSettings(){
        System.setProperty("management.endpoints.web.path-mapping.prometheus","metrics");
        System.setProperty("management.endpoints.web.base-path","/");
        System.setProperty("management.endpoints.web.exposure.include","prometheus");
    }
    public static void setLogLevel(ActionTrackerConfig config){
        String applicationLogLevels = "info";
        if(config.getLogLevel() != null && config.getLogLevel() != LogLevel.N_A){
            if(config.getLogLevel() == LogLevel.UNKNOWN){
                String logLevels = String.join("| ", Arrays.stream(LogLevel.values()).map(LogLevel::getLevel).toList());
                log.warn("log level improperly set. Please use values {}", logLevels);
            }
            else {
                applicationLogLevels = config.getLogLevel().getLevel();
            }

        }
        System.setProperty("logging.level.com.nimbusrun", applicationLogLevels);

    }
}
