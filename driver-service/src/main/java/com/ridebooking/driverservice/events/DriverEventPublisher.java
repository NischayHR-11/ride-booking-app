package com.ridebooking.driverservice.events;

import com.google.gson.Gson;
import in.zeta.oms.atropos.client.AtroposPublisherClient;
import in.zeta.oms.atropos.model.PublishMode;
import in.zeta.oms.atropos.response.PublishEventResponse;
import in.zeta.oms.atropos.response.PublishStatus;
import in.zeta.spectra.capture.SpectraLogger;
import olympus.pubsub.model.OperationType;
import olympus.pubsub.model.PubSubEvent;
import olympus.pubsub.model.TopicScope;
import olympus.trace.OlympusSpectra;
import org.apache.http.NameValuePair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/*
 * Design Patterns used in this component:
 *
 * 1. DEPENDENCY INJECTION — AtroposPublisherClient, Gson, and the publish mode
 *    are injected by Spring; no manual construction needed.
 *
 * 2. TEMPLATE METHOD (private publishEvent)
 *    — All public publish methods delegate to a single private publishEvent()
 *      that encapsulates the Atropos SDK boilerplate (builder, mode, status
 *      check, exception wrapping). Only the topic name and payload vary.
 */
@Component
public class DriverEventPublisher {

    private static final SpectraLogger logger = OlympusSpectra.getLogger(DriverEventPublisher.class);

    private final AtroposPublisherClient atroposPublisherClient;
    private final Gson gson;
    private final PublishMode publishMode;

    public DriverEventPublisher(
            AtroposPublisherClient atroposPublisherClient,
            Gson gson,
            @Value("${atropos.publish.mode}") String publishModeString) {
        this.atroposPublisherClient = atroposPublisherClient;
        this.gson = gson;
        this.publishMode = PublishMode.valueOf(publishModeString.toUpperCase());
    }

    public void publishDriverAssigned(DriverAssignedEvent event) {
        publishEvent(event.getRideId(), "driver-assigned", event);
        logger.info("[DriverService] DRIVER_ASSIGNED event published")
                .attr("rideId", event.getRideId())
                .attr("driverId", event.getDriverId())
                .log();
    }

    public void publishNoDriverAvailable(NoDriverAvailableEvent event) {
        publishEvent(event.getRideId(), "no-driver-available", event);
        logger.info("[DriverService] NO_DRIVER_AVAILABLE event published")
                .attr("rideId", event.getRideId())
                .attr("reason", event.getReason())
                .log();
    }

    // -------------------------------------------------------------------------
    // [Template Method Pattern — START]
    // Invariant skeleton: build PubSubEvent → publish → check status → throw on failure
    // -------------------------------------------------------------------------
    private void publishEvent(String objectId, String topic, Object payload) {
        PubSubEvent.Builder builder = new PubSubEvent.Builder()
                .tenant("0")
                .topicScope(TopicScope.SYSTEM)
                .objectType(topic)
                .objectID(objectId)
                .operationType(OperationType.CREATED)
                .sourceAttributes(new NameValuePair[0])
                .tags(List.of())
                .stateMachineState("default")
                .data(gson.toJsonTree(payload));

        try {
            PublishEventResponse response = atroposPublisherClient
                    .publish(builder, publishMode)
                    .toCompletableFuture()
                    .get();

            if (response.getStatus() == PublishStatus.FAILED) {
                logger.error("[DriverService] Failed to publish event to topic: " + topic).log();
                throw new RuntimeException("Atropos publish failed for topic: " + topic);
            }
        } catch (Exception e) {
            logger.error("[DriverService] Exception while publishing event to topic: " + topic, e).log();
            throw new RuntimeException("Atropos publish error", e);
        }
    }
    // [Template Method Pattern — END]
}
