@echo off
cd /d "%~dp0"
if not exist ".venv\Scripts\pythonw.exe" (
  echo Richte den Shrimp Launcher ein ...
  python -m venv .venv || goto :error
  ".venv\Scripts\python.exe" -m pip install -r requirements.txt || goto :error
)
start "" ".venv\Scripts\pythonw.exe" main.py
exit /b 0

:error
echo Einrichtung fehlgeschlagen. Ist Python installiert?
pause
