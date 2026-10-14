@echo off
setlocal
where java >nul 2>nul || (echo ERROR: Java 17 not found. & exit /b 1)
java -version
echo.
where gradle >nul 2>nul || (echo ERROR: Install Gradle 8.8 and add it to PATH. & exit /b 1)
echo Building JujutsuCraft NPC Compatibility Forge addon...
call gradle --no-daemon clean build
if errorlevel 1 (
  echo Forge compile failed. Send the COMPLETE build error output for fixes.
  exit /b 1
)
echo.
echo Build completed. Check build\libs for the mod JAR.
echo This does not mean in-game casting, domains, or animations have been verified.
endlocal
