package com.eupolemo.planetasplugin.render;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Textura vinda de um mapa equiretangular em texto, com a legenda no proprio arquivo.
 *
 * <p>Formato:
 * <pre>
 * ; comentario
 * 72 36                      &lt;- largura e altura, primeira linha de dados
 * = . BLUE_CONCRETE          &lt;- legenda: simbolo -&gt; bloco
 * = # GREEN_CONCRETE
 * ..##..                     &lt;- 'altura' linhas de 'largura' simbolos
 * </pre>
 *
 * <p>As linhas da grade vao de 90N ate 90S, e cada coluna de 180W ate 180E.
 */
public final class TexturaAscii implements Textura {

    private final int largura;
    private final int altura;
    private final BlockData[] celulas;
    private final String origem;

    private TexturaAscii(int largura, int altura, BlockData[] celulas, String origem) {
        this.largura = largura;
        this.altura = altura;
        this.celulas = celulas;
        this.origem = origem;
    }

    public static TexturaAscii carregar(Path arquivo) throws IOException {
        String nome = arquivo.getFileName().toString();

        List<String> uteis = new ArrayList<>();
        for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
            String limpa = linha.strip();
            if (!limpa.isEmpty() && !limpa.startsWith(";")) {
                uteis.add(limpa);
            }
        }
        if (uteis.isEmpty()) {
            throw new IOException(nome + ": arquivo vazio");
        }

        String[] dimensoes = uteis.get(0).split("\\s+");
        if (dimensoes.length != 2) {
            throw new IOException(nome + ": cabecalho invalido, esperado '<largura> <altura>'");
        }
        int largura;
        int altura;
        try {
            largura = Integer.parseInt(dimensoes[0]);
            altura = Integer.parseInt(dimensoes[1]);
        } catch (NumberFormatException e) {
            throw new IOException(nome + ": dimensoes nao numericas", e);
        }
        if (largura < 2 || altura < 2) {
            throw new IOException(nome + ": dimensoes muito pequenas");
        }

        Map<Character, BlockData> legenda = new HashMap<>();
        int i = 1;
        while (i < uteis.size() && uteis.get(i).startsWith("=")) {
            lerLegenda(nome, uteis.get(i), legenda);
            i++;
        }
        if (legenda.isEmpty()) {
            throw new IOException(nome + ": nenhuma legenda '= <simbolo> <BLOCO>' encontrada");
        }

        List<String> grade = uteis.subList(i, uteis.size());
        if (grade.size() != altura) {
            throw new IOException(nome + ": esperado " + altura + " linhas de grade, encontrado " + grade.size());
        }

        BlockData[] celulas = new BlockData[largura * altura];
        for (int y = 0; y < altura; y++) {
            String linha = grade.get(y);
            if (linha.length() != largura) {
                throw new IOException(nome + ": linha " + y + " tem " + linha.length()
                        + " simbolos, esperado " + largura);
            }
            for (int x = 0; x < largura; x++) {
                char simbolo = linha.charAt(x);
                BlockData bloco = legenda.get(simbolo);
                if (bloco == null) {
                    throw new IOException(nome + ": simbolo '" + simbolo + "' na linha " + y
                            + " nao esta na legenda");
                }
                celulas[y * largura + x] = bloco;
            }
        }

        return new TexturaAscii(largura, altura, celulas, nome);
    }

    /** Le uma linha {@code = <simbolo> <MATERIAL>}; texto apos ';' e comentario. */
    private static void lerLegenda(String nome, String linha, Map<Character, BlockData> destino)
            throws IOException {
        String corpo = linha.substring(1);
        int comentario = corpo.indexOf(';');
        if (comentario >= 0) {
            corpo = corpo.substring(0, comentario);
        }
        String[] partes = corpo.strip().split("\\s+");
        if (partes.length != 2 || partes[0].length() != 1) {
            throw new IOException(nome + ": legenda invalida '" + linha
                    + "', esperado '= <simbolo> <BLOCO>'");
        }
        Material material = Material.matchMaterial(partes[1]);
        if (material == null || !material.isBlock()) {
            throw new IOException(nome + ": bloco desconhecido na legenda: " + partes[1]);
        }
        destino.put(partes[0].charAt(0), material.createBlockData());
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
