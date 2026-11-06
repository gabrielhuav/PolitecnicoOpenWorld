package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MapProviderTest {
    @Test
    fun legacy_credentialed_settings_resolve_to_key_free_osm() {
        for (provider in listOf(MapProvider.CARTO_VOYAGER, MapProvider.CARTO_DB_DARK,
            MapProvider.CARTO_DB_LIGHT, MapProvider.GOOGLE_MAPS, MapProvider.GOOGLE_MAPS_NATIVE)) {
            assertEquals(MapProvider.OSM_WEB, provider.keyFreeProvider)
            assertEquals(MapProvider.OSM_WEB.webTileTemplate, provider.webTileTemplate)
            assertEquals(19, provider.tileMaxNativeZoom)
        }
    }

    @Test
    fun offered_providers_have_no_credentialed_tile_urls() {
        for (provider in MapProvider.entries.filter { it.keyFreeProvider == it }) {
            assertFalse(provider.webTileTemplate.contains("cartocdn"))
            assertFalse(provider.webTileTemplate.contains("google"))
        }
        assertEquals(17, MapProvider.OPEN_TOPO.tileMaxNativeZoom)
    }
}
