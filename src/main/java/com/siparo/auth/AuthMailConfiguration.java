package com.siparo.auth;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration @EnableAsync
public class AuthMailConfiguration {
    @Bean public Executor authMailExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); executor.setMaxPoolSize(4); executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("auth-mail-"); executor.initialize();
        return executor;
    }
}
