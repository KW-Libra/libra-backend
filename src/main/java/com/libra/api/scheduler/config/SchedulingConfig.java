package com.libra.api.scheduler.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring 스케줄링 활성화. 실제 일일 트리거 빈
 * ({@link com.libra.api.scheduler.service.DailyRebalanceScheduler}) 은
 * {@code libra.scheduler.enabled=true} 일 때만 등록된다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
