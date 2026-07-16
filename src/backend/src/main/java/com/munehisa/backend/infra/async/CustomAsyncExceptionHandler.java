package com.munehisa.backend.infra.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;

@Slf4j
public class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        // params are deliberately omitted: async methods in this codebase (e.g. email
        // sending) receive verification/reset tokens as arguments, which must not be logged.
        log.error("Uncaught exception in async method '{}.{}'",
                method.getDeclaringClass().getSimpleName(), method.getName(), ex);
    }
}
