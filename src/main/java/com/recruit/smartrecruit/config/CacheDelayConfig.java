package com.recruit.smartrecruit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class CacheDelayConfig {

    @Bean
    public ScheduledExecutorService cacheDelayExecutor() {
        return Executors.newScheduledThreadPool(2);
    }
}