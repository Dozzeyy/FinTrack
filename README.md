# FinTrack - Count every penny

FinTrack is a robust, privacy-focused Android expense tracker designed to help you manage your finances with ease. It features automated transaction detection, biometric security, and a flexible budget management system.

## 🚀 Key Features

- **Automated SMS Detection:** Automatically parse transaction messages to save you time.
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

Check App screenshots here
![SS 1](https://github.com/Dozzeyy/FinTrack/blob/main/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png)
![SS 2](https://github.com/Dozzeyy/FinTrack/blob/main/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png)
![SS 3](https://github.com/Dozzeyy/FinTrack/blob/main/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png)
![SS 4](https://github.com/Dozzeyy/FinTrack/blob/main/fastlane/metadata/android/en-US/images/phoneScreenshots/4.png)

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
