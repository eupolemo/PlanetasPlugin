# PlanetasPlugin

Plugin Paper que constrói o sistema solar em blocos: 37 corpos — Sol, 8 planetas, 11 luas,
cinturão de asteroides, cinturão de Kuiper, nuvem de Oort, planetas anões, um cometa com cauda,
estrelas vizinhas e um buraco negro. Com textura por latitude/longitude, inclinação axial real,
achatamento por eixo, anéis e um render do sistema inteiro em escala comprimida.

O que cada render sobrescreveu é gravado em disco, então `/planetas remover` continua
funcionando depois de o servidor reiniciar.

- Paper API `1.21.4-R0.1-SNAPSHOT`
- Java 21
- Maven

## Build

```
mvn clean package
```

Jar em `target/PlanetasPlugin-1.6.0.jar`.

## Instalar no servidor

Copie o jar para `plugins/` e reinicie o servidor. Ou use o script:

```
./deploy.sh /caminho/do/servidor/plugins
./deploy.sh usuario@servidor:/caminho/do/servidor/plugins
```

Grave o destino em `.deploy.env` (`DEST=...`) para rodar só `./deploy.sh`.

## Jogadores de Bedrock

Um cliente Bedrock não conecta num servidor Java, e um Bedrock Dedicated Server não roda
plugins Java. Para que celular, console e Windows 10/11 entrem, o **servidor** precisa de
[Geyser](https://geysermc.org) + Floodgate — o plugin não muda nada, porque a renderização é
toda server-side.

Se um jogador Bedrock entra mas `/planetas render` falha, quase sempre é permissão, não
compatibilidade: `planetas.render` é `default: op`, e o Floodgate prefixa o nome dele (padrão
`.`). Confira o nome exato com `/list` e use o prefixo:

```
op .NomeDoJogador
```

Alternativas descartadas em [ADR-0001](docs/adr/0001-compatibilidade-com-bedrock.md).

## Comandos

| Comando | Permissão | Efeito |
|---|---|---|
| `/planetas` | `planetas.use` (todos) | Lista os corpos |
| `/planetas info <id>` | `planetas.use` | Detalhes de um corpo |
| `/planetas render <id> [raio]` | `planetas.render` (op) | Constrói um corpo acima de você |
| `/planetas sistema [escala] [espacamento]` | `planetas.sistema` (op) | Constrói o sistema solar inteiro |
| `/planetas remover <id\|sistema>` | `planetas.render` (op) | Desfaz, restaurando os blocos |
| `/planetas reload` | `planetas.reload` (op) | Recarrega o `config.yml` |

Aliases: `/planeta`, `/pl`.

## O sistema completo

`/planetas sistema [escala]` constrói 31 corpos de uma vez, saindo do Sol no sentido **+X**.

### Dois argumentos: tamanho e espaçamento

```
/planetas sistema [escala] [espacamento]
```

- **escala** — multiplica distâncias **e** tamanhos.
- **espaçamento** — se informado, **ignora as distâncias orbitais** e enfileira os corpos com
  esse vão fixo entre superfícies, mantendo a ordem astronômica.

```
/planetas sistema 1.0 10
```

Corpos em tamanho cheio, 10 blocos de vão entre eles. Medido:

| corpo | x do Sol | raio | alcance | vão real |
|---|---:|---:|---:|---:|
| sol | 0 | 64 | 64 | — |
| mercurio | 84 | 10 | 10 | 10 |
| venus | 126 | 22 | 22 | 10 |
| terra | 182 | 24 | 24 | 10 |
| marte | 229 | 13 | 13 | 10 |
| vesta | 258 | 6 | 6 | 10 |
| asteroides | 360 | 40 | 86 | 10 |
| ceres | 462 | 6 | 6 | 10 |
| jupiter | 526 | 48 | 48 | 10 |
| saturno | 656 | 34 | 72 | 10 |
| halley | 848 | 4 | 110 | 10 |
| urano | 1.022 | 30 | 54 | 10 |
| netuno | 1.115 | 29 | 29 | 10 |
| plutao | 1.163 | 9 | 9 | 10 |
| kuiper | 1.307 | 60 | 125 | 10 |
| haumea | 1.469 | 9 | 27 | 10 |
| quaoar | 1.524 | 5 | 18 | 10 |
| arrokoth | 1.561 | 5 | 9 | 10 |
| makemake | 1.586 | 6 | 6 | 10 |
| eris | 1.611 | 9 | 9 | 10 |

**1.620 blocos contra 2.947** — 55% da extensão, com os corpos do mesmo tamanho.

"Alcance" é o que o corpo ocupa a partir do centro, contando anel, enxame e cauda: por isso
Saturno reserva 72 (anéis), o Cinturão de Kuiper 125 e o Halley 110 (a cauda). O vão de 10 é
medido entre esses alcances, não entre as superfícies das esferas.

O ganho depende da escala — e ele encolhe conforme os corpos encolhem:

| escala | vão | extensão | vs. orbital |
|---:|---:|---:|---:|
| 1,0 | 10 | 1.620 | 55% |
| 1,0 | 20 | 1.810 | 61% |
| 0,5 | 10 | 904 | 61% |
| 0,5 | 20 | 1.094 | 74% |
| 0,25 | 10 | 586 | 79% |
| 0,25 | 20 | 776 | **105%** |

Na escala 0,25 com vão 20 o modo enfileirado fica **maior** que o orbital: os corpos param de
encolher no piso de raio 3, mas o vão continua fixo. **O modo enfileirado vale a pena em escala
cheia**, que é justamente quando o modo orbital é mais desperdiçado.

Nesse modo o `orbita.desvio` é ignorado — não há corpos co-orbitais para separar — e as folgas
são exatas por construção, então não há aviso de sobreposição.

Para deixar como padrão, use `sistema.espacamento` no config (negativo = distâncias orbitais).

### Só a escala

```
/planetas sistema 0.25
```

A escala multiplica **distâncias e tamanhos juntos**. Isso importa: encolher só as distâncias
faria os corpos se sobreporem, porque os raios ficariam iguais. Medido:

| escala | extensão | blocos | chunks | folga mín. | Sol | Terra | Júpiter |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 0,10 | 297 | 1.957 | ≈123 | **−4** ✗ | 6 | 3 | 5 |
| 0,15 | 444 | 3.272 | ≈147 | 1 | 10 | 4 | 7 |
| 0,25 | 738 | 8.164 | ≈202 | 4 | 16 | 6 | 12 |
| 0,40 | 1.179 | 22.183 | ≈317 | 6 | 26 | 10 | 19 |
| 0,60 | 1.768 | 52.003 | ≈509 | 11 | 38 | 14 | 29 |
| 1,00 | 2.947 | 172.570 | ≈1.088 | 18 | 64 | 24 | 48 |

**0,25 é um bom ponto de partida**: cabe em 738 blocos, custa 8 mil blocos em vez de 172 mil, e
constrói em segundos em vez de minutos.

Abaixo de 0,15 os corpos começam a colidir — não porque a escala falhe, mas porque o raio tem
piso de 3 blocos, então os menores param de encolher enquanto as distâncias continuam. O
planejador detecta e avisa qual par se sobrepõe; ele constrói mesmo assim, a decisão é sua.

Aceita 0,05 a 2,0. Acima de 1,0 os corpos param de crescer em `render.raio-maximo`.

Para mudar o padrão sem passar argumento, mexa em `sistema.unidade` (500) — mas aí só as
distâncias mudam, e os corpos podem colidir. O argumento é o caminho seguro.

Números da escala 1,0:

| | |
|---|---|
| corpos construídos | 31 |
| blocos | ~172 mil |
| memória do undo | ~11 MB |
| extensão | 2.947 blocos do Sol até Éris |

O tempo depende do terreno, não do número de blocos: em área nova a geração dos ~700 chunks
domina, e a construção leva de dezenas de segundos a alguns minutos. Em área já gerada é rápido.
O comando reporta o progresso a cada 5 s.

O Sol tem 64 blocos de raio e nasce 70 acima de você, então é preciso estar **abaixo de y=185**
para tudo caber sob o teto do mundo. O comando confere isso antes e diz para onde ir.

Tudo é um render só: `/planetas remover sistema` desfaz os 31 corpos juntos — inclusive numa
sessão futura, depois de o servidor reiniciar.

### A escala

Distâncias reais não cabem — Netuno está 78× mais longe do Sol que Mercúrio. O layout comprime:

```
distancia = sistema.unidade * UA ^ sistema.compressao
```

Com `compressao: 1.0` a escala é linear e o modelo fica impraticável. O padrão (`500` e `0.42`)
põe Mercúrio a 336 blocos, a Terra a 500, Júpiter a 1.000 e Éris a 2.938 — a ordem e a noção de
"muito mais longe" se preservam sem exigir uma caminhada de horas.

Os **raios** também não estão em escala: o Sol em escala seria 2.600 blocos com a Terra em 24.

Corpos praticamente co-orbitais (Haumea, Quaoar, Arrokoth e Makemake estão todos por volta de
44 UA) colidiriam na linha. `orbita.desvio` tira cada um para o lado. O planejador **confere as
folgas antes de construir** e avisa no chat qual par se sobrepõe e por quantos blocos.

### As luas

A Lua está a 0,0026 UA da Terra — invisível em qualquer escala que mostre o sistema inteiro.
Em vez de sumirem, as luas são enfileiradas ao lado do corpo que orbitam, no sentido +Z.

## Persistência do undo

O histórico de undo vive em `plugins/PlanetasPlugin/renders/<chave>.bin.gz`. Sem ele, reiniciar
o servidor com o sistema solar construído deixava 179 mil blocos órfãos: continuavam no mundo, e
nada mais sabia como desfazê-los.

O arquivo é gravado quando o render termina, e no desligamento para renders interrompidos no
meio. É lido **só na hora de remover** — o boot apenas lista os arquivos, então nada disso ocupa
memória enquanto você não desfaz nada.

Formato: cabeçalho, UUID do mundo, uma paleta com os estados de bloco distintos e, para cada
posição, o índice na paleta. A paleta é o que faz a diferença — um render no ar sobrescreve
quase só ar, então milhares de entradas apontam para meia dúzia de estados. Medido com as
179.295 entradas do sistema completo:

| | |
|---|---|
| arquivo | 0,89 MB |
| sem compressão | 2,39 MB (2,7× maior) |
| bytes por bloco | 5,19 |
| escrita | 215 ms (assíncrona) |
| leitura | 23 ms (assíncrona) |

A escrita é feita num arquivo temporário e só então renomeada: se o servidor morrer no meio,
o arquivo anterior continua íntegro.

**A restauração passa pelo mesmo escritor**, com carregamento assíncrono de chunk e tudo — os
chunks do modelo podem ter sido descarregados desde a construção, e lê-los de volta na thread
principal travaria o servidor exatamente como na construção. Devolver 179 mil blocos num único
tick também travaria. A lista encolhe pelo fim, então o que sobra é exatamente o que ainda está no
mundo — um desligamento no meio de um `remover` grava um histórico coerente e você continua de
onde parou.

Casos tratados: mundo não carregado, arquivo corrompido (o registro é descartado e os blocos
ficam), bloco que sumiu numa atualização do Minecraft (perde-se aquela posição, não o render
inteiro), e corpo apagado do `config.yml` depois de construído (ainda dá para remover pelo id).

## Render

Para cada bloco da casca a normal vira latitude/longitude, e a textura diz qual bloco usar.

**A casca é o conjunto dos blocos internos que fazem fronteira com o lado de fora** — não uma
faixa de distâncias. Isso é estanque por construção: qualquer caminho de fora até o centro,
andando pelas 6 faces, precisa entrar no sólido, e o primeiro bloco sólido do caminho tem um
vizinho externo, logo está na casca. Vale para qualquer achatamento.

**A geometria roda fora da thread principal.** O sistema completo são dezenas de milhões de
operações; calcular isso na thread do servidor congelaria tudo.

**Os chunks são carregados de forma assíncrona antes da escrita.** Isto não é otimização, é o
que impede o servidor de morrer. Fatiar por número de blocos não basta: o modelo se espalha por
~700 chunks, quase todos terreno que ainda não existe, e escrever direto faz o `getBlockData`
disparar geração de chunk **síncrona**. A thread do servidor fica parada em
`ServerChunkCache.syncLoad` e o watchdog do Paper derruba tudo com *"The server has not responded
for 10 seconds"*.

As ações são agrupadas por chunk; cada chunk é pedido com `getChunkAtAsync` (que gera terreno
numa thread própria) e só entra na fila de escrita quando está pronto. `render.chunks-em-voo`
controla quantos são gerados em paralelo.

O agrupamento preserva a ordem relativa dentro de cada chunk — que é onde posições repetidas
sempre caem, já que a mesma coordenada está sempre no mesmo chunk. Por isso o desfazer em ordem
inversa continua correto quando um anel foi construído por cima de uma casca.

- Verificado por flood fill nos 37 corpos: nenhum vazamento, inclusive no elipsoide de Haumea
  e em cada corpo de cinturão individualmente.
- Blocos são colocados sem física (`applyPhysics = false`), senão a areia cairia.
- `remover` restaura o estado anterior de cada bloco, na ordem inversa.

### Achatamento

`escala` multiplica o raio em cada eixo. `1/1/1` é uma esfera. Saturno é 10% mais achatado nos
polos, e Haumea gira em 4 horas e ficou com forma de ovo:

```yaml
escala: { x: 1.0, y: 0.44, z: 0.73 }
```

### Anéis

Bandas no plano equatorial, em múltiplos do raio (`1.0` = superfície):

```yaml
anel:
  bandas:
    - { interno: 1.24, externo: 1.53, bloco: LIGHT_GRAY_CONCRETE }
    - { interno: 1.62, externo: 2.10, bloco: WHITE_CONCRETE }
```

O vão entre as duas é a Divisão de Cassini. Saturno, Urano, Haumea e Quaoar têm anéis — e o
disco de acreção de Sagitário A* usa o mesmo mecanismo, com três bandas de blocos quentes.

O plano do anel é `dy·cos − dz·sen = 0`. Em vez de testar cada bloco do volume, o código resolve
uma coordenada em função da outra — um bloco por coluna, sem os furos que um plano inclinado
deixa numa grade cúbica. Acima de 45° a conta inverte, senão estoura em Urano (97,77°).

### Enxames

`campo` troca o corpo único por vários corpos irregulares:

```yaml
campo:
  formato: anel      # ou 'esfera', para a Nuvem de Oort
  quantidade: 70
  corpo-min: 2
  corpo-max: 6
  interno: 1.0       # múltiplos do raio
  externo: 2.0
  espessura: 0.10    # dispersão vertical (só no formato anel)
  irregularidade: 0.35
  semente: 2024
```

O raio de cada corpo varia com a direção — por isso são batatas, não bolas. A mesma semente
sempre reproduz o mesmo arranjo.

No formato `esfera` a direção é sorteada pelo cosseno da latitude, não pela latitude: sortear a
latitude direto acumularia os corpos nos polos.

Um `campo` de **um corpo só**, com `interno: 0`, dá a forma irregular sem inventar outro
mecanismo — é assim que Fobos, Deimos, Vesta e o núcleo do Halley são feitos. Com `quantidade: 2`
e `interno: 0.7` sai o binário de contato de Arrokoth.

Num render do sistema, um cinturão é reposicionado: a quantidade passa a vir da circunferência
(`sistema.densidade-faixa` corpos por 100 blocos — o Kuiper tem ~14 mil blocos de volta e recebe
293 corpos, contra 90 no render isolado), e a **espessura passa a ser absoluta**
(`sistema.espessura-faixa`, em blocos).

Essa segunda parte não é detalhe: `espessura` é fração do raio de referência, que no render
isolado do cinturão de asteroides é 40 e no sistema vira 826. Mantida como fração, a espessura
saltaria de ±4 para ±83 blocos — e o Kuiper, de ±5 para ±207, mais do que a altura inteira do
overworld. Metade dos corpos seria descartada em silêncio.

Pelo mesmo motivo, `render.raio-maximo` **não** se aplica ao raio de um enxame: ali ele é
distância de layout, não tamanho de corpo. O que respeita o teto é `corpo-max`.

### Cauda de cometa

```yaml
cauda:
  comprimento: 110
  abertura: 0.20
  densidade: 0.06
  bloco: WHITE_STAINED_GLASS
```

Cone esparso apontando para +X, que num render do sistema é o lado oposto ao Sol — que é para
onde a cauda de um cometa realmente aponta, empurrada pelo vento solar, e não arrastada pelo
movimento. Rarefaz com a distância.

## Os corpos

| id | raio | inclinação | órbita (UA) | destaque |
|---|---:|---:|---:|---|
| `sol` | 64 | 7,25° | 0 | granulação e manchas solares |
| `mercurio` | 10 | 0,03° | 0,387 | crateras e a bacia de Caloris |
| `venus` | 22 | 177,4° | 0,723 | nuvens, rotação retrógrada |
| `terra` | 24 | 23,44° | 1,0 | continentes desenhados à mão |
| `lua` | 12 | 6,68° | ↳ terra | mares só no lado visível |
| `marte` | 13 | 25,19° | 1,524 | Syrtis Major, Valles Marineris |
| `fobos` | 5 | — | ↳ marte | batata irregular |
| `deimos` | 4 | — | ↳ marte | batata irregular |
| `asteroides` | anel | 10° | 2,1–3,3 | cinturão principal |
| `vesta` | 6 | — | 2,36 | segundo maior do cinturão |
| `ceres` | 6 | 4° | 2,77 | faculas de Occator |
| `jupiter` | 48 | 3,13° | 5,203 | Grande Mancha Vermelha, achatado |
| `io` | 8 | — | ↳ jupiter | enxofre e vulcões |
| `europa` | 7 | — | ↳ jupiter | lineae na crosta de gelo |
| `ganimedes` | 9 | — | ↳ jupiter | terreno sulcado |
| `calisto` | 8 | — | ↳ jupiter | bacia Valhalla |
| `saturno` | 34 | 26,73° | 9,537 | anéis + Divisão de Cassini |
| `tita` | 9 | — | ↳ saturno | névoa laranja |
| `encelado` | 4 | — | ↳ saturno | tiger stripes do polo sul |
| `halley` | 4 | — | 17,8 | núcleo irregular + cauda |
| `urano` | 30 | 97,77° | 19,19 | deitado de lado, com anel |
| `netuno` | 29 | 28,32° | 30,07 | Grande Mancha Escura |
| `triton` | 7 | 156,9° | ↳ netuno | terreno cantaloupe |
| `kuiper` | anel | 5° | 30–50 | 293 corpos no sistema |
| `plutao` | 9 | 122,53° | 39,5 | Tombaugh Regio, Cthulhu Macula |
| `caronte` | 5 | — | ↳ plutao | Mordor Macula |
| `haumea` | 9 | 126° | 43,1 | ovo 1 : 0,44 : 0,73, com anel |
| `quaoar` | 5 | — | 43,7 | anel longe demais para a teoria |
| `arrokoth` | 5 | — | 44,6 | binário de contato |
| `makemake` | 6 | — | 45,8 | metano congelado, escuro |
| `eris` | 9 | 78° | 67,8 | o corpo mais refletivo conhecido |
| `sedna` | 7 | — | fora | 11 mil anos por volta |
| `oort` | 60 | — | fora | casca esférica de 140 corpos |
| `betelgeuse` | 60 | 20° | fora | supergigante vermelha |
| `sirius` | 30 | — | fora | a mais brilhante do céu |
| `proxima` | 12 | — | fora | anã vermelha, a mais próxima |
| `sagitario-a` | 20 | 30° | fora | buraco negro + disco de acreção |

"fora" = sem `orbita` no config, então não entra no `/planetas sistema`. Sedna a 506 UA ficaria a
6.800 blocos, e a Nuvem de Oort, muito além disso: o modelo viraria caminhada. Renderize com
`/planetas render <id>` para vê-los de perto.

## Texturas

Ficam em `plugins/PlanetasPlugin/texturas/`, resolvidas pelo campo `textura` do corpo (que por
padrão é o próprio id — daí Fobos, Deimos, Vesta e Arrokoth apontarem todos para `asteroides`):

1. `<id>.png` — imagem equiretangular (proporção 2:1); cada pixel casa com o bloco de cor mais
   próxima de uma paleta interna. Use um Blue Marble da NASA para a Terra real.
2. `<id>.txt` — mapa em texto 72×36 (células de 5°), com a legenda no próprio arquivo:

```
72 36
= . BLUE_CONCRETE     ; oceano
= # GREEN_CONCRETE    ; terra
....##########....
```

Trocar o bloco de um terreno é editar a legenda e dar `/planetas reload`. Símbolo usado na grade
e ausente da legenda é erro de carga, com a linha apontada na mensagem.

### O Sol e o motor de luz

Só 3,5% da casca do Sol (1.461 de 42.282 blocos) emite luz, via `SHROOMLIGHT`. Trocar `.` na
legenda de `sol.txt` por `GLOWSTONE` deixa a estrela inteira brilhante — mas são ~40 mil fontes
de luz de uma vez, e o motor de luz do Paper vai engasgar. O padrão é conservador de propósito.
O mesmo vale para o disco de acreção de Sagitário A*, que já traz 12 mil blocos, sendo boa parte
`SHROOMLIGHT`.

## Estrutura

```
PlanetasPlugin.java              ciclo de vida, migracao de config, cache de texturas
planeta/Planeta.java             record de um corpo
planeta/PlanetaManager.java      le o config e indexa por id e por mundo
command/PlanetasCommand.java     executor + tab-complete
listener/PlayerListener.java     title ao entrar e ao trocar de mundo
render/EsferaRenderer.java       geometria (async), orquestracao de construcao e undo
render/EscritorEmLote.java       escrita fatiada com carregamento assincrono de chunk
render/RegistroRenders.java      historico de undo em disco (paleta + gzip)
render/BlocoSalvo.java           o estado de um bloco antes do render
render/SistemaSolar.java         onde cada corpo fica no sistema completo
render/Trabalho.java             um corpo a construir num ponto
render/Escala.java               achatamento por eixo
render/Anel.java                 uma banda de anel
render/Campo.java                enxame de corpos (anel ou esfera)
render/Cauda.java                cauda de cometa
render/Orbita.java               posicao no sistema
render/Textura.java              amostragem por lat/lon
render/TexturaAscii.java         mapa em texto com legenda embutida
render/TexturaImagem.java        PNG equiretangular -> bloco mais proximo
render/Texturas.java             descobre png ou txt na pasta do plugin
```

## Atualizando de uma versão anterior

O Bukkit nunca sobrescreve um `config.yml` existente, então o plugin versiona o arquivo
(`versao-config`). Ao encontrar um formato antigo ele arquiva o atual como
`config-v<n>.yml.bak`, gera o novo e reescreve as texturas embutidas.

| formato | versão | novidades |
|---|---|---|
| 1 | 1.0.0 | Terra, textura sem legenda |
| 2 | 1.1.0 | 8 planetas, legenda na textura, anéis, inclinação por corpo |
| 3 | 1.2.0 | Sol, Lua, cinturão, 5 anões, achatamento por eixo |
| 4 | 1.3.0 | sistema completo, Kuiper, Oort, luas, cometa, estrelas, buraco negro |
| 4 | 1.4.0 | undo persistido em disco; sem mudanca de config |
| 4 | 1.4.2 | carregamento assincrono de chunk; sem mudanca de config |
| 4 | 1.5.0 | escala como argumento do /planetas sistema; sem mudanca de config |
| 4 | 1.6.0 | espacamento fixo como segundo argumento; sem mudanca de config |

Suas edições ficam no `.bak` — mas os arquivos de textura editados à mão são sobrescritos na
migração, porque o formato deles pode mudar junto.

**Apague o jar da versão anterior** antes de reiniciar: dois jars na mesma pasta e o Paper
carrega os dois.

## Notas

- API do Bukkit é single-thread. A geometria roda async, mas toda escrita de bloco volta para a
  thread principal — é o que a API exige.
- `/reload` do servidor é fonte de bugs difíceis. Reinicie o servidor ao trocar o jar.
- Renders sobrevivem a restart, mas o registro é por **id**: renomear um corpo no `config.yml`
  depois de construí-lo faz o arquivo antigo continuar valendo pelo id antigo. O `/planetas`
  lista essas chaves órfãs, e o `remover` aceita elas.
- Ids de corpos precisam servir como nome de arquivo (minúsculas, números, `.`, `-`, `_`). O
  plugin recusa renderizar um id fora disso, em vez de construir algo que não daria para desfazer.
- Subir de versão do Minecraft: mude `paper.api.version` no `pom.xml` e `api-version` no
  `plugin.yml`.
