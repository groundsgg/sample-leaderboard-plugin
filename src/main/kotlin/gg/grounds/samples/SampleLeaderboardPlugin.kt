package gg.grounds.samples

import gg.grounds.events.match.MatchEnded
import gg.grounds.grpc.leaderboard.LeaderboardServiceGrpc
import gg.grounds.grpc.leaderboard.SubmitMode
import gg.grounds.grpc.leaderboard.SubmitScoreRequest
import gg.grounds.sdk.GroundsEvents
import gg.grounds.sdk.GroundsEventsClient
import gg.grounds.sdk.GroundsServices
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin

/**
 * v2.2-Service-Architecture demo. Exercises the full SDK surface:
 *
 * - **Services** (`PlayerJoinEvent` → `LeaderboardService.submitScore`)
 *   bumps the player's `"logins"` board in ACCUMULATE mode.
 * - **Events** (`match.lifecycle.ended.>` → `MatchEnded` callback)
 *   submits a "wins" score for the winning team's players on every
 *   match completion. Subscription is established at onEnable and
 *   torn down at onDisable.
 *
 * The plugin never sees a service URL, NATS endpoint, or credential —
 * `GroundsServices.channel` reads `LEADERBOARD_SERVICE_URL`, and
 * `GroundsEvents.connect` reads `NATS_URL`; forge injects both into
 * the pod's env. The SDK attaches the projected
 * ServiceAccount-JWT to every gRPC call.
 *
 * Companion docs:
 * - Service Architecture v2.2 (Confluence 216662019)
 * - groundsgg/library-grpc-contracts/sdk/README.md
 */
class SampleLeaderboardPlugin : JavaPlugin(), Listener {

    private val leaderboard by lazy {
        LeaderboardServiceGrpc.newBlockingStub(GroundsServices.channel("leaderboard"))
    }
    private var events: GroundsEventsClient? = null

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        logger.info(
            "SampleLeaderboardPlugin enabled — leaderboard service: ${System.getenv("LEADERBOARD_SERVICE_URL") ?: "(unset)"}, NATS: ${System.getenv("NATS_URL") ?: "(unset)"}",
        )

        try {
            val client = GroundsEvents.connect()
            client.on("match.lifecycle.ended.>", MatchEnded.parser()) { event ->
                onMatchEnded(event)
            }
            events = client
            logger.info("Subscribed to match.lifecycle.ended.>")
        } catch (e: Exception) {
            // No NATS in dev / no events: block declared → log and
            // skip. The gRPC half of the plugin still works.
            logger.warning("event subscription disabled: ${e.message}")
        }
    }

    override fun onDisable() {
        events?.close()
        GroundsServices.shutdown()
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        try {
            val reply = leaderboard.submitScore(
                SubmitScoreRequest.newBuilder()
                    .setBoardId("logins")
                    .setPlayerId(player.uniqueId.toString())
                    .setScore(1)
                    .setMode(SubmitMode.SUBMIT_MODE_ACCUMULATE)
                    .build(),
            )
            logger.info("${player.name}: logins=${reply.effectiveScore}, rank=${reply.rank}, season=${reply.seasonId}")
        } catch (e: Exception) {
            // Leaderboard outages must not block player joins. Log + carry on.
            logger.warning("submitScore failed for ${player.name}: ${e.message}")
        }
    }

    /**
     * Match-end handler. Runs on a NATS dispatcher worker thread —
     * if you'd touch tick-thread-only state (worlds, scoreboards),
     * wrap with `Bukkit.getScheduler().runTask(plugin) { ... }`.
     * The leaderboard call below is pure gRPC, so the worker thread
     * is fine here.
     *
     * Submits a +1 to the winning team's players on the
     * `match-wins.<minigame>` board. Free-for-all matches with no
     * `winner_team_id` are skipped — nothing to attribute the win to.
     */
    private fun onMatchEnded(event: MatchEnded) {
        val winnerTeamId = event.winnerTeamId.takeIf { event.hasWinnerTeamId() } ?: return
        val board = "match-wins.unknown" // Minigame id not in MatchEnded; mark on the proto + change here.

        for (result in event.resultsList) {
            if (result.teamId != winnerTeamId) continue
            try {
                leaderboard.submitScore(
                    SubmitScoreRequest.newBuilder()
                        .setBoardId(board)
                        .setPlayerId(result.playerId)
                        .setScore(1)
                        .setMode(SubmitMode.SUBMIT_MODE_ACCUMULATE)
                        .setIdempotencyKey("match-ended-${event.matchId}-${result.playerId}")
                        .build(),
                )
            } catch (e: Exception) {
                logger.warning("score submit for match-end failed (player=${result.playerId}): ${e.message}")
            }
        }
    }
}
