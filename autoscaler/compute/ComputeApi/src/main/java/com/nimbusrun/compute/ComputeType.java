package com.nimbusrun.compute;

public enum ComputeType {
    AWS("aws"),UNKNOWN("unknown");
    private String simpleName;
    ComputeType(String simpleName){
        this.simpleName = simpleName;
    }
    public static ComputeType computeTypeValueFor(String computeType){
        return switch (computeType.toLowerCase()){
            case "aws" -> AWS;
            case null, default -> UNKNOWN;
        };
    }

    public String getSimpleName() {
        return simpleName;
    }
}
