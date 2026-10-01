# server/

Turns the `server { }` block into a running Paper or Folia process.

## Files

| File | Responsibility |
|---|---|
| `RunServerTask` | `runServer` task plus `ServerInputs`, the `@Nested` input bundle shared with `runDev` |
| `ServerLaunchBuilder` | Shared entry point: prepare dir, check EULA, resolve jar + plugins, build the `JvmCommand`. Also `ServerSettings` |
| `PaperFillApi` | Fill v3 lookup, build selection, jar download with `.sha256` sidecar |
| `PluginDownloads` | Modrinth / Hangar / URL resolvers into the plugin cache |
| `PluginSource` | Sealed, serializable model of one `downloadPlugins` entry |
| `ServerDirPreparer` | `server.properties` line patch, EULA check |

## Fill API (verified against the live API, 2026-10)

`GET https://fill.papermc.io/v3/projects/{paper|folia}/versions/{version}/builds` returns an array
(newest first, but mc-run sorts by `id` anyway) of
`{ id, channel, downloads: { "server:default": { name, url, size, checksums: { sha256 } } } }`.
Selection: pinned `build` if set, else newest `STABLE`, else newest of any channel with a warning.
The "latest" list is cached 3 hours; a pinned build with a valid sidecar never touches the network.

## Plugins

- Modrinth: `GET /v2/project/{slug}/version/{version_number}` (one call, the version number works as
  id). Primary file, `hashes.sha1`.
- Hangar: `GET /api/v1/projects/{slug}/versions/{version}`, `downloads.PAPER` (Folia uses it too),
  `fileInfo.sha256Hash`. Externally hosted versions have no `downloadUrl`: tell the user to use `url()`.
- URL: cached forever by URL hash; verified only when a SHA-256 is given.
- Version metadata of Modrinth and Hangar is immutable, so it is cached forever.

## Launch

Main class comes from the jar manifest (Paperclip), never hard-coded. Program args:
`--nogui`, then `--add-plugin=<abs path>` per jar (own jar first, then downloads), then user args.
`acceptEula = true` becomes `-Dcom.mojang.eula.agree=true`. Paperclip unpacks `versions/` and
`libraries/` into the run directory on first start; that is expected.

Expected log noise: with `acceptEula = true` Paper and Folia print three ERROR lines about "the
Spigot command line EULA agreement flag". That is their standard notice for the system property.

Verified manually (2026-10-01): Paper 1.21.11, Paper 26.2 and Folia 1.21.11 start, load the dev
plugin and ViaVersion through `--add-plugin`, accept console input and stop cleanly.
