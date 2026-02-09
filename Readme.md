📦 Distributed Order Management System using Saga Orchestration (Kafka + Spring Boot)
📌 Overview

This project is a distributed microservices-based order processing system built using Spring Boot and Apache Kafka, implementing the Saga Orchestration Pattern.

It ensures data consistency across services (Order, Inventory, Payment) without using distributed transactions, by coordinating steps through a Saga Orchestrator Service.

Each business operation is executed as a local transaction and coordinated using Kafka events and commands.

🏗️ Architecture
Microservices
Service	Responsibility
Order Service	Manages order lifecycle
Inventory Service	Reserves/releases stock
Payment Service	Charges/refunds payment
Saga Orchestrator	Controls workflow
Kafka	Event & command broker
Pattern Used

✅ Saga Orchestration

✅ Event-driven architecture

✅ Asynchronous messaging

✅ Compensating transactions

✅ Dead Letter Queues (DLQ)

🔄 Saga Flow
1️⃣ Order Creation
Client → Order Service → order.created

2️⃣ Inventory Reservation
Saga → inventory.reserve.cmd
Inventory → inventory.reserved / inventory.failed

3️⃣ Payment Processing
Saga → payment.charge.cmd
Payment → payment.success / payment.failed

4️⃣ Order Completion / Rollback
Scenario	Action
Success	order.confirm.cmd
Payment Failed	inventory.release.cmd + order.cancel.cmd
Inventory Failed	order.cancel.cmd
📊 Order Status Lifecycle
CREATED
INVENTORY_RESERVED
PAYMENT_SUCCESS_PENDING
PAYMENT_FAILED_PENDING
FAILED
CANCELLED
COMPLETED

📨 Kafka Topics
Events
order.created
inventory.reserved
inventory.failed
payment.success
payment.failed
order.cancelled

Commands
inventory.reserve.cmd
inventory.release.cmd
payment.charge.cmd
payment.refund.cmd
order.confirm.cmd
order.cancel.cmd

Dead Letter Queues
order.dlq
inventory.dlq
payment.dlq

📁 Project Structure
distributed-order-system/
│
├── order-service/
├── inventory-service/
├── payment-service/
├── saga-orchestrator/
└── kafka/


Each service contains:

controller/
service/
repository/
dto/
kafka/
config/

🧩 Saga Orchestrator
Role

The Saga Orchestrator:

Listens to business events

Decides next step

Sends commands

Handles failures

Triggers compensation

Main Logic
Event	Action
order.created	reserve inventory
inventory.reserved	charge payment
inventory.failed	cancel order
payment.success	confirm order
payment.failed	release inventory + cancel order
💰 Money Handling

All monetary values use:

java.math.BigDecimal


❗ Never use double or float for payments.

🔁 Reliability Features
1️⃣ Idempotency

Each service prevents duplicate processing using:

ProcessedOrderRepository

2️⃣ Optimistic Locking

Used in Order Service for concurrent updates.

3️⃣ Retry + DLQ

Kafka consumers use:

DefaultErrorHandler
DeadLetterPublishingRecoverer


For automatic retries and DLQ routing.

4️⃣ Traceability

Each message carries:

traceId = orderNumber


Used with MDC logging.

🧾 Logging Format
[SAGA] [SERVICE] [TRACE] [ORDER] [STEP] [STATUS]


Example:

[SAGA] [ORDER] [TRACE:ORD-123] [STEP:PAYMENT_SUCCESS] [STATUS:SUCCESS]

⚙️ Technologies Used
Tech	Purpose
Java 17	Language
Spring Boot 4	Framework
Spring Kafka	Messaging
Apache Kafka	Broker
JPA/Hibernate	Persistence
MySQL/Postgres	Database
Lombok	Boilerplate reduction
Jackson	JSON parsing
🛠️ Configuration (application.yml example)
spring:
  application:
    name: saga-orchestrator

  kafka:
    bootstrap-servers: localhost:9092

    consumer:
      group-id: saga-group
      auto-offset-reset: earliest

    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

📌 Key Design Principles

❌ No distributed transactions

✅ Eventual consistency

✅ Compensating transactions

✅ Stateless orchestration

✅ Loose coupling

✅ Failure isolation

🚧 Current Status
Implemented

✅ Order Service

✅ Inventory Service

✅ Payment Service (basic)

✅ Saga Orchestrator

✅ DLQ Handling

✅ Retry Mechanism

✅ BigDecimal for payments

✅ Logging & tracing

In Progress / Future

⏳ Payment Refund Flow

⏳ Saga State Persistence

⏳ Monitoring Dashboard

⏳ Metrics (Prometheus/Grafana)

⏳ UI Client

📈 Future Improvements

Add Saga State Store (Redis/DB)

Exactly-once semantics

Kafka Streams

Circuit Breakers

Distributed Tracing (OpenTelemetry)

Kubernetes Deployment

🚀 How to Run
1️⃣ Start Kafka
docker-compose up

2️⃣ Start Services (Order → Inventory → Payment → Saga)
mvn spring-boot:run

3️⃣ Test
POST /orders


Saga starts automatically.

🧠 Learning Outcomes

This project demonstrates:

Real-world Saga implementation

Kafka-based orchestration

Handling race conditions

Designing compensations

Building fault-tolerant systems

Production-grade microservices
