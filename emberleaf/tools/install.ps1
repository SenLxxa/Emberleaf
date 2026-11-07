# Installiert Emberleaf in einen kurzen Ordner und legt eine Desktop-Verknüpfung an.
# Läuft über das signierte pythonw.exe, damit Windows (Smart App Control) es nicht blockiert.
# Aufruf aus dem emberleaf-Ordner:  powershell -ExecutionPolicy Bypass -File tools\install.ps1
param([string]$Target = (Join-Path $HOME "Emberleaf"))
$ErrorActionPreference = "Stop"
$src = Split-Path $PSScriptRoot -Parent

New-Item -ItemType Directory -Force $Target | Out-Null
foreach ($item in "backend", "ui", "client-mod", "packs", "main.py", "requirements.txt", "emberleaf.ico") {
    $dest = Join-Path $Target $item
    if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
    if (-not (Test-Path (Join-Path $src $item))) { continue }  # client-mod gibt es erst nach dem Mod-Build
    Copy-Item -Recurse (Join-Path $src $item) $dest -Exclude "__pycache__"
}
# Lagerfeuer-Server (Freunde) mit Anleitung, damit man ihn aus dem Launcher heraus findet
$server = Join-Path (Split-Path $src -Parent) "emberleaf-server"
$serverDest = Join-Path $Target "lagerfeuer"
if (Test-Path $serverDest) { Remove-Item -Recurse -Force $serverDest }
if (Test-Path $server) {
    New-Item -ItemType Directory -Force $serverDest | Out-Null
    foreach ($file in "ANLEITUNG.md", "worker.js", "schema.sql", "wrangler.toml") {
        Copy-Item (Join-Path $server $file) $serverDest
    }
}

$venv = Join-Path $Target ".venv"
if (-not (Test-Path (Join-Path $venv "Scripts\pythonw.exe"))) { python -m venv $venv }
& (Join-Path $venv "Scripts\python.exe") -m pip install --disable-pip-version-check -q -r (Join-Path $Target "requirements.txt")
if ($LASTEXITCODE -ne 0) { throw "Die Pakete konnten nicht installiert werden" }

$shell = New-Object -ComObject WScript.Shell
$link = $shell.CreateShortcut((Join-Path ([Environment]::GetFolderPath("Desktop")) "Emberleaf.lnk"))
$link.TargetPath = Join-Path $venv "Scripts\pythonw.exe"
$link.Arguments = '"' + (Join-Path $Target "main.py") + '"'
$link.WorkingDirectory = $Target
$link.IconLocation = (Join-Path $Target "emberleaf.ico") + ",0"
$link.Description = "Emberleaf Minecraft Launcher"
$link.Save()
"Emberleaf ist installiert in $Target, die Verknuepfung liegt auf dem Desktop."
