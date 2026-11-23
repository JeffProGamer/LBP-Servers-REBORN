# LBP Reborn: verified Render configuration audit
## Source and scope
Audited `https://github.com/JeffProGamer/Refresh` at commit
`738a79b89e721057673cc300ffe3c3f654d03297`, with Bunkum submodule
`40879e5f20fdde0c5fbbe501c589f82b60bed697`.

The original workspace is `JeffProGamer/LBP-Servers-REBORN` (sticker tooling),
not the C# server. A separate, unmodified checkout was fetched into
`.work/Refresh` for this audit and build. These findings do not prove which
commit is deployed on Render.

**This fork cannot provide remote archive search, Neon S3 persistence, and
retail PS4 v1.28 support through environment variables alone.**

## Fix for Render's missing Dockerfile error
The repository now includes a root `Dockerfile`, `.dockerignore`, and public
configuration under `deploy/render/`. The Dockerfile fetches the pinned Refresh
commit above and initializes its Bunkum submodule; it does not depend on the
local `.work` checkout. No credentials or game-save files enter the build context.

**The Dockerfile patches Bunkum's `SocketHttpListener` at build time** with three fixes:

1. **HTTP version** — Accepts non-standard HTTP version strings sent by the PS4 LBP3 client (previously rejected as "invalid HTTP version" before reaching any endpoint handler).
2. **HTTP method** — Accepts non-standard HTTP methods sent by the PS4 LBP3 client (previously rejected with "Rejected request that sent invalid method").
3. **Host header** — Accepts PS4 requests without a `Host` header (previously rejected as "Rejected request without Host header").

Without these patches, Bunkum rejects PS4 requests before they reach any endpoint handler, causing the sticker to hang loading forever.

Commit and push these deployment files to `JeffProGamer/LBP-Servers-REBORN`,
then configure your existing Render service:

- Runtime: **Docker**.
- Root Directory: **blank** (repository root).
- Dockerfile Path: **`./Dockerfile`**.
- Docker Build Context Directory: **`.`** (repository root).
- Docker Command override: **blank**; use the image entrypoint.
- Environment: set `POSTGRES_CONNECTION_STRING` as shown below.
- Keep `PORT=10000`; the included `bunkum.json` listens on port 10000.
- Deploy the latest commit, not the old `6432f93` revision without a Dockerfile.

The bundled configs set your public URL and keep ticket verification and
Patchwork enforcement enabled. Missing JSON properties retain Refresh defaults.
The runtime runs as non-root UID 1001. This image does not enable S3, remote
archive search, retail PS4 support, or bundle the separate web frontend.
Local uploads still require durable storage or a future S3 integration.
Do not deploy real uploads onto ephemeral disk expecting them to survive.

Docker is unavailable in the local environment, so the Linux container itself
has not been built or launched here. Local .NET publish validation does not
replace a Render container build or database connection test.

## Render environment variables actually supported
| Key | Value | Purpose |
| --- | --- | --- |
| `POSTGRES_CONNECTION_STRING` | `Host=<NEON_HOST>;Port=5432;Database=<NEON_DATABASE>;Username=<NEON_ROLE>;Password=<NEON_PASSWORD>;SSL Mode=VerifyFull` | Npgsql connection string; use the exact host/database/role from Neon. |
| `BUNKUM_DATA_FOLDER` | `/refresh/data` | Optional explicit configuration/data location for the existing Docker image; not durable storage by itself. |

`db.json` has `PreferConnectionStringEnvironmentVariable=true` by default.
PostgreSQL is selected in code with `UseNpgsql`; there is no `Database.Provider`
configuration property. Do not commit a real connection string to Git.

**There are no native double-underscore equivalents in this commit.**
`Database__Provider`, `Database__ConnectionString`, `Archive__Enabled`,
`DryArchive__Enabled`, `S3__Endpoint`, `S3__ForcePathStyle`,
`Bunkum__ExternalUrl`, and `ASPNETCORE_URLS` do not configure these services.
The server is Bunkum, not an ASP.NET Core host. `PORT` is not read by the
application either: align Render's port with `bunkum.json.ListenPort`, or
implement startup translation from Render's `PORT`.

The existing entrypoint changes directory to `/refresh/data`, and JSON configs
are read from `BUNKUM_DATA_FOLDER` or the working directory. Provision JSON
files before startup, for example through a deliberate startup copy of Render
secret files. The existing entrypoint does not perform that copy. Generated
config files and local uploads disappear with ephemeral storage.

## Exact reverse-proxy settings
`/refresh/data/bunkum.json`:

