
@echo off
rem -------------------------------------------------------------------------
rem MHDsend  Launcher
rem -------------------------------------------------------------------------

if not "%ECHO%" == "" echo %ECHO%
if "%OS%" == "Windows_NT" setlocal

set MAIN_CLASS=kr.irm.fhir.MHDsend
set MAIN_JAR=MHDsend-1.0.0.jar

set DIRNAME=.\
if "%OS%" == "Windows_NT" set DIRNAME=%~dp0%

rem Read all command line arguments
set ARGS=
:loop
if [%1] == [] goto end
	set ARGS=%ARGS% %1
	shift
	goto loop
:end

if not "%MHDSEND_HOME%" == "" goto HAVE_MHDSEND_HOME

set MHDSEND_HOME=%DIRNAME%..

:HAVE_MHDSEND_HOME

if not "%JAVA_HOME%" == "" goto HAVE_JAVA_HOME

set JAVA=java

goto SKIP_SET_JAVA_HOME

:HAVE_JAVA_HOME

set JAVA=%JAVA_HOME%\bin\java

:SKIP_SET_JAVA_HOME

set CP=%MHDSEND_HOME%\etc\MHDsend\
SET CP=%CP%;%MHDSEND_HOME%\lib\%MAIN_JAR%
SET CP=%CP%;%MHDSEND_HOME%\lib\animal-sniffer-annotations-1.17.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\checker-qual-2.8.1.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\commons-cli-1.4.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\commons-codec-1.12.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\commons-io-2.6.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\commons-lang3-3.9.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\commons-text-1.7.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\error_prone_annotations-2.3.2.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\failureaccess-1.0.1.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\gson-2.8.5.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\guava-28.0-jre.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\hapi-fhir-base-4.1.0.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\hapi-fhir-client-4.1.0.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\hapi-fhir-structures-r4-4.1.0.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\httpclient-4.5.9.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\httpcore-4.4.11.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\j2objc-annotations-1.3.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\jcl-over-slf4j-1.7.28.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\jsr305-3.0.2.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\log4j-1.2.17.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\org.hl7.fhir.r4-4.1.0.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\org.hl7.fhir.utilities-4.1.0.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\slf4j-api-1.7.26.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\slf4j-log4j12-1.7.26.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\ucum-1.0.2.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\xpp3-1.1.4c.jar
SET CP=%CP%;%MHDSEND_HOME%\lib\xpp3_xpath-1.1.4c.jar

"%JAVA%" %JAVA_OPTS% -cp "%CP%" %MAIN_CLASS% %ARGS%