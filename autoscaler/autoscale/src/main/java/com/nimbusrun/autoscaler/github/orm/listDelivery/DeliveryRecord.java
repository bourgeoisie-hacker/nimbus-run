package com.nimbusrun.autoscaler.github.orm.listDelivery;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
public class DeliveryRecord {

    private String id;
    private String guid;

    @JsonProperty("delivered_at")
    private ZonedDateTime deliveredAt;

    private boolean redelivery;
    private double duration;
    private String status;

    @JsonProperty("status_code")
    private int statusCode;

    private String event;
    private String action;

    @JsonProperty("installation_id")
    private String installationId;  // nullable

    @JsonProperty("repository_id")
    private String repositoryId;

    private String url;

    @JsonProperty("throttled_at")
    private ZonedDateTime throttledAt; // nullable

}

