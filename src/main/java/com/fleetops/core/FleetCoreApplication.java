package com.fleetops.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FleetCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(FleetCoreApplication.class, args);
    }
}
