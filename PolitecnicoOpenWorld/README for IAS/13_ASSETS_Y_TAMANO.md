# 🗜️ Assets y tamaño: qué se sube a cada tienda

**Actualizado:** 2026-07-30 · Los pesos salen de `:app:bundleRelease` y de `du`, no de estimaciones.

> **La regla en una línea:** el repositorio puede pesar lo que haga falta; **lo que se sube a las
> tiendas, no**. Si un formato solo lo entiende una plataforma, se guardan los dos en git y **cada
> build se lleva el suyo**.

---

## 1. Los límites REALES de cada tienda

Verificado en la documentación de Apple y de Google el 2026-07-30. **No son lo que se suele
repetir por ahí**, así que conviene tenerlos a mano.

### 🍏 App Store

| Límite | Valor | Qué pasa si te pasas |
|---|---|---|
| **Tamaño máximo de la app (sin comprimir)** | **4 GB** | Rechazo al subir |
| Secciones `__TEXT` del binario | 80 MB | Rechazo (es código, no assets: no nos afecta) |
| **Descarga por datos móviles** | **200 MB** | ⚠️ **NO es un rechazo.** Desde iOS 13 el usuario decide: puede permitir siempre, o que le pregunten. Es fricción, no un muro. |

> ### ✅ Para un juego, el App Store NO es el problema
>
> POW con el mundo abierto entero serían **~399 MB**: eso es **el 10 % del límite de 4 GB**.
> Cabe de sobra. Lo único que pasa es que quien lo instale con datos móviles verá un aviso.
>
> ⚠️ **Corrección a lo que decía el doc 12 el 07-30:** ahí puse que el mundo "no cabe" en iOS por
> los 200 MB. **Era demasiado alarmista.** El límite duro son 4 GB; los 200 MB son un aviso que el
> usuario puede desactivar desde iOS 13. On-Demand Resources sigue siendo *deseable* (mejora la
> conversión de instalación), pero **no es un bloqueador** para publicar.

### 🤖 Google Play — **aquí sí aprieta**

| Límite | Valor |
|---|---|
| **Descarga del módulo base** | **500 MB** ← el que nos afecta |
| Total de módulos + asset packs de instalación | 4 GB |
| Un asset pack suelto | 1,5 GB |
| On-demand / fast-follow (suma) | 30 GB |
| Aviso al usuario con datos móviles | 200 MB |
| Apps de más de 1 GB | exigen `minSdk` ≥ 21 |

**El tope de 500 MB del módulo base es exactamente donde está puesta la barrera del CI.** No fue
casualidad: es el número de Google.

> **Conclusión, y es al revés de lo que parece:** el que manda es **Play**, no el App Store.
> Optimizar assets es una necesidad de Android; para iOS es solo una cortesía.

---

## 2. Dónde está el peso (MEDIDO, AAB de release 2026-07-30)

**AAB = 416 MB.** Margen hasta el tope: **84 MB.**

| Carpeta | MB | % |
|---|---:|---:|
| `assets/STREETFIGHTER` | 112,2 | 27,0 % |
| `assets/SPRITES` | 105,7 | 25,4 % |
| `assets/AUDIO` | 45,8 | 11,0 % |
| `assets/BUILDINGS` | 31,3 | 7,5 % |
| `assets/INTERIORS` | 23,6 | 5,7 % |
| resto (STORY, TRANSIT, PLACES, libs, dex, res) | 97 | 23 % |

⚠️ **El código no pesa: el `dex` entero son 6,8 MB.** Mover archivos de `:app` a `:shared` no
cambia el AAB. Lo que sí lo cambiaría es **duplicar assets**: si algún día `:shared` empaquetara
sus propios recursos del mundo, acabarían dos veces. **Los assets se quedan en `app/src/main/assets/`.**

### Por formato

| Formato | Archivos | Peso |
|---|---:|---:|
| `.webp` | 1 789 | 202,3 MB |
| **`.png`** | **142** | **83,5 MB** ← sin convertir |
| `.m4a` | 85 | 7,6 MB |
| `.mp3` | 21 | 19,0 MB |
| `.wav` | 3 | 25,2 MB |
| `.mp4` | 2 | 6,4 MB |

---

## 3. 🎧 Qué formato de audio va en cada sitio

**El formato lo decide QUIÉN usa el archivo, no una preferencia.**

