# Process Issues Pipeline — Design Spec

**Date:** 2026-03-21
**Status:** Approved

---

## Problem

The Featured project has 20 open GitHub issues. Processing each one manually
(implement → review → PR → CI → merge) is repetitive, slow, and context-switching
intensive. A fully autonomous pipeline would free the developer from babysitting
each issue through its lifecycle.

---

## Goals

- Process all open GitHub issues autonomously from implementation to merged PR
- Maximize throughput by running independent issues in parallel (max 3 concurrent)
- Respect dependencies between issues
- Handle CI failures and bot/copilot review comments without human intervention
- Notify the developer (via GitHub issue comment) when an issue cannot be resolved

---

## Non-Goals

- Does not require human approval at any stage (fully autonomous)
- Does not process issues that are closed or labeled `skip-pipeline`

---

## Architecture

### Trigger

Invoked as a Claude Code skill: `/process-issues`

Runs inside a ralph-loop:
```
ralph-loop "Run /process-issues orchestrator" \
  --completion-promise PIPELINE_COMPLETE \
  --max-iterations 200
```

### Subagent Dispatch Mechanism

The orchestrator uses Claude Code's `Agent` tool to dispatch subagents. Each subagent
receives a self-contained prompt containing: the issue body, worktree path, branch name,
signal file paths, and the full lifecycle instructions. Subagents run in the same session
context (subagent-driven-development pattern).

### Components

```
Orchestrator (ralph-loop body)
├── [First iteration only] Startup phase:
│   ├── Preflight: gh auth status → halt with error if unauthenticated
│   ├── gh issue list --state open --json number,title,body,labels
│   ├── AI inference call → dependency adjacency list (with cycle detection)
│   └── Write .pipeline/state.json
├── [Every iteration]:
│   ├── Check .pipeline/signals/ for new .done/.failed files
│   ├── Update state.json: mark completed issues, cascade failures to dependents
│   ├── Free worktree slots for completed issues
│   ├── Determine dispatchable issues (status=pending, deps all done, slot available)
│   ├── Dispatch Agent subagent per dispatchable issue (fills up to 3 slots)
│   └── If all issues terminal (done|failed) → write REPORT.md → print PIPELINE_COMPLETE
│
└── Subagent (one per issue, stays alive through full PR lifecycle)
    ├── Preflight: check worktree/branch existence
    ├── Implement
    ├── Local review + simplify (max 3 inner iterations)
    ├── Push PR
    └── PR feedback loop (max 3 total push attempts)
        ├── On CI failure: fix + push
        ├── On serious bot comments: fix + push
        └── On success: merge, cleanup, write .done signal
        └── On max attempts exceeded: cleanup, write .failed signal
```

---

## State File: `.pipeline/state.json`

Gitignored. **Written exclusively by the orchestrator. Subagents must never write this file.**
Subagents communicate results only via signal files in `.pipeline/signals/`.

```json
{
  "issues": [
    {
      "id": 53,
      "title": "Testing utilities (FakeConfigValues, test DSL)",
      "status": "pending",
      "worktree_path": null,
      "branch": "feat/issue-53-testing-utilities",
      "pr_number": null,
      "dependencies": [],
      "attempt_count": 0,
      "failure_reason": null
    }
  ],
  "max_concurrent": 3,
  "started_at": "2026-03-21T10:00:00Z",
  "completed_at": null
}
```

**Notes:**
- `worktree_path` is `null` until the subagent confirms worktree creation (via signal file)
- `started_at` / `completed_at` are informational only (for the REPORT.md)
- `pr_number` is written by the orchestrator after reading the subagent's `.started` signal

**Status values:** `pending` → `in_progress` → `done | failed`

### Dependency Failure Cascade

When any issue transitions to `failed`, the orchestrator immediately marks all issues
whose `dependencies` list contains that issue's ID as `failed` with
`failure_reason: "dependency #{id} failed"`. Their `.failed` signal files are written
by the orchestrator (no subagent needed). This prevents deadlock.

---

## Dependency Inference

At startup, a single AI call reads all issue titles and bodies and produces a
dependency adjacency list. The orchestrator then:
1. Detects cycles using DFS
2. Breaks any cycle by removing the lowest-confidence edge and logging it in `state.json`
3. Logs the final inferred graph in `.pipeline/dependency-graph.json`

Example inferred structure:

| Wave | Issues | Reason |
|------|--------|--------|
| 0 | #49, #50, #51, #52, #53, #54, #55, #57, #58, #59, #62, #64, #65, #66 | No deps |
| 1 | #56, #60, #61 | Depend on provider implementations |
| 2 | #63, #67 | Depend on docs + providers |
| 3 | #68 | Depends on #67 |

Actual graph determined at runtime from real issue content.

---

## Subagent Lifecycle

### Preflight

Before doing any work, the subagent checks:
```bash
# Prune stale git worktree index entries first
git worktree prune
# If worktree directory exists from a prior partial run → remove it
if [ -d ".worktrees/issue-{id}" ]; then
  git worktree remove --force .worktrees/issue-{id}
fi
# If branch already exists on remote → delete it
if git ls-remote --heads origin feat/issue-{id}-{slug} | grep -q .; then
  git push origin --delete feat/issue-{id}-{slug}
fi
```

### Implementation Loop

```
1. git worktree add .worktrees/issue-{id} -b feat/issue-{id}-{slug}
2. gh issue view {id}  →  read full issue body
3. Implement the change
4. [Local review loop, max 3 iterations]:
   a. superpowers:requesting-code-review
   b. simplify skill
   c. If code-reviewer subagent returns no serious issues → break
   d. Else fix issues → go to (a)
5. git push origin feat/issue-{id}-{slug}
6. gh pr create --title "feat: {title}" --body "Closes #{id}"
7. Write .pipeline/signals/{id}.started  (contains PR number from step 6)
```

