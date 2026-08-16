package ovh.gabrielhuav.pow.data.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * 🍏 Lectura de JSON "suelto" en `commonMain` — el reemplazo de `org.json`, que es de la JVM y **no
 * existe en iOS** (paso 1 de `PLAN_SF_EN_iOS.md` §4).
 *
 * ## Por qué es una CAPA DE COMPATIBILIDAD y no una reescritura
 *
 * Este port ya se intentó una vez y **se revirtió**: pasar los ficheros de SF a la API cruda de
 * kotlinx obliga a reescribir cada acceso, y cada uno es una oportunidad de cambiar el
 * comportamiento **en silencio** sobre ficheros de datos del juego que ya existen.
 *
 * Por eso estas funciones se llaman IGUAL que las de `org.json` y **replican su semántica exacta**:
 * los 5 archivos que las usan solo cambian el `import`. Lo que se lee es el mismo JSON, con los
 * mismos defaults, y un fallo se nota al compilar, no en la cara del jugador.
 *
 * ## Las 3 reglas de `org.json` que aquí se respetan a propósito
 *
 * 1. **Los `opt*` nunca lanzan.** Si falta la clave, el tipo no encaja o el valor es `null`,
 *    devuelven el default. `org.json` se comporta así y los JSON del juego dependen de ello (hay
 *    campos opcionales por todas partes: `phrase_en`, `hintEs`, `tier`…). Los `get*`, en cambio,
 *    lanzan porque se usan para campos obligatorios y el llamador ya está dentro de `runCatching`.
 * 2. **`optString` COERCIONA.** Un número o un booleano se leen como su texto (`12` → `"12"`).
 *    kotlinx no lo hace solo; si no se imitara, cualquier campo numérico escrito sin comillas
 *    volvería al default sin avisar.
 * 3. **`optInt` acepta el número escrito como texto** (`"3"` → `3`), igual que `org.json`.
 *
 * ⚠️ **NO "simplifiques" esto a `decodeFromString<T>()`.** Estos JSON tienen claves DINÁMICAS (el
 * nombre de cada peleador, el nombre de cada clip de voz): no hay data class que los describa. Es
 * el mismo motivo por el que existen `jsonOf`/`jsonArrayOf` en vez de `encodeToString`.
 */

/** Parsea un texto a objeto JSON. Devuelve un objeto VACÍO si el texto no es un objeto válido. */
fun powJsonObjeto(texto: String): JsonObject =
    runCatching { PowJson.parseToJsonElement(texto).jsonObject }.getOrElse { JsonObject(emptyMap()) }

/** Las claves del objeto. Equivale a `JSONObject.keys()`. */
fun JsonObject.claves(): Set<String> = keys

/** `JSONObject.optJSONObject(key)`: el objeto hijo, o `null` si falta o no es un objeto. */
fun JsonObject.optJSONObject(key: String): JsonObject? = this[key] as? JsonObject

/** `JSONObject.optJSONArray(key)`: el array hijo, o `null` si falta o no es un array. */
fun JsonObject.optJSONArray(key: String): JsonArray? = this[key] as? JsonArray

/**
 * `JSONObject.getJSONObject(key)` pero SIN lanzar: devuelve un objeto vacío si no está.
 * En las llamadas actuales siempre se usa sobre una clave que acaba de salir de `claves()`.
 */
fun JsonObject.getJSONObject(key: String): JsonObject = optJSONObject(key) ?: JsonObject(emptyMap())

/** `JSONObject.optString(key, fallback)`, con la coerción de números y booleanos a texto. */
fun JsonObject.optString(key: String, fallback: String = ""): String =
    (this[key] as? JsonPrimitive)?.contentOrNull ?: fallback

/** `JSONObject.getString(key)` — LANZA si falta o el valor es `null`. */
fun JsonObject.getString(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull
        ?: throw PowJsonException("falta el texto '$key' o es null")

/** `JSONObject.optInt(key, fallback)`. Acepta el entero escrito como texto, igual que `org.json`. */
fun JsonObject.optInt(key: String, fallback: Int = 0): Int =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: fallback

/** `JSONObject.optLong(key, fallback)`. Acepta el número escrito como texto. */
fun JsonObject.optLong(key: String, fallback: Long = 0L): Long =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: fallback

/** `JSONObject.optDouble(key, fallback)`. */
fun JsonObject.optDouble(key: String, fallback: Double = 0.0): Double =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: fallback

/** `JSONObject.optBoolean(key, fallback)`. */
fun JsonObject.optBoolean(key: String, fallback: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: fallback

/** `JSONObject.has(key)`. */
fun JsonObject.has(key: String): Boolean = containsKey(key)

/**
 * `JSONObject.getInt(key)` — **LANZA si falta o no es un número, igual que `org.json`.**
 *
 * ⚠️ Que lance NO es un descuido: los dos únicos sitios que lo usan (`SfSceneRenderer` y
 * `SfStageSelectOverlay`, para leer `cols`/`rows`/`fps` del atlas de un escenario) están dentro de
 * un `runCatching`. Si esto devolviera un default, un JSON corrupto pintaría el fondo MAL en vez de
 * caer limpiamente al escenario estático de reserva.
 */
fun JsonObject.getInt(key: String): Int =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        ?: throw PowJsonException("falta el entero '$key' o no es un número")

/** `JSONObject.getDouble(key)` — LANZA igual que `org.json`; mismo motivo que [getInt]. */
fun JsonObject.getDouble(key: String): Double =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
        ?: throw PowJsonException("falta el decimal '$key' o no es un número")

/** El equivalente de `org.json.JSONException`, que tampoco existe fuera de la JVM. */
class PowJsonException(mensaje: String) : Exception(mensaje)

/** `JSONArray.optString(index)`, con la misma coerción que su hermana de objeto. */
fun JsonArray.optString(index: Int, fallback: String = ""): String =
    (getOrNull(index) as? JsonPrimitive)?.contentOrNull ?: fallback

/** `JSONArray.getString(index)` — LANZA si el índice no existe o el valor es `null`. */
fun JsonArray.getString(index: Int): String =
    (getOrNull(index) as? JsonPrimitive)?.contentOrNull
        ?: throw PowJsonException("falta el texto en el índice $index o es null")

/**
 * `JSONArray.optLong(index, fallback)`. Añadida al portar `OverpassRepository`: la lista de nodos
 * de una vía es un array de ids `Long` puros (`"nodes": [123, 456]`), sin objetos de por medio.
 */
fun JsonArray.optLong(index: Int, fallback: Long = 0L): Long =
    (getOrNull(index) as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: fallback

/** `JSONArray.optJSONObject(index)`: el objeto en esa posición, o `null`. */
fun JsonArray.optJSONObject(index: Int): JsonObject? = getOrNull(index) as? JsonObject

/** `JSONArray.optJSONArray(index)`: el array en esa posición, o `null`. */
fun JsonArray.optJSONArray(index: Int): JsonArray? = getOrNull(index) as? JsonArray

private fun JsonArray.getOrNull(index: Int): JsonElement? =
    if (index in 0 until size) this[index] else null
