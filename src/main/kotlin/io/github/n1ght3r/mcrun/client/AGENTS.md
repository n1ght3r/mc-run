# client/

Installs and launches a vanilla client from Mojang's metadata, the same way the official launcher
and Fabric Loom do, without any mod loader.

## Files

| File | Responsibility |
|---|---|
| `InstallClientTask` | `installClient`: runs `ClientInstaller`, writes `launch.json`. Always runs (the cache is untracked) |
| `ClientInstaller` | Client jar, libraries, native variant selection + extraction, parallel asset download |
| `MojangMetadata` | Cached manifest + version JSON; `javaMajorFor` used at configuration time |
| `MojangManifest` | Hand-written parser and models (`VersionInfo`, `Library`, `Argument`, `Rule`, ...) |
| `RuleEvaluator` | `rules` evaluation (`Environment`), classifier arch detection |
| `LaunchSpec` | Install result as JSON: classpath, dirs, rule-filtered but unresolved templates |
| `LaunchArguments` | `${placeholder}` resolution, `-cp ${classpath}` stripping |
| `ClientLaunchBuilder` | Shared by `runClient` and `runDev`: placeholder values, quick play, `options.txt` defaults. Also `ClientSettings` |
| `RunClientTask` | `runClient` task plus `ClientInputs`, the `@Nested` input bundle shared with `runDev` |

## Facts about the metadata (checked on 1.21.11 and 26.2)

- `arguments.jvm` entries are a string or `{ rules, value }` with `value` a string or array.
  26.x also has `arguments.default-user-jvm` (launcher memory/GC defaults); mc-run ignores it, users
  pass `jvmArgs`.
- Native libraries are plain entries named `group:artifact:version:natives-<os>[-arm64|-x86]`.
  Their rules check **only the OS**, so all arch variants pass. `ClientInstaller.selectLibraries`
  keeps one variant per library for the current arch (falling back to the x86_64 one).
- 1.21.11 uses `-Djava.library.path=${natives_directory}`; 26.x uses `${natives_directory}/java`.
  Natives are extracted (flattened, `META-INF/` skipped) into whatever `java.library.path` resolves to.
- `-XstartOnFirstThread` sits under an `osx` rule in `arguments.jvm`; rule evaluation keeps it on macOS.
- All `features` are false: no demo, no custom resolution, no quick-play feature args. Quick play is
  appended by hand: `--quickPlayMultiplayer localhost:<port>` (1.20+).

## Placeholders

Offline values: `auth_access_token=0`, `user_type=legacy`, `auth_uuid` = undashed
`UUID.nameUUIDFromBytes("OfflinePlayer:<name>")`, `version_type=release`. `clientid` and
`auth_xuid` are empty: an empty bare value drops itself **and** its `--flag`. An unknown
placeholder drops the argument (and its flag if it was a bare value); a raw `${...}` never reaches
the game.

## Pitfalls

- `java.nio.file.Path` is `Iterable<Path>` in Kotlin: `listOfPaths + path` appends the path's
  **segments**. Always `+ listOf(path)`. This shipped once and broke the classpath.
- Expected noise on launch with an offline account: authlib 401 "Failed to fetch user properties",
  Realms JWT errors, "Failed to retrieve profile key pair". Not bugs. On some Windows machines oshi
  also logs "Unable to locate English counter names in registry Perflib 009" (the server prints it
  too); that is a broken Windows performance-counter registry, unrelated to mc-run.
- Verified manually (2026-10-01): 1.21.11 (LWJGL 3.3.3, Java 25 via override) and 26.2 (LWJGL
  3.4.1, natives under `natives/26.2/java`) both launch and auto-join.
