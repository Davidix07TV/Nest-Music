# Self-hosting a Metroserver for Nest Music

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

There is only one public instance (`wss://metroserverx.meowery.eu/ws`) and the maintainers state
there are no others — the request for an instance list is
[still open](https://github.com/MetrolistGroup/metroserver/issues/4). Self-hosting is therefore the
way to give Nest Music a server that accepts it.

## What this stack gives you

- `metroserver` behind Caddy, so `wss://` works with an automatic, auto-renewed certificate.
- A `ua_policy.json` that explicitly allows Nest Music: hosting works, and the rickroll/ad tiers do
  not apply to it. Matching is a case-insensitive *substring*, so `com.nestmusic.music.debug` and
  any future suffix are covered by the same entry.
- Metrolist and N-Zik clients keep working — their package names are compiled into the server's
  defaults, and a policy file can only extend the allow list, never remove from it.

## Requirements

- A host running Docker with the Compose plugin, reachable on ports 80 and 443.
- A (sub)domain with an `A`/`AAAA` record pointing at that host.

## Setup

```bash
cd deploy/metroserver
cp .env.example .env
$EDITOR .env                 # set LT_DOMAIN to your domain
docker compose up -d
docker compose logs -f caddy # wait until the certificate is issued
```

The endpoint is then:

```
wss://<LT_DOMAIN>/ws
```

The `/ws` path matters: the client connects to that path, not to the bare host.

## Pointing the apps at it

In Nest Music: **Settings → Listen Together → Server URL**, then paste `wss://<LT_DOMAIN>/ws`.
This works on any build, no rebuild needed.

To ship that server as the app's *default* instead, add it to the `ServersJson` list in
`app/src/main/kotlin/com/nestmusic/music/listentogether/ListenTogetherServers.kt` (the first entry
is the default).

## Operating notes

- Room state lives in memory; the `metroserver-data` volume persists restart recovery state and the
  per-User-Agent connection counters.
- Set `UA_ADMIN_TOKEN` in `docker-compose.yml` to expose the `/uas` report:

  ```bash
  curl -H 'Authorization: Bearer replace-me' https://<LT_DOMAIN>/uas
  ```

- To refuse a client instead of serving it an ad, add it to `block` in `ua_policy.json`. Precedence
  upstream is `block` → `rickroll` → `advert` → `allow`, so `block` always wins.
- `advert_title` is what non-allowed clients see as their queue title; edit it freely.
- The compose file pins `:latest`. Pin a specific digest or tag if you prefer reproducible upgrades.
