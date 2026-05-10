# Contributing to Twinface

Thanks for considering a contribution. Twinface is in pre-1.0 active development —
the architecture is still moving, but the workflow is fixed.

## TL;DR

- File an issue *before* sending a PR for anything bigger than a typo fix.
- Every PR runs through a five-phase review loop. The loop is enforced; please don't try to skip it.
- AI-generated PRs are welcome. The CI gate is what catches AI architecture drift, not human review alone.

## How to contribute

### 1. Find or open an issue

- Check open issues first; if your idea fits an existing one, comment there.
- If not, open a new issue describing **the problem**, not your proposed solution. The "what is broken" framing helps us design the right fix together.
- For new MCP tool templates: open an issue with the source Zap pattern you'd like to migrate. Reference the [anti-pattern catalog](docs/anti-patterns.md) if it applies.

### 2. Branch + work

- Branch from `main`.
- Branch naming: `feat/`, `fix/`, `chore/`, `docs/`, or `test/` + short slug.
- Use a separate `git worktree` per concern if you're working on more than one thing — keeps PRs surgical.

### 3. Run the loop locally

Twinface is built with a five-phase workflow that runs every change through a discipline of separation between intent, design, implementation, and review. Contributors are asked to follow the same:

| Phase | What you do |
|------|-------------|
| **Intent** | One paragraph in the PR description on the problem you're solving — before any code. |
| **Design** | 2–3 plausible options in the PR description; pick one with a 1-line rationale. |
| **Spec** | Acceptance criteria as checkboxes in the PR description. The implementation will be reviewed against this list. |
| **Implementation** | TDD where reasonable. Architecture rules are enforced by ArchUnit at CI time — see `domain/src/test/.../DomainArchitectureTest.kt` and `application/.../ApplicationArchitectureTest.kt`. |
| **Review** | Self-review pass: read your own diff with the spec checklist open. Merge only after all boxes are checked. |

For AI-generated PRs (Claude / Cursor / Copilot), the same loop applies. The CI gate (ArchUnit + tests) is what catches drift from the spec.

### 4. Tests are required

- Every new `@McpTool` ships with at least one integration test against either the real external API or a documented stub.
- Architecture tests must pass — `./gradlew test --tests '*ArchitectureTest*'`.
- Coverage isn't enforced numerically, but PRs without tests will be asked for tests before review.

### 5. Open the PR

- Base: `main`.
- Title: `<type>: <slug>` — e.g., `feat: add send_email MCP tool`.
- Description: include the four sections above (Intent / Design / Spec / Self-review).

The maintainer reviews against the spec, not the implementation. If the implementation doesn't match the spec, the spec wins — either rewrite the spec or rewrite the impl.

## What we're looking for

**Most welcome**:

- New `@McpTool` templates that solve a Zapier anti-pattern in 50–200 lines of Kotlin.
- Anti-pattern catalog additions — found a Zapier shape that isn't documented yet? Open an issue with diagrams.
- Migration walkthroughs — even a partial migration writeup is gold.
- Bug reports with reproduction steps.

**Welcome but please discuss first**:

- New non-trivial features (e.g., a new auth method, a new database backend).
- Breaking API changes.

**Probably not**:

- Cosmetic refactors to working code — the architecture is still in flux; refactors will conflict.
- New external dependencies without strong reason.

## AI-assisted contributions

If you used AI to write the PR — that's fine, but say so in the description (one line). The CI gate doesn't care, but it helps calibrate review depth on the narrative parts.

## Code style

- Kotlin: follow the [official conventions](https://kotlinlang.org/docs/coding-conventions.html). `ktlint` runs in CI.
- No comments that re-state what the code does. Comments are for **why** (constraints, invariants, surprises).
- No `TODO` / `FIXME` in merged code. Open an issue and link it.

## Dev environment

```bash
git clone https://github.com/itstimi-XD/zapier-replacement-mcp.git twinface
cd twinface
./gradlew bootRun  # starts on :8080
./gradlew test     # runs all tests including ArchUnit
```

Required:

- JDK 21+
- PostgreSQL 14+ (or Docker)
- Optional: Claude Desktop / Cursor / Cline configured to talk to MCP servers locally

## Code of conduct

Be kind, be specific, assume good faith. Disagreements about technical decisions are encouraged; disagreements about people are not.

## License

By contributing, you agree your contributions are licensed under MIT (the same as the project).

---

Stuck? Open an issue and tag it `question`.
