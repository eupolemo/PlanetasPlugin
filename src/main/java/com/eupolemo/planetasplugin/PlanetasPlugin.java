package com.eupolemo.planetasplugin;

import com.eupolemo.planetasplugin.command.PlanetasCommand;
import com.eupolemo.planetasplugin.listener.PlayerListener;
import com.eupolemo.planetasplugin.planeta.Planeta;
import com.eupolemo.planetasplugin.planeta.PlanetaManager;
import com.eupolemo.planetasplugin.render.EsferaRenderer;
import com.eupolemo.planetasplugin.render.RegistroRenders;
import com.eupolemo.planetasplugin.render.Textura;
import com.eupolemo.planetasplugin.render.Texturas;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public final class PlanetasPlugin extends JavaPlugin {

    /**
     * Versao do formato de config.yml e das texturas embutidas. Subir este numero faz o plugin
     * arquivar a config antiga e reescrever as texturas do jar no proximo boot.
     */
    private static final int VERSAO_CONFIG = 4;

    private PlanetaManager planetaManager;
    private EsferaRenderer esferaRenderer;
    /** Ler e converter uma imagem grande custa caro; guarda a textura ja resolvida por planeta. */
    private final Map<String, Textura> cacheTexturas = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        boolean migrou = migrarConfig();
        for (String id : Texturas.EMBUTIDAS) {
            // So sobrescreve edicoes do usuario quando o formato das texturas mudou.
            saveResource("texturas/" + id + ".txt", migrou);
        }

        this.planetaManager = new PlanetaManager(this);
        this.planetaManager.carregar();
        this.esferaRenderer = new EsferaRenderer(this,
                new RegistroRenders(getDataFolder().toPath().resolve("renders"), getLogger()));

        int salvos = esferaRenderer.chavesSalvas().size();
        if (salvos > 0) {
            getLogger().info(salvos + " render(s) de sessoes anteriores continuam desfaziveis: "
                    + String.join(", ", esferaRenderer.chavesSalvas()));
        }

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        PluginCommand comando = getCommand("planetas");
        if (comando == null) {
            getLogger().severe("Comando 'planetas' nao declarado no plugin.yml. Desabilitando.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        PlanetasCommand executor = new PlanetasCommand(this);
        comando.setExecutor(executor);
        comando.setTabCompleter(executor);

        getLogger().info("PlanetasPlugin ativo com " + planetaManager.todos().size() + " corpo(s).");
    }

    @Override
    public void onDisable() {
        if (esferaRenderer != null) {
            esferaRenderer.desligar();
        }
        getLogger().info("PlanetasPlugin desligado.");
    }

    /**
     * Arquiva um config.yml de formato antigo e gera um novo a partir do jar. Sem isso, quem ja
     * rodou uma versao anterior ficaria com a config velha (o Bukkit nunca sobrescreve) e as
     * texturas antigas, que o parser atual rejeita.
     *
     * @return true se houve migracao
     */
    private boolean migrarConfig() {
        int versao = getConfig().getInt("versao-config", 1);
        if (versao >= VERSAO_CONFIG) {
            return false;
        }

        File atual = new File(getDataFolder(), "config.yml");
        File backup = new File(getDataFolder(), "config-v" + versao + ".yml.bak");
        try {
            Files.move(atual.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            getLogger().severe("Nao consegui arquivar o config.yml antigo: " + e.getMessage()
                    + ". Apague plugins/PlanetasPlugin/ e reinicie.");
            return false;
        }

        saveDefaultConfig();
        reloadConfig();
        getLogger().warning("config.yml da versao " + versao + " foi arquivado como "
                + backup.getName() + " e substituido pelo formato " + VERSAO_CONFIG
                + ". As texturas embutidas tambem foram reescritas.");
        return true;
    }

    public PlanetaManager getPlanetaManager() {
        return planetaManager;
    }

    public EsferaRenderer getEsferaRenderer() {
        return esferaRenderer;
    }

    /**
     * Corpos podem compartilhar textura — Fobos, Deimos e Vesta usam a mesma rocha —, entao o
     * cache e indexado pelo nome do arquivo, nao pelo id do corpo.
     *
     * @return a textura do corpo, carregando do disco na primeira chamada
     * @throws IOException se nao houver arquivo de textura ou ele for invalido
     */
    public Textura getTextura(Planeta corpo) throws IOException {
        Textura emCache = cacheTexturas.get(corpo.textura());
        if (emCache != null) {
            return emCache;
        }
        Textura textura = Texturas.carregar(this, corpo.textura());
        cacheTexturas.put(corpo.textura(), textura);
        return textura;
    }

    /** Recarrega config.yml do disco e reconstroi planetas e texturas. */
    public void recarregar() {
        reloadConfig();
        cacheTexturas.clear();
        planetaManager.carregar();
    }
}
