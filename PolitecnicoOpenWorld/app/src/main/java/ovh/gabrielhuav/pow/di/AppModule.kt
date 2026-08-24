package ovh.gabrielhuav.pow.di

import ovh.gabrielhuav.pow.data.local.room.getInstance

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ovh.gabrielhuav.pow.data.cache.RoadNetworkCache
import ovh.gabrielhuav.pow.data.cache.TileCache
import ovh.gabrielhuav.pow.data.local.room.PowDatabase
import ovh.gabrielhuav.pow.data.repository.CampaignRepository
import ovh.gabrielhuav.pow.data.repository.CollectibleRepository
import ovh.gabrielhuav.pow.data.repository.SaveGameRepository
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.AndroidWorldMapEnvironment
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapEnvironment
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────────
// ETAPA 4 (DI con Hilt, ver PLAN_DI_hilt.md): módulo ÚNICO de aplicación. Provee las deps
// singleton que ANTES construían los Factory manuales de cada ViewModel: la BD Room (vía su
// getInstance existente), los caches derivados de sus DAOs y los repositorios basados en
// SharedPreferences (@ApplicationContext). Los ViewModels ahora las reciben por @Inject.
//
// NOTA: se usa `PowDatabase.getInstance(ctx)` (no `Room.databaseBuilder` directo) para NO
// duplicar la construcción de la BD (migración MIGRATION_7_8 + fallback ya viven ahí). SoundManager
// y OverpassRepository NO se proveen: el primero se usa como singleton por getInstance() en Views/VM,
// y el segundo lo instancia el WorldMapViewModel internamente (no es dep de constructor).
// ─────────────────────────────────────────────────────────────────────────────
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePowDatabase(@ApplicationContext context: Context): PowDatabase =
        PowDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideRoadNetworkCache(db: PowDatabase): RoadNetworkCache =
        RoadNetworkCache(db.roadNetworkDao())

    /**
     * Lo que el mundo abierto necesita de Android, detrás de una interfaz de `commonMain`.
     * Ver `WorldMapEnvironment`: es lo que permitió sacar el `Context` del ViewModel del mundo.
     */
    @Provides
    @Singleton
    fun provideWorldMapEnvironment(@ApplicationContext context: Context): WorldMapEnvironment =
        AndroidWorldMapEnvironment(context)

    @Provides
    @Singleton
    fun provideTileCache(db: PowDatabase): TileCache =
        TileCache(db.mapTileDao())

    @Provides
    @Singleton
    fun provideCollectibleRepository(db: PowDatabase): CollectibleRepository =
        CollectibleRepository(db.collectibleDao())

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)

    @Provides
    @Singleton
    fun provideCampaignRepository(@ApplicationContext context: Context): CampaignRepository =
        CampaignRepository(context)

    @Provides
    @Singleton
    fun provideSaveGameRepository(@ApplicationContext context: Context): SaveGameRepository =
        SaveGameRepository(context)
}
