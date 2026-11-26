package xyz.mcutils.backend.job;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobRunnerTest {
    private PrometheusRegistry registry;
    private MockEnvironment environment;
    private JobRunner runner;

    @BeforeEach
    void setUp() {
        this.registry = new PrometheusRegistry();
        this.environment = new MockEnvironment();
    }

    @AfterEach
    void tearDown() {
        if (this.runner != null) {
            this.runner.stop();
        }
    }

    private JobRunner runner(List<Job> jobs, List<JobProvider> providers) {
        this.runner = new JobRunner(jobs, providers, this.environment, new JobMetric(this.registry));
        return this.runner;
    }

    private record TestJob(String name, JobTrigger trigger, JobBody body) implements Job {
        @Override
        public JobResult run(JobContext context) throws Exception {
            return this.body.run(context);
        }

        @Override
        public Duration errorBackoff() {
            return Duration.ofMillis(10);
        }
    }

    @FunctionalInterface
    private interface JobBody {
        JobResult run(JobContext context) throws Exception;
    }

    private double counter(String job, String result) {
        for (MetricSnapshot snapshot : this.registry.scrape()) {
            if (snapshot instanceof CounterSnapshot counters && snapshot.getMetadata().getName().equals("background_job_cycles")) {
                return counters.getDataPoints().stream()
                        .filter(point -> job.equals(point.getLabels().get("name")) && result.equals(point.getLabels().get("result")))
                        .mapToDouble(CounterSnapshot.CounterDataPointSnapshot::getValue)
                        .findFirst().orElse(0);
            }
        }
        return 0;
    }

    private double gauge(String metric, String job) {
        for (MetricSnapshot snapshot : this.registry.scrape()) {
            if (snapshot instanceof GaugeSnapshot gauges && snapshot.getMetadata().getName().equals(metric)) {
                return gauges.getDataPoints().stream()
                        .filter(point -> job.equals(point.getLabels().get("name")))
                        .mapToDouble(GaugeSnapshot.GaugeDataPointSnapshot::getValue)
                        .findFirst().orElse(-1);
            }
        }
        return -1;
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting for condition");
            }
            Thread.sleep(5);
        }
    }

    @Test
    void continuousJobRunsBackToBackWhileItWorksAndWaitsWhenIdle() throws Exception {
        AtomicInteger cycles = new AtomicInteger();
        Job job = new TestJob("worker", JobTrigger.continuous(Duration.ofMillis(10)), context ->
                cycles.incrementAndGet() <= 3 ? JobResult.WORKED : JobResult.IDLE);
        runner(List.of(job), List.of()).start();

        await(() -> counter("worker", "idle") >= 2);
        assertEquals(3, counter("worker", "worked"));
        assertTrue(gauge("background_job_last_success_timestamp_seconds", "worker") > 0);
        assertEquals(1, gauge("background_job_running", "worker"));
    }

    @Test
    void failedCycleIsCountedAndTheLoopContinues() throws Exception {
        AtomicInteger cycles = new AtomicInteger();
        Job job = new TestJob("flaky", JobTrigger.continuous(Duration.ofMillis(10)), context -> {
            if (cycles.incrementAndGet() == 1) {
                throw new IllegalStateException("boom");
            }
            return JobResult.WORKED;
        });
        runner(List.of(job), List.of()).start();

        await(() -> counter("flaky", "worked") >= 2);
        assertEquals(1, counter("flaky", "failed"));
    }

    @Test
    void jobsSwitchedOffInConfigDoNotRun() throws Exception {
        this.environment.setProperty("mc-utils.jobs.off.enabled", "false");
        AtomicInteger offCycles = new AtomicInteger();
        AtomicInteger onCycles = new AtomicInteger();
        Job off = new TestJob("off", JobTrigger.continuous(Duration.ofMillis(10)), context -> {
            offCycles.incrementAndGet();
            return JobResult.IDLE;
        });
        Job on = new TestJob("on", JobTrigger.continuous(Duration.ofMillis(10)), context -> {
            onCycles.incrementAndGet();
            return JobResult.IDLE;
        });
        runner(List.of(off, on), List.of()).start();

        await(() -> onCycles.get() >= 2);
        assertEquals(0, offCycles.get());
    }

    @Test
    void cronJobRunsOnStartWhenAsked() throws Exception {
        AtomicInteger cycles = new AtomicInteger();
        Job job = new TestJob("registry", JobTrigger.cronAndOnStart("0 0 0 1 1 *"), context -> {
            cycles.incrementAndGet();
            return JobResult.WORKED;
        });
        runner(List.of(job), List.of()).start();

        await(() -> cycles.get() == 1);
        Thread.sleep(100);
        assertEquals(1, cycles.get());
    }

    @Test
    void periodicJobRepeatsAfterItsInterval() throws Exception {
        AtomicInteger cycles = new AtomicInteger();
        Job job = Job.scheduled("tick", JobTrigger.periodic(Duration.ofMillis(10), Duration.ofMillis(10)), cycles::incrementAndGet);
        runner(List.of(job), List.of()).start();

        await(() -> counter("tick", "worked") >= 3);
        assertTrue(cycles.get() >= 3);
    }

    @Test
    void providedJobsAreStartedAlongsideBeans() throws Exception {
        AtomicInteger cycles = new AtomicInteger();
        Job provided = new TestJob("provided", JobTrigger.continuous(Duration.ofMillis(10)), context -> {
            cycles.incrementAndGet();
            return JobResult.IDLE;
        });
        runner(List.of(), List.of(() -> List.of(provided))).start();

        await(() -> cycles.get() >= 1);
    }

    @Test
    void stopEndsRunningLoopsAndASleepingJobWakesUp() throws Exception {
        AtomicInteger cycles = new AtomicInteger();
        Job job = new TestJob("sleepy", JobTrigger.continuous(Duration.ofMinutes(10)), context -> {
            cycles.incrementAndGet();
            return JobResult.IDLE;
        });
        runner(List.of(job), List.of()).start();
        await(() -> cycles.get() == 1);

        this.runner.stop();

        await(() -> gauge("background_job_running", "sleepy") == 0);
        assertEquals(1, cycles.get());
    }

    @Test
    void cycleInterruptedByShutdownIsNotCountedAsFailed() throws Exception {
        AtomicInteger started = new AtomicInteger();
        Job job = new TestJob("blocked", JobTrigger.continuous(Duration.ZERO), context -> {
            started.incrementAndGet();
            Thread.sleep(Duration.ofMinutes(10));
            return JobResult.WORKED;
        });
        runner(List.of(job), List.of()).start();
        await(() -> started.get() == 1);

        this.runner.stop();

        await(() -> gauge("background_job_running", "blocked") == 0);
        assertEquals(0, counter("blocked", "failed"));
    }

    @Test
    void duplicateJobNamesAreRejected() {
        Job a = new TestJob("same", JobTrigger.continuous(Duration.ZERO), context -> JobResult.IDLE);
        Job b = new TestJob("same", JobTrigger.continuous(Duration.ZERO), context -> JobResult.IDLE);
        assertThrows(IllegalStateException.class, () -> runner(List.of(a, b), List.of()));
    }

    @Test
    void jobContextSleepStopsEarlyWhenNoLongerRunning() {
        AtomicInteger calls = new AtomicInteger();
        JobContext context = new JobContext(() -> calls.incrementAndGet() <= 2);
        assertFalse(context.sleep(Duration.ofSeconds(30)));
    }
}
