package io.github.aayush10016.trail.backgroundjob.adapter.in.scheduler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.aayush10016.trail.backgroundjob.application.service.BackgroundJobProcessingSummary;
import io.github.aayush10016.trail.backgroundjob.application.service.BackgroundJobProcessorService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BackgroundJobSchedulerTest {

    @Test
    void skipsProcessingWhenSchedulerIsDisabled() {
        var processor = Mockito.mock(BackgroundJobProcessorService.class);
        var scheduler = new BackgroundJobScheduler(
                processor,
                new BackgroundJobSchedulerProperties(false, "worker-a", 30_000));

        scheduler.runScheduledCycle();

        verify(processor, never()).processOneCycle("worker-a");
    }

    @Test
    void delegatesOneCycleWhenSchedulerIsEnabled() {
        var processor = Mockito.mock(BackgroundJobProcessorService.class);
        when(processor.processOneCycle("worker-a")).thenReturn(new BackgroundJobProcessingSummary(0, 0, 0, 0));
        var scheduler = new BackgroundJobScheduler(
                processor,
                new BackgroundJobSchedulerProperties(true, "worker-a", 30_000));

        scheduler.runScheduledCycle();

        verify(processor).processOneCycle("worker-a");
    }
}
