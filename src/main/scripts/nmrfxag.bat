@echo off

rem nvjp [ script  [ arg ... ] ]
rem 
rem optional environment variables:
rem
rem JAVA_HOME  - directory of JDK/JRE, if not set then 'java' must be found on PATH
rem CLASSPATH  - colon separated list of additional jar files & class directories
rem JAVA_OPTS  - list of JVM options, e.g. "-Xmx256m -Dfoo=bar"
rem TCLLIBPATH - space separated list of Tcl library directories
rem


if "%OS%" == "Windows_NT" setlocal

set nvjver=${project.version}
set nvjpmain=org.nmrfx.processor.gui.MainApp


set dir=%~dp0

set cp="%dir%\analystgui-%nvjver%.jar;${wclasspath};%CLASSPATH%"

if "%TCLLIBPATH%" == "" goto nullTcllib
set tcllibpath=-DTCLLIBPATH="%TCLLIBPATH%"
:nullTcllib

java %tcllibpath% -Djava.awt.headless=true -cp %cp% %JAVA_OPTS% %nvjpmain% %*

