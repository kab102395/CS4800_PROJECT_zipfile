# Stanislaus State University Themed Runner Game

A school-themed endless runner game built with Defold game engine, featuring Stanislaus State University branding and a Java backend with SQLite database for score tracking.

## 🎮 Features

- **Stan State Themed**: Custom visuals including Naraghi Hall background, school-colored character, and "Extra Credit" collectibles
- **Endless Runner**: Jump over obstacles and collect extra credit items
- **Score Tracking**: Java backend with SQLite database records your runs
- **Leaderboard**: View top 10 runs with scores and extra credit collected
- **School Accessories**: Character equipped with backpack
- **Custom Character**: Spine-animated hero with school-themed colors (Cardinal Red and Gold)

## 🛠️ Technologies

- **Game Engine**: Defold
- **Scripting**: Lua
- **Backend**: Java (Spark framework)
- **Database**: SQLite
- **Build Tool**: Gradle

## 📋 Prerequisites

- [Defold Editor](https://defold.com/download/) (latest version)
- Java JDK 11 or higher
- Gradle (or use the included Gradle wrapper)

## 🚀 Getting Started

### Game Setup

1. **Open in Defold**:
   - Open Defold Editor
   - Select `File → Open Project`
   - Navigate to this project folder
   - Click `Open`

2. **Build and Run**:
   - Press `F5` or click `Project → Build and Run`
   - The game will launch automatically

### Backend Setup

1. **Navigate to backend directory**:
   cd backend
   2. **Make Gradle wrapper executable** (if needed):
   chmod +x gradlew
   3. **Run the backend server**:
 
   ./gradlew run
      Or on Windows:
   gradlew.bat run
   4. **Server will start on port 7070** (or PORT environment variable if set)

## 🎯 How to Play

- **Jump**: Press `Space` or click/tap the screen
- **Collect Extra Credit**: Jump to collect the extra credit items
- **Avoid Hazards**: Don't hit the books!
- **Score**: Your score increases with distance traveled and extra credit collected
