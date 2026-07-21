# `setup-env.sh --container` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in `--container` flag to `setup-env.sh` that runs the chatbot in a Docker or Podman container instead of via `./mvnw`, auto-detecting which tool is installed and working, asking only when both are available.

**Architecture:** A new self-contained multi-stage `Dockerfile` builds the app with nothing but the container runtime required on the host (no local JDK/Maven needed). `setup-env.sh` gains flag parsing, a tool-detection function, and a `run_container` function that builds and runs the image, called at the very end instead of `exec ./mvnw "$@"` when container mode is active. Everything before that final launch step (the interactive Twitch/locale variable prompts) is unchanged.

**Tech Stack:** POSIX `sh` (matching the existing script's `#!/bin/sh` shebang — no bashisms), Docker/Podman, `eclipse-temurin:25-jdk`/`eclipse-temurin:25-jre` base images (both confirmed to exist and pull successfully as of this plan).

## Global Constraints

- Spec: [docs/superpowers/specs/2026-07-21-setup-env-container-option-design.md](../specs/2026-07-21-setup-env-container-option-design.md) — read it before starting, every task below implements a piece of it.
- `./mvnw` remains the default launch path. Nothing changes for anyone who doesn't pass `--container`.
- No port mapping: the bot's Twitch chat connection is outbound-only (Twitch4j's `TwitchChatBuilder`), and the embedded Spring web server on 8080 serves nothing (no actuator, no controllers) — don't add `-p` anywhere.
- No new dependency beyond Docker/Podman themselves and the base images — no compose, no bats/shellcheck test harness (none exists in this repo; adding one is out of scope).
- Absolute `CHATBOT_COMMANDS_FILE` paths are a known, accepted limitation (out of scope) — the volume mount only needs to work correctly for the default/relative-path case.
- No silent fallback to `./mvnw` if container mode is requested but fails (missing/broken tool, requested tool unavailable) — always a clear error message to stderr and a non-zero exit.
- This repo has no automated shell test harness. Every verification step below uses real, directly-runnable commands (this plan's author confirmed Docker is installed and working in the reference environment, Podman is not — steps are written to exercise both the "tool available" and "tool unavailable" paths for real using that fact; a human with both tools installed should still run the manual checklist in the spec's Testing section before merging).

---

## File Structure

- Create: `Dockerfile` (repo root) — multi-stage build producing the runnable image.
- Create: `.dockerignore` (repo root) — keeps the build context small.
- Modify: `setup-env.sh` — flag parsing, tool detection/validation, `run_container`, updated help comment, updated `--clear` unset list, updated `offer_save`.

---

### Task 1: `Dockerfile` and `.dockerignore`

**Files:**

- Create: `Dockerfile`
- Create: `.dockerignore`

**Interfaces:**

- Produces: a `docker build -t chatbot .` (or `podman build -t chatbot .`) that succeeds and produces a runnable image whose `ENTRYPOINT` starts the Spring Boot app. Task 2's `run_container` function depends on the image being buildable under the tag name `chatbot` from the repo root.

