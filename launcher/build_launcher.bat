@echo off
chcp 65001 >nul
echo Building New_Flight_chess Launcher...

where g++ >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo g++ not found. Please install MinGW-w64 or add it to PATH.
    echo Or use: "C:\MinGW\bin\g++.exe" -o Launcher.exe launcher.cpp
    pause
    exit /b 1
)

g++ -O2 -static -o Launcher.exe launcher.cpp
if %ERRORLEVEL% neq 0 (
    echo Build failed.
    pause
    exit /b 1
)

copy /Y Launcher.exe ..\Launcher.exe >nul 2>&1
echo.
echo Build succeeded: launcher\Launcher.exe
echo Also copied to project root: ..\Launcher.exe
echo Double-click Launcher.exe (in project root or in launcher folder) to compile and run.
pause
