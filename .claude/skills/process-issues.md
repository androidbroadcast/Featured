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

<!-- Task 5 fills this section -->

# Dependency Inference Prompt

<!-- Task 6 fills this section -->
