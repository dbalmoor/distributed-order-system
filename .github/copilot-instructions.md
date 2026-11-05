# distributed-order-system
Java 17, Spring Boot 3.2.5, Kafka, PostgreSQL, Maven multi-module (common-dto, order-service, inventory-service, payment-service, saga-orchestrator, gateway-service). Windows + PowerShell.

Start by reading docs/PROGRESS.md and only the section of design.md for the current phase. Do not scan the whole repo.

Rules:
- Open only files the task names or that a search finds.
- Small targeted edits; never regenerate whole files.
- Do not print file contents or long summaries. Final reply: max 6 lines.
- Keep design decisions: orderId as key, single writer of order status, outbox, RECORD ack mode, @RetryableTopic, 3 order states.
- Run `mvn -B verify` only when asked; report only the BUILD result and failing test names.
- Never commit or push. Never poll CI or GitHub; I check it myself.
- No Co-authored-by trailers. Edit docs/PROGRESS.md only when the task says so.
- The project targets Java 17. Never change java.version, the CI JDK, Spring Boot or Spring Cloud versions, or JVM flags to fix a local problem. If mvn -version does not show 17, stop and tell me.