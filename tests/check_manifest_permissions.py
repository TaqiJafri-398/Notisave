import xml.etree.ElementTree as ET
from pathlib import Path

root = ET.parse(Path(__file__).parents[1] / "AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"
declarations = root.findall("uses-permission") + root.findall("uses-permission-sdk-23")
permissions = [node.get(android + "name") for node in declarations]
expected = {
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.USE_BIOMETRIC",
}
if len(permissions) != len(expected) or set(permissions) != expected:
    raise SystemExit(f"Unexpected manifest permissions: {permissions!r}")
if "android.permission.INTERNET" in permissions:
    raise SystemExit("INTERNET permission is not allowed")
print("Manifest permission allowlist OK")
