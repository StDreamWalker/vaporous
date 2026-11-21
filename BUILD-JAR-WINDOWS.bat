@echo off
echo Building Vaporous (the first build downloads Minecraft and takes a few minutes)...
call gradlew.bat build
if errorlevel 1 (
  echo.
  echo BUILD FAILED - copy the error text above and send it to Claude.
  pause
  exit /b 1
)
copy /Y build\libs\vaporous-0.1.0.jar vaporous-0.1.0.jar >nul
echo.
echo Done! vaporous-0.1.0.jar is now in this folder.
echo Put it in your .minecraft\mods folder along with Fabric API.
pause
