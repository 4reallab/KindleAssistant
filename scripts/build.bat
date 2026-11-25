@echo off
setlocal
set "ROOT=%~dp0.."
set "JAVA_HOME=D:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "GRADLE_USER_HOME=%ROOT%\.gradle-home"
set "ANDROID_HOME=%ROOT%\android-sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
call "%ROOT%\gradlew.bat" -p "%ROOT%" assembleRelease %*
endlocal
