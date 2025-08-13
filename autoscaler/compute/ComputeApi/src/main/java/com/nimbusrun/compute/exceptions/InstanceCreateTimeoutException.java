package com.nimbusrun.compute.exceptions;

public class InstanceCreateTimeoutException extends RuntimeException{
    private final boolean shouldHaveBeenCreated;
    public InstanceCreateTimeoutException(String msg, boolean shouldHaveBeenCreated){
        super(msg);
        this.shouldHaveBeenCreated = shouldHaveBeenCreated;
    }
    public InstanceCreateTimeoutException( boolean shouldHaveBeenCreated){

        this.shouldHaveBeenCreated = shouldHaveBeenCreated;
    }

    public boolean isShouldHaveBeenCreated() {
        return shouldHaveBeenCreated;
    }
}
