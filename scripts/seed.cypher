// Clear existing data (safe to re-run)
MATCH (n) DETACH DELETE n;

// Services
CREATE (gw:Service {name: "APIGateway", team: "Core", tier: "P0", environment: "prod"})
CREATE (auth:Service {name: "AuthService", team: "Security", tier: "P0", environment: "prod"})
CREATE (order:Service {name: "OrderService", team: "Checkout", tier: "P0", environment: "prod"})
CREATE (pay:Service {name: "PaymentService", team: "Checkout", tier: "P0", environment: "prod"})
CREATE (inv:Service {name: "InventoryService", team: "Logistics", tier: "P1", environment: "prod"})
CREATE (fraud:Service {name: "FraudService", team: "Risk", tier: "P1", environment: "prod"})
CREATE (notify:Service {name: "NotificationService", team: "Engagement", tier: "P2", environment: "prod"})
CREATE (analytics:Service {name: "AnalyticsService", team: "Data", tier: "P2", environment: "prod"});

CREATE (userDb:Database {name: "UserDB", engine: "PostgreSQL", environment: "prod"})
CREATE (payDb:Database {name: "PaymentsDB", engine: "PostgreSQL", environment: "prod"})
CREATE (invDb:Database {name: "ProductsDB", engine: "PostgreSQL", environment: "prod"})
CREATE (fraudDb:Database {name: "FraudCache", engine: "Redis", environment: "prod"})
CREATE (dataLake:Database {name: "DataLake", engine: "ClickHouse", environment: "prod"})
CREATE (stripe:ExternalAPI {name: "StripeAPI", provider: "Stripe"})
CREATE (sendgrid:ExternalAPI {name: "SendGridAPI", provider: "Twilio"})
CREATE (identityVerify:ExternalAPI {name: "PersonaVerify", provider: "Persona"});

MATCH (gw:Service {name: "APIGateway"}), (auth:Service {name: "AuthService"}), (order:Service {name: "OrderService"}), (analytics:Service {name: "AnalyticsService"})
CREATE (gw)-[:CALLS {protocol: "REST", latencyMs: 35, critical: true}]->(auth)
CREATE (gw)-[:CALLS {protocol: "REST", latencyMs: 65, critical: true}]->(order)
CREATE (gw)-[:CALLS {protocol: "REST", latencyMs: 20, critical: false}]->(analytics)

MATCH (auth:Service {name: "AuthService"}), (userDb:Database {name: "UserDB"}), (identityVerify:ExternalAPI {name: "PersonaVerify"})
CREATE (auth)-[:READS_FROM]->(userDb)
CREATE (auth)-[:WRITES_TO {operation: "SYNC", critical: true}]->(userDb)
CREATE (auth)-[:CALLS {protocol: "HTTPS", latencyMs: 150, critical: false}]->(identityVerify)

MATCH (order:Service {name: "OrderService"}), (pay:Service {name: "PaymentService"}), (inv:Service {name: "InventoryService"}), (fraud:Service {name: "FraudService"}), (notify:Service {name: "NotificationService"}), (userDb:Database {name: "UserDB"})
CREATE (order)-[:CALLS {protocol: "gRPC", latencyMs: 80, critical: true}]->(pay)
CREATE (order)-[:CALLS {protocol: "gRPC", latencyMs: 45, critical: true}]->(inv)
CREATE (order)-[:CALLS {protocol: "gRPC", latencyMs: 90, critical: false}]->(fraud)
CREATE (order)-[:CALLS {protocol: "ASYNC", latencyMs: 15, critical: false}]->(notify)
CREATE (order)-[:READS_FROM]->(userDb)

MATCH (pay:Service {name: "PaymentService"}), (payDb:Database {name: "PaymentsDB"}), (stripe:ExternalAPI {name: "StripeAPI"})
CREATE (pay)-[:WRITES_TO {operation: "SYNC", critical: true}]->(payDb)
CREATE (pay)-[:READS_FROM]->(payDb)
CREATE (pay)-[:CALLS {protocol: "HTTPS", latencyMs: 220, critical: true}]->(stripe)

MATCH (inv:Service {name: "InventoryService"}), (invDb:Database {name: "ProductsDB"})
CREATE (inv)-[:READS_FROM]->(invDb)
CREATE (inv)-[:WRITES_TO {operation: "SYNC", critical: true}]->(invDb)

MATCH (fraud:Service {name: "FraudService"}), (fraudDb:Database {name: "FraudCache"})
CREATE (fraud)-[:READS_FROM]->(fraudDb)
CREATE (fraud)-[:WRITES_TO {operation: "ASYNC", critical: false}]->(fraudDb)

MATCH (notify:Service {name: "NotificationService"}), (sendgrid:ExternalAPI {name: "SendGridAPI"})
CREATE (notify)-[:CALLS {protocol: "HTTPS", latencyMs: 110, critical: false}]->(sendgrid)

MATCH (analytics:Service {name: "AnalyticsService"}), (dataLake:Database {name: "DataLake"})
CREATE (analytics)-[:WRITES_TO {operation: "ASYNC", critical: false}]->(dataLake);
