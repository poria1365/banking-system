package com.banking.infrastructure.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

// Stereotype annotation for outbound persistence adapters.
// Same idea as @UseCase — @Component registration plus a readable layer label.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface PersistenceAdapter {}
