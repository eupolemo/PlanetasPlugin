package com.eupolemo.planetasplugin.render;

import com.eupolemo.planetasplugin.planeta.Planeta;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decide onde cada corpo fica no render do sistema completo.
 *
 * <p>Distancias reais nao cabem: Netuno esta 78 vezes mais longe do Sol do que Mercurio, e a
 * Nuvem de Oort, 100 mil vezes. O layout comprime com {@code distancia = unidade * UA^compressao}.
 * Com compressao 1.0 a escala e linear e o modelo fica impraticavel; abaixo disso os corpos
 * externos se aproximam, preservando a ordem e a nocao de "muito mais longe" sem exigir uma
 * caminhada de horas.
 *
 * <p>As luas nao entram nessa conta — a Lua estaria a 0,0026 UA da Terra, invisivel em qualquer
 * escala que mostre o sistema inteiro. Elas sao enfileiradas ao lado do corpo que orbitam.
 */
public final class SistemaSolar {

    /** O plano pronto, mais o que o operador precisa saber antes de mandar construir. */
    public record Plano(List<Trabalho> trabalhos, List<String> avisos, int extensao) {
    }

    /** Raio minimo que o renderizador aceita para uma casca. */
    public static final int RAIO_MINIMO = 3;

    private final double unidade;
    private final double compressao;
    private final double folgaLuas;
    private final double densidadeFaixa;
    private final double espessuraFaixa;
    private final double escala;
    private final int raioMaximo;
    /** >= 0 enfileira os corpos com esse vao fixo; negativo usa as distancias orbitais reais. */
    private final int espacamento;

    /**
     * @param escala      multiplica distancias E tamanhos. Encolher so as distancias faria os
     *                    corpos se sobreporem, porque os raios ficariam iguais.
     * @param raioMaximo  teto de raio da config, aplicado depois da escala
     * @param espacamento se >= 0, ignora as distancias orbitais e enfileira os corpos com esse
     *                    vao fixo entre superficies. A ordem astronomica se mantem; o que se
     *                    perde e a nocao de distancia — e o que se ganha e ver todos os corpos
     *                    em tamanho cheio sem atravessar quilometros de vazio.
     */
    public SistemaSolar(double unidade, double compressao, double folgaLuas,
                        double densidadeFaixa, double espessuraFaixa,
                        double escala, int raioMaximo, int espacamento) {
        this.unidade = unidade;
        this.compressao = compressao;
        this.folgaLuas = folgaLuas;
        this.densidadeFaixa = densidadeFaixa;
        this.espessuraFaixa = espessuraFaixa;
        this.escala = escala;
        this.raioMaximo = raioMaximo;
        this.espacamento = espacamento;
    }

    public boolean enfileirado() {
        return espacamento >= 0;
    }

    /** @return o corpo redimensionado pela escala deste layout */
    public Planeta escalar(Planeta corpo) {
        return corpo.escalado(escala, RAIO_MINIMO, raioMaximo);
    }

    /** @return a distancia ao Sol, em blocos, de um corpo a {@code ua} unidades astronomicas */
    public int distancia(double ua) {
        if (ua <= 0) {
            return 0;
        }
        return (int) Math.round(unidade * escala * Math.pow(ua, compressao));
    }

