# Bringt Webseite + Lagerfeuer zu Cloudflare (Weg B aus ANLEITUNG.md, automatisch).
# Aufruf aus dem Projektordner:  powershell -ExecutionPolicy Bypass -File emberleaf-server\tools\deploy.ps1
#
#   1. worker.js aus src/, site/ und app/ bauen (build.py)
#   2. Anmeldung bei Cloudflare (einmalig: im Browser auf "Allow" klicken)
#   3. D1-Datenbank "lagerfeuer" und den Datei-Speicher (KV) "emberleaf-files" anlegen, falls es sie noch nicht gibt,
#      und ihre IDs in wrangler.toml eintragen
#   4. Worker hochladen und pruefen, ob /v1/health und die Webseite antworten
#   5. Die Adresse ausgeben (die steht als SITE_URL in emberleaf/backend/version.py)
param([string]$Tools = (Join-Path $HOME "Emberleaf-Tools"))
$ErrorActionPreference = "Stop"
$server = Split-Path $PSScriptRoot -Parent
$wrangler = Join-Path $Tools "wrangler\node_modules\.bin\wrangler.cmd"
if (-not (Test-Path $wrangler)) { throw "Wrangler fehlt: $wrangler" }
$env:PATH = (Join-Path $Tools "node") + ";" + $env:PATH
$env:WRANGLER_SEND_METRICS = "false"
$env:NO_COLOR = "1"
$env:FORCE_COLOR = "0"
Set-Location $server

function W { & $wrangler @args; if ($LASTEXITCODE -ne 0) { throw "wrangler $($args -join ' ') ist fehlgeschlagen" } }
# Wrangler schreibt Warnungen nach stderr; mit "Stop" wuerde PowerShell 5.1 schon daran abbrechen
function Run([string[]]$wranglerArgs) {
    $ErrorActionPreference = "Continue"
    $text = (& $wrangler @wranglerArgs 2>&1 | ForEach-Object { "$_" }) | Out-String
    return @{ text = $text; code = $LASTEXITCODE }
}

$python = Join-Path (Split-Path $server -Parent) "emberleaf\.venv\Scripts\python.exe"
if (-not (Test-Path $python)) { $python = "python" }
& $python -I (Join-Path $server "build.py")
if ($LASTEXITCODE -ne 0) { throw "worker.js konnte nicht gebaut werden." }

$whoRun = Run @("whoami")
$who = $whoRun.text
if ($whoRun.code -ne 0 -or $who -match "not authenticated") {
    Write-Host "Cloudflare-Anmeldung: im Browser auf 'Allow' klicken ..." -ForegroundColor Yellow
    W login
}

$dbs = (& $wrangler d1 list --json) | Out-String | ConvertFrom-Json
$db = $dbs | Where-Object { $_.name -eq "lagerfeuer" } | Select-Object -First 1
if (-not $db) {
    W d1 create lagerfeuer
    $dbs = (& $wrangler d1 list --json) | Out-String | ConvertFrom-Json
    $db = $dbs | Where-Object { $_.name -eq "lagerfeuer" } | Select-Object -First 1
}
if (-not $db.uuid) { throw "Die Datenbank 'lagerfeuer' wurde nicht gefunden." }
$toml = Join-Path $server "wrangler.toml"
$text = [IO.File]::ReadAllText($toml) -replace 'database_id = "[^"]*"', "database_id = `"$($db.uuid)`""
[IO.File]::WriteAllText($toml, $text, (New-Object Text.UTF8Encoding($false)))
Write-Host "Datenbank: lagerfeuer ($($db.uuid))"

# Datei-Speicher fuer die Emberleaf-Versionen, die auf der Webseite hochgeladen werden
function Find-Kv { (& $wrangler kv namespace list) | Out-String | ConvertFrom-Json | Where-Object { $_.title -like "*emberleaf-files" } | Select-Object -First 1 }
$kv = Find-Kv
if (-not $kv) {
    W kv namespace create emberleaf-files
    $kv = Find-Kv
}
if (-not $kv.id) { throw "Der Datei-Speicher 'emberleaf-files' wurde nicht gefunden." }
$text = [IO.File]::ReadAllText($toml) -replace '(binding = "FILES"\s*\r?\nid = ")[^"]*"', "`${1}$($kv.id)`""
[IO.File]::WriteAllText($toml, $text, (New-Object Text.UTF8Encoding($false)))
Write-Host "Datei-Speicher: emberleaf-files ($($kv.id))"

$deployRun = Run @("deploy")
$out = $deployRun.text
Write-Host $out
if ($deployRun.code -ne 0) {
    if ($out -match "workers\.dev subdomain") {
        throw "Cloudflare braucht noch deine workers.dev-Adresse: Oeffne dash.cloudflare.com -> Workers & Pages einmal und waehle einen Namen. Dann dieses Skript nochmal starten."
    }
    throw "Das Hochladen hat nicht geklappt."
}
$url = [regex]::Match($out, "https://lagerfeuer\.[a-z0-9-]+\.workers\.dev").Value
if (-not $url) { throw "Die Adresse des Workers stand nicht in der Ausgabe." }

for ($i = 0; $i -lt 15; $i++) {
    try { $health = Invoke-RestMethod -TimeoutSec 5 "$url/v1/health"; break } catch { Start-Sleep 2 }
}
if (-not $health.ok) { throw "$url/v1/health antwortet nicht." }
$page = Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 "$url/"
if ($page.StatusCode -ne 200 -or $page.Content -notmatch "Emberleaf herunterladen") { throw "Die Webseite unter $url/ antwortet nicht richtig." }
Write-Host ""
Write-Host "Webseite und Lagerfeuer laufen: $url" -ForegroundColor Green
Write-Output "SITE_URL=$url"
