# Metroserver for Nest Music

Nest Music's **Listen Together** feature is a client of
[metroserver](https://github.com/MetrolistGroup/metroserver), the upstream Go WebSocket backend.
That server decides, from the client's `User-Agent`, who may host rooms and who gets an ad or a
rickroll instead (see `internal/server/uapolicy.go` upstream).

The app sends its own package name (`com.nestmusic.music`, or `com.nestmusic.music.debug`) as the
`User-Agent`. It is **not** in the upstream allow list, so on a server using the upstream policy
Nest Music cannot host rooms:

```
host_not_allowed: Only allowlisted clients can host rooms
```

The one public instance (`wss://metroserverx.meowery.eu/ws`) is run by Metrolist's maintainer, and
the request for a list of alternative instances is
[still open](https://github.com/MetrolistGroup/metroserver/issues/4). Self-hosting is how Nest
Music gets a server that accepts it: Nest Music now runs its own instance on Render
(`wss://nest-music-listen-together.onrender.com/ws`), and that is the default in new builds.

## What is in here

| File | Purpose |
| --- | --- |
| `Dockerfile` | Builds metroserver from upstream source **pinned to a commit**, with this directory's policy baked in. No dependency on their container registry. Built with the repository root as context. |
| `ua_policy.json` | Allows `com.nestmusic.music` (and `.debug`) to host, with no block/rickroll rules. |
| `docker-compose.yml` | VPS path: metroserver plus Caddy for automatic TLS. |
| `Caddyfile`, `.env.example` | Used by the compose stack. |
| `../../render.yaml` | Render Blueprint (Render only reads it from the repository root). |
| `../../.dockerignore` | Keeps the repository root small as a build context (`.git`, `ios`, `desktop`, `**/build`, …). |
| `../../.github/workflows/keep-warm.yml` | Pings `/health` every 10 minutes so the free Render instance does not sleep. |

Matching in the policy is a case-insensitive *substring*, so the single `com.nestmusic.music` entry
also covers the `.debug` package and any future suffix. Metrolist and N-Zik keep working: their
package names are compiled into the server defaults, and a policy file can only extend the allow
list, never remove from it.

## Option A — Render, free and without a credit card

Render's free instance type needs no payment method. It sleeps after ~15 minutes idle and wakes in
30-60 seconds; Nest Music retries the connection on its own, so the first attempt after a quiet
spell wakes the server and the room opens on a later attempt.

1. Render Dashboard → **New** → **Blueprint**.
2. Pick this repository. Render reads `render.yaml` from the root and shows one web service.
3. **Apply** and wait for the first build (a few minutes: it clones and compiles metroserver).
4. The endpoint is `wss://nest-music-listen-together.onrender.com/ws` — or whatever name Render
   assigns if that one is taken. The `/ws` path matters.
5. That URL is already the default in Nest Music, so nothing else is needed. If Render assigned a
   different name, paste it in the app: **Settings → Listen Together → Server URL**.

`autoDeploy` is off: pushing a commit to the app does not restart the server, because a restart
drops the rooms that are open at that moment. To deploy an update on purpose, use **Manual Deploy**
in the dashboard.

If creation fails with an error about the region, change the `region` in `render.yaml` (e.g. to
`oregon`) and re-apply — a failed creation leaves nothing behind, but a service that exists can
never change region.

## Option B — your own VPS with docker compose

Requirements: a host running Docker with the Compose plugin, reachable on ports 80 and 443, and a
(sub)domain whose `A`/`AAAA` record points at it.

```bash
cd deploy/metroserver
cp .env.example .env
$EDITOR .env                 # set LT_DOMAIN to your domain
docker compose up -d
docker compose logs -f caddy # wait until the certificate is issued
```

The endpoint is then `wss://<LT_DOMAIN>/ws`.

## Pointing the apps at it

In Nest Music: **Settings → Listen Together → Server URL**, then paste the `wss://…/ws` address.
This works on any build, no rebuild needed.

Nest Music's own instance is already the first entry of the `ServersJson` list in
`app/src/main/kotlin/com/nestmusic/music/listentogether/ListenTogetherServers.kt`, and the first
entry is the default for a fresh install. To ship a different server as the default, put it first in
that list.

## Keeping the free instance awake

`https://nest-music-listen-together.onrender.com/health` is pinged every 10 minutes by
`.github/workflows/keep-warm.yml`, which keeps the free instance from spinning down between plays
(GitHub Actions minutes are free on public repositories, and ~730 hours/month still fit Render's
750-hour free allowance). Scheduled workflows only run from the default branch and GitHub may delay
them by a few minutes, so an occasional cold start is still possible — Nest Music retries the
connection by itself.

## Operating notes

- Rooms live in memory. `DATABASE_FILE` only holds restart-recovery state and the per-User-Agent
  connection counters; on Render's free instance (no persistent disk) that is lost on redeploy, on
  a VPS the compose volume keeps it.
- With `UA_ADMIN_TOKEN` set, read the connected-client report:

  ```bash
  curl -H 'Authorization: Bearer <token>' https://<host>/uas
  ```

- `https://<host>/health` returns `{"status":"ok"}` and is what Render uses as a health check.
- To refuse a client instead of serving it an ad, add it to `block` in `ua_policy.json`. Precedence
  upstream is `block` → `rickroll` → `advert` → `allow`, so `block` always wins.
- `advert_title` is what non-allowed clients see as their queue title; edit it freely.

## Updating the pinned server

`Dockerfile` pins the upstream commit in `ARG METROSERVER_COMMIT`. Pick a new commit deliberately,
bump the argument, and deploy again:

```bash
# see what changed upstream since the pin
git clone --quiet https://github.com/MetrolistGroup/metroserver /tmp/metroserver
git -C /tmp/metroserver log --oneline 9da6f9da..origin/master
```

Metroserver is GPL-3.0, the same licence as the rest of this repository. This Dockerfile builds it
from source rather than redistributing a binary, so the corresponding source stays with the upstream
project at the pinned commit.
