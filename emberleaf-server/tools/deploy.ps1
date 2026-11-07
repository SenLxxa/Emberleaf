# Bringt das Lagerfeuer zu Cloudflare (Weg B aus ANLEITUNG.md, automatisch).
# Aufruf aus dem Projektordner:  powershell -ExecutionPolicy Bypass -File emberleaf-server\tools\deploy.ps1
#
#   1. Anmeldung bei Cloudflare (einmalig: im Browser auf "Allow" klicken)
#   2. D1-Datenbank "lagerfeuer" anlegen, falls es sie noch nicht gibt, und ihre ID in wrangler.toml eintragen
#   3. Worker hochladen und pruefen, ob /v1/health antwortet
#   4. Die Adresse ausgeben (die kommt als LAGERFEUER_URL in emberleaf/backend/version.py)
param([string]$Tools = (Join-Path $HOME "Emberleaf-Tools"))
$ErrorActionPreference = "Stop"
$server = Split-Path $PSScriptRoot -Parent
$wrangler = Join-Path $Tools "wrangler\node_modules\.bin\wrangler.cmd"
if (-not (Test-Path $wrangler)) { throw "Wrangler fehlt: $wrangler" }
$env:PATH = (Join-Path $Tools "node") + ";" + $env:PATH
$env:WRANGLER_SEND_METRICS = "false"
Set-Location $server

function W { & $wrangler @args; if ($LASTEXITCODE -ne 0) { throw "wrangler $($args -join ' ') ist fehlgeschlagen" } }

$who = (& $wrangler whoami 2>&1) | Out-String
if ($LASTEXITCODE -ne 0 -or $who -match "not authenticated") {
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

$out = (& $wrangler deploy 2>&1) | Out-String
Write-Host $out
if ($LASTEXITCODE -ne 0) {
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
Write-Host ""
Write-Host "Lagerfeuer laeuft: $url" -ForegroundColor Green
Write-Output "LAGERFEUER_URL=$url"
