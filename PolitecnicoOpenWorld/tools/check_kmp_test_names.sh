#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# 🍏 GUARDA: nombres de test que Kotlin/Native (iOS) RECHAZA.
#
# POR QUÉ EXISTE: en `commonTest` un nombre entre backticks con `(`, `)` o `,`
# COMPILA EN LA JVM y **falla en Kotlin/Native**:
#     e: ...Test.kt:62 Name contains illegal characters: ",()".
#
# Eso es invisible desde Windows y Linux, donde Kotlin/Native no compila iOS. Sin esta
# guarda, el único modo de enterarse es abrir un Mac — un viaje de ida y vuelta por un
# nombre de función. Ya pasó DOS veces (21 casos el 2026-07-27 y 4 más el mismo día,
# estos últimos escritos con el gotcha ya documentado en 09 §🍏 KMP/iOS).
#
# Documentar el gotcha no bastó: nada lo impedía mecánicamente. Esto sí.
#
# Uso:  tools/check_kmp_test_names.sh [directorio]
#       (por defecto: shared/src/commonTest)
# Sale 1 si encuentra algo, 0 si está limpio.
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

DIR="${1:-shared/src/commonTest}"

if [ ! -d "$DIR" ]; then
  echo "check_kmp_test_names: no existe el directorio '$DIR'" >&2
  exit 2
fi

# Nombres de función entre backticks que contengan ( ) o , en cualquier posición.
HALLAZGOS="$(grep -rnE 'fun `[^`]*[(),][^`]*`' "$DIR" --include='*.kt' || true)"

if [ -n "$HALLAZGOS" ]; then
  echo "❌ Nombres de test que Kotlin/Native (iOS) RECHAZA — quita los ( ) , de los backticks:"
  echo ""
  echo "$HALLAZGOS"
  echo ""
  echo "Compilan en la JVM pero rompen 'compileTestKotlinIosSimulatorArm64' con:"
  echo "    Name contains illegal characters"
  echo "Ver 09_CONVENTIONS_GOTCHAS.md, sección KMP/iOS."
  exit 1
fi

echo "✅ Nombres de test compatibles con Kotlin/Native en '$DIR'."
exit 0
