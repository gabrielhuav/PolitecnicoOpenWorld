package ovh.gabrielhuav.pow.domain.platform

/**
 * iOS arranca solo con el modo pelea (+ ajustes y coleccionables).
 * El catálogo real está en `modosDe()`, en `commonMain`: aquí solo se dice DÓNDE estamos.
 */
actual val plataformaActual: PowPlataforma = PowPlataforma.IOS
