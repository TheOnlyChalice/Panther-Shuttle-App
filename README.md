# Panther Shuttle App

Panther Shuttle App is an Android application designed to support an on-campus shuttle system with three user roles:

- **Student** – view the live shuttle map, daily schedule, notifications, next stop, and favorite stops
- **Driver** – share live location, view next stop information, estimate student demand, and send notifications
- **Manager** – add/edit/delete shuttle stops and maintain the official weekly schedule

The app uses **Firebase** for authentication and cloud data storage and **Google Maps** for route and stop visualization.

---

## Features

### Student
- View the next scheduled stop and ETA
- View estimated number of students at the next stop
- See the shuttle on a live map when driver sharing is enabled
- Browse the official schedule by day of the week
- Save favorite stops and times
- Receive in-app notifications from the driver
- Enable reminders before favorite stops

### Driver
- Share live shuttle location
- View official stop markers on the map
- See the next stop based on the manager schedule
- Estimate student demand at the next stop
- Send notifications to:
  - all students
  - a specific stop
  - a specific stop and scheduled time

### Manager
- Add stop markers to the map
- Rename, move, or delete stop markers
- Create and edit schedule entries by day of the week
- Add repeated entries across multiple days

---

## Technologies Used

- **Android Studio**
- **Kotlin**
- **Firebase Authentication**
- **Cloud Firestore**
- **Google Maps API**
- **Material Design Components**

---

## Project Structure

- `app/src/main/java/com/example/protoshuttleapp/ui`
  - student, driver, and manager UI logic
- `app/src/main/java/com/example/protoshuttleapp/data`
  - Firebase repository and Firestore data models
- `app/src/main/res/layout`
  - XML layout files
- `app/src/main/res/values`
  - resource values including strings, themes, and Maps key placeholder

---

## Setup Instructions

This public repository does **not** include the real Firebase config file or a real Google Maps API key.

### 1. Clone the repository

### 2. Open in Android Studio

### 3. Get a google API key and add it to the google_maps_api.xml file.

### 4. Go to Firebase and get the .json file
- Go to the Firebase Console
- Create a Firebase project
- Add an Android app to that project
