# Nitkc Ryousyoku App

[日本語版はこちら (Japanese)](README_ja.md)

This is the native Android application for viewing the Nitkc (Kagawa National College of Technology) dormitory food menu.
It is the native app version of the PWA available at [ryousyoku.m4sak1.me](https://ryousyoku.m4sak1.me/).

## 🌟 Overview
The Nitkc Ryousyoku App is designed to be lightweight, lightning-fast, and completely offline-capable after the initial data fetch. Instead of bundling heavy PDF files and images inside the APK (which would cause the app size to bloat over time), the app dynamically fetches metadata and pre-rendered images from our backend infrastructure.

The core design philosophy is to provide the same sleek, developer-focused, monospace aesthetic of the web version, but with the smooth performance and native integration of an Android application built purely with Jetpack Compose.

## 🏗️ System Architecture & Mechanics

### 1. The Backend Infrastructure (Web/Scraper)
The data for this app is entirely reliant on a backend scraper system hosted via GitHub Actions on the web repository. 
- **Data Source**: The original data is sourced directly from [the official Kagawa NCT Dormitory Menu PDF](https://www.kagawa-nct.ac.jp/dormitoryE/kondate.pdf).
- **Automated Processing**: Every weekend (typically Friday or Saturday), a GitHub Action triggers a Python script (`check_and_process.py`). This script downloads the latest official PDF.
- **Image Generation**: Using `PyMuPDF` (`fitz`), the script parses the PDF to extract the exact date range of the menu (e.g., `20260727-20260802`). It then converts the first page of the PDF into a high-quality 150 DPI PNG image.
- **JSON Metadata**: The script updates a centralized `menus.json` file which acts as the database for both the Web PWA and this Android app.

### 2. The Android Native App (Frontend)
Built entirely in **Kotlin** and **Jetpack Compose**, this app acts as a highly optimized client for the JSON database mentioned above.

- **Smart Menu Selection (The Sunday/Monday Boundary)**:
  The school often uploads next week's menu on Friday or Saturday. If the app immediately displayed the newest PDF, students wouldn't be able to check their meals for the current Saturday and Sunday. 
  To solve this, the app parses the `YYYYMMDD-YYYYMMDD` filename of each menu to extract its active date range. It compares this against the user's current local time. The app will continue displaying the "Current Week" menu throughout the weekend, and automatically switch to the "Next Week" menu exactly at **Monday 00:00**.

- **Aggressive Caching Strategy**:
  - **JSON Fetching**: Uses `OkHttp` and `kotlinx.serialization` to fetch the lightweight `menus.json` from the cloud.
  - **Image Caching (Coil)**: We utilize **Coil** (Coroutine Image Loader) with a heavily optimized disk-cache policy. When a user views a menu for the first time, the PNG is downloaded. From that point on, it is stored in the device's local cache. Even if the user opens the app offline (e.g., in a spot with bad reception in the dormitory), the previously loaded menus will display instantly.
  - **Zero Bloat**: Since images are fetched on-demand and cached locally, the base APK size remains extremely small.

- **UI & Aesthetic Decisions**:
  - **Pixel-Perfect Web Replication**: The margins, border radiuses, and `#ff8c42` theme colors are perfectly aligned with the web app.
  - **No Ripple Effect**: To maintain a sharp, web-like interaction model, the default Android Material "blue ripple effect" on tap has been intentionally disabled using a custom `LocalIndication`.

## 🚀 CI/CD & Automated Releases
The release process is fully automated via GitHub Actions (`.github/workflows/release.yml`).
- **Dev Branch (`dev`)**: Pushing to the `dev` branch triggers a Beta build. The Action automatically increments the beta version counter, builds a release-signed APK, and publishes it to GitHub Releases as a `Pre-release` (e.g., `Pre release - v0.0.0-beta.1`).
- **Main Branch (`main`)**: Merging into the `main` branch triggers a stable build. The patch version is incremented, the beta counter is reset, and the stable APK is published as a `Release` (e.g., `Release - v0.0.1`).
- Because both releases share the exact same `applicationId` (`com.m4sak1.ryousyoku`), users can seamlessly upgrade from a Beta version to a Stable version without uninstalling the app.

## 📥 Downloading
You can download the latest automatically built APK from our GitHub Releases page:
- [Latest Release / 最新リリース](https://github.com/m4sa-k1/nitkc-ryousyoku-app/releases/latest)

## 🤝 Code of Conduct
Please read our [Code of Conduct](CODE_OF_CONDUCT.md) before participating in this project to ensure a welcoming environment for everyone.

## 📄 License
This project is licensed under the MIT License.
