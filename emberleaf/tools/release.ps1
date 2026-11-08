# Baut eine neue Emberleaf-Version fuer die Webseite. Hochladen tust du sie dann auf der Webseite unter /upload,
# danach holen sich alle Launcher das Update von selbst ("Update ist da - Jetzt neu starten / Spaeter").
#
# Aufruf aus dem Projektordner (dort, wo emberleaf, emberleaf-mod usw. liegen):
#   powershell -ExecutionPolicy Bypass -File emberleaf\tools\release.ps1 -Version 0.0.9 -Notes "- Neue Insel`n- Weniger Ruckler"
#
# Was passiert:
#   1. Versionsnummer in Launcher (backend/version.py) und Mod (gradle.properties) setzen
#   2. Client-Mod bauen, Shader packen, Emberleaf.exe bauen
#   3. Dateien nach emberleaf\dist\release-<Version> sammeln (mit emberleaf-release.json und notes.md)
#      und alle Dateien einmal komplett entpacken (tools/verify_build.py)
#   4. Den Ordner und die Upload-Seite oeffnen: Dateien reinziehen, Text einfuegen, veroeffentlichen
param(
    [Parameter(Mandatory = $true)] [string]$Version,
    [string]$Notes = "",
    [string]$NotesFile = "",
    [switch]$SkipBuild,     # nur die schon gebauten Dateien einsammeln
    [switch]$NoOpen         # am Ende nichts oeffnen
)
$ErrorActionPreference = "Stop"
$utf8 = New-Object Text.UTF8Encoding($false)
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$launcher = Join-Path $root "emberleaf"
$mod = Join-Path $root "emberleaf-mod"

function Step($text) { Write-Host ""; Write-Host "== $text" -ForegroundColor Yellow }
function Check($what) { if ($LASTEXITCODE -ne 0) { throw "$what ist fehlgeschlagen (Code $LASTEXITCODE)" } }

if ($Version -notmatch '^\d{1,4}\.\d{1,4}\.\d{1,4}$') { throw "Version bitte als 0.0.9 (drei Zahlen mit Punkt)" }
if ($NotesFile) { $Notes = [IO.File]::ReadAllText((Resolve-Path $NotesFile), [Text.Encoding]::UTF8) }
$Notes = $Notes.Replace('`n', "`n").Trim()
$site = [regex]::Match([IO.File]::ReadAllText((Join-Path $launcher "backend\version.py")), 'SITE_URL = "([^"]+)"').Groups[1].Value
if (-not $site) { throw "SITE_URL fehlt in backend\version.py" }

Step "Version $Version eintragen"
$versionPy = Join-Path $launcher "backend\version.py"
$text = [IO.File]::ReadAllText($versionPy, [Text.Encoding]::UTF8) -replace 'APP_VERSION = "[^"]*"', "APP_VERSION = `"$Version`""
[IO.File]::WriteAllText($versionPy, $text, $utf8)
$props = Join-Path $mod "gradle.properties"
$text = [IO.File]::ReadAllText($props, [Text.Encoding]::UTF8) -replace '(?m)^version=.*$', "version=$Version"
[IO.File]::WriteAllText($props, $text, $utf8)

$python = Join-Path $launcher ".venv\Scripts\python.exe"
if (-not $SkipBuild) {
    Step "Client-Mod bauen"
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $mod "tools\gradle.ps1") build
    Check "Der Mod-Build"
    $jar = Join-Path $mod "build\libs\emberleaf-client-$Version.jar"
    if (-not (Test-Path $jar)) { throw "$jar wurde nicht gebaut" }
    $clientMod = Join-Path $launcher "client-mod"
    New-Item -ItemType Directory -Force $clientMod | Out-Null
    Get-ChildItem $clientMod -Filter "emberleaf-client-*.jar" | Remove-Item -Force
    Copy-Item $jar $clientMod

    Step "Shader packen"
    & $python -I (Join-Path $root "emberleaf-shader\make_pack.py")
    Check "Das Shader-Packen"

    Step "Emberleaf.exe bauen"
    # PyInstaller und WebView2 vertragen keine sehr langen Pfade: ueber ein kurzes Laufwerk bauen
    $drive = @("R", "S", "T", "U", "V", "W") | Where-Object { -not (Test-Path "${_}:\") } | Select-Object -First 1
    if (-not $drive) { throw "Kein freier Laufwerksbuchstabe fuer den Build" }
    subst "${drive}:" $root
    try {
        & powershell -NoProfile -ExecutionPolicy Bypass -File "${drive}:\emberleaf\tools\build.ps1"
        Check "Der exe-Build"
    } finally {
        subst "${drive}:" /D
    }
}

Step "Dateien fuers Release sammeln"
$out = Join-Path $launcher "dist\release-$Version"
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force $out | Out-Null
$files = @()
function Add-File($path, $kind) {
    if (-not (Test-Path $path)) { throw "$path fehlt" }
    $item = Get-Item $path
    Copy-Item $item.FullName (Join-Path $out $item.Name)
    $script:files += [ordered]@{
        name = $item.Name; kind = $kind; size = $item.Length
        sha256 = (Get-FileHash $item.FullName -Algorithm SHA256).Hash.ToLower()
    }
}
Add-File (Join-Path $launcher "dist\Emberleaf.exe") "launcher"
Add-File (Join-Path $launcher "client-mod\emberleaf-client-$Version.jar") "mod"
Get-ChildItem (Join-Path $launcher "packs\shaderpacks") -Filter "Emberleaf-*.zip" | ForEach-Object { Add-File $_.FullName "shaderpack" }
$manifest = [ordered]@{ version = $Version; notes = $Notes; files = $files }
[IO.File]::WriteAllText((Join-Path $out "emberleaf-release.json"), ($manifest | ConvertTo-Json -Depth 5), $utf8)
$files | ForEach-Object { "{0,-34} {1,8:N0} KB  {2}" -f $_.name, ($_.size / 1KB), $_.kind }

Step "Dateien pruefen (komplett entpacken)"
# Dieser PC hatte schon umgekippte Bits beim Kopieren: kaputte Dateien duerfen nie an alle gehen
& $python -W ignore (Join-Path $launcher "tools\verify_build.py") $out
Check "Die Pruefung der Release-Dateien"

$body = if ($Notes) { $Notes } else { "Neue Emberleaf-Version." }
[IO.File]::WriteAllText((Join-Path $out "notes.md"), $body, $utf8)

Write-Host ""
Write-Host "Emberleaf $Version ist fertig gebaut: $out" -ForegroundColor Green
Write-Host "Jetzt auf $site/upload hochladen: die Dateien aus dem Ordner reinziehen, den Text aus notes.md einfuegen,"
Write-Host "Hochladen und veroeffentlichen. Die Launcher holen sich das Update in den naechsten 30 Minuten."
if (-not $NoOpen) {
    Start-Process explorer.exe $out
    Start-Process "$site/upload"
}
