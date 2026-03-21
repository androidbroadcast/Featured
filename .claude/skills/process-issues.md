---
name: process-issues
description: Autonomously process all open GitHub issues through implement→review→PR→CI→merge. Uses ralph-loop with max 3 parallel worktrees. Output PIPELINE_COMPLETE when done.
---

# Orchestrator Prompt

You are the orchestrator for the Featured project issue processing pipeline.
You run as a ralph-loop body. When all issues reach terminal state, output exactly:
PIPELINE_COMPLETE

## Startup Phase

Run ONLY when `.pipeline/state.json` does not exist.

### 1. Preflight check

```bash
gh auth status
```

If this fails, output:
```
PIPELINE_ERROR: gh CLI not authenticated. Run `gh auth login` first.
PIPELINE_COMPLETE
```
Then stop.

### 2. Fetch all open issues

```bash
mkdir -p .pipeline/signals
gh issue list --state open --json number,title,body,labels \
  --limit 100 > .pipeline/issues.json
echo "Fetched $(cat .pipeline/issues.json | python3 -c 'import sys,json; print(len(json.load(sys.stdin)))') issues"
```

### 3. Infer dependencies

Use the **Dependency Inference Prompt** section below. Replace `{ISSUES_JSON}` with
the full content of `.pipeline/issues.json`. Parse the returned JSON.

Write the result to `.pipeline/dependency-graph.json`.

### 4. Write state.json

Build `.pipeline/state.json` from the issues and inference result:

```json
{
  "issues": [
    {
      "id": <number from gh issue>,
      "title": <title string>,
      "status": "pending",
      "worktree_path": null,
      "branch": "feat/issue-<id>-<slug>",
      "pr_number": null,
      "dependencies": [<ids from dependency-graph.json for this issue>],
      "attempt_count": 0,
      "failure_reason": null
    }
  ],
  "max_concurrent": 3,
  "started_at": "<current ISO 8601 timestamp>",
  "completed_at": null
}
```

For `<slug>`: take the title, lowercase it, replace spaces and special chars with hyphens,
truncate to 40 characters.

## Iteration Phase

Runs every iteration (including after startup).

### 1. Load state

```bash
cat .pipeline/state.json
```

### 2. Process signal files

For each file in `.pipeline/signals/`:

- **`{id}.started`**: Read its content (contains the PR number). Update issue `{id}`:
  set `pr_number` to the value read. Delete the signal file.

- **`{id}.done`**: Update issue `{id}`: set `status` → `"done"`. Delete the signal file.

- **`{id}.failed`**: Update issue `{id}`: set `status` → `"failed"`.
  **Cascade:** find all issues whose `dependencies` array contains `{id}`.
  For each cascaded issue: set `status` → `"failed"`,
  `failure_reason` → `"dependency #{id} failed"`,
  and write `.pipeline/signals/{cascaded_id}.failed`.
  Delete the original signal file.

After processing all signals, save the updated `state.json`.

### 3. Check completion

Count issues where `status` is `"pending"` or `"in_progress"`.

If count == 0:
1. Write `.pipeline/REPORT.md`:

```markdown
# Pipeline Report — {date}

## Summary
- Total: {n} issues
- Merged: {merged_count}
- Failed: {failed_count}

## Merged Issues
{for each done issue: "- #{id} {title} — {pr_url}"}

## Failed Issues
{for each failed issue: "- #{id} {title} — {failure_reason}"}
```

2. Update `state.json`: set `completed_at` to current ISO timestamp.
3. Output: `PIPELINE_COMPLETE`

### 4. Dispatch subagents

Find all **dispatchable** issues:
- `status == "pending"`
- every ID in `dependencies[]` has `status == "done"`
- count of issues with `status == "in_progress"` < `max_concurrent`

For each dispatchable issue (dispatch up to fill the concurrent slot limit):

1. Update `state.json`: set issue `status` → `"in_progress"`, `worktree_path` →
   `.worktrees/issue-{id}`.
2. Dispatch a subagent using the **Subagent Prompt Template** section below.
   Substitute these variables in the template before dispatching:
   - `{ISSUE_ID}` → the issue number
   - `{ISSUE_TITLE}` → the issue title string
   - `{ISSUE_BODY}` → the full issue body text
   - `{BRANCH}` → the `branch` field from state.json
   - `{WORKTREE_PATH}` → `.worktrees/issue-{id}`
3. Save `state.json` after each dispatch.

# Subagent Prompt Template

You are an autonomous subagent implementing GitHub issue #{ISSUE_ID}: {ISSUE_TITLE}

**Issue body:**
{ISSUE_BODY}

**Worktree:** {WORKTREE_PATH}
**Branch:** {BRANCH}
**Signal dir:** `.pipeline/signals/`

Complete all phases in order. Do not stop until you write a `.done` or `.failed` signal.

---

## Phase 1: Create Worktree

Invoke skill: `superpowers:using-git-worktrees`
- Target directory: `{WORKTREE_PATH}`
- Branch: `{BRANCH}`
- The skill handles preflight cleanup (existing worktree/branch from prior runs).

All subsequent work is done inside `{WORKTREE_PATH}`.

---

## Phase 2: Implement

Re-read the issue body above carefully. Then invoke skill: `superpowers:test-driven-development`

