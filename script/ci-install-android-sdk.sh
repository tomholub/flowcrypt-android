#!/bin/bash

set -euxo pipefail

mkdir ~/.android
touch ~/.android/repositories.cfg

SDK_ARCHIVE=sdk-tools-linux-4333796.zip

sudo apt-get -yq install adb qemu-kvm libvirt-daemon-system libvirt-clients bridge-utils
sudo kvm-ok

if [ -d ~/Android ]; then
    echo "~/Android already exists, skipping installation"
    export PATH="$ANDROID_SDK_ROOT/tools:$ANDROID_SDK_ROOT/tools/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
else
    echo "~/Android does not exist, installing"
    mkdir -p $ANDROID_SDK_ROOT

    # download, unpack and remove sdk archive
    wget https://dl.google.com/android/repository/$SDK_ARCHIVE
    unzip -qq $SDK_ARCHIVE -d $ANDROID_SDK_ROOT
    rm $SDK_ARCHIVE

    export PATH="$ANDROID_SDK_ROOT/tools:$ANDROID_SDK_ROOT/tools/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

    # install sdkmanager deps
    echo "yes" | sdkmanager --licenses > /dev/null
    ( sleep 5; echo "y" ) | sdkmanager "build-tools;29.0.0" "platforms;android-24"
    sdkmanager "extras;google;m2repository"
    sdkmanager "platform-tools"
    sdkmanager "emulator"
    sdkmanager "system-images;android-24;google_apis;x86_64"
    echo -ne '\n' | avdmanager -v create avd --name ci-test-nexus4-x86-64-api24 --package "system-images;android-24;google_apis;x86_64" --device 'Nexus 4' --abi 'google_apis/x86_64'
fi

sdkmanager --list
