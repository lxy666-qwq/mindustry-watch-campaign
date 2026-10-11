@echo off
cd /d "%~dp0"
set "MINDUSTRY_DATA_DIR=%~dp0watch-data"
java -Dnodiscord -jar "%~dp0Mindustry.jar"
if errorlevel 1 pause
