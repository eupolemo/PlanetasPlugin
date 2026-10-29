package com.eupolemo.planetasplugin.render;

/**
 * Um enxame de corpos irregulares, em vez de um unico corpo: cinturoes e nuvens.
 *
 * <p>Distancias sao multiplos do raio de referencia do corpo.
 *
 * @param quantidade      quantos corpos gerar
 * @param corpoMin        menor raio de um corpo, em blocos
 * @param corpoMax        maior raio de um corpo, em blocos
 * @param interno         distancia minima do centro
 * @param externo         distancia maxima do centro
 * @param espessura       dispersao acima e abaixo do plano; ignorada no formato ESFERA
 * @param irregularidade  0 = esferas; 0.5 = batatas bem deformadas
 * @param semente         a mesma semente gera sempre o mesmo arranjo
 * @param formato         ANEL para um cinturao; ESFERA para uma casca em torno do centro
 */
public record Campo(int quantidade, double corpoMin, double corpoMax, double interno,
                    double externo, double espessura, double irregularidade, long semente,
                    Formato formato) {

    /** Como os corpos se distribuem no espaco. */
    public enum Formato {
        /** Faixa achatada no plano equatorial: cinturao de asteroides, de Kuiper. */
        ANEL,
        /** Casca esferica em todas as direcoes: a Nuvem de Oort. */
        ESFERA
    }

    public Campo {
        if (quantidade < 1) {
            throw new IllegalArgumentException("quantidade deve ser >= 1");
        }
        if (corpoMin < 1 || corpoMax < corpoMin) {
            throw new IllegalArgumentException("corpo-min deve ser >= 1 e corpo-max >= corpo-min");
        }
        if (interno < 0 || externo < interno) {
            throw new IllegalArgumentException("externo deve ser >= interno");
        }
        if (irregularidade < 0 || irregularidade > 0.9) {
            throw new IllegalArgumentException("irregularidade deve estar entre 0 e 0.9");
        }
    }

    /** @return copia com os corpos redimensionados; nenhum corpo fica menor que 1 bloco. */
    public Campo escalado(double fator) {
        return new Campo(quantidade, Math.max(1, corpoMin * fator), Math.max(1, corpoMax * fator),
                interno, externo, espessura, irregularidade, semente, formato);
    }

    /**
     * @return copia reposicionada para o layout do sistema completo
     *
     * <p>A espessura entra em blocos e nao como fracao: no render isolado o raio de referencia do
     * cinturao de asteroides e 40, e no sistema passa a 826. Manter a fracao multiplicaria a
     * espessura por 20 e o cinturao nao caberia mais na altura do mundo.
     */
    public Campo reposicionado(double novoInterno, double novoExterno, int novaQuantidade,
                               double espessuraEmBlocos, double raioReferencia) {
        return new Campo(novaQuantidade, corpoMin, corpoMax, novoInterno, novoExterno,
                espessuraEmBlocos / raioReferencia, irregularidade, semente, formato);
    }
}
