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
set nvjpmain=org.nmrfx.analyst.gui.NMRAnalystApp
set LOG_CONFIG=-Dlogback.configurationFile=config/logback.xml

set dir=%~dp0

set javaexe=java
set cp="%dir%/lib/nmrfx-analyst-gui-%nvjver%.jar;%dir%lib/*;%dir%plugins/*"

set JAVA_OPTS="-Dprism.maxvram=2G"
set JAVA_OPTS="%JAVA_OPTS% --add-opens=javafx.base/com.sun.javafx.event=ALL-UNNAMED"
set JAVA_OPTS="%JAVA_OPTS% --add-opens=javafx.base/com.sun.javafx.collections=ALL-UNNAMED"
set JAVA_OPTS="%JAVA_OPTS% --add-opens=javafx.base/com.sun.javafx.runtime=ALL-UNNAMED"
set JAVA_OPTS="%JAVA_OPTS% --add-opens=javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED"
set JAVA_OPTS="%JAVA_OPTS% --add-opens=javafx.graphics/com.sun.javafx.scene.traversal=ALL-UNNAMED"
set JAVA_OPTS="%JAVA_OPTS% --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED"
set JAVA_OPTS="%JAVA_OPTS% --add-opens=javafx.controls/javafx.scene.control.skin=ALL-UNNAMED"


set testjava="%dir%jre\bin\java.exe"

if exist %testjava% (
    set javaexe=%testjava%
)


%javaexe%  -mx2048m -cp %cp% %LOG_CONFIG% %JAVA_OPTS% %nvjpmain% %*