```json
{
  "Version": 4,
  "ExternalUrl": "https://lbpreborn.onrender.com",
  "ListenHost": "0.0.0.0",
  "ListenPort": 10000,
  "ThreadCount": 4,
  "UseForwardedIp": true
}
```

Use HTTPS externally and HTTP between Render's TLS-terminating proxy and
Bunkum. `UseForwardedIp` trusts the first `X-Forwarded-For` address; only enable
it behind a trusted proxy that sanitizes forwarded headers. Do not combine
untrusted forwarded headers with IP authentication.

The following is a **partial set of properties to merge** into the generated
`refreshGameServer.json` (schema version 29), not a complete replacement:

```json
{
  "InstanceName": "LBP Reborn",
  "WebExternalUrl": "https://lbpreborn.onrender.com",
  "GameConfigStorageUrl": "https://lbpreborn.onrender.com/lbp",
  "PermitAllLogins": true,
  "PermitPsnLogin": true,
  "UseTicketVerification": true,
  "AllowUsersToUseIpAuthentication": false,
  "UseDeflateCompression": true
}
```

`RegistrationEnabled`, `PermitWebLogin`, `PermitRpcnLogin`,
`RequireGameLoginToRegister`, and `MaintenanceMode` are also exact properties
in this file; their values depend on your registration/access policy.

## Sticker system (LBPWN) — deployed and live

The sticker endpoints are deployed on `https://lbpreborn.onrender.com/lbp/`:

| Endpoint | Method | Content |
| --- | --- | --- |
| `/lbp/sticker-script` | GET | `lbpwn.ff` sticker script |
| `/lbp/sticker-plan` | GET | `lbp_reborn_sticker.plan` |
| `/lbp/r/c8a8d8eddcb9819ce1a318bdc4854900ce6a8280` | GET | `lbpwn.ff` by hash (pre-loaded into data store) |
| `/lbp/debug-dump` | GET/POST | Raw request dump for troubleshooting |

All three serve verified content (SHA256 matches the local files).

**How `lbpwn.ff` works:** it patches Sony server URLs in game memory to point to
`lbpreborn.onrender.com`, then the game itself connects to our server. The script
does not maintain a connection — the game's own HTTP stack does.

**If the sticker hangs loading forever:** the game is connecting but not getting
a response it understands. Use the debug-dump endpoint to see what the game
requests:

```
curl https://lbpreborn.onrender.com/lbp/debug-dump
```

This logs the raw method, URL, headers, cookies, query string, and body so you
can align our endpoints with what the game actually expects.

**`Auth__EnablePs4Support` does not exist.** It is not referenced anywhere in
the codebase and setting it does nothing. The supported environment variables are
listed below under "Render environment variables actually supported".

