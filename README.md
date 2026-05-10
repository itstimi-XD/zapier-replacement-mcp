# Twinface · zapier-replacement-mcp

> Same backend, two faces. Humans run it cheaper than Zapier. AI agents call it natively as MCP tools.

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![MCP](https://img.shields.io/badge/MCP-spec--compatible-purple)](https://modelcontextprotocol.io)

**Status:** pre-1.0. Anti-pattern catalog 4/6 shipped; first `@McpTool` template (`send_slack_message`) follows. See [CONTRIBUTING.md](CONTRIBUTING.md) for the workflow.

**Maintained by Nova** — Seoul-based backend engineer.

---

## The Problem

Zapier is fantastic for prototyping. Past 30,000 tasks/month, it gets expensive — and in the audits I've run, **a small number of Zaps tend to consume most of the task budget**. Migrating just those workflows to a maintainable backend usually cuts the bill substantially.

This repo catalogs the recurring anti-patterns and aims to provide production-ready Spring Boot templates that replace them.

---

## Anti-Pattern Catalog (in progress)

### 1. Multi-Branch Parallel Paths · ✅ shipped
**Symptom.** A single Zap with 3+ Paths, each running similar logic for different segments (e.g., one path per agent, one per region).
**Cost.** Tasks consumed = sum of *every* branch's steps, even though only 1 branch executes per trigger.
**Domain.** `MultiBranchRouter` + `RouteEventUseCase`.

### 2. Cascading Sub-Zaps · ✅ shipped
**Symptom.** Zap A's webhook triggers Zap B, which triggers Zap C.
**Cost.** Task multiplication — a single business event burns 3× the tasks it should.
**Domain.** `SequentialPipeline` + `ExecutePipelineUseCase`.

### 3. Polling Instead of Webhook · ✅ shipped
**Symptom.** "Find New Row" / "Schedule" trigger checking every hour, regardless of whether data changed.
**Cost.** ~720 tasks/month per Zap, executing into the void. Plus retried/duplicate deliveries fan-out without dedup.
**Domain.** `WebhookEventReceiver` + `ReceiveWebhookEventUseCase` (with idempotent dispatch via `IdempotencyStorePort`).

### 4. Filter Step as Gatekeeper · ✅ shipped
**Symptom.** A Filter step that rejects most triggers, but lands at the *end* of a chain — after expensive API calls have already run.
**Cost.** Pays for HubSpot / OpenAI / etc. on every event, then rejects 80%+ of them at the filter.
**Domain.** `GuardedPipeline` (gate as a separate, mandatory field — type-level enforcement) + `ExecuteGuardedPipelineUseCase`.

### 5. Code-by-Zapier Heavy Lifting · planned
**Symptom.** Python/JS step doing data transformation, lookups, or formatting on every run.
**Cost.** Each invocation is a task, plus you hit the 1s/10s execution limit.

### 6. Synchronous Webhook Chains · planned
**Symptom.** External webhook → Zapier → external API → wait → respond to original sender.
**Cost.** Every hop is a billable task; latency stacks.

---

## Architecture

Clean / Layered architecture, enforced at compile time via Gradle modules and at test time via ArchUnit:

```
api/            ← Spring Boot entry + MCP protocol layer (only here)
application/    ← use cases + ports (no framework imports)
domain/         ← pure business logic, zero external deps
infrastructure/ ← Twilio/Gmail/Slack/Postgres adapters
```

**Stack**

- Kotlin 1.9 / Java 21
- Spring Boot 3.5
- Gradle KTS multi-module
- PostgreSQL
- Optional adapters: Twilio (SMS), SendGrid/Gmail (email), Slack SDK

---

## Hosting

These templates are designed to run on a single small VPS (Hetzner, Fly.io, Railway). The aim is to replace expensive Zapier setups with a single process and a database — no Kubernetes, no managed orchestration.

---

## Build

```bash
./gradlew build           # all modules
./gradlew test            # all tests including ArchUnit
./gradlew :api:bootRun    # run the API module locally on :8080
```

Requires JDK 21+. The Gradle toolchain auto-provisions if needed.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the five-phase workflow. AI-generated PRs are welcome — the ArchUnit suite is the gatekeeper.

Open an issue first for anything bigger than a typo. Especially welcome: new anti-pattern catalog entries (with diagrams), `@McpTool` templates, and migration walkthroughs.

---

## License

MIT. Use it, fork it, ship it.
