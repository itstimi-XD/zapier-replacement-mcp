# zapier-replacement-mcp

> Open-source automation backend that humans run cheaper than Zapier — and AI agents call natively as MCP tools.

**Status:** very early. Multi-module Spring Boot scaffold with the first migration template (`multi-branch-router`) in progress. Templates ship as they're implemented; track progress in GitHub issues.

**Maintained by Nova** — Seoul-based backend engineer.

---

## The Problem

Zapier is fantastic for prototyping. Past 30,000 tasks/month, it gets expensive — and in the audits I've run, **a small number of Zaps tend to consume most of the task budget**. Migrating just those workflows to a maintainable backend usually cuts the bill substantially.

This repo catalogs the recurring anti-patterns and aims to provide production-ready Spring Boot templates that replace them.

---

## Anti-Pattern Catalog (in progress)

### 1. Multi-Branch Parallel Paths (in progress)
**Symptom.** A single Zap with 3+ Paths, each running similar logic for different segments (e.g., one path per agent, one per region).
**Cost.** Tasks consumed = sum of *every* branch's steps, even though only 1 branch executes per trigger.
**Status.** Domain + use case landed; full template module coming.

### 2. Cascading Sub-Zaps (in progress)
**Symptom.** Zap A's webhook triggers Zap B, which triggers Zap C.
**Cost.** Task multiplication — a single business event burns 3× the tasks it should.
**Status.** Sequential pipeline domain + use case landed; full template module coming.

### 3. Polling Instead of Webhook (planned)
**Symptom.** "Find New Row" / "Schedule" trigger checking every hour, regardless of whether data changed.
**Cost.** ~720 tasks/month per Zap, executing into the void.

### 4. Filter Step as Gatekeeper (planned)
**Symptom.** A Filter step that rejects 80%+ of triggers downstream of an expensive trigger.
**Cost.** Filters count as tasks even when they filter out — you pay for the rejection.

### 5. Code-by-Zapier Heavy Lifting (planned)
**Symptom.** Python/JS step doing data transformation, lookups, or formatting on every run.
**Cost.** Each invocation is a task, plus you hit the 1s/10s execution limit.

### 6. Synchronous Webhook Chains (planned)
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

## Services

If you'd rather skip the rebuild, consulting offerings (audit / migration / retainer) are available. Contact via GitHub issues for now; a proper landing page is coming.

Based in Seoul (KST) — happy to work asynchronously across US timezones.

---

## License

MIT. Use it, fork it, ship it.
