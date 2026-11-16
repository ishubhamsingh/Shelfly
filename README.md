<p align="center">
  <img src="assets/icon.png" width="96" height="96" alt="Shelfly icon" />
</p>

<h1 align="center">Shelfly</h1>
<p align="center">Track expiry dates for food, medicine, and cosmetics — powered by Android AppFunctions</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Kotlin-2.2.21-7F52FF?logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-2024.12-4285F4?logo=jetpackcompose&logoColor=white" />
  <img src="https://img.shields.io/badge/AppFunctions-1.0.0--rc01-orange" />
  <img src="https://img.shields.io/github/license/ishubhamsingh/Shelfly" />
</p>

---

## What is Shelfly?

Shelfly is a minimal Android app for tracking when things on your shelf expire — groceries, medicines, skincare, and anything else with a best-before date. Instead of hunting through kitchen cabinets when you're already sick, you get a nudge before things go bad.

The differentiator: **Shelfly exposes its entire data surface as Android AppFunctions**, so Gemini (and any future on-device AI agent) can add, list, update, and delete items through natural conversation — no typing required.

---

## Features

### Core
- **Add items** with category (Food / Medicine / Cosmetic / Other), expiry date, quantity, and notes
- **Status-aware home screen** — items are colour-coded: Fresh → Expiring Soon → Expired → Consumed
- **Filter & Sort** — filter by status, sort by expiry date, category, name, date added, or quantity
- **Item detail** — full view with consume / edit / delete actions

### Notifications
- **Daily background check** via WorkManager — notifies when items enter your reminder window (1–14 days configurable)
- **Custom notification** with category chip badges rendered via RemoteViews
- **Group or separate** — one grouped notification for all expiring items, or one per item so you can dismiss them individually
- **Quiet hours** — no alerts between 10 pm and 8 am
- **Immediate alert** when you add an item that's already within the expiry window

### AI / AppFunctions
Shelfly registers 9 AppFunctions callable by Gemini or any on-device AI agent:

| Function | What it does |
|---|---|
| `addItem` | Add an item with full metadata |
| `addItemFromMfg` | Add using manufacture date + shelf life |
| `listExpiringSoon` | List items expiring within N days |
| `listExpired` | List all expired items |
| `listByCategory` | Filter by Food / Medicine / Cosmetic / Other |
| `getItem` | Look up a single item by ID |
| `markConsumed` | Mark an item as consumed |
| `updateExpiry` | Change the expiry date of an existing item |
| `deleteItem` | Remove an item permanently |

### Onboarding & Settings
- First-launch onboarding wizard with optional notification permission request
- Dynamic color theming (Material You)
- Live notification preview in settings

---

## Tech Stack

| Layer | Library |
|---|---|
| Language | Kotlin 2.2.21 |
| UI | Jetpack Compose + Material 3 |
| Architecture | ViewModel + StateFlow + Unidirectional data flow |
| Database | Room 2.8.4 (KSP) |
| Dependency Injection | Hilt 2.59.2 (KSP) |
| Background Work | WorkManager 2.9.1 (HiltWorker) |
| Preferences | DataStore Preferences |
| Navigation | Navigation Compose |
| AppFunctions | `androidx.appfunctions:1.0.0-rc01` |
| Build | AGP 9.1 · Gradle 9.3 · KSP 2.3 |

**Min SDK:** 33 (Android 13) · **Target SDK:** 36 (Android 16) · **Compile SDK:** 37

AppFunctions are runtime-gated to API 36+ — the app runs normally on API 33–35 without the AI agent integration.

---

## Screenshots

<div align="center">

### Light Mode
| Home | Add / Edit | Detail | Filter | Settings |
|:---:|:---:|:---:|:---:|:---:|
| <img src="assets/home_light.png" width="180"> | <img src="assets/add_light.png" width="180"> | <img src="assets/detail_light.png" width="180"> | <img src="assets/filter_light.png" width="180"> | <img src="assets/settings_light.png" width="180"> |

### Dark Mode
| Home | Add / Edit | Detail | Filter | Settings |
|:---:|:---:|:---:|:---:|:---:|
| <img src="assets/home_dark.png" width="180"> | <img src="assets/add_dark.png" width="180"> | <img src="assets/detail_dark.png" width="180"> | <img src="assets/filter_dark.png" width="180"> | <img src="assets/settings_dark.png" width="180"> |

</div>

---

## Building

```bash
git clone https://github.com/ishubhamsingh/Shelfly.git
cd Shelfly
./gradlew assembleDebug
```

Requires JDK 17+, an Android Studio version that supports **AGP 9.1**, and the **Android SDK Platform 37** (`platforms;android-37.0`). Runs on a device / emulator with **Android 13 (API 33)** or higher.

> AppFunctions need API 36 or newer. On API 33–35 the app runs normally but the AI agent integration is unavailable.
> For ADB testing, use an **API 37 Google APIs** emulator image: the API 36 emulator image ships with the AppFunctions flags disabled, so `cmd app_function` is unavailable there.

---

## Verifying AppFunctions (ADB)

```bash
# List registered functions
adb shell cmd app_function list-app-functions --package dev.ishubhamsingh.shelfly

# Call the ping function
adb shell cmd app_function execute-app-function \
  --package dev.ishubhamsingh.shelfly \
  --function dev.ishubhamsingh.shelfly.appfunctions.ShelflyAppFunctionService#ping \
  --parameters '{"message":"hello"}'
```

---

## License

```
Copyright 2026 Shubham Singh

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
