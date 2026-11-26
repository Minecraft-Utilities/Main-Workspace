package xyz.mcutils.backend.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.Main;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts every enabled {@link Job} on its own virtual thread and drives its cycles from its
 * {@link JobTrigger}. A job never overlaps itself because its cycles run one after another.
 */
@Service
@Slf4j
public class JobRunner {
    private final List<Job> jobs;
    private final Environment environment;
    private final JobMetric metric;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final JobContext context = new JobContext(this.running::get);
    private final List<Future<?>> loops = new ArrayList<>();

    public JobRunner(List<Job> jobs, List<JobProvider> providers, Environment environment, JobMetric metric) {
        List<Job> all = new ArrayList<>(jobs);
        providers.forEach(provider -> all.addAll(provider.jobs()));
        this.jobs = List.copyOf(all);
        this.environment = environment;
        this.metric = metric;
        if (this.jobs.stream().map(Job::name).distinct().count() != this.jobs.size()) {
            throw new IllegalStateException("Job names must be unique: " + this.jobs.stream().map(Job::name).toList());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        for (Job job : this.jobs) {
            if (!this.environment.getProperty("mc-utils.jobs." + job.name() + ".enabled", Boolean.class, true)) {
                log.info("Job {} is disabled", job.name());
                continue;
            }
            this.loops.add(Main.EXECUTOR.submit(() -> runLoop(job)));
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void stop() {
        this.running.set(false);
        this.loops.forEach(loop -> loop.cancel(true));
    }

    private void runLoop(Job job) {
        this.metric.setRunning(job.name(), true);
        try {
            switch (job.trigger()) {
                case JobTrigger.Continuous continuous -> runContinuous(job, continuous);
                case JobTrigger.Cron cron -> runCron(job, cron);
                case JobTrigger.Periodic periodic -> runPeriodic(job, periodic);
            }
        } finally {
            this.metric.setRunning(job.name(), false);
        }
    }

    private void runContinuous(Job job, JobTrigger.Continuous trigger) {
        while (this.running.get()) {
            JobResult result = cycle(job);
            boolean waited = switch (result) {
                case null -> this.context.sleep(job.errorBackoff());
                case WORKED -> true;
                case IDLE -> trigger.idleDelay().isZero() || this.context.sleep(trigger.idleDelay());
            };
            if (!waited) {
                return;
            }
        }
    }

    private void runCron(Job job, JobTrigger.Cron trigger) {
        CronExpression cron = CronExpression.parse(trigger.expression());
        if (trigger.runOnStart() && this.running.get()) {
            cycle(job);
        }
        while (this.running.get()) {
            ZonedDateTime now = ZonedDateTime.now();
            ZonedDateTime next = cron.next(now);
            if (next == null || !this.context.sleep(Duration.between(now, next))) {
                return;
            }
            cycle(job);
        }
    }

    private void runPeriodic(Job job, JobTrigger.Periodic trigger) {
        if (!this.context.sleep(trigger.initialDelay())) {
            return;
        }
        while (this.running.get()) {
            cycle(job);
            if (!this.context.sleep(trigger.interval())) {
                return;
            }
        }
    }

    /**
     * Runs one cycle and records it.
     *
     * @return the cycle's result, or null if it failed or was cut short by shutdown
     */
    private JobResult cycle(Job job) {
        long start = System.nanoTime();
        try {
            JobResult result = job.run(this.context);
            if (result == null) {
                throw new IllegalStateException("Job " + job.name() + " returned no result");
            }
            this.metric.recordCycle(job.name(), result, secondsSince(start));
            return result;
        } catch (Exception e) {
            if (!this.running.get()) {
                return null;
            }
            this.metric.recordFailure(job.name(), secondsSince(start));
            log.error("Job {} cycle failed", job.name(), e);
            return null;
        }
    }

    private static double secondsSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000_000.0;
    }
}
