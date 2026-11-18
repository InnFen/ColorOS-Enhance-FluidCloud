#!/system/bin/sh
PATH=/system/bin:/system/xbin
export PATH

if [ "$(id -u)" != 0 ]; then
    echo AOD_RESTART_ROOT_DENIED
    exit 10
fi

verified_systemui_pid() {
    candidate=$(pidof com.android.systemui) || return 1
    case "$candidate" in
        ''|*[!0-9]*) return 1 ;;
    esac
    [ "$candidate" -gt 1 ] || return 1
    name=$(tr -d '\000' < "/proc/$candidate/cmdline" 2>/dev/null) || return 1
    [ "$name" = com.android.systemui ] || return 1
    echo "$candidate"
}

old_pid=$(verified_systemui_pid) || {
    echo AOD_RESTART_TARGET_UNAVAILABLE
    exit 11
}

case "$request_deadline" in
    ''|*[!0-9]*) echo AOD_RESTART_FAILED; exit 12 ;;
esac
now=$(date +%s) || exit 12
if [ "$now" -ge "$request_deadline" ]; then
    echo AOD_RESTART_TIMEOUT
    exit 13
fi

# Never force-stop the package: Android must be able to start its replacement.
if ! kill -TERM "$old_pid"; then
    echo AOD_RESTART_FAILED
    exit 12
fi

attempt=0
while [ "$attempt" -lt 20 ]; do
    sleep 0.5
    new_pid=$(verified_systemui_pid)
    if [ -n "$new_pid" ] && [ "$new_pid" != "$old_pid" ]; then
        echo "AOD_RESTART_OK $old_pid $new_pid"
        exit 0
    fi
    attempt=$((attempt + 1))
done
echo AOD_RESTART_TIMEOUT
exit 13
