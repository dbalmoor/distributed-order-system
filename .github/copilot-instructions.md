**Verdict: the README and ADRs are honest and strong.** Marking three scenarios as "NO TEST" and listing known limitations is what will impress an interviewer. A few things contradict the code or the other docs, and fixing them costs nothing.

## Fix first (free, about 30 minutes)

1. **The `docker compose -f up -d` command in Getting started is broken** (`-f` has no filename). Use `docker compose up -d`, or `-f <your-file>.yml`. Test it from a fresh clone, since a broken first command costs you a recruiter.
2. **The intro overclaims.** "Every consumer is safe against duplicates and late events" conflicts with your own matrix, where duplicate `inventory.reserve.cmd` has no test and the duplicate path doesn't re-emit the acknowledgement. Change it to: "Consumers are designed to tolerate duplicates and late events; test coverage per scenario is in the failure matrix."
3. **Failure matrix, "Cancel request after the pivot".** The cited test is `cancelRequestsBeforePivotWaitForRequiredCompensationAcknowledgments`, whose name is about *before* the pivot. Open it. If it doesn't also assert the after-pivot rejection, mark that row NO TEST.
4. **You did the manual DLT check, so say so.** Mark the poison-message row and scenario 16 as "manually verified, no automated test", and add a screenshot under `docs/evidence/`.
5. **The four PNGs are out of date, and the README doesn't embed them.**
   - `kafka-topology.png` shows `order.dlq`, `inventory.dlq` and `payment.dlq`. Phase 6 replaced those with `<topic>-retry-N` and `<topic>-dlt`.
   - `architecture.png` shows only 6 event topics and no outbox, no saga database and no gateway.

   Your mermaid diagrams are accurate. Delete the PNGs, or move them to a `docs/archive/` folder, unless you regenerate them to match.
6. **ADR 0006's "Rejected alternatives" says "R24 does not specify a rejected alternative."** That is internal requirement-ID text leaking into a public ADR, and it's the exact question an interviewer will ask. Also remove the "R16" and "R24" references in ADR 0004 and ADR 0006. Replace ADR 0006 with:

   ```markdown
   # ADR 0006: Shared DTO module
   ## Status
   Accepted (known trade-off)
   ## Context
   All services exchange the same command and event payloads (`BaseEvent` and its subclasses).
   ## Decision
   Keep the payload classes in one `common-dto` Maven module built in the root reactor. One change updates producers and consumers together, and the root build fails if a service no longer compiles against it.
   ## Consequences
   Services share a release cadence for contract changes: changing a DTO means rebuilding and redeploying every service that uses it, and removing a field can break a consumer that was not redeployed. Additive, optional fields are the safe way to evolve a contract here.
   ## Rejected alternatives
   - Per-service copies of the contracts with consumer-driven contract tests: avoids coupling deployments, but duplicates classes and needs test infrastructure beyond this project's scope.
   - Avro or Protobuf with a schema registry: gives enforced, versioned compatibility, but adds infrastructure. Listed as a known limitation.
   ```
7. **ADR 0001** is missing the question interviewers ask first. Add a rejected alternative: "Choreography (services reacting to each other's events): rejected because there would be no single place to own timeouts, compensation order and recovery."
8. **ADR 0004 and its index line say "atomic conditional update", but the code doesn't do that.** The ADR admits it, and the index line doesn't. Reword the index to "Atomic conditional update as the oversell guard (implementation pending)", until you do the task below.
9. **"As-built design" is the wrong label for `design.md`,** because the file still mixes target design with what exists. Rename the link to "Design (target architecture and decisions)", or add a short "Status vs implementation" table at its top.
10. **Add an "Evidence" section to the README:** link the CI runs, the test results file, and the Kafka UI and DLT screenshots. A recruiter should be able to see proof in 30 seconds.

## Order of work (stop when credits run out; each prompt stands alone)

**Step B, small and the most valuable: close the inventory gap.** This turns a documented weakness into an oversell proof. Start a new chat with `#file:docs/PROGRESS.md` and send:

