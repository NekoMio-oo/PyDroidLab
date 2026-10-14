"""
android_app.py
Built-in Android Native Hardware & System Bridge for PyDroidLab.

Usage in user Python scripts:
    import android_app

    android_app.toast("Hello from Python!")
    android_app.vibrate(200)
    android_app.notify("Task Finished", "Data scraping completed.")
    android_app.set_clipboard("Copied text")
    print(android_app.get_clipboard())
    android_app.speak("任务已完成")
    print(android_app.device_info())
    android_app.show_image("plot.png")
"""

import json

_bridge = None


def _set_bridge(bridge_instance):
    global _bridge
    _bridge = bridge_instance


def _require_bridge():
    if _bridge is None:
        raise RuntimeError("AndroidHardwareBridge is not attached.")
    return _bridge


def toast(message: str):
    """Displays a native Android Toast message on screen."""
    _require_bridge().showToast(str(message))


def vibrate(duration_ms: int = 200):
    """Vibrates the device for `duration_ms` milliseconds."""
    _require_bridge().vibrate(int(duration_ms))


def notify(title: str, message: str):
    """Sends a native Android system notification."""
    _require_bridge().sendNotification(str(title), str(message))


def get_clipboard() -> str:
    """Reads plain text from the Android system clipboard."""
    return str(_require_bridge().getClipboardText() or "")


def set_clipboard(text: str):
    """Copies plain text to the Android system clipboard."""
    _require_bridge().setClipboardText(str(text))


def speak(text: str):
    """Uses Android native Text-To-Speech (TTS) engine to speak text aloud."""
    _require_bridge().speakText(str(text))


def device_info() -> dict:
    """Returns a dictionary containing Android device model, SDK version, ABI, and battery level."""
    raw = str(_require_bridge().getDeviceInfoJson() or "{}")
    return json.loads(raw)


def show_image(image_path: str):
    """Opens an image file (PNG/JPG/BMP) directly in PyDroidLab's built-in zoomable image preview dialog."""
    _require_bridge().showImagePreview(str(image_path))
