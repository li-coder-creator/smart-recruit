package com.recruit.smartrecruit.config;

import com.recruit.smartrecruit.constant.ApplicationConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ApplicationTimeConfig {
    @Bean("applicationClock")
    public Clock applicationClock() {
        return Clock.system(ApplicationConstants.ZONE_ID);
    }
}
