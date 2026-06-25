#!/bin/sh

APP_PATH="$0"

while [ -h "$APP_PATH" ]; do
    ls=$(ls -ld "$APP_PATH")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        APP_PATH="$link"
    else
        APP_PATH=$(dirname "$APP_PATH")/"$link"
    fi
done

APP_HOME=$(cd "$(dirname "$APP_PATH")" >/dev/null 2>&1 && pwd -P) || exit

exec java \
    -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
