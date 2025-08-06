package com.nimbusrun.compute;


import java.util.List;

public record ComputeConfigResponse(List<String> errors, List<String> warrnings, List<ActionPool> actionPools) {

}