```text
Task: implement ADR 0004 in inventory-service. Read only docs/PROGRESS.md, InventoryServiceImpl.processReserve, InventoryRepository and the Inventory entity.
Replace load-FOR-UPDATE, check, save with one atomic conditional update per item:
UPDATE ... SET available = available - :qty, reserved = reserved + :qty [, version = version + 1 if @Version is used] WHERE product_id = :id AND available >= :qty
(use the real column names; @Modifying returning int).
Rules: process items in ascending productId to avoid deadlocks; if any item updates 0 rows there must be no partial reservation, and the failure outcome (inventory.failed outbox row + processed-event row) must still commit exactly as today; keep the processed_inventory_events dedupe, RELEASED marker and outbox writes in the same transaction.
Add one integration test: stock 10, 50 concurrent reserve commands of qty 1 with distinct sagaIds -> exactly 10 succeed, 40 fail, available never negative.
Run only that test and InventoryServiceImplTest. Then update ADR 0004 (Status: Accepted, remove "Current implementation differs"), the README Known limitations inventory bullet and the README "Pessimistic lock on inventory" design bullet. Nothing else.
Do not print files. Reply in max 6 lines.
```

**Step C, Phase 7a: metrics and log fields (cheap).** In a new chat, with `#file:docs/PROGRESS.md`:

```text
Task: Phase 7a observability (metrics + log correlation). No OpenTelemetry in this task.
Read docs/PROGRESS.md and only the files you must touch. Ports come from each application.yaml.

1. Add spring-boot-starter-actuator and micrometer-registry-prometheus to order, inventory, payment and saga-orchestrator. Expose only health,info,prometheus.
2. Add Micrometer metrics with low-cardinality tags only (never orderId/sagaId):
   - every publishing service: gauge outbox.pending (NEW + IN_PROGRESS rows), counters outbox.published and outbox.publish.failures
   - saga-orchestrator: counters saga.started, saga.completed, saga.cancelled, saga.duplicate.events, saga.rejected.events (invalid for state), saga.timeouts (tag step); gauge saga.needs_attention
   - payment and inventory: counters payment.duplicate and inventory.duplicate (duplicate-delivery path)
   - each service: counter kafka.dlt.messages (tag topic), incremented in the existing DLT handler
   Prometheus will show them as saga_completed_total etc.
3. Log correlation: in each @KafkaListener and in the outbox poller put MDC keys sagaId, orderId, messageId, eventType and clear them in finally. Add them to the console/file log pattern.
4. Add a prometheus service to docker compose (observability/prometheus.yml scraping the four services via host.docker.internal).
5. Add one integration test in saga-orchestrator: after the happy path, the saga.completed counter is 1 and saga.needs_attention is 0.
Run mvn -B verify once; fix only failures caused by this change.
Update only the Phase 7 line in docs/PROGRESS.md ("7a done"). Do not print files. Reply in max 6 lines.
```

After it finishes, check by hand: `curl http://localhost:<port>/actuator/prometheus | findstr saga_`, open Prometheus at `http://localhost:9090/targets`, and take a screenshot.

**Step D, Phase 7b: real tracing. Optional, and run it only if you have credits left, because it's the expensive one.**

```text
Task: Phase 7b distributed tracing with Micrometer Tracing + OTLP to Jaeger. Read docs/PROGRESS.md only, plus files you must touch.
1. Add micrometer-tracing-bridge-otel and opentelemetry-exporter-otlp to the four services; management.tracing.sampling.probability=1.0 and management.otlp.tracing.endpoint=http://localhost:4318/v1/traces. Add jaegertracing/all-in-one to docker compose (UI 16686, OTLP 4318).
2. We have custom KafkaTemplate and listener container factory beans, so the observation properties do NOT apply. Enable observation explicitly: kafkaTemplate.setObservationEnabled(true) and factory.getContainerProperties().setObservationEnabled(true).
3. Existing MDC key "traceId" holds our business correlation id (BaseEvent.traceId). Rename that MDC key to correlationId everywhere so it does not collide with the OTel traceId.
4. Outbox hop: when writing an outbox row, store the current traceparent in the row's headers. In the outbox poller, restore it with io.micrometer.tracing.propagation.Propagator.extract, create a span with that parent and publish inside its scope, so one trace spans POST /orders -> order.created -> saga -> inventory -> payment -> confirm. Add sagaId and orderId as span attributes.
If after one attempt the trace still breaks at the outbox hop, stop and report what you see; do not loop. Run mvn -B verify once.
Update only the Phase 7 line in docs/PROGRESS.md. Do not print files. Reply in max 6 lines.
```

## After Phase 7

Update the README by hand:
- Known limitations: remove "Prometheus metrics" after 7a, and "OpenTelemetry tracing" only if 7b worked.
- Add one line that the Actuator endpoints are unauthenticated.
- Add the Jaeger and Prometheus screenshots to the Evidence section.

Send me the **"N tests" figure from the Actions summary**. I'll use it to fill the numbers in your project story, together with the oversell result from Step B.