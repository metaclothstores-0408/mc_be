package com.metacloth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables async execution for headless DTF production pipeline.
 * Isolated — does not affect existing request threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig { }
