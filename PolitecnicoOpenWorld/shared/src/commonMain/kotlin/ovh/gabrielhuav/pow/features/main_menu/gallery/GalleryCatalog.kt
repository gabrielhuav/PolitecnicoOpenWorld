package ovh.gabrielhuav.pow.features.main_menu.gallery

import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStageCatalog
import ovh.gabrielhuav.pow.platform.assets.PowAssetsFuente

enum class GalleryCategory { FIGHTERS, MAPS }

enum class GallerySource { FIGHTERS, STAGES, INTERIORS, TRANSIT }

enum class GalleryLighting { DAY, NIGHT, APOCALYPSE }

data class GalleryImage(
    val assetPath: String,
    val previewPath: String = assetPath,
    // Los atlas de combate se representan con su primera celda, nunca encogidos enteros.
    val portraitCellSize: Int? = null,
)

data class GalleryItem(
    val id: String,
    val category: GalleryCategory,
    val title: String,
    val source: GallerySource,
    val images: List<GalleryImage>,
    val lighting: GalleryLighting? = null,
)

data class GalleryState(
    val items: List<GalleryItem> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
)

/** Catálogo de los recursos empaquetados. No cambia inventario, desbloqueos ni partidas. */
object GalleryCatalog {
    private const val FIGHTER_IMAGES = "STREETFIGHTER/IMAGES/"
    private val imageExtensions = setOf("webp", "png", "jpg", "jpeg")

    // Llamar en segundo plano: listar assets también hace E/S.
    fun load(assets: PowAssetsFuente): List<GalleryItem> = fromPaths(listImages(assets, ""))

    /** Función pura: permite auditar cobertura sin cargar imágenes ni depender de Android. */
    fun fromPaths(paths: List<String>): List<GalleryItem> {
        val images = paths.filter { it.substringAfterLast('.').lowercase() in imageExtensions }.distinct().sorted()
        val remaining = images.toMutableSet()
        val items = mutableListOf<GalleryItem>()

        SfFighterId.entries.forEach { fighter ->
            val shared = fighter.sharedSet
            val frames = if (shared == null) {
                images.filter { it == fighter.spriteAsset }.map { GalleryImage(it, portraitCellSize = 256) }
            } else {
                // Estos peleadores arman su atlas en runtime; mostramos sus fotogramas fuente.
                val directories = listOf("Idle", "Walk", "Run", "Special").map { "${shared.basePath}${shared.folder}$it/" }
                images.filter { path -> directories.any { path.startsWith(it) } }
                    .sortedWith(compareBy<String> { !isIdle(it) }.thenBy { naturalKey(it) })
                    .map { GalleryImage(it) }
            }
            if (frames.isNotEmpty()) {
                items += GalleryItem(
                    "fighter:${fighter.name}", GalleryCategory.FIGHTERS, fighter.displayName,
                    GallerySource.FIGHTERS, frames,
                )
                remaining.removeAll(frames.map { it.assetPath }.toSet())
            }
        }

        // Un atlas de escenario y su miniatura son UNA imagen; se reutiliza la miniatura existente.
        remaining.filter { it.startsWith(FIGHTER_IMAGES + "fondo_") && !it.endsWith("_thumb.webp") }
            .forEach { path ->
                val thumbnail = path.substringBeforeLast('.') + "_thumb.webp"
                items += stageItem(path, if (thumbnail in images) thumbnail else path)
                remaining.remove(path)
                remaining.remove(thumbnail)
            }
        // Incluye también arte alternativo que solo existe como miniatura (no está en ALL_STAGES).
        remaining.filter { it.startsWith(FIGHTER_IMAGES + "fondo_") }.forEach { path ->
            items += stageItem(path, path)
            remaining.remove(path)
        }

        remaining.forEach { path ->
            val source = when {
                path.startsWith("INTERIORS/") -> GallerySource.INTERIORS
                path.startsWith("TRANSIT/") && path.substringAfterLast('/') in setOf("map.webp", "mapa.webp", "inside.webp") -> GallerySource.TRANSIT
                else -> null
            }
            if (source != null) {
                items += GalleryItem(path, GalleryCategory.MAPS, artworkTitle(path), source, listOf(GalleryImage(path)))
            }
        }
        return items.sortedWith(compareBy<GalleryItem> { it.category.ordinal }.thenBy { it.title.lowercase() }.thenBy { it.id })
    }

    private fun stageItem(path: String, preview: String): GalleryItem {
        val file = path.substringAfterLast('/')
        val stage = SfStageCatalog.stageForFile(file.removeSuffix("_thumb.webp") + if (file.endsWith("_thumb.webp")) ".webp" else "")
        val lighting = when {
            "_noche_2" in file -> GalleryLighting.APOCALYPSE
            "_noche_1" in file -> GalleryLighting.NIGHT
            else -> GalleryLighting.DAY
        }
        val title = stage?.displayName ?: readableName(
            file.substringBeforeLast('.').removePrefix("fondo_").removeSuffix("_thumb")
                .removeSuffix("_anim").replace("_noche_1", "").replace("_noche_2", ""),
        )
        return GalleryItem(path, GalleryCategory.MAPS, title, GallerySource.STAGES, listOf(GalleryImage(path, preview)), lighting)
    }

    private fun listImages(assets: PowAssetsFuente, directory: String): List<String> =
        assets.listar(directory).sorted().flatMap { name ->
            val path = if (directory.isEmpty()) name else "$directory/$name"
            when {
                name.substringAfterLast('.').lowercase() in imageExtensions -> listOf(path)
                '.' !in name -> listImages(assets, path)
                else -> emptyList()
            }
        }

    private fun isIdle(path: String): Boolean = path.contains("idle", ignoreCase = true) && path.endsWith("_1.webp")

    private fun naturalKey(path: String): String = Regex("\\d+").replace(path) { it.value.padStart(6, '0') }

    private fun artworkTitle(path: String): String {
        val parts = path.substringBeforeLast('.').split('/')
        return readableName(parts.drop(if (parts.first() == "SPRITES") 2 else 1).joinToString(" "))
    }

    private fun readableName(name: String): String = name
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .replace('_', ' ').replace('-', ' ').replace('/', ' ').replace(Regex("\\s+"), " ").trim()
}
