@echo off  
echo Changing directory to single  
cd /d "%~dp0single"
echo Running center.bat  
call center.bat  
timeout /t 2 /nobreak >nul  
  
echo start external.bat  
call external.bat  
echo external.bat end  
timeout /t 2 /nobreak >nul  
  
echo start global.bat  
call global.bat    
echo global.bat end  
timeout /t 2 /nobreak >nul  
  
echo start game.bat  
call game.bat
echo game.bat end  
timeout /t 2 /nobreak >nul  
  
echo start http.bat  
call http.bat  
echo http.bat end  
timeout /t 2 /nobreak >nul

echo start gmback.bat
call gmback.bat
echo gmback.bat end
timeout /t 2 /nobreak >nul

echo success  
pause