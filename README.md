# zapier-to-springboot

> Drop-in Spring Boot templates for the most common (and most expensive) Zapier anti-patterns.

**Maintained by [Nova Kim](https://linkedin.com/in/...)** — Seoul-based backend engineer specializing in cost-reduction migrations for SMBs that have outgrown Zapier.

---

## The Problem

Zapier is fantastic for prototyping. Past 30,000 tasks/month, it gets expensive — and in nearly every audit I run, **1 or 2 Zaps consume 70%+ of the entire task budget**. Migrating just those workflows to a maintainable backend usually cuts the bill by 70–80%.

This repo catalogs the recurring anti-patterns and provides production-ready Spring Boot templates that replace them.

---

## Anti-Pattern Catalog

### 1. Multi-Branch Parallel Paths
**Symptom.** A single Zap with 3+ Paths, each running similar logic for different segments (e.g., one path per agent, one per region).
**Cost.** Tasks consumed = sum of *every* branch's steps, even though only 1 branch executes per trigger.
**Template.** [`templates/multi-branch-router/`](./templates/multi-branch-router/)

### 2. Cascading Sub-Zaps
**Symptom.** Zap A's webhook triggers Zap B, which triggers Zap C.
**Cost.** Task multiplication — a single business event burns 3× the tasks it should.
**Template.** [`templates/cascading-zaps/`](./templates/cascading-zaps/)

### 3. Polling Instead of Webhook
**Symptom.** "Find New Row" / "Schedule" trigger checking every hour, regardless of whether data changed.
**Cost.** ~720 tasks/month per Zap, executing into the void.
**Template.** [`templates/polling-to-webhook/`](./templates/polling-to-webhook/)

### 4. Filter Step as Gatekeeper
**Symptom.** A Filter step that rejects 80%+ of triggers downstream of an expensive trigger.
**Cost.** Filters count as tasks even when they filter out — you pay for the rejection.
**Template.** [`templates/pre-filter-webhook/`](./templates/pre-filter-webhook/)

### 5. Code-by-Zapier Heavy Lifting
**Symptom.** Python/JS step doing data transformation, lookups, or formatting on every run.
**Cost.** Each invocation is a task, plus you hit the 1s/10s execution limit.
**Template.** [`templates/external-transform-service/`](./templates/external-transform-service/)

### 6. Synchronous Webhook Chains
**Symptom.** External webhook → Zapier → external API → wait → respond to original sender.
**Cost.** Every hop is a billable task; latency stacks.
**Template.** [`templates/webhook-receiver/`](./templates/webhook-receiver/)

---

## Architecture

Every template follows Clean / Layered architecture so you can adopt one without inheriting an opinion you didn't ask for:

```
domain/         ← pure business logic, zero framework dependencies
application/    ← use cases, transactional boundaries
infrastructure/ ← Spring/JPA/messaging adapters
api/            ← REST controllers, webhook receivers
```

**Stack**

- Kotlin 1.9+
- Spring Boot 3.x
- PostgreSQL
- Optional adapters: Twilio (SMS), SendGrid/Gmail (email), Slack SDK

Each template is a standalone Gradle module — copy what you need.

---

## Hosting

These templates are designed to run on a single small VPS (Hetzner, Fly.io, Railway). Most replace $500–$1,000/mo of Zapier with $20–$50/mo of compute. No Kubernetes. No managed orchestration. Just one process and a database.

---

## Hire Me

If your Zapier bill exceeds $500/month and you'd rather skip the rebuild, I run **paid migrations**:

| Tier | Price | Outcome |
|------|-------|---------|
| Audit | $1,500 | A diagnostic report identifying which Zaps are bleeding you and the projected savings |
| Migration | $7,500+ | I migrate your top 1–3 cost offenders to a hosted backend |
| Retainer | $1,500/mo | Ongoing maintenance + monitoring + incremental migrations |

Based in Seoul (KST). I work while you sleep, which most US clients find unexpectedly helpful.

→ **[Book a free 15-min audit](https://cal.com/...)**

---

## License

MIT. Use it, fork it, ship it. If it saves you a bunch of money, a star is appreciated.
