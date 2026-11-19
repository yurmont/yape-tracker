#!/usr/bin/env bash
# Builds and tests the app inside Docker, for a PC without a JDK or Android SDK.
#
#   ./android/docker-build.sh                       # unit tests + debug APK
#   ./android/docker-build.sh :app:assembleRelease  # any Gradle tasks
#
# The SDK and the Gradle cache live in Docker volumes, so only the first run downloads them.
# Running this accepts the Android SDK license inside the container.
set -euo pipefail

cd "$(dirname "$0")"
TASKS=("$@")
[ ${#TASKS[@]} -eq 0 ] && TASKS=(testDebugUnitTest assembleDebug)

# Git Bash on Windows would rewrite the /work paths below; this keeps them as-is.
export MSYS_NO_PATHCONV=1

docker run --rm \
  -v "$(pwd -W 2>/dev/null || pwd)":/work \
  -v yape-android-sdk:/opt/android-sdk \
  -v yape-gradle:/root/.gradle \
  -e ANDROID_HOME=/opt/android-sdk \
  -w /work \
  gradle:8.9-jdk17 \
  bash -c '
    set -euo pipefail
    TOOLS=$ANDROID_HOME/cmdline-tools/latest/bin
    if [ ! -x "$TOOLS/sdkmanager" ]; then
      echo "Installing Android command-line tools…"
      apt-get update -qq && apt-get install -y -qq unzip >/dev/null
      curl -fsSL -o /tmp/tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
      mkdir -p $ANDROID_HOME/cmdline-tools
      unzip -q /tmp/tools.zip -d $ANDROID_HOME/cmdline-tools
      mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest
    fi
    if [ ! -d "$ANDROID_HOME/platforms/android-35" ]; then
      # `yes` dies of SIGPIPE once sdkmanager stops reading; that is expected.
      (yes || true) | $TOOLS/sdkmanager --licenses >/dev/null
      $TOOLS/sdkmanager "platforms;android-35" "build-tools;34.0.0" "platform-tools" >/dev/null
    fi
    [ -x ./gradlew ] || gradle wrapper --gradle-version 8.9 --distribution-type bin -q
    ./gradlew --no-daemon '"${TASKS[*]}"'
  '
