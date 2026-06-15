package com.skm.payments.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

// Off under "test": the embedded-Kafka e2e creates its own topic, and this avoids a broker
// connection attempt during the non-Kafka tests.
@Configuration
@Profile("!test")
public class KafkaConfig {

  public static final String PAYMENT_EVENTS_TOPIC = "payment-events";

  @Bean
  public NewTopic paymentEventsTopic() {
    return TopicBuilder.name(PAYMENT_EVENTS_TOPIC).partitions(3).replicas(1).build();
  }
}
