#!/bin/bash

set -euxo pipefail

if [[ -d ~/.android ]]
then
     echo "~/.android already exists"
else
     mkdir ~/.android
     touch ~/.android/repositories.cfg
fi

SDK_ARCHIVE=commandlinetools-linux-8512546_latest.zip

sudo apt-get -qq install qemu-kvm libvirt-daemon-system libvirt-clients bridge-utils > /dev/null
sudo kvm-ok

if [[ -d ~/Android ]]; then
    echo "~/Android already exists, skipping installation"
else
    echo "~/Android does not exist, installing"
    mkdir -p $ANDROID_SDK_ROOT

    # download, unpack and remove sdk archive
    wget https://dl.google.com/android/repository/$SDK_ARCHIVE
    unzip -qq $SDK_ARCHIVE -d $ANDROID_SDK_ROOT
    cd $ANDROID_SDK_ROOT/cmdline-tools && mkdir latest && (ls | grep -v latest | xargs mv -t latest)
    cd $SEMAPHORE_GIT_DIR
    rm $SDK_ARCHIVE

    # Install Android SDK
    (echo "yes" | ${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null | grep -v = || true)
    ( sleep 5; echo "y" ) | (${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager "build-tools;33.0.0" "platforms;android-33" > /dev/null | grep -v = || true)
    (${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager "extras;google;m2repository" | grep -v = || true)
    (${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager "platform-tools" | grep -v = || true)
    (${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager "emulator" | grep -v = || true)
    (echo "y" | ${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager "system-images;android-32;google_apis;x86_64" > /dev/null | grep -v = || true)
fi

#Uncomment this for debug
#${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager --list

