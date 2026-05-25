package com.banking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

// Entry point — nothing fancy here.
// @EnableAsync lets event publishing happen off the main thread if needed later.
@SpringBootApplication
@EnableAsync
public class BankingApplication {
    public static void main(String[] args) {
        SpringApplication.run(BankingApplication.class, args);
    }
}
