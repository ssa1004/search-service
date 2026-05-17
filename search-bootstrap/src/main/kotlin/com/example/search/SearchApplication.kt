package com.example.search

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Search Service 진입점.
 *
 * [EnableScheduling] 은 outbox relay 의 @Scheduled polling 을 위해 필요.
 */
@SpringBootApplication(scanBasePackages = ["com.example.search"])
@ConfigurationPropertiesScan(basePackages = ["com.example.search.bootstrap.config"])
@EnableScheduling
class SearchApplication

fun main(args: Array<String>) {
    SpringApplication.run(SearchApplication::class.java, *args)
}
