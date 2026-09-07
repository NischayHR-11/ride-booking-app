package com.ridebooking.rideservice.events;

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
 *      that handles the Atropos SDK boilerplate (builder construction, mode,
 *      error handling). Each public method only supplies what varies: the
 *      topic name and the payload object.
 */
@Component
public class RideEventPublisher {

    private static final SpectraLogger logger = OlympusSpectra.getLogger(RideEventPublisher.class);

    private final AtroposPublisherClient atroposPublisherClient;
    private final Gson gson;
    private final PublishMode publishMode;

    public RideEventPublisher(
            AtroposPublisherClient atroposPublisherClient,
            Gson gson,
            @Value("${atropos.publish.mode}") String publishModeString) {
        this.atroposPublisherClient = atroposPublisherClient;
        this.gson = gson;
        this.publishMode = PublishMode.valueOf(publishModeString.toUpperCase());
    }

    public void publishRideRequested(RideRequestedEvent event) {
        publishEvent(event.getRideId(), "ride-requested", event, TopicScope.SYSTEM);
        logger.info("[RideService] RIDE_REQUESTED event published")
                .attr("rideId", event.getRideId())
                .attr("riderId", event.getRiderId())
                .log();
    }

    public void publishRideCancelled(RideCancelledEvent event) {
        publishEvent(event.getRideId(), "ride-cancelled", event, TopicScope.SYSTEM);
        logger.info("[RideService] RIDE_CANCELLED event published")
                .attr("rideId", event.getRideId())
                .log();
    }

    public void publishRideCompleted(RideCompletedEvent event) {
        publishEvent(event.getRideId(), "ride-completed", event, TopicScope.SYSTEM);
        logger.info("[RideService] RIDE_COMPLETED event published")
                .attr("rideId", event.getRideId())
                .attr("driverId", event.getDriverId())
                .log();
    }

    // -------------------------------------------------------------------------
    // [Template Method Pattern — START]
    // Invariant skeleton shared by all publish methods:
    //   build PubSubEvent → call Atropos SDK → check status → throw on failure
    // -------------------------------------------------------------------------
    private void publishEvent(String objectId, String topic, Object payload, TopicScope scope) {
        PubSubEvent.Builder builder = new PubSubEvent.Builder()
                .tenant("0")
                .topicScope(scope)
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
                logger.error("[RideService] Failed to publish event to topic: " + topic).log();
                throw new RuntimeException("Atropos publish failed for topic: " + topic);
            }
        } catch (Exception e) {
            logger.error("[RideService] Exception while publishing event to topic: " + topic, e).log();
            throw new RuntimeException("Atropos publish error", e);
        }
    }
    // [Template Method Pattern — END]
}
