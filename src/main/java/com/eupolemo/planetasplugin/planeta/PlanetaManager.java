package com.eupolemo.planetasplugin.planeta;

import com.eupolemo.planetasplugin.PlanetasPlugin;
import com.eupolemo.planetasplugin.render.Anel;
import com.eupolemo.planetasplugin.render.Campo;
import com.eupolemo.planetasplugin.render.Cauda;
import com.eupolemo.planetasplugin.render.Escala;
import com.eupolemo.planetasplugin.render.Orbita;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Le os corpos do config.yml e mantem em memoria. */
public final class PlanetaManager {

    private final PlanetasPlugin plugin;

    /** id -> corpo, na ordem do arquivo. */
    private Map<String, Planeta> porId = Collections.emptyMap();
    /** nome do mundo -> corpo; so entram os que declaram 'mundo'. */
    private Map<String, Planeta> porMundo = Collections.emptyMap();

    public PlanetaManager(PlanetasPlugin plugin) {
        this.plugin = plugin;
    }

    public void carregar() {
        Map<String, Planeta> ids = new LinkedHashMap<>();
        Map<String, Planeta> mundos = new LinkedHashMap<>();

        int raioPadrao = plugin.getConfig().getInt("render.raio-padrao", 24);
        double inclinacaoPadrao = plugin.getConfig().getDouble("render.inclinacao-graus", 0.0);

        ConfigurationSection raiz = plugin.getConfig().getConfigurationSection("planetas");
        if (raiz == null) {
            plugin.getLogger().warning("Secao 'planetas' ausente no config.yml. Nada carregado.");
        } else {
            for (String id : raiz.getKeys(false)) {
                ConfigurationSection sec = raiz.getConfigurationSection(id);
                if (sec == null) {
                    plugin.getLogger().warning("Corpo '" + id + "' ignorado: entrada nao e um mapa.");
                    continue;
                }

                String mundo = sec.getString("mundo");
                if (mundo != null && mundo.isBlank()) {
                    mundo = null;
                }
                String chave = id.toLowerCase(Locale.ROOT);

                Planeta planeta = new Planeta(
                        chave,
                        sec.getString("nome", id),
                        mundo,
                        sec.getString("descricao", ""),
                        Planeta.parseCor(sec.getString("cor")),
                        sec.getInt("raio", raioPadrao),
                        sec.getDouble("inclinacao-graus", inclinacaoPadrao),
                        lerEscala(id, sec.getConfigurationSection("escala")),
                        lerAneis(id, sec.getConfigurationSection("anel")),
                        lerCampo(id, sec.getConfigurationSection("campo")),
                        lerCauda(id, sec.getConfigurationSection("cauda")),
                        sec.getString("textura", chave),
                        lerOrbita(sec.getConfigurationSection("orbita"))
                );

                ids.put(planeta.id(), planeta);
                if (mundo != null) {
                    Planeta anterior = mundos.put(mundo, planeta);
                    if (anterior != null) {
                        plugin.getLogger().warning("Mundo '" + mundo + "' mapeado por '"
                                + anterior.id() + "' e '" + planeta.id() + "'. Vale o ultimo.");
                    }
                }
            }
        }

        this.porId = Collections.unmodifiableMap(ids);
        this.porMundo = Collections.unmodifiableMap(mundos);
        avisarOrbitasOrfas();
    }

    /** Uma lua cujo corpo pai nao existe some do render do sistema sem explicacao. */
    private void avisarOrbitasOrfas() {
        for (Planeta p : porId.values()) {
            Orbita orbita = p.orbita();
            if (orbita != null && orbita.de() != null && !porId.containsKey(orbita.de())) {
                plugin.getLogger().warning("'" + p.id() + "' orbita '" + orbita.de()
                        + "', que nao existe no config. Ficara de fora do /planetas sistema.");
            }
        }
    }

    private Escala lerEscala(String id, @Nullable ConfigurationSection secao) {
        if (secao == null) {
            return Escala.ESFERA;
        }
        try {
            return new Escala(secao.getDouble("x", 1.0), secao.getDouble("y", 1.0),
                    secao.getDouble("z", 1.0));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Escala de '" + id + "' ignorada: " + e.getMessage());
            return Escala.ESFERA;
        }
    }

