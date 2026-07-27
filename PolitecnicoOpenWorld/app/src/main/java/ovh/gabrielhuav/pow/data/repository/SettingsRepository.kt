package ovh.gabrielhuav.pow.data.repository

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import com.russhwolf.settings.Settings
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin
import ovh.gabrielhuav.pow.features.settings.models.ControlType

class SettingsRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "pow_game_settings"
        private const val KEY_CONTROL_TYPE = "CONTROL_TYPE"
        private const val KEY_CONTROLS_SCALE = "CONTROLS_SCALE"
        private const val KEY_SWAP_CONTROLS = "SWAP_CONTROLS"
        private const val KEY_PLAYER_SKIN = "PLAYER_SKIN"

        private const val KEY_SHOW_ROAD_NETWORK = "SHOW_ROAD_NETWORK"
        private const val SCALE_MIN = 0.6f
        private const val SCALE_MAX = 1.4f

        // ─── Jugabilidad: población de NPCs ──────────────────────────────────
        private const val KEY_NPC_DENSITY = "NPC_DENSITY"        // multiplicador 0.4–1.6
        private const val KEY_NPC_EMOJI_LOD = "NPC_EMOJI_LOD"    // NPCs lejanos como emoji (optimizar dibujado)
        private const val KEY_NPC_FULL_EMOJI = "NPC_FULL_EMOJI"  // TODOS los NPCs como emoji (gama baja)
        private const val KEY_SHOW_ZOOM_WIDGET = "SHOW_ZOOM_WIDGET" // widget de nivel de zoom (Interfaz)
        private const val KEY_SHOW_SPEEDOMETER = "SHOW_SPEEDOMETER"  // widget velocímetro al conducir (Interfaz)
        private const val KEY_SHOW_COORDS_WIDGET = "SHOW_COORDS_WIDGET" // widget de coordenadas X/Y/Z (Interfaz)
        private const val KEY_DEVELOPER_MODE = "DEVELOPER_MODE" // Modo Desarrollador (Interfaz): muestra botones/opciones de prueba
        private const val KEY_SHOW_HITBOXES = "SHOW_HITBOXES" // 🆕 dibuja las hitboxes del modo pelea (estilo Minecraft)
        private const val KEY_SHOW_SF_FPS = "SHOW_SF_FPS" // 🆕 contador de FPS en el modo pelea
        private const val KEY_SHOW_VOICE_SUBTITLES = "SHOW_VOICE_SUBTITLES" // 🆕 subtítulos de voces (default OFF)
        private const val KEY_SHOW_WORLD_SHOULDERS = "SHOW_WORLD_SHOULDERS" // 🆕 gatillos L/R en el mundo (default OFF)
        private const val KEY_MUSIC_VOLUME = "MUSIC_VOLUME" // volumen música 0f..1f (Audio)
        private const val KEY_SFX_VOLUME = "SFX_VOLUME"     // volumen efectos 0f..1f (Audio)
        private const val KEY_LANGUAGE = "APP_LANGUAGE"             // idioma de la UI (BCP-47; "" = sistema)
        private const val KEY_PLAYER_NAME = "PLAYER_NAME"           // nombre de jugador para multijugador
        // Tutorial de controles (optativo): ¿ya se le OFRECIÓ al jugador? (una vez por mundo;
        // siempre re-visible desde Ajustes → Controles).
        private const val KEY_TUTORIAL_EXTERIOR_SEEN = "TUTORIAL_EXTERIOR_SEEN"
        private const val KEY_TUTORIAL_INTERIOR_SEEN = "TUTORIAL_INTERIOR_SEEN"
        const val NPC_DENSITY_MIN = 0.4f
        const val NPC_DENSITY_MAX = 1.6f
    }

    // 🍏 Fase 4: `Settings` (multiplatform-settings) en vez de tocar `SharedPreferences` directo.
    // ⚠️ ENVUELVE EL MISMO FICHERO de siempre (`PREFS_NAME`), así que **los ajustes que ya tiene
    // el jugador se conservan tal cual**: no hay migración de datos ni se pierde nada. El día que
    // esta clase se mueva a `:shared`, lo único que cambia es quién construye el `Settings`
    // (en iOS, `NSUserDefaultsSettings`).
    private val settings: Settings =
        SharedPreferencesSettings(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    // Por defecto el LOD de emojis se activa SOLO en gama baja (se puede cambiar en Ajustes).
    private val lowRamDefault: Boolean = try {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice
    } catch (e: Exception) { false }

    // ─── Controles ───────────────────────────────────────────────────────

    fun saveControlsSettings(type: ControlType, scale: Float, swap: Boolean) {
        val clampedScale = scale.coerceIn(SCALE_MIN, SCALE_MAX)
        settings.putString(KEY_CONTROL_TYPE, type.name)
        settings.putFloat(KEY_CONTROLS_SCALE, clampedScale)
        settings.putBoolean(KEY_SWAP_CONTROLS, swap)
    }

    fun getControlType(): ControlType {
        val defaultType = ControlType.JOYSTICK
        val saved = settings.getString(KEY_CONTROL_TYPE, defaultType.name)
        return runCatching { ControlType.valueOf(saved) }.getOrElse {
            settings.putString(KEY_CONTROL_TYPE, defaultType.name)
            defaultType
        }
    }

    fun getControlsScale(): Float = settings.getFloat(KEY_CONTROLS_SCALE, 1.0f).coerceIn(SCALE_MIN, SCALE_MAX)

    fun getSwapControls(): Boolean = settings.getBoolean(KEY_SWAP_CONTROLS, false)

    // ─── Skin del jugador ────────────────────────────────────────────────

    /** Persiste la skin elegida entre sesiones. */
    fun savePlayerSkin(skin: PlayerSkin) {
        settings.putString(KEY_PLAYER_SKIN, skin.name)
    }

    /** Devuelve la skin guardada, o LAZARO si no hay ninguna o es inválida. */
    fun getPlayerSkin(): PlayerSkin {
        val saved = settings.getString(KEY_PLAYER_SKIN, PlayerSkin.LAZARO.name)
        return runCatching { PlayerSkin.valueOf(saved) }.getOrElse { PlayerSkin.LAZARO }
    }

    // ─── Red vial ────────────────────────────────────────────────────────

    fun saveShowRoadNetwork(show: Boolean) {
        settings.putBoolean(KEY_SHOW_ROAD_NETWORK, show)
    }

    fun getShowRoadNetwork(): Boolean = settings.getBoolean(KEY_SHOW_ROAD_NETWORK, true)

    // ─── Jugabilidad: población de NPCs ──────────────────────────────────────

    /** Multiplicador de densidad de NPCs elegido por el usuario (se combina con gama/ciudad). */
    fun getNpcDensity(): Float = settings.getFloat(KEY_NPC_DENSITY, 1.0f).coerceIn(NPC_DENSITY_MIN, NPC_DENSITY_MAX)
    fun saveNpcDensity(v: Float) {
        settings.putFloat(KEY_NPC_DENSITY, v.coerceIn(NPC_DENSITY_MIN, NPC_DENSITY_MAX))
    }

    /** ¿Dibujar los NPCs lejanos como emoji ("Optimizar dibujado de NPCs")? Default = isLowRamDevice. */
    fun getNpcEmojiLod(): Boolean = settings.getBoolean(KEY_NPC_EMOJI_LOD, lowRamDefault)
    fun saveNpcEmojiLod(enabled: Boolean) {
        settings.putBoolean(KEY_NPC_EMOJI_LOD, enabled)
    }

    /** ¿Dibujar TODOS los NPCs como emoji ("Optimizar para gama baja")? Default = false. */
    fun getNpcFullEmoji(): Boolean = settings.getBoolean(KEY_NPC_FULL_EMOJI, false)
    fun saveNpcFullEmoji(enabled: Boolean) {
        settings.putBoolean(KEY_NPC_FULL_EMOJI, enabled)
    }

    // ─── Interfaz: widget de nivel de zoom ───────────────────────────────────

    fun getShowZoomWidget(): Boolean = settings.getBoolean(KEY_SHOW_ZOOM_WIDGET, false)
    fun saveShowZoomWidget(show: Boolean) {
        settings.putBoolean(KEY_SHOW_ZOOM_WIDGET, show)
    }

    // ─── Interfaz: velocímetro (visible solo al conducir). Default = activado. ──

    fun getShowSpeedometer(): Boolean = settings.getBoolean(KEY_SHOW_SPEEDOMETER, true)
    fun saveShowSpeedometer(show: Boolean) {
        settings.putBoolean(KEY_SHOW_SPEEDOMETER, show)
    }

    // ─── Interfaz: widget de coordenadas (X/Y/Z, global e interiores). Default = oculto. ──

    fun getShowCoordsWidget(): Boolean = settings.getBoolean(KEY_SHOW_COORDS_WIDGET, false)
    fun saveShowCoordsWidget(show: Boolean) {
        settings.putBoolean(KEY_SHOW_COORDS_WIDGET, show)
    }

    // ─── Interfaz: Modo Desarrollador. Default = desactivado. ──
    // Cuando está activo, la UI revela botones/opciones de prueba que se ocultarán
    // en la versión final del juego.

    fun getDeveloperMode(): Boolean = settings.getBoolean(KEY_DEVELOPER_MODE, false)
    fun saveDeveloperMode(enabled: Boolean) {
        settings.putBoolean(KEY_DEVELOPER_MODE, enabled)
    }

    // ─── 🆕 Modo pelea: dibujar HITBOXES (push/hurt/hit) sobre los peleadores, estilo
    // Minecraft (F3+B). Default = desactivado. Útil para ver dónde "vive" cada asset. ──

    fun getShowHitboxes(): Boolean = settings.getBoolean(KEY_SHOW_HITBOXES, false)
    fun saveShowHitboxes(enabled: Boolean) {
        settings.putBoolean(KEY_SHOW_HITBOXES, enabled)
    }

    // ─── 🆕 (2026-07-25) Modo pelea: contador de FPS en pantalla (como ya existía en el mundo
    // abierto). Default = desactivado. Útil para medir la fluidez en gama baja. ──
    fun getShowSfFps(): Boolean = settings.getBoolean(KEY_SHOW_SF_FPS, false)
    fun saveShowSfFps(enabled: Boolean) {
        settings.putBoolean(KEY_SHOW_SF_FPS, enabled)
    }

    // ─── 🆕 (2026-07-25) Subtítulos de las VOCES de los peleadores (frases de special/win/etc.).
    // Default = APAGADO (decisión del dueño). El jugador los prende en Ajustes si los quiere. ──
    fun getShowVoiceSubtitles(): Boolean = settings.getBoolean(KEY_SHOW_VOICE_SUBTITLES, false)
    fun saveShowVoiceSubtitles(enabled: Boolean) {
        settings.putBoolean(KEY_SHOW_VOICE_SUBTITLES, enabled)
    }

    // ─── 🆕 (2026-07-26) GATILLOS L1/L2/R1/R2 en el MUNDO ABIERTO. Nacieron en el modo pelea,
    // donde cada uno tiene su acción (parry/burla/agarre/súper). En el mundo abierto TODAVÍA no
    // hacen nada, así que el default es APAGADO: quien no los prenda no ve ningún botón nuevo.
    // ⚠️ Cuando se les dé función, quitar el aviso "aún sin función" de la descripción. ──
    fun getShowWorldShoulderButtons(): Boolean =
        settings.getBoolean(KEY_SHOW_WORLD_SHOULDERS, false)
    fun saveShowWorldShoulderButtons(enabled: Boolean) {
        settings.putBoolean(KEY_SHOW_WORLD_SHOULDERS, enabled)
    }

    // ─── Tutorial de controles (optativo): se ofrece UNA vez al entrar por primera vez al
    // mapa exterior / a un interior; después queda disponible en Ajustes → Controles. ──

    fun getTutorialExteriorSeen(): Boolean = settings.getBoolean(KEY_TUTORIAL_EXTERIOR_SEEN, false)
    fun saveTutorialExteriorSeen() {
        settings.putBoolean(KEY_TUTORIAL_EXTERIOR_SEEN, true)
    }

    fun getTutorialInteriorSeen(): Boolean = settings.getBoolean(KEY_TUTORIAL_INTERIOR_SEEN, false)
    fun saveTutorialInteriorSeen() {
        settings.putBoolean(KEY_TUTORIAL_INTERIOR_SEEN, true)
    }

    // ─── Audio: volumen de música y efectos (0f..1f). Default = 1.0 (máximo). ──

    fun getMusicVolume(): Float = settings.getFloat(KEY_MUSIC_VOLUME, 1.0f).coerceIn(0f, 1f)
    fun saveMusicVolume(v: Float) {
        settings.putFloat(KEY_MUSIC_VOLUME, v.coerceIn(0f, 1f))
    }

    fun getSfxVolume(): Float = settings.getFloat(KEY_SFX_VOLUME, 1.0f).coerceIn(0f, 1f)
    fun saveSfxVolume(v: Float) {
        settings.putFloat(KEY_SFX_VOLUME, v.coerceIn(0f, 1f))
    }

    // ─── Idioma / i18n ───────────────────────────────────────────────────────
    // Etiqueta BCP-47 del idioma de la UI ("es", "en", …). "" = seguir el idioma
    // del sistema. Se aplica envolviendo el Context en MainActivity.attachBaseContext
    // (ver i18n/LocaleHelper.kt); al cambiarlo se recrea la Activity.

    fun getLanguage(): String = settings.getString(KEY_LANGUAGE, "")
    fun saveLanguage(tag: String) {
        settings.putString(KEY_LANGUAGE, tag)
    }

    // ─── Nombre de jugador (multijugador) ────────────────────────────────────
    // Se recuerda entre sesiones para no reescribirlo cada vez. Al iniciar sesión con
    // Google, si está vacío se prellena con el nombre de la cuenta.

    fun getPlayerName(): String = settings.getString(KEY_PLAYER_NAME, "")
    fun savePlayerName(name: String) {
        settings.putString(KEY_PLAYER_NAME, name)
    }
}
