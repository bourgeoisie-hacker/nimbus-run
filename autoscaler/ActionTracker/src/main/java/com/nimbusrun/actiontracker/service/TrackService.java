package com.nimbusrun.actiontracker.service;

import com.nimbusrun.Constants;
import com.nimbusrun.github.GithubActionJob;
import com.nimbusrun.github.WorkflowJobStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class TrackService {
    private static Integer MAX_QUEUED_TIME_OF_JOB_IN_MINUTES_DEFAULT = 10;
    private static Integer MAX_RETRY_ATTEMPTS_DEFAULT = 3;
    private Map<String, WorkflowJobWatcher> workflowJobWatcherMap = new ConcurrentHashMap<>();
    private BlockingDeque<GithubActionJob> jobQueue = new LinkedBlockingDeque<>();
    private Map<String,RetryTracker> retryTrackerMap = new ConcurrentHashMap<>();
    private final ExecutorService mainThread;
    private final ScheduledExecutorService runUpdateWatcher;
    private final KafkaProducer kafkaProducer;

    public TrackService(KafkaProducer kafkaReceiver){
        mainThread = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
        runUpdateWatcher = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());

        mainThread.execute(this::runUpdateWatcher);
        runUpdateWatcher.scheduleWithFixedDelay(this::checkRetryStatus, 30, 20 ,TimeUnit.SECONDS);
        this.kafkaProducer = kafkaReceiver;
    }

    public boolean hasActionPoolLabel(GithubActionJob gj ){
        return gj.getLabels().stream().anyMatch(label->{
            String[] labelSplit = label.replace(" ", "").split("=");
            if(labelSplit.length != 3){
                return false;
            }else if(labelSplit[0] == Constants.ACTION_GROUP_LABEL_KEY){
                return true;
            }
            return false;
        });
    }
    public void runUpdateWatcher(){
            while (true) {
                try{
                    GithubActionJob gj;
                    while((gj = this.jobQueue.poll(1, TimeUnit.HOURS)) != null){
                        if(!hasActionPoolLabel(gj)){
                            return;
                        }
                        GithubActionJob finalGj = gj;
                        WorkflowJobWatcher watcher = workflowJobWatcherMap.computeIfAbsent(gj.getId(),
                                (key)-> WorkflowJobWatcher.WorkflowJobWatcherBuilder.aWorkflowJobWatcher()
                                .withRunId(finalGj.getRunId()).withJobId(finalGj.getId()).build());
                        watcher.getGithubActionJobs().add(gj);
                    }
                }catch (Exception e){}
            }
    }

    public void checkRetryStatus(){
            try{
                Map<String, WorkflowJobWatcher> watcherMap = new HashMap<>(workflowJobWatcherMap);
                List<WorkflowJobWatcher> watchersToRetry = watcherMap.values().stream().filter(w->
                    w.getGithubActionJobs().stream().noneMatch(i-> WorkflowJobStatus.isActiveStatus(i.getStatus()) )
                ).toList();
                for(WorkflowJobWatcher w : watchersToRetry){
                    try{
                        Optional<GithubActionJob> opt = w.getGithubActionJobs().stream().filter(gj -> gj.getStatus() == WorkflowJobStatus.QUEUED).findFirst();
                        if(opt.isPresent()){

                            RetryTracker retryTracker = retryTrackerMap.computeIfAbsent(w.getJobId(), (key)-> new RetryTracker(w.getJobId(),w.getRunId()));
                            Optional<Long> latestTime = retryTracker.getRetryTimes().stream().max(Comparator.comparingLong(i->i));
                            if(latestTime.isEmpty() || shouldRetry(latestTime.get(), retryTracker.getRetryTimes().size())){{
                                //Because I don't want github api to be everywhere if we can avoid it we should instead
                                // have the autoscaler check whether or not a job should be retried. Action Tracker will just be dumb

                                kafkaProducer.sendRetry(opt.get().getJsonStr());
                            }}

                        }
                    }catch (Exception e){
                       log.error("Failed to retry job id: %s for run id: %s".formatted(w.getJobId(), w.getRunId()), e);
                    }
                }
            }catch (Exception e){

            }
    }

    public boolean shouldRetry(long lastRetry, long retryAttempts){
        Instant lastAttempt = Instant.ofEpochMilli(lastRetry);
        Instant now = Instant.now();
        int elaspedTime = Duration.between(lastAttempt, now).toMinutesPart();
        if(elaspedTime > MAX_QUEUED_TIME_OF_JOB_IN_MINUTES_DEFAULT && retryAttempts > MAX_RETRY_ATTEMPTS_DEFAULT){
            return true;
        }
        return false;
    }

    public void receiveJob(GithubActionJob gj){
        jobQueue.offer(gj);
    }
}
