# JServ

**JServ** is a robust Java-based service that integrates three essential IRC modules—**SpamScan**, **HostServ**, and **NickServ**—into a single, efficient package. Designed specifically for use with `midircd` and `snircd`, JServ streamlines spam detection, hidden host management, and nickname protection for IRC networks.

## Key Features

### Modular Architecture
- **Plugin System:** SpamScan, HostServ, and NickServ are implemented as independent modules
- **Dynamic Module Management:** Modules can be enabled/disabled via configuration without code changes
- **Extended Module Configuration:** Comprehensive JSON-based module definition with className, numeric suffix, and config file mapping
- **Automatic Module Loading:** Modules are automatically instantiated and registered based on configuration
- **Module Lifecycle:** Each module has its own initialization, handshake, and shutdown process
- **Extensible Design:** New modules can be easily added by implementing the Module interface and adding a configuration entry
- **Centralized Management:** ModuleManager handles all module registration, enabling/disabling, and message routing

### SpamScan Module
- **Automated Spam Detection:** Actively scans IRC messages for spam using a customizable badword list.
- **Knocker Bot Detection:** Advanced pattern matching to detect and automatically kill Knocker spambots on connection.
- **Homoglyph Detection:** Identifies and blocks messages containing homoglyphs used for spam.
- **Flood Protection:** Monitors message frequency and automatically takes action against flooding users.
- **Repeat Message Detection:** Tracks and punishes users repeatedly sending identical messages.
- **Time-Based Protection:** Enhanced spam checks for users who joined channels recently (within 5 minutes).
- **Badword Management:** Allows IRC operators (Opers) to add, list, or remove badwords via user-friendly commands.
- **Channel Management:** Opers can add/remove channels from SpamScan monitoring.
- **Operator Commands:** Includes comprehensive help, configuration, and management commands.
- **JSON Config Storage:** Spam detection rules and badword lists are stored in easily editable JSON files.
- **Self-Healing Configs:** Missing config files are automatically generated and maintained.
- **Runtime Control:** Can be enabled/disabled dynamically through configuration
- **Graceful Logout:** Sends proper QUIT command on shutdown

### HostServ Module
- **Hidden Host Management:** Enables authenticated users to set and manage their hidden hosts for privacy.
- **Automatic VHost Application:** Automatically applies virtual hosts on user authentication (AC command).
- **Automatic VHost on Connect:** Sets virtual hosts for users connecting with registered accounts.
- **Weekly Host Change:** Users can change their hidden host once every seven days.
- **Operator Privileges:** Opers can manually set or update hidden hosts for any user.
- **Secure Storage:** Host information is securely stored and managed via PostgreSQL.
- **Runtime Control:** Can be enabled/disabled dynamically through configuration
- **Graceful Logout:** Sends proper QUIT command on shutdown

### NickServ Module
- **Nickname Protection:** Protects registered nicknames from unauthorized use.
- **Authentication Enforcement:** Users must authenticate within 60 seconds or be disconnected.
- **Account Matching:** Verifies that authenticated users match the registered nickname owner.
- **Grace Period:** Configurable warning period before automatic disconnection.
- **PROTECT Flag:** Users with PROTECT flag can bypass all nickname protection.
- **Oper/Service Exemption:** IRC operators and services are automatically exempted from protection.
- **Nick Change Detection:** Monitors and enforces protection when users change to registered nicknames.
- **Dummy-Nick System:** Creates placeholder users to block nicknames instead of network-wide bans.
- **Database Integration:** Checks registered nicknames against the PostgreSQL database.
- **User Notifications:** Sends warnings and success messages for authentication status.
- **INFO Command:** Comprehensive nickname information including all reserved nicks and formatted dates.
- **Runtime Control:** Can be enabled/disabled dynamically through configuration
- **Graceful Logout:** Sends proper QUIT command on shutdown

### Channel and User Management
- **Channel Modes and Permissions:** Manage channel modes, user roles (op, voice), and moderation status.
- **User Tracking:** Track users, their flood/repeat status, channels, registration, operator status, and service flags.
- **Bidirectional User-Channel Tracking:** Users track their channels and channels track their users for reliable spam detection.
- **Join Timestamp Tracking:** Records exact join times for time-based spam protection.
- **Account History:** Log changes to user accounts, including password and email updates.
- **Channel Statistics:** Track channel creation, last active time, stats resets, ban durations, founder, and user join stats.
- **Proper User Addition:** Users and channels are correctly synchronized on connect, JOIN, and BURST commands.

### Database Integration
- **PostgreSQL Backend:** All persistent data (host info, channel/user details, configs) are handled via PostgreSQL for reliability and security.
- **Transaction Support:** Robust transaction handling (begin, commit) for data integrity.
- **Schema Management:** Automatic creation and management of database schema and tables.

### Configuration and Extensibility
- **Configurable via JSON:** Both core service and spam/badword management are configured in JSON files for easy customization.
- **Self-Healing Setup:** Automatic generation of missing configurations and database tables at startup.
- **Daemon Support:** Run as a background daemon with automatic PID file management and log rotation.
- **Graceful Shutdown:** Clean shutdown handling with proper resource cleanup.
- **Modular Design:** Independent modules can be enabled/disabled via `config-modules-extended.json`
- **Hot Configuration:** Module states can be changed without recompiling the application

## Compatibility

