package ovh.gabrielhuav.pow.data.json

import kotlinx.serialization.json.Json

/**
 * 🍏 EL parser JSON del proyecto — sustituye a `Gson()` (Fase 3 de `PLAN_MIGRACION_KMP.md`).
 *
 * POR QUÉ: Gson usa reflexión de la JVM y **no existe en iOS**. Estaba en 20 archivos, 5 de ellos
 * el protocolo de red de "Huelum vs. Goya".
 *
 * ⚠️⚠️ **CADA OPCIÓN DE AQUÍ ESTÁ PUESTA PARA IMITAR A GSON, NO POR GUSTO.** Este JSON lee
 * **partidas guardadas de jugadores REALES** y habla con **clientes viejos** (los que ya tienen la
 * 1.0.0.14 instalada) y con los servidores Node. Los defaults de kotlinx NO son los de Gson, y
 * cambiarlos rompe cosas de forma SILENCIOSA. Si tocas una, rompes compatibilidad:
 *
 * | Opción | Default kotlinx | Aquí | Por qué |
 * |---|---|---|---|
 * | `ignoreUnknownKeys` | `false` (peta) | **`true`** | Gson ignora campos que no conoce. Sin esto, un cliente VIEJO que reciba un campo NUEVO **crashea**. |
 * | `encodeDefaults` | `false` (los omite) | **`true`** | Gson SIEMPRE escribe el valor. Sin esto, un save perdería los campos que coincidan con su default y el JSON cambiaría. |
 * | `explicitNulls` | `true` (escribe `null`) | **`false`** | Gson OMITE los nulos al serializar. Con `true` el JSON llevaría decenas de `"campo": null` que antes no iban. |
 * | `coerceInputValues` | `false` (peta) | **`true`** | Si un JSON viejo trae `null` donde ahora hay un tipo no-nulo, Gson ponía el default; kotlinx lanzaría excepción y la partida no cargaría. |
 * | `isLenient` | `false` | **`true`** | Gson parsea en modo laxo. Es la red y assets escritos a mano: mejor tolerar que crashear. |
 *
 * Regla práctica: **si un campo nuevo puede faltar en datos viejos, dale SIEMPRE un valor por
 * defecto en la data class.** Es lo que hacía Gson solo (rellenaba con null/0/false) y es lo que
 * mantiene vivas las partidas guardadas.
 */
val PowJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}
