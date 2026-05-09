package com.example.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Search Service 진입점.
 *
 * <p>{@link EnableScheduling} 은 outbox relay 의 @Scheduled polling 을 위해 필요.</p>
 */
@SpringBootApplication(scanBasePackages = "com.example.search")
@ConfigurationPropertiesScan(basePackages = "com.example.search.bootstrap.config")
@EnableScheduling
public class SearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}
