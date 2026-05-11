#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

set -e

MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(pwd)}"
WRAPPER_DIR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/maven-wrapper.jar"
WRAPPER_LAUNCHER="org.apache.maven.wrapper.MavenWrapperMain"
DOWNLOAD_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"

if [ -r "$WRAPPER_DIR/maven-wrapper.properties" ]; then
    DOWNLOAD_URL=$(grep wrapperUrl "$WRAPPER_DIR/maven-wrapper.properties" | cut -d'=' -f2)
fi

if [ ! -e "$WRAPPER_JAR" ]; then
    if command -v wget > /dev/null; then
        wget -q -O "$WRAPPER_JAR" "$DOWNLOAD_URL" || rm -f "$WRAPPER_JAR"
    elif command -v curl > /dev/null; then
        curl -o "$WRAPPER_JAR" "$DOWNLOAD_URL" -s || rm -f "$WRAPPER_JAR"
    else
        echo "ERROR: cannot download $DOWNLOAD_URL — install wget or curl"
        exit 1
    fi
fi

if [ -n "$JAVA_HOME" ]; then
    JAVA_EXE="$JAVA_HOME/bin/java"
else
    JAVA_EXE="java"
fi

exec "$JAVA_EXE" \
  -classpath "$WRAPPER_JAR" \
  "-Dmaven.multiModuleProjectDirectory=$MAVEN_PROJECTBASEDIR" \
  "$WRAPPER_LAUNCHER" "$@"
