# prompt.md — Instructions for producing design.md

> Usage: open the project in VS Code with `requirement.md` in the repo root, then paste the prompt in section 1 into Claude. Do not skip the exploration step. Do not let it write code yet.

---

## 1. The prompt (copy everything inside the block)

```text
You are acting as a Staff Backend Engineer reviewing and designing improvements for a distributed order management system (Spring Boot, Kafka, PostgreSQL, saga orchestration) that lives in this repository.

YOUR TASK IN THIS SESSION: produce a file named `design.md` in the repo root. DO NOT modify any source code, configs, or tests in this session. The only file you may create or edit is `design.md`.

STEP 1 — EXPLORE THE CODEBASE FIRST
Read `requirement.md` fully. Then explore the repository thoroughly before designing anything:
- Module/service layout, pom/gradle files, shared common DTO module
- Kafka config for every service: topics, partitions, keys, producer and consumer properties, error handlers, retry/DLQ setup
- Every Kafka listener and every producer call
- Entities, repositories, DB migrations/schema, where @Version is used
- How idempotency is implemented in each service (or not)
- How the Saga Orchestrator decides next steps and whether it persists any state
- How Order status transitions are performed and where
- Existing tests (if any), docker/compose files, README, logging/MDC setup
Base everything on what the code actually does, not on what the README says. Cite file paths and class/method names as evidence.

STEP 2 — AS-IS AUDIT
For each requirement in requirement.md (R0.1 through R21, plus optional O1–O5), mark it DONE, PARTIAL, or MISSING with a one-to-three line justification and code references. Also list any additional design flaws, bugs, race conditions, or code smells you found that requirement.md does not mention.

STEP 3 — WRITE design.md
Use exactly this structure:

1. Summary and scope
2. As-is architecture (components, topics, data model, message flow) with a Mermaid diagram
3. Requirement status table (R-id | status | evidence)
4. Additional issues found in the code (ranked by severity, with evidence)
5. Target architecture (Mermaid diagrams: component view, and sequence diagrams for: happy path, inventory failure, payment failure with release-then-cancel, payment success but confirm failure with refund, timeout/late reply)
6. Detailed design per requirement, in this order: R1, R2, R3, R4, R5, R6, R7, R8, then R9–R11, R12–R14, R15–R18, R19–R21. For each:
   - Problem in the current code
   - Chosen approach and at least one alternative considered, with trade-offs
   - Data model changes (tables, columns, indexes, constraints) with DDL sketches
   - New or changed Kafka topics, event/command schemas, keys, headers
   - Class-level changes (which classes are added/modified/removed)
   - Transaction boundaries (what is committed atomically, where offsets are committed)
   - Failure scenarios handled and how
   - How it will be tested
7. State machines: Order and Saga, with allowed-transition tables and Mermaid state diagrams
8. Kafka design table: topic | key | partitions | producer service | consumer service | retry/DLQ policy
9. Failure-scenario matrix: scenario | expected behaviour | mechanism | test
10. Test strategy (unit vs Testcontainers integration, what is mocked, CI)
11. Migration/rollout plan: ordered, incremental implementation steps where the system stays runnable after each step, with size estimates (S/M/L) and dependencies
12. Risks, open questions, and assumptions (explicitly list anything you could not determine from the code)
13. Known limitations that will remain after this work

DESIGN RULES
- Prefer the simplest approach that is correct and explainable in an interview. Justify any added complexity.
- Never assume exactly-once delivery. Design for at-least-once delivery with idempotent processing.
- Every state change and its outgoing message must be atomic (outbox). Every consumer must be idempotent and safe under duplicates, reordering, and late arrival.
- Do not invent facts about the code. If something cannot be verified, put it under "Open questions".
- Do not over-engineer: no new infrastructure unless the requirement justifies it. Debezium, schema registry, and Temporal belong in ADR-style comparisons unless clearly needed.
- If you disagree with a requirement or find it wrong for this codebase, say so and propose an alternative instead of silently following it.
- Keep Java/Spring conventions consistent with the existing code style.

BEFORE YOU FINISH
- Re-read design.md and check that every requirement R0.1–R21 appears in the status table and has a detailed design section (or is explicitly deferred with a reason).
- List the top 5 decisions you are least sure about at the end under "Open questions".
- Reply with a short summary only: what you found, the three most serious problems in the current code, and the questions you need answered. Do not paste the whole design into chat.
```

---

## 2. After Claude produces design.md

1. Read the **As-is audit** and **Additional issues** sections first. If they don't match your own knowledge of the code, tell Claude which parts are wrong and ask it to re-verify.
2. Bring `design.md` back to me for review.
3. Do not start implementation until the design is approved.

---

## 3. Follow-up prompts for later sessions

Use these after the design is approved. Run one at a time.

**Implementation, one step at a time:**

```text
Read design.md and requirement.md. Implement only step <N> from the migration/rollout plan in design.md. Before coding, restate the step, list the files you will change, and wait for my confirmation. Then implement it, together with its tests. Run the tests and show me the results. Do not start the next step.
```

**Review after each step:**

```text
Review the changes you just made as a skeptical Staff Engineer. Look for: transaction boundary mistakes, race conditions, idempotency holes, missing tests for failure paths, and anything that contradicts design.md. List problems first, then fix them.
```

**Interview prep after each phase:**

```text
Based on the code and design.md, act as an interviewer for an SDE-2 backend role and ask me the 10 hardest questions about what we just built. Ask one at a time, wait for my answer, and critique it.
```

---

## 4. Tips

- If Claude's context fills up on a large repo, ask it to explore one service at a time and append findings to a scratch file (for example `notes/audit.md`) before writing `design.md`.
- If it starts writing code in this session, stop it and repeat: "Only edit design.md."
- Commit `requirement.md`, `prompt.md`, and `design.md` to the repo. Design documents and ADRs are good portfolio material.
