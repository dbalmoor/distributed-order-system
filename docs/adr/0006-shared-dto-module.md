# ADR 0006: Shared DTO module

## Status

Accepted

## Context

The services exchange common command and event contracts. The repository defines these contracts in `common-dto`, including `BaseEvent`.

## Decision

Keep shared message DTOs in the `common-dto` Maven module and include it in the root reactor build with the services.

## Consequences

Services compile against the same DTO definitions, and the root Maven build verifies the shared contracts with the complete application. Changes to a shared DTO require compatible service deployments.

## Rejected alternatives

R24 does not specify a rejected alternative.
