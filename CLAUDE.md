# CLAUDE.md — zapier-replacement-mcp

> Claude Code: 매 세션 시작 시 이 파일을 먼저 읽어줘.
> 전체 사업 컨텍스트는 `docs/private/CLAUDE-private.md` 참고 (git 제외).

---

## 🎯 이 프로젝트가 뭔가

**One-line narrative**:
> "I rebuild your Zapier on infrastructure that humans *and* AI agents can use."

Zapier → Spring Boot 마이그레이션 + MCP layer 추가 오픈소스 서버.
Layer C vertical-specific MCP server — gateway/aggregator 경쟁 아님.

---

## 🔧 기술 스택 (이 프로젝트)

```
Language: Kotlin 1.9+
Framework: Spring Boot 3.x
Database: PostgreSQL
Architecture: Clean / Layered (api → application → domain ← infrastructure)
MCP SDK: io.modelcontextprotocol:kotlin-sdk (공식, JetBrains 협업)
MCP Spring: Spring AI MCP Server Boot Starter (@McpTool annotation)
Hosting: Fly.io 또는 Hetzner (단일 process + Postgres)
Inspector: npx @modelcontextprotocol/inspector
```

---

## 📁 디렉터리 구조 (Clean Architecture 엄수)

```
zapier-replacement-mcp/
├── api/               ← MCP protocol layer, @McpTool 정의만
├── application/       ← use cases + ports interfaces
│   └── ports/         ← infrastructure가 구현할 interface
├── domain/            ← 순수 Kotlin, 외부 의존성 0
└── infrastructure/    ← Twilio/Gmail/Slack/Postgres adapters
```

**의존성 방향 엄수**:
- `domain` → 누구도 모름
- `application` → `domain`만 알고, 외부와는 ports로 분리
- `infrastructure` → `application/ports` 구현
- `api` → `application` 호출 + MCP protocol

이 방향 위반하는 코드 절대 안 됨. 위반하면 거절하고 재설계 요청할 것.

---

## 🛠️ MCP Server MVP — 첫 7개 Tools

```kotlin
// MessagingTools
@McpTool send_sms(phone, message) -> messageId
@McpTool send_email(to, subject, body) -> messageId
@McpTool post_slack_message(channel, text) -> ts

// DataTools
@McpTool query_postgres(sql, params) -> List<Map>

// WorkflowTools
@McpTool schedule_recurring_task(cron, taskDef) -> taskId
@McpTool trigger_webhook(url, payload) -> response
@McpTool watch_email_inbox(filter) -> ongoing stream
```

**모든 tool에 workspace_id implicit 주입** (멀티테넌트 필수).

---

## 💰 가격 (MCP Product)

| 티어 | 가격 | 한도 |
|---|---|---|
| Free (self-host) | $0 | 무한 |
| Pro | $19/mo | 50K calls, 1 workspace |
| Team | $99/mo | 500K calls, 5 workspaces |
| Enterprise | 협의 | self-host license |

---

## 📝 Claude Code에게 — 작업 시 지킬 원칙

1. **clean/layered architecture 어기는 코드 작성 금지**. 의존성 방향 위반 시 즉시 지적하고 재설계.
2. **한국어로 설명하되, 코드/주석/commit message는 영어**. (오픈소스 distribution 때문)
3. **Nova가 프론트엔드 약함** → React/HTML/CSS 작업 시 *왜* 그렇게 짰는지 길게 설명. 백엔드는 짧게 OK.
4. **모든 외부 의존성은 Port interface 뒤에 숨김**. Twilio API 직접 import해서 use case에 박지 마.
5. **테스트 작성**. 적어도 use case 레벨까지는 단위 테스트. infrastructure는 통합 테스트.
6. **commit message 규칙**: `feat: ...` / `fix: ...` / `refactor: ...` / `docs: ...` / `test: ...`
7. **특정 고객사 관련 어떤 것도 코드/문서/예제에 포함하지 마**. universal pattern으로 추상화.
8. **MCP 표준이 사라질 가능성 대비** — domain/application은 MCP 모름. api 레이어만 MCP 알도록.
9. **모든 비-trivial 작업은 리뷰 루프 적용** — 작업 → 리뷰(서브에이전트) → 수정 → 재리뷰 → blocker 해소될 때까지. 메모리 `feedback_review_loop_rule.md` 참조. (2026-04-27 확정)

---

## 📚 Reference 문서들 (이 repo의 docs/ 폴더)

- `docs/private/CLAUDE-private.md` — 전체 사업 컨텍스트 (git 제외, 본인용)
- `docs/private/korea-to-us-business-setup-v2.md` — 한국→미국 사업 셋업 디테일
- `docs/private/cold-emails-v2.md` — 콜드 이메일 5종 + 발송 전략
- `docs/anti-patterns/` — Zapier 안티패턴 catalog (각 template의 근거, 나중에 추가)

---

## 🔄 이 파일 업데이트 규칙

새 결정 내릴 때마다 Nova가 직접 또는 Claude Code가 업데이트.
업데이트 시 *"v2 변경: ..."* 처럼 변경 이유 남길 것.

---

*Last updated: 2026-04-26*
*Origin: Anthropic Claude (mobile chat) 와의 사업 전략 대화에서 압축됨*
