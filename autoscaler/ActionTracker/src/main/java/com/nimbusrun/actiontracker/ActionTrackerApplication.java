package com.nimbusrun.actiontracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ActionTrackerApplication {

    //TODO Track who triggered the github action
    //TODO Track when job comes in without the proper labels
    public static void main(String[] args) {
        SpringApplication.run(ActionTrackerApplication.class, args);
    }

}
