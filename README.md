# Noti & Status Saver (Notisave)

**Notification & WhatsApp Status Saver for Android** — automatically saves
incoming notifications from WhatsApp, Facebook, Instagram, Messenger,
Telegram, SMS and other apps, and lets you view and save WhatsApp statuses
you have already seen.

## Features

- **Automatic capture** via Android's `NotificationListenerService`
- **Local SQLite history** — no server, no account, nothing uploaded
- **Deleted WhatsApp message recovery** — messages captured as notifications
  stay available even if deleted in WhatsApp (only messages received while
  saving was on can be shown)
- **WhatsApp Status Saver** — shows statuses you have viewed in WhatsApp
  (images + videos) in a grid; tap to view full-screen, Save button copies
  any status to Pictures/Notisave (visible in Gallery); Save All, Saved
  filter, and delete of saved copies. Access works through Android's
  system folder picker (Storage Access Framework): the user picks the
  WhatsApp folder once — the app asks for no all-files or storage
  permission, which keeps Play Protect from hard-blocking the install.
- **WhatsApp-only by default** — only WhatsApp notifications are saved on a
  fresh install; any other app can be enabled from the Apps tab
- **Dashboard** with statistics: total, today, this week, WhatsApp, top apps
- **Chronological history** (newest first) with app icons, sender, preview, timestamp
- **Search** by app name, sender or message content
- **Filter** by app, plus a dedicated deleted-WhatsApp view
- **Per-app save toggles** in the Apps tab
- **Delete** individual notifications or clear all history
- **Dark / Light / System** themes
- **Notification access setup** with status card and one-tap settings shortcut
- **Privacy notice** on first run and in Settings
- **About Developer** section with WhatsApp contact

## Architecture

Single-activity app with four fragments (Home, Notifications, Apps, Settings),
a `NotificationListenerService` that writes to SQLite on a background thread,
and a `BootReceiver` that rebinds the listener after reboot. Framework-only
(no AndroidX), minSdk 26, targetSdk 35. Built without Gradle — see `build.sh`.

## Build

```bash
./build.sh
```

Produces a signed `Notisave.apk` (release keystore: `notisave-release.keystore`).

## Privacy

Notifications may contain personal information. Notisave stores them in a
local database and does not upload or sync them. Android Auto Backup is disabled
for this app, so the database is not included in device backup. Optional backups
are created only when you export them and are password-encrypted with AES-GCM;
where you save or share an exported file is outside the app's control. Keep the
password safe because it cannot be recovered. App lock uses Android device authentication (Android 10+). Notification
history can be filtered to today, the last 7 days, or the last 30 days. The app
does not bypass encryption or access private app databases, and it cannot recover
messages that were never delivered as notifications.

## Developer

Muhammad Taqi — WhatsApp 03077982214

## Development checks

Run `python3 tests/check_manifest_permissions.py` to verify the app's intentionally small manifest permission set. See [CHANGELOG.md](CHANGELOG.md) for release notes.
