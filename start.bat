@echo off
echo ==================================================
echo AI Finance Assistant - Startup Script
echo ==================================================
echo.
echo IMPORTANT: Make sure Docker Desktop is running before continuing!
echo.
pause

echo.
echo Cleaning up any old processes on ports 8080, 5001, and 5173...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :5001') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :5173') do taskkill /F /PID %%a >nul 2>&1

echo.
echo [1/4] Starting Docker infrastructure...
docker compose up -d postgres redis rabbitmq

echo.
echo [2/4] Starting Spring Boot Backend (Port 8080)...
start "Finance Backend" cmd /k "cd ""Finance and Accounting Assistant"" && .\mvnw.cmd spring-boot:run"

echo.
echo [3/4] Starting Python AI Service (Port 5001)...
start "Finance AI Service" cmd /k "cd finance-ai && py -3.12 app.py"

echo.
echo [4/4] Starting React Frontend (Port 5173)...
start "Finance Frontend" cmd /k "cd finance-frontend && npm run dev"

echo.
echo ==================================================
echo All services have been launched in separate windows!
echo Wait 15-30 seconds for them to fully initialize.
echo Then, open your browser to: http://localhost:5173
echo ==================================================
echo.
pause
