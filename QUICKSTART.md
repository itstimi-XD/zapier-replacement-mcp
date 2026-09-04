# Quickstart

한국어 → [QUICKSTART.ko.md](QUICKSTART.ko.md)

Run the MCP server locally and call a tool from an MCP client in about two minutes.
No Slack credentials required — the default build ships a stub adapter so you can see the
whole path work before wiring anything real.

---

## Requirements

- **JDK 21** (the Gradle toolchain will tell you if it disagrees)
- Nothing else. The Gradle wrapper is committed.

---

## 1. Run it

```bash
git clone https://github.com/itstimi-XD/zapier-replacement-mcp.git
cd zapier-replacement-mcp
./gradlew :api:bootRun
```

The server starts on **`http://localhost:8080`**.

Verify it is up:

```bash
curl -s localhost:8080/actuator/health
# {"status":"UP"}
```

---

## 2. Endpoints

The Spring AI MCP server starter exposes the standard **SSE transport**:

| Purpose | Path |
|---|---|
| SSE stream (client connects here) | `http://localhost:8080/sse` |
| Message endpoint | `http://localhost:8080/mcp/message` |
| Health | `http://localhost:8080/actuator/health` |

Server identity, as advertised to clients:

```yaml
spring.ai.mcp.server.name: twinface
spring.ai.mcp.server.version: 0.0.1-SNAPSHOT
```

Both paths are overridable via `spring.ai.mcp.server.sse-endpoint` and
`sse-message-endpoint` if your client expects different ones.

---

## 3. Connect an MCP client

### Claude Code

```bash
claude mcp add --transport sse twinface http://localhost:8080/sse
claude mcp list      # twinface: ... - ✔ Connected
```

### Claude Desktop

Add to `claude_desktop_config.json`:

```jsonc
{
  "mcpServers": {
    "twinface": {
      "type": "sse",
      "url": "http://localhost:8080/sse"
    }
  }
}
```

### Anything else

Any MCP client that speaks the SSE transport works — point it at `/sse`. If your client
only supports stdio, put a stdio↔SSE bridge in front; this server is HTTP-native by
design, because the same process also serves the human-facing face.

---

## 4. Call the tool

One tool is live today:

**`send_slack_message`**

| Parameter | Required | Description |
|---|---|---|
| `channel` | yes | `#general`, `C1234567`, or `@username` |
| `text` | yes | Message body. Slack mrkdwn supported. |
| `threadTs` | no | Thread timestamp — omit for a top-level message |

From an MCP client, just ask for it in natural language:

> "Send a Slack message to #general saying the deploy finished."

Returns a flat object:

```jsonc
{ "ok": true, "channel": "#general", "messageTs": "1725400000.000100", "error": null }
```

**With the default config this does not hit Slack.** `twinface.slack.enabled` is `false`,
so a stub adapter answers. That is deliberate — you can exercise the MCP path, the use
case, and the response shape without credentials.

---

## 5. Wire real Slack (optional)

```bash
export TWINFACE_SLACK_ENABLED=true
export TWINFACE_SLACK_TOKEN=xoxb-...      # bot token with chat:write
./gradlew :api:bootRun
```

Or in `application.yml`:

```yaml
twinface:
  slack:
    enabled: true
    base-url: https://slack.com/api
```

> The live Slack adapter is the next thing on the path to v0.1. Until it lands,
> `enabled: true` will fail fast rather than silently no-op — a stub that pretends to
> succeed is worse than an error.

---

## 6. Multi-tenant note

The reference deployment is **single-tenant**:

```yaml
twinface:
  workspace:
    id: default          # override with TWINFACE_WORKSPACE_ID
```

`WorkspaceContextPort` is the seam. A hosted deployment implements it against whatever
carries tenant identity in your setup (a header, a token claim, a subdomain) and nothing
in the domain or application layer changes.

---

## Troubleshooting

**`Unsupported class file major version` / toolchain complaints**
JDK version mismatch. Check `java -version` — the build targets JDK 21.

**Client connects but lists zero tools**
The tool object must be registered in `McpToolsConfiguration.twinfaceMcpTools(...)`.
A `@Tool`-annotated method on a bean that is not in that `toolObjects(...)` list is
invisible to MCP — this is the most common mistake when adding a tool.

**Port 8080 taken**

```bash
./gradlew :api:bootRun --args='--server.port=8081'
```

---

## Where to go next

- [README.md](README.md) — the anti-pattern catalog and the "two faces" thesis
- [docs/anti-patterns.md](docs/anti-patterns.md) — the deep dives
- [CONTRIBUTING.md](CONTRIBUTING.md) — adding a tool, module boundaries, the ArchUnit rules
