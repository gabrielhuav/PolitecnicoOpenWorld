# 🛡️ Play Store — Formulario de Seguridad de los datos + políticas (playbook)

> **Para qué:** que NUNCA vuelvan a rechazarnos por lo mismo. Aquí está EXACTAMENTE lo que
> pusimos en el formulario que Google aprobó (2026-07), qué recopila la app de verdad, los
> errores que cometimos y el checklist antes de cada envío. Léelo antes de tocar la ficha de
> Play o de subир una versión nueva.
>
> **App:** PolitecnicoOpenWorld · package `ovh.gabrielhuav.pow` · Desarrollador: Gabriel Hurtado Avilés
> **Contacto oficial de la ficha:** `gabrielhuav@gmail.com` (⚠️ NO `iiaragonii6@…`, ver §Errores).

---

## 1. Qué nos rechazó Play (correos del 2026-07-22) — 3 problemas distintos

Google mandó **5 correos** esa noche; son **3 problemas** reales:

| # | Issue (título del correo) | Detalle exacto de Google |
|---|---|---|
| 1 | **Invalid Privacy policy** | *"URL `…/policy_en_es.html` does not link to a valid privacy policy page, HTTP server is returning 404: Not Found"* |
| 2 | **Invalid account deletion link on your Data safety form** | *"URL `…/account-deletion.html` does not link to a valid page, HTTP server is returning 404: Not Found"* |
| 3 | **Invalid Data safety form** (×3 correos repetidos) | *"We detected user data transmitted off devices that you have not disclosed… Version code 1037: Personal Info Data Type - **Email Address**"* |

**Traducción:** (1 y 2) las dos páginas de políticas daban **404**; (3) la app manda el **correo**
del usuario fuera del dispositivo (vía Google Sign-In / Firebase) pero el formulario **no lo
declaraba**.

## 2. Causa raíz y ERRORES que cometimos (para no repetirlos)

- **💥 Se borró la rama `gh-pages` por accidente.** Esa rama alojaba `policy_en_es.html` **y**
  `account-deletion.html` (GitHub Pages servía desde ahí). Al borrarla → las dos URLs dieron 404
  → problemas #1 y #2. **Lección:** `gh-pages` es infraestructura de cumplimiento, no una rama
  cualquiera. Ahora está **protegida** (ver §5).
- **📧 Correo equivocado en las páginas.** Una versión traía `iiaragonii6@gmail.com`; el correcto
  es **`gabrielhuav@gmail.com`**. No fue motivo de rechazo, pero es un dato de cumplimiento: el
  correo de contacto/eliminación debe ser real y tuyo.
- **📋 Formulario incompleto.** No declaramos **Dirección de correo electrónico** (ni Nombre ni
  ID de usuario) aunque la app los recoge con Google Sign-In. Google lo **detecta automáticamente**
  (escanea los SDK); no sirve "no declarar y esperar que no se den cuenta".
- **🔁 Reenviamos 3 veces sin corregir la causa** → 3 rechazos iguales. **Lección:** lee el
  *detalle* del correo (dice el dato exacto: "Email Address") y corrige ESO antes de reenviar.

## 3. EXACTAMENTE qué pusimos en el formulario (valores aprobados) ✅

Ruta en Play Console: **Contenido de la app → Seguridad de los datos.**

### Paso 2 · Seguridad y recopilación de datos
- **¿Tu app recopila o comparte datos?** → **Sí**
- **¿Todos los datos se encriptan en tránsito?** → **Sí** (Firebase Auth = HTTPS; servidores en Render = WSS/TLS)
- **Método de creación de cuenta** → **OAuth** (inicio de sesión con Google / Firebase Auth)
- **URL de eliminación de cuenta** → `https://gabrielhuav.github.io/PolitecnicoOpenWorld/account-deletion.html`
- **¿Forma de borrar PARTE de los datos sin borrar la cuenta?** (opcional) → **No** (solo ofrecemos borrado total)
- **Insignias adicionales** (revisión de seguridad independiente / UPI) → **omitidas** (no aplican)

### Paso 3 · Tipos de datos — SOLO se marcó "Información personal" (3 de 9)
- ✅ **Dirección de correo electrónico**
- ✅ **Nombre**
- ✅ **ID de usuario** ← el UID de Firebase va aquí, en *Información personal*, **NO** en "Dispositivo u otros IDs".
- ❌ **Ubicación** (aproximada y precisa): **NO marcada** — ver §4 el porqué.
- ❌ Todo lo demás (Financiera, Salud, Mensajes, Fotos, Audio, Archivos, Calendario, Contactos,
  Actividad en apps, Navegación web, Info de app/rendimiento, Dispositivo u otros IDs): **NO**.