    private @Nullable Orbita lerOrbita(@Nullable ConfigurationSection secao) {
        if (secao == null) {
            return null;
        }
        String de = secao.getString("de");
        return new Orbita(
                secao.getDouble("ua", 0.0),
                de == null || de.isBlank() ? null : de.toLowerCase(Locale.ROOT),
                secao.getDouble("interna", 0.0),
                secao.getDouble("externa", 0.0),
                secao.getDouble("desvio", 0.0)
        );
    }

    private @Nullable Campo lerCampo(String id, @Nullable ConfigurationSection secao) {
        if (secao == null) {
            return null;
        }
        String formato = secao.getString("formato", "anel").toUpperCase(Locale.ROOT);
        Campo.Formato tipo;
        try {
            tipo = Campo.Formato.valueOf(formato);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Formato de campo desconhecido em '" + id + "': "
                    + formato + ". Usando ANEL.");
            tipo = Campo.Formato.ANEL;
        }
        try {
            return new Campo(
                    secao.getInt("quantidade", 60),
                    secao.getDouble("corpo-min", 2.0),
                    secao.getDouble("corpo-max", 6.0),
                    secao.getDouble("interno", 1.0),
                    secao.getDouble("externo", 2.0),
                    secao.getDouble("espessura", 0.2),
                    secao.getDouble("irregularidade", 0.35),
                    secao.getLong("semente", 1L),
                    tipo
            );
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Campo de '" + id + "' ignorado: " + e.getMessage());
            return null;
        }
    }

    private @Nullable Cauda lerCauda(String id, @Nullable ConfigurationSection secao) {
        if (secao == null) {
            return null;
        }
        BlockData bloco = bloco(id, secao.getString("bloco"), "cauda");
        if (bloco == null) {
            return null;
        }
        try {
            return new Cauda(secao.getInt("comprimento", 80), secao.getDouble("abertura", 0.22),
                    secao.getDouble("densidade", 0.05), bloco);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Cauda de '" + id + "' ignorada: " + e.getMessage());
            return null;
        }
    }

    private List<Anel> lerAneis(String id, @Nullable ConfigurationSection secao) {
        if (secao == null) {
            return List.of();
        }
        List<Anel> aneis = new ArrayList<>();
        for (Map<?, ?> banda : secao.getMapList("bandas")) {
            Object interno = banda.get("interno");
            Object externo = banda.get("externo");
            Object nomeBloco = banda.get("bloco");
            if (!(interno instanceof Number ni) || !(externo instanceof Number ne) || nomeBloco == null) {
                plugin.getLogger().warning("Anel de '" + id + "' ignorado: precisa de "
                        + "'interno', 'externo' e 'bloco'.");
                continue;
            }
            BlockData bloco = bloco(id, nomeBloco.toString(), "anel");
            if (bloco == null) {
                continue;
            }
            try {
                aneis.add(new Anel(ni.doubleValue(), ne.doubleValue(), bloco));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Anel de '" + id + "' ignorado: " + e.getMessage());
            }
        }
        return aneis;
    }

    private @Nullable BlockData bloco(String id, @Nullable String nome, String onde) {
        if (nome == null || nome.isBlank()) {
            plugin.getLogger().warning("Faltou 'bloco' no " + onde + " de '" + id + "'.");
            return null;
        }
        Material material = Material.matchMaterial(nome);
        if (material == null || !material.isBlock()) {
            plugin.getLogger().warning("Bloco desconhecido no " + onde + " de '" + id + "': " + nome);
            return null;
        }
        return material.createBlockData();
    }

    public Collection<Planeta> todos() {
        return porId.values();
    }

    public Collection<String> ids() {
        return porId.keySet();
    }

    public @Nullable Planeta porId(String id) {
        return porId.get(id.toLowerCase(Locale.ROOT));
    }

    public @Nullable Planeta porMundo(World mundo) {
        return porMundo.get(mundo.getName());
    }
}