| Quién lo usa | Formato | Por qué |
|---|---|---|
| **Solo Android** (música de fondo del mundo) | **`.ogg`** (Vorbis) | Pesa menos que AAC y, sobre todo, **empalma sin hueco en los bucles** |
| **Android + iOS** (voces y SFX de la pelea) | **`.m4a`** (AAC) | ⚠️ **iOS no puede leer Ogg**: `AVAudioPlayer` no trae el códec. Android sí lee AAC. Un solo archivo sirve para los dos. |

### ⚠️ El detalle que hace falta entender: el bucle

La música de fondo se reproduce con `isLooping = true` (ver `SoundManager.kt`). **AAC mete unas
muestras de relleno** al principio y al final del archivo — es cómo funciona el códec — y en un
bucle eso se puede oír **como un clic en la costura**. Ogg Vorbis no tiene ese problema.

Por eso la respuesta no es "un formato para cada tienda" sino:

> **Ogg para lo que solo suena en Android. `.m4a` para lo que suena en las dos.**
> Y sí: **los dos archivos pueden convivir en git.** Lo que no puede es que los dos entren en el
> mismo build.

### 💡 Y no hacen falta dos versiones de todo

Los **85 `.m4a` de la pelea pesan 7,6 MB entre todos**. Duplicarlos a Ogg ahorraría 1 o 2 MB y
costaría dos pipelines, dos juegos de archivos y un mecanismo de selección en el build.
**No sale a cuenta.** El trabajo rinde donde está el peso: los PNG y la música de fondo.

---

## 4. Lo que se hizo y lo que queda

### ✅ Hecho el 2026-07-30

**Las 3 pistas de música pasaron de 24 a 16 bits: 37,8 MB → 25,2 MB (−12,6 MB).**

Eran PCM de **24 bits a 44,1 kHz**, o sea másters de estudio metidos crudos en el juego. Se pasaron
a 16 bits, que es calidad CD.

> **Esto no cambia nada de lo que se oye, y no es una opinión:** la salida de audio de Android es
> de 16 bits, así que esos 24 bits **ya se estaban recortando en tiempo de ejecución** en cada
> reproducción. Se comprobó que las tres duran exactamente lo mismo que antes.
>
> Se hizo así, y no a Ogg, porque **en el Mac no hay `ffmpeg` ni `oggenc`** (medido) y no se puede
> escuchar el resultado. Un cambio de formato en música que va en bucle hay que oírlo.

### 🔜 Pendiente — en una máquina con `ffmpeg` y `cwebp`

```bash
bash tools/optimizar_assets_produccion.sh --dry-run   # dice cuánto ahorraría
bash tools/optimizar_assets_produccion.sh             # lo aplica
```

| Tarea | Ahorro estimado | Riesgo |
|---|---:|---|
| **142 PNG → WebP** | **~60 MB** | Bajo. El proyecto ya usa WebP en 1 789 archivos. ⚠️ Hay que cambiar las rutas en Kotlin. |
| **3 BGM → Ogg** | **~23 MB** | Medio. ⚠️ **Hay que ESCUCHAR el bucle** y cambiar 3 rutas en `SoundManager.kt`. |

Las dos juntas dejarían el AAB en torno a **330 MB**: margen de 170 MB en vez de 84.

⚠️ **Ninguna de las dos la comprueba un test.** El audio hay que oírlo y las imágenes hay que verlas.

---

## 5. La barrera del CI

`.github/workflows/android-release.yml`, paso *"Validar tamaño máximo de Play Store"*:

| Umbral | Qué hace |
|---|---|
| **450 MB** | ⚠️ Avisa (`::warning`). Aviso temprano: quedan menos de 50 MB. |
| **500 MB** | ❌ Falla el build. Es el límite real de Google para el módulo base. |

Y en **cada** run publica el desglose por carpeta en el resumen. Así, cuando el AAB crezca, se sabe
**qué carpeta lo hizo crecer** sin tener que reproducirlo en local.

---

## Fuentes

- [Apple — Maximum build file sizes](https://developer.apple.com/help/app-store-connect/reference/maximum-build-file-sizes/) (4 GB, 80 MB de `__TEXT`)
- [Google Play — Download size limits](https://support.google.com/googleplay/android-developer/answer/9859372) (500 MB de módulo base, 4 GB acumulado)
- [Apple sube el límite móvil a 200 MB](https://appleinsider.com/articles/19/05/31/apple-bumps-up-4g-app-store-download-limit-for-iphones-ipads-to-200mb) y [iOS 13 permite saltárselo](https://techcrunch.com/2019/06/03/ios-13-will-let-you-bypass-the-app-store-download-cap-when-on-a-cellular-connection)
