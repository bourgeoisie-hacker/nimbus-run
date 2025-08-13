package com.nimbusrun.webhook;

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
        String dependenciesLogLevel = "warn";
        String applicationLogLevels = "info";
        if(config.getLogLevel() != null && config.getLogLevel() != WebHookConfig.LogLevel.N_A){
            if(config.getLogLevel() == WebHookConfig.LogLevel.UNKNOWN){
                String logLevels = String.join("| ", Arrays.stream(WebHookConfig.LogLevel.values()).map(WebHookConfig.LogLevel::getLevel).toList());
                log.warn("log level improperly set. Please use values {}", logLevels);
            }
            else if(WebHookConfig.LogLevel.VERBOSE == config.getLogLevel()){
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
