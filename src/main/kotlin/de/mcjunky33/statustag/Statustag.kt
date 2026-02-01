package de.mcjunky33.statustag

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.command.argument.ColorArgumentType
import net.minecraft.scoreboard.ServerScoreboard
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import net.minecraft.text.TextCodecs
import java.util.concurrent.CompletableFuture

object Statustag : ModInitializer {
    private val gson = Gson()
    val statusList: ArrayList<Status> = ArrayList()
    private val playerStatusMap: MutableMap<String, String> = mutableMapOf()
    private var fileLoaded = false
    private val logger: Logger = LoggerFactory.getLogger("Statustag")
    private val configFile get() = File(FabricLoader.getInstance().configDir.toFile(), "statustag.json")

    data class Status(val name: String, val text: MutableText)

    override fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->

            // ---- /status command ----
            dispatcher.register(
                CommandManager.literal("status")
                    .then(
                        CommandManager.argument("id", StringArgumentType.word())
                            .suggests(StatusTabProvider())
                            .executes { context ->
                                val source = context.source
                                val player = source.player ?: run {
                                    source.sendFeedback({
                                        Text.literal("You must be a player to use this command").formatted(Formatting.RED)
                                    }, false)
                                    return@executes 0
                                }
                                val statusId = context.getArgument("id", String::class.java)
                                val status = statusList.firstOrNull { it.name == statusId }
                                if (status == null) {
                                    source.sendFeedback({
                                        Text.literal("Invalid status").formatted(Formatting.RED)
                                    }, false)
                                    return@executes 0
                                }
                                setPlayerStatus(player.name.string, statusId, source.server)
                                source.sendFeedback({
                                    Text.literal("Your status has been set to ").append(status.text.copy())
                                }, false)
                                1
                            }
                            .then(
                                CommandManager.argument("player", StringArgumentType.word())
                                    .requires { source ->
                                        val player = source.player
                                        if (player != null) {
                                            val entry = net.minecraft.server.PlayerConfigEntry(player.gameProfile)

                                            source.server.playerManager.isOperator(entry)
                                        } else {
                                            true
                                        }
                                    }
                                    .executes { context ->
                                        val source = context.source
                                        val statusId = context.getArgument("id", String::class.java)
                                        val targetName = context.getArgument("player", String::class.java)
                                        val status = statusList.firstOrNull { it.name == statusId }
                                        if (status == null) {
                                            source.sendFeedback({
                                                Text.literal("Invalid status").formatted(Formatting.RED)
                                            }, false)
                                            return@executes 0
                                        }
                                        setPlayerStatus(targetName, statusId, source.server)
                                        source.sendFeedback({
                                            Text.literal("Set status of ").append(Text.literal(targetName))
                                                .append(" to ").append(status.text.copy())
                                        }, false)
                                        1
                                    }
                            )
                    )
                    .then(
                        CommandManager.literal("reset")
                            .executes { context ->
                                val source = context.source
                                val player = source.player ?: run {
                                    source.sendFeedback({
                                        Text.literal("You must be a player to use this command").formatted(Formatting.RED)
                                    }, false)
                                    return@executes 0
                                }
                                removePlayerStatus(player.name.string, source.server)
                                source.sendFeedback({ Text.literal("Your status has been reset") }, false)
                                1
                            }
                            .then(
                                CommandManager.argument("player", StringArgumentType.word())
                                    .requires { source ->
                                        val player = source.player
                                        if (player != null) {
                                            val entry = net.minecraft.server.PlayerConfigEntry(player.gameProfile)

                                            source.server.playerManager.isOperator(entry)
                                        } else {
                                            true
                                        }
                                    }
                                    .executes { context ->
                                        val source = context.source
                                        val targetName = context.getArgument("player", String::class.java)
                                        removePlayerStatus(targetName, source.server)
                                        source.sendFeedback({
                                            Text.literal("Reset status of ").append(Text.literal(targetName))
                                        }, false)
                                        1
                                    }
                            )
                    )
            )

            // ---- /statusadd ----
            dispatcher.register(
                CommandManager.literal("statusadd")
                    .requires { source ->
                        val player = source.player
                        if (player != null) {
                            val entry = net.minecraft.server.PlayerConfigEntry(player.gameProfile)

                            source.server.playerManager.isOperator(entry)
                        } else {
                            true
                        }
                    }
                    .then(
                        CommandManager.argument("id", StringArgumentType.word())
                            .then(
                                CommandManager.argument("name", StringArgumentType.word())
                                    .then(
                                        CommandManager.argument("color", ColorArgumentType.color())
                                            .executes {
                                                val source = it.source
                                                val id = it.getArgument("id", String::class.java)
                                                val name = it.getArgument("name", String::class.java)
                                                val color = it.getArgument("color", Formatting::class.java)

                                                val text = Text.literal("[").formatted(Formatting.WHITE)
                                                    .append(Text.literal(name).formatted(color))
                                                    .append(Text.literal("]").formatted(Formatting.WHITE))

                                                addStatus(id, text, source.server)

                                                source.sendFeedback({
                                                    Text.literal("Added status ").append(text.copy())
                                                }, false)
                                                1
                                            }
                                    )
                            )
                    )
            )