This task has no application code to drive with a red/green cycle — its "test" is a real build and a real run, verified with the exact commands below (already dry-run by this plan's author; expected outputs are what was actually observed, not guesses).

- [ ] **Step 1: Create `.dockerignore`**

```
target/
.git/
.claude/
node_modules/
```

- [ ] **Step 2: Create `Dockerfile`**

```dockerfile
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 3: Build the image**

Run: `docker build -t chatbot .`
Expected: the build stage's `RUN ./mvnw package -DskipTests` ends with `[INFO] BUILD SUCCESS`, and the overall build finishes without error. Confirm the image exists:

Run: `docker images chatbot`
Expected: one row with `REPOSITORY=chatbot`, `TAG=latest`.

(If your environment has Podman instead of Docker, substitute `podman` for `docker` in both commands — the Dockerfile itself is tool-agnostic.)

- [ ] **Step 4: Smoke-test that the built jar actually launches the Spring app**

Run: `timeout 20 docker run --rm -e TWITCH_CHANNEL=smoketest -e TWITCH_BOT_USERNAME=smoketest -e TWITCH_OAUTH_TOKEN=smoketest chatbot`
Expected: the process runs for the full 20 seconds (dummy credentials mean it can't actually complete a Twitch connection, so `timeout` kills it — that's expected and fine). Exit code is `124` (timeout's own "killed" code), not an early failure. The captured output contains a line matching `Started ChatbotApp in <N> seconds` — this proves the jar is well-formed and the Spring context started successfully, which is what this step is actually checking (not the Twitch connection itself, which needs real credentials and is out of scope for this smoke test).

If instead you see an early exit (before 20s) with something like `no main manifest attribute` or `ClassNotFoundException`, the jar packaging is broken — check the `COPY --from=build /app/target/*.jar app.jar` line matches the actual jar name produced by `./mvnw package` (there should be exactly one jar in `target/`; if the build produces more than one, e.g. an `-original` variant sometimes left by Spring Boot's repackage goal, narrow the glob).

- [ ] **Step 5: Confirm the build context is reasonably small (`.dockerignore` is working)**

Run: `docker build -t chatbot . 2>&1 | grep "transferring context"`
Expected: a context size in the tens of MB, not hundreds+ (this repo's `target/` and `.git/` directories are much larger than the source alone — if you see a huge context size, `.dockerignore` isn't being picked up; confirm it's at the repo root, same directory as the `Dockerfile`).

- [ ] **Step 6: Commit**

```bash
git add Dockerfile .dockerignore
git commit -m "Add multi-stage Dockerfile for running the chatbot via Docker or Podman"
```

---

### Task 2: `setup-env.sh` — `--container` flag, detection, and launch

**Files:**

- Modify: `setup-env.sh`

**Interfaces:**

- Consumes: the `chatbot` image tag built by Task 1 (`docker build -t chatbot .` / `podman build -t chatbot .`), and the existing `TWITCH_CHANNEL`, `TWITCH_BOT_USERNAME`, `TWITCH_OAUTH_TOKEN`, `CHATBOT_COMMANDS_FILE`, `CHATBOT_LOCALE` variables already collected earlier in the script (unchanged).
- Produces: nothing anything else in the codebase depends on — this is the script's only consumer-facing surface, exercised directly via the CLI.

This task is one cohesive change to a single file. Steps are still small and independently runnable/revertible, but there's no "write a failing test, then the code" cycle in the usual TDD sense (no test framework exists for this script) — each step below is verified by actually running the modified script with the given inputs and confirming the given real output.

- [ ] **Step 1: Update the header comment**

In `setup-env.sh`, the header comment block currently reads (lines 1–14):

```sh
#!/bin/sh
# Ensures the Twitch chatbot environment variables are set, then runs ./mvnw.
# Missing mandatory variables are always prompted for.
#
#   ./setup-env.sh                       # prompt only for what's missing, then run ./mvnw
#   ./setup-env.sh -u|--update           # review and override every variable, even if already set
#   eval "$(./setup-env.sh -c|--clear)"  # unset every variable in your current shell
#
# --clear also removes any saved block from your shell profile, and prints unset commands: a
# subprocess cannot remove variables from the shell that started it, so eval is what actually
# applies that part to your session.
#
# If any value had to be typed, you're offered to save it to your shell profile so it persists
# across sessions. Declining still launches the app with the values just entered.
```

Replace it with:

```sh
#!/bin/sh
# Ensures the Twitch chatbot environment variables are set, then runs ./mvnw.
# Missing mandatory variables are always prompted for.
#
#   ./setup-env.sh                          # prompt only for what's missing, then run ./mvnw
#   ./setup-env.sh -u|--update              # review and override every variable, even if already set
#   eval "$(./setup-env.sh -c|--clear)"     # unset every variable in your current shell
#   ./setup-env.sh --container              # run via Docker or Podman (auto-detected) instead of ./mvnw
#   ./setup-env.sh --container=docker       # same, forcing Docker specifically
#   ./setup-env.sh --container=podman       # same, forcing Podman specifically
#
# --clear also removes any saved block from your shell profile, and prints unset commands: a
# subprocess cannot remove variables from the shell that started it, so eval is what actually
# applies that part to your session.
#
# If any value had to be typed, you're offered to save it to your shell profile so it persists
# across sessions. Declining still launches the app with the values just entered.
```

- [ ] **Step 2: Add `container_requested`/`container` variables and extend the flag-parsing `case` block**

Find:

```sh
update=0
prompted=0

case "${1:-}" in
  -u | --update)
    update=1
    shift
    ;;
  -c | --clear)
    strip_profile_block
    echo 'unset TWITCH_CHANNEL TWITCH_BOT_USERNAME TWITCH_OAUTH_TOKEN CHATBOT_COMMANDS_FILE CHATBOT_LOCALE'
    exit 0
    ;;
esac
```

Replace with:

```sh
update=0
prompted=0
container_requested=0
container=""

case "${1:-}" in
  -u | --update)
    update=1
    shift
    ;;
  -c | --clear)
    strip_profile_block
    echo 'unset TWITCH_CHANNEL TWITCH_BOT_USERNAME TWITCH_OAUTH_TOKEN CHATBOT_COMMANDS_FILE CHATBOT_LOCALE CHATBOT_CONTAINER_TOOL'
    exit 0
    ;;
  --container)
    container_requested=1
    shift
    ;;
  --container=*)
    container_requested=1
    container=${1#--container=}
    shift
    ;;
esac
```

- [ ] **Step 3: Verify `--clear` still works and now also unsets the new variable**

Run: `./setup-env.sh --clear`
Expected output (exact line, nothing else since no profile block exists yet in a fresh checkout):

```
unset TWITCH_CHANNEL TWITCH_BOT_USERNAME TWITCH_OAUTH_TOKEN CHATBOT_COMMANDS_FILE CHATBOT_LOCALE CHATBOT_CONTAINER_TOOL
```

- [ ] **Step 4: Add the `tool_usable` helper and `detect_container_tool`/`validate_container_tool` functions**

Add these new functions after the existing `offer_save` function (i.e. right before the line `ask TWITCH_CHANNEL "Twitch channel name"`):

```sh
tool_usable() {
  command -v "$1" >/dev/null 2>&1 && "$1" info >/dev/null 2>&1
}

validate_container_tool() {
  if ! tool_usable "$container"; then
    echo "$container is not available and working." >&2
    exit 1
  fi
}

detect_container_tool() {
  docker_ok=0
  podman_ok=0
  tool_usable docker && docker_ok=1
  tool_usable podman && podman_ok=1

  if [ "$docker_ok" -eq 1 ] && [ "$podman_ok" -eq 0 ]; then
    container=docker
    return 0
  fi

  if [ "$podman_ok" -eq 1 ] && [ "$docker_ok" -eq 0 ]; then
    container=podman
    return 0
  fi

  if [ "$docker_ok" -eq 0 ] && [ "$podman_ok" -eq 0 ]; then
    echo 'Neither docker nor podman is available and working.' >&2
    exit 1
  fi

  if [ -n "${CHATBOT_CONTAINER_TOOL:-}" ]; then
    container=$CHATBOT_CONTAINER_TOOL
    return 0
  fi

  printf 'Both docker and podman are available. Which one do you want to use? [docker/podman]: '
  read -r container_answer
  case "$container_answer" in
    docker | podman) container=$container_answer ;;
    *)
      echo 'Please answer "docker" or "podman".' >&2
      exit 1
      ;;
  esac

  CHATBOT_CONTAINER_TOOL=$container
  prompted=1
}
```

- [ ] **Step 5: Add `run_container`**

Add this function right after `detect_container_tool` (still before the `ask TWITCH_CHANNEL ...` line):

```sh
# Trailing args ("$@") are Maven-specific (forwarded to ./mvnw in the
# default path) and don't apply to a container run, so they're not
# forwarded here.
run_container() {
  "$container" build -t chatbot .
  "$container" run --rm \
    -e TWITCH_CHANNEL -e TWITCH_BOT_USERNAME -e TWITCH_OAUTH_TOKEN -e CHATBOT_LOCALE \
    -v "$PWD/$CHATBOT_COMMANDS_FILE:/app/$CHATBOT_COMMANDS_FILE" \
    chatbot
}
```

- [ ] **Step 6: Wire detection/validation into the main flow, after the existing variable prompts**

Find:

```sh
ask TWITCH_CHANNEL "Twitch channel name"
ask TWITCH_BOT_USERNAME "Twitch bot account username"
ask_secret TWITCH_OAUTH_TOKEN "Twitch OAuth token (no 'oauth:' prefix)"

: "${CHATBOT_COMMANDS_FILE:=commands.txt}"
ask CHATBOT_COMMANDS_FILE "Commands file path"
ask CHATBOT_LOCALE "Locale (fr or en, blank = auto-detect from system)"

if [ "$prompted" -eq 1 ]; then
  offer_save
fi

exec ./mvnw "$@"
```

Replace with:

```sh
ask TWITCH_CHANNEL "Twitch channel name"
ask TWITCH_BOT_USERNAME "Twitch bot account username"
ask_secret TWITCH_OAUTH_TOKEN "Twitch OAuth token (no 'oauth:' prefix)"

: "${CHATBOT_COMMANDS_FILE:=commands.txt}"
ask CHATBOT_COMMANDS_FILE "Commands file path"
ask CHATBOT_LOCALE "Locale (fr or en, blank = auto-detect from system)"

if [ "$container_requested" -eq 1 ]; then
  if [ -n "$container" ]; then
    validate_container_tool
  else
    detect_container_tool
  fi
fi

if [ "$prompted" -eq 1 ]; then
  offer_save
fi

if [ -n "$container" ]; then
  run_container
else
  exec ./mvnw "$@"
fi
```

- [ ] **Step 7: Add `CHATBOT_CONTAINER_TOOL` to `offer_save`'s persisted block**

Find, inside `offer_save`:

```sh
    echo "export CHATBOT_COMMANDS_FILE=\"$CHATBOT_COMMANDS_FILE\""
    [ -n "$CHATBOT_LOCALE" ] && echo "export CHATBOT_LOCALE=\"$CHATBOT_LOCALE\""
    echo "$PROFILE_END"
```

Replace with:

```sh
    echo "export CHATBOT_COMMANDS_FILE=\"$CHATBOT_COMMANDS_FILE\""
    [ -n "$CHATBOT_LOCALE" ] && echo "export CHATBOT_LOCALE=\"$CHATBOT_LOCALE\""
    [ -n "$CHATBOT_CONTAINER_TOOL" ] && echo "export CHATBOT_CONTAINER_TOOL=\"$CHATBOT_CONTAINER_TOOL\""
    echo "$PROFILE_END"
```

- [ ] **Step 8: Verify bare `--container` auto-detects correctly when exactly one tool is available**

Run: `TWITCH_CHANNEL=t TWITCH_BOT_USERNAME=t TWITCH_OAUTH_TOKEN=t CHATBOT_COMMANDS_FILE=commands.txt CHATBOT_LOCALE=en timeout 30 ./setup-env.sh --container > /tmp/container-autodetect.log 2>&1; echo "exit: $?"; grep "Started ChatbotApp" /tmp/container-autodetect.log`

(Same non-interactive pre-export trick as before.) This exercises `detect_container_tool`'s "exactly one candidate works" branch directly — no prompt should appear anywhere in the log, and it should reach `run_container` and build/run using whichever single tool your environment actually has (Docker, in this plan's reference environment). Expected: `exit: 124` (killed by `timeout`, meaning it ran without an early failure) and one line matching `Started ChatbotApp in <N> seconds`.

- [ ] **Step 9: Verify the "neither tool available" path (portable — works regardless of what's actually installed)**

Run: `PATH=/nonexistent TWITCH_CHANNEL=t TWITCH_BOT_USERNAME=t TWITCH_OAUTH_TOKEN=t CHATBOT_COMMANDS_FILE=commands.txt CHATBOT_LOCALE=en ./setup-env.sh --container; echo "exit: $?"`

Overriding `PATH` to a directory with nothing in it makes `command -v docker`/`command -v podman` fail regardless of what's really installed, without needing an environment that's actually missing both tools. `./setup-env.sh` itself still runs fine (the shebang invokes `/bin/sh` directly, not via `PATH` lookup, and every other shell construct used before this point — `read`, `printf`, `case`, `eval` — is a shell builtin, not an external command). Expected: stderr shows exactly `Neither docker nor podman is available and working.`, and `exit: 1`.

- [ ] **Step 10: Verify the forced-tool-unavailable path (real, deterministic — requires an environment where at least one of Docker/Podman is genuinely not installed)**

Run: `TWITCH_CHANNEL=t TWITCH_BOT_USERNAME=t TWITCH_OAUTH_TOKEN=t CHATBOT_COMMANDS_FILE=commands.txt CHATBOT_LOCALE=en ./setup-env.sh --container=podman`

(Pre-exporting all five variables makes this fully non-interactive — the `ask`/`ask_secret` calls see `$current` already set and `$update=0`, so they export and return immediately without prompting; see the existing `ask` function's early-return branch.)

Expected, if Podman is not installed/working in your environment (true of this plan's reference environment): stderr shows exactly `podman is not available and working.` and the script exits non-zero (`$?` is `1`). Confirm with:

Run: `echo $?`
Expected: `1`

If Podman _is_ available in your environment, run the equivalent with whichever of the two tools is genuinely absent instead, to exercise the same path.

- [ ] **Step 11: Verify the end-to-end forced-tool-available path**

Run: `TWITCH_CHANNEL=t TWITCH_BOT_USERNAME=t TWITCH_OAUTH_TOKEN=t CHATBOT_COMMANDS_FILE=commands.txt CHATBOT_LOCALE=en timeout 30 ./setup-env.sh --container=docker`

Expected: no interactive prompts (all variables pre-exported, so `prompted` stays `0` and `offer_save` never fires). The script proceeds straight to `run_container`: `docker build -t chatbot .` runs (fast if Task 1's image is still cached), then `docker run --rm ...` starts the app in the foreground. `timeout 30` kills it after 30s (exit code `124`) since there's no real Twitch connection to complete — same reasoning as Task 1 Step 4. Confirm the container actually started the app by capturing output to a file and checking it:

Run: `TWITCH_CHANNEL=t TWITCH_BOT_USERNAME=t TWITCH_OAUTH_TOKEN=t CHATBOT_COMMANDS_FILE=commands.txt CHATBOT_LOCALE=en timeout 30 ./setup-env.sh --container=docker > /tmp/container-run.log 2>&1; grep "Started ChatbotApp" /tmp/container-run.log`
Expected: one matching line, e.g. `Started ChatbotApp in 6.4 seconds ...`.

If your environment only has Podman, substitute `--container=podman` and `podman build`/`podman run` — the script code is identical either way, only the invoked tool differs.

- [ ] **Step 12: Verify the `commands.txt` volume mount is wired correctly**

Run: `docker run --rm -v "$PWD/commands.txt:/app/commands.txt" --entrypoint cat chatbot /app/commands.txt`
Expected: output is byte-identical to `cat commands.txt` run on the host. This confirms the mount path resolution is correct; it doesn't exercise a live Twitch round-trip (that needs real credentials and a real channel, out of scope for an automated check — the underlying "re-read the file on every command" behavior is unchanged, existing `FileCommandRepository` logic, not touched by this feature).

- [ ] **Step 13: Run the full existing test suite to confirm nothing in the Java app was affected**

Run: `./mvnw verify`
Expected: `BUILD SUCCESS`, all JaCoCo coverage checks met — this task only touches `setup-env.sh`, `Dockerfile`, and `.dockerignore`, none of which are part of the Maven build, so this should be unaffected. Running it anyway confirms no accidental collateral damage (e.g. an errant file edit).

- [ ] **Step 14: Format and commit**

```bash
pnpm prettier:format
git add setup-env.sh
git commit -m "Add --container flag to setup-env.sh: run via Docker or Podman"
```

---

### Task 3: Manual verification checklist (human, not automated)

**Files:** none — this task is a documentation/verification pass, no code changes.

The reference environment used to write and verify this plan has Docker but not Podman, so Tasks 1–2's steps already exercise the "only one tool available" and "requested tool unavailable" paths for real. The scenarios below need an environment with **both** Docker and Podman installed to verify — they cannot be exercised by an implementer working only from this plan's reference environment. Have a human (or an agent working in a machine with both tools) run through these before considering the feature fully validated:

- [ ] **Step 1: Both tools available, no `CHATBOT_CONTAINER_TOOL` set** — run `./setup-env.sh --container` (with the other variables already set so only the container-tool question appears). Expected: prompted once with `Both docker and podman are available. Which one do you want to use? [docker/podman]: `, answer `podman`, offered to save afterward (since answering set `prompted=1`).
- [ ] **Step 2: Re-run `./setup-env.sh --container` immediately after** (assuming the save was accepted in Step 1). Expected: no prompt this time — `CHATBOT_CONTAINER_TOOL=podman` is read from the environment/profile and used directly.
- [ ] **Step 3: Real Twitch round-trip** — with real `TWITCH_CHANNEL`/`TWITCH_BOT_USERNAME`/`TWITCH_OAUTH_TOKEN` values and `./setup-env.sh --container`, confirm the bot actually joins the configured channel and responds to a `!` command typed in chat (per `commands.txt`).
- [ ] **Step 4: `commands.txt` hot-reload while containerized** — with the container from Step 3 still running, edit `commands.txt` on the host (add or change a `!command=response` line) and confirm the next matching chat message gets the updated response, without restarting the container.

This task has no commit of its own — it's a verification gate. If any scenario fails, open a follow-up fix against the relevant task above.
