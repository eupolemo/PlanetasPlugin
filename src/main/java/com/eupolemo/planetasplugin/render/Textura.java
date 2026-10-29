package com.eupolemo.planetasplugin.render;

import org.bukkit.block.data.BlockData;

/** Mapa de superficie de um planeta, amostrado por coordenada geografica. */
public interface Textura {

    /**
     * @param lat latitude em graus, -90 (sul) a +90 (norte)
     * @param lon longitude em graus, -180 a +180
     * @return o bloco que representa aquele ponto da superficie
     */
    BlockData amostrar(double lat, double lon);

    /** Descricao curta da fonte, para log e para o /planetas info. */
    String origem();
}
