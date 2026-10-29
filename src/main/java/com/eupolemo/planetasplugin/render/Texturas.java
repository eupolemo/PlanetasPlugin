package com.eupolemo.planetasplugin.render;

import com.eupolemo.planetasplugin.PlanetasPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Descobre a textura de um planeta em {@code plugins/PlanetasPlugin/texturas/}.
 * Uma imagem equiretangular {@code <id>.png} tem prioridade sobre o mapa em texto {@code <id>.txt}.
 */
public final class Texturas {

    /** Texturas embutidas no jar, extraidas no primeiro boot. */
    public static final String[] EMBUTIDAS = {
            "sol", "mercurio", "venus", "terra", "lua", "marte", "asteroides",
            "jupiter", "io", "europa", "ganimedes", "calisto",
            "saturno", "tita", "encelado", "urano", "netuno", "triton",
            "ceres", "plutao", "caronte", "haumea", "makemake", "eris",
            "sedna", "quaoar", "cometa",
            "betelgeuse", "sirius", "proxima", "buraco-negro"
    };

    private Texturas() {
    }

    public static Path pasta(PlanetasPlugin plugin) {
        return plugin.getDataFolder().toPath().resolve("texturas");
    }

    /**
     * @return a textura do planeta
     * @throws IOException se nenhum arquivo existir ou o arquivo encontrado for invalido
     */
    public static Textura carregar(PlanetasPlugin plugin, String id) throws IOException {
        Path pasta = pasta(plugin);

        Path png = pasta.resolve(id + ".png");
        if (Files.isRegularFile(png)) {
            return TexturaImagem.carregar(png);
        }

        Path txt = pasta.resolve(id + ".txt");
        if (Files.isRegularFile(txt)) {
            return TexturaAscii.carregar(txt);
        }

        throw new IOException("nenhuma textura para '" + id + "'. Esperado " + png + " ou " + txt);
    }
}
