package com.banking.infrastructure.annotation;

import org.springframework.stereotype.Service;

import java.lang.annotation.*;

// Stereotype annotation for application-layer use cases.
// @Service makes it a Spring bean; the name helps with AOP pointcut matching
// and makes the layer boundary obvious at a glance.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
public @interface UseCase {}
