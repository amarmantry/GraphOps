# GraphOps — Microservice Dependency & Blast Radius Intelligence

GraphOps is a dependency intelligence and failure-simulation tool for distributed systems, built on **CognoDB** (a managed graph database speaking openCypher over Bolt). It maps services, databases, and third-party APIs into an infrastructure graph to analyze multi-hop failure blast radii, trace critical execution paths, and identify shared infrastructure bottlenecks.

**Live Hosted Demo:** [https://graphops-production-ea65.up.railway.app/](https://graphops-production-ea65.up.railway.app/)

**Walkthrough Video (< 3 mins):** [Watch the GraphOps Demo on Loom](https://www.loom.com/share/YOUR_VIDEO_ID)

---

## 1. Why a Graph Database?

In modern microservice architectures, answering operational questions such as **"If Service X fails, what downstream components are affected?"** or **"What is the exact dependency route from Service A to Database B?"** is fundamentally a graph reachability problem.

### The Relational SQL Problem

In a relational database, storing connections in an adjacency table (`edges: source_id, target_id, rel_type`) requires recursive Common Table Expressions (CTEs) or multiple self-`JOIN`s to query paths of unknown depth.

* Recursive CTEs execute iterative table scans and global index lookups at every hop.
* Querying across heterogeneous entity types (`Service`, `Database`, `ExternalAPI`) with edge-specific properties requires verbose, fragile joins across multiple tables.


* Relational queries return flattened rows rather than returning the traversed path as a structured entity.



### The Graph Advantage in CognoDB

* **Direct Pointer Traversal:** CognoDB follows relationships locally between adjacent nodes without performing global table joins.


* **Concise Path Queries:** Variable-length paths (`*1..4`) and shortest-path algorithms are native language constructs in openCypher.


* **Rich Edge Semantics:** Edges natively encapsulate properties such as `latencyMs`, `protocol`, and `critical` without requiring secondary junction tables.



---

## 2. Graph Data Model

The graph consists of **16 nodes** and **20 typed relationships** representing a production e-commerce and checkout backend.

```
                     ┌──────────────────┐
                     │   APIGateway     │
                     └──┬──────┬──────┬─┘
           CALLS (35ms) │      │      │ CALLS (20ms, non-critical)
        ┌───────────────┘      │      └─────────────────┐
        ▼                      ▼ CALLS (65ms)           ▼
 ┌──────────────┐     ┌──────────────────┐     ┌──────────────────┐
 │ AuthService  │     │   OrderService   │     │ AnalyticsService │
 └──────┬───────┘     └──┬──────┬──────┬─┘     └────────┬─────────┘
        │                │      │      │                │ WRITES_TO
        │                │      │      │ CALLS (15ms)   ▼
        │   CALLS (80ms) │      │      └────────► ┌──────────────────┐
        │ ┌──────────────┘      │                 │NotificationService│
        │ │                     │ CALLS (45ms)    └────────┬─────────┘
        ▼ ▼                     ▼                          │ CALLS
 ┌──────────────┐     ┌──────────────────┐                 ▼
 │PaymentService│     │ InventoryService │        ┌──────────────────┐
 └──────┬───────┘     └────────┬─────────┘        │   SendGridAPI    │
        │                      │                  │  (ExternalAPI)   │
        │ WRITES_TO            │ WRITES_TO        └──────────────────┘
        ▼                      ▼
 ┌──────────────┐     ┌──────────────────┐
 │  PaymentsDB  │     │   ProductsDB     │
 │  (Database)  │     │   (Database)     │
 └──────────────┘     └──────────────────┘

```

### Node Labels & Properties

* **`:Service` (8 nodes):** `name`, `team`, `tier` (`P0`, `P1`, `P2`), `environment`

* `APIGateway`, `AuthService`, `OrderService`, `PaymentService`, `InventoryService`, `FraudService`, `NotificationService`, `AnalyticsService`



* **`:Database` (5 nodes):** `name`, `engine` (`PostgreSQL`, `Redis`, `ClickHouse`), `environment`

* `UserDB`, `PaymentsDB`, `ProductsDB`, `FraudCache`, `DataLake`



* **`:ExternalAPI` (3 nodes):** `name`, `provider`

* `StripeAPI` (Stripe), `SendGridAPI` (Twilio), `PersonaVerify` (Persona)





### Relationship Types & Properties

* **`-[:CALLS]->`**: `Service` $\rightarrow$ `Service` or `Service` $\rightarrow$ `ExternalAPI`

* Properties: `protocol` (`REST`, `gRPC`, `HTTPS`, `ASYNC`), `latencyMs` (integer), `critical` (boolean)




* **`-[:READS_FROM]->`**: `Service` $\rightarrow$ `Database`

* **`-[:WRITES_TO]->`**: `Service` $\rightarrow$ `Database`

* Properties: `operation` (`SYNC`, `ASYNC`), `critical` (boolean)





---

## 3. Core Features & Cypher Queries

All Cypher queries in GraphOps are fully parameterized via `Map.of()` parameters using the official Neo4j Java driver.

### 1. Service Dependencies (1-Hop Inspection)

Retrieves all direct outbound calls, database operations, and API integrations for a chosen service.

```cypher
MATCH (s:Service {name: $serviceName})-[r:CALLS|READS_FROM|WRITES_TO]->(target)
RETURN s.name AS source, target.name AS target, type(r) AS relType,
       coalesce(r.protocol, r.operation) AS protocolOrOperation,
       r.latencyMs AS latencyMs, r.critical AS critical
ORDER BY target.name;

```

### 2. Blast Radius Simulation (Multi-Hop Traversal)

Performs a variable-length traversal (clamped between 1 and 6 hops) to identify all reachable downstream services, databases, and APIs when an outage occurs. It calculates minimum hop distance and detects whether any leg of the dependency chain traverses a critical link.

```cypher
MATCH path = (s:Service {name: $serviceName})-[:CALLS|READS_FROM|WRITES_TO*1..4]->(affected)
WHERE affected <> s
WITH affected, min(length(path)) AS hopDistance,
     any(rel IN relationships(path) WHERE coalesce(rel.critical, false) = true) AS anyCriticalHop
RETURN affected.name AS name, labels(affected) AS labels, hopDistance, anyCriticalHop
ORDER BY hopDistance, name;

```

### 3. Path Trace (Shortest Path Discovery)

Finds the shortest dependency chain between any two infrastructure components (e.g., `APIGateway` to `PaymentsDB`) across up to 8 hops.

```cypher
MATCH (a {name: $sourceName}), (b {name: $targetName})
MATCH path = shortestPath((a)-[:CALLS|READS_FROM|WRITES_TO*..8]-(b))
RETURN path;

```

### 4. Bottleneck Detection (Fan-In Analysis)

Aggregates databases and external APIs that are shared across multiple upstream services to identify single points of failure.

```cypher
MATCH (s:Service)-[:CALLS|READS_FROM|WRITES_TO]->(dep)
WHERE dep:Database OR dep:ExternalAPI
WITH dep, collect(DISTINCT s.name) AS services
WHERE size(services) >= $minFanIn
RETURN dep.name AS depName, labels(dep)[0] AS depType, services, size(services) AS fanIn
ORDER BY fanIn DESC, depName;

```

---

## 4. Engineering Architecture & Code Structure

The project uses a clean layered architecture with Spring Boot and the official `org.neo4j.driver:neo4j-java-driver`.

```
GraphOps-main/
├── pom.xml                                  # Maven project configuration (Java 21 / Spring Boot)
├── scripts/
│   └── seed.cypher                          # Full seed script for nodes, edges & metadata
├── src/main/
│   ├── java/com/amarmantry/graphops/
│   │   ├── GraphopsApplication.java         # Application main class
│   │   ├── config/
│   │   │   ├── Neo4jDriverConfig.java       # Singleton Driver bean reading env vars
│   │   │   └── Neo4jHealthCheck.java        # Startup node count verification
│   │   ├── controller/
│   │   │   ├── GraphOpsViewController.java   # UI controller with error & empty states
│   │   │   └── GraphOpsDebugController.java  # REST endpoints for API verification
│   │   ├── dto/                             # Strongly typed records (ServiceDto, DependencyDto, etc.)
│   │   └── repository/
│   │       ├── GraphOpsRepository.java       # Repository interface
│   │       ├── Neo4jGraphOpsRepository.java  # Parameterized Cypher driver implementation
│   │       └── GraphQueryException.java      # Runtime exception for query failures
│   └── resources/
│       ├── application.properties           # Driver connection variable placeholders
│       └── templates/
│           ├── layout.html                  # Tailwind CSS master layout
│           ├── dashboard.html               # 1-hop dependency view
│           ├── blast-radius.html            # Multi-hop blast radius simulation view
│           └── explore.html                 # Shortest path & bottleneck view
└── .env.example                             # Environment variable template

```

### Key Engineering Practices

1. **Parameterized Cypher Execution:** All dynamic queries pass user arguments through parameter maps (`Map.of(...)`), preventing Cypher injection vulnerabilities.


2. **Safe Traversal Bounds:** For queries where Cypher syntax restricts parameterizing range bounds (`*1..N`), the hop limit is bounded and sanitized in Java (`Math.max(1, Math.min(maxHops, 6))`) before query construction.


3. **Singleton Driver & Connection Management:** A single `org.neo4j.driver.Driver` instance manages connection pooling across threads. Every query runs within an auto-closing `try (Session session = driver.session())` block.


4. **Graceful Error Handling:** Database queries are wrapped in `GraphQueryException` handlers. When CognoDB is unreachable, `GraphOpsViewController` catches the error and serves a clean UI warning banner without crashing the application.


5. **No Frontend Build Overhead:** The UI uses Thymeleaf server-side templates with Tailwind CSS via CDN, ensuring rapid startup and zero Node.js build dependencies.



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

# Create your local environment file
cp .env.example .env

```

Set your CognoDB connection details in `.env` or your shell environment:

```env
NEO4J_URI=bolt+s://YOUR_INSTANCE_ID.databases.cognodb.cloud
NEO4J_USERNAME=cognodb
NEO4J_PASSWORD=YOUR_COGNODB_PASSWORD

```

### 2. Seed CognoDB

1. Log in to [console.cognodb.com](https://www.google.com/search?q=https://console.cognodb.com) and open your instance's **Cypher Console**.


2. Copy the entire contents of `scripts/seed.cypher` and run it. The script is idempotent and safe to re-run.



### 3. Run the Application

```bash
./mvnw clean spring-boot:run

```

Open your browser to `http://localhost:8080`[cite: 7].

---

## 6. Live Deployment

The application is deployed on **Railway**:

* **Hosted URL:** [https://graphops-production-ea65.up.railway.app/](https://graphops-production-ea65.up.railway.app/)
* **Environment Variables:** `NEO4J_URI`, `NEO4J_USERNAME`, and `NEO4J_PASSWORD` are injected via the Railway dashboard.


* **Port Configuration:** Bound to `PORT=8080`.



---

## 7. Submission Checklist Verification

* [x] **Graph Data Model:** Labeled nodes (`Service`, `Database`, `ExternalAPI`), typed edges (`CALLS`, `READS_FROM`, `WRITES_TO`), and properties (`latencyMs`, `critical`, `tier`, `engine`)[cite: 1, 2].
* [x] **Seed Script:** Repeatable `scripts/seed.cypher` loading 16 nodes and 20 relationships[cite: 1, 2].
* [x] **Cypher Traversals:** Variable multi-hop traversal (`*1..N`), shortest-path finding (`shortestPath`), and fan-in aggregation.


* [x] **Parameterized Queries:** 100% of user inputs passed via `Map.of()` parameters using the official Neo4j Java driver.


* [x] **Non-Technical UI/UX:** Clean, responsive Tailwind dashboard with dropdown selectors, loading states, empty prompt cards, and database error banners[cite: 1, 4, 6, 7, 8].
* [x] **Engineering & Secrets:** Secrets loaded strictly from environment variables; singleton driver connection pooling; controller-level graceful degradation[cite: 1, 19].
* [x] **Live Hosted Demo:** Deployed and publicly accessible on Railway.


* [x] **Walkthrough Video:** $\le$ 2-minute screen recording demonstrating the live application and query execution.



---

## License

MIT
