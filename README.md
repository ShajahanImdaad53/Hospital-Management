# 🏥 NexusHealth - Clinical Management System

A modern, full-featured clinical management system built with Spring Boot 4.0.5 and Java 25. Manage appointments, consultations, doctors, patients, and pharmacist operations efficiently.

---

## 📋 Features

- 👨‍⚕️ **Doctor Management** - Register and manage doctors, specializations, and schedules
- 👥 **Patient Management** - Patient profiles, medical history, and demographics
- 📅 **Appointment Booking** - Streamlined appointment scheduling and cancellation
- 💬 **Consultations** - Consultation records and documentation
- 💊 **Pharmacy Management** - Medication inventory and prescriptions
- 👔 **Role-Based Access** - Admin, Doctor, Patient, Pharmacist, Receptionist roles
- 🔐 **Secure Authentication** - Spring Security with encrypted password handling
- 📊 **Dashboard** - Role-specific dashboards with key metrics
- 💾 **SQLite Database** - Lightweight, embedded database with automatic schema initialization

---

## 🚀 Quick Start

### ⚙️ Prerequisites

- **Java**: Java 25 (LTS) or higher
- **Maven**: 3.9.15+ (included as Maven Wrapper)
- **Git**: For cloning and version control
- **VSCode** (optional): For IDE-based execution

---

## 🎯 How to Run

### Option 1: 🖥️ Command Line (Windows/macOS/Linux)

1. **Clone the repository**:
   ```bash
   git clone https://github.com/ShajahanImdaad53/ZibrijProject.git
   cd NexusHealth
   ```

2. **Set Java environment** (if needed):
   ```bash
   # Windows PowerShell
   $env:JAVA_HOME = 'C:\Users\<YourUsername>\.jdk\jdk-25.0.2'
   
   # macOS/Linux
   export JAVA_HOME=/path/to/jdk-25
   ```

3. **Run the application**:
   ```bash
   # Windows
   .\mvnw spring-boot:run
   
   # macOS/Linux
   ./mvnw spring-boot:run
   ```

4. **Access the application**:
   - Open your browser and navigate to: **http://localhost:8081**
   - Login with admin credentials or create a new account

---

### Option 2: 🔵 VSCode (Recommended for Development)

#### Setup

1. **Open VSCode**:
   - Open the `NexusHealth` project folder in VSCode

2. **Install Extensions** (if not already installed):
   - Extension Pack for Java (Microsoft)
   - Spring Boot Extension Pack
   - Maven for Java (Microsoft)

3. **Configure Java Home** (if needed):
   - Press `Ctrl+Shift+P` (or `Cmd+Shift+P` on macOS)
   - Type: `Java: Configure Java Runtime`
   - Select or point to Java 25 installation

#### Running from VSCode

**Method 1: Using Run Configuration (Easiest)**

1. Open the terminal in VSCode: `Ctrl+` (backtick)
2. Run the command:
   ```bash
   .\mvnw spring-boot:run
   ```
3. Wait for "Started ClinicApplication" message

**Method 2: Using Maven Explorer (GUI)**

1. Click the Maven icon in the VSCode sidebar (looks like 🔄)
2. Expand `Clinic` → `Plugins` → `spring-boot`
3. Right-click `spring-boot:run` → **Run**

**Method 3: Using Debug Configuration**

1. Go to **Run and Debug** (Ctrl+Shift+D)
2. Create a new launch configuration:
   ```json
   {
       "name": "Spring Boot Debug",
       "type": "java",
       "name": "Spring Boot App",
       "request": "launch",
       "mainClass": "com.NexusHelth.ClinicApplication",
       "preLaunchTask": "maven: clean",
       "args": ""
   }
   ```
3. Click **Run** (▶️)

#### VSCode Tips

