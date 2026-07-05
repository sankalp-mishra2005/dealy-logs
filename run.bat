@echo off
title BSP Delay Management System
echo ============================================
echo   BSP Delay Management System - Starting...
echo ============================================
echo.

set JAVA_HOME=C:\Program Files\Java\jdk-24.0.2
set PATH=%JAVA_HOME%\bin;%PATH%
set MVN=.\maven\apache-maven-3.9.6\bin\mvn

echo Java Version:
java -version
echo.
echo Starting Spring Boot on http://localhost:8085
echo Press Ctrl+C to stop the server.
echo.

call %MVN% spring-boot:run
pause
