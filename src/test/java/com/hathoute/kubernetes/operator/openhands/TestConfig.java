package com.hathoute.kubernetes.operator.openhands;

import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
public class TestConfig {

  // Disable ServerSideApply (doesn't work with Kubernetes Mockserver)
  @Bean
  Consumer<ConfigurationServiceOverrider> noSsaConfigOverrider() {
    return o -> o.withUseSSAToPatchPrimaryResource(false)
        .withSSABasedCreateUpdateMatchForDependentResources(false);
  }

}
