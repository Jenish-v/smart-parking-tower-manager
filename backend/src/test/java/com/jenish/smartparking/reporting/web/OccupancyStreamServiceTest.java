package com.jenish.smartparking.reporting.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jenish.smartparking.allocation.application.OccupancyService;
import com.jenish.smartparking.allocation.application.OccupancySnapshot;
import com.jenish.smartparking.allocation.application.OccupancySnapshot.FloorOccupancy;
import com.jenish.smartparking.facility.domain.FacilityId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class OccupancyStreamServiceTest {

    private static final FacilityId FACILITY_ID =
            new FacilityId(UUID.fromString("d936bb7d-3027-47aa-a47b-d04a37e07310"));

    @Test
    void sendsTheInitialSnapshotAndChangedOccupancy() throws Exception {
        OccupancyService occupancyService = mock(OccupancyService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        SseEmitter emitter = mock(SseEmitter.class);
        OccupancySnapshot initial = snapshot(0, 7_200);
        OccupancyStreamService stream = new OccupancyStreamService(
                occupancyService, scheduler, () -> emitter);
        when(occupancyService.getOccupancy(FACILITY_ID)).thenReturn(snapshot(1, 7_199));

        stream.subscribe(FACILITY_ID, initial);
        stream.broadcast();

        verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        assertEquals(1, stream.activeFacilityCount());
    }

    @Test
    void removesACompletedSubscriber() throws Exception {
        OccupancyService occupancyService = mock(OccupancyService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        SseEmitter emitter = mock(SseEmitter.class);
        OccupancyStreamService stream = new OccupancyStreamService(
                occupancyService, scheduler, () -> emitter);
        ArgumentCaptor<Runnable> completion = ArgumentCaptor.forClass(Runnable.class);

        stream.subscribe(FACILITY_ID, snapshot(0, 7_200));
        verify(emitter).onCompletion(completion.capture());
        clearInvocations(emitter);
        completion.getValue().run();

        assertEquals(0, stream.activeFacilityCount());
    }

    @Test
    void closesSubscribersWhenRefreshFails() throws Exception {
        OccupancyService occupancyService = mock(OccupancyService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        SseEmitter emitter = mock(SseEmitter.class);
        OccupancyStreamService stream = new OccupancyStreamService(
                occupancyService, scheduler, () -> emitter);
        RuntimeException failure = new RuntimeException("database unavailable");
        when(occupancyService.getOccupancy(FACILITY_ID)).thenThrow(failure);

        stream.subscribe(FACILITY_ID, snapshot(0, 7_200));
        stream.broadcast();

        verify(emitter).completeWithError(failure);
        assertEquals(0, stream.activeFacilityCount());
    }

    private static OccupancySnapshot snapshot(long occupied, long available) {
        return new OccupancySnapshot(
                FACILITY_ID,
                Instant.parse("2026-08-22T22:00:00Z").plusSeconds(occupied),
                7_200,
                7_200,
                occupied,
                available,
                List.of(new FloorOccupancy(1, 1_200, 1_200, occupied, 1_200 - occupied)));
    }
}
