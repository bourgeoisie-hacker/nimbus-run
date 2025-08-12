package com.nimbusrun.autoscaler;

import com.nimbusrun.autoscaler.config.BaseConfig;
import com.nimbusrun.autoscaler.config.ConfigReader;
import com.nimbusrun.autoscaler.config.ConfigurationFileInfo;
import com.nimbusrun.autoscaler.github.GithubService;
import com.nimbusrun.compute.ActionPool;
import com.nimbusrun.compute.Compute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Slf4j
@SpringBootApplication
@ComponentScan({"com.nimbusrun.autoscaler, com.nimbusrun.compute"})
public class AutoScalerApplication {

    public static void main(String[] args) throws Exception {

        setSystemProperties();
        SpringApplication.run(AutoScalerApplication.class, args);
//        ctx.getBean(Autoscaler.class).receivedRequests.offer(new ActionPool("t3.medium", 1, 1));
//        ctx.getBean(Compute.class).createCompute(new ActionPool("t3.medium", 1, 1));
//        ctx.getBean(Compute.class).createCompute(new ActionPool("n1-standard-4", 1, 1));
    }

    // Not the biggest fan of having validation in multiple spots. But to avoid springboot stacktraces as much as possible we should validate the config value first.
    public static void setSystemProperties() throws IOException {

        BaseConfig config = ConfigReader.readConfig();
        List<String> errors = ConfigReader.validateBaseConfig(config);

        if(!errors.isEmpty()){
            log.error("Errors found in Configuration file: {}", String.join("\n\t", errors));
            System.exit(1);
        }
        setLogLevel(config);
        System.setProperty("spring.config.location", System.getenv(ConfigReader.NIMBUS_RUN_CONFIGURATION_FILE_ENV));
        System.setProperty("spring.profiles.active", config.getComputeType());
        System.setProperty("spring.application.name", config.getName());
    }

    public static void setLogLevel(BaseConfig config){
        String dependenciesLogLevel = "warn";
        String applicationLogLevels = "info";
        if(config.getLogLevel() != null && config.getLogLevel() != BaseConfig.LogLevel.N_A){
            if(config.getLogLevel() == BaseConfig.LogLevel.UNKNOWN){
                String logLevels = String.join("| ", Arrays.stream(BaseConfig.LogLevel.values()).map(BaseConfig.LogLevel::getLevel).toList());
                log.warn("log level improperly set. Please use values {}", logLevels);
            }
            else if(BaseConfig.LogLevel.VERBOSE == config.getLogLevel()){
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
