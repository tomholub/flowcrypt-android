#!/usr/bin/env bash
#
# © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
# Contributors: denbond7
#

set -o xtrace

attempts=$1
script_name=$2

if [[ -z attempts ]]; then
  echo "Please specify attempts count"
  exit 1
else
  if [[ $attempts =~ ^[\-0-9]+$ ]] && ((attempts > 0)); then
    echo "We are going to run tests for $attempts iterations"
    echo "------------------------------------------------------"
  else
    echo "Please specify attempts count as a number > 0"
    exit 1
  fi
fi

for ((i=1;i<=attempts;i++)); do
    echo "Run attempt $i"
    ./script/adb_kill_all_emualtor.sh
    sleep 60
    "$ANDROID_HOME/emulator/emulator" -avd ci-emulator -no-snapshot -no-window -no-boot-anim -no-audio -gpu auto -read-only -no-metrics &
    ./script/ci-wait-for-emulator.sh
    ./script/$script_name
    testResults=$?
    if [[ $testResults -ne 0 ]]; then
      echo "Attempt $i failed"
      echo "Stopping..."
      exit 1
    else
      echo "------------------------------------------------------"
    fi
done

echo "Tests completed for $attempts iterations"
