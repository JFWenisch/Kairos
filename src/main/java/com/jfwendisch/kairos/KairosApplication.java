package com.jfwendisch.kairos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KairosApplication {
    public static void main(String[] args) {
        SpringApplication.run(KairosApplication.class, args);
    }
}
