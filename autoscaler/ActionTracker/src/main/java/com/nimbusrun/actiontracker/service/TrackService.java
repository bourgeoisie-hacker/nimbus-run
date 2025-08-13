package com.nimbusrun.actiontracker.service;

import com.nimbusrun.github.GithubActionJob;
import com.nimbusrun.github.WorkflowJobStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    private static Integer MAX_JOB_IN_QUEUED_IN_MINUTES_DEFAULT = 5;
    private static Integer MAX_TIME_BTW_RETRIES_IN_MINUTES_DEFAULT = 5;
    private static Integer MAX_RETRY_ATTEMPTS_DEFAULT = 3;
    private Map<String, WorkflowJobWatcher> workflowJobWatcherMap = new ConcurrentHashMap<>();
    private BlockingDeque<GithubActionJob> jobQueue = new LinkedBlockingDeque<>();
    private Map<String,RetryTracker> retryTrackerMap = new ConcurrentHashMap<>();
    private final ExecutorService mainThread;
    private final ScheduledExecutorService runUpdateWatcher;
    private final KafkaProducer kafkaProducer;
    private final String actionGroupName;
    private final Integer maxJobInQueuedInMinutes;
    private final Integer maxTimeBtwRetriesInMinutes;
    private final Integer maxRetries;

    public TrackService(KafkaProducer kafkaReceiver, @Value("${github.groupName}") String actionGroupName,
                        @Value("${retryPolicy.maxJobInQueuedInMinutes:#{null}}") Integer maxJobInQueuedInMinutes,
                        @Value("${retryPolicy.maxTimeBtwRetriesInMinutes:#{null}}") Integer maxTimeBtwRetriesInMinutes,
                        @Value("${retryPolicy.maxRetries:#{null}}") Integer maxRetries){
        this.maxJobInQueuedInMinutes = Optional.ofNullable(maxJobInQueuedInMinutes).orElse(MAX_JOB_IN_QUEUED_IN_MINUTES_DEFAULT);
        this.maxTimeBtwRetriesInMinutes = Optional.ofNullable(maxTimeBtwRetriesInMinutes).orElse(MAX_TIME_BTW_RETRIES_IN_MINUTES_DEFAULT);
        this.maxRetries = Optional.ofNullable(maxRetries).orElse(MAX_RETRY_ATTEMPTS_DEFAULT);
        this.kafkaProducer = kafkaReceiver;
        this.actionGroupName = actionGroupName;
        mainThread = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
        runUpdateWatcher = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());

        mainThread.execute(this::runUpdateWatcher);
        runUpdateWatcher.scheduleWithFixedDelay(this::checkRetryStatus, 30, 20 ,TimeUnit.SECONDS);

    }

    public void runUpdateWatcher(){
            while (true) {
                try{
                    GithubActionJob gj;
                    while((gj = this.jobQueue.poll(3, TimeUnit.SECONDS)) != null){
                        if(gj.getActionGroupName().isEmpty() || !gj.getActionGroupName().get().equalsIgnoreCase(this.actionGroupName)){
                            continue;
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
                List<WorkflowJobWatcher> watchersToRetry = watcherMap.values().stream().filter(this::safeToRetry).toList();
                for(WorkflowJobWatcher w : watchersToRetry){
                    try{
                        Optional<GithubActionJob> opt = w.getGithubActionJobs().stream().filter(gj -> gj.getStatus() == WorkflowJobStatus.QUEUED).findFirst();
                        if(opt.isPresent()){
                            long minutesSinceStart = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - opt.get().getStartedAt());
                            RetryTracker retryTracker = retryTrackerMap.computeIfAbsent(w.getJobId(), (key)-> new RetryTracker(w.getJobId(),w.getRunId()));
                            Optional<Long> latestTime = retryTracker.getRetryTimes().stream().max(Comparator.comparingLong(i->i));
                            if(minutesSinceStart > this.maxJobInQueuedInMinutes && (latestTime.isEmpty() || shouldRetry(latestTime.get(), retryTracker.getRetryTimes().size()))){{
                                //Because I don't want github api to be everywhere if we can avoid it we should instead
                                // have the autoscaler check whether or not a job should be retried. Action Tracker will just be dumb
                                log.info("Retrying {}", opt.get().getJsonStr().replace(" ", ""));
                                kafkaProducer.sendRetry(opt.get().getJsonStr());
                                retryTracker.getRetryTimes().add(System.currentTimeMillis());
                            }}

                        }
                    }catch (Exception e){
                       log.error("Failed to retry job id: %s for run id: %s".formatted(w.getJobId(), w.getRunId()), e);
                    }
                }
            }catch (Exception e){

            }
    }

    public boolean safeToRetry(WorkflowJobWatcher watcher){
        boolean hasQueued = watcher.getGithubActionJobs().stream().anyMatch(gj -> WorkflowJobStatus.QUEUED == gj.getStatus());
        long startedOperations = watcher.getGithubActionJobs().stream().filter(gj -> WorkflowJobStatus.isActiveStatus(gj.getStatus())).count();
        return hasQueued && startedOperations==0;
    }
    public boolean shouldRetry(long lastRetry, long retryAttempts){
        Instant lastAttempt = Instant.ofEpochMilli(lastRetry);
        Instant now = Instant.now();
        int elaspedTime = Duration.between(lastAttempt, now).toMinutesPart();
        if(elaspedTime > this.maxTimeBtwRetriesInMinutes && retryAttempts > this.maxRetries){
            return true;
        }
        return false;
    }

    public void receiveJob(GithubActionJob gj){
        jobQueue.offer(gj);
    }
}
