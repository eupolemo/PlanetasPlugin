package com.eupolemo.planetasplugin.render;

import org.bukkit.block.data.BlockData;

/**
 * Um estado de bloco numa posicao. Serve tanto para "coloque isto aqui" quanto para "isto estava
 * aqui antes" — a operacao de desfazer e a mesma escrita, com a lista invertida.
 */
public record BlocoSalvo(int x, int y, int z, BlockData dado) {

    /** Chave do chunk que contem esta posicao, para agrupar escritas. */
    public long chunk() {
        return ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
    }

    public static int chunkX(long chave) {
        return (int) (chave >> 32);
    }

    public static int chunkZ(long chave) {
        return (int) chave;
    }
}
