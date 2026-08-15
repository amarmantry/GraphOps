package com.amarmantry.graphops.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class Neo4jDriverConfig {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jDriverConfig.class);

    @Bean
    public Driver neo4jDriver() {
        String uri = System.getenv().getOrDefault("NEO4J_URI", "bolt+s://localhost:7687");
        String username = System.getenv().getOrDefault("NEO4J_USERNAME", "neo4j");
        String password = System.getenv("NEO4J_PASSWORD");

        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException(
                    "NEO4J_PASSWORD environment variable must be set. " +
                            "See .env.example for format."
            );
        }

        logger.info("Creating Neo4j Driver for: {}", uri);

        Driver driver = GraphDatabase.driver(
                uri,
                AuthTokens.basic(username, password)
        );

        // Non-fatal connectivity check (log warning but don't fail startup)
        try {
            var session = driver.session();
            var result = session.run("RETURN 1 AS num");
            result.consume();
            session.close();
            logger.info("✓ Connected to CognoDB successfully");
        } catch (Exception e) {
            // Log warning but allow app to start
            // Errors will be handled at query time by GlobalExceptionHandler
            logger.warn("⚠ Could not verify CognoDB connection at startup: {}", e.getMessage());
            logger.warn("  The app will start, but queries will fail until the database is available.");
        }

        return driver;
    }
}