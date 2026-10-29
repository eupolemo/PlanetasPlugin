package com.eupolemo.planetasplugin.render;

import com.eupolemo.planetasplugin.PlanetasPlugin;
import com.eupolemo.planetasplugin.planeta.Planeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Constroi corpos celestes em blocos: a casca de um elipsoide pintada pela textura, os aneis no
 * plano equatorial, enxames de asteroides e caudas de cometa.
 *
 * <p>Nada pesado acontece na thread principal do servidor. A geometria (dezenas de milhoes de
 * operacoes num render do sistema completo) e o historico de undo em disco rodam fora dela; a
 * escrita dos blocos volta para a thread principal, mas fatiada e so depois de os chunks terem
 * sido carregados de forma assincrona — ver {@link EscritorEmLote}.
 */
public final class EsferaRenderer {

    /** Valores lidos antes de sair da thread principal, para nao tocar no servidor de fora dela. */
    private record Ambiente(int alturaMinima, int alturaMaxima, double rotacao) {
    }

    /** Geometria pronta, mais o que ficou de fora por nao caber na altura do mundo. */
    private record Geometria(Deque<BlocoSalvo> alvos, int forasDeAlcance) {
    }

    /** Um render desta sessao: o que ele sobrescreveu e se isso ja foi para o disco. */
    private static final class Render {
        private final UUID mundo;
        private final List<BlocoSalvo> anteriores;
        private @Nullable EscritorEmLote escritor;
        /** true quando o escritor em curso e uma restauracao, nao uma construcao. */
        private boolean restaurando;
        private boolean persistido;

        private Render(UUID mundo, List<BlocoSalvo> anteriores) {
            this.mundo = mundo;
            this.anteriores = anteriores;
        }
    }

    private final PlanetasPlugin plugin;
    private final RegistroRenders registro;
    private final Map<String, Render> ativos = new HashMap<>();
    /** Chaves com alguma operacao em voo; impede dois comandos brigarem pelo mesmo render. */
    private final Set<String> ocupadas = new HashSet<>();

    public EsferaRenderer(PlanetasPlugin plugin, RegistroRenders registro) {
        this.plugin = plugin;
        this.registro = registro;
    }

    // -------------------------------------------------------------------------------- consulta

    /** @return true se existe render desta chave, nesta sessao ou salvo de uma anterior. */
    public boolean temEsfera(String chave) {
        String k = chave.toLowerCase(Locale.ROOT);
        return ativos.containsKey(k) || registro.existe(k);
    }

    public TreeSet<String> chavesSalvas() {
        TreeSet<String> chaves = registro.chaves();
        chaves.addAll(ativos.keySet());
        return chaves;
    }

    /**
     * Valida um trabalho antes de calcular qualquer coisa.
     *
     * @return null se estiver tudo bem, ou a razao da recusa
     */
    public @Nullable String validar(Trabalho trabalho, World mundo) {
        int raioMaximo = plugin.getConfig().getInt("render.raio-maximo", 64);

        if (trabalho.planeta().ehCampo()) {
            // Num enxame o raio e distancia de layout, nao tamanho de corpo: o Cinturao de Kuiper
            // chega a 2585 no sistema completo. O que precisa respeitar o teto e cada corpo.
            Campo campo = trabalho.planeta().campo();
            if (campo.corpoMax() > raioMaximo) {
                return trabalho.planeta().nome() + ": corpo-max e " + campo.corpoMax()
                        + ", acima do teto de " + raioMaximo + ".";
            }
            if (trabalho.raio() < 1) {
                return trabalho.planeta().nome() + ": raio de referencia deve ser >= 1.";
            }
            // Corpos que nao couberem na altura sao apenas descartados, com aviso no fim.
            return null;
        }

        if (trabalho.raio() < 3 || trabalho.raio() > raioMaximo) {
            return trabalho.planeta().nome() + ": raio deve estar entre 3 e " + raioMaximo
                    + " (veio " + trabalho.raio() + ").";
        }
        int alcance = (int) Math.ceil(trabalho.raio() * trabalho.planeta().escala().y()) + 1;
        if (trabalho.cy() - alcance < mundo.getMinHeight()
                || trabalho.cy() + alcance >= mundo.getMaxHeight()) {
            return trabalho.planeta().nome() + " nao cabe na altura do mundo nesse ponto.";
        }
        return null;
    }

