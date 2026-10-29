# ADR-0001: Compatibilidade com Bedrock via Geyser, sem addon nativo

## Status

Aceita — 2026-08-15

## Contexto

O plugin é um plugin Paper (API Bukkit), escrito em Java e executado no servidor. Jogadores
de Minecraft Bedrock — celular, console, Windows 10/11 — não conseguem conectar num servidor
Java: os protocolos de rede são incompatíveis, e não há ponte nativa.

Isso apareceu na prática quando um jogador de Bedrock não conseguiu executar `/planetas render`
enquanto o mesmo comando funcionava no cliente Java.

As restrições reais:

- Bedrock Dedicated Server não executa plugins Java. O ecossistema de extensão dele é outro:
  behavior packs em JSON e JavaScript, com uma API muito mais limitada.
- O que o plugin faz é quase todo escrita de blocos no mundo (`World#getBlockAt`,
  `Block#setBlockData`) e mensagens de chat/title. Nada disso depende do cliente.
- Os 22 blocos usados pelas texturas e pelos anéis (concreto, terracota, `DEEPSLATE`,
  `ANDESITE`, `SHROOMLIGHT`, `SAND`, `SNOW_BLOCK`, `STONE`) existem nas duas edições.

## Decisão

O plugin continua sendo **exclusivamente um plugin Paper/Java**. O suporte a jogadores Bedrock
é responsabilidade da infraestrutura do servidor — **Geyser** (tradução de protocolo) e
**Floodgate** (autenticação sem conta Java) —, não do plugin.

Não haverá behavior pack nem port para Bedrock.

## Alternativas consideradas

**Escrever um behavior pack Bedrock equivalente.** Perdeu por custo e por capacidade: seria uma
segunda base de código, em outra linguagem, com outra API, para manter em paralelo. Pior, a
Scripting API do Bedrock não oferece escrita de blocos em lote com o controle que a geração das
cascas exige, e não há equivalente ao agendador do Bukkit para fatiar o trabalho por tick. O
recurso central do plugin ficaria degradado.

**Manter uma base compartilhada com dois adaptadores.** Perdeu porque a superfície comum entre
Bukkit e a API do Bedrock é pequena demais: sobrariam os cálculos de geometria puros, que já são
a parte fácil e barata. A abstração custaria mais do que economiza.

**Não suportar Bedrock de forma alguma.** Perdeu porque Geyser resolve o problema sem custo
nenhum para o plugin — a decisão é de quem opera o servidor, e já existe gente jogando assim.

## Consequências

**A favor.** Uma base de código só, no ecossistema onde o plugin já é bom. Como toda a
renderização é server-side, um jogador Bedrock atrás do Geyser vê exatamente as mesmas esferas,
recebe os mesmos titles e usa os mesmos comandos — sem nenhuma linha de código específica.

**Contra, aceito de propósito.** O plugin não funciona sozinho para Bedrock: exige que o
servidor rode Geyser e Floodgate. Isso é uma dependência operacional que a documentação precisa
deixar clara, porque o sintoma ("meu filho não conseguiu rodar o comando") não aponta para a
causa.

**Armadilha conhecida.** O Floodgate prefixa o nome dos jogadores Bedrock — por padrão com `.`.
Comandos administrativos precisam do prefixo (`op .Fulano`, não `op Fulano`), senão falham em
silêncio. Como `planetas.render` e `planetas.reload` têm `default: op`, essa é a causa mais
provável de um jogador Bedrock não conseguir renderizar, e não uma incompatibilidade de fato.

**Restrição futura.** Qualquer recurso novo deve continuar server-side. Nada de item customizado
por resource pack, GUI de inventário exótica ou pacote de rede próprio — coisas que o Geyser
traduz mal ou não traduz. Se algum dia isso for necessário, esta decisão precisa ser revista
numa nova ADR.
