package com.nimbusrun.autoscaler.github.orm.runner;

import java.util.ArrayList;
import lombok.Data;

@Data
public class ListSelfHostedRunners {
    public int total_count;
    public ArrayList<Runner> runners;
}