    // ------------------------------------------------------------------------------ construir

    /**
     * Desfaz o que houver nesta chave e entao constroi todos os trabalhos como um render so.
     *
     * @param chave  id sob o qual o resultado fica registrado para o /planetas remover
     * @param rotulo nome mostrado ao jogador nas mensagens de progresso
     * @return false se a validacao falhou; nesse caso o motivo ja foi enviado ao remetente
     */
    public boolean renderizar(String chave, String rotulo, List<Trabalho> trabalhos,
                              World mundo, CommandSender remetente) {

        String k = chave.toLowerCase(Locale.ROOT);
        if (trabalhos.isEmpty()) {
            enviar(remetente, Component.text("Nada para construir.", NamedTextColor.RED));
            return false;
        }
        if (ocupadas.contains(k)) {
            enviar(remetente, Component.text("Ja ha uma operacao em andamento em '" + k
                    + "'. Espere ela terminar.", NamedTextColor.RED));
            return false;
        }
        for (Trabalho trabalho : trabalhos) {
            String erro = validar(trabalho, mundo);
            if (erro != null) {
                enviar(remetente, Component.text(erro, NamedTextColor.RED));
                return false;
            }
        }
        if (!RegistroRenders.chaveValida(k)) {
            enviar(remetente, Component.text("O id '" + k + "' nao serve como nome de arquivo, "
                    + "entao este render nao sobreviveria a um restart.", NamedTextColor.RED));
            return false;
        }

        // Se ja havia algo aqui, desfaz primeiro e so entao constroi.
        desfazer(k, remetente, restaurados -> {
            if (restaurados > 0) {
                enviar(remetente, Component.text("Render anterior desfeito: " + restaurados
                        + " blocos.", NamedTextColor.GRAY));
            }
            construir(k, rotulo, trabalhos, mundo, remetente);
        });
        return true;
    }

