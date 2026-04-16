package com.example.poc.network.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NetworkBuildConfiguration {

	@Bean
	Clock networkBuildClock() {
		return Clock.systemUTC();
	}
}
