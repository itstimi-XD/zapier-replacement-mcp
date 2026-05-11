# Changelog

All notable changes to Twinface (zapier-replacement-mcp) are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/) once it
ships its first tagged release.

---

## [Unreleased]

### Added

- **Anti-pattern catalog complete (6/6)**. The full v0.1 set of Zapier shapes
  with their Twinface domain primitives:
  - A1 Multi-branch parallel paths → `MultiBranchRouter` + `RouteEventUseCase`
  - A2 Cascading Zaps → `SequentialPipeline` + `ExecutePipelineUseCase`
  - A3 Polling instead of webhook → `WebhookEventReceiver` + `ReceiveWebhookEventUseCase` + `IdempotencyStorePort`
  - A4 Filter after expensive steps → `GuardedPipeline` + `ExecuteGuardedPipelineUseCase`
  - A5 Code-by-Zapier heavy lifting → `EventEnrichment` + `EnrichmentPipeline` (domain-only by design)
  - A6 Synchronous webhook chains → `AsyncRequest` + `AsyncRequestState` + `AsyncRequestStateMachine` + `AsyncRequestRegistryPort` + `AcceptAsyncRequestUseCase`
- [`docs/anti-patterns.md`](docs/anti-patterns.md): deep-dive catalog with
  before/after Mermaid diagrams, cost arithmetic, and migration code samples.
- [`CONTRIBUTING.md`](CONTRIBUTING.md): documents the five-phase review-loop
  workflow (Intent → Design → Spec → Implementation → Review).
- [`LICENSE`](LICENSE): MIT.
- [`.github/workflows/ci.yml`](.github/workflows/ci.yml): CI runs ArchUnit
  architecture tests, full test suite, and the bootJar build on every push to
  `main` and every PR. Java 21 toolchain, Gradle setup-gradle action with
  caching.

### Notes on architecture

Architecture rules are enforced by ArchUnit at CI time, not just code review.
AI-generated code that crosses a layer boundary fails CI before merge.

The dependency direction is `api → application → domain ← infrastructure`
(strict). Tests live in `domain/src/test/.../DomainArchitectureTest.kt` and
`application/.../ApplicationArchitectureTest.kt`.

### Added (cont.)

- **First MCP tool: `send_slack_message`**. The scaffold is now a runnable
  MCP server you can wire into Claude Desktop / the MCP Inspector.
  - `:domain` — `WorkspaceId` value class for tenant identity.
  - `:application` — `SlackMessagePort` (sealed `SlackSendResult` outcomes),
    `WorkspaceContextPort`, `SendSlackMessageUseCase` (input validation +
    implicit workspace resolution).
  - `:infrastructure` — `StaticWorkspaceContext` (single-tenant, reads
    `twinface.workspace.id`), `StubSlackMessageAdapter` (placeholder until
    the real Slack Web API integration lands), `SlackProperties` reserving
    `twinface.slack.*` namespace.
  - `:api` — `SendSlackMessageMcpTool` (Spring AI `@Tool` annotation),
    `McpToolsConfiguration` (`MethodToolCallbackProvider` bean +
    `mcpToolDispatcher` Coroutine dispatcher so the `runBlocking` bridge
    does not pin Tomcat workers).
  - Build — Spring AI BOM 1.0.0-M6 +
    `spring-ai-mcp-server-webmvc-spring-boot-starter`, Spring milestone repo
    added at root.

### Coming next

- The remaining six tools toward the v0.1 release (`send_email`, `send_sms`,
  `query_postgres`, `trigger_webhook`, `schedule_recurring_task`,
  `watch_email_inbox`).
- Live Slack adapter replacing the stub (HTTP client + workspace-scoped
  token lookup).
