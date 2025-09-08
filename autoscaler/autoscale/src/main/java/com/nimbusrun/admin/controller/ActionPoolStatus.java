package com.nimbusrun.admin.controller;

import java.util.Map;

public record ActionPoolStatus(String computeType, String runnerGroup, Map<String,Object> actionPool) {
}
