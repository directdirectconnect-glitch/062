#!/usr/bin/env python3
"""Install and smoke-test the actual debug APK in a booted Android emulator.

This is a device-driven test, not a unit test. GitHub Actions launches the emulator.
The script refuses to claim coverage of microphone quality or a physical Fold hinge.
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
import time
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / "build" / "emulator-smoke"
REPORT.mkdir(parents=True, exist_ok=True)
PKG = "com.example.english1000"
ACTIVITY = PKG + "/.MainActivity"
APK = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
PASSED: list[str] = []
TRACE: list[str] = []


def adb(*args: str, timeout: int = 45, check: bool = True) -> str:
    command = ["adb", *args]
    p = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                       timeout=timeout, text=True, encoding="utf-8", errors="replace")
    if check and p.returncode != 0:
        raise RuntimeError(f"Command failed {command}: {p.stdout[-900:]} {p.stderr[-900:]}")
    return p.stdout


def report(step: str) -> None:
    PASSED.append(step)
    TRACE.append("PASS: " + step)
    print("PASS:", step, flush=True)


def screen_size() -> tuple[int, int]:
    text = adb("shell", "wm", "size")
    result = re.findall(r"(?:Physical|Override) size:\s*(\d+)x(\d+)", text)
    if not result:
        raise RuntimeError("Cannot read emulator display size: " + text)
    width, height = map(int, result[-1])
    return width, height


def hierarchy(label: str) -> list[dict[str, str]]:
    """Dump Android's real accessibility hierarchy; capture evidence for each stage."""
    last_error: Exception | None = None
    for attempt in range(5):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/ew-ui.xml", timeout=55)
            xml = adb("exec-out", "cat", "/sdcard/ew-ui.xml", timeout=25)
            root = ET.fromstring(xml.strip())
            (REPORT / (label + ".xml")).write_text(xml, encoding="utf-8")
            return [dict(n.attrib) for n in root.iter("node")]
        except (ET.ParseError, RuntimeError, subprocess.TimeoutExpired) as error:
            last_error = error
            time.sleep(1 + attempt)
    raise RuntimeError(f"UI hierarchy unavailable after retries: {last_error}")


def screenshot(label: str) -> None:
    result = subprocess.run(["adb", "exec-out", "screencap", "-p"],
                            capture_output=True, timeout=25)
    if result.returncode != 0 or not result.stdout.startswith(b"\x89PNG"):
        raise RuntimeError(f"Cannot capture screenshot {label}: {result.stderr!r}")
    (REPORT / (label + ".png")).write_bytes(result.stdout)


def matching(nodes: list[dict[str, str]], fragments: tuple[str, ...]) -> list[dict[str, str]]:
    found = []
    for node in nodes:
        value = (node.get("text", "") + " " + node.get("content-desc", "")).casefold()
        if any(fragment.casefold() in value for fragment in fragments):
            found.append(node)
    return found


def visible_center(node: dict[str, str]) -> tuple[int, int] | None:
    coordinates = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not coordinates:
        return None
    left, top, right, bottom = map(int, coordinates.groups())
    width, height = screen_size()
    if right <= left or bottom <= top:
        return None
    x, y = (left + right) // 2, (top + bottom) // 2
    if not (0 <= x < width and 35 <= y < height - 35):
        return None
    return x, y


def expect_text(label: str, *fragments: str) -> None:
    nodes = hierarchy(label)
    if not matching(nodes, fragments):
        visible = [n.get("text", "") for n in nodes if n.get("text")]
        raise AssertionError(f"Expected one of {fragments!r} on {label}, got {visible[:65]!r}")


