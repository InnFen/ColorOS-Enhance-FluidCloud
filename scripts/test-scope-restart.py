"""Run the packaged shell logic with fake processes; never execute a real kill or ADB."""
import argparse
from pathlib import Path
import shlex
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASH = shutil.which("bash")


class ScriptTest(unittest.TestCase):
    def run_script(self, mode="ok"):
        fixture = Path(tempfile.mkdtemp(prefix="scope-script-", dir=ROOT / ".local"))
        self.assertTrue(fixture.resolve().is_relative_to((ROOT / ".local").resolve()))
        for pid, name in [(101, "com.android.systemui"), (102, "com.android.phone"),
                          (201, "com.android.systemui"), (202, "com.android.phone"), (203, "com.android.settings")]:
            proc = fixture / "proc" / str(pid)
            proc.mkdir(parents=True)
            (proc / "cmdline").write_bytes(("wrong.process" if mode == "identity" and pid == 102 else name).encode() + b"\0")
        tr = Path(BASH).resolve().parents[1] / "usr/bin/tr.exe"
        # Only the filesystem boundary is relocated. All packaged PID/timeout/ordering logic runs.
        body = (ROOT / "app/src/main/res/raw/restart_scopes.sh").read_text(encoding="utf-8")
        body = body.replace('"/proc/', '"' + fixture.as_posix() + '/proc/')
        prefix = f"""
request_deadline={'1' if mode == 'expired' else '100'}
target_user=0
ui_killed=0; phone_killed=0; settings_started=0
id() {{ echo {'10000' if mode == 'denied' else '0'}; }}
date() {{ echo 10; }}
sleep() {{ :; }}
tr() {{ {shlex.quote(tr.as_posix())} "$@"; }}
pidof() {{
  case "$1" in
    com.android.systemui) if [ "$ui_killed" = 1 ]; then echo 201; else echo 101; fi ;;
    com.android.phone) if [ "$phone_killed" = 1 ] && [ {shlex.quote(mode)} != timeout ]; then echo 202; else echo 102; fi ;;
    com.android.settings) if [ "$settings_started" = 1 ]; then echo 203; else return 1; fi ;;
    *) exit 97 ;;
  esac
}}
kill() {{
  echo "kill $*" >> {shlex.quote((fixture / 'operations').as_posix())}
  case "$1:$2" in -TERM:101) ui_killed=1 ;; -TERM:102) phone_killed=1 ;; *) exit 98 ;; esac
}}
am() {{ echo "am $*" >> {shlex.quote((fixture / 'operations').as_posix())}; settings_started=1; }}
"""
        result = subprocess.run([BASH, "--noprofile", "--norc"], input=prefix + body,
                                text=True, capture_output=True, timeout=10)
        operations = (fixture / "operations").read_text() if (fixture / "operations").exists() else ""
        return result, operations

    def test_all_scopes_are_verified_and_settings_opens_last(self):
        result, operations = self.run_script()
        self.assertEqual(0, result.returncode, result.stderr + result.stdout)
        self.assertEqual("kill -TERM 101\nkill -TERM 102\nam start --user 0 -W -a android.settings.SETTINGS -p com.android.settings\n", operations)
        self.assertIn("COLOROS_SCOPE_OK settings 0 203\nCOLOROS_SCOPES_DONE", result.stdout)

    def test_no_signal_after_expired_root_authorization(self):
        result, operations = self.run_script("expired")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", operations)
        self.assertIn("TIMEOUT", result.stdout)

    def test_no_signal_without_root_or_with_wrong_process_identity(self):
        for mode in ("denied", "identity"):
            with self.subTest(mode=mode):
                result, operations = self.run_script(mode)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual("", operations)

    def test_phone_timeout_keeps_systemui_success_and_never_opens_settings(self):
        result, operations = self.run_script("timeout")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("COLOROS_SCOPE_OK systemui 101 201", result.stdout)
        self.assertIn("COLOROS_SCOPE_ERROR phone TIMEOUT", result.stdout)
        self.assertNotIn("am ", operations)
        self.assertNotIn("COLOROS_SCOPES_DONE", result.stdout)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--bash", default=BASH)
    args, remaining = parser.parse_known_args()
    BASH = args.bash
    if not BASH or not Path(BASH).is_file():
        raise SystemExit("Pass --bash with the existing Git Bash executable; no tool is installed by this check.")
    unittest.main(argv=[__file__] + remaining)
