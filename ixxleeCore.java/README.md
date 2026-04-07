# project-ixxlee

Built with Java 26 and Gradle wrapper.

## Usage

1. Set JAVA_HOME to your JDK 26 path:
   setx JAVA_HOME "C:\\Program Files\\Java\\jdk-26"
   set PATH=%JAVA_HOME%\\bin;%PATH%

2. If Gradle CLI is installed:
   gradle wrapper --gradle-version 9.4.2

3. Otherwise use wrapper after installing Gradle once, then run:
   gradlew.bat build
   gradlew.bat run

## Oracle JDBC (optional)

Add to uild.gradle:
implementation 'com.oracle.database.jdbc:ojdbc11:21.12.0.0'

## Notes

The wrapper depends on gradle-wrapper.jar, which is not yet present; run gradle wrapper from a machine with Gradle.
