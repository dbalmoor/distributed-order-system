# Distributed Order & Payment System

A scalable, event-driven microservices-based backend system built using Java and Spring Boot.  
This project simulates the core backend architecture of modern e-commerce platforms such as Amazon and Flipkart.

---

## 🚀 Tech Stack

- Java 17
- Spring Boot 3
- Spring Cloud Gateway
- PostgreSQL
- Redis
- Apache Kafka
- Docker & Docker Compose
- JWT Security
- Swagger (OpenAPI)
- Prometheus & Grafana
- JUnit & Mockito

---

## 📐 Architecture Overview

Client → API Gateway → Microservices → Message Broker → Databases → Cache

Services:
- Order Service
- Payment Service
- Inventory Service
- Gateway Service

Communication: Event-driven via Kafka

---

## 📦 Features

- Order creation & tracking
- Inventory reservation
- Payment processing
- Distributed transactions (Saga Pattern)
- Retry & Dead Letter Queue
- Idempotency handling
- Caching with Redis
- Circuit breaking
- Centralized authentication
- Monitoring & metrics

---

## ⚙️ Project Structure

```
distributed-order-system/
 ├── gateway-service/
 ├── order-service/
 ├── payment-service/
 ├── inventory-service/
 ├── docker-compose.yml
 └── README.md
```

---

## ▶️ Running Locally

### Prerequisites

- Java 17+
- Docker
- Maven
- Git

### Steps

```bash
git clone <repository-url>
cd distributed-order-system

mvn clean package -DskipTests
docker compose build
docker compose up
```

---

## 🔍 API Documentation

Swagger UI is available at:

```
http://localhost:8081/swagger-ui.html
```

(Adjust port per service)

---

## 🧪 Testing

```bash
mvn test
```

---

## 📈 Future Enhancements

- Kubernetes deployment
- Cloud hosting (AWS/GCP)
- Distributed tracing (Zipkin)
- ElasticSearch integration
- Advanced fraud detection

---

## 👩‍💻 Author

Deepana Balmoor  
Associate Software Engineer | Java Backend Developer

---

## 📄 License

This project is for learning and portfolio purposes.
