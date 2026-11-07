# Startet Gradle fuer den Emberleaf-Client-Mod mit dem JDK aus tools\jdk-path.txt (oder JAVA_HOME).
# Aufruf aus dem Session-Ordner:  powershell -NoProfile -ExecutionPolicy Bypass -File emberleaf-mod\tools\gradle.ps1 build
# Das komplette Protokoll landet in emberleaf-mod\build\gradle-last.log, ausgegeben werden die letzten Zeilen.
param([Parameter(ValueFromRemainingArguments = $true)] [string[]]$Tasks)
$ErrorActionPreference = "Stop"
$project = Split-Path $PSScriptRoot -Parent
$jdkFile = Join-Path $PSScriptRoot "jdk-path.txt"
if (Test-Path $jdkFile) { $env:JAVA_HOME = (Get-Content $jdkFile -TotalCount 1).Trim() }
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    throw "Kein Java 25 gefunden: JAVA_HOME setzen oder den Pfad in tools\jdk-path.txt eintragen."
}
New-Item -ItemType Directory -Force (Join-Path $project "build") | Out-Null
$log = Join-Path $project "build\gradle-last.log"
$errLog = Join-Path $project "build\gradle-last.err.log"
$proc = Start-Process -FilePath (Join-Path $project "gradlew.bat") -ArgumentList (@("--no-daemon", "--console=plain") + $Tasks) `
    -WorkingDirectory $project -NoNewWindow -Wait -PassThru -RedirectStandardOutput $log -RedirectStandardError $errLog
Get-Content $log -Tail 40
Get-Content $errLog -Tail 40
exit $proc.ExitCode
