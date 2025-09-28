package io.openauth.sim.rest.ui;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestOperatorUiConfiguration {

  @Bean
  RestTemplate ocraUiRestTemplate(RestTemplateBuilder builder) {
    return builder.build();
  }
}
