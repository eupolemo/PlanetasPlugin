package com.eupolemo.planetasplugin.render;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Textura vinda de uma imagem equiretangular (proporcao 2:1), por exemplo um Blue Marble da NASA.
 * Cada pixel amostrado vira o bloco de cor mais proxima da paleta.
 */
public final class TexturaImagem implements Textura {

    /** Blocos candidatos e sua cor aproximada no mapa. */
    private static final Map<Material, int[]> PALETA = new LinkedHashMap<>();

    static {
        PALETA.put(Material.BLUE_CONCRETE, new int[]{44, 46, 143});
        PALETA.put(Material.LIGHT_BLUE_CONCRETE, new int[]{36, 137, 199});
        PALETA.put(Material.CYAN_CONCRETE, new int[]{21, 119, 136});
        PALETA.put(Material.GREEN_CONCRETE, new int[]{73, 91, 36});
        PALETA.put(Material.LIME_CONCRETE, new int[]{94, 168, 24});
        PALETA.put(Material.MOSS_BLOCK, new int[]{89, 109, 45});
        PALETA.put(Material.SAND, new int[]{219, 207, 163});
        PALETA.put(Material.YELLOW_TERRACOTTA, new int[]{186, 133, 35});
        PALETA.put(Material.BROWN_CONCRETE, new int[]{96, 60, 32});
        PALETA.put(Material.GRAY_CONCRETE, new int[]{55, 58, 62});
        PALETA.put(Material.WHITE_CONCRETE, new int[]{207, 213, 214});
        PALETA.put(Material.SNOW_BLOCK, new int[]{249, 254, 254});
    }

    private final int largura;
    private final int altura;
    /** Bloco ja resolvido por pixel: evita refazer a busca de cor durante o render. */
    private final BlockData[] celulas;
    private final String origem;

    private TexturaImagem(int largura, int altura, BlockData[] celulas, String origem) {
        this.largura = largura;
        this.altura = altura;
        this.celulas = celulas;
        this.origem = origem;
    }

    public static TexturaImagem carregar(Path arquivo) throws IOException {
        BufferedImage imagem;
        try (var entrada = Files.newInputStream(arquivo)) {
            imagem = ImageIO.read(entrada);
        }
        if (imagem == null) {
            throw new IOException("formato de imagem nao suportado: " + arquivo);
        }

        int largura = imagem.getWidth();
        int altura = imagem.getHeight();
        BlockData[] celulas = new BlockData[largura * altura];
        Map<Integer, BlockData> cache = new HashMap<>();

        for (int y = 0; y < altura; y++) {
            for (int x = 0; x < largura; x++) {
                int rgb = imagem.getRGB(x, y) & 0xFFFFFF;
                celulas[y * largura + x] = cache.computeIfAbsent(rgb, TexturaImagem::maisProximo);
            }
        }

        return new TexturaImagem(largura, altura, celulas, arquivo.getFileName().toString());
    }

    private static BlockData maisProximo(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        Material melhor = Material.BLUE_CONCRETE;
        long menorDistancia = Long.MAX_VALUE;
        for (Map.Entry<Material, int[]> entrada : PALETA.entrySet()) {
            int[] cor = entrada.getValue();
            long dr = r - cor[0];
            long dg = g - cor[1];
            long db = b - cor[2];
            // Pesos aproximam a sensibilidade do olho; verde pesa mais que azul.
            long distancia = 2 * dr * dr + 4 * dg * dg + 3 * db * db;
            if (distancia < menorDistancia) {
                menorDistancia = distancia;
                melhor = entrada.getKey();
            }
        }
        return melhor.createBlockData();
    }

    @Override
    public BlockData amostrar(double lat, double lon) {
        int coluna = Math.floorMod((int) Math.floor((lon + 180.0) / 360.0 * largura), largura);
        int linha = (int) Math.floor((90.0 - lat) / 180.0 * altura);
        linha = Math.clamp(linha, 0, altura - 1);
        return celulas[linha * largura + coluna];
    }

    @Override
    public String origem() {
        return origem + " (" + largura + "x" + altura + ")";
    }
}
