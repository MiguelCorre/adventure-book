package com.adventurebook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AdventureBookApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdventureBookApplication.class, args);
    }
}
