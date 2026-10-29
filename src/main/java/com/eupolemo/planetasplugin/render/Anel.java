package com.eupolemo.planetasplugin.render;

import org.bukkit.block.data.BlockData;

/**
 * Uma banda de anel, no plano equatorial do planeta.
 *
 * @param interno raio inicial, como multiplo do raio do planeta (1.0 = superficie)
 * @param externo raio final, como multiplo do raio do planeta
 * @param bloco   material da banda
 */
public record Anel(double interno, double externo, BlockData bloco) {

    public Anel {
        if (interno < 1.0) {
            throw new IllegalArgumentException("raio interno do anel deve ser >= 1.0 (a superficie)");
        }
        if (externo <= interno) {
            throw new IllegalArgumentException("raio externo do anel deve ser maior que o interno");
        }
    }
}
