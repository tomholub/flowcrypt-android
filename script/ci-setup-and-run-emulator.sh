#!/bin/bash

"$ANDROID_HOME/emulator/emulator" -accel-check
avdmanager list devices #debug
echo -ne '\n' | avdmanager -v create avd --name ci-emulator --package "system-images;android-32;google_apis;x86_64" --device 'pixel_5' --abi 'google_apis/x86_64'
cat ~/.android/avd/ci-emulator.avd/config.ini
# echo "hw.ramSize=3064"  >> ~/.android/avd/ci-emulator.avd/config.ini
# cat ~/.android/avd/ci-emulator.avd/config.ini
"$ANDROID_HOME/emulator/emulator" -list-avds #debug
"$ANDROID_HOME/emulator/emulator" -avd ci-emulator -no-window -no-boot-anim -no-audio -gpu auto -read-only &
