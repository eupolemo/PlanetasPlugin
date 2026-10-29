package com.eupolemo.planetasplugin.render;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Escreve muitos blocos sem travar o servidor, agrupando por chunk e carregando cada chunk fora
 * da thread principal antes de tocar nele.
 *
 * <p>Fatiar por numero de blocos nao basta. Um modelo do sistema solar se espalha por ~2.947
 * blocos e ~700 chunks, quase todos terreno que ainda nao existe. Escrever direto faz o
 * {@code getBlockData} disparar geracao de chunk <em>sincrona</em>: a thread do servidor fica
 * parada em {@code ServerChunkCache.syncLoad} e o watchdog do Paper mata o servidor com
 * "The server has not responded for 10 seconds".
 *
 * <p>Aqui os chunks sao pedidos com {@code getChunkAtAsync}, que gera terreno numa thread propria.
 * So quando o chunk esta pronto os blocos dele entram na fila de escrita da thread principal.
 */
public final class EscritorEmLote extends BukkitRunnable {

    private final Plugin plugin;
    private final World mundo;
    /** Blocos ainda nao despachados, por chunk. */
    private final Map<Long, List<BlocoSalvo>> porChunk = new ConcurrentHashMap<>();
    /** Ordem em que os chunks serao pedidos. */
    private final Deque<Long> pendentes;
    /** Blocos de chunks ja carregados, esperando a vez de serem escritos. */
    private final Queue<BlocoSalvo> prontos = new ConcurrentLinkedQueue<>();
    private final AtomicInteger emVoo = new AtomicInteger();

    private final int porTick;
    private final int maxEmVoo;
    /** Se true, guarda o estado anterior de cada bloco (construcao). Se false, e uma restauracao. */
    private final boolean registrarAnterior;
    private final @Nullable List<BlocoSalvo> anteriores;
    private final @Nullable CommandSender remetente;
    private final String rotulo;
    private final Consumer<Integer> aoTerminar;

    private final int total;
    private int escritos;
    private int ticks;

    /**
     * @param acoes             na ordem em que devem ser aplicadas; a ordem relativa dentro de
     *                          cada chunk e preservada, que e o que importa quando duas acoes
     *                          caem na mesma posicao
     * @param registrarAnterior true na construcao, para montar o historico de undo
     * @param anteriores        lista onde o historico e acumulado, ou de onde ele e consumido
     */
    public EscritorEmLote(Plugin plugin, World mundo, Collection<BlocoSalvo> acoes,
                          boolean registrarAnterior, @Nullable List<BlocoSalvo> anteriores,
                          int porTick, int maxEmVoo, String rotulo,
                          @Nullable CommandSender remetente, Consumer<Integer> aoTerminar) {
        this.plugin = plugin;
        this.mundo = mundo;
        this.registrarAnterior = registrarAnterior;
        this.anteriores = anteriores;
        this.porTick = porTick;
        this.maxEmVoo = maxEmVoo;
        this.rotulo = rotulo;
        this.remetente = remetente;
        this.aoTerminar = aoTerminar;
        this.total = acoes.size();

        LinkedHashSet<Long> ordem = new LinkedHashSet<>();
        for (BlocoSalvo acao : acoes) {
            long chave = acao.chunk();
            ordem.add(chave);
            porChunk.computeIfAbsent(chave, k -> new ArrayList<>()).add(acao);
        }
        this.pendentes = new ArrayDeque<>(ordem);
    }

    public int chunks() {
        return porChunk.size();
    }

    public int total() {
        return total;
    }

    @Override
    public void run() {
        pedirChunks();

        int feitos = 0;
        while (feitos < porTick) {
            BlocoSalvo acao = prontos.poll();
            if (acao == null) {
                break;
            }
            Block bloco = mundo.getBlockAt(acao.x(), acao.y(), acao.z());
            BlockData atual = bloco.getBlockData();
            if (registrarAnterior) {
                if (atual.matches(acao.dado())) {
                    escritos++;
                    continue;
                }
                if (anteriores != null) {
                    anteriores.add(new BlocoSalvo(acao.x(), acao.y(), acao.z(), atual));
                }
            }
            // applyPhysics = false: sem isso a areia cai e a neve se atualiza sozinha.
            bloco.setBlockData(acao.dado(), false);
            escritos++;
            feitos++;
        }

        avisarProgresso();

        if (pendentes.isEmpty() && emVoo.get() == 0 && prontos.isEmpty()) {
            cancel();
            aoTerminar.accept(escritos);
        }
    }

    private void pedirChunks() {
        while (emVoo.get() < maxEmVoo && !pendentes.isEmpty()) {
            long chave = pendentes.poll();
            List<BlocoSalvo> lote = porChunk.get(chave);
            if (lote == null) {
                continue;
            }
            emVoo.incrementAndGet();
            mundo.getChunkAtAsync(BlocoSalvo.chunkX(chave), BlocoSalvo.chunkZ(chave), true)
                    .whenComplete((chunk, erro) -> {
                        if (erro == null) {
                            porChunk.remove(chave);
                            prontos.addAll(lote);
                        } else {
                            plugin.getLogger().warning("Falhei ao carregar o chunk "
                                    + BlocoSalvo.chunkX(chave) + "," + BlocoSalvo.chunkZ(chave)
                                    + ": " + erro.getMessage() + ". Esses blocos ficam de fora.");
                            porChunk.remove(chave);
                        }
                        emVoo.decrementAndGet();
                    });
        }
    }

    /** A geracao de terreno domina o tempo aqui; sem isso o jogador acha que travou. */
    private void avisarProgresso() {
        ticks++;
        if (remetente == null || ticks % 100 != 0 || total == 0) {
            return;
        }
        int pct = (int) (100L * escritos / total);
        enviar(Component.text(rotulo + ": " + pct + "% (" + escritos + "/" + total
                + " blocos, " + porChunk.size() + " chunks restantes)", NamedTextColor.GRAY));
    }

    /**
     * @return o que ainda nao foi escrito, para que um desligamento no meio da operacao nao perca
     *         o historico de undo
     */
    public List<BlocoSalvo> restantes() {
        List<BlocoSalvo> sobrando = new ArrayList<>(prontos);
        for (List<BlocoSalvo> lote : porChunk.values()) {
            sobrando.addAll(lote);
        }
        return sobrando;
    }

    private void enviar(Component mensagem) {
        if (remetente == null || (remetente instanceof Player jogador && !jogador.isOnline())) {
            return;
        }
        remetente.sendMessage(mensagem);
    }
}
