# 📊 DU Data Usage Widget

An elegant, minimalist Android widget designed specifically for **du (UAE)** users to track their daily 1GB data bundle and 30-day plan status directly from their home screen.

No more manual dial codes or opening slow apps. Just a glance at your home screen to see exactly how much data you have left.

![Screenshot_20260301_150606_One UI Home](https://github.com/user-attachments/assets/db32c00b-f6a9-47a3-95ae-8e5f7baeb552)

---

## ✨ Features

### 🌓 Two Beautiful Widget Styles
*   **DU Wide Dashboard**: A comprehensive horizontal view showing your data balance, 30-day plan progress, and a renewal countdown timer.
*   **DU Compact (1x1)**: A stunning, space-saving circular progress ring that fits perfectly in a single grid square.

### 📡 Smart Data Sync
*   **One-Tap Refresh**: Tap the 🔄 icon to instantly request your latest balance.
*   **SMS-Powered**: Automatically sends a silent "balance" request to `1355` and parses the reply.
*   **Instant-Read**: Reads your inbox immediately on refresh to show the last known balance while waiting for the new SMS.

### 📉 Visual Tracking
*   **Daily Data Bar**: Visual progress of your daily 1GB allowance.
*   **Renewal Countdown**: "Renews in Xh Ym" timer so you know exactly when your data resets.
*   **Plan Status**: Tracks your 30-day bundle and shows exactly how many days are left.

### 🛡️ Privacy & Performance
*   **Invisible App**: Once permissions are granted, the app icon is hidden from your menu (optional) and has no background battery drain.
*   **Dark Mode Aesthetic**: A premium dark navy design (`#1A1A2E`) with modern blue accents that looks great on any wallpaper.

---

## 🚀 How to Install

1.  **Build & Install**: Deploy the APK to your Android device.
2.  **Grant Permissions**: Open the app **once** from your menu to allow SMS reading/sending. This is required for the widget to fetch your data.
3.  **Add Widget**:
    *   Long-press your home screen.
    *   Select **Widgets**.
    *   Search for **"DU Data Usage Widget"**.
    *   Choose between the **Wide** or **Compact** version.
4.  **Ready to Go**: Tap the 🔄 button anytime to see your latest data!

---

## 🛠️ Technical Details

*   **Language**: 100% Kotlin
*   **Architecture**: AppWidgetProvider with BroadcastReceiver for SMS processing.
*   **Min SDK**: Android 31 (Android 12+)
*   **Permissions**: `SEND_SMS`, `RECEIVE_SMS`, `READ_SMS`.

---

## 📸 Screenshots

*(Add your screenshots here to make it even more attractive!)*

---

*Developed with ❤️ for the UAE du community.*
