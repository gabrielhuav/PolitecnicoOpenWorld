package ovh.gabrielhuav.pow.domain.models.map

import org.osmdroid.util.GeoPoint

/**
 * Contrato común de una estación de transporte público (Metro, Metrobús y futuros: Suburbano,
 * Mexibús, Tren Ligero…). Permite que la capa de transporte UNIFICADA (ver `TransitSystemConfig`)
 * trate cualquier estación de forma genérica sin depender del tipo concreto.
 *
 * `MetroStation` y `MetrobusStation` lo implementan (cambio ADITIVO: sus campos ya coincidían).
 * Añadir un transporte nuevo = un data class que implemente esta interfaz + una entrada en el
 * catálogo de `TransitSystemConfig`. NO se renombran ni eliminan los tipos existentes.
 */
interface TransitStation {
    val name: String
    val routes: List<String>
    val location: GeoPoint
}
