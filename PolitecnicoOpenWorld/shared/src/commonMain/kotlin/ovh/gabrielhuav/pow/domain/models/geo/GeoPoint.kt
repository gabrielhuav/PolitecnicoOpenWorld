package ovh.gabrielhuav.pow.domain.models.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 🍏 Punto lat/lon PROPIO — reemplaza a `org.osmdroid.util.GeoPoint` (Fase 2 de
 * `README for IAS/PLAN_MIGRACION_KMP.md`).
 *
 * POR QUÉ EXISTE: osmdroid aparecía en 51 archivos, pero **45 de ellos solo lo usaban como DATO**
 * (medido: 419 `.latitude` + 418 `.longitude` frente a 14 usos de geometría real). No necesitaban
 * un mapa, necesitaban una pareja de `double`. Con este tipo, esos 45 archivos dejan de depender de
 * osmdroid y pueden viajar a iOS; osmdroid queda SOLO en los 6 archivos que dibujan de verdad.
 *
 * ⚠️ SE LLAMA IGUAL (`GeoPoint`) A PROPÓSITO: así el cambio en esos 45 archivos fue **solo la línea
 * del `import`**, sin tocar ninguno de los ~837 sitios de uso. Menos diff = menos donde equivocarse,
 * y lo verifica el compilador. En los archivos que SÍ dibujan conviven los dos tipos: ahí el de
 * osmdroid se importa con alias (ver `GeoPointInterop.kt`).
 *
 * ⚠️ LAS FÓRMULAS SON LAS DE OSMDROID, COPIADAS AL PIE DE LA LETRA — no una haversine "equivalente"
 * sacada de internet. Las distancias mandan en el juego (aggro de NPCs, radios de interacción,
 * colisiones, rutas): cualquier desviación cambia el comportamiento. `RADIUS_EARTH_METERS` es el
 * MISMO valor que usa osmdroid (6378137, radio ECUATORIAL WGS84, no el radio medio 6371000).
 * `GeoPointParidadOsmdroidTest` (en `:app`, donde sí está osmdroid) compara ambas implementaciones.
 *
 * Notas de equivalencia con el tipo de osmdroid:
 * - `equals`: el de osmdroid TAMBIÉN compara lat/lon (+alt); un `data class` es equivalente. Además
 *   POW no mete GeoPoints en `Set`/`Map` ni los compara con `==` (verificado), así que da igual.
 * - `altitude`: osmdroid la tiene, POW **nunca** la usa (0 referencias) → no se replica.
 * - MUTABILIDAD: el de osmdroid es mutable (`setLatitude`…); POW **nunca** lo muta (verificado),
 *   así que aquí es inmutable, que es lo correcto.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    /**
     * Distancia en metros. Port EXACTO de `GeoPoint.distanceToAsDouble` de osmdroid.
     * El `min(1.0, …)` no es adorno: evita que un error de redondeo meta un valor >1 en `asin`
     * (que daría NaN) cuando los dos puntos son prácticamente el mismo.
     *
     * ⚠️ **`.pow(2)` y NO `x * x`, aunque parezcan lo mismo.** Se probó con `x * x` y el test de
     * paridad lo cazó: daba 9204804.584314916 donde osmdroid da 9204804.584314918 (2 ULP en 9200 km
     * — irrelevante para el juego, pero delata que NO es la misma operación en punto flotante).
     * Con `.pow(2)`, que es lo que hace osmdroid (`Math.pow`), sale **bit a bit** idéntico.
     * No lo "simplifiques" a `x * x`: el test volverá a fallar.
     */
    fun distanceToAsDouble(other: GeoPoint): Double {
        val lat1 = DEG2RAD * latitude
        val lat2 = DEG2RAD * other.latitude
        val lon1 = DEG2RAD * longitude
        val lon2 = DEG2RAD * other.longitude
        return RADIUS_EARTH_METERS * 2 * asin(
            min(
                1.0,
                sqrt(
                    sin((lat2 - lat1) / 2).pow(2) +
                        cos(lat1) * cos(lat2) * sin((lon2 - lon1) / 2).pow(2),
                ),
            ),
        )
    }

    companion object {
        /** Radio ECUATORIAL WGS84, el mismo que `GeoConstants.RADIUS_EARTH_METERS` de osmdroid. */
        const val RADIUS_EARTH_METERS: Double = 6378137.0

        /** `MathConstants.DEG2RAD` de osmdroid = PI/180. */
        const val DEG2RAD: Double = kotlin.math.PI / 180.0
    }
}
