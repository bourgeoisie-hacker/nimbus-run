package com.nimbusrun.admin;

import java.util.Map;

public record ActionPoolStatus(String computeType, Map<String,Object> actionPool) {
}
