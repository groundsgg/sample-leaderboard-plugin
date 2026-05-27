package gg.grounds.samples

import gg.grounds.grpc.leaderboard.LeaderboardServiceGrpc
import gg.grounds.grpc.leaderboard.SubmitMode
import gg.grounds.grpc.leaderboard.SubmitScoreRequest
import gg.grounds.sdk.GroundsServices
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin

/**
 * Minimal v2.2-Service-Architecture demo.
 *
 * On every PlayerJoinEvent, calls `LeaderboardService.submitScore` to
 * bump the player's `"logins"` board in ACCUMULATE mode. The plugin
 * never sees a service URL or credential — `GroundsServices.channel`
 * reads `LEADERBOARD_SERVICE_URL` from the pod's env (injected by
 * forge from this plugin's `grounds.yaml`), and the SDK attaches the
 * projected ServiceAccount-JWT to every gRPC call.
 *
 * Companion docs:
 * - Service Architecture v2.2 (Confluence 216662019)
 * - groundsgg/library-grpc-contracts/sdk/README.md
 */
class SampleLeaderboardPlugin : JavaPlugin(), Listener {

    private val leaderboard by lazy {
        LeaderboardServiceGrpc.newBlockingStub(GroundsServices.channel("leaderboard"))
    }

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        logger.info("SampleLeaderboardPlugin enabled — leaderboard service: ${System.getenv("LEADERBOARD_SERVICE_URL") ?: "(unset)"}")
    }

    override fun onDisable() {
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
                    .build()
            )
            logger.info("${player.name}: logins=${reply.effectiveScore}, rank=${reply.rank}, season=${reply.seasonId}")
        } catch (e: Exception) {
            // Leaderboard outages must not block player joins. Log + carry on.
            logger.warning("submitScore failed for ${player.name}: ${e.message}")
        }
    }
}
