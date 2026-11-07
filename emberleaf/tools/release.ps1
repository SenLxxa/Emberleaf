# Veroeffentlicht eine neue Emberleaf-Version auf GitHub. Alle Launcher deiner Freunde laden sie dann von selbst
# und melden sich mit "Update ist da - Jetzt neu starten / Spaeter".
#
# Aufruf aus dem Projektordner (dort, wo emberleaf, emberleaf-mod usw. liegen):
#   powershell -ExecutionPolicy Bypass -File emberleaf\tools\release.ps1 -Version 0.0.3 -Notes "- Neue Insel`n- Weniger Ruckler"
#
# Was passiert:
#   1. Versionsnummer in Launcher (backend/version.py) und Mod (gradle.properties) setzen
#   2. Client-Mod bauen, Shader packen, Emberleaf.exe bauen
#   3. emberleaf-release.json schreiben (Dateiliste mit SHA-256, daran prueft der Launcher die Downloads)
#   4. Code committen, Tag v<Version> setzen, zu GitHub pushen und das Release mit allen Dateien anlegen
param(
    [Parameter(Mandatory = $true)] [string]$Version,
    [string]$Notes = "",
    [string]$NotesFile = "",
    [string]$Tools = (Join-Path $HOME "Emberleaf-Tools"),   # git, gh, node, jdk (ohne Installation)
    [switch]$SkipBuild,     # nur veroeffentlichen, was schon gebaut ist
    [switch]$NoPublish      # nur bauen, nichts hochladen
)
$ErrorActionPreference = "Stop"
$utf8 = New-Object Text.UTF8Encoding($false)
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$launcher = Join-Path $root "emberleaf"
$mod = Join-Path $root "emberleaf-mod"
$git = Join-Path $Tools "git\cmd\git.exe"
$gh = Join-Path $Tools "gh\bin\gh.exe"
$env:GH_CONFIG_DIR = Join-Path $Tools "gh-config"
$env:PATH = (Join-Path $Tools "git\cmd") + ";" + $env:PATH

function Step($text) { Write-Host ""; Write-Host "== $text" -ForegroundColor Yellow }
function Check($what) { if ($LASTEXITCODE -ne 0) { throw "$what ist fehlgeschlagen (Code $LASTEXITCODE)" } }

if ($Version -notmatch '^\d{1,4}\.\d{1,4}\.\d{1,4}$') { throw "Version bitte als 0.0.3 (drei Zahlen mit Punkt)" }
if ($NotesFile) { $Notes = [IO.File]::ReadAllText((Resolve-Path $NotesFile), [Text.Encoding]::UTF8) }
$Notes = $Notes.Replace('`n', "`n").Trim()
if (-not $NoPublish) {
    foreach ($tool in $git, $gh) { if (-not (Test-Path $tool)) { throw "$tool fehlt (Werkzeuge liegen in $Tools)" } }
    & $gh auth status *> $null
    if ($LASTEXITCODE -ne 0) { throw "Nicht bei GitHub angemeldet: $gh auth login --web" }
    & $git -C $root rev-parse --verify --quiet "refs/tags/v$Version" *> $null
    if ($LASTEXITCODE -eq 0) { throw "Version $Version gibt es schon. Nimm eine hoehere Nummer." }
}

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

if ($NoPublish) { Write-Host "Fertig gebaut (nicht hochgeladen): $out"; exit 0 }

Step "Code zu GitHub pushen"
& $git -C $root add -A; Check "git add"
& $git -C $root diff --cached --quiet
if ($LASTEXITCODE -ne 0) { & $git -C $root commit -q -m "Emberleaf $Version"; Check "git commit" }
& $git -C $root tag -a "v$Version" -m "Emberleaf $Version"; Check "git tag"
& $git -C $root push -q origin HEAD; Check "git push"
& $git -C $root push -q origin "v$Version"; Check "git push (Tag)"

Step "Release auf GitHub anlegen"
$notesPath = Join-Path $out "notes.md"
$body = if ($Notes) { $Notes } else { "Neue Emberleaf-Version." }
[IO.File]::WriteAllText($notesPath, $body, $utf8)
$assets = Get-ChildItem $out -File | Where-Object { $_.Name -ne "notes.md" } | ForEach-Object { $_.FullName }
& $gh release create "v$Version" @assets --title "Emberleaf $Version" --notes-file $notesPath --latest --verify-tag
Check "Das Release"
Write-Host ""
Write-Host "Emberleaf $Version ist veroeffentlicht. Die Launcher deiner Freunde holen es sich in den naechsten 30 Minuten." -ForegroundColor Green
