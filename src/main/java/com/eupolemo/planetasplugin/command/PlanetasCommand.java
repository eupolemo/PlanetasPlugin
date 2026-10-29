package com.eupolemo.planetasplugin.command;

import com.eupolemo.planetasplugin.PlanetasPlugin;
import com.eupolemo.planetasplugin.planeta.Planeta;
import com.eupolemo.planetasplugin.render.SistemaSolar;
import com.eupolemo.planetasplugin.render.Textura;
import com.eupolemo.planetasplugin.render.Trabalho;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlanetasCommand implements CommandExecutor, TabCompleter {

    /** Chave sob a qual o render do sistema completo fica registrado para o remover. */
    private static final String CHAVE_SISTEMA = "sistema";

    private final PlanetasPlugin plugin;

    public PlanetasCommand(PlanetasPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("planetas.use")) {
            sender.sendMessage(Component.text("Sem permissao.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            listar(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!sender.hasPermission("planetas.reload")) {
                    sender.sendMessage(Component.text("Sem permissao para recarregar.", NamedTextColor.RED));
                    return true;
                }
                plugin.recarregar();
                sender.sendMessage(Component.text("Config recarregada: "
                        + plugin.getPlanetaManager().todos().size() + " corpo(s).", NamedTextColor.GREEN));
            }
            case "info" -> {
                Planeta corpo = exigirCorpo(sender, label, "info", args);
                if (corpo != null) {
                    info(sender, corpo);
                }
            }
            case "sistema" -> sistema(sender, label, args);
            case "render" -> render(sender, label, args);
            case "remover" -> remover(sender, label, args);
            default -> ajuda(sender, label);
        }
        return true;
    }

    // ------------------------------------------------------------------------ sistema completo

    private void sistema(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("planetas.sistema")) {
            sender.sendMessage(Component.text("Sem permissao para construir o sistema.", NamedTextColor.RED));
            return;
        }
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage(Component.text("Esse comando precisa de um jogador: o Sol nasce "
                    + "acima de quem executa.", NamedTextColor.RED));
            return;
        }

        double escala = 1.0;
        if (args.length >= 2) {
            try {
                escala = Double.parseDouble(args[1].replace(',', '.'));
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Escala invalida: " + args[1]
                        + ". Use algo como 0.3, 0.5 ou 1.0.", NamedTextColor.RED));
                return;
            }
            if (escala < 0.05 || escala > 2.0) {
                sender.sendMessage(Component.text("Escala deve estar entre 0.05 e 2.0.",
                        NamedTextColor.RED));
                return;
            }
        }

        // Negativo = usar as distancias orbitais. Qualquer valor >= 0 enfileira os corpos.
        int espacamento = plugin.getConfig().getInt("sistema.espacamento", -1);
        if (args.length >= 3) {
            try {
                espacamento = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Espacamento invalido: " + args[2]
                        + ". Use um numero de blocos, por exemplo 10.", NamedTextColor.RED));
                return;
            }
            if (espacamento < 0 || espacamento > 500) {
                sender.sendMessage(Component.text("Espacamento deve estar entre 0 e 500 blocos.",
                        NamedTextColor.RED));
                return;
            }
        }

        SistemaSolar layout = new SistemaSolar(
                plugin.getConfig().getDouble("sistema.unidade", 500.0),
                plugin.getConfig().getDouble("sistema.compressao", 0.42),
                plugin.getConfig().getDouble("sistema.folga-luas", 6.0),
                plugin.getConfig().getDouble("sistema.densidade-faixa", 2.0),
                plugin.getConfig().getDouble("sistema.espessura-faixa", 14.0),
                escala,
                plugin.getConfig().getInt("render.raio-maximo", 64),
                espacamento);

        var corpos = plugin.getPlanetaManager().todos();
        // O maior raio precisa ser o ja escalado: e ele que decide a altura da origem.
        int maiorRaio = corpos.stream()
                .filter(Planeta::noSistema)
                .map(layout::escalar)
                .mapToInt(p -> (int) Math.ceil(p.raio() * p.escala().y()))
                .max().orElse(0);
        if (maiorRaio == 0) {
            sender.sendMessage(Component.text("Nenhum corpo tem 'orbita' no config.yml.", NamedTextColor.RED));
            return;
        }

        int alturaExtra = plugin.getConfig().getInt("render.altura-extra", 6);
        Location origem = jogador.getLocation().getBlock().getLocation()
                .add(0, maiorRaio + alturaExtra, 0);

        // Sem isto o erro sairia como "o Sol nao cabe na altura do mundo", sem dizer o que fazer.
        var mundo = origem.getWorld();
        int topo = mundo.getMaxHeight() - 1 - (maiorRaio + alturaExtra) - maiorRaio;
        if (origem.getBlockY() + maiorRaio >= mundo.getMaxHeight()) {
            sender.sendMessage(Component.text("Voce esta alto demais: o maior corpo tem "
                    + maiorRaio + " blocos de raio e nao cabe daqui ate o teto do mundo. "
                    + "Va para y=" + topo + " ou menos, ou use uma escala menor.",
                    NamedTextColor.RED));
            return;
        }
        if (origem.getBlockY() - maiorRaio < mundo.getMinHeight()) {
            sender.sendMessage(Component.text("Voce esta baixo demais: o maior corpo nao cabe "
                    + "daqui ate o fundo do mundo.", NamedTextColor.RED));
            return;
        }

        SistemaSolar.Plano plano = layout.planejar(corpos, this::textura, origem);

        for (String aviso : plano.avisos()) {
            sender.sendMessage(Component.text("  aviso: " + aviso, NamedTextColor.YELLOW));
        }
        if (plano.trabalhos().isEmpty()) {
            sender.sendMessage(Component.text("Nenhum corpo pode ser construido.", NamedTextColor.RED));
            return;
        }

        String modo = layout.enfileirado()
                ? "enfileirado com " + espacamento + " blocos de vao"
                : "distancias orbitais";
        sender.sendMessage(Component.text("Sistema solar (escala " + escala + ", " + modo + "): "
                + plano.trabalhos().size() + " corpos, " + plano.extensao()
                + " blocos do Sol ate a borda, no sentido +X.", NamedTextColor.GOLD));
        if (!layout.enfileirado()) {
            sender.sendMessage(Component.text("Muito espalhado? /" + label
                    + " sistema " + escala + " 10 enfileira os corpos com 10 blocos de vao, "
                    + "mantendo o tamanho deles.", NamedTextColor.GRAY));
        }

        plugin.getEsferaRenderer().renderizar(CHAVE_SISTEMA, "Sistema Solar",
                plano.trabalhos(), origem.getWorld(), sender);
    }

    /** Resolve a textura de um corpo, anotando o motivo quando falha em vez de abortar tudo. */
    private @Nullable Textura textura(Planeta corpo, List<String> avisos) {
        try {
            return plugin.getTextura(corpo);
        } catch (IOException e) {
            avisos.add(corpo.nome() + " ficou de fora: " + e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------- corpo unico

    private void render(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("planetas.render")) {
            sender.sendMessage(Component.text("Sem permissao para renderizar.", NamedTextColor.RED));
            return;
        }
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage(Component.text("Esse comando precisa de um jogador: o corpo nasce "
                    + "acima de quem executa.", NamedTextColor.RED));
            return;
        }

        Planeta corpo = exigirCorpo(sender, label, "render", args);
        if (corpo == null) {
            return;
        }

        int raio = corpo.raio();
        if (args.length >= 3) {
            try {
                raio = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Raio invalido: " + args[2], NamedTextColor.RED));
                return;
            }
        }

        Textura textura;
        try {
            textura = plugin.getTextura(corpo);
        } catch (IOException e) {
            sender.sendMessage(Component.text("Nao consegui carregar a textura de '" + corpo.id()
                    + "': " + e.getMessage(), NamedTextColor.RED));
            return;
        }

        int alturaExtra = plugin.getConfig().getInt("render.altura-extra", 6);
        Location centro = jogador.getLocation().getBlock().getLocation()
                .add(0, raio + alturaExtra, 0);

        Trabalho trabalho = new Trabalho(corpo, centro.getBlockX(), centro.getBlockY(),
                centro.getBlockZ(), raio, textura);
        plugin.getEsferaRenderer().renderizar(corpo.id(), corpo.nome(), List.of(trabalho),
                centro.getWorld(), sender);
    }

    private void remover(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("planetas.render")) {
            sender.sendMessage(Component.text("Sem permissao.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Uso: /" + label + " remover <id|sistema>",
                    NamedTextColor.YELLOW));
            return;
        }

        String alvo = args[1].toLowerCase(Locale.ROOT);
        String rotulo;
        if (alvo.equals(CHAVE_SISTEMA)) {
            rotulo = "Sistema Solar";
        } else {
            Planeta corpo = plugin.getPlanetaManager().porId(alvo);
            if (corpo != null) {
                rotulo = corpo.nome();
            } else if (plugin.getEsferaRenderer().temEsfera(alvo)) {
                // Corpo removido do config depois de construido: ainda da para desfazer.
                rotulo = alvo;
            } else {
                sender.sendMessage(Component.text("Corpo desconhecido: " + args[1], NamedTextColor.RED));
                return;
            }
        }

        plugin.getEsferaRenderer().remover(alvo, sender, rotulo);
    }

    private @Nullable Planeta exigirCorpo(CommandSender sender, String label, String sub, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Uso: /" + label + " " + sub + " <id>", NamedTextColor.YELLOW));
            return null;
        }
        Planeta corpo = plugin.getPlanetaManager().porId(args[1]);
        if (corpo == null) {
            sender.sendMessage(Component.text("Corpo desconhecido: " + args[1], NamedTextColor.RED));
        }
        return corpo;
    }

    // ------------------------------------------------------------------------------- listagem

    private void listar(CommandSender sender) {
        var corpos = plugin.getPlanetaManager().todos();
        if (corpos.isEmpty()) {
            sender.sendMessage(Component.text("Nenhum corpo configurado.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.text("=== Corpos (" + corpos.size() + ") ===", NamedTextColor.GOLD));
        for (Planeta p : corpos) {
            Component linha = Component.text(" - ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(p.nome(), p.cor()))
                    .append(Component.text(" (" + p.id() + ")", NamedTextColor.GRAY));
            if (plugin.getEsferaRenderer().temEsfera(p.id())) {
                linha = linha.append(Component.text(" [renderizado]", NamedTextColor.DARK_AQUA));
            }
            sender.sendMessage(linha);
        }

        var salvos = new java.util.TreeSet<>(plugin.getEsferaRenderer().chavesSalvas());
        plugin.getPlanetaManager().ids().forEach(salvos::remove);
        for (String chave : salvos) {
            String nome = chave.equals(CHAVE_SISTEMA) ? "Sistema solar completo" : chave;
            sender.sendMessage(Component.text(nome + " construido (removivel com /planetas remover "
                    + chave + ").", NamedTextColor.DARK_AQUA));
        }
        if (sender instanceof Player player) {
            Planeta atual = plugin.getPlanetaManager().porMundo(player.getWorld());
            if (atual != null) {
                sender.sendMessage(Component.text("Voce esta em: ", NamedTextColor.GRAY)
                        .append(Component.text(atual.nome(), atual.cor())));
            }
        }
    }

    private void info(CommandSender sender, Planeta corpo) {
        sender.sendMessage(Component.text(corpo.nome(), corpo.cor()));
        if (!corpo.descricao().isBlank()) {
            sender.sendMessage(Component.text("  " + corpo.descricao(), NamedTextColor.GRAY));
        }
        if (corpo.mundo() != null) {
            sender.sendMessage(campo("mundo", corpo.mundo()));
        }
        sender.sendMessage(campo("raio padrao", corpo.raio() + " blocos"));
        sender.sendMessage(campo("inclinacao axial", corpo.inclinacao() + " graus"));
        if (corpo.ehAchatado()) {
            sender.sendMessage(campo("escala", corpo.escala().x() + " / " + corpo.escala().y()
                    + " / " + corpo.escala().z()));
        }
        if (corpo.temAneis()) {
            sender.sendMessage(campo("aneis", corpo.aneis().size() + " banda(s), ate "
                    + Math.round(corpo.aneis().getLast().externo() * corpo.raio()) + " blocos"));
        }
        if (corpo.ehCampo()) {
            sender.sendMessage(campo("enxame", corpo.campo().quantidade() + " corpos, formato "
                    + corpo.campo().formato().name().toLowerCase(Locale.ROOT)));
        }
        if (corpo.temCauda()) {
            sender.sendMessage(campo("cauda", corpo.cauda().comprimento() + " blocos"));
        }
        if (corpo.noSistema()) {
            var o = corpo.orbita();
            if (o.de() != null) {
                sender.sendMessage(campo("orbita", "em torno de " + o.de()));
            } else if (o.ehFaixa()) {
                sender.sendMessage(campo("orbita", o.interna() + " a " + o.externa() + " UA"));
            } else {
                sender.sendMessage(campo("orbita", o.ua() + " UA do Sol"));
            }
        } else {
            sender.sendMessage(campo("orbita", "fora do /planetas sistema"));
        }
        try {
            sender.sendMessage(campo("textura", plugin.getTextura(corpo).origem()));
        } catch (IOException e) {
            sender.sendMessage(Component.text("  textura: ausente", NamedTextColor.RED));
        }
    }

    private static Component campo(String rotulo, String valor) {
        return Component.text("  " + rotulo + ": ", NamedTextColor.GRAY)
                .append(Component.text(valor, NamedTextColor.WHITE));
    }

    private void ajuda(CommandSender sender, String label) {
        sender.sendMessage(Component.text("/" + label, NamedTextColor.YELLOW)
                .append(Component.text(" - lista os corpos", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " info <id>", NamedTextColor.YELLOW)
                .append(Component.text(" - detalhes", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " render <id> [raio]", NamedTextColor.YELLOW)
                .append(Component.text(" - constroi um corpo acima de voce", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " sistema [escala] [espacamento]",
                        NamedTextColor.YELLOW)
                .append(Component.text(" - constroi o sistema solar inteiro. A escala encolhe "
                        + "distancias e corpos juntos; o espacamento, se informado, ignora as "
                        + "distancias orbitais e enfileira tudo com esse vao fixo.",
                        NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " remover <id|sistema>", NamedTextColor.YELLOW)
                .append(Component.text(" - desfaz", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " reload", NamedTextColor.YELLOW)
                .append(Component.text(" - recarrega a config", NamedTextColor.GRAY)));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filtrar(List.of("info", "render", "sistema", "remover", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remover")) {
            List<String> opcoes = new ArrayList<>(plugin.getEsferaRenderer().chavesSalvas());
            if (!opcoes.contains(CHAVE_SISTEMA)) {
                opcoes.add(CHAVE_SISTEMA);
            }
            return filtrar(opcoes, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("render"))) {
            return filtrar(plugin.getPlanetaManager().ids(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("render")) {
            return filtrar(List.of("16", "24", "32", "48"), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sistema")) {
            return filtrar(List.of("0.15", "0.25", "0.4", "0.6", "1.0"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("sistema")) {
            return filtrar(List.of("10", "20", "40"), args[2]);
        }
        return List.of();
    }

    private List<String> filtrar(Iterable<String> opcoes, String prefixo) {
        String p = prefixo.toLowerCase(Locale.ROOT);
        List<String> resultado = new ArrayList<>();
        for (String opcao : opcoes) {
            if (opcao.toLowerCase(Locale.ROOT).startsWith(p)) {
                resultado.add(opcao);
            }
        }
        return resultado;
    }
}
