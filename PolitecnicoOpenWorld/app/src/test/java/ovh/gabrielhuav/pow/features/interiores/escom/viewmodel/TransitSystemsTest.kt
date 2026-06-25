package ovh.gabrielhuav.pow.features.interiores.escom.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de lógica PURA (sin Android) para el catálogo [TransitSystems].
 *
 * `TransitSystemConfig` declara un lambda `loadStations: (Context) -> ...`, pero el lambda NO se
 * invoca al construir el catálogo (los repos no se inicializan), así que leer los campos de
 * `TransitSystems.METRO/.METROBUS` es lógica pura. Estos tests BLINDAN datos que fueron bugfixes
 * reales (sprite del jugador del Metrobús que estaba en una carpeta VACÍA) y los getters derivados.
 */
class TransitSystemsTest {

    @Test
    fun player_sprite_dir_points_to_real_sprites_for_both_systems() {
        // Bugfix: METROBUS usaba "PRINCIPAL/" (VACÍO) → jugador invisible. Ambos usan los reales.
        assertEquals("SPRITES/PLAYER/", TransitSystems.METRO.spriteBaseDir)
        assertEquals("SPRITES/PLAYER/", TransitSystems.METROBUS.spriteBaseDir)
    }

    @Test
    fun key_and_prefs_prefix_are_consistent() {
        assertEquals("metro", TransitSystems.METRO.key)
        assertEquals("metrobus", TransitSystems.METROBUS.key)
        assertTrue(TransitSystems.METRO.prefsPrefix.startsWith(TransitSystems.METRO.key))
        assertTrue(TransitSystems.METROBUS.prefsPrefix.startsWith(TransitSystems.METROBUS.key))
    }

    @Test
    fun derived_asset_and_prefs_paths_are_built_from_prefixes() {
        val m = TransitSystems.METRO
        assertEquals("TRANSIT/METRO/matrix.json", m.matrixAsset)
        assertEquals("TRANSIT/METRO/waypoints.json", m.waypointsAsset)
        assertEquals("TRANSIT/METRO/global_waypoints.json", m.globalWaypointsAsset)
        assertEquals("metro_station_Tacuba", m.stationPrefsName("Tacuba"))
        assertEquals("metro_map_global", m.mapGlobalPrefsName)
        assertEquals("metro_station_interior", m.stationRoutePrefix)
    }

    @Test
    fun map_image_filenames_differ_by_system() {
        // Gotcha conocido: metro = map.png, metrobús = mapa.png.
        assertEquals("TRANSIT/METRO/map.png", TransitSystems.METRO.mapImage)
        assertEquals("TRANSIT/METROBUS/mapa.png", TransitSystems.METROBUS.mapImage)
    }

    @Test
    fun turnstile_board_delta_has_opposite_signs() {
        assertTrue(TransitSystems.METRO.turnstileBoardDeltaY > 0f)
        assertTrue(TransitSystems.METROBUS.turnstileBoardDeltaY < 0f)
    }

    @Test
    fun animation_and_overlay_match_each_system() {
        assertEquals(TransitAnimationAxis.VERTICAL, TransitSystems.METRO.animationAxis)
        assertEquals(TransitAnimationAxis.HORIZONTAL, TransitSystems.METROBUS.animationAxis)
        assertEquals(TransitOverlayType.VIDEO, TransitSystems.METRO.overlayType)
        assertEquals(TransitOverlayType.GRADIENT, TransitSystems.METROBUS.overlayType)
    }

    @Test
    fun metro_uses_video_overlay_asset_metrobus_does_not() {
        assertEquals("TRANSIT/METRO/video.mp4", TransitSystems.METRO.overlayVideoAsset)
        assertEquals(null, TransitSystems.METROBUS.overlayVideoAsset)
    }
}
