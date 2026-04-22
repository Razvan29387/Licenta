package com.example.demo.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync // Activează suportul pentru metode asincrone
public class AsyncConfig {

    /**
     * Definește un pool de thread-uri personalizat pentru sarcinile asincrone.
     * Acest lucru ne permite să controlăm numărul de request-uri paralele.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Numărul de thread-uri de bază, active în permanență
        executor.setCorePoolSize(5); 
        // Numărul maxim de thread-uri care pot fi create
        executor.setMaxPoolSize(10); 
        // Numărul de sarcini care pot aștepta în coadă
        executor.setQueueCapacity(25); 
        executor.setThreadNamePrefix("ImportThread-");
        executor.initialize();
        return executor;
    }
}