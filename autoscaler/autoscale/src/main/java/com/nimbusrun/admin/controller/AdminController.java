package com.nimbusrun.admin.controller;

import com.nimbusrun.admin.AdminService;
import com.nimbusrun.compute.Compute;
import com.nimbusrun.compute.GithubApi;
import com.nimbusrun.config.ConfigReader;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

@RestController
@RequestMapping("/")
public class AdminController {

  private final Compute compute;
  private ConfigReader configReader;
  private AdminService adminService;
  private final GithubApi githubApi;
  public AdminController(ConfigReader configReader, Compute compute, AdminService adminService,
      GithubApi githubApi){
    this.configReader = configReader;
    this.compute = compute;
    this.adminService = adminService;

    this.githubApi = githubApi;
  }

  @GetMapping(value = "api/v1/admin/action-pool-status", produces = "application/json")
  public ResponseEntity<ActionPoolStatus> nimbusConfig(){
    ActionPoolStatus s = new ActionPoolStatus(configReader.getBaseConfig().getComputeType(),githubApi.getRunnerGroupName(),compute.actionPoolToApiResponse());
      return new ResponseEntity<>(s, HttpStatus.OK);
  }

  @GetMapping(value = "api/v1/admin/posted-webhooks",produces = "application/json")
  public ResponseEntity<List<WorkflowManagerResponse>> getPostedWebhooks(){
    return  new ResponseEntity<>(this.adminService.getWorkflowManagers().stream().map(WorkflowManagerResponse::fromWorkflowManager).toList(),HttpStatus.OK);
  }

  @GetMapping("actionpool.html")
  public ModelAndView actionPoolStatusPage(ModelMap map) {
    if(this.configReader.getBaseConfig().getComputeType().equalsIgnoreCase("aws")){
      return new ModelAndView("actionpool-aws", map);
    } else if(this.configReader.getBaseConfig().getComputeType().equalsIgnoreCase("gcp")){
      return new ModelAndView("actionpool-gcp", map);
    }else {
      return new ModelAndView("redirect:/api/v1/admin/action-pool-status");
    }
//    map.addAttribute("config", response);

  }

}
