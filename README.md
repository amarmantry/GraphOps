# GraphOps — Dependency Intelligence for Distributed Systems

GraphOps is a dependency intelligence and cascading failure simulation platform for distributed microservice architectures. Backed by **CognoDB** (a managed graph database speaking openCypher over Bolt) and built with Spring Boot, it models service-to-service calls, database operations, and third-party integrations into a queryable dependency graph to analyze multi-hop blast radii, trace critical execution routes, and detect infrastructure bottlenecks.

* **Live Demo:** [https://graphops-production-ea65.up.railway.app/](https://graphops-production-ea65.up.railway.app/)

* **Video Walkthrough (< 2 mins):** [Watch the GraphOps Loom Demo](https://www.loom.com/share/0aa6d06cbf1649bcaf83dd838bb76099)


---

## 1. Why a Graph Database?

In distributed systems, critical questions like **"If Service X fails, what downstream systems and databases break?"** or **"What is the exact call chain between the API Gateway and PaymentsDB?"** are fundamentally graph traversal problems.

### The Relational SQL Problem

In a relational database, dependencies must be stored in an adjacency table (`edges: source_id, target_id, rel_type`). Answering variable-depth questions requires recursive Common Table Expressions (CTEs) or multiple self-`JOIN` operations:

* **Computational Cost:** Recursive CTEs execute iterative table scans and global index lookups at every single hop level.
* **Schema Rigidity:** Querying across heterogeneous entity types (`Service`, `Database`, `ExternalAPI`) with edge-level metadata (such as `latencyMs` or `protocol`) requires awkward joins across multiple tables.


* **No Native Paths:** Relational tables return flattened sets of rows rather than the continuous execution path connecting components.



### The Graph Advantage with CognoDB

* **Direct Pointer Traversal (Index-Free Adjacency):** Relationships are stored as direct physical references between nodes, traversing connections without global table scans.


* **Native Multi-Hop Queries:** openCypher queries express variable-depth reachability (`*1..4`) and shortest-path calculations in declarative statements.


* **Rich Relationship Semantics:** Properties like `latencyMs`, `protocol`, and `critical` exist directly on relationships without junction tables.



---

## 2. Graph Data Model & Topology

The graph models a realistic production e-commerce checkout and authentication infrastructure comprising **16 nodes** and **20 typed relationships**.

```mermaid
graph LR
    subgraph Services [P0 / P1 / P2 Services]
        GW[APIGateway] -->|CALLS {critical: true, 65ms}| ORD[OrderService]
        GW -->|CALLS {critical: true, 35ms}| AUTH[AuthService]
        GW -->|CALLS {critical: false, 20ms}| ANALYTICS[AnalyticsService]
        ORD -->|CALLS {protocol: gRPC, 80ms}| PAY[PaymentService]
        ORD -->|CALLS {protocol: gRPC, 45ms}| INV[InventoryService]
        ORD -->|CALLS {protocol: gRPC, 90ms}| FRAUD[FraudService]
        ORD -->|CALLS {protocol: ASYNC, 15ms}| NOTIFY[NotificationService]
    end

    subgraph Databases [Storage & Caches]
        AUTH -->|READS_FROM / WRITES_TO| UDB[(UserDB - PostgreSQL)]
        ORD -->|READS_FROM| UDB
        PAY -->|READS_FROM / WRITES_TO| PDB[(PaymentsDB - PostgreSQL)]
        INV -->|READS_FROM / WRITES_TO| IDB[(ProductsDB - PostgreSQL)]
        FRAUD -->|READS_FROM / WRITES_TO| FCACHE[(FraudCache - Redis)]
        ANALYTICS -->|WRITES_TO| DL[(DataLake - ClickHouse)]
    end

    subgraph ExternalAPIs [Third-Party Services]
        PAY -->|CALLS {critical: true}| STRIPE[StripeAPI]
        NOTIFY -->|CALLS {critical: false}| SENDGRID[SendGridAPI]
        AUTH -->|CALLS {critical: false}| PERSONA[PersonaVerify]
    end

```

### Schema Summary

* **16 Nodes across 3 Labels:**

* **`:Service` (8 nodes):** `APIGateway`, `AuthService`, `OrderService`, `PaymentService`, `InventoryService`, `FraudService`, `NotificationService`, `AnalyticsService`

* **`:Database` (5 nodes):** `UserDB`, `PaymentsDB`, `ProductsDB`, `FraudCache`, `DataLake`

* **`:ExternalAPI` (3 nodes):** `StripeAPI`, `SendGridAPI`, `PersonaVerify`



* **20 Relationships across 3 Types:**

* `CALLS`: `Service` $\rightarrow$ `Service` or `Service` $\rightarrow$ `ExternalAPI` (contains `protocol`, `latencyMs`, `critical`)


* `READS_FROM`: `Service` $\rightarrow$ `Database`

* `WRITES_TO`: `Service` $\rightarrow$ `Database` (contains `operation`, `critical`)





---

## 3. Core Features & Cypher Queries

All Cypher queries in GraphOps are fully parameterized using `Map.of()` parameters through the official Neo4j Java driver to prevent Cypher injection.

### 1. Service Dependencies (1-Hop Inspection)

Retrieves all direct outbound service-to-service calls, database reads/writes, and external API requests with protocol and latency metadata.

```cypher
MATCH (s:Service {name: $serviceName})-[r:CALLS|READS_FROM|WRITES_TO]->(target)
RETURN s.name AS source, target.name AS target, type(r) AS relType,
       coalesce(r.protocol, r.operation) AS protocolOrOperation,
       r.latencyMs AS latencyMs, r.critical AS critical
ORDER BY target.name;

```

### 2. Failure Blast Radius (Multi-Hop Traversal)

Evaluates cascading downstream failure risks by discovering all reachable nodes across 1 to 4 hops and detecting if any leg of the chain traverses a critical path.

```cypher
MATCH path = (s:Service {name: $serviceName})-[:CALLS|READS_FROM|WRITES_TO*1..4]->(affected)
WHERE affected <> s
WITH affected, min(length(path)) AS hopDistance,
     any(rel IN relationships(path) WHERE coalesce(rel.critical, false) = true) AS anyCriticalHop
RETURN affected.name AS name, labels(affected) AS labels, hopDistance, anyCriticalHop
ORDER BY hopDistance, name;

```

### 3. Path Trace (Shortest Path Discovery)

Finds the shortest dependency chain between any two infrastructure components across up to 8 hops.

```cypher
MATCH (a {name: $sourceName}), (b {name: $targetName})
MATCH path = shortestPath((a)-[:CALLS|READS_FROM|WRITES_TO*..8]-(b))
RETURN path;

```

### 4. Bottleneck Detection (Fan-In Analysis)

Aggregates shared databases and external APIs to surface single points of failure across the architecture.

```cypher
MATCH (s:Service)-[:CALLS|READS_FROM|WRITES_TO]->(dep)
WHERE dep:Database OR dep:ExternalAPI
WITH dep, collect(DISTINCT s.name) AS services
WHERE size(services) >= $minFanIn
RETURN dep.name AS depName, labels(dep)[0] AS depType, services, size(services) AS fanIn
ORDER BY fanIn DESC, depName;

```

---

## 4. Tech Stack & Architecture

* **Backend:** Java 21, Spring Boot 3.3.x, official `neo4j-java-driver` over Bolt (`bolt+s://`)


* **Database:** CognoDB Cloud (Managed openCypher Graph Database)


* **Frontend:** Thymeleaf + Tailwind CSS (server-side rendered, zero Node.js build overhead)
* **Hosting:** Railway (Auto-deploy on GitHub push)

```
graphops/
├── pom.xml                                  # Maven dependencies (Spring Boot, neo4j-java-driver)
├── scripts/
│   └── seed.cypher                          # Idempotent Cypher seed script (16 nodes, 20 edges)
├── src/main/
│   ├── java/com/amarmantry/graphops/
│   │   ├── GraphopsApplication.java         # Application entry point
│   │   ├── config/
│   │   │   ├── Neo4jDriverConfig.java       # Singleton Bolt Driver bean with env credentials
│   │   │   └── Neo4jHealthCheck.java        # Startup connection verification
│   │   ├── controller/
│   │   │   ├── GraphOpsViewController.java   # UI controller handling view models & dbError states
│   │   │   └── GraphOpsDebugController.java  # REST endpoints for API verification
│   │   ├── dto/                             # Strongly-typed record DTOs
│   │   └── repository/
│   │       ├── GraphOpsRepository.java       # Repository interface
│   │       ├── Neo4jGraphOpsRepository.java  # Parameterized Cypher driver implementation
│   │       └── GraphQueryException.java      # Runtime exception for query failures
│   └── resources/
│       ├── application.properties           # Driver connection variable placeholders
│       └── templates/
│           ├── layout.html                  # Tailwind master layout & navigation
│           ├── dashboard.html               # 1-hop dependencies view
│           ├── blast-radius.html            # Multi-hop blast radius simulation view
│           ├── explore.html                 # Shortest path & bottleneck view
│           └── architecture.html            # In-app graph schema & data flow view
└── .env.example                             # Environment variable template

```

---

## 5. Local Setup & Seeding

### Prerequisites

* Java 21+
* Maven 3.8+
* A free CognoDB Cloud account (`console.cognodb.com`)



### 1. Clone & Configure

```bash
git clone https://github.com/amarmantry/GraphOps.git
cd GraphOps

# Create local environment configuration
cp .env.example .env

```

Configure your CognoDB instance credentials in `.env`:

```env
NEO4J_URI=bolt+s://YOUR_INSTANCE_ID.databases.cognodb.cloud
NEO4J_USERNAME=cognodb
NEO4J_PASSWORD=YOUR_COGNODB_PASSWORD

```

### 2. Seed CognoDB

1. Log in to [console.cognodb.com](https://www.google.com/search?q=https://console.cognodb.com) and open your instance's **Cypher Console**.


2. Copy and run the entire contents of `scripts/seed.cypher`. The script is idempotent and safe to re-run.



### 3. Run Application

```bash
./mvnw clean spring-boot:run

```

Access the application at `http://localhost:8080`.

---

## 6. Deployment & Engineering Resilience

* **Live Hosted URL:** [https://graphops-production-ea65.up.railway.app/](https://graphops-production-ea65.up.railway.app/)

* **Zero Hardcoded Secrets:** Credentials are read exclusively from environment variables (`NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD`).


* **Graceful Degradation:** If CognoDB Cloud is unreachable or experiencing network latency, queries are trapped in `GraphQueryException` and the UI renders a clean error notification banner without crashing the server.


* **Sanitized Query Bounds:** Traversal range limits are clamped in application logic (`Math.max(1, Math.min(maxHops, 6))`) prior to executing Cypher queries.



---

## 7. Submission Checklist Verification

* [x] **Graph Data Model:** Labeled nodes (`Service`, `Database`, `ExternalAPI`), typed relationships (`CALLS`, `READS_FROM`, `WRITES_TO`), and rich properties (`latencyMs`, `critical`, `tier`, `engine`).


* [x] **Seed Script:** Repeatable `scripts/seed.cypher` creating 16 nodes and 20 relationships.


* [x] **Cypher Traversals:** Multi-hop traversal (`*1..4`), shortest-path finding (`shortestPath`), and fan-in aggregation.


* [x] **Parameterized Queries:** 100% of user inputs passed via `Map.of()` parameters using the official Neo4j Java driver.


* [x] **Polished UI/UX:** Server-rendered dashboard with dropdown selectors, loading states, empty prompt cards, and database error banners.


* [x] **Engineering & Secrets:** Secrets loaded strictly from environment variables; singleton driver connection pooling; controller-level graceful degradation.


* [x] **Live Hosted Demo:** Deployed and publicly accessible on Railway.


* [x] **Walkthrough Video:** Screen recording demonstrating live application and query execution.



---

## License

MIT