### PR Feedback Loop

**Retry budget:** 3 total push attempts (initial push + 2 fix pushes).
The counter `attempt_count` tracks fix pushes only.
Rule: `if attempt_count >= 2 → FAIL`.

**Per-iteration rule:** Each loop iteration performs at most ONE `fix + push`.
If both CI failures AND serious bot comments exist in the same iteration,
fix ALL of them in a single commit and push once. `attempt_count` increments by 1 per loop iteration, never more.

**CI timeout:** 30 minutes maximum wait per CI run. If exceeded, treat as CI failure.

```
LOOP:
  if attempt_count >= 2 → FAIL     ← guard at top: fires before waiting for CI
  Wait for gh pr checks (poll every 60s, timeout 30 min)
  Collect all issues to fix this iteration:
    - If CI failed: read gh run view --log-failed, collect fixes
    - Read bot comments: gh pr reviews + gh api .../comments
    - Collect all serious comment fixes
  If CI green AND no serious bot comments:
    → BREAK → SUCCESS
  Apply ALL fixes in one commit, git push
  attempt_count++
  continue loop

SUCCESS:
  gh pr merge --squash   (CI is already confirmed green; --auto is not needed)
  git worktree remove .worktrees/issue-{id}
  git push origin --delete feat/issue-{id}-{slug}  (remote branch cleanup)
  Write .pipeline/signals/{id}.done
```

### Failure Path

```
If open PR exists: gh pr close {pr_number} --comment "Pipeline: abandoning after {n} attempts"
git push origin --delete feat/issue-{id}-{slug}  (remote branch cleanup)
git worktree remove --force .worktrees/issue-{id}
gh issue comment {id} \
  --body "⚠️ Pipeline failed after {n} attempts.\n\nReason: {failure_reason}\n\nLast CI: {url}"
Write .pipeline/signals/{id}.failed
```

---

## "Serious" Comment Classification

The subagent uses AI judgment to classify bot/copilot comments:

- **Serious (must fix):** logic errors, missing error handling, API misuse, test failures,
  security issues, broken functionality, compilation errors
- **Skip:** style nits, cosmetic suggestions, compliments, `[optional]` suggestions,
  already-addressed comments, whitespace-only

---

## Gradle Concurrency

With 3 parallel subagents each running `./gradlew` in separate worktrees, the shared
Gradle daemon pool and `~/.gradle/caches` can cause conflicts.

**Mitigation:** Each subagent runs Gradle with `--no-daemon`:
```bash
./gradlew --no-daemon test
./gradlew --no-daemon :core:koverVerify
```

The per-worktree `.gradle/` build directory provides isolation for build outputs.
The shared `~/.gradle/caches/` is read-only during a build and safe for concurrent reads.

---

## Signal Files

| File | Written by | Meaning |
|------|-----------|---------|
| `.pipeline/signals/{id}.started` | Subagent | Worktree created; contains PR number |
| `.pipeline/signals/{id}.done` | Subagent | PR merged successfully |
| `.pipeline/signals/{id}.failed` | Subagent or orchestrator (cascade) | Issue could not be resolved |

---

## Output

**`.pipeline/REPORT.md`** written at completion:

```markdown
# Pipeline Report — 2026-03-21

## Summary
- Merged: 17 issues
- Failed: 3 issues

## Failed Issues
- #58 Detekt custom rules: CI failed 2 fix attempts (ktlint formatting error)
- #63 Docs website: Bot comment unresolvable after 2 fix attempts
- #68 ConfigCat provider: Dependency #67 failed → auto-skipped
```

---

## Skill File Structure

`.claude/skills/process-issues.md` contains three sections separated by markdown headers:

```markdown
---
name: process-issues
description: Autonomous issue processing pipeline
---

# Orchestrator Prompt
[Instructions for the ralph-loop orchestrator: startup, iteration logic, dispatch]

# Subagent Prompt Template
[Instructions for each per-issue subagent: full lifecycle]
Variables: {issue_id}, {issue_title}, {issue_body}, {branch}, {worktree_path}

# Dependency Inference Prompt
[One-shot prompt to infer adjacency list from issue list JSON]
```

---

## File Manifest

| Path | Purpose |
|------|---------|
| `.claude/skills/process-issues.md` | Skill (orchestrator + subagent + inference prompts) |
| `.pipeline/state.json` | Runtime state — orchestrator-only writes (gitignored) |
| `.pipeline/signals/{id}.started` | Subagent signal: worktree ready, PR created |
| `.pipeline/signals/{id}.done` | Subagent signal: merged |
| `.pipeline/signals/{id}.failed` | Subagent/orchestrator signal: failed or cascaded |
| `.pipeline/dependency-graph.json` | Inferred graph (informational) |
| `.pipeline/REPORT.md` | Final summary |
| `.gitignore` | Must include `.pipeline/` |

---

## Verification Plan

1. **Preflight test:** Run with unauthenticated `gh` — confirm pipeline halts with error
2. **Single dry-run:** state.json with only #53, `max_concurrent: 1`
   - Confirm worktree created, PR created, CI polling works, merge succeeds
   - Confirm `.pipeline/signals/53.done` written
3. **Collision recovery:** Pre-create `.worktrees/issue-53` — confirm preflight removes it
4. **Parallel:** 3 independent issues, confirm 3 worktrees simultaneously active
5. **Failure cascade:** Mark #67 as failed — confirm #68 immediately marked failed
6. **Failure path:** Simulate unresolvable CI — confirm GitHub comment + PR closed + branch deleted
7. **Full run:** `/process-issues` on all 20 issues, confirm `PIPELINE_COMPLETE`
