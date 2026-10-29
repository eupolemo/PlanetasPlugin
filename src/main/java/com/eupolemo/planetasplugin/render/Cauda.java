package com.eupolemo.planetasplugin.render;

import org.bukkit.block.data.BlockData;

/**
 * Cauda de cometa: um cone esparso de blocos apontando no sentido +X, que num render do sistema
 * completo e justamente o sentido oposto ao Sol.
 *
 * @param comprimento  ate onde vai, em blocos
 * @param abertura     raio do cone por bloco de comprimento; 0.25 abre 1 para cada 4
 * @param densidade    fracao do volume preenchida na base; cai a zero na ponta
 * @param bloco        material dos graos
 */
public record Cauda(int comprimento, double abertura, double densidade, BlockData bloco) {

    /** @return copia proporcionalmente mais curta ou mais longa. */
    public Cauda escalada(double fator) {
        return new Cauda(Math.max(1, (int) Math.round(comprimento * fator)), abertura,
                densidade, bloco);
    }

    public Cauda {
        if (comprimento < 1) {
            throw new IllegalArgumentException("comprimento da cauda deve ser >= 1");
        }
        if (abertura <= 0 || abertura > 2.0) {
            throw new IllegalArgumentException("abertura deve estar entre 0 e 2.0");
        }
        if (densidade <= 0 || densidade > 1.0) {
            throw new IllegalArgumentException("densidade deve estar entre 0 e 1.0");
        }
    }
}
