package com.eupolemo.planetasplugin.render;

import com.eupolemo.planetasplugin.planeta.Planeta;

/**
 * Um corpo a construir num ponto do mundo. Um render de corpo unico tem um trabalho; um render
 * do sistema completo tem dezenas, todos desfeitos juntos.
 *
 * <p>Guarda coordenadas soltas em vez de um {@code Location} porque a geometria e calculada
 * fora da thread principal, e {@code Location} carrega uma referencia ao mundo.
 */
public record Trabalho(Planeta planeta, int cx, int cy, int cz, int raio, Textura textura) {
}
