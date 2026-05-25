package com.banking.infrastructure.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

// AOP aspect that automatically records latency + outcome for every @UseCase method.
// No instrumentation code needed in the services themselves — this intercepts them all.
// Metrics land in Micrometer as banking.usecase.<method_name>{class, outcome}.
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionLoggingAspect {

    private final MeterRegistry meterRegistry;

    @Around("@within(com.banking.infrastructure.annotation.UseCase)")
    public Object measureUseCase(ProceedingJoinPoint pjp) throws Throwable {
        String className  = pjp.getTarget().getClass().getSimpleName();
        String methodName = pjp.getSignature().getName();
        String metricName = "banking.usecase." + toSnakeCase(methodName);

        long start = System.nanoTime();
        String outcome = "success";

        try {
            return pjp.proceed();
        } catch (Exception e) {
            outcome = "error";
            throw e;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            Timer.builder(metricName)
                .tag("class", className)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);

            log.debug("UseCase executed: {}.{}() outcome={} durationMs={}",
                className, methodName, outcome, durationMs);
        }
    }

    private String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }
}