Key project conventions (from CLAUDE.md):
- All public declarations need explicit visibility modifiers (Explicit API mode)
- JVM target: Java 21, Kotlin 2.2.0, Android minSdk: 24, compileSdk: 36
- Use version catalog (`gradle/libs.versions.toml`) for all dependency versions — never hardcode versions
- Run `./gradlew --no-daemon test` to verify tests pass

---

## Phase 3: Local Quality Loop (max 3 iterations)

```
for i in 1..3:
  a. Invoke skill: superpowers:requesting-code-review
  b. Invoke skill: simplify
  c. If reviewer returns no serious issues → break
  d. Fix issues → continue
```

---

## Phase 4: Pre-Push Verification

Invoke skill: `superpowers:verification-before-completion`

Run the full check suite:
```bash
cd {WORKTREE_PATH}
./gradlew --no-daemon test :core:koverVerify spotlessCheck
```
Expected: `BUILD SUCCESSFUL` with no test failures.
If it fails, diagnose and fix before proceeding.

---

## Phase 5: Push and Create PR

```bash
cd {WORKTREE_PATH}
git push origin {BRANCH}

PR_URL=$(gh pr create \
  --title "feat: {ISSUE_TITLE}" \
  --body "$(cat <<'PRBODY'
Closes #{ISSUE_ID}
PRBODY
)")
PR_NUMBER=$(gh pr view {BRANCH} --json number -q .number)
echo "$PR_NUMBER" > .pipeline/signals/{ISSUE_ID}.started
```

---

## Phase 6: PR Feedback Loop

**Budget:** `attempt_count` starts at 0. Max fix pushes: 2. Rule: if `attempt_count >= 2` → FAIL.

```
LOOP:
  GUARD: if attempt_count >= 2 → go to FAILURE PATH

  WAIT FOR CI (poll every 60s, timeout 1800s / 30 min):
    end_time = now + 1800
    while now < end_time:
      result=$(gh pr checks $PR_NUMBER 2>&1)
      if echo "$result" | grep -q "pass"; then CI_PASSED=true; break; fi
      if echo "$result" | grep -q "fail\|error"; then CI_PASSED=false; break; fi
      sleep 60
    if timed out → CI_PASSED=false

  COLLECT FIXES:
    fixes_needed=false

    If CI_PASSED == false:
      Invoke skill: superpowers:systematic-debugging
        Read logs: gh run view --log-failed
      Apply all identified fixes.
      fixes_needed=true

    REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
    Read bot comments:
      gh pr reviews $PR_NUMBER --json author,body,state
      gh api repos/$REPO/pulls/$PR_NUMBER/comments

    For each SERIOUS comment (logic errors, API misuse, missing tests, security issues,
    compilation errors — NOT style nits, compliments, or [optional] suggestions):
      Fix the code.
      Reply: gh api repos/$REPO/pulls/$PR_NUMBER/comments --method POST -f body="Fixed."
      fixes_needed=true

  If CI_PASSED == true AND fixes_needed == false:
    → break to SUCCESS PATH

  Invoke skill: superpowers:verification-before-completion
  git add -A
  git commit -m "fix: address CI/review feedback (attempt $((attempt_count + 1)))"
  git push origin {BRANCH}
  attempt_count=$((attempt_count + 1))
  continue loop
```

---

## SUCCESS PATH

Invoke skill: `superpowers:finishing-a-development-branch`
Select the option: **merge to main (squash merge)**.
The skill handles: `gh pr merge --squash`, worktree removal, remote branch deletion.

```bash
echo "merged" > .pipeline/signals/{ISSUE_ID}.done
```

---

## FAILURE PATH

```bash
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
if [ -n "$PR_NUMBER" ]; then
  gh pr close $PR_NUMBER \
    --comment "⚠️ Pipeline: abandoning after $attempt_count fix attempts."
fi
```

Invoke skill: `superpowers:finishing-a-development-branch`
Select the option: **discard branch**.
The skill handles: worktree removal and remote branch deletion.

```bash
gh issue comment {ISSUE_ID} \
  --body "$(cat <<'COMMENT'
⚠️ Pipeline failed after $attempt_count attempts.

**What was tried:** Implemented the issue, passed local review, created PR, attempted CI fixes.
**Reason:** Exhausted retry budget — CI or bot review comments could not be resolved.

Please implement this issue manually.
COMMENT
)"

echo "failed" > .pipeline/signals/{ISSUE_ID}.failed
```

# Dependency Inference Prompt

You are analyzing GitHub issues for a Kotlin Multiplatform library called Featured.
Infer dependency relationships between issues.

Given these issues as JSON:
{ISSUES_JSON}

Return a JSON object with this exact structure and no other text:
```json
{
  "dependencies": {
    "<issue_id>": [<list of issue IDs this issue depends on>],
    ...
  },
  "confidence": {
    "<id>-><id>": "high|medium|low",
    ...
  },
  "cycles_broken": ["<id>-><id> (reason)"]
}
```

Rules:
- Issue A depends on issue B if A **cannot be meaningfully implemented** without B first.
- Common patterns: documentation issues depend on the features they document;
  integration guides depend on the providers they describe; platform module issues
  depend on individual provider implementations; Dokka/API-reference issues depend
  on KDoc audit issues.
- Issues with no dependencies: `"<id>": []`
- If a cycle exists, remove the lowest-confidence edge, record it in `cycles_broken`.
- Output ONLY the JSON object. No explanation, no markdown fences.
