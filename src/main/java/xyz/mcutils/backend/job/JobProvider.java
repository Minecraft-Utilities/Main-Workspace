package xyz.mcutils.backend.job;

import java.util.List;

/**
 * Supplies jobs whose number depends on configuration, such as one per swept name length.
 */
public interface JobProvider {
    List<Job> jobs();
}
