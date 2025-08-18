package com.nimbusrun.webhook;

import com.nimbusrun.logging.LogLevel;
import com.nimbusrun.webhook.config.WebHookConfig;
import com.nimbusrun.webhook.config.WebHookConfigReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.Arrays;

@Slf4j
@SpringBootApplication
public class WebhookApplication {



    public static void main(String[] args) throws IOException {
        setSystemProperties();
        SpringApplication.run(WebhookApplication.class, args);

    }
    public static void setSystemProperties() throws IOException {
        WebHookConfig config = WebHookConfigReader.readConfig();
        System.setProperty("spring.config.location", System.getenv(WebHookConfigReader.NIMBUS_RUN_CONFIGURATION_FILE_ENV));
        System.setProperty("spring.application.name", config.getName());
        setLogLevel(config);
    }
    public static void setLogLevel(WebHookConfig config){
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
