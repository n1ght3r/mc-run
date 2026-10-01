# mc-run

Gradle plugin (`io.github.n1ght3r.mc-run`) that launches a **Paper / Folia dev server** and a
**vanilla Minecraft client** for any supported version from one build. It replaces
`xyz.jpenilla.run-paper` and adds `runDev`, which starts both and joins the client to the server.
Kotlin (`kotlin-dsl`), single module, published through JitPack
(`com.github.n1ght3r:mc-run:<tag>`, repo https://github.com/n1ght3r/mc-run).

## Build and test

```bash
./gradlew build            # compile, unit + TestKit tests, validatePlugins
./gradlew test             # offline tests only (tag "network" excluded)
./gradlew networkTest      # hits Mojang, Fill, Modrinth, Hangar; downloads ~600 MB on first run
python tools/codemap.py    # regenerate the knowledge graph (.claude/codemap.json)
```

`networkTest` writes into the real `~/.gradle/caches/mc-run`, so a second run is fast.

### Manual check matrix (not automated)

Run after touching `dev/`, `client/` or the launch builders, ideally on Windows and Linux:

- `runServer`: Paper 1.21.11 and 26.x, Folia 1.21.11
- `runClient`: 1.21.11 and 26.x, auto-join works
- `runDev`: start, join, close the client, the server stops cleanly
- Ctrl+C during `runDev` leaves no orphan java processes

Easiest setup: a throwaway project whose `settings.gradle.kts` has
`pluginManagement { includeBuild("<path to mc-run>") }`.

## Stack and constraints

- Plugin bytecode is **Java 17** (`options.release = 17`, Kotlin `-Xjdk-release=17`) so it loads in
  any Gradle on 17+. Do not call JDK APIs newer than 17 (for example `HttpClient.close()` is 21+).
  The launched server and client get their JDK from **toolchains**, never the Gradle JVM.
- One runtime dependency: **Gson**. `kotlin-dsl` provides the Kotlin stdlib through Gradle; do not
  add `kotlin("jvm")` or a second JSON library.
- Developed on Gradle 9.7.1. Everything must stay **configuration-cache compatible**
  (`gradle.properties` turns it on for this build; TestKit tests check reuse).
- Base package `io.github.n1ght3r.mcrun`. Comments and KDoc are in English. No em dash characters
  anywhere in the repo (code, comments, docs).

## Architecture in one pass

[McRunPlugin.kt](src/main/kotlin/io/github/n1ght3r/mcrun/McRunPlugin.kt) creates the `mcRun { }`
extension ([McRunExtension.kt](src/main/kotlin/io/github/n1ght3r/mcrun/McRunExtension.kt)), sets the
conventions, registers the `mcRunDownloads` build service and four tasks:

| Task | Class | Does |
|---|---|---|
| `installClient` | `client/InstallClientTask` | client jar, libraries, natives, assets into the cache; writes `build/mc-run/client/<v>/launch.json` |
| `runServer` | `server/RunServerTask` | resolve the server jar + plugins, prepare `run/<v>/server`, run in the foreground |
| `runClient` | `client/RunClientTask` | read `launch.json`, resolve placeholders, run the client in `run/<v>/client` |
| `runDev` | `dev/RunDevTask` | both of the above in one `DevSession` with ready detection and shared lifecycle |

Data flow: **DSL (`ServerSpec` / `ClientSpec`) → task `@Nested` inputs (`ServerInputs` /
`ClientInputs`) → serializable settings (`ServerSettings` / `ClientSettings`) → launch builder →
`JvmCommand` → `ExecOperations.exec` or `ProcessBuilder`.** The builders
(`ServerLaunchBuilder`, `ClientLaunchBuilder`) are shared by the single-process tasks and `runDev`,
so a fix in one path fixes both.

All network access goes through `download/Downloader` (retries, checksums, file locks, cache-aware
reads). Tasks reach it through the `DownloadService` build service; configuration-time code (the
Java version lookup) builds its own `Downloader` on the same cache.

## Where to look

Per-file index: [.claude/CODEMAP.md](.claude/CODEMAP.md) (human) and
[.claude/codemap.json](.claude/codemap.json) (generated knowledge graph: nodes, roles, `uses` edges).

| Task | Location |
|---|---|
| DSL property, default value, task wiring | [McRunExtension.kt](src/main/kotlin/io/github/n1ght3r/mcrun/McRunExtension.kt), [McRunPlugin.kt](src/main/kotlin/io/github/n1ght3r/mcrun/McRunPlugin.kt) |
| HTTP, retries, checksums, cache layout, locks | `download/` (see [download/AGENTS.md](src/main/kotlin/io/github/n1ght3r/mcrun/download/AGENTS.md)) |
| Paper/Folia jars, Modrinth/Hangar/URL plugins, `server.properties`, EULA | `server/` (see [server/AGENTS.md](src/main/kotlin/io/github/n1ght3r/mcrun/server/AGENTS.md)) |
| Mojang metadata, rules, natives, assets, launch arguments | `client/` (see [client/AGENTS.md](src/main/kotlin/io/github/n1ght3r/mcrun/client/AGENTS.md)) |
| Server + client together, ready line, shutdown | `dev/` (see [dev/AGENTS.md](src/main/kotlin/io/github/n1ght3r/mcrun/dev/AGENTS.md)) |
| Java major per Minecraft version | `client/MojangMetadata.javaMajorFor`, fallback `util/JavaVersions` |
| Command line, Java argument files | [util/JvmCommand.kt](src/main/kotlin/io/github/n1ght3r/mcrun/util/JvmCommand.kt) |
| Consumer docs | [README.md](README.md) |

## Invariants

**Run tasks are `DefaultTask` + `ExecOperations`, not `JavaExec`.** The classpath, main class and
arguments are only known after downloading, and a task's input properties must not change once it
executes. Keep resolution inside the task action and the inputs immutable.

**Every launch goes through a `JvmCommand` with a Java argument file.** The client classpath is long
enough to break the Windows command-line limit. Never build a raw `java -cp ...` line elsewhere.

**The EULA is never accepted silently.** `acceptEula` defaults to `false`; `ServerDirPreparer.checkEula`
fails unless the DSL or `eula.txt` says yes.

**`server.properties` is patched, not rewritten.** Only `online-mode` and `server-port` are touched,
line by line; every other line stays byte for byte.

**Plugins are passed with `--add-plugin`, never copied** into the server's `plugins/` folder, so no
stale jars accumulate.

**Everything downloaded is verified** (SHA-1 for Mojang files and Modrinth, SHA-256 for Fill and
Hangar) and written atomically (temp file, then move) under a file lock. The cache is shared by every
project and Gradle process on the machine.

**Offline must keep working.** `--offline` / `-Pmcrun.offline` serves only what is cached; every
resolver has a cached path that needs no network.

**Configuration-cache safety.** Tasks hold only `Property` / `ConfigurableFileCollection` inputs,
`@ServiceReference` services and serializable settings. No `Project` access at execution time.

## Keeping the knowledge graph current

The graph is part of the work, not an afterthought. When you add, remove, rename or repurpose a
file:

1. Give the main declaration a KDoc whose **first sentence states its role**. `tools/codemap.py`
   reads that sentence into the graph.
2. Run `python tools/codemap.py` (and `--check` before committing) to regenerate
   `.claude/codemap.json`.
3. Update the row in [.claude/CODEMAP.md](.claude/CODEMAP.md) and the package `AGENTS.md` if the
   file's responsibility or an invariant changed.
4. Record a design decision (and why) in the "Decisions" section of
   [.claude/CODEMAP.md](.claude/CODEMAP.md).

## Releasing

Push a tag `vX.Y.Z`, then open https://jitpack.io/#n1ght3r/mc-run and check the build log
(`jitpack.yml` pins JDK 21). Consumers map the plugin id to the JitPack module in
`pluginManagement.resolutionStrategy` (see [README.md](README.md)). Publishing to the Gradle Plugin
Portal would remove that mapping.

## Roadmap

Not built yet, in rough priority order:

- Purpur server type (`https://api.purpurmc.org/v2/purpur/{version}/latest/download`).
- Version-suffixed tasks (`runServer1_21_11`, `runClient26_2`, ...) generated from a list.
- Debug port (`-Pmcrun.debug`), JetBrains Runtime hot-swap.
- Velocity proxy in front of the server.
- Out of scope on purpose: Microsoft login, modded clients, redistributing any Mojang or Paper jar.
