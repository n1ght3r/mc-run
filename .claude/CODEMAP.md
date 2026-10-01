# CODEMAP: mc-run

Where every file lives and what it is responsible for. This is the **human index**; the rules live in
[AGENTS.md](../AGENTS.md) and the four package `AGENTS.md` files. The machine-readable knowledge
graph is [codemap.json](codemap.json), **generated** by `python tools/codemap.py` from the source
(nodes = files, `uses` edges = references, roles = first KDoc sentence).

`0.1.0` · Kotlin (`kotlin-dsl`), Java 17 bytecode · Gradle 9.7.1 · base package
`io.github.n1ght3r.mcrun` (paths below are relative to `src/main/kotlin/io/github/n1ght3r/mcrun/`).

When you add, remove or rename a file: update the row here, then regenerate `codemap.json`.

---

## Start here

| I want to... | Go to |
|---|---|
| Add or change a DSL property | [McRunExtension.kt](../src/main/kotlin/io/github/n1ght3r/mcrun/McRunExtension.kt) (property) + [McRunPlugin.kt](../src/main/kotlin/io/github/n1ght3r/mcrun/McRunPlugin.kt) (convention + `wireServer`/`wireClient`) + the matching `ServerInputs`/`ClientInputs` field |
| Change how the server starts | [server/ServerLaunchBuilder.kt](../src/main/kotlin/io/github/n1ght3r/mcrun/server/ServerLaunchBuilder.kt) (affects `runServer` and `runDev`) |
| Change how the client starts | [client/ClientLaunchBuilder.kt](../src/main/kotlin/io/github/n1ght3r/mcrun/client/ClientLaunchBuilder.kt) (affects `runClient` and `runDev`) |
| Support a new server type (Purpur) | [ServerType.kt](../src/main/kotlin/io/github/n1ght3r/mcrun/ServerType.kt) + a resolver next to [server/PaperFillApi.kt](../src/main/kotlin/io/github/n1ght3r/mcrun/server/PaperFillApi.kt), dispatched from `ServerLaunchBuilder` |
| Add a plugin source | [server/PluginSource.kt](../src/main/kotlin/io/github/n1ght3r/mcrun/server/PluginSource.kt) + [server/PluginDownloads.kt](../src/main/kotlin/io/github/n1ght3r/mcrun/server/PluginDownloads.kt) + a DSL method in `PluginDownloadsSpec` |
| A Mojang metadata change breaks the client | [client/MojangManifest.kt](../src/main/kotlin/io/github/n1ght3r/mcrun/client/MojangManifest.kt), [client/RuleEvaluator.kt](../src/main/kotlin/io/github/n1ght3r/mcrun/client/RuleEvaluator.kt), [client/ClientInstaller.kt](../src/main/kotlin/io/github/n1ght3r/mcrun/client/ClientInstaller.kt) |
| Shutdown / orphan process problems | [dev/DevSession.kt](../src/main/kotlin/io/github/n1ght3r/mcrun/dev/DevSession.kt) |
| Anything network or cache | `download/` + [download/AGENTS.md](../src/main/kotlin/io/github/n1ght3r/mcrun/download/AGENTS.md) |

---

## Root package

| File | Role |
|---|---|
| `McRunPlugin.kt` | Applies `java-base`, creates the extension, sets conventions, registers `mcRunDownloads` and the four tasks, resolves the Java toolchain lazily |
| `McRunExtension.kt` | DSL: `McRunExtension`, `ServerSpec`, `PluginDownloadsSpec`, `ClientSpec`. `PAPER` / `FOLIA` shorthands so scripts need no import |
| `McRunTask.kt` | Base task: download service reference, per-task work dir, `launch()` through `ExecOperations` |
| `ServerType.kt` | `PAPER`, `FOLIA` with their Fill project ids |

## download/

| File | Role |
|---|---|
| `Downloader.kt` | HTTP with retries, checksums, atomic writes, `cachedText`, offline handling |
| `DownloadService.kt` | Build service wrapping one `Downloader` |
| `Cache.kt` | Cache path layout |
| `Checksum.kt` | SHA-1 / SHA-256 value type and digests |
| `FileLocks.kt` | JVM + OS file locks for cache writers |