### Paso 4 · Uso y manejo — IDÉNTICO para los 3 (Correo, Nombre, ID de usuario)
- **¿Recopilados o compartidos?** → **Recopilados** (⚠️ NO "Compartidos" — Firebase es proveedor, no venta a terceros)
- **¿Procesamiento efímero?** → **No** (el UID/correo persisten mientras exista la cuenta)
- **¿Obligatorio u opcional?** → **Los usuarios pueden decidir** (opcional: solo al iniciar sesión para jugar en línea)
- **¿Con qué fin?** → **Funciones de la app** + **Administración de la cuenta** (y NADA más:
  sin publicidad, sin estadísticas, sin personalización, sin comunicaciones del desarrollador)

### Paso 5 · Vista previa — cómo se ve en la ficha (lo aprobado)
- **Datos compartidos:** *No se comparten datos con terceros.*
- **Datos recopilados → Información personal:** *Nombre, Dirección de correo electrónico, ID de usuario.*
- **Eliminación de datos:** *Borra la cuenta de la app* → `…/account-deletion.html`.
- **Prácticas de seguridad:** *Los datos están encriptados en tránsito.*
- **Política de Privacidad:** `https://gabrielhuav.github.io/PolitecnicoOpenWorld/policy_en_es.html`

## 4. Qué recopila la app DE VERDAD (base técnica del formulario)

Verificado contra el manifest y las dependencias (`app/build.gradle.kts`):

- **Correo + Nombre + UID:** vía **Firebase Auth (Google Sign-In)** (`firebase-auth-ktx`). Solo si
  el usuario inicia sesión para el multijugador; un jugador / Modo Historia funciona **sin cuenta**.
- **Ubicación (`ACCESS_FINE/COARSE_LOCATION` + `play-services-location` + Google Maps SDK):** se usa
  para **centrar el mapa en el dispositivo**. Por el código, el **GPS real no se transmite a nuestro
  servidor** (lo que viaja en multijugador son posiciones de juego para el render, no un rastreo del
  GPS). Por eso **NO se declara** ubicación — así el formulario queda **consistente con la política**
  (que dice "ubicación solo en el dispositivo, no para seguimiento"). El Google Maps SDK sí manda
  ubicación a Google, pero eso cae bajo las divulgaciones de **Google**, no las nuestras.
  ⚠️ **Si algún día mandas el GPS crudo a tu servidor, HAY que declarar Ubicación aproximada/precisa
  Y actualizar la política** para que coincidan.
- **Sin analíticas ni anuncios:** no hay Firebase Analytics/Crashlytics ni AdMob → no se declara
  "Estadísticas" ni "Publicidad". (Si algún día agregas Analytics/Crashlytics/ads, hay que declararlos.)
- **Encriptado en tránsito:** HTTPS (Firebase) + WSS (servidores Render).

> **Regla de oro de consistencia:** el **formulario**, la **política de privacidad** y lo que la
> app **realmente hace** deben decir lo mismo. Google compara los tres. Una contradicción = rechazo.

## 5. Las páginas de políticas (GitHub Pages) — hosting y protección

- **Rama:** `gh-pages` del repo `gabrielhuav/PolitecnicoOpenWorld`. GitHub Pages sirve desde
  ahí (**Settings → Pages → Deploy from a branch → gh-pages → /(root)**).
- **Archivos:** `policy_en_es.html` (política bilingüe ES/EN) y `account-deletion.html` (bilingüe).
- **URLs (las que van en Play):**
  - Política: `https://gabrielhuav.github.io/PolitecnicoOpenWorld/policy_en_es.html`
  - Eliminación de cuenta: `https://gabrielhuav.github.io/PolitecnicoOpenWorld/account-deletion.html`
- **La política declara** (debe seguir coincidiendo con el formulario): recolección de correo +
  nombre de Google + UID de Firebase **solo en funciones en línea**; ubicación **solo en el
  dispositivo**; datos de sesión transitorios; borrado de cuenta; cifrado WSS/HTTPS; contacto
  `gabrielhuav@gmail.com`. Requisitos de Google para el link de eliminación: nombra la app/dev,
  los pasos, y qué se borra/conserva (nuestra página los cumple).
- **🔒 PROTECCIÓN (para no repetir el borrado):** ruleset en **Settings → Rules** con target
  `gh-pages`, **Enforcement: Active**, Bypass vacío, y reglas ✅ **Restrict deletions** +
  ✅ **Block force pushes** (sin "Require PR" ni checks — no estorban las actualizaciones).
  Si algún día hay que borrarla a propósito: desactivar el ruleset un momento, borrar, reactivar.
- **Cómo actualizar la política:** editar el HTML y `git push origin gh-pages` (push normal;
  Pages reconstruye en ~1 min). **Verifica en el navegador que carga (200) antes de reenviar a Play.**

## 6. ✅ CHECKLIST antes de CADA envío a Play (pégalo mentalmente)

1. **Abre las 2 URLs en el navegador** y confirma que cargan (no 404):
   `policy_en_es.html` y `account-deletion.html`. (El 90% de nuestros rechazos fue esto.)
