package org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Bot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Api.McApiClient;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Whitelist.DiscordWhitelistListener;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Whitelist.PendingWhitelistModule;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;

public class DiscordBotRuntime {
    private final Loader plugin;
    private final PendingWhitelistModule whitelistModule;
    private final McApiClient mcApiClient;
    private final Supplier<DiscordBotSettings> settingsSupplier;
    private final List<Object> additionalListeners = new CopyOnWriteArrayList<>();

    private JDA jda;

    public DiscordBotRuntime(
            Loader plugin,
            PendingWhitelistModule whitelistModule,
            McApiClient mcApiClient,
            Supplier<DiscordBotSettings> settingsSupplier) {
        this.plugin = plugin;
        this.whitelistModule = whitelistModule;
        this.mcApiClient = mcApiClient;
        this.settingsSupplier = settingsSupplier;
    }

    public synchronized void start(DiscordBotSettings settings) {
        if (jda != null)
            return;
        String token = settings.getToken();
        if (token.isEmpty())
            return;
        try {

            JDABuilder builder = JDABuilder.createLight(token)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(
                            new DiscordWhitelistListener(plugin, whitelistModule, mcApiClient, settingsSupplier));

            for (Object listener : additionalListeners) {
                builder.addEventListeners(listener);
            }

            jda = builder.build();
            plugin.getLogger().info("[DiscordBot] Bot を起動しました");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[DiscordBot] Bot の起動に失敗しました: " + e.getMessage());
            jda = null;
        }
    }

    public synchronized void stop() {
        if (jda == null)
            return;
        try {
            jda.shutdownNow();
            if (!jda.awaitShutdown(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("[DiscordBot] Bot のシャットダウンがタイムアウトしました。");
            } else {
                plugin.getLogger().info("[DiscordBot] Bot を正常に停止しました");
            }
        } catch (InterruptedException e) {
            plugin.getLogger().log(Level.WARNING, "[DiscordBot] Bot 停止待機中に割り込みが発生しました: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[DiscordBot] Bot 停止中にエラー: " + e.getMessage());
        } finally {
            jda = null;
        }
    }

    public synchronized boolean isOnline() {
        return jda != null;
    }

    public synchronized JDA getJda() {
        return jda;
    }

    public void registerListener(Object listener) {
        if (listener == null || additionalListeners.contains(listener)) {
            return;
        }
        additionalListeners.add(listener);
        JDA currentJda = getJda();
        if (currentJda != null) {
            currentJda.addEventListener(listener);
        }
    }

    public void unregisterListener(Object listener) {
        if (listener == null) {
            return;
        }
        additionalListeners.remove(listener);
        JDA currentJda = getJda();
        if (currentJda != null) {
            currentJda.removeEventListener(listener);
        }
    }
}
