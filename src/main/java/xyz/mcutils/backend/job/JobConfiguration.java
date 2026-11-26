package xyz.mcutils.backend.job;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.mcutils.backend.service.MetricService;

/**
 * Registers the metric that {@link JobRunner} records into.
 */
@Configuration
public class JobConfiguration {

    @Bean
    JobMetric jobMetric() {
        return new JobMetric(MetricService.REGISTRY);
    }
}
