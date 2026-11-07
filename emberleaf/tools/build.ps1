# Baut Emberleaf.exe (eine einzelne Datei) in den Ordner dist.
# Aufruf aus dem emberleaf-Ordner:  powershell -ExecutionPolicy Bypass -File tools\build.ps1
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not (Test-Path ".venv\Scripts\python.exe")) {
    python -m venv .venv
    & ".venv\Scripts\python.exe" -m pip install -r requirements.txt pyinstaller pillow
}
& ".venv\Scripts\python.exe" tools\make_icon.py
$extra = @()
if (Test-Path "client-mod") { $extra += "--add-data", "client-mod;client-mod" }  # der Emberleaf-Mod fürs Spiel
if (Test-Path "packs") { $extra += "--add-data", "packs;packs" }  # Emberleaf-Shader (Texturen baut der Launcher selbst)
foreach ($file in "ANLEITUNG.md", "worker.js", "schema.sql", "wrangler.toml") {
    if (Test-Path "..\emberleaf-server\$file") { $extra += "--add-data", "..\emberleaf-server\$file;lagerfeuer" }
}
& ".venv\Scripts\python.exe" -m PyInstaller --noconfirm --clean --onefile --windowed `
    --name Emberleaf --icon emberleaf.ico `
    --add-data "ui;ui" --add-data "emberleaf.ico;." @extra `
    --collect-data minecraft_launcher_lib `
    main.py
if ($LASTEXITCODE -ne 0) { throw "PyInstaller ist fehlgeschlagen" }
Get-Item "dist\Emberleaf.exe" | Select-Object Name, @{n = "MB"; e = { [math]::Round($_.Length / 1MB, 1) } }
