package com.printerhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PrinterHub application entry point.
 *
 * Annotations explained:
 *
 * @SpringBootApplication — shorthand for @Configuration + @EnableAutoConfiguration + @ComponentScan.
 *   Auto-configures Tomcat, JPA, Jackson, etc. based on what's on the classpath.
 *
 * @EnableJpaAuditing — activates the @CreatedDate / @LastModifiedDate fields
 *   on our entities (defined in the core module).
 *
 * @EnableScheduling — allows @Scheduled methods (used later for polling adapters
 *   and pushing heartbeats over WebSocket).
 *
 * @EntityScan / @EnableJpaRepositories — needed because our entities and
 *   repositories live in the 'core' module, not this module's package.
 *   Without these, Spring Boot wouldn't scan across module boundaries.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EntityScan(basePackages = "com.printerhub.core.entity")
@EnableJpaRepositories(basePackages = "com.printerhub.core.repository")
public class PrinterHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrinterHubApplication.class, args);
    }
}
