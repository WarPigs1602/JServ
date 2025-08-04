# JServ

**JServ** is a robust Java-based service that integrates two essential IRC modules—**SpamScan** and **HostServ**—into a single, efficient package. Designed specifically for use with `midircd` and `snircd`, JServ streamlines spam detection and hidden host management for IRC networks.

## Key Features

### SpamScan
- **Automated Spam Detection:** Actively scans IRC messages for spam using a customizable badword list.
- **Badword Management:** Allows IRC operators (Opers) to add, list, or remove badwords via user-friendly commands.
- **Operator Commands:** Includes comprehensive help, configuration, and management commands.
- **JSON Config Storage:** Spam detection rules and badword lists are stored in easily editable JSON files.
- **Self-Healing Configs:** Missing config files are automatically generated and maintained.

### HostServ
- **Hidden Host Management:** Enables authenticated users to set and manage their hidden hosts for privacy.
- **Weekly Host Change:** Users can change their hidden host once every seven days.
- **Operator Privileges:** Opers can manually set or update hidden hosts for any user.
- **Secure Storage:** Host information is securely stored and managed via PostgreSQL.

### Channel and User Management
- **Channel Modes and Permissions:** Manage channel modes, user roles (op, voice, admin, service, owner, etc.), and moderation status.
- **User Tracking:** Track users, their flood/repeat status, channels, registration, operator status, and service flags.
- **Account History:** Log changes to user accounts, including password and email updates.
- **Channel Statistics:** Track channel creation, last active time, stats resets, ban durations, founder, and user join stats.

### Database Integration
- **PostgreSQL Backend:** All persistent data (host info, channel/user details, configs) are handled via PostgreSQL for reliability and security.
- **Transaction Support:** Robust transaction handling (begin, commit) for data integrity.
- **Schema Management:** Automatic creation and management of database schema and tables.

### Configuration and Extensibility
- **Configurable via JSON:** Both core service and spam/badword management are configured in JSON files for easy customization.
- **Self-Healing Setup:** Automatic generation of missing configurations and database tables at startup.

## Compatibility

- **IRC Daemons:** Requires `midircd` and `snircd`
- **Java Runtime:** JRE 17 or newer
- **Database:** PostgreSQL

## Installation

### 1. Build from Source
- Recommended: Use an IDE with Maven support for seamless compilation.
- Clone the repository and build using Maven:
  ```sh
  git clone https://github.com/WarPigs1602/JServ.git
  cd JServ
  mvn package
  ```

### 2. Use Precompiled Release
- Download the latest release: [JServ.zip](https://github.com/user-attachments/files/21579038/JServ.zip)
- Ensure you have JRE 17+ and PostgreSQL installed.

## Usage

Start JServ as a background service:
```sh
java -jar JServ.jar &
exit
```

## Operator & User Commands

Refer to the built-in help system or project wiki for a complete list of operator and user commands for SpamScan and HostServ.

## Important Notes

- Ensure PostgreSQL is running and accessible by JServ before starting the service.
- Configuration files are auto-generated if missing, but manual review is recommended for customization.

## License

MIT License © 2024-2025 Andreas Pschorn
