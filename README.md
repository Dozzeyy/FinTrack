# FinTrack - Count every penny (AKA Xpent)

FinTrack is a robust, privacy-focused Android expense tracker designed to help you manage your finances with ease. It features automated transaction detection, biometric security, and a flexible budget management system.

## 🚀 Key Features

- **Automated SMS Detection:** Automatically parse transaction messages to save you time.
- **Multi Category:** In same Add transaction screen, pick more than one expense/ income categories to save your time by avoiding creation of multiple entries for the same shopping transaction.
- **Web App (Basic) :** Creates a local server to record transaction on any devices connected in your local network.
- **WebDAV Support:** Auto sync your database to any WebDAV supported cloud provider with optional E2EE support.
- **Biometric Security:** Secure your financial data with Fingerprint, Face ID, or System PIN.
- **Budgeting System:** Set targets for any category (Daily, Weekly, Monthly, Yearly) and track your progress in real-time.
- **Advanced Tags:** Organize transactions with multi-select tags for deep analytical insights.
- **Integrated Calculator:** Perform quick calculations directly within the amount field.
- **Global Search:** Search through all transaction notes, accounts, categories, and tags instantly.
- **Dual Number Systems:** Toggle between **Lakhs/Crores** (Indian) and **Millions/Billions** (International) formatting.
- **Database Management:** 
    - Full manual and scheduled backups.
    - Export transactions to CSV/Excel.
    - Seamlessly import existing database files.

[Visit us for more info](https://vahak.org)

To verify the downloaded APK use this SHA256 fingerprint of the signing certificate - `68:EB:C8:89:43:C4:A7:35:29:E7:D0:1E:C8:02:F9:FF:3A:96:5B:05:3C:8A:40:70:23:7B:6D:A7:A4:ED:F5:90`


Check App screenshots here
<div style="display: flex; justify-content: center; gap: 10px; flex-wrap: wrap;">
  <img src="https://raw.githubusercontent.com/Dozzeyy/FinTrackd/main/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="Screen 1" width="200">
  <img src="https://raw.githubusercontent.com/Dozzeyy/FinTrackd/main/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" alt="Screen 2" width="200">
  <img src="https://raw.githubusercontent.com/Dozzeyy/FinTrackd/main/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" alt="Screen 3" width="200">
  <img src="https://raw.githubusercontent.com/Dozzeyy/FinTrackd/main/fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" alt="Screen 4" width="200">
</div>

<div style="display: flex; justify-content: center; gap: 10px; flex-wrap: wrap;">
  <img src="https://raw.githubusercontent.com/Dozzeyy/FinTrackd/main/fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" alt="Screen 1" width="200">
  <img src="https://raw.githubusercontent.com/Dozzeyy/FinTrackd/main/fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" alt="Screen 2" width="200">
</div>

## 🛠 Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Database:** Room (SQLite with TRUNCATE journal mode for data integrity)
- **Background Tasks:** WorkManager (for scheduled backups)
- **Security:** Android Biometric API

## 📂 Project Structure

- `app/src/main/kotlin/com/openapps/fintrack/data`: Data entities, DAO, and database configuration.
- `app/src/main/kotlin/com/openapps/fintrack/ui`: Composable screens and ViewModel logic.
- `app/src/main/kotlin/com/openapps/fintrack/ui/theme`: App-wide theme configuration (Light, Dark, OLED Dark).

## 📄 License

This project is open-source. You are free to contribute. For inquiries, contact: app.upstream242@passmail.com

---
*Built with ❤️ to help you take control of your financial journey.*
