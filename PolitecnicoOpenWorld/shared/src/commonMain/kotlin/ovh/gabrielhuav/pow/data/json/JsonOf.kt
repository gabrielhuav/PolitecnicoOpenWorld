package ovh.gabrielhuav.pow.data.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 🍏 Sustituto EXACTO de `gson.toJson(mapOf(...))` para los mensajes de red (Fase 3 de
 * `PLAN_MIGRACION_KMP.md`).
 *
 * POR QUÉ EXISTE: el código construía los mensajes de red como `Map<String, Any?>` heterogéneos
 * (`mapOf("type" to "NPC_DESTROY", "npcId" to id)`) y se los pasaba a Gson. **kotlinx.serialization
 * NO puede serializar `Map<String, Any?>`**: necesita conocer los tipos en compilación. Reescribir
 * los 17 sitios a mano a `buildJsonObject { … }` habría multiplicado el diff — y cada línea tocada
 * es una oportunidad de romper el formato de cable.
 *
 * ⚠️⚠️ **ESTE JSON LO LEEN CLIENTES QUE YA ESTÁN INSTALADOS (1.0.0.14) Y LOS SERVIDORES NODE.**
 * El formato tiene que salir **byte a byte** igual que el de Gson. Dos detalles que NO son obvios y
 * que están replicados a propósito:
 *  1. **Gson OMITE las claves cuyo valor es `null`** al serializar un Map (`serializeNulls` es
 *     false por defecto). Aquí se hace igual: una pareja con valor null NO se escribe. Si esto
 *     emitiera `"clave":null`, un cliente viejo podría interpretarlo distinto que "ausente".
 *  2. Los `enum` viajan como su `.name`, que es lo que hacía Gson.
 *
 * `JsonObjectCompatGsonTest` compara la salida de esta función contra la de Gson de verdad. Si
 * tocas algo aquí y ese test se pone rojo, **has cambiado el protocolo**.
 */
fun jsonOf(vararg pares: Pair<String, Any?>): String = buildJsonObject {
    for ((clave, valor) in pares) {
        if (valor == null) continue // Gson omite los nulos en los mapas: ver punto 1 de arriba.
        put(clave, aElemento(valor))
    }
}.toString()

/**
 * Igual que [jsonOf] pero con el mapa ya construido. Lo usan los transportes de SF, que reciben el
 * payload hecho (`send(payload: Map<String, Any?>)`) y antes hacían `gson.toJson(payload)`.
 */
fun jsonOf(mapa: Map<String, Any?>): String = jsonOf(*mapa.toList().toTypedArray())

/**
 * Array JSON a partir de una lista heterogénea — el equivalente de `gson.toJson(listOf(mapOf(...)))`.
 *
 * ⚠️ Lo usa el **mapa WEB**, que es el renderer POR DEFECTO del juego: los payloads que se
 * inyectan al WebView (`updateNpcs`, `updateMetro`, `updatePolice`…) son `List<Map<String, Any>>`.
 * `PowJson.encodeToString` sobre eso COMPILA pero revienta en runtime con
 * *"Serializer for class 'Any' is not found"*, porque kotlinx necesita los tipos en compilación.
 * Ese fallo se coló en la Fase 3 y lo cazó la auditoría: no lo "simplifiques" de vuelta.
 */
fun jsonArrayOf(lista: List<Any?>): String =
    JsonArray(lista.map { aElemento(it) }).toString()

/** Convierte un valor suelto al árbol JSON. Solo acepta lo que el protocolo usa de verdad. */
private fun aElemento(valor: Any?): JsonElement = when (valor) {
    null -> JsonPrimitive(null as String?)
    is JsonElement -> valor // ya convertido por quien llama (p. ej. una lista @Serializable)
    is String -> JsonPrimitive(valor)
    is Boolean -> JsonPrimitive(valor)
    is Number -> JsonPrimitive(valor)
    is Enum<*> -> JsonPrimitive(valor.name)
    is List<*> -> JsonArray(valor.map { aElemento(it) })
    is Map<*, *> -> JsonObject(
        valor.entries
            .filter { it.value != null }
            .associate { it.key.toString() to aElemento(it.value) },
    )
    else -> error(
        "jsonOf no sabe convertir ${valor::class.simpleName}. Si es un modelo, márcalo " +
            "@Serializable y pásalo ya convertido con PowJson.encodeToJsonElement(...).",
    )
}
