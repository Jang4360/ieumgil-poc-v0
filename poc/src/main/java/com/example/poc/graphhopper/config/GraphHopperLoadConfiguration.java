package com.example.poc.graphhopper.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "poc.graphhopper-load", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(GraphHopperLoadProperties.class)
public class GraphHopperLoadConfiguration {
}
