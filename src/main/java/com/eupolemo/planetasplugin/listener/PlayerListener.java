package com.eupolemo.planetasplugin.listener;

import com.eupolemo.planetasplugin.PlanetasPlugin;
import com.eupolemo.planetasplugin.planeta.Planeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Duration;

public final class PlayerListener implements Listener {

    private static final Title.Times TEMPOS =
            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500));

    private final PlanetasPlugin plugin;

    public PlayerListener(PlanetasPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("avisar-no-join", true)) {
            return;
        }
        anunciar(event.getPlayer());
    }

    @EventHandler
    public void onMudarDeMundo(PlayerChangedWorldEvent event) {
        anunciar(event.getPlayer());
    }

    private void anunciar(Player player) {
        Planeta planeta = plugin.getPlanetaManager().porMundo(player.getWorld());
        if (planeta == null) {
            return;
        }
        player.showTitle(Title.title(
                Component.text(planeta.nome(), planeta.cor()),
                Component.text(planeta.descricao(), NamedTextColor.GRAY),
                TEMPOS
        ));
    }
}
