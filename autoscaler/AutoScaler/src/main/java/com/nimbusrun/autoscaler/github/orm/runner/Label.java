package com.nimbusrun.autoscaler.github.orm.runner;

import java.util.ArrayList;
import lombok.Data;
import lombok.NoArgsConstructor;

// import com.fasterxml.jackson.databind.ObjectMapper; // version 2.11.1
// import com.fasterxml.jackson.annotation.JsonProperty; // version 2.11.1
/* ObjectMapper om = new ObjectMapper();
Root root = om.readValue(myJsonString, Root.class); */
@Data
@NoArgsConstructor
public class Label{
    public int id;
    public String name;
    public String type;

    public Label(int id, String name, String type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }
}