def expect_text_scroll(label: str, *fragments: str) -> None:
    """A vertical ScrollView only exposes on-screen children to UiAutomator."""
    for attempt in range(5):
        nodes = hierarchy(label + "_scroll" + str(attempt))
        visible = [n for n in matching(nodes, fragments) if visible_center(n)]
        if visible:
            return
        width, height = screen_size()
        adb("shell", "input", "swipe", str(width // 2), str(height * 4 // 5),
            str(width // 2), str(height // 4), "350")
        time.sleep(0.8)
    raise AssertionError(f"Control {fragments!r} not reachable after scrolling")


def tap_text(label: str, *fragments: str, scroll: bool = True) -> None:
    for attempt in range(5 if scroll else 1):
        nodes = hierarchy(label + "_search" + str(attempt))
        candidates = matching(nodes, fragments)
        # Prefer real clickable buttons to headings.
        candidates.sort(key=lambda n: (n.get("clickable") != "true", len(n.get("text", ""))))
        for node in candidates:
            coords = visible_center(node)
            if coords:
                adb("shell", "input", "tap", str(coords[0]), str(coords[1]))
                time.sleep(0.9)
                return
        if not scroll:
            break
        width, height = screen_size()
        adb("shell", "input", "swipe", str(width // 2), str(height * 4 // 5),
            str(width // 2), str(height // 4), "350")
        time.sleep(0.7)
    raise AssertionError(f"Could not tap visible control {fragments!r} in {label}")


def ensure_alive() -> None:
    pid = adb("shell", "pidof", PKG, check=False).strip()
    if not pid:
        raise AssertionError("Application process is no longer running")


def launch() -> None:
    for attempt in range(2):
        output = adb("shell", "am", "start", "-W", "-n", ACTIVITY, timeout=50)
        TRACE.append(f"Start attempt {attempt + 1}: " + output.strip())
        time.sleep(2.5)
        if adb("shell", "pidof", PKG, check=False).strip():
            return
        time.sleep(1)
    ensure_alive()


def stage(label: str) -> None:
    ensure_alive()
    screenshot(label)
    hierarchy(label)


def main() -> None:
    if not APK.is_file():
        raise FileNotFoundError("Build :app:assembleDebug first: " + str(APK))
    adb("wait-for-device", timeout=120)
    print("Device:", adb("shell", "getprop", "ro.build.version.release").strip(), flush=True)
    adb("shell", "settings", "put", "global", "window_animation_scale", "0")
    adb("shell", "settings", "put", "global", "transition_animation_scale", "0")
    adb("shell", "settings", "put", "global", "animator_duration_scale", "0")
    # No app INTERNET permission; also disable emulator networking for the smoke run.
    adb("shell", "svc", "wifi", "disable")
    adb("shell", "svc", "data", "disable")
    adb("shell", "logcat", "-c", check=False)
    adb("install", "-r", str(APK), timeout=90)
    # Brand-new AVD: the app has no previous user data. Do not call pm clear here:
    # Android may asynchronously force-stop a just-launched Activity during data clear.
    adb("shell", "pm", "grant", PKG, "android.permission.RECORD_AUDIO",
        check=False)
    adb("shell", "wm", "size", "884x2208")
    adb("shell", "wm", "density", "440")
    launch()
    expect_text("home_compact", "Изучать", "Learn words")
    expect_text("home_three_modes", "Проверять", "Check words")
    expect_text("home_ask_button", "Спроси слово", "Ask a word")
    stage("01_phone_home_offline")
    report("Phone UI starts offline with all three mode buttons")

    tap_text("ask_start", "Спроси слово", "Ask a word")
    expect_text("ask_options", "Добавлять примеры", "Include usage examples")
    tap_text("ask_confirm", "Начать", "Start", scroll=False)
    expect_text("ask_activity", "СПРОСИ СЛОВО", "ASK A WORD")
    stage("02_ask_word_screen")
    report("Ask Word activity launches from shared example options")
    adb("shell", "input", "keyevent", "4")
    time.sleep(1.1)

    tap_text("learn_start", "Изучать", "Learn words")
    expect_text("learn_options", "Добавлять примеры", "Include usage examples")
    tap_text("learn_confirm", "Начать", "Start", scroll=False)
    expect_text("learn_active", "Пауза", "Pause listening")
    stage("03_listening_lessons")
    report("Learning mode launches from the same example options")

    tap_text("check_start", "Проверять", "Check words")
    expect_text_scroll("check_button", "Не знаю", "Don't know")
    stage("04_checking_words")
    report("Checking mode shows manual answer controls without requiring ASR")

    # Process-death recovery: current question is persisted by the app.
    adb("shell", "am", "force-stop", PKG)
    launch()
    expect_text_scroll("restored_check", "Не знаю", "Don't know")
    stage("05_restored_after_force_stop")
    report("Current manual-check screen survives process restart")

    # Simulate unfolded wide display, with >600 dp to exercise the adaptive layout.
    adb("shell", "wm", "size", "1768x2208")
    adb("shell", "wm", "density", "320")
    time.sleep(2)
    ensure_alive()
    expect_text_scroll("wide_check", "Не знаю", "Don't know")
    stage("06_fold_width_adapted")
    report("Unfolded-width layout preserves manual-check controls")

    # Back to compact dimensions, exercising the inverse configuration change.
    adb("shell", "wm", "size", "884x2208")
    adb("shell", "wm", "density", "440")
    time.sleep(2)
    ensure_alive()
    expect_text_scroll("fold_back", "Не знаю", "Don't know")
    stage("07_folded_width_adapted")
    report("Folded-width layout preserves manual-check controls")
    print("SMOKE PASS: all checks completed", flush=True)


if __name__ == "__main__":
    error_message = None
    try:
        main()
    except Exception as exc:
        error_message = f"{type(exc).__name__}: {exc}"
        TRACE.append("FAIL: " + error_message)
        print("SMOKE FAIL:", error_message, file=sys.stderr, flush=True)
        try:
            screenshot("FAIL_last_screen")
        except Exception as diagnostic_error:
            TRACE.append("Screenshot diagnostic failed: " + str(diagnostic_error))
        try:
            nodes = hierarchy("FAIL_last_hierarchy")
            TRACE.append("Visible UI text at failure: " + repr([n.get("text", "") for n in nodes if n.get("text")][:80]))
        except Exception as diagnostic_error:
            TRACE.append("UI diagnostic failed: " + str(diagnostic_error))
        try:
            TRACE.append("Foreground activity: " + adb("shell", "dumpsys", "activity", "activities", timeout=30, check=False)[-12000:])
            TRACE.append("Process ID: " + adb("shell", "pidof", PKG, check=False).strip())
        except Exception as diagnostic_error:
            TRACE.append("Process diagnostic failed: " + str(diagnostic_error))
    finally:
        try:
            (REPORT / "logcat.txt").write_text(
                adb("logcat", "-d", "-v", "brief", timeout=35, check=False)[-400_000:],
                encoding="utf-8"
            )
            adb("shell", "wm", "size", "reset", check=False)
            adb("shell", "wm", "density", "reset", check=False)
        except Exception:
            pass
        (REPORT / "result.json").write_text(
            json.dumps({"passed": PASSED, "failure": error_message}, ensure_ascii=False, indent=2),
            encoding="utf-8"
        )
        (REPORT / "trace.txt").write_text("\n\n".join(TRACE), encoding="utf-8")
    sys.exit(1 if error_message else 0)