- 💡 **Hot Reload**: Use Spring Boot DevTools for automatic restart on file changes
- 🐛 **Debugging**: Set breakpoints by clicking line numbers, then run in debug mode (F5)
- 📝 **Terminal**: Use the integrated terminal (Ctrl+`) to run Maven commands
- 🔍 **Code Navigation**: Ctrl+Click on class names to jump to definitions
- 🧹 **Format Code**: Right-click → Format Document (Alt+Shift+F)

---

## 📊 Build & Test

### Clean Build
```bash
.\mvnw clean install
```

### Compile Only
```bash
.\mvnw clean compile
```

### Run Tests
```bash
.\mvnw clean test
```

### Generate Project Reports
```bash
.\mvnw clean test-compile
```

---

## 🗄️ Database

- **Type**: SQLite (embedded, no external installation needed)
- **Location**: `clinic.db` in the project root
- **Auto-Init**: Schema initializes automatically on first run
- **Data**: Includes sample admin account and test data

### Access Database

1. Download **DB Browser for SQLite**: https://sqlitebrowser.org/
2. Open `clinic.db` file
3. Browse tables and data

---

## 📦 Project Structure

```
NexusHealth/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/NexusHelth/
│   │   │       ├── ClinicApplication.java      (Main entry point)
│   │   │       ├── config/                     (Spring config)
│   │   │       ├── controller/                 (REST/Web controllers)
│   │   │       ├── dto/                        (Data transfer objects)
│   │   │       ├── model/                      (JPA entities)
│   │   │       ├── service/                    (Business logic)
│   │   │       └── util/                       (Helper utilities)
│   │   └── resources/
│   │       ├── application.properties          (Server config)
│   │       ├── schema.sql                      (Database schema)
│   │       ├── static/                         (JS, CSS, images)
│   │       └── templates/                      (HTML/Thymeleaf)
│   └── test/                                   (Unit/Integration tests)
├── .mvn/                                       (Maven wrapper)
├── mvnw / mvnw.cmd                            (Maven wrapper scripts)
├── pom.xml                                     (Maven configuration)
└── README.md                                   (This file)
```

---

## ⚙️ Configuration

### Server Port

Edit `src/main/resources/application.properties`:
```properties
server.port=8081
```

### Database Path

Default: `clinic.db` in project root. To change:
```properties
spring.datasource.url=jdbc:sqlite:/path/to/your/clinic.db
```

### Enable Debug Logging

```properties
logging.level.root=INFO
logging.level.com.NexusHelth=DEBUG
```

---

## 🔐 Default Login

- **Username**: admin
- **Password**: (Set during first signup or check database)

---

## 🛠️ Tech Stack

| Component | Version | Details |
|-----------|---------|---------|
| **Java** | 25 | Latest LTS version |
| **Spring Boot** | 4.0.5 | Web framework |
| **Spring Security** | 7.0.4 | Authentication & authorization |
| **Thymeleaf** | 3.x | Template engine |
| **SQLite JDBC** | 3.44.0.0 | Database driver |
| **Tomcat** | 11.0.20 | Embedded servlet container |
| **Maven** | 3.9.15 | Build tool |

---

## 📖 Copy to Another Computer

See [PORTABILITY.md](PORTABILITY.md) for detailed instructions on transferring the application to another machine.

---

## 🐛 Troubleshooting

### Port Already in Use
If port 8081 is already in use:
```properties
# In application.properties, change to:
server.port=8082
```

### Maven Build Fails
```bash
# Clear Maven cache
.\mvnw clean

# Rebuild
.\mvnw clean install
```

### Database Lock Error
Delete the old `clinic.db` file and restart:
```bash
del clinic.db
.\mvnw spring-boot:run
```

### Java Version Mismatch
Ensure Java 25 is set:
```bash
# Check Java version
java -version

# Set JAVA_HOME to Java 25
$env:JAVA_HOME = 'C:\path\to\java25'
```

---

## 📝 Recent Updates

- ✅ **Upgraded to Java 25** - Latest LTS version for modern features and security
- ✅ **Spring Boot 4.0.5** - Latest stable release
- ✅ **CVE Vulnerability Scanned** - All dependencies verified for security
- ✅ **VSCode Integration Guide** - Complete setup and run instructions

---

## 📞 Support

For issues or questions:
1. Check the Troubleshooting section above
2. Review Spring Boot documentation: https://spring.io/projects/spring-boot
3. Check SQLite JDBC documentation: https://github.com/xerial/sqlite-jdbc

---

## 📄 License

This project is part of the Zibrij initiative.

---

## 👤 Author

- **GitHub**: [ShajahanImdaad53](https://github.com/ShajahanImdaad53)
- **Repository**: [ZibrijProject](https://github.com/ShajahanImdaad53/ZibrijProject)

---

**Happy Coding! 🚀**
