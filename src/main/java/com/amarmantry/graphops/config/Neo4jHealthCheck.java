package com.amarmantry.graphops.config;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class Neo4jHealthCheck implements CommandLineRunner {

    private final Driver driver;

    public Neo4jHealthCheck(Driver driver) {
        this.driver = driver;
    }

    @Override
    public void run(String... args) {
        try (Session session = driver.session()) {
            Result result = session.run("MATCH (n) RETURN count(n) AS totalNodes");
            if (result.hasNext()) {
                long totalNodes = result.next().get("totalNodes").asLong();
                System.out.println("==================================================");
                System.out.println(" Neo4j Connection Successful!");
                System.out.println(" Total Nodes in Database: " + totalNodes);
                System.out.println("==================================================");
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to connect to Neo4j database: " + e.getMessage());
        }
    }
}