## server/

| File | Role |
|---|---|
| `RunServerTask.kt` | `runServer`, `ServerInputs` |
| `ServerLaunchBuilder.kt` | Shared server preparation, `ServerSettings` |
| `PaperFillApi.kt` | Fill v3 resolution and jar cache |
| `PluginDownloads.kt` | Modrinth, Hangar, URL resolvers |
| `PluginSource.kt` | `downloadPlugins` entry model |
| `ServerDirPreparer.kt` | `server.properties` patch, EULA check |

## client/

| File | Role |
|---|---|
| `InstallClientTask.kt` | `installClient`, writes `launch.json` |
| `RunClientTask.kt` | `runClient`, `ClientInputs` |
| `ClientInstaller.kt` | Client jar, libraries, natives, assets |
| `ClientLaunchBuilder.kt` | Placeholder values, quick play, `options.txt`, `ClientSettings` |
| `LaunchSpec.kt` | Install result persisted as JSON |
| `LaunchArguments.kt` | Template resolution |
| `MojangMetadata.kt` | Cached manifest / version JSON, Java major lookup |
| `MojangManifest.kt` | Version JSON parser and models |
| `RuleEvaluator.kt` | Rules, `Environment`, classifier arch |

## dev/

| File | Role |
|---|---|
| `RunDevTask.kt` | `runDev` task |
| `DevSession.kt` | Two-process lifecycle |

## util/

| File | Role |
|---|---|
| `JvmCommand.kt` | Command model, Java argument file writer |
| `JavaVersions.kt` | Offline fallback Minecraft version to Java major |
| `OfflineUuid.kt` | Offline-mode UUID |
| `PluginInfo.kt` | Plugin version (from `mc-run.properties`), User-Agent |

## Tests (`src/test/kotlin/...`)

| File | Covers |
|---|---|
| `client/RuleEvaluatorTest.kt` | Rules, OS/arch mapping, classifier arch |
| `client/LaunchArgumentsTest.kt` | Placeholders, dropped flags, classpath stripping |
| `client/MojangManifestTest.kt` | Parsing, macOS `-XstartOnFirstThread`, native variant selection |
| `server/ServerDirPreparerTest.kt` | Properties patch keeps other keys, EULA |
| `server/PaperFillApiTest.kt` | Fill response parsing |
| `util/UtilTest.kt` | Offline UUID, Java table, argument file quoting |
| `McRunPluginFunctionalTest.kt` | TestKit: tasks, configuration cache reuse, defaults; `installClient` (network) |
| `server/ServerDownloadNetworkTest.kt` | Real Fill, Modrinth, Hangar downloads (network) |

---

## Decisions (and where the build differs from the original design)

| Original design | Built | Why |
|---|---|---|
| `RunServerTask` / `RunClientTask` extend `JavaExec` | `DefaultTask` + `ExecOperations` | Classpath and main class are known only after downloading inside the action; changing task inputs during execution is an error on Gradle 9 |
| Choose `-cp` handling | `-cp ${classpath}` stripped from templates; the classpath goes into a Java `@argfile` | Windows command-line length limit; same path for all three run tasks |
| Run directory `run/` or `run/{version}/` | `run/{version}/server` and `run/{version}/client`, both overridable (`server.runDirectory`, `client.gameDirectory`) | Chosen at session start; keeps server and client files apart |
| GitHub user, license, JSON lib | `n1ght3r`, MIT, Gson | Chosen at session start |
| `nogui` | `--nogui` | Same effect, option form |
| Extract natives to `natives/{id}` | Extract into the dir `-Djava.library.path` resolves to | 26.x points it at `natives/{id}/java` |
| (not designed up front) | Native variants filtered by arch | Mojang rules only check the OS; all arch variants would collide |
| (not designed up front) | `-Pmcrun.cacheDir` | Lets TestKit tests share the real cache |
| Purpur, version-suffixed tasks, debug port, Velocity | Not built | See Roadmap in AGENTS.md |
