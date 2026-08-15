package com.amarmantry.graphops.repository;

import com.amarmantry.graphops.dto.*;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class Neo4jGraphOpsRepository implements GraphOpsRepository {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jGraphOpsRepository.class);
    private final Driver driver;

    public Neo4jGraphOpsRepository(Driver driver) {
        this.driver = driver;
    }

    @Override
    public List<ServiceDto> findAllServices() {
        String query = "MATCH (s:Service) " +
                "RETURN s.name AS name, s.team AS team, s.tier AS tier, s.environment AS environment " +
                "ORDER BY s.name ASC";

        logger.info("Executing: findAllServices");

        try (var session = driver.session()) {
            var result = session.run(query);
            List<ServiceDto> services = result.stream()
                    .map(record -> new ServiceDto(
                            record.get("name").asString(),
                            record.get("team").asString(),
                            record.get("tier").asString(),
                            record.get("environment").asString()
                    ))
                    .toList();

            logger.info("✓ Retrieved {} services", services.size());
            return services;
        } catch (Exception e) {
            logger.error("✗ Failed to fetch services: {}", e.getMessage(), e);
            throw new GraphQueryException("Could not load services from the database", e);
        }
    }

    @Override
    public List<ComponentDto> findAllComponents() {
        String query = "MATCH (n) " +
                "WHERE (n:Service OR n:Database OR n:ExternalAPI) AND n.name IS NOT NULL " +
                "RETURN n.name AS name, labels(n)[0] AS type " +
                "ORDER BY n.name ASC";

        logger.info("Executing: findAllComponents");

        try (var session = driver.session()) {
            var result = session.run(query);
            List<ComponentDto> components = result.stream()
                    .map(record -> new ComponentDto(
                            record.get("name").asString(),
                            record.get("type").asString()
                    ))
                    .toList();

            logger.info("✓ Retrieved {} components", components.size());
            return components;
        } catch (Exception e) {
            logger.error("✗ Failed to fetch components: {}", e.getMessage(), e);
            throw new GraphQueryException("Could not load components from the database", e);
        }
    }

    @Override
    public List<DependencyDto> findDirectDependencies(String serviceName) {
        String query = "MATCH (s:Service {name: $serviceName})-[r:CALLS|READS_FROM|WRITES_TO]->(target) " +
                "RETURN s.name AS source, type(r) AS relationship, properties(r) AS properties, " +
                "target.name AS target, labels(target)[0] AS targetType";

        logger.info("Executing: findDirectDependencies for service: {}", serviceName);

        try (var session = driver.session()) {
            var result = session.run(query, Map.of("serviceName", serviceName));
            List<DependencyDto> dependencies = result.stream()
                    .map(record -> {
                        var props = record.get("properties").asMap();
                        return new DependencyDto(
                                record.get("source").asString(),
                                record.get("relationship").asString(),
                                props,
                                record.get("target").asString(),
                                record.get("targetType").asString()
                        );
                    })
                    .toList();

            logger.info("✓ Retrieved {} direct dependencies for {}", dependencies.size(), serviceName);
            return dependencies;
        } catch (Exception e) {
            logger.error("✗ Failed to fetch dependencies for {}: {}", serviceName, e.getMessage(), e);
            throw new GraphQueryException("Could not load dependencies for service: " + serviceName, e);
        }
    }

    @Override
    public BlastRadiusResponseDto findBlastRadius(String serviceName, int maxHops) {
        String query = "MATCH path = (s:Service {name: $serviceName})-[:CALLS|READS_FROM|WRITES_TO*1.." + maxHops + "]->(downstream) " +
                "RETURN downstream.name AS name, labels(downstream)[0] AS type, length(path)-1 AS hops";

        logger.info("Executing: findBlastRadius for service: {} (maxHops: {})", serviceName, maxHops);

        try (var session = driver.session()) {
            var result = session.run(query, Map.of("serviceName", serviceName));
            List<BlastRadiusResponseDto.AffectedComponent> affected = result.stream()
                    .map(record -> new BlastRadiusResponseDto.AffectedComponent(
                            record.get("name").asString(),
                            record.get("type").asString(),
                            record.get("hops").asInt()
                    ))
                    .toList();

            logger.info("✓ Blast radius analysis for {} returned {} affected components", serviceName, affected.size());
            return new BlastRadiusResponseDto(serviceName, affected);
        } catch (Exception e) {
            logger.error("✗ Failed to analyze blast radius for {}: {}", serviceName, e.getMessage(), e);
            throw new GraphQueryException("Could not analyze blast radius for service: " + serviceName, e);
        }
    }

    @Override
    public PathTraceResponseDto findShortestPath(String source, String target) {
        String query = "MATCH path = shortestPath((src)-[*1..6]->(tgt)) " +
                "WHERE src.name = $source AND tgt.name = $target " +
                "RETURN path";

        logger.info("Executing: findShortestPath from {} to {}", source, target);

        try (var session = driver.session()) {
            var result = session.run(query, Map.of("source", source, "target", target));

            if (result.hasNext()) {
                var record = result.next();
                var path = record.get("path").asPath();
                logger.info("✓ Found path from {} to {} with {} hops", source, target, path.length());
                return new PathTraceResponseDto(source, target, path.length(), true);
            } else {
                logger.info("✓ No path found from {} to {}", source, target);
                return new PathTraceResponseDto(source, target, 0, false);
            }
        } catch (Exception e) {
            logger.error("✗ Failed to trace path from {} to {}: {}", source, target, e.getMessage(), e);
            throw new GraphQueryException("Could not trace path between components", e);
        }
    }

    @Override
    public List<SharedDependencyDto> findSharedDependencies(int minDependents) {
        String query = "MATCH (upstream:Service)-[r:CALLS|READS_FROM|WRITES_TO]->(target) " +
                "WHERE (target:Database OR target:ExternalAPI) " +
                "WITH target, labels(target)[0] AS type, count(DISTINCT upstream) AS dependentCount, " +
                "collect(DISTINCT upstream.name) AS dependents " +
                "WHERE dependentCount >= $minDependents " +
                "RETURN target.name AS component, type, dependentCount, dependents " +
                "ORDER BY dependentCount DESC";

        logger.info("Executing: findSharedDependencies (minDependents: {})", minDependents);

        try (var session = driver.session()) {
            var result = session.run(query, Map.of("minDependents", minDependents));
            List<SharedDependencyDto> bottlenecks = result.stream()
                    .map(record -> new SharedDependencyDto(
                            record.get("component").asString(),
                            record.get("type").asString(),
                            record.get("dependentCount").asInt(),
                            record.get("dependents").asList(Object::toString)
                    ))
                    .toList();

            logger.info("✓ Found {} shared dependencies/bottlenecks", bottlenecks.size());
            return bottlenecks;
        } catch (Exception e) {
            logger.error("✗ Failed to find shared dependencies: {}", e.getMessage(), e);
            throw new GraphQueryException("Could not analyze shared dependencies", e);
        }
    }
}