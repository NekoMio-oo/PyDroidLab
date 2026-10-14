#!/bin/sh
#
# Gradle start up script for POSIX generated for PyDroidLab.
# Automatically bootstraps Gradle 8.7 if gradle-wrapper.jar is not yet cached locally.
#

set -e

APP_HOME=$( cd "${0%/*}" && pwd -P ) || exit
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

if [ -f "$WRAPPER_JAR" ] && [ -s "$WRAPPER_JAR" ]; then
    exec "$JAVACMD" -Xmx64m -Xms64m -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi

# Fallback: auto-download Gradle 8.7 specified in gradle-wrapper.properties into ~/.gradle/wrapper/dists
GRADLE_VERSION="8.7"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
DIST_DIR="$GRADLE_USER_HOME/wrapper/dists/gradle-${GRADLE_VERSION}-bin/auto"
GRADLE_BIN="$DIST_DIR/gradle-${GRADLE_VERSION}/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
    echo "Bootstrapping Gradle ${GRADLE_VERSION} distribution..."
    mkdir -p "$DIST_DIR"
    ZIP_FILE="$DIST_DIR/gradle-${GRADLE_VERSION}-bin.zip"
    DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    if command -v curl >/dev/null 2>&1; then
        curl -fL "$DIST_URL" -o "$ZIP_FILE"
    elif command -v wget >/dev/null 2>&1; then
        wget -q "$DIST_URL" -O "$ZIP_FILE"
    else
        echo "Error: Neither curl nor wget is installed to bootstrap Gradle." >&2
        exit 1
    fi
    unzip -q -o "$ZIP_FILE" -d "$DIST_DIR"
    rm -f "$ZIP_FILE"
fi

exec "$GRADLE_BIN" -p "$APP_HOME" "$@"
