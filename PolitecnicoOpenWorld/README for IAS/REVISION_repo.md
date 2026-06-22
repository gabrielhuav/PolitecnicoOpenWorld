# REVISIÓN del repositorio completo — POW (informe, 2026-06-21)

> Revisión de TODO el repo (no solo el módulo Android). Carpeta externa
> `…\PolitecnicoOpenWorld\` (el módulo cuelga de `…\PolitecnicoOpenWorld\PolitecnicoOpenWorld\`).
> **Solo reporte + limpiezas triviales.** Servidores Node: SOLO revisión, sin tocar lógica.

---

## 0. TL;DR — lo importante primero

* ✅ **SEGURIDAD OK:** el keystore de firma `llave_pow.jks` **NUNCA** se commiteó (verificado en
  155 commits / 11 ramas con `git log --all -- '*.jks'` → vacío). **No está comprometido.** No hay
  que purgar historia ni rotar la llave.
* ✅ `.gitignore` raíz es **completo y correcto** (cubre `*.jks`, `google-services.json`,
  `secrets.properties`, `*.env`, `build/`, `.idea/`, `local.properties`, service-accounts).
* ✅ **Sin secretos hardcodeados** en código Kotlin ni en los `server.js`/`auth.js` (todo por `process.env`).
* ✅ El prompt viejo `PROMPT_nueva_sesion.md` **YA NO existe** en la raíz (el duplicado ya se eliminó).
  Solo queda el bueno: `README for IAS/PROMPT_nueva_optimizacion.md`.
* ⚠️ **2 hardening menores en los servidores** (no urgentes): `cors()` abierto a `*` y `AUTH_REQUIRED`
  con default `false`. Ver §3.

---

## 1. Raíz del repo

| Item | Estado | Recomendación |
|------|--------|---------------|
| `README.md` (77 KB) | **Bilingüe EN+ES**, es el README PÚBLICO de GitHub. **NO es redundante** con `README for IAS` (eso es contexto interno para IAs, otro público). | **Mantener ambos.** Solo revisar que el README público no tenga datos desfasados (última edición 16-jun; no refleja `domain.models.map`, parciales nuevos ni i18n). Actualización de bajo riesgo cuando quieras. |
| `LICENSE` | Presente. | OK. |
| `account-deletion.html` (5.7 KB) | Página de borrado de cuenta (requisito Google Play). Legítima. Editada 21-jun. | OK. Coincide con `settings_privacy_url` / flujo `deleteAccount`. |
| `.github/workflows/android-release.yml` | CI de release. | OK. Revisar que los secretos del workflow (keystore/firebase) vengan de GitHub Secrets, NO del repo. |
| `.idea/` | En la raíz EXTERNA. El `.gitignore` raíz YA ignora `.idea/` (con excepción de `codeStyles/` y `runConfigurations/`). | OK, no se versiona basura del IDE. |
| `.gitattributes` | Presente (68 B). Probablemente fija `* text=auto` o CRLF. | Revisar que fije CRLF/eol coherente con la regla del proyecto (archivos fuente CRLF). |
| `gradle*/build.gradle.kts/gradle.properties/settings.gradle.kts` | En el módulo anidado. | OK. |
| `verificar-repo.ps1` | Script de verificación (en el módulo anidado, 6.5 KB). | Útil; mantener. |
| **Estructura anidada** `PolitecnicoOpenWorld/PolitecnicoOpenWorld/` | El módulo Android cuelga un nivel más abajo que la raíz git. | Es intencional (raíz git arriba, proyecto AS abajo). No tocar; solo recordarlo al navegar. |

**Cruft / archivos sueltos:** no encontré archivos sueltos sospechosos en la raíz. Limpio.

---

## 2. Servidores Node — SOLO revisión (NO refactor)

`Multiplayer/` (open world) y `MultiplayerInteriores/` (interiores) son sistemas APARTE; cliente⇄servidor
deben coincidir (09 §11). NO se tocó nada.

### Estructura (idéntica en ambos)
* `server.js` (Multiplayer 508 líneas, Interiores 816), `auth.js` (68), `package.json`, `Dockerfile`,
  `docker-compose.yml`.
* Deps limpias y al día: `cors ^2.8.5`, `express ^4.19.2`, `firebase-admin ^12.7.0`, `uuid ^9.0.1`, `ws ^8.16.0`.

### Seguridad
* ✅ **Sin secretos hardcodeados.** Credenciales por `process.env` (`FIREBASE_SERVICE_ACCOUNT` o
  `GOOGLE_APPLICATION_CREDENTIALS`). El service account vive como variable en Render, no en el repo (09 §8).
* ✅ Verificación de token Firebase en el handshake (`auth.js`: `extractToken` → `verifyIdToken`,
  cabecera `Authorization: Bearer <token>` o `?token=`).
* ⚠️ **Hardening 1 — CORS abierto:** `app.use(cors())` sin opciones = permite CUALQUIER origen (`*`).
  En un server WS con auth por token el riesgo es bajo, pero conviene restringir `origin` a los dominios
  propios si algún día sirves HTTP sensible. (No urgente.)
* ⚠️ **Hardening 2 — `AUTH_REQUIRED` default false:** `const AUTH_REQUIRED = process.env.AUTH_REQUIRED === 'true'`.
  Si la variable NO está puesta a `'true'` en producción, el server **acepta clientes sin autenticar**.
  **Acción recomendada (deploy, no código):** confirmar que en Render `AUTH_REQUIRED=true` y que el
  service account está configurado. (El código es defensivo a propósito para dev local; el riesgo es
  de configuración de entorno.)

### Coincidencia cliente⇄servidor (matrices/ids)
No la audité byte a byte (fuera de alcance "solo revisión" + riesgo de cambiar nada). **Recordatorio:**
mantener `ROOMS`/matrices/ids de `MultiplayerInteriores/server.js` en sync con el cliente
(`ZombieRoomCatalog`, tipos/colisiones) — ver 09 §11. Si tocas uno, toca el otro.

---

## 3. 🔐 Seguridad — conclusión consolidada

| Riesgo potencial | Resultado | Acción |
|------------------|-----------|--------|
| Keystore `*.jks` en historia git | **NO** (verificado, 155 commits/11 ramas) | Ninguna. La llave está a salvo. |
| `google-services.json` / `secrets.properties` en historia | **NO** | Ninguna. |
| Secretos hardcodeados (código + servers) | **NO** | Ninguna. |
| `.gitignore` incompleto | **NO** (completo) | Ninguna. |
| CORS abierto en servers | Sí (`*`) | Hardening opcional (restringir origin). |
| `AUTH_REQUIRED` default false | Sí | Verificar `AUTH_REQUIRED=true` en prod (config Render). |
| `.gitattributes` eol | Revisar | Confirmar política CRLF. |

**No hay nada que purgar de la historia ni rotar.** El único trabajo de seguridad real es de
**configuración de despliegue** (variables de entorno de los servidores), no de código.

---

## 4. Organización general / `.gitignore`

* `.gitignore` raíz y `.gitignore` del módulo (anidado) son ambos válidos (el del módulo es más
  específico). No hay artefactos de build versionados que detectara.
* No se requieren movimientos/renombrados de docs sueltos: `README for IAS/` está bien organizado
  (00–09 + planes + los 3 docs nuevos de esta sesión).

---

## 5. Limpiezas aplicadas esta sesión
* **Ninguna destructiva.** El único candidato (borrar el prompt viejo) **ya estaba hecho** (el archivo
  no existe). No se borró ni movió nada del repo.

## 6. Recomendaciones (no aplicadas — tú decides)
1. **(Deploy)** Confirmar `AUTH_REQUIRED=true` + service account en Render para AMBOS servidores. *(Seguridad real.)*
2. **(Opcional)** Restringir `cors()` a orígenes propios en los dos `server.js`.
3. **(Opcional)** Revisar `.gitattributes` para fijar EOL coherente (CRLF en fuentes).
4. **(Bajo)** Pasar por el README público (77 KB) para reflejar el estado actual (`domain.models.map`,
   parciales, i18n) — es de cara al público de GitHub.
