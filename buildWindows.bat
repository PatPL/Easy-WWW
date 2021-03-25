@echo off

REM Clean previous folder
rd /s /q buildWindows
mkdir buildWindows

REM Package the project into a .jar
cd Easy-WWW\out\production\Easy-WWW
echo   Packaging the project into a jar file...
jar -cvf Easy-WWW.jar * > nul
move Easy-WWW.jar ../../../../buildWindows/Easy-WWW.jar > nul
cd ../../../..

REM Build a custom JRE runtime
REM jdeps --list-deps all.jar
REM java.base
REM java.desktop
cd buildWindows
echo   Building a custom JRE runtime...
jlink --no-header-files --no-man-pages --compress=2 --strip-debug --add-modules java.base,java.desktop --output java-runtime

REM Add launchers and package the project
echo   Creating launcher scripts...
copy ..\buildResources\startServer.bat . > nul

echo   Compressing everything into a zip archive...
tar -acf all-build-Windows.zip java-runtime startServer.bat Easy-WWW.jar

REM Cleanup
echo   Final cleanup...
del startServer.bat
del Easy-WWW.jar
rd /s /q java-runtime

echo   Done.
pause