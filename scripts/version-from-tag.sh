#!/usr/bin/env bash
# Calcule le numéro de version Android à partir d'une étiquette Git vX.Y.Z.
#   v1.0.0  -> versionName 1.0.0, versionCode 10000
set -euo pipefail
tag="${1:?étiquette manquante, par exemple v1.0.0}"
name="${tag#v}"
if [[ ! "$name" =~ ^([0-9]+)\.([0-9]+)(\.([0-9]+))?$ ]]; then
  echo "Étiquette « $tag » invalide : attendu vX.Y ou vX.Y.Z" >&2
  exit 1
fi
major="${BASH_REMATCH[1]}"; minor="${BASH_REMATCH[2]}"; patch="${BASH_REMATCH[4]:-0}"
if (( minor > 99 || patch > 99 )); then
  echo "Mineur et correctif doivent rester sous 100 (reçu $name)" >&2
  exit 1
fi
echo "VERSION_NAME=$name"
echo "VERSION_CODE=$(( major * 10000 + minor * 100 + patch ))"
