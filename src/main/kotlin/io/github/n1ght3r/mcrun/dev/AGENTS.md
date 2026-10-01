# dev/

`runDev`: server and client in one task. `JavaExec` blocks, so `DevSession` manages both processes.

## Files

| File | Responsibility |
|---|---|
| `RunDevTask` | Builds both `JvmCommand`s through the shared launch builders and hands them to `DevSession` |
| `DevSession` | Process lifecycle, output pumping, stdin forwarding, ready detection, shutdown |

## Lifecycle

1. Start the server (`ProcessBuilder`, stderr merged), pump its output unprefixed, keep the last 40
   lines for error messages, forward Gradle's stdin to it.
2. Wait for `Done \(\d+(\.\d+)?s\)!`. Server death or the timeout (`readyTimeoutSeconds`, default
   180) fails the task with the tail.
3. Start the client, output prefixed `[client] `.
4. Poll every 200 ms: client gone -> `stop` to the server, wait 30 s, then kill the tree; server gone
   -> kill the client tree.
5. Cancellation interrupts the task thread; a JVM shutdown hook covers daemon death. Both paths run
   `shutdown()`, which is idempotent.
6. Non-zero server exit fails the build only when mc-run did not ask it to stop.

## Rules

- `shutdown()` holds `this` while the server stops (up to 30 s). The output pumps must never need
  that monitor, or the server blocks on a full stdout pipe. The log tail has its own lock.
- The stdin forwarder polls `available()` instead of blocking in `read()`, so it ends with the session
  and does not steal input from the next build in the same daemon.
- Kill with `descendants()` first: a launcher-style child would otherwise survive as an orphan.
- Verified manually on Windows (2026-10-01) with Paper 1.21.11 and Paper 26.2: ready detection,
  auto-join, client close stops the server, no orphan java processes. Re-run that check after any
  change here.
