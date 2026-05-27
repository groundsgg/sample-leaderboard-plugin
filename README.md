# sample-leaderboard-plugin

End-to-end demo of the v2.2 [Service Architecture](https://grounds.atlassian.net/wiki/spaces/GK/pages/216662019).

A minimal Paper plugin that:

- Declares `services.leaderboard` in `grounds.yaml`
- Forge injects `LEADERBOARD_SERVICE_URL` into the pod's env
- The Grounds SDK reads it via `GroundsServices.channel("leaderboard")`
- On every `PlayerJoinEvent` the plugin calls `LeaderboardService.submitScore`
  to bump the player's `"logins"` board in ACCUMULATE mode

No service URL, credential, or gRPC channel construction in the plugin —
all of that lives in the SDK + forge's env-injection.

## Build + push

```bash
# Push to your per-dev vCluster (requires `grounds login` first).
./gradlew groundsPush
```

The plugin will land in `<sanitized-username>/sample-leaderboard-plugin`
inside your dev vCluster, alongside the bundle-provisioned
`service-leaderboard`.

## What this proves

| Layer | Implementation | Shipped in |
|---|---|---|
| Proto | `gg.grounds.grpc.leaderboard.LeaderboardService` | [library-grpc-contracts#52](https://github.com/groundsgg/library-grpc-contracts/pull/52) |
| SDK (gRPC + JWT-auth + channel cache) | `GroundsServices.channel("leaderboard")` | [library-grpc-contracts#49](https://github.com/groundsgg/library-grpc-contracts/pull/49) |
| NATS SDK | `GroundsEvents.connect()` (not used here, demo is request/response) | [library-grpc-contracts#51](https://github.com/groundsgg/library-grpc-contracts/pull/51) |
| Service impl | `gg.grounds.api.LeaderboardGrpcService` (Postgres-backed) | [service-leaderboard](https://github.com/groundsgg/service-leaderboard) |
| Bundle wiring | per-dev vCluster includes `service-leaderboard` | [library-platform-bundle#10](https://github.com/groundsgg/library-platform-bundle/pull/10) |
| Forge ENV-Gen | `{KEY}_SERVICE_URL` injection | [grounds-forge#250](https://github.com/groundsgg/grounds-forge/pull/250) |
| **Plugin via SDK** | this repo | — |
