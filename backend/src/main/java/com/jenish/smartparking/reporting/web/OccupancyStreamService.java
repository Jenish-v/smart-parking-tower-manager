package com.jenish.smartparking.reporting.web;

import com.jenish.smartparking.allocation.application.OccupancyService;
import com.jenish.smartparking.allocation.application.OccupancySnapshot;
import com.jenish.smartparking.facility.domain.FacilityId;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@Lazy
public class OccupancyStreamService {

    private static final long REFRESH_SECONDS = 5;

    private static final long NO_TIMEOUT = 0L;

    private final OccupancyService occupancyService;

    private final ScheduledExecutorService scheduler;

    private final Supplier<SseEmitter> emitterFactory;

    private final Map<FacilityId, Channel> channels = new ConcurrentHashMap<>();

    @Autowired
    public OccupancyStreamService(@Lazy OccupancyService occupancyService) {
        this(
                occupancyService,
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "occupancy-stream-refresh");
                    thread.setDaemon(true);
                    return thread;
                }),
                () -> new SseEmitter(NO_TIMEOUT));
        scheduler.scheduleWithFixedDelay(
                this::broadcast,
                REFRESH_SECONDS,
                REFRESH_SECONDS,
                TimeUnit.SECONDS);
    }

    OccupancyStreamService(
            OccupancyService occupancyService,
            ScheduledExecutorService scheduler,
            Supplier<SseEmitter> emitterFactory) {
        this.occupancyService = occupancyService;
        this.scheduler = scheduler;
        this.emitterFactory = emitterFactory;
    }

    public SseEmitter subscribe(FacilityId facilityId, OccupancySnapshot initialSnapshot) {
        SseEmitter emitter = emitterFactory.get();
        Channel channel = channels.computeIfAbsent(facilityId, ignored -> new Channel(initialSnapshot));
        channel.emitters().add(emitter);
        Runnable cleanup = () -> remove(facilityId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        sendSnapshot(facilityId, emitter, initialSnapshot);
        return emitter;
    }

    void broadcast() {
        channels.forEach((facilityId, channel) -> {
            try {
                OccupancySnapshot current = occupancyService.getOccupancy(facilityId);
                if (sameCounts(channel.lastSnapshot(), current)) {
                    sendHeartbeat(facilityId, channel);
                } else {
                    channel.lastSnapshot(current);
                    channel.emitters().forEach(emitter -> sendSnapshot(facilityId, emitter, current));
                }
            } catch (RuntimeException exception) {
                fail(facilityId, channel, exception);
            }
        });
    }

    int activeFacilityCount() {
        return channels.size();
    }

    private void sendSnapshot(FacilityId facilityId, SseEmitter emitter, OccupancySnapshot snapshot) {
        try {
            emitter.send(SseEmitter.event()
                    .id(snapshot.capturedAt().toString())
                    .name("occupancy")
                    .data(OccupancyResponse.from(snapshot)));
        } catch (IOException | IllegalStateException exception) {
            remove(facilityId, emitter);
            emitter.complete();
        }
    }

    private void sendHeartbeat(FacilityId facilityId, Channel channel) {
        channel.emitters().forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException exception) {
                remove(facilityId, emitter);
                emitter.complete();
            }
        });
    }

    private void fail(FacilityId facilityId, Channel channel, RuntimeException exception) {
        channels.remove(facilityId, channel);
        channel.emitters().forEach(emitter -> emitter.completeWithError(exception));
        channel.emitters().clear();
    }

    private void remove(FacilityId facilityId, SseEmitter emitter) {
        channels.computeIfPresent(facilityId, (ignored, channel) -> {
            channel.emitters().remove(emitter);
            return channel.emitters().isEmpty() ? null : channel;
        });
    }

    private static boolean sameCounts(OccupancySnapshot first, OccupancySnapshot second) {
        return first.totalSpaces() == second.totalSpaces()
                && first.operationalSpaces() == second.operationalSpaces()
                && first.occupiedSpaces() == second.occupiedSpaces()
                && first.availableSpaces() == second.availableSpaces()
                && first.floors().equals(second.floors());
    }

    @PreDestroy
    void close() {
        channels.values().forEach(channel -> channel.emitters().forEach(SseEmitter::complete));
        channels.clear();
        scheduler.shutdownNow();
    }

    private static final class Channel {

        private final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();

        private volatile OccupancySnapshot lastSnapshot;

        private Channel(OccupancySnapshot lastSnapshot) {
            this.lastSnapshot = lastSnapshot;
        }

        private Set<SseEmitter> emitters() {
            return emitters;
        }

        private OccupancySnapshot lastSnapshot() {
            return lastSnapshot;
        }

        private void lastSnapshot(OccupancySnapshot snapshot) {
            lastSnapshot = snapshot;
        }
    }
}
