@echo off
echo ==================================================
echo AI Finance Assistant - Startup Script
echo ==================================================
echo.
echo IMPORTANT: Make sure Docker Desktop is running before continuing!
echo.
pause

echo.
echo [1/1] Starting complete Docker infrastructure...
docker compose up -d

echo.
echo ==================================================
echo All services have been launched in Docker!
echo Wait 15-30 seconds for them to fully initialize.
echo Then, open your browser to: http://localhost/
echo ==================================================
echo.
pause