    /**
     * @param corpos   todos os corpos configurados
     * @param texturas resolvedor de textura por corpo; corpos sem textura ficam de fora
     * @param origem   onde fica o Sol
     */
    public Plano planejar(Collection<Planeta> corpos, ResolvedorTextura texturas, Location origem) {
        List<Trabalho> trabalhos = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        Map<String, Trabalho> porId = new HashMap<>();

        int ox = origem.getBlockX();
        int oy = origem.getBlockY();
        int oz = origem.getBlockZ();

        // Primeira passada: o que orbita o Sol, na ordem de distancia.
        List<Planeta> principais = corpos.stream()
                .filter(Planeta::noSistema)
                .filter(p -> p.orbita().de() == null)
                .sorted((a, b) -> Double.compare(distanciaDe(a), distanciaDe(b)))
                .toList();

        int extensao = 0;
        // No modo enfileirado, x avanca corpo a corpo; no modo orbital, cada um vai para a sua
        // distancia calculada.
        int cursor = ox;
        int alcanceAnterior = 0;
        boolean primeiro = true;

        for (Planeta original : principais) {
            Textura textura = texturas.de(original, avisos);
            if (textura == null) {
                continue;
            }
            Planeta corpo = escalar(original);

            Trabalho trabalho;
            if (enfileirado()) {
                // Faixas viram um anel compacto no proprio lugar da fila, do tamanho que teriam
                // num /planetas render isolado — nao ha mais "distancia ao Sol" para orbitar.
                int alcanceAtual = alcanceDe(corpo, corpo.raio());
                if (!primeiro) {
                    cursor += alcanceAnterior + espacamento + alcanceAtual;
                }
                trabalho = new Trabalho(corpo, cursor, oy, oz, corpo.raio(), textura);
                alcanceAnterior = alcanceAtual;
                primeiro = false;
            } else if (corpo.orbita().ehFaixa()) {
                trabalho = faixa(corpo, textura, ox, oy, oz);
            } else {
                int d = distancia(corpo.orbita().ua());
                int desvio = (int) Math.round(corpo.orbita().desvio() * escala);
                trabalho = new Trabalho(corpo, ox + d, oy, oz + desvio, corpo.raio(), textura);
            }
            trabalhos.add(trabalho);
            porId.put(corpo.id(), trabalho);
            extensao = Math.max(extensao, Math.abs(trabalho.cx() - ox) + alcance(trabalho));
        }

        // Segunda passada: luas, enfileiradas ao lado do corpo que orbitam.
        Map<String, Integer> deslocamento = new HashMap<>();
        int folga = Math.max(2, (int) Math.round(folgaLuas * escala));
        for (Planeta original : corpos) {
            if (!original.noSistema() || original.orbita().de() == null) {
                continue;
            }
            Trabalho pai = porId.get(original.orbita().de());
            if (pai == null) {
                continue;
            }
            Textura textura = texturas.de(original, avisos);
            if (textura == null) {
                continue;
            }
            Planeta corpo = escalar(original);

            int acumulado = deslocamento.getOrDefault(pai.planeta().id(), alcance(pai) + folga);
            int z = pai.cz() + acumulado + corpo.raio();
            deslocamento.put(pai.planeta().id(), acumulado + 2 * corpo.raio() + folga);
            trabalhos.add(new Trabalho(corpo, pai.cx(), pai.cy(), z, corpo.raio(), textura));
        }

        if (!enfileirado()) {
            avisos.addAll(conferirFolgas(trabalhos, ox));
        }
        return new Plano(trabalhos, avisos, extensao);
    }

    /**
     * Um cinturao vira um anel em torno do Sol. Os limites do campo sao multiplos do raio do
     * corpo, entao o raio passa a ser a borda externa e os limites viram fracoes dela.
     */
    private Trabalho faixa(Planeta corpo, Textura textura, int ox, int oy, int oz) {
        int dentro = distancia(corpo.orbita().interna());
        int fora = Math.max(dentro + 1, distancia(corpo.orbita().externa()));

        double circunferencia = 2 * Math.PI * (dentro + fora) / 2.0;
        int quantidade = Math.max(8, (int) Math.round(circunferencia / 100.0 * densidadeFaixa));

        Campo ajustado = corpo.campo().reposicionado((double) dentro / fora, 1.0, quantidade,
                espessuraFaixa * escala, fora);
        return new Trabalho(corpo.com(ajustado, fora), ox, oy, oz, fora, textura);
    }

    /** Quanto o corpo ocupa a partir do proprio centro, contando anel, enxame e cauda. */
    private static int alcance(Trabalho trabalho) {
        return alcanceDe(trabalho.planeta(), trabalho.raio());
    }

    private static int alcanceDe(Planeta p, int raio) {
        double maior = raio;
        for (Anel anel : p.aneis()) {
            maior = Math.max(maior, anel.externo() * raio);
        }
        if (p.ehCampo()) {
            maior = Math.max(maior, p.campo().externo() * raio + p.campo().corpoMax());
        }
        if (p.temCauda()) {
            // A cauda so aponta para +X, mas contar simetrico e mais simples e so custa espaco.
            maior = Math.max(maior, p.cauda().comprimento());
        }
        return (int) Math.ceil(maior);
    }

    /** Corpos sobrepostos arruinam o modelo em silencio; melhor avisar antes de construir. */
    private List<String> conferirFolgas(List<Trabalho> trabalhos, int ox) {
        List<String> avisos = new ArrayList<>();
        List<Trabalho> linha = trabalhos.stream()
                .filter(t -> !t.planeta().ehCampo())
                .filter(t -> t.planeta().orbita().de() == null)
                .sorted((a, b) -> Integer.compare(a.cx(), b.cx()))
                .toList();

        for (int i = 1; i < linha.size(); i++) {
            Trabalho anterior = linha.get(i - 1);
            Trabalho atual = linha.get(i);
            int dx = atual.cx() - anterior.cx();
            int dz = atual.cz() - anterior.cz();
            int folga = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz))
                    - alcance(anterior) - alcance(atual);
            if (folga < 0) {
                avisos.add(anterior.planeta().nome() + " e " + atual.planeta().nome()
                        + " se sobrepoem em " + (-folga) + " blocos. Aumente 'sistema.unidade'.");
            }
        }
        return avisos;
    }

    private double distanciaDe(Planeta corpo) {
        Orbita o = corpo.orbita();
        return o.ehFaixa() ? (o.interna() + o.externa()) / 2.0 : o.ua();
    }

    /** Resolve a textura de um corpo, registrando o motivo quando nao consegue. */
    @FunctionalInterface
    public interface ResolvedorTextura {
        Textura de(Planeta corpo, List<String> avisos);
    }
}
