package xyz.mcutils.backend.job;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Handed to a job cycle so long cycles can stop and wait cooperatively during shutdown.
 */
public class JobContext {
    private final BooleanSupplier running;

    JobContext(BooleanSupplier running) {
        this.running = running;
    }

    public boolean isRunning() {
        return this.running.getAsBoolean();
    }

    /**
     * Sleeps for {@code duration} unless the application is shutting down.
     *
     * @return false if the wait was cut short and the cycle should return
     */
    public boolean sleep(Duration duration) {
        long deadline = System.nanoTime() + duration.toNanos();
        while (isRunning()) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                return true;
            }
            try {
                Thread.sleep(Math.min(Duration.ofNanos(remainingNanos).toMillis() + 1, 1000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
