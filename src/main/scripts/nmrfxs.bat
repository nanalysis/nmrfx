@echo off

rem nvjp [ script  [ arg ... ] ]
rem 
rem optional environment variables:
rem
rem JAVA_HOME  - directory of JDK/JRE, if not set then 'java' must be found on PATH
rem CLASSPATH  - colon separated list of additional jar files & class directories
rem JAVA_OPTS  - list of JVM options, e.g. "-Xmx256m -Dfoo=bar"
rem


if "%OS%" == "Windows_NT" setlocal

set nvjver=${project.version}
set nvjpmain=org.python.util.jython

set dir=%~dp0

set javaexe=java
set cp="%dir%structure-%nvjver%.jar;%dir%lib/Manifest.jar"
set pgm=%~f0

set testjava=%dir%jre\bin\java.exe

if exist %testjava% (
    set javaexe="%testjava%"
    set cp="%dir%lib/structure-%nvjver%.jar;%dir%lib/%Manifest.jar"
)


if "%1"=="" (
    %javaexe% -Djava.awt.headless=true -mx2048m -cp %cp% %JAVA_OPTS% %nvjpmain%
) else (
    %javaexe% -Djava.awt.headless=true -mx2048m -cp %cp% %JAVA_OPTS% %nvjpmain% -c "import dispatchnvfxs" %*
)

