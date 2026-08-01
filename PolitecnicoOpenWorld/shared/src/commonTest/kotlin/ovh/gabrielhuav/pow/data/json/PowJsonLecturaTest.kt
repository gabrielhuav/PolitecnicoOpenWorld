package ovh.gabrielhuav.pow.data.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fija la semántica de `org.json` que estos accesores reemplazan.
 *
 * **Por qué existe:** los JSON del juego (`combos.json`, `voice_phrases.json`,
 * `special_phrases.json`, los `*_anim.json` de escenarios) YA están escritos y llenos de campos
 * opcionales. Una diferencia de comportamiento aquí no rompe la compilación — se traduce en frases
 * que desaparecen, combos que no se enseñan o fondos mal recortados. Eso no lo ve ningún test de
 * `:app`, así que se fija aquí.
 */
class PowJsonLecturaTest {

    // ── Los opt* nunca lanzan; los get* sí fijan campos obligatorios ─────────

    @Test
    fun `un texto que no es JSON da objeto vacio en vez de reventar`() {
        assertTrue(powJsonObjeto("esto no es json").keys.isEmpty())
        assertTrue(powJsonObjeto("").keys.isEmpty())
        assertTrue(powJsonObjeto("[1,2,3]").keys.isEmpty()) // es un array, no un objeto
    }

    @Test
    fun `una clave que falta devuelve el default`() {
        val o = powJsonObjeto("""{"a":"hola"}""")
        assertEquals("", o.optString("noExiste"))
        assertEquals("puesto", o.optString("noExiste", "puesto"))
        assertEquals(7, o.optInt("noExiste", 7))
        assertEquals(9L, o.optLong("noExiste", 9L))
    }

    @Test
    fun `un null de JSON cuenta como ausente - lo mismo que hacia org punto json`() {
        val o = powJsonObjeto("""{"a":null}""")
        assertEquals("x", o.optString("a", "x"))
        assertEquals(3, o.optInt("a", 3))
    }

    @Test
    fun `un tipo que no encaja devuelve el default y no lanza`() {
        val o = powJsonObjeto("""{"obj":{"k":1},"arr":[1]}""")
        assertEquals("d", o.optString("obj", "d"))
        assertNull(o.optJSONObject("arr"))
        assertNull(o.optJSONArray("obj"))
    }

    // ── La coerción, que es lo fácil de perder al migrar ─────────────────────

    @Test
    fun `optString COERCIONA numeros y booleanos a texto`() {
        val o = powJsonObjeto("""{"n":12,"d":1.5,"b":true}""")
        assertEquals("12", o.optString("n"))
        assertEquals("1.5", o.optString("d"))
        assertEquals("true", o.optString("b"))
        assertEquals("12", o.getString("n"))
        assertFailsWith<PowJsonException> { o.getString("noExiste") }
    }

    @Test
    fun `optInt acepta el numero escrito como texto`() {
        val o = powJsonObjeto("""{"txt":"3","num":4}""")
        assertEquals(3, o.optInt("txt"))
        assertEquals(4, o.optInt("num"))
    }

    // ── getInt/getDouble SÍ lanzan, y eso es deliberado ──────────────────────

    @Test
    fun `getInt lanza si falta - el llamador cuenta con ello para caer al fallback`() {
        val o = powJsonObjeto("""{"cols":4}""")
        assertEquals(4, o.getInt("cols"))
        assertFailsWith<PowJsonException> { o.getInt("rows") }
    }

    @Test
    fun `getDouble lanza si el valor no es un numero`() {
        val o = powJsonObjeto("""{"fps":24.0,"malo":"x"}""")
        assertEquals(24.0, o.getDouble("fps"))
        assertFailsWith<PowJsonException> { o.getDouble("malo") }
    }

    // ── Arrays y recorrido de claves dinámicas ───────────────────────────────

    @Test
    fun `los arrays se leen por indice y fuera de rango da el default`() {
        val arr = powJsonObjeto("""{"a":["x",{"k":1}]}""").optJSONArray("a")!!
        assertEquals(2, arr.size)
        assertEquals("x", arr.optString(0))
        assertEquals("x", arr.getString(0))
        assertEquals(1, arr.optJSONObject(1)?.optInt("k"))
        assertEquals("", arr.optString(99))
        assertFailsWith<PowJsonException> { arr.getString(99) }
        assertNull(arr.optJSONObject(99))
    }

    @Test
    fun `las claves dinamicas se recorren en el ORDEN del fichero`() {
        // Importa: los básicos de `combos.json` se enseñan en el orden en que están escritos.
        val o = powJsonObjeto("""{"z":1,"a":2,"m":3}""")
        assertEquals(listOf("z", "a", "m"), o.keys.toList())
    }

    @Test
    fun `getJSONObject devuelve vacio en vez de lanzar - se usa tras recorrer las claves`() {
        val o = powJsonObjeto("""{"a":{"k":"v"}}""")
        assertEquals("v", o.getJSONObject("a").optString("k"))
        assertTrue(o.getJSONObject("noExiste").keys.isEmpty())
    }
}
