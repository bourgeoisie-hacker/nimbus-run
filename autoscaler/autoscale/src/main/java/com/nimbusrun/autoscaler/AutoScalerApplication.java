package com.nimbusrun.autoscaler;

import com.nimbusrun.Constants;
import com.nimbusrun.autoscaler.config.BaseConfig;
import com.nimbusrun.autoscaler.config.ConfigReader;
import com.nimbusrun.compute.Compute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.io.IOException;
import java.util.Arrays;

@Slf4j
@SpringBootApplication
@ComponentScan({"com.nimbusrun.autoscaler, com.nimbusrun.compute"})
public class AutoScalerApplication {

    public static void main(String[] args) throws Exception {

        setSystemProperties();
        var ctx = SpringApplication.run(AutoScalerApplication.class, args);
        ctx.getBean(Compute.class).listAllComputeInstances();
//        ctx.getBean(Autoscaler.class).receivedRequests.offer(new ActionPool("t3.medium", 1, 1));
//        ctx.getBean(Compute.class).createCompute(new ActionPool("t3.medium", 1, 1));
//        ctx.getBean(Compute.class).createCompute(new ActionPool("n1-standard-4", 1, 1));
    }

    public static void setSystemProperties() throws IOException {

        BaseConfig config = ConfigReader.readConfig();
        setLogLevel(config);
        setManagementSettings();
        System.setProperty("spring.config.location", System.getenv(ConfigReader.NIMBUS_RUN_CONFIGURATION_FILE_ENV));
        if(Boolean.parseBoolean(config.getStandalone())){
            System.setProperty("spring.profiles.active", Constants.STANDALONE_PROFILE_NAME );
        }
        System.setProperty("spring.application.name", config.getName());

    }

    public static void setManagementSettings(){
        System.setProperty("management.endpoints.web.path-mapping.prometheus","metrics");
        System.setProperty("management.endpoints.web.base-path","/");
        System.setProperty("management.endpoints.web.exposure.include","prometheus");
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
