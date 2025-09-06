package com.nimbusrun.admin;

import com.nimbusrun.compute.Compute;
import com.nimbusrun.config.ConfigReader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

  private final Compute compute;
  private ConfigReader configReader;
  public AdminController(ConfigReader configReader, Compute compute){
    this.configReader = configReader;
    this.compute = compute;

  }

  @GetMapping(value = "action-pool-status", produces = "application/json")
  public ResponseEntity<ActionPoolStatus> nimbusConfig(){
    ActionPoolStatus s = new ActionPoolStatus(configReader.getBaseConfig().getComputeType(),compute.actionPoolToApiResponse());
      return new ResponseEntity<>(s, HttpStatus.OK);
  }



}
