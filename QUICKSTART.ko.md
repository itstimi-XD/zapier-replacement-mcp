# 빠른 시작

English → [QUICKSTART.md](QUICKSTART.md)

MCP 서버를 로컬에서 띄우고 MCP 클라이언트로 툴을 호출하기까지 약 2분. **Slack 자격증명은
필요 없다** — 기본 빌드에 스텁 어댑터가 들어 있어서, 실제 연동 전에 전체 경로가 도는 걸
먼저 볼 수 있다.

---

## 준비물

- **JDK 21** (안 맞으면 Gradle 툴체인이 알려준다)
- 그 외 없음. Gradle wrapper가 커밋돼 있고 데이터베이스도 필요 없다. 데이터를 저장하는 어댑터가 아직
  없어서 `application.yml`에서 DB 자동 설정을 꺼 두었다.

---

## 1. 실행

```bash
git clone https://github.com/itstimi-XD/zapier-replacement-mcp.git
cd zapier-replacement-mcp
./gradlew :api:bootRun
```

**`http://localhost:8080`** 에서 뜬다.

확인:

```bash
curl -s localhost:8080/actuator/health
# {"status":"UP"}
```

---

## 2. 엔드포인트

Spring AI MCP 서버 스타터가 표준 **SSE 전송**을 노출한다.

| 용도 | 경로 |
|---|---|
| SSE 스트림 (클라이언트가 여기 붙는다) | `http://localhost:8080/sse` |
| 메시지 엔드포인트 | `http://localhost:8080/mcp/message` |
| 헬스 | `http://localhost:8080/actuator/health` |

클라이언트에 광고되는 서버 신원:

```yaml
spring.ai.mcp.server.name: twinface
spring.ai.mcp.server.version: 0.0.1-SNAPSHOT
```

클라이언트가 다른 경로를 기대하면 `spring.ai.mcp.server.sse-endpoint` /
`sse-message-endpoint` 로 바꿀 수 있다.

---

## 3. MCP 클라이언트 연결

### Claude Code

```bash
claude mcp add --transport sse twinface http://localhost:8080/sse
claude mcp list      # twinface: ... - ✔ Connected
```

### Claude Desktop

`claude_desktop_config.json` 에 추가:

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

### 그 외

SSE 전송을 지원하는 MCP 클라이언트면 뭐든 된다 — `/sse`를 가리키면 끝. stdio만 지원하는
클라이언트라면 앞에 stdio↔SSE 브릿지를 두면 된다. 이 서버가 HTTP 네이티브인 건 의도적이다.
**같은 프로세스가 사람용 얼굴도 서빙하기 때문**이다.

---

## 4. 툴 호출

현재 살아 있는 툴은 하나다.

**`send_slack_message`**

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `channel` | 예 | `#general`, `C1234567`, `@username` |
| `text` | 예 | 메시지 본문. Slack mrkdwn 지원 |
| `threadTs` | 아니오 | 스레드 타임스탬프 — 최상위 메시지면 생략 |

MCP 클라이언트에서는 그냥 자연어로 시키면 된다:

> "#general에 배포 끝났다고 슬랙 보내줘"

응답은 평평한 객체:

```jsonc
{ "ok": true, "channel": "#general", "messageTs": "1725400000.000100", "error": null }
```

**기본 설정에서는 실제 Slack으로 안 나간다.** `twinface.slack.enabled`가 `false`라 스텁
어댑터가 답한다. 의도된 것이다 — 자격증명 없이도 MCP 경로·유스케이스·응답 모양을 다
확인할 수 있다.

---

## 5. 실제 Slack: 아직 없음

실제 Slack 어댑터는 아직 들어가지 않았다. 지금 있는 건 위에서 설명한 스텁뿐이다.

설정 스위치는 이미 있고, 켜면 조용히 넘어가지 않고 바로 실패한다.

```bash
export TWINFACE_SLACK_ENABLED=true
./gradlew :api:bootRun
# APPLICATION FAILED TO START
# (요약)
# Description: twinface.slack.enabled is not false, but the live Slack adapter is not implemented yet. ...
# Action:      Set twinface.slack.enabled=false (env TWINFACE_SLACK_ENABLED=false) to run with the stub adapter.
```

`enabled: true`면 스텁이 빠지고 기동이 실패한다. 스텁이 계속 성공을 돌려주면 Slack에 아무것도
안 갔는데 메시지가 나간 줄 알게 되기 때문이다.

토큰 설정은 바인딩돼 있지만, 실제 어댑터가 들어오기 전까지 읽는 코드가 없다.

```yaml
twinface:
  slack:
    enabled: false                 # 실제 어댑터가 나오기 전까지 true면 기동 실패
    base-url: https://slack.com/api
    token: <your-bot-token>        # env TWINFACE_SLACK_TOKEN; 예약만 됨, 아직 안 쓰임
```

토큰은 로그에 찍히지 않는다.

---

## 6. 멀티테넌시 메모

레퍼런스 배포는 **싱글 테넌트**다.

```yaml
twinface:
  workspace:
    id: default          # TWINFACE_WORKSPACE_ID 로 오버라이드
```

`WorkspaceContextPort`가 이음새다. 호스팅 배포에서는 테넌트 신원을 담는 것(헤더, 토큰
클레임, 서브도메인 등)에 맞춰 이 포트만 구현하면 되고, **도메인과 애플리케이션 레이어는
하나도 안 바뀐다.**

---

## 문제 해결

**`Unsupported class file major version` / 툴체인 불평**
JDK 버전 불일치. `java -version` 확인 — 빌드 타깃은 JDK 21.

**클라이언트는 붙는데 툴이 0개로 보임**
툴 객체가 `McpToolsConfiguration.twinfaceMcpTools(...)`에 등록돼야 한다.
`@Tool`이 붙어 있어도 그 `toolObjects(...)` 목록에 없는 빈은 **MCP에 보이지 않는다.**
툴을 추가할 때 가장 흔한 실수다.

**8080 포트 사용 중**

```bash
./gradlew :api:bootRun --args='--server.port=8081'
```

---

## 다음

- [README.md](README.md) — 안티패턴 카탈로그와 "두 얼굴" 논지
- [docs/anti-patterns.md](docs/anti-patterns.md) — 심층 분석
- [CONTRIBUTING.md](CONTRIBUTING.md) — 툴 추가, 모듈 경계, ArchUnit 규칙
