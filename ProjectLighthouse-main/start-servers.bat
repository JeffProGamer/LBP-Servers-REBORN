@echo off
echo Starting Project Lighthouse servers...
echo.

REM Clean and build all projects
echo Cleaning and building projects...
dotnet clean ProjectLighthouse.Servers.Website\ProjectLighthouse.Servers.Website.csproj
dotnet clean ProjectLighthouse.Servers.GameServer\ProjectLighthouse.Servers.GameServer.csproj
dotnet clean ProjectLighthouse.Servers.API\ProjectLighthouse.Servers.API.csproj
dotnet build ProjectLighthouse.Servers.Website\ProjectLighthouse.Servers.Website.csproj -v:m
dotnet build ProjectLighthouse.Servers.GameServer\ProjectLighthouse.Servers.GameServer.csproj -v:m
dotnet build ProjectLighthouse.Servers.API\ProjectLighthouse.Servers.API.csproj -v:m
echo.

REM Start servers in background
echo Starting servers...
start "Lighthouse Website" cmd /c "dotnet run --project ProjectLighthouse.Servers.Website\ProjectLighthouse.Servers.Website.csproj --no-build --no-restore"
start "Lighthouse GameServer" cmd /c "dotnet run --project ProjectLighthouse.Servers.GameServer\ProjectLighthouse.Servers.GameServer.csproj --no-build --no-restore"
start "Lighthouse API" cmd /c "dotnet run --project ProjectLighthouse.Servers.API\ProjectLighthouse.Servers.API.csproj --no-build --no-restore"

echo.
echo Servers started! Check the console windows for status.
echo Website: http://localhost:10060
echo GameServer: http://localhost:10061
echo API: http://localhost:10062
echo.
echo Press any key to exit...
pause >nul