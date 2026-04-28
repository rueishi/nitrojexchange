#!/bin/sh

#
# Gradle startup script generated for the NitroJEx scaffold.
# The script delegates to the checked-in wrapper JAR so contributors and CI use
# the pinned Gradle distribution from `gradle-wrapper.properties`.
#

APP_HOME=$(cd "${0%/*}" && printf '%s\n' "$PWD")
APP_BASE_NAME=${0##*/}

DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

warn () {
    printf '%s\n' "$*"
} >&2

die () {
    printf '%s\n' "$*"
    exit 1
} >&2

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    [ -x "$JAVACMD" ] || die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
else
    JAVACMD=java
    command -v java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

if [ -n "$DEBUG" ] ; then
    set -x
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