2. **Correo correcto** en ambas páginas: `gabrielhuav@gmail.com`.
3. **Formulario = realidad = política.** Si agregaste un SDK nuevo (analytics, ads, un backend que
   reciba más datos, ubicación al servidor…), **declara los datos nuevos** en Seguridad de los datos
   **y** actualiza la política.
4. **Data safety:** correo + nombre + UID declarados (Recopilados, opcional, Funciones + Admin de
   cuenta). Nada de "Compartidos" salvo que de verdad transfieras a terceros.
5. **`gh-pages` sigue protegida** (ruleset activo) — que nadie la borre.
6. **Lee el detalle del correo de rechazo** (dice el dato/URL exacto) y corrige ESO; no reenvíes a ciegas.
7. **No reenvíes varias veces seguidas** ni hagas más cambios mientras está "en revisión" (reinicia la cola).
8. Tras corregir: **Contenido de la app → guardar** → **Descripción general de la publicación → Enviar a revisión.**

## 7. Tiempos de revisión (referencia real)

- Aprobación normal / reenvío corregido: **~1–3 días hábiles** (a nosotros nos tomó **~2 días
  hábiles** el reenvío que sí pasó). En casos excepcionales hasta ~7.
- Corregir el formulario **no** alarga la revisión por sí mismo; lo que pesa es la cola de Google.
- Este cambio (declarar correo + URLs vivas) es de los más limpios → no dispara escrutinio extra.

## 8. Lo que despliega el CI a Play (contexto)

`.github/workflows/android-release.yml` sube el **AAB firmado a la pista `alpha` (prueba cerrada)**
al mergear a `main`. **Producción es promoción MANUAL** en Play Console (o cambiar `tracks: alpha`
→ `production`). El `versionCode` = `1000 + run_number` (siempre único y creciente). El `versionName`
**NO se autoincrementa de verdad** (el job `bump-version` no logra pushear a `main` protegida — ver §9).
La versión rechazada fue **code 1037**; la que pasó fue la promoción posterior.

## 9. 🆕 Gotchas de CI/CD: firma y versionado (2026-07-27)

Aprendido tras el fallo de firma del release **1.0.0.14**:

- **💥 Subir AGP puede romper la FIRMA del AAB.** El bump `agp 9.3.0 → 9.3.1` (PR #137) hizo fallar
  `:app:signReleaseBundle` (`FinalizeBundleTask$BundleToolRunnable`). El `bundletool` que firma el
  bundle viene **DENTRO de AGP**, así que cambiar de AGP cambia la herramienta de firma **sin tocar
  un solo secret**. **Revertir a `9.3.0` lo arregló.** Regla: **no subas AGP a la ligera**; aunque
  Android Studio lo sugiera y sea un parche (x.y.**z**), pruébalo en una rama con `workflow_dispatch`
  ANTES de mergear a `main`.
- **El fallo de firma NO es la versión.** `signReleaseBundle` corre DESPUÉS de estampar
  versionName/versionCode; un desajuste de versión solo puede fallar en el paso de **SUBIDA a Play**
  (code duplicado), nunca en la firma. Si falla la firma → mira keystore/AGP, no el número.
- **🔎 Diagnóstico integrado en el workflow:** antes de compilar valida el keystore con `keytool`
  (contraseña + alias) y corre `bundleRelease --stacktrace`, para que el error real salga a la luz en
  vez del genérico *"A failure occurred…"*.
- **⚠️ `bump-version` NO funciona con `main` protegida.** El job hace `git push origin main`, que la
  rama protegida rechaza con **403** (GitHub Actions no está en el Bypass list del ruleset). Por eso el
  `versionName` se quedó "atorado" en la versión ya publicada, y hay que **subirlo A MANO** en
  `app/build.gradle.kts` + `distribution/whatsnew/*` antes de cada release. Para automatizarlo: guarda
  un **`RELEASE_PAT`** (PAT del dueño, que SÍ está en el bypass; el workflow ya lo usa:
  `token: secrets.RELEASE_PAT || secrets.GITHUB_TOKEN`) **o** mete *GitHub Actions* al Bypass list.
- **🔑 Build MANUAL desde Android Studio — cuidado con el `versionCode`.** `build.gradle.kts` usa
  `versionCode = getenv("APP_VERSION_CODE") ?: 12`. En CI se inyecta `1000 + run_number` (p. ej. 1040+);
  en un build local NO existe esa env var → cae a **12**, MUY por debajo de lo ya subido, y **Play lo
  rechaza** ("el código de versión debe ser mayor que N"). Al firmar a mano: pon un `versionCode` MAYOR
  que el último de CI (edita el fallback o exporta `APP_VERSION_CODE`) y cambia el `versionName` a mano.
  El `RELEASE_PAT` **no** interviene aquí: es solo para el push del auto-bump del CI, no para firmar.
