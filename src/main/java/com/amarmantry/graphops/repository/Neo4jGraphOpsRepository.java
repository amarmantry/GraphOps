package com.amarmantry.graphops.repository;

import com.amarmantry.graphops.dto.*;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
public class Neo4jGraphOpsRepository implements GraphOpsRepository {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jGraphOpsRepository.class);

    private final Driver driver;

    public Neo4jGraphOpsRepository(Driver driver) {
        this.driver = driver;
    }

    // ---------- Query 1: Dynamic service discovery ----------
    // ---------- Query 1: Dynamic service discovery ----------
    @Override
    public List<ServiceDto> findAllServices() {
        String cypher = """
                MATCH (s:Service)
                RETURN s.name AS name, s.team AS team, s.tier AS tier, s.environment AS environment
                ORDER BY s.name ASC
                """;
        try (Session session = driver.session()) {
            return session.run(cypher).list(r -> new ServiceDto(
                    r.get("name").asString(),
                    r.get("team").isNull() ? null : r.get("team").asString(),
                    r.get("tier").isNull() ? null : r.get("tier").asString(),
                    r.get("environment").isNull() ? null : r.get("environment").asString()
            ));
        } catch (Exception e) {
            logger.error("findAllServices failed", e);
            throw new GraphQueryException("Could not load services from the database", e);
        }
    }

    // ---------- Query 2: Dynamic component discovery (any node type) ----------
    @Override
    public List<ComponentDto> findAllComponents() {
        String cypher = """
                MATCH (n)
                WHERE n:Service OR n:Database OR n:ExternalAPI
                RETURN n.name AS name, labels(n) AS labels,
                       coalesce(n.engine, n.provider) AS engineOrProvider
                ORDER BY labels(n)[0], n.name
                """;
        try (Session session = driver.session()) {
            return session.run(cypher).list(r -> new ComponentDto(
                    r.get("name").asString(),
                    r.get("labels").asList(Value::asString),
                    r.get("engineOrProvider").isNull() ? null : r.get("engineOrProvider").asString()
            ));
        } catch (Exception e) {
            logger.error("findAllComponents failed", e);
            throw new GraphQueryException("Could not load components from the database", e);
        }
    }

    // ---------- Query 3: 1-hop direct dependencies ----------
    @Override
    public List<DependencyDto> findDirectDependencies(String serviceName) {
        String cypher = """
                MATCH (s:Service {name: $serviceName})-[r:CALLS|READS_FROM|WRITES_TO]->(target)
                RETURN s.name AS source, target.name AS target, type(r) AS relType,
                       coalesce(r.protocol, r.operation) AS protocolOrOperation,
                       r.latencyMs AS latencyMs, r.critical AS critical
                ORDER BY target.name
                """;
        try (Session session = driver.session()) {
            return session.run(cypher, Map.of("serviceName", serviceName)).list(this::toDependencyDto);
        } catch (Exception e) {
            logger.error("findDirectDependencies failed for {}", serviceName, e);
            throw new GraphQueryException("Could not load dependencies for " + serviceName, e);
        }
    }

    // ---------- Query 4: multi-hop downstream blast radius ----------
    @Override
    public BlastRadiusResponseDto findBlastRadius(String serviceName, int maxHops) {
        // variable-length traversal, parameterised on hop count via string-built range
        // (Neo4j does not allow parameters inside *min..max, so we validate + inline safely)
        int safeMaxHops = Math.max(1, Math.min(maxHops, 6)); // clamp to avoid runaway traversals on free tier
        String cypher = String.format("""
            MATCH path = (s:Service {name: $serviceName})-[:CALLS|READS_FROM|WRITES_TO*1..%d]->(affected)
            WHERE affected <> s
            WITH affected, min(length(path)) AS hopDistance,
                 any(rel IN relationships(path) WHERE coalesce(rel.critical, false) = true) AS anyCriticalHop
            RETURN affected.name AS name, labels(affected) AS labels, hopDistance, anyCriticalHop
            ORDER BY hopDistance, name
            """, safeMaxHops);

        try (Session session = driver.session()) {
            List<BlastRadiusResponseDto.AffectedNode> affected = session.run(cypher, Map.of("serviceName", serviceName))
                    .list(r -> new BlastRadiusResponseDto.AffectedNode(
                            r.get("name").asString(),
                            r.get("labels").asList(Value::asString),
                            r.get("hopDistance").asInt(),
                            r.get("anyCriticalHop").isNull() ? false : r.get("anyCriticalHop").asBoolean()
                    ));
            return new BlastRadiusResponseDto(serviceName, affected);
        } catch (Exception e) {
            logger.error("findBlastRadius failed for {}", serviceName, e);
            throw new GraphQueryException("Could not compute blast radius for " + serviceName, e);
        }
    }

    // ---------- Query 5: shortest path trace ----------
    @Override
    public PathTraceResponseDto findShortestPath(String sourceName, String targetName) {
        String cypher = """
                MATCH (a {name: $sourceName}), (b {name: $targetName})
                MATCH path = shortestPath((a)-[:CALLS|READS_FROM|WRITES_TO*..8]-(b))
                RETURN path
                """;
        try (Session session = driver.session()) {
            var result = session.run(cypher, Map.of("sourceName", sourceName, "targetName", targetName));
            if (!result.hasNext()) {
                return new PathTraceResponseDto(sourceName, targetName, false, List.of(), List.of(), 0);
            }
            Record record = result.next();
            Path path = record.get("path").asPath();

            List<String> nodeNames = new ArrayList<>();
            for (Node n : path.nodes()) {
                nodeNames.add(n.get("name").asString());
            }
            List<String> relTypes = new ArrayList<>();
            for (Relationship rel : path.relationships()) {
                relTypes.add(rel.type());
            }
            return new PathTraceResponseDto(sourceName, targetName, true, nodeNames, relTypes, path.length());
        } catch (Exception e) {
            logger.error("findShortestPath failed for {} -> {}", sourceName, targetName, e);
            throw new GraphQueryException("Could not trace path between " + sourceName + " and " + targetName, e);
        }
    }

    // ---------- Query 6: shared dependencies (fan-in) ----------
    @Override
    public List<SharedDependencyDto> findSharedDependencies(int minFanIn) {
        String cypher = """
                MATCH (s:Service)-[:CALLS|READS_FROM|WRITES_TO]->(dep)
                WHERE dep:Database OR dep:ExternalAPI
                WITH dep, collect(DISTINCT s.name) AS services
                WHERE size(services) >= $minFanIn
                RETURN dep.name AS depName, labels(dep)[0] AS depType, services, size(services) AS fanIn
                ORDER BY fanIn DESC, depName
                """;
        try (Session session = driver.session()) {
            return session.run(cypher, Map.of("minFanIn", minFanIn)).list(r -> new SharedDependencyDto(
                    r.get("depName").asString(),
                    r.get("depType").asString(),
                    r.get("services").asList(Value::asString),
                    r.get("fanIn").asInt()
            ));
        } catch (Exception e) {
            logger.error("findSharedDependencies failed", e);
            throw new GraphQueryException("Could not compute shared dependencies", e);
        }
    }

    // ---------- helpers ----------
    private DependencyDto toDependencyDto(Record r) {
        return new DependencyDto(
                r.get("source").asString(),
                r.get("target").asString(),
                r.get("relType").asString(),
                r.get("protocolOrOperation").isNull() ? null : r.get("protocolOrOperation").asString(),
                r.get("latencyMs").isNull() ? null : r.get("latencyMs").asInt(),
                r.get("critical").isNull() ? null : r.get("critical").asBoolean()
        );
    }
}