- **IRC Daemons:** Requires `midircd` and `snircd`
- **Java Runtime:** JRE 17 or newer
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
- Download the latest release: [JServ.zip](https://github.com/WarPigs1602/JServ/releases/download/JServ/JServ.zip)
- Ensure you have JRE 17+ and PostgreSQL installed.

## Usage

### Running JServ

JServ can be started in two modes:

**Foreground Mode (default):**
```sh
java -jar JServ.jar
```
Press `CTRL+C` to stop the application gracefully.

**Daemon Mode (background process):**
```sh
java -jar JServ.jar --daemon
```

When started in daemon mode:
- The process runs detached in the background
- Process ID (PID) is saved to `jserv.pid`
- Output is logged to `jserv.log`, `jserv.out`, and `jserv.err`
- Stop the daemon: `kill $(cat jserv.pid)`

**Command Line Options:**
- `-d, --daemon` - Run in daemon mode (detached background process)
- `-h, --help` - Show help message and exit

### Graceful Shutdown

JServ implements graceful shutdown handling:
- Responds to `CTRL+C` (SIGINT) and `SIGTERM` signals
- Sends QUIT commands for all service bots (SpamScan, HostServ, NickServ)
- Sends SQUIT command to properly disconnect from IRC server
- Shuts down all enabled modules cleanly via ModuleManager
- Waits for QUIT/SQUIT commands to be transmitted (1.5 seconds total)
- Closes database connections cleanly
- Stops all threads properly
- Flushes all log buffers
- Complete graceful disconnect from IRC network before termination

## Module Configuration

Modules are configured in `config-modules-extended.json`:

```json
{
  "modules": [
    {
      "name": "SpamScan",
      "enabled": true,
      "className": "net.midiandmore.jserv.SpamScan",
      "numericSuffix": "AAC",
      "configFile": "config-spamscan.json"
    },
    {
      "name": "HostServ",
      "enabled": true,
      "className": "net.midiandmore.jserv.HostServ",
      "numericSuffix": "AAB",
      "configFile": "config-hostserv.json"
    },
    {
      "name": "NickServ",
      "enabled": true,
      "className": "net.midiandmore.jserv.NickServ",
      "numericSuffix": "AAD",
      "configFile": "config-nickserv.json"
    },
    {
      "name": "SaslServ",
      "enabled": true,
      "className": "net.midiandmore.jserv.SaslServ",
      "numericSuffix": "AAE",
      "configFile": "config-saslserv.json"
    }
  ]
}
```

**Configuration Parameters:**
- `name`: Display name of the module
- `enabled`: Set to `false` to disable the module
- `className`: Full qualified class name for automatic instantiation
- `numericSuffix`: P10 protocol numeric suffix (e.g., AAC, AAB, AAD)
- `configFile`: Module-specific configuration file name

Changes require a restart to take effect.

### Available Modules

- **SpamScan** (`spamscan`): Automated spam detection and badword filtering
- **HostServ** (`hostserv`): Hidden host management for authenticated users
- **NickServ** (`nickserv`): Nickname protection and authentication enforcement
- **SaslServ** (`saslserv`): SASL authentication validation (mechanism `PLAIN`)

### Adding New Modules

To add a new module:

1. Implement the `Module` interface
2. Implement required methods: `initialize()`, `handshake()`, `parseLine()`, `shutdown()`, etc.
3. Add a configuration entry in `config-modules-extended.json`:
   ```json
   {
     "name": "MyModule",
     "enabled": true,
     "className": "net.midiandmore.jserv.MyModule",
     "numericSuffix": "AAE",
     "configFile": "config-mymodule.json"
   }
   ```
4. Create module-specific configuration file if needed
5. Restart JServ - the module will be automatically loaded and registered

Example module implementation:

```java
public class MyModule implements Module {
    private boolean enabled = false;
    
    @Override
    public void initialize(JServ jserv, SocketThread st, PrintWriter pw, BufferedReader br) {
        // Initialize module
    }
    
    @Override
    public void enable() {
        this.enabled = true;
    }
    
    @Override
    public void parseLine(String text) {
        if (!enabled) return;
        // Process IRC protocol line
    }
    
    @Override
    public String getNumericSuffix() {
        return "AAE"; // Must match config
    }
    
    // Implement other required methods...
}
```

The ModuleManager will automatically instantiate your module using reflection based on the `className` field.

## Operator & User Commands

### NickServ User Commands
- **INFO <nickname>** - Display comprehensive information about a registered nickname, including account details, registration date, and all reserved nicknames
- **HELP** - Show help information
- **SHOWCOMMANDS** - List all available commands
- **VERSION** - Show version information

For detailed information about NickServ commands and features, see [README_NICKSERV.md](README_NICKSERV.md).

### SpamScan & HostServ Commands
Refer to the built-in help system or project wiki for a complete list of operator and user commands for SpamScan and HostServ.

## Important Notes

- Ensure PostgreSQL is running and accessible by JServ before starting the service.
- Configuration files are auto-generated if missing, but manual review is recommended for customization.
- Modules must be enabled in `config-modules-extended.json` to be active.
- Each module has its own configuration file (e.g., `config-spamscan.json`, `config-hostserv.json`).
- Module numeric suffixes (AAC, AAB, AAD) must be unique and follow P10 protocol standards.
- Disabling a module prevents it from processing IRC messages and reduces resource usage.
- All services properly log out with QUIT commands when shutting down.
- SpamScan requires proper channel and user tracking to function correctly.
- In daemon mode, all logs are written to files; use `tail -f jserv.log` for monitoring.

## Configuration Files

- `config-jserv.json` - Main JServ configuration (server connection, numeric, etc.)
- `config-modules-extended.json` - Extended module configuration with class names and numeric suffixes
- `config-spamscan.json` - SpamScan module configuration
- `config-hostserv.json` - HostServ module configuration
- `config-nickserv.json` - NickServ module configuration
- `badwords-spamscan.json` - Badword list for SpamScan

## License

MIT License © 2024-2025 Andreas Pschorn
MIT License © 2024-2025 Andreas Pschorn
