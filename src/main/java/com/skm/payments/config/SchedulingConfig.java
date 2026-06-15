package com.skm.payments.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the scheduled outbox relay. Off under the "test" profile so tests drive it explicitly.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {}
