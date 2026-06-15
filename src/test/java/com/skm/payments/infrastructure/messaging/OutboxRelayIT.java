package com.skm.payments.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.skm.payments.application.PaymentEventTypes;
import com.skm.payments.domain.OutboxEvent;
import com.skm.payments.repository.OutboxRepository;
import com.skm.payments.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/** The relay publishes unpublished rows and marks them — verified with a recording publisher. */
@Import(OutboxRelayIT.RecordingPublisherConfig.class)
class OutboxRelayIT extends AbstractIntegrationTest {

  @Autowired OutboxRelay relay;
  @Autowired OutboxRepository outbox;
  @Autowired RecordingPublisher publisher;

  @TestConfiguration
  static class RecordingPublisherConfig {
    @Bean
    @Primary
    RecordingPublisher recordingPublisher() {
      return new RecordingPublisher();
    }
  }

  static class RecordingPublisher implements EventPublisher {
    final List<String> payloads = new CopyOnWriteArrayList<>();

    @Override
    public void publish(String topic, String key, String payload) {
      payloads.add(payload);
    }
  }

  @Test
  void publishesUnpublishedRowsAndMarksThem() {
    UUID id = UUID.randomUUID();
    OutboxEvent row = new OutboxEvent();
    row.setId(id);
    row.setAggregateType("PAYMENT");
    row.setAggregateId(UUID.randomUUID());
    row.setEventType(PaymentEventTypes.SUCCEEDED);
    row.setPayload("{\"eventId\":\"" + id + "\"}");
    row.setCreatedAt(Instant.now());
    outbox.save(row);

    int published = relay.publishBatch();

    assertThat(published).isGreaterThanOrEqualTo(1);
    assertThat(publisher.payloads).anyMatch(p -> p.contains(id.toString()));
    assertThat(outbox.findById(id).orElseThrow().getPublishedAt()).isNotNull();
  }
}
