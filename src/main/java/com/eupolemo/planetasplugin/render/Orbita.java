package com.eupolemo.planetasplugin.render;

import org.jetbrains.annotations.Nullable;

/**
 * Onde o corpo entra no render do sistema completo.
 *
 * <p>Um corpo sem orbita nao aparece em {@code /planetas sistema} — e o caso de estrelas de
 * outros sistemas e da Nuvem de Oort, longe demais para caber no mesmo modelo.
 *
 * @param ua      distancia media ao Sol, em unidades astronomicas; 0 para luas
 * @param de      id do corpo em torno do qual este orbita, ou null se orbita o Sol
 * @param interna limite interno, em UA, quando o corpo e uma faixa (cinturao)
 * @param externa limite externo, em UA, quando o corpo e uma faixa
 * @param desvio  deslocamento lateral em blocos, para tirar da linha corpos que estao
 *                praticamente a mesma distancia do Sol e se sobreporiam
 */
public record Orbita(double ua, @Nullable String de, double interna, double externa, double desvio) {

    public boolean ehFaixa() {
        return externa > interna;
    }
}
