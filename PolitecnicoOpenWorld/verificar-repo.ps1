# ============================================
# Verificador y protector de archivos sensibles
# ============================================
# Para verificar y proteger:
#   PowerShell -ExecutionPolicy Bypass -File .\verificar-repo.ps1
#
# Para restaurar los archivos despues:
#   PowerShell -ExecutionPolicy Bypass -File .\verificar-repo.ps1 -Restaurar
# ============================================

param(
    [switch]$Restaurar
)

$carpetaSegura = "$(Get-Location)\_sensitive_backup"
$archivoLog = "$carpetaSegura\ubicaciones-originales.txt"

# ============================================
# MODO RESTAURAR
# ============================================
if ($Restaurar) {
    Write-Host ""
    Write-Host "=======================================" -ForegroundColor Cyan
    Write-Host "  Restaurando archivos sensibles..." -ForegroundColor Cyan
    Write-Host "=======================================" -ForegroundColor Cyan
    Write-Host ""

    if (-not (Test-Path $archivoLog)) {
        Write-Host "No se encontro el archivo de log en:" -ForegroundColor Red
        Write-Host "   $archivoLog" -ForegroundColor Red
        exit
    }

    $lineas = Get-Content $archivoLog | Where-Object { $_ -match "^ORIGEN:" }

    foreach ($linea in $lineas) {
        $origen = $linea -replace "^ORIGEN: ", ""
        $nombreArchivo = Split-Path $origen -Leaf
        $backup = "$carpetaSegura\$nombreArchivo"

        if (Test-Path $backup) {
            $carpetaDestino = Split-Path $origen -Parent
            if (-not (Test-Path $carpetaDestino)) {
                New-Item -ItemType Directory -Path $carpetaDestino -Force | Out-Null
            }
            Move-Item -Path $backup -Destination $origen -Force
            Write-Host "Restaurado: $origen" -ForegroundColor Green
        } else {
            Write-Host "No se encontro el backup de: $nombreArchivo" -ForegroundColor Yellow
        }
    }

    Write-Host ""
    Write-Host "=======================================" -ForegroundColor Cyan
    Write-Host "  Restauracion completada." -ForegroundColor Cyan
    Write-Host "=======================================" -ForegroundColor Cyan
    Write-Host ""
    exit
}

# ============================================
# MODO VERIFICAR Y PROTEGER
# ============================================

Write-Host ""
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host "  Verificando archivos sensibles..." -ForegroundColor Cyan
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host ""

$archivosProblema = @(
    "*.jks",
    "*.keystore",
    "key.properties",
    "keystore.properties",
    "google-services.json",
    "*service-account*.json",
    "*.env",
    ".env.*",
    "secrets.properties",
    "api-*.json",
    "play-store-api.json"
)

$encontrados = @()

foreach ($patron in $archivosProblema) {
    $resultados = Get-ChildItem -Recurse -Include $patron -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -notlike "*_sensitive_backup*" }
    if ($resultados) {
        foreach ($archivo in $resultados) {
            $encontrados += $archivo.FullName
        }
    }
}

if ($encontrados.Count -gt 0) {
    Write-Host "ATENCION: Se encontraron $($encontrados.Count) archivo(s) sensible(s)." -ForegroundColor Yellow
    Write-Host ""

    # Crear carpeta de backup dentro del proyecto
    if (-not (Test-Path $carpetaSegura)) {
        New-Item -ItemType Directory -Path $carpetaSegura -Force | Out-Null
    }

    # Crear log
    "# Log de archivos movidos - $(Get-Date)" | Out-File $archivoLog -Encoding UTF8
    "" | Out-File $archivoLog -Append -Encoding UTF8

    foreach ($archivo in $encontrados) {
        $nombreArchivo = Split-Path $archivo -Leaf
        $destino = "$carpetaSegura\$nombreArchivo"

        # Guardar ubicacion original en log
        "ARCHIVO: $nombreArchivo" | Out-File $archivoLog -Append -Encoding UTF8
        "ORIGEN: $archivo" | Out-File $archivoLog -Append -Encoding UTF8
        "DESTINO: $destino" | Out-File $archivoLog -Append -Encoding UTF8
        "" | Out-File $archivoLog -Append -Encoding UTF8

        # Mover archivo
        Move-Item -Path $archivo -Destination $destino -Force
        Write-Host "Movido: $nombreArchivo" -ForegroundColor Yellow
        Write-Host "  Desde: $archivo" -ForegroundColor Gray
        Write-Host "  Hacia: $destino" -ForegroundColor Gray
        Write-Host ""

        # Quitar del tracking de Git
        $rutaRelativa = $archivo -replace [regex]::Escape((Get-Location).Path + "\"), "" -replace "\\", "/"
        git rm --cached $rutaRelativa 2>$null
    }

    Write-Host "Log guardado en: $archivoLog" -ForegroundColor Cyan
    Write-Host ""

    # Commit automatico
    git commit -m "Remove sensitive files from tracking" 2>$null
    Write-Host "Commit realizado: sensitive files removed" -ForegroundColor Green

} else {
    Write-Host "No se encontraron archivos sensibles." -ForegroundColor Green
}

# ============================================
# VERIFICAR GIT TRACKING
# ============================================

Write-Host ""
Write-Host "---------------------------------------" -ForegroundColor Cyan
Write-Host "  Verificando Git tracking..." -ForegroundColor Cyan
Write-Host "---------------------------------------" -ForegroundColor Cyan
Write-Host ""

$gitTracked = git ls-files 2>$null | Select-String -Pattern "\.jks|\.keystore|key\.properties|keystore\.properties|google-services|service-account|\.env|secrets\.properties"

if ($gitTracked) {
    Write-Host "CUIDADO - Git aun rastrea archivos sensibles:" -ForegroundColor Red
    $gitTracked | ForEach-Object { Write-Host "   $_" -ForegroundColor Red }
} else {
    Write-Host "Git no rastrea ningun archivo sensible." -ForegroundColor Green
}

Write-Host ""
Write-Host "=======================================" -ForegroundColor Cyan

if ($encontrados.Count -eq 0 -and -not $gitTracked) {
    Write-Host "  Todo limpio! Puedes subir a GitHub." -ForegroundColor Green
} else {
    Write-Host "  Archivos protegidos! Ya puedes subir a GitHub." -ForegroundColor Green
    Write-Host ""
    Write-Host "  Para restaurar los archivos despues:" -ForegroundColor Gray
    Write-Host "  PowerShell -ExecutionPolicy Bypass -File .\verificar-repo.ps1 -Restaurar" -ForegroundColor Gray
}

Write-Host "=======================================" -ForegroundColor Cyan
Write-Host ""