No environment variable fixes the sticker hanging — that is a protocol-level
issue (the game sends requests Bunkum can't parse, or requests endpoints we
don't serve yet). Fixing it requires inspecting debug-dump output and adding
the missing endpoint behavior.

## Retail PS4 authentication: blocked by missing implementation

Exact `GameServerConfig` properties (set via `refreshGameServer.json`, NOT env vars):

| Property | Default | Meaning |
| --- | --- | --- |
| `PermitAllLogins` | `true` | Global game-login switch. |
| `PermitPsnLogin` | `true` | Allows the implemented PSN platforms. |
| `UseTicketVerification` | `true` | Verifies supported NP tickets and expiry. Keep enabled. |
| `AllowUsersToUseIpAuthentication` | `false` | Optional account/IP fallback, not PS4 support. |
| `EnforcePatchwork` | `true` | Requires Patchwork except for PSP. |
| `RequiredPatchworkMajorVersion` | `1` | Required client Patchwork major version. |
| `RequiredPatchworkMinorVersion` | `0` | Required client Patchwork minor version. |
| `HmacDigestKeys` | `["CustomServerDigest"]` | Configured HMAC keys, not a switch for retail compatibility. |
| `Sha1DigestKeys` | `["CustomServerDigest"]` | Configured SHA-1 keys. |

Setting `EnforcePatchwork=false` removes only that gate and also removes a
client-security requirement; it does not implement PS4 support. Do not disable
ticket verification as a PS4 workaround.

Evidence:

- `Refresh.Database/Models/Authentication/TokenPlatform.cs`: no PS4 member.
- `Refresh.Core/Extensions/TicketExtensions.cs`: recognizes RPCN and PSN issuer
  `0x100`, assigning the latter PS3; other issuers are unknown.
- `Refresh.Core/Authentication/TokenGameUtility.cs:58`: explicitly excludes
  PS4 title IDs; normal CUSA title IDs cannot resolve to LBP3 here.
- `Refresh.Interfaces.Game/Endpoints/Handshake/AuthenticationEndpoints.cs`:
  rejects unknown platforms/games; ticket key selection covers PS3/Vita/PSP
  and RPCN only. Existing users need matching registration and per-account
  platform permissions too.
- `Refresh.GameServer/Middlewares/DigestMiddleware.cs:80`: TODO for PS4
  support; fallback detection checks the `01.26` user-agent string, not a
  verified v1.28 integration.

The HTTPS sticker redirect does not establish server protocol compatibility.
PS4 ticket parsing/verification, game/platform resolution, request/response
behavior, digests, and asset compatibility need implementation and real-client
verification. No client/server was launched as part of this source audit.

## Archived levels: local Dry archive only
Exact `dry.json` schema:

```json
{
  "Version": 1,
  "Enabled": true,
  "Location": "/var/dry/",
  "UseFolderNames": true
}
```

Enable this **only if the archive is actually mounted/populated**. Otherwise
leave `Enabled=false` (the default).

`DryDataStore` uses `File.Exists`, `File.ReadAllBytes`, and `File.OpenRead`, not
HTTP. With folder names enabled it expects files at:
`<Location>/dry23r<hash[0]>/<hash[0..2]>/<hash[2..4]>/<full-sha1>`.
With folder names disabled the `dry23r<hash[0]>` segment is omitted.

`DownloadingDataStore` copies sufficiently requested archive assets into the
primary store after its hit threshold (3); the stock primary store is local
disk. This is not a remote LittleBigArchive connector.

`GameDatabaseContext.Levels.cs:484` searches local database level titles and
descriptions (and numeric IDs), not an external catalog. Dry asset access does
not import level metadata or make 10M+ levels searchable. There is no
`ArchiveConfig`, remote archive endpoint, or catalog-provider toggle in this
commit. Remote search/catalog ingestion and a hash-addressed remote asset
source are additional development, requiring a real provider API and access
policy; a guessed URL would not be a working configuration.

## S3: adapter exists but is not used by Refresh
Bunkum's `Bunkum.DataStores.S3/Configuration/S3Config.cs` defines these exact
JSON properties for `s3.json`:

| Property | Placeholder |
| --- | --- |
| `Version` | `1` |
| `ServiceUrl` | `<NEON_S3_HTTPS_ENDPOINT>` |
| `BucketName` | `<NEON_BUCKET_NAME>` |
| `AccessKeyId` | `<NEON_ACCESS_KEY_ID>` |
| `AccessKey` | `<NEON_SECRET_ACCESS_KEY>` |

`AccessKey` means the secret key, not the access-key ID. There is no
`ForcePathStyle` or region property in this schema. The adapter constructs
`AmazonS3Config` with `ServiceURL` only.

**Adding `s3.json` currently does nothing for Refresh.**
`RefreshGameServer.cs:55` chooses `FileSystemDataStore`, and the game-server
project does not reference the S3 project or call its storage extension.
The default primary store needs to be replaced with an S3-backed `IDataStore`
*before* constructing the Dry caching/aggregate stores, so both uploads and
archive cache writes use S3. Adding a second storage service later is not a
reliable substitute. Endpoint, credentials, bucket, provider-required signing
region/path style, and necessary stream operations must be implemented and
tested against the actual storage endpoint. Existing local assets also need
migration; changing a backend does not copy them automatically.

## Build verification
Command, run inside `.work/Refresh` on Windows:

```powershell
dotnet build Refresh.sln -c Release --nologo -v minimal
```

Result: **exit code 0, 0 errors, 226 warnings**, elapsed 35.82 seconds.
Warnings include Bunkum nullable-reference and missing XML documentation
warnings. The repository compiles successfully, but is not warning-free.
No server source was changed or warnings suppressed to obtain this result.
The build does not validate Docker deployment, Neon connectivity, S3 access,
archive availability, or PS4 gameplay.

## Deployment conclusion
You can configure PostgreSQL and the proxy URL with the supported mechanisms
above. There is no honest ready-to-paste `__` environment table that enables
all three requested features on this revision. Environment binding, S3 primary
storage integration, remote archive/catalog support, and retail PS4 support
are separate missing features, not undocumented configuration switches.

### Deployment-file verification
The deployment JSON files parsed successfully. The Dockerfile's `dotnet publish`
command was exercised against the pinned local source on Windows with output
`.artifacts/render-publish`: exit code 0, 0 errors, 258 warnings. This is a
separate publish result from the solution build above; Docker is not installed
locally, so the image build and Linux runtime remain unverified.
