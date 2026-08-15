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

    /**
     * Creates a singleton Neo4j Driver bean.
     *
     * Credentials are read from environment variables:
     * - NEO4J_URI: bolt+s://instance-id.databases.cognodb.cloud
     * - NEO4J_USERNAME: cognodb
     * - NEO4J_PASSWORD: your-password (from CognoDB console)
     *
     * The Driver is thread-safe and should be reused for all queries.
     */
    @Bean
    public Driver neo4jDriver() {
        // Read from environment variables (with sensible defaults for local dev)
        String uri = System.getenv().getOrDefault("NEO4J_URI", "bolt+s://localhost:7687");
        String username = System.getenv().getOrDefault("NEO4J_USERNAME", "neo4j");
        String password = System.getenv("NEO4J_PASSWORD");

        // Validate that password is provided
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException(
                    "NEO4J_PASSWORD environment variable must be set. " +
                            "See .env.example for format."
            );
        }

        logger.info("Connecting to Neo4j at: {}", uri);

        // Create Driver with authentication
        Driver driver = GraphDatabase.driver(
                uri,
                AuthTokens.basic(username, password)
        );

        // Test connectivity on startup
        try {
            var session = driver.session();
            var result = session.run("RETURN 1 AS num");
            result.consume();
            session.close();
            logger.info("✓ Connected to CognoDB successfully");
        } catch (Exception e) {
            logger.error("✗ Failed to connect to CognoDB", e);
            throw new RuntimeException("Database connection failed on startup", e);
        }

        return driver;
    }
}