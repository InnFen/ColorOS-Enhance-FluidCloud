#!/system/bin/sh
PATH=/system/bin:/system/xbin
export PATH

fail() { echo "COLOROS_SCOPE_ERROR $1 $2"; exit 12; }
[ "$(id -u)" = 0 ] || fail all ROOT_DENIED
case "$request_deadline:$target_user" in
    *[!0-9:]*|:*|*:) fail all FAILED ;;
esac
check_deadline() {
    now=$(date +%s) || fail "$1" FAILED
    [ "$now" -lt "$request_deadline" ] || fail "$1" TIMEOUT
}
verified_pid() {
    candidate=$(pidof "$1") || return 1
    case "$candidate" in ''|*[!0-9]*) return 1 ;; esac
    [ "$candidate" -gt 1 ] || return 1
    name=$(tr -d '\000' < "/proc/$candidate/cmdline" 2>/dev/null) || return 1
    [ "$name" = "$1" ] || return 1
    echo "$candidate"
}
old_systemui=$(verified_pid com.android.systemui) || fail systemui TARGET_UNAVAILABLE
old_phone=$(verified_pid com.android.phone) || fail phone TARGET_UNAVAILABLE
old_settings=$(verified_pid com.android.settings)
if [ -z "$old_settings" ]; then
    # An absent Settings process is fine; an ambiguous or unverified one must not be killed.
    [ -z "$(pidof com.android.settings)" ] || fail settings TARGET_UNAVAILABLE
    old_settings=0
fi
restart_persistent() {
    scope=$1; package=$2; old_pid=$3
    check_deadline "$scope"
    current=$(verified_pid "$package") || fail "$scope" TARGET_UNAVAILABLE
    [ "$current" = "$old_pid" ] || fail "$scope" TARGET_UNAVAILABLE
    # Persistent system processes must be restarted, never force-stopped as packages.
    kill -TERM "$old_pid" || fail "$scope" FAILED
    attempt=0
    while [ "$attempt" -lt 20 ]; do
        sleep 0.5
        next=$(verified_pid "$package")
        if [ -n "$next" ] && [ "$next" != "$old_pid" ]; then
            echo "COLOROS_SCOPE_OK $scope $old_pid $next"
            return
        fi
        attempt=$((attempt+1))
    done
    fail "$scope" TIMEOUT
}
restart_persistent systemui com.android.systemui "$old_systemui"
restart_persistent phone com.android.phone "$old_phone"
check_deadline settings
if [ "$old_settings" != 0 ]; then
    current=$(verified_pid com.android.settings) || fail settings TARGET_UNAVAILABLE
    [ "$current" = "$old_settings" ] || fail settings TARGET_UNAVAILABLE
    kill -TERM "$old_settings" || fail settings FAILED
    attempt=0
    while [ -e "/proc/$old_settings" ] && [ "$attempt" -lt 20 ]; do
        sleep 0.1; attempt=$((attempt+1))
    done
    [ ! -e "/proc/$old_settings" ] || fail settings TIMEOUT
fi
check_deadline settings
am start --user "$target_user" -W -a android.settings.SETTINGS -p com.android.settings >/dev/null 2>&1 || fail settings FAILED
attempt=0
while [ "$attempt" -lt 20 ]; do
    next=$(verified_pid com.android.settings)
    if [ -n "$next" ] && [ "$next" != "$old_settings" ]; then
        echo "COLOROS_SCOPE_OK settings $old_settings $next"
        echo COLOROS_SCOPES_DONE
        exit 0
    fi
    sleep 0.5; attempt=$((attempt+1))
done
fail settings TIMEOUT