            // ---- /statusremove ----
            dispatcher.register(
                CommandManager.literal("statusremove").requires { source ->
                    val player = source.player
                    if (player != null) {
                        val entry = net.minecraft.server.PlayerConfigEntry(player.gameProfile)

                        source.server.playerManager.isOperator(entry)
                    } else {
                        true
                    }
                }
                    .then(
                        CommandManager.argument("id", StringArgumentType.word())
                            .suggests(StatusSuggestionProvider())
                            .executes {
                                val source = it.source
                                val id = it.getArgument("id", String::class.java)
                                if (statusList.none { it.name == id }) {
                                    source.sendFeedback({
                                        Text.literal("Invalid status").formatted(Formatting.RED)
                                    }, false)
                                    return@executes 0
                                }
                                removeStatus(id, source.server)
                                source.sendFeedback({
                                    Text.literal("Removed status ").append(Text.literal(id))
                                }, false)
                                1
                            }
                    )
            )
        }
    }

    private fun setPlayerStatus(playerName: String, statusId: String, server: MinecraftServer) {
        val status = statusList.firstOrNull { it.name == statusId } ?: return
        val scoreboard = server.scoreboard

        scoreboard.getScoreHolderTeam(playerName)?.let {
            scoreboard.removeScoreHolderFromTeam(playerName, it)
        }

        val team = scoreboard.getTeam(statusId) ?: scoreboard.addTeam(statusId).apply {
            displayName = status.text.copy()
            prefix = status.text.copy().append(" ")
        }

        scoreboard.addScoreHolderToTeam(playerName, team)
        playerStatusMap[playerName] = statusId
        writeStatus()
    }

    private fun removePlayerStatus(playerName: String, server: MinecraftServer) {
        val scoreboard = server.scoreboard
        scoreboard.getScoreHolderTeam(playerName)?.let {
            scoreboard.removeScoreHolderFromTeam(playerName, it)
        }
        playerStatusMap.remove(playerName)
        writeStatus()
    }

    private fun addStatus(id: String, text: MutableText, server: MinecraftServer) {
        statusList.removeIf { it.name == id }
        statusList.add(Status(id.lowercase().replace(" ", "_"), text))
        reloadStatusTeams(server)
        writeStatus()
    }

    private fun removeStatus(id: String, server: MinecraftServer) {
        statusList.removeIf { it.name == id }
        server.scoreboard.getTeam(id)?.let { server.scoreboard.removeTeam(it) }
        playerStatusMap.entries.removeIf { it.value == id }
        reloadStatusTeams(server)
        writeStatus()
    }

    // ---------------------
    // FIXED: JSON Speicherung
    // ---------------------
    private fun writeStatus() {
        val json = JsonObject()
        val statusesArray = JsonArray()

        statusList.forEach { status ->
            val obj = JsonObject()
            obj.addProperty("name", status.name)
            obj.add("text", TextCodecs.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, status.text).result().get())// <-- FIX: speichert Farbe!
            statusesArray.add(obj)
        }
        json.add("statuses", statusesArray)

        val playersObj = JsonObject()
        playerStatusMap.forEach { (player, statusId) ->
            playersObj.addProperty(player, statusId)
        }
        json.add("players", playersObj)

        configFile.writeText(gson.toJson(json))
    }

    // ---------------------
    // FIXED: JSON Laden
    // ---------------------
    private fun loadStatus(server: MinecraftServer) {
        if (!configFile.exists()) configFile.writeText("""{"statuses":[],"players":{}}""")

        try {
            val rawJson = configFile.readText()
            val jsonElement = gson.fromJson(rawJson, com.google.gson.JsonElement::class.java)
            val json = if (jsonElement.isJsonObject) jsonElement.asJsonObject else JsonObject().apply {
                add("statuses", JsonArray())
                add("players", JsonObject())
            }

            json.getAsJsonArray("statuses")?.forEach {
                val obj = it.asJsonObject
                val text = TextCodecs.CODEC.decode(com.mojang.serialization.JsonOps.INSTANCE, obj.get("text"))
                    .result()
                    .get()
                    .first as MutableText
                statusList.add(Status(obj["name"].asString, text))
            }

            json.getAsJsonObject("players")?.entrySet()?.forEach { (player, statusElem) ->
                val statusId = statusElem.asString
                playerStatusMap[player] = statusId
                val status = statusList.firstOrNull { it.name == statusId } ?: return@forEach
                val team = server.scoreboard.getTeam(statusId) ?: server.scoreboard.addTeam(statusId).apply {
                    displayName = status.text.copy()
                    prefix = status.text.copy().append(" ")
                }
                server.scoreboard.addScoreHolderToTeam(player, team)
            }

        } catch (e: Exception) {
            logger.error("(Statustag) Failed to load statustag.json", e)
        }
    }

    fun reloadStatusTeams(server: MinecraftServer) {
        if (!fileLoaded) {
            fileLoaded = true
            loadStatus(server)
        }
        val scoreboard = server.scoreboard
        statusList.forEach { status ->
            val team = scoreboard.getTeam(status.name)
                ?: scoreboard.addTeam(status.name).apply { displayName = status.text.copy() }
            team.prefix = status.text.copy().append(" ")
        }
    }

    class StatusSuggestionProvider : SuggestionProvider<ServerCommandSource> {
        override fun getSuggestions(
            context: CommandContext<ServerCommandSource>,
            builder: SuggestionsBuilder
        ): CompletableFuture<Suggestions> {
            statusList.forEach { builder.suggest(it.name) }
            return builder.buildFuture()
        }
    }

    class StatusTabProvider : SuggestionProvider<ServerCommandSource> {
        override fun getSuggestions(
            context: CommandContext<ServerCommandSource>,
            builder: SuggestionsBuilder
        ): CompletableFuture<Suggestions> {
            builder.suggest("<id>")
            builder.suggest("reset")
            return builder.buildFuture()
        }
    }
}
