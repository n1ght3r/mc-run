# mc-run

Gradle plugin that launches a **Paper / Folia dev server** and a **vanilla Minecraft client** for any
supported version from one build, plus `runDev`, which starts both and joins the client to the server
automatically. A replacement for `xyz.jpenilla.run-paper` with a client launcher.

Everything (server jars, plugins, the client, libraries, assets) is downloaded from the official
source onto your machine, verified, and cached in `~/.gradle/caches/mc-run`. Nothing from Mojang or
PaperMC is redistributed.

## Setup

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
    // JitPack rewrites the groupId, so map the plugin id to the module by hand.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.github.n1ght3r.mc-run") {
                useModule("com.github.n1ght3r:mc-run:${requested.version}")
            }
        }
    }
}

plugins {
    // Recommended: lets Gradle download the JDK a Minecraft version needs.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
```

```kotlin
// build.gradle.kts
plugins {
    id("io.github.n1ght3r.mc-run") version "v0.1.0"
}

mcRun {
    minecraftVersion = "1.21.11"
    server {
        type = PAPER                     // PAPER or FOLIA
        acceptEula = true                // explicit opt-in, never defaulted
        jvmArgs.addAll("-Xmx2G")
        downloadPlugins {
            modrinth("viaversion", "5.10.0")
            // hangar("ViaVersion", "5.10.0")
            // url("https://example.com/plugin.jar", sha256 = "...")
        }
    }
    client {
        username = "Dev"
    }
}
```

## Tasks

| Task | What it does |
|---|---|
| `runServer` | Runs the server in `run/<version>/server` with your plugin (`--add-plugin`) and the downloaded ones. Type commands in the console. |
| `runClient` | Runs the vanilla client in `run/<version>/client`, offline account, joins `localhost:<port>` |
| `runDev` | Starts the server, waits for `Done (...)!`, starts the client. Closing the client stops the server. |
| `installClient` | Downloads the client only (run by `runClient` / `runDev`) |

## Reference

| Property | Default |
|---|---|
| `minecraftVersion` | required |
| `javaVersion` | `javaVersion.majorVersion` from Mojang's version JSON (1.21.x: 21, 26.x: 25) |
| `offline` | `--offline` or `-Pmcrun.offline` |
| `server.type` | `PAPER` |
| `server.build` | newest STABLE build |
| `server.port` | `25565` |
| `server.acceptEula` | `false` (the task fails until you accept, here or in `eula.txt`) |
| `server.pluginJar` | `shadowJar` output when Shadow is applied, else `jar` output |
| `server.runDirectory` | `run/<version>/server` |
| `server.jvmArgs` / `server.args` | empty |
| `server.readyTimeoutSeconds` | `180` (for `runDev`) |
| `client.username` | `Dev` |
| `client.autoJoin` | `true` (`--quickPlayMultiplayer`, Minecraft 1.20+) |
| `client.gameDirectory` | `run/<version>/client` |
| `client.writeDefaultOptions` | `true` (first run only: skip onboarding and the multiplayer warning) |
| `client.jvmArgs` / `client.args` | empty |

`server.properties` is created if missing; mc-run only sets `online-mode=false` and `server-port`,
every other line is left alone. Microsoft login is out of scope: the client is offline-only.

## Development

See [AGENTS.md](AGENTS.md). `./gradlew build` runs the offline tests; `./gradlew networkTest` runs the
ones that download real artifacts.

## License

MIT, see [LICENSE](LICENSE).
