# JServ

**JServ** is a robust Java-based service that seamlessly integrates two essential IRC modules—**SpamScan** and **HostServ**—into a single, efficient package. Designed specifically for use with `midircd` and `snircd`, JServ streamlines spam detection and hidden host management for IRC networks.

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

- **SpamScan Commands:**
  - `/badword add <word>`
  - `/badword list`
  - `/badword delete <word>`
  - `/spamscan help`
- **HostServ Commands:**
  - `/hostserv set <host>`
  - `/hostserv info`
  - `/hostserv help`

Refer to the documentation or in-service help commands for a full list and syntax.

## Important Notes

- JServ operates exclusively with `midircd` and `snircd` IRC daemons.
- All configuration and management tasks can be performed via IRC operator commands.
- The system automatically maintains configuration files and database integrity.
- Security and privacy for users are prioritized via proper host masking and secure storage.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for full details.

---

**Author:** Andreas Pschorn (WarPigs)  
**Vendor:** MidiAndMore.Net  
**Version:** 1.0
