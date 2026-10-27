package edu.sjsu.scheduler.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled methods (the notification retry job). */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
