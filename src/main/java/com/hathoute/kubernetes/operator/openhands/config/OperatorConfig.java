package com.hathoute.kubernetes.operator.openhands.config;

import com.hathoute.kubernetes.operator.openhands.crd.LLMTaskResource;
import io.javaoperatorsdk.operator.processing.event.source.inbound.SimpleInboundEventSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OperatorConfig {

  @Bean
  SimpleInboundEventSource<LLMTaskResource> reporterEventSource() {
    return new SimpleInboundEventSource<>("reporter-event-source");
  }
}
