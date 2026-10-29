package com.eupolemo.planetasplugin.planeta;

import com.eupolemo.planetasplugin.render.Anel;
import com.eupolemo.planetasplugin.render.Campo;
import com.eupolemo.planetasplugin.render.Cauda;
import com.eupolemo.planetasplugin.render.Escala;
import com.eupolemo.planetasplugin.render.Orbita;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Um corpo celeste configurado: estrela, planeta, lua, anao, cinturao, cometa ou buraco negro.
 *
 * @param id          chave usada em comandos, ex: "marte"
 * @param nome        nome exibido ao jogador
 * @param mundo       mundo do servidor que representa este corpo, ou null se ele so existe
 *                    como corpo renderizado
 * @param descricao   texto curto mostrado no /planetas info
 * @param cor         cor usada nas mensagens
 * @param raio        raio padrao, em blocos; num cinturao, a distancia de referencia
 * @param inclinacao  inclinacao axial em graus
 * @param escala      achatamento por eixo
 * @param aneis       bandas de anel, vazio se nao tem
 * @param campo       se nao for null, o corpo e um enxame, nao um corpo unico
 * @param cauda       se nao for null, o corpo tem cauda de cometa
 * @param textura     id do arquivo de textura; por padrao o proprio id do corpo
 * @param orbita      onde o corpo entra no render do sistema, ou null se nao entra
 */
public record Planeta(String id, String nome, @Nullable String mundo, String descricao,
                      TextColor cor, int raio, double inclinacao, Escala escala,
                      List<Anel> aneis, @Nullable Campo campo, @Nullable Cauda cauda,
                      String textura, @Nullable Orbita orbita) {

    public Planeta {
        aneis = List.copyOf(aneis);
    }

    public boolean temAneis() {
        return !aneis.isEmpty();
    }

    public boolean ehCampo() {
        return campo != null;
    }

    public boolean temCauda() {
        return cauda != null;
    }

    public boolean noSistema() {
        return orbita != null;
    }

    /** @return true se o corpo nao e uma esfera perfeita. */
    public boolean ehAchatado() {
        return escala.x() != escala.y() || escala.y() != escala.z();
    }

    /**
     * Redimensiona o corpo inteiro: raio, corpos de enxame e cauda.
     *
     * <p>Encolher so as distancias do sistema faria os corpos se sobreporem, porque os raios
     * ficariam do mesmo tamanho. A escala precisa valer para os dois.
     *
     * @param minimo raio minimo aceito pelo renderizador
     * @param maximo teto de raio da config
     */
    public Planeta escalado(double fator, int minimo, int maximo) {
        int novoRaio = Math.min(maximo, Math.max(minimo, (int) Math.round(raio * fator)));
        return new Planeta(id, nome, mundo, descricao, cor, novoRaio, inclinacao, escala, aneis,
                campo == null ? null : campo.escalado(fator),
                cauda == null ? null : cauda.escalada(fator),
                textura, orbita);
    }

    /** @return copia com outro campo e outro raio, usada pelo layout do sistema completo. */
    public Planeta com(Campo novoCampo, int novoRaio) {
        return new Planeta(id, nome, mundo, descricao, cor, novoRaio, inclinacao, escala,
                aneis, novoCampo, cauda, textura, orbita);
    }

    public static TextColor parseCor(String hex) {
        if (hex == null || hex.isBlank()) {
            return NamedTextColor.WHITE;
        }
        TextColor cor = TextColor.fromCSSHexString(hex.startsWith("#") ? hex : "#" + hex);
        return cor != null ? cor : NamedTextColor.WHITE;
    }
}
