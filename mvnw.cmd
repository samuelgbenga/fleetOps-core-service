@REM Maven Wrapper script for Windows
@SET "MAVEN_PROJECTBASEDIR=%~dp0"
@IF "%MAVEN_PROJECTBASEDIR:~-1%"=="\" SET "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"

@SET "WRAPPER_DIR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper"
@SET "WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar"
@SET "WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain"

@IF NOT "%JAVA_HOME%" == "" (
    SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) ELSE (
    SET "JAVA_EXE=java"
)

"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" %WRAPPER_LAUNCHER% %*