    private void construir(String chave, String rotulo, List<Trabalho> trabalhos,
                           World mundo, CommandSender remetente) {

        Ambiente ambiente = new Ambiente(mundo.getMinHeight(), mundo.getMaxHeight(),
                plugin.getConfig().getDouble("render.rotacao-graus", 0.0));

        Render render = new Render(mundo.getUID(), new ArrayList<>());
        ativos.put(chave, render);
        ocupadas.add(chave);

        enviar(remetente, Component.text("Calculando " + rotulo + " ("
                + trabalhos.size() + " corpo(s))...", NamedTextColor.GRAY));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Geometria geometria = calcular(trabalhos, ambiente);
            if (!plugin.isEnabled()) {
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> colocar(chave, rotulo, geometria, render, mundo, remetente));
        });
    }

    private void colocar(String chave, String rotulo, Geometria geometria, Render render,
                         World mundo, CommandSender remetente) {
        // Enquanto a geometria era calculada, alguem pode ter removido ou refeito este render.
        if (ativos.get(chave) != render) {
            ocupadas.remove(chave);
            return;
        }

        EscritorEmLote escritor = new EscritorEmLote(plugin, mundo, geometria.alvos(),
                true, render.anteriores, porTick(), chunksEmVoo(), rotulo, remetente,
                escritos -> {
                    render.escritor = null;
                    ocupadas.remove(chave);
                    persistir(chave, render);
                    enviar(remetente, Component.text(rotulo + " pronto: " + escritos
                            + " blocos.", NamedTextColor.GREEN));
                    if (geometria.forasDeAlcance() > 0) {
                        enviar(remetente, Component.text(geometria.forasDeAlcance()
                                + " blocos ficaram fora da altura do mundo e nao foram "
                                + "colocados.", NamedTextColor.YELLOW));
                    }
                });

        render.escritor = escritor;
        render.restaurando = false;
        enviar(remetente, Component.text("Construindo " + rotulo + ": " + escritor.total()
                + " blocos em " + escritor.chunks() + " chunks. Terreno novo precisa ser gerado, "
                + "entao isso leva um tempo.", NamedTextColor.GRAY));
        escritor.runTaskTimer(plugin, 1L, 1L);
    }

    /** Grava o historico de undo. Copia a lista: a escrita sai da thread principal. */
    private void persistir(String chave, Render render) {
        List<BlocoSalvo> copia = List.copyOf(render.anteriores);
        render.persistido = true;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> registro.salvar(chave, render.mundo, copia));
    }

    // -------------------------------------------------------------------------------- desfazer

    /**
     * Desfaz o render da chave, restaurando os blocos originais na ordem inversa.
     *
     * <p>A restauracao passa pelo mesmo escritor da construcao, e pelo mesmo motivo: os chunks do
     * modelo podem ter sido descarregados, e le-los de volta na thread principal travaria o
     * servidor exatamente como na construcao.
     */
    public void remover(String chave, CommandSender remetente, String rotulo) {
        String k = chave.toLowerCase(Locale.ROOT);
        if (ocupadas.contains(k)) {
            enviar(remetente, Component.text("Ja ha uma operacao em andamento em '" + k
                    + "'. Espere ela terminar.", NamedTextColor.RED));
            return;
        }
        desfazer(k, remetente, restaurados -> {
            if (restaurados == 0) {
                enviar(remetente, Component.text("Nada de " + rotulo + " para remover.",
                        NamedTextColor.GRAY));
            } else {
                enviar(remetente, Component.text(rotulo + " removido: " + restaurados
                        + " blocos restaurados.", NamedTextColor.GREEN));
            }
        });
    }

    /** Desfaz o que houver na chave e chama {@code depois} com quantos blocos foram restaurados. */
    private void desfazer(String chave, CommandSender remetente, IntConsumer depois) {
        Render emMemoria = ativos.get(chave);
        if (emMemoria != null) {
            if (emMemoria.escritor != null) {
                emMemoria.escritor.cancel();
                if (emMemoria.restaurando) {
                    // Uma restauracao interrompida deixa o historico no que sobrou dela.
                    List<BlocoSalvo> sobrando = emMemoria.escritor.restantes();
                    emMemoria.anteriores.clear();
                    emMemoria.anteriores.addAll(sobrando);
                }
                emMemoria.escritor = null;
            }
            restaurar(chave, emMemoria, remetente, depois);
            return;
        }

        if (!registro.existe(chave)) {
            depois.accept(0);
            return;
        }

        // Render de uma sessao anterior: ler o arquivo fora da thread principal.
        ocupadas.add(chave);
        enviar(remetente, Component.text("Lendo o render salvo de '" + chave + "'...",
                NamedTextColor.GRAY));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            RegistroRenders.Salvo salvo = registro.carregar(chave);
            if (!plugin.isEnabled()) {
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                ocupadas.remove(chave);
                if (salvo == null) {
                    registro.apagar(chave);
                    enviar(remetente, Component.text("O render salvo de '" + chave + "' esta "
                            + "ilegivel; o registro foi descartado e os blocos ficaram no mundo.",
                            NamedTextColor.RED));
                    depois.accept(0);
                    return;
                }
                Render render = new Render(salvo.mundo(), new ArrayList<>(salvo.blocos()));
                render.persistido = true;
                ativos.put(chave, render);
                restaurar(chave, render, remetente, depois);
            });
        });
    }

    private void restaurar(String chave, Render render, CommandSender remetente, IntConsumer depois) {
        World mundo = plugin.getServer().getWorld(render.mundo);
        if (mundo == null) {
            plugin.getLogger().warning("O mundo do render '" + chave + "' nao esta carregado; "
                    + "os blocos ficam como estao.");
            enviar(remetente, Component.text("O mundo desse render nao esta carregado.",
                    NamedTextColor.RED));
            depois.accept(0);
            return;
        }
        if (render.anteriores.isEmpty()) {
            concluirRestauracao(chave, render);
            depois.accept(0);
            return;
        }

        // Ordem inversa: se a mesma posicao foi sobrescrita duas vezes (um anel por cima da casca),
        // desfazer de tras para frente termina no estado original. O agrupamento por chunk preserva
        // a ordem relativa dentro de cada chunk, que e onde posicoes repetidas sempre caem.
        List<BlocoSalvo> invertido = new ArrayList<>(render.anteriores);
        Collections.reverse(invertido);
        int total = invertido.size();

        render.persistido = false;
        ocupadas.add(chave);

        EscritorEmLote escritor = new EscritorEmLote(plugin, mundo, invertido,
                false, null, porTick(), chunksEmVoo(), "Desfazendo " + chave, remetente,
                escritos -> {
                    render.escritor = null;
                    ocupadas.remove(chave);
                    render.anteriores.clear();
                    concluirRestauracao(chave, render);
                    depois.accept(total);
                });

        render.escritor = escritor;
        render.restaurando = true;
        escritor.runTaskTimer(plugin, 1L, 1L);
    }

    private void concluirRestauracao(String chave, Render render) {
        ativos.remove(chave);
        render.persistido = true;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> registro.apagar(chave));
    }

    /**
     * Cancela tarefas pendentes e grava o que ainda estiver sem salvar. Chamado no desligamento,
     * quando nao ha mais ticks nem tarefas assincronas: a escrita aqui e sincrona de proposito.
     */
    public void desligar() {
        for (Map.Entry<String, Render> entrada : ativos.entrySet()) {
            Render render = entrada.getValue();
            if (render.escritor != null) {
                render.escritor.cancel();
                if (render.restaurando) {
                    // O que nao foi restaurado ainda esta no mundo: o historico e o que sobrou.
                    List<BlocoSalvo> sobrando = render.escritor.restantes();
                    render.anteriores.clear();
                    render.anteriores.addAll(sobrando);
                }
                render.escritor = null;
                render.persistido = false;
            }
            if (!render.persistido) {
                registro.salvar(entrada.getKey(), render.mundo, render.anteriores);
            }
        }
        ativos.clear();
        ocupadas.clear();
    }

    private int porTick() {
        return Math.max(1, plugin.getConfig().getInt("render.blocos-por-tick", 1500));
    }

    private int chunksEmVoo() {
        return Math.max(1, plugin.getConfig().getInt("render.chunks-em-voo", 8));
    }

    // ------------------------------------------------------------------ geometria (thread async)

    private Geometria calcular(List<Trabalho> trabalhos, Ambiente ambiente) {
        Deque<BlocoSalvo> alvos = new ArrayDeque<>();
        int foras = 0;
        for (Trabalho trabalho : trabalhos) {
            double inclinacao = Math.toRadians(trabalho.planeta().inclinacao());
            double sen = Math.sin(inclinacao);
            double cos = Math.cos(inclinacao);

            if (trabalho.planeta().ehCampo()) {
                foras += campo(trabalho, ambiente, sen, cos, alvos);
            } else {
                casca(trabalho, ambiente, sen, cos, alvos);
                if (trabalho.planeta().temAneis()) {
                    foras += aneis(trabalho, ambiente, sen, cos, alvos);
                }
            }
            if (trabalho.planeta().temCauda()) {
                foras += cauda(trabalho, ambiente, alvos);
            }
        }
        return new Geometria(alvos, foras);
    }

    /**
     * Casca do elipsoide: os blocos de dentro que fazem fronteira com o lado de fora.
     *
     * <p>E estanque por construcao: qualquer caminho de fora ate o centro, andando pelas 6 faces,
     * precisa entrar no solido, e o primeiro bloco solido do caminho tem um vizinho de fora — ou
     * seja, esta na casca. Vale para qualquer achatamento, o que um teste por faixa de distancia
     * nao garantia.
     */
    private void casca(Trabalho trabalho, Ambiente ambiente, double sen, double cos,
                       Deque<BlocoSalvo> alvos) {
        Escala escala = trabalho.planeta().escala();
        int raio = trabalho.raio();

        double ex = raio * escala.x();
        double ey = raio * escala.y();
        double ez = raio * escala.z();

        int lx = (int) Math.ceil(ex) + 1;
        int ly = (int) Math.ceil(ey) + 1;
        int lz = (int) Math.ceil(ez) + 1;

        for (int dy = -ly; dy <= ly; dy++) {
            for (int dx = -lx; dx <= lx; dx++) {
                for (int dz = -lz; dz <= lz; dz++) {
                    if (!dentro(dx, dy, dz, ex, ey, ez)) {
                        continue;
                    }
                    if (dentro(dx + 1, dy, dz, ex, ey, ez) && dentro(dx - 1, dy, dz, ex, ey, ez)
                            && dentro(dx, dy + 1, dz, ex, ey, ez) && dentro(dx, dy - 1, dz, ex, ey, ez)
                            && dentro(dx, dy, dz + 1, ex, ey, ez) && dentro(dx, dy, dz - 1, ex, ey, ez)) {
                        continue;
                    }

                    // Normal do elipsoide: o gradiente de (x/ex)^2+(y/ey)^2+(z/ez)^2. Numa esfera
                    // isso volta a ser a propria direcao do ponto.
                    double gx = dx / (ex * ex);
                    double gy = dy / (ey * ey);
                    double gz = dz / (ez * ez);
                    double norma = Math.sqrt(gx * gx + gy * gy + gz * gz);
                    if (norma == 0.0) {
                        continue;
                    }
                    gx /= norma;
                    gy /= norma;
                    gz /= norma;

                    double ry = gy * cos - gz * sen;
                    double rz = gy * sen + gz * cos;
                    double lat = Math.toDegrees(Math.asin(Math.clamp(ry, -1.0, 1.0)));
                    double lon = Math.toDegrees(Math.atan2(rz, gx)) + ambiente.rotacao();

                    int y = trabalho.cy() + dy;
                    if (y < ambiente.alturaMinima() || y >= ambiente.alturaMaxima()) {
                        continue;
                    }
                    alvos.add(new BlocoSalvo(trabalho.cx() + dx, y, trabalho.cz() + dz,
                            trabalho.textura().amostrar(lat, lon)));
                }
            }
        }
    }

    private static boolean dentro(double dx, double dy, double dz, double ex, double ey, double ez) {
        double x = dx / ex;
        double y = dy / ey;
        double z = dz / ez;
        return x * x + y * y + z * z <= 1.0;
    }

    /**
     * Bandas de anel no plano equatorial.
     *
     * <p>O plano e {@code dy*cos - dz*sen = 0}. Em vez de testar cada bloco do volume, resolvemos
     * uma coordenada em funcao da outra: sai exatamente um bloco por coluna, sem os furos que um
     * plano inclinado deixaria numa grade cubica. Qual coordenada resolver depende da inclinacao,
     * senao a conta estoura perto de 90 graus (o caso de Urano).
     */
    private int aneis(Trabalho trabalho, Ambiente ambiente, double sen, double cos,
                      Deque<BlocoSalvo> alvos) {
        Planeta planeta = trabalho.planeta();
        int raio = trabalho.raio();
        double maiorExterno = planeta.aneis().stream().mapToDouble(Anel::externo).max().orElse(0.0);
        int alcance = (int) Math.ceil(maiorExterno * raio) + 1;
        boolean resolverY = Math.abs(cos) >= Math.abs(sen);
        int foraDeAlcance = 0;

        for (int dx = -alcance; dx <= alcance; dx++) {
            for (int outro = -alcance; outro <= alcance; outro++) {
                int dy;
                int dz;
                if (resolverY) {
                    dz = outro;
                    dy = (int) Math.round(dz * sen / cos);
                } else {
                    dy = outro;
                    dz = (int) Math.round(dy * cos / sen);
                }

                double rz = dy * sen + dz * cos;
                double distancia = Math.sqrt((double) dx * dx + rz * rz);

                BlockData bloco = null;
                for (Anel anel : planeta.aneis()) {
                    if (distancia >= anel.interno() * raio && distancia <= anel.externo() * raio) {
                        bloco = anel.bloco();
                        break;
                    }
                }
                if (bloco == null) {
                    continue;
                }

                int y = trabalho.cy() + dy;
                if (y < ambiente.alturaMinima() || y >= ambiente.alturaMaxima()) {
                    foraDeAlcance++;
                    continue;
                }
                alvos.add(new BlocoSalvo(trabalho.cx() + dx, y, trabalho.cz() + dz, bloco));
            }
        }
        return foraDeAlcance;
    }

    /**
     * Espalha corpos irregulares: cinturao (ANEL) ou nuvem (ESFERA).
     *
     * <p>A mesma semente sempre reproduz o mesmo arranjo, entao da para ajustar o resto da config
     * sem que o cinturao mude embaixo de voce.
     */
    private int campo(Trabalho trabalho, Ambiente ambiente, double sen, double cos,
                      Deque<BlocoSalvo> alvos) {
        Campo campo = trabalho.planeta().campo();
        int raio = trabalho.raio();
        Random rng = new Random(campo.semente());
        int foraDeAlcance = 0;

        for (int i = 0; i < campo.quantidade(); i++) {
            double distancia = (campo.interno()
                    + rng.nextDouble() * (campo.externo() - campo.interno())) * raio;

            double px;
            double py;
            double pz;
            if (campo.formato() == Campo.Formato.ESFERA) {
                // Direcao uniforme na esfera: sortear o cosseno da latitude, nao a latitude, senao
                // os corpos se acumulam nos polos.
                double u = rng.nextDouble() * 2 - 1;
                double fi = rng.nextDouble() * 2 * Math.PI;
                double s = Math.sqrt(1 - u * u);
                px = distancia * s * Math.cos(fi);
                py = distancia * u;
                pz = distancia * s * Math.sin(fi);
            } else {
                double angulo = rng.nextDouble() * 2 * Math.PI;
                px = distancia * Math.cos(angulo);
                py = (rng.nextDouble() * 2 - 1) * campo.espessura() * raio;
                pz = distancia * Math.sin(angulo);
            }

            int bx = trabalho.cx() + (int) Math.round(px);
            int by = trabalho.cy() + (int) Math.round(py * cos + pz * sen);
            int bz = trabalho.cz() + (int) Math.round(-py * sen + pz * cos);

            double corpo = campo.corpoMin() + rng.nextDouble() * (campo.corpoMax() - campo.corpoMin());
            double[] fase = {rng.nextDouble() * 6.283, rng.nextDouble() * 6.283, rng.nextDouble() * 6.283};
            double giro = rng.nextDouble() * 360.0;

            foraDeAlcance += asteroide(trabalho.textura(), ambiente, bx, by, bz, corpo,
                    campo.irregularidade(), fase, giro, alvos);
        }
        return foraDeAlcance;
    }

    /** Um corpo do enxame. Mesmo teste de fronteira da casca, com raio dependente da direcao. */
    private int asteroide(Textura textura, Ambiente ambiente, int bx, int by, int bz, double corpo,
                          double irregularidade, double[] fase, double giro, Deque<BlocoSalvo> alvos) {

        int limite = (int) Math.ceil(corpo * (1.0 + irregularidade)) + 1;
        int foraDeAlcance = 0;

        for (int dy = -limite; dy <= limite; dy++) {
            for (int dx = -limite; dx <= limite; dx++) {
                for (int dz = -limite; dz <= limite; dz++) {
                    if (!dentroCorpo(dx, dy, dz, corpo, irregularidade, fase)) {
                        continue;
                    }
                    if (dentroCorpo(dx + 1, dy, dz, corpo, irregularidade, fase)
                            && dentroCorpo(dx - 1, dy, dz, corpo, irregularidade, fase)
                            && dentroCorpo(dx, dy + 1, dz, corpo, irregularidade, fase)
                            && dentroCorpo(dx, dy - 1, dz, corpo, irregularidade, fase)
                            && dentroCorpo(dx, dy, dz + 1, corpo, irregularidade, fase)
                            && dentroCorpo(dx, dy, dz - 1, corpo, irregularidade, fase)) {
                        continue;
                    }

                    double d = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
                    BlockData bloco;
                    if (d == 0.0) {
                        bloco = textura.amostrar(0, giro);
                    } else {
                        double lat = Math.toDegrees(Math.asin(Math.clamp(dy / d, -1.0, 1.0)));
                        double lon = Math.toDegrees(Math.atan2(dz / d, dx / d)) + giro;
                        bloco = textura.amostrar(lat, lon);
                    }

                    int y = by + dy;
                    if (y < ambiente.alturaMinima() || y >= ambiente.alturaMaxima()) {
                        foraDeAlcance++;
                        continue;
                    }
                    alvos.add(new BlocoSalvo(bx + dx, y, bz + dz, bloco));
                }
            }
        }
        return foraDeAlcance;
    }

    private static boolean dentroCorpo(double dx, double dy, double dz, double corpo,
                                       double irregularidade, double[] fase) {
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d == 0.0) {
            return true;
        }
        double ruido = (Math.sin(fase[0] + 2.3 * dx / corpo)
                + Math.sin(fase[1] + 2.7 * dy / corpo)
                + Math.sin(fase[2] + 3.1 * dz / corpo)) / 3.0;
        return d <= corpo * (1.0 + irregularidade * ruido);
    }

    /**
     * Cone esparso apontando para +X. Num render do sistema completo esse e o sentido oposto ao
     * Sol, que e para onde a cauda de um cometa realmente aponta — ela e empurrada pelo vento
     * solar, e nao arrastada pelo movimento.
     */
    private int cauda(Trabalho trabalho, Ambiente ambiente, Deque<BlocoSalvo> alvos) {
        Cauda cauda = trabalho.planeta().cauda();
        Random rng = new Random(trabalho.planeta().id().hashCode());
        int inicio = trabalho.raio() + 1;
        int foraDeAlcance = 0;

        for (int dx = inicio; dx <= cauda.comprimento(); dx++) {
            double raio = cauda.abertura() * dx;
            // Rarefaz com a distancia: a cauda se dispersa conforme se afasta do nucleo.
            double densidade = cauda.densidade() * (1.0 - (double) dx / cauda.comprimento());
            if (densidade <= 0) {
                continue;
            }
            int limite = (int) Math.ceil(raio);
            for (int dy = -limite; dy <= limite; dy++) {
                for (int dz = -limite; dz <= limite; dz++) {
                    if (dy * dy + dz * dz > raio * raio || rng.nextDouble() > densidade) {
                        continue;
                    }
                    int y = trabalho.cy() + dy;
                    if (y < ambiente.alturaMinima() || y >= ambiente.alturaMaxima()) {
                        foraDeAlcance++;
                        continue;
                    }
                    alvos.add(new BlocoSalvo(trabalho.cx() + dx, y, trabalho.cz() + dz, cauda.bloco()));
                }
            }
        }
        return foraDeAlcance;
    }

    private static void enviar(CommandSender remetente, Component mensagem) {
        if (remetente instanceof Player jogador && !jogador.isOnline()) {
            return;
        }
        remetente.sendMessage(mensagem);
    }
}
