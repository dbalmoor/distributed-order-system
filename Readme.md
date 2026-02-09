# 📦 Distributed Order Management System  
### Saga Orchestration with Spring Boot & Apache Kafka

---

## 📌 Overview

This project is a **distributed microservices-based order processing system** built using **Spring Boot** and **Apache Kafka**, implementing the **Saga Orchestration Pattern**.

It ensures **data consistency across services** without distributed transactions by coordinating business steps using Kafka-based events and commands.

---

## 🏗️ Architecture

### Microservices

| Service | Responsibility |
|---------|----------------|
| Order Service | Manages order lifecycle |
| Inventory Service | Reserves and releases stock |
| Payment Service | Charges and refunds payments |
| Saga Orchestrator | Controls workflow |
| Kafka | Message broker |

### Pattern Used

- ✅ Saga Orchestration  
- ✅ Event-Driven Architecture  
- ✅ Asynchronous Messaging  
- ✅ Compensating Transactions  
- ✅ Dead Letter Queues (DLQ)

---

## 🔄 Saga Workflow

### Order Processing Flow

Client → Order Service → order.created
↓
Saga Orchestrator
↓
inventory.reserve.cmd
↓
inventory.reserved / inventory.failed
↓
payment.charge.cmd
↓
payment.success / payment.failed


### Compensation Flow

| Failure | Action |
|---------|---------|
| Inventory Failed | Cancel Order |
| Payment Failed | Release Inventory + Cancel Order |

---

## 📊 Order Status Lifecycle

```java
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
Responsibilities
Listens to domain events

Controls workflow

Sends commands

Handles failures

Triggers compensation

Event Handling
Event	Action
order.created	Reserve inventory
inventory.reserved	Charge payment
inventory.failed	Cancel order
payment.success	Confirm order
payment.failed	Release inventory + Cancel order
💰 Payment Handling
All monetary values use:

java.math.BigDecimal
Floating-point types are avoided to prevent precision errors.

🔁 Reliability Features
Idempotency
Prevents duplicate processing using processed-order tracking.

Optimistic Locking
Used in Order Service for concurrent updates.

Retry & DLQ
Kafka consumers use retry mechanisms and Dead Letter Queues.

Distributed Tracing
Each message carries:

traceId = orderNumber
Used with MDC logging.

🧾 Logging Format
[SAGA] [SERVICE] [TRACE] [ORDER] [STEP] [STATUS]
Example:

[SAGA] [ORDER] [TRACE:ORD-123] [STEP:PAYMENT_SUCCESS] [STATUS:SUCCESS]
⚙️ Technology Stack
Technology	Purpose
Java 17	Programming Language
Spring Boot 4	Framework
Spring Kafka	Messaging
Apache Kafka	Broker
JPA / Hibernate	ORM
MySQL / PostgreSQL	Database
Lombok	Boilerplate Reduction
Jackson	JSON Processing
🛠️ Configuration Example
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
📌 Design Principles
❌ No Distributed Transactions

✅ Eventual Consistency

✅ Compensating Transactions

✅ Loose Coupling

✅ Fault Isolation

✅ Scalability

🚧 Project Status
Completed
✅ Order Service

✅ Inventory Service

✅ Payment Service

✅ Saga Orchestrator

✅ DLQ Handling

✅ Retry Mechanism

✅ Distributed Tracing

✅ Logging System

Planned
⏳ Payment Refund Workflow

⏳ Saga State Persistence

⏳ Monitoring Dashboard

⏳ Metrics Integration

⏳ UI Client

🚀 How to Run
1. Start Kafka
docker-compose up
2. Start Services
Run in order:

order-service
inventory-service
payment-service
saga-orchestrator
3. Test
Send request:

POST /orders
Saga starts automatically.

🧠 Learning Outcomes
This project demonstrates:

Real-world Saga implementation

Kafka-based orchestration

Distributed transaction handling

Failure recovery mechanisms

Production-grade microservices design

📈 Future Enhancements
Saga State Store (Redis / DB)

Exactly-Once Semantics

Kafka Streams

OpenTelemetry Tracing

Kubernetes Deployment

Circuit Breakers

👩‍💻 Author
Deepana Balmoor
Associate Software Engineer | Java Backend Developer