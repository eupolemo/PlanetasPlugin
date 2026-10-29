#!/usr/bin/env bash
# Compila e envia o jar para a pasta plugins/ do servidor.
#
# Destino local:
#   ./deploy.sh /caminho/do/servidor/plugins
# Destino remoto (scp):
#   ./deploy.sh usuario@servidor:/caminho/do/servidor/plugins
#
# Ou grave o destino em .deploy.env:  DEST=/caminho/do/servidor/plugins
set -euo pipefail

cd "$(dirname "$0")"

if [[ -f .deploy.env ]]; then
    # shellcheck disable=SC1091
    source .deploy.env
fi

DEST="${1:-${DEST:-}}"
if [[ -z "$DEST" ]]; then
    echo "erro: destino nao informado. Uso: ./deploy.sh <pasta-plugins|user@host:/pasta-plugins>" >&2
    exit 1
fi

mvn -B clean package

JAR="$(ls -t target/*.jar | head -1)"
echo "jar: $JAR"

if [[ "$DEST" == *:* ]]; then
    scp "$JAR" "$DEST/"
else
    [[ -d "$DEST" ]] || { echo "erro: pasta nao existe: $DEST" >&2; exit 1; }
    cp "$JAR" "$DEST/"
fi

echo "enviado para $DEST"
echo "reinicie o servidor para carregar (nao use /reload em producao)."
