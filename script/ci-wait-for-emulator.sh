#!/bin/bash
set -o xtrace
adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done;'
adb shell wm dismiss-keyguard
sleep 1
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

###################################################################################################
# to test WKD we need to route all traffic for localhost:443 to localhost:1212
# as we can't use 443 directly for a mock web server.
adb root
# Need wait for the root environment
sleep 20
adb shell "echo 1 > /proc/sys/net/ipv4/ip_forward"
adb shell "iptables -t nat -A PREROUTING -s 127.0.0.1 -p tcp --dport 443 -j REDIRECT --to 1212"
adb shell "iptables -t nat -A OUTPUT -s 127.0.0.1 -p tcp --dport 443 -j REDIRECT --to 1212"
###################################################################################################

echo "Emulator is ready"
set +o xtrace
