# download/

Every byte mc-run fetches goes through this package. Nothing else opens an HTTP connection.

## Files

| File | Responsibility |
|---|---|
| `Downloader` | HTTP GET with User-Agent, 3 retries (no retry on 4xx except 429), checksum + size verification, atomic writes, `cachedText` for metadata |
| `DownloadService` | Build service (`mcRunDownloads`) holding one `Downloader` per build; tasks get it via `@ServiceReference` |
| `Cache` | Path layout of `~/.gradle/caches/mc-run/` (override: `-Pmcrun.cacheDir`) |
| `Checksum` | SHA-1 / SHA-256 value type, file and string digests |
| `FileLocks` | In-JVM `ReentrantLock` + OS `FileChannel.lock` on a hidden `.<name>.lock` sibling |

## Cache layout

```
servers/{paper|folia}/{version}/{build}.jar (+ .sha256 sidecar, builds.json)
plugins/{modrinth|hangar}/{slug}/{version}/...   plugins/url/{sha1(url)[0..16]}/...
client/version_manifest_v2.json
client/versions/{id}/{id}.json, {id}.jar
client/libraries/{maven path}
client/natives/{id}/            (26.x: natives/{id}/java/)
client/assets/indexes/{id}.json, assets/objects/{xx}/{hash}
```

## Rules

- **Write atomically**: temp file in the target directory, verify, then `ATOMIC_MOVE`. A failed
  replace is tolerated only when a valid file already exists (Windows keeps running jars locked).
- **Lock big artifacts** with `download`. Assets use `downloadUnlocked`: thousands of files, and two
  writers produce identical verified content.
- **`cachedText` freshness**: a matching checksum or a file younger than `maxAge` is used without a
  request; on network failure or offline, a stale copy beats failing.
- **Offline** (`Downloader.offline`): any request that would hit the network throws
  `DownloadException` with the URL, so the message says what is missing from the cache.
- Keep this package free of Minecraft knowledge; resolvers live in `server/` and `client/`.
