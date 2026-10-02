# System Privilege & USB Auto-Grant Guide for DiPlay

This guide explains how to bypass the Android USB permission confirmation dialog:
> *"Open DiPlay when this USB device is connected? [ ] Always open DiPlay..."*

---

## 1. Priv-app Permission Whitelist (`privapp-permissions-com.shihab.diplay.xml`)

Place this file at `/system/etc/permissions/privapp-permissions-com.shihab.diplay.xml` (or `/vendor/etc/permissions/privapp-permissions-com.shihab.diplay.xml`):

```xml
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.shihab.diplay">
        <permission name="android.permission.MANAGE_USB"/>
        <permission name="android.permission.CHANGE_WIFI_STATE"/>
        <permission name="android.permission.ACCESS_FINE_LOCATION"/>
        <permission name="android.permission.CONNECTIVITY_INTERNAL"/>
    </privapp-permissions>
</permissions>
```

---

## 2. Preferred App Binding (`/system/etc/preferred-apps.xml`)

To permanently bind Apple Inc. USB Vendor ID 1452 (`0x05AC`) to `com.shihab.diplay` and bypass the *"Always open DiPlay?"* prompt:

Place this file at `/system/etc/preferred-apps.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<preferred-apps>
    <!-- Direct link Apple Inc. USB Vendor ID 1452 (Hex: 0x05AC) to DiPlay MainActivity -->
    <usb-device vendor-id="1452" package="com.shihab.diplay" class="com.shilapi.xcertplay.MainActivity" />
    <preference package="com.shihab.diplay" class="com.shilapi.xcertplay.MainActivity">
        <usb-device vendor-id="1452" />
    </preference>
</preferred-apps>
```

Alternatively, on rooted devices, you can inject directly into Android's user settings `/data/system/users/0/usb_device_manager.xml`:

```xml
<?xml version="1.0" encoding="utf-8" standalone="yes"?>
<settings>
    <preference package="com.shihab.diplay">
        <usb-device vendor-id="1452" />
    </preference>
</settings>
```

---

## 3. Install as System Privileged App (`/system/priv-app/`)

Push the APK and the configuration XML files to the system partition via Root or ADB:

```bash
adb root
adb remount

# 1. Create priv-app folder and push APK
adb shell mkdir -p /system/priv-app/DiPlay
adb push DiPlay.apk /system/priv-app/DiPlay/DiPlay.apk
adb shell chmod 644 /system/priv-app/DiPlay/DiPlay.apk

# 2. Push the privapp-permissions XML whitelist
adb push etc/permissions/privapp-permissions-com.shihab.diplay.xml /system/etc/permissions/privapp-permissions-com.shihab.diplay.xml
adb shell chmod 644 /system/etc/permissions/privapp-permissions-com.shihab.diplay.xml

# 3. Push the preferred-apps.xml configuration
adb push etc/preferred-apps.xml /system/etc/preferred-apps.xml
adb shell chmod 644 /system/etc/preferred-apps.xml

# 4. Reboot the head unit
adb reboot
```

---

## 4. AOSP ROM / Firmware Level Whitelist (`config.xml`)

In custom AOSP Android OS builds (such as DiLink or automotive custom ROMs), configure the framework overlay in `frameworks/base/core/res/res/values/config.xml`:

```xml
<!-- Auto-grant USB device permissions to DiPlay -->
<string-array name="config_autoGrantUsbPermissions" translatable="false">
    <item>com.shihab.diplay</item>
</string-array>
```

Or for Android Automotive OS (AAOS):
```xml
<!-- Auto-grant USB accessory/device permissions -->
<string-array name="config_autoGrantUsbAccessoryPermissions" translatable="false">
    <item>com.shihab.diplay</item>
</string-array>
```
