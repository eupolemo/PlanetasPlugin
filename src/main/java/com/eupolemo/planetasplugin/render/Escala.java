package com.eupolemo.planetasplugin.render;

/**
 * Achatamento do corpo, como multiplicador do raio em cada eixo. {@code 1,1,1} e uma esfera.
 *
 * <p>Serve tanto para o achatamento leve dos gigantes gasosos (Saturno tem ~10%) quanto para
 * corpos francamente alongados, como Haumea.
 *
 * @param x leste-oeste
 * @param y vertical, o eixo de rotacao
 * @param z norte-sul
 */
public record Escala(double x, double y, double z) {

    public static final Escala ESFERA = new Escala(1.0, 1.0, 1.0);

    public Escala {
        if (x <= 0 || y <= 0 || z <= 0) {
            throw new IllegalArgumentException("escala deve ser positiva em todos os eixos");
        }
    }

    public double maior() {
        return Math.max(x, Math.max(y, z));
    }
}
