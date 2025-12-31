# JServ

**JServ** is a robust Java-based service that integrates essential IRC modules—**SpamScan**, **HostServ**, **NickServ**, **SaslServ**, **ChanServ**, **AuthServ**, and **OperServ**—into a single, efficient package. Designed specifically for use with **JIRCd** (Java IRC Daemon), JServ streamlines spam detection, hidden host management, nickname protection, authentication, channel management, and operator services for IRC networks.

## Key Features

### Modular Architecture
- **Plugin System:** SpamScan, HostServ, NickServ, SaslServ, ChanServ, AuthServ, and OperServ are implemented as independent modules
- **Dynamic Module Management:** Modules can be enabled/disabled via configuration without code changes
- **Extended Module Configuration:** Comprehensive JSON-based module definition with className, numeric suffix, and config file mapping
- **Automatic Module Loading:** Modules are automatically instantiated and registered based on configuration
- **Module Lifecycle:** Each module has its own initialization, handshake, and shutdown process
- **Extensible Design:** New modules can be easily added by implementing the Module interface and adding a configuration entry
- **Centralized Management:** ModuleManager handles all module registration, enabling/disabling, and message routing

### SpamScan Module

**Purpose:** Provides automated spam detection and prevention for IRC channels. SpamScan actively monitors channel messages, user behavior, and connection patterns to identify and block spam, flood attacks, and malicious bots in real-time.

**Key Capabilities:**
- **Automated Spam Detection:** Actively scans IRC messages for spam using a customizable badword list
- **Knocker Bot Detection:** Advanced pattern matching to detect and automatically kill Knocker spambots on connection
- **Homoglyph Detection:** Identifies and blocks messages containing homoglyphs used for spam
- **Flood Protection:** Monitors message frequency and automatically takes action against flooding users
- **Repeat Message Detection:** Tracks and punishes users repeatedly sending identical messages
- **Time-Based Protection:** Enhanced spam checks for users who joined channels recently (configurable time window, default 5 minutes)
- **Dual Detection Modes:** Normal and Lax spam detection modes with separate thresholds
- **User Classification:** Distinguishes between new and established users with different threshold levels
- **Configurable Thresholds:** Separate repeat and flood thresholds for new vs. established users in both detection modes
- **Similarity Detection:** Advanced message similarity detection using configurable similarity threshold (default 0.8)
- **Cross-Channel Spam Detection:** Detects spam patterns across multiple channels with dedicated time window and similarity threshold
- **Suspicious Ident Detection:** Automatically detects and kills users with suspicious idents (root, admin, etc.)
- **Suspicious TLD Detection:** Monitors and flags messages containing suspicious top-level domains (tk, ml, ga, etc.)
- **Score-Based System:** Dynamic spam scoring with configurable decay rate and interval for rehabilitating good behavior
- **Extreme Spam Threshold:** Configurable threshold for instant action against extreme spam behavior
- **G-Line Support:** Automatic network-wide bans (G-Lines) after repeated violations
  - Configurable kill threshold before G-Line activation (default: 3 kills)
  - Adjustable G-Line duration (default: 24 hours)
  - Custom G-Line reason with violation URL support
- **Badword Management:** Allows IRC operators (Opers) to add, list, or remove badwords via user-friendly commands
- **Channel Management:** Opers can add/remove channels from SpamScan monitoring
- **Operator Commands:** Comprehensive help, configuration, and management commands including GLINESTATS
- **JSON Config Storage:** Spam detection rules and badword lists are stored in easily editable JSON files
- **Self-Healing Configs:** Missing config files are automatically generated and maintained
- **Kill Tracking:** Maintains statistics of killed users for G-Line enforcement
- **Runtime Control:** Can be enabled/disabled dynamically through configuration
- **Graceful Logout:** Sends proper QUIT command on shutdown

### HostServ Module

**Purpose:** Manages virtual hosts (vhosts) for authenticated users, providing privacy and customization options. HostServ allows users to hide their real IP addresses behind custom hostnames, enhancing security and allowing personalized network presence.

**Key Capabilities:**
- **Hidden Host Management:** Enables authenticated users to set and manage their hidden hosts for privacy.
- **Automatic VHost Application:** Automatically applies virtual hosts on user authentication (AC command).
- **Automatic VHost on Connect:** Sets virtual hosts for users connecting with registered accounts.
- **Weekly Host Change:** Users can change their hidden host once every seven days.
- **Operator Privileges:** Opers can manually set or update hidden hosts for any user.
- **Secure Storage:** Host information is securely stored and managed via PostgreSQL.
- **Runtime Control:** Can be enabled/disabled dynamically through configuration
- **Graceful Logout:** Sends proper QUIT command on shutdown

### NickServ Module

**Purpose:** Protects registered nicknames from unauthorized use through automated enforcement. NickServ ensures that only the rightful owners can use their registered nicknames by requiring authentication within a grace period.

**Key Capabilities:**
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

### SaslServ Module

**Purpose:** Implements server-to-server SASL authentication protocol for secure pre-connection authentication. SaslServ validates user credentials before they fully connect to the network, providing enhanced security and seamless authentication for IRC clients.

**Key Capabilities:**
- **SASL Authentication:** Server-to-server SASL validation protocol for JIRCd
- **PLAIN Mechanism Support:** Implements SASL PLAIN authentication mechanism
- **Database Integration:** Authenticates users against PostgreSQL database
- **Relay Mode Support:** Optional relay authentication to external control services (e.g., mIAuthd)
- **Account Token Generation:** Generates extended account tokens (username:timestamp:id) for JIRCd
- **Configurable Account Parameters:** Customizable account name and ID in N-Line registration
- **Timeout Handling:** Configurable relay timeout with automatic failure handling
- **Remote Authentication:** MD5-based digest authentication for relay mode
- **Config Fallback:** Optional fallback to config-based authentication if database fails
- **Runtime Control:** Can be enabled/disabled dynamically through configuration
- **Graceful Logout:** Sends proper QUIT command on shutdown

### ChanServ Module

**Purpose:** Manages channel registration, ownership, and access control. ChanServ allows users to register channels, maintain persistent channel settings, and control user access through a comprehensive permission system.

**Key Capabilities:**
- **Channel Registration:** Register channels with founder privileges and persistent ownership
- **Access Level Management:** Control user access with OP, VOICE, and BAN flags
- **Automatic Mode Application:** Automatically apply channel modes based on user access levels upon join
- **Channel Settings:** Configure AUTOOP, PROTECT, AUTOLIMIT, and other channel behaviors
- **Topic Management:** Set, lock, and manage channel topics with protection
- **Ban List Management:** Maintain and enforce channel ban lists
- **User Commands:** REGISTER, ADDUSER, DELUSER, MODUSER, LISTUSERS, SET, INFO, UNREGISTER
- **Operator Commands:** CHANLIST for network-wide channel overview
- **Runtime Control:** Can be enabled/disabled dynamically through configuration
- **Graceful Logout:** Sends proper QUIT command on shutdown

### AuthServ Module

**Purpose:** Provides centralized user account management and authentication services. AuthServ handles user registration, password management, and authentication, serving as the foundation for other services like ChanServ and HostServ.

**Key Capabilities:**
- **Account Registration:** Register new accounts with email verification and auto-generated passwords
- **Secure Authentication:** IDENTIFY command for account login with database validation
- **Password Management:** Change passwords (PASSWD), request new passwords (REQUESTPASSWORD), and cancel pending changes (RESETPASSWORD)
- **Email Notifications:** Automated email system for registration, password changes, and recovery
- **User Flags System:** Comprehensive permission system (+n, +i, +c, +h, +q, +o, +a, +d, +p, +T flags)
- **Account Information:** View account details, creation date, and last login (INFO, STATUS commands)
- **Account Deletion:** Secure account deletion with password confirmation
- **One Registration Per Session:** Prevents abuse by limiting registrations per connection
- **Integration:** Automatically applies channel modes via ChanServ after authentication
- **Runtime Control:** Can be enabled/disabled dynamically through configuration
- **Graceful Logout:** Sends proper QUIT command on shutdown

### OperServ Module

**Purpose:** Provides network operator tools for network management, monitoring, and security enforcement. OperServ offers commands for G-Line management, trust control, and network statistics accessible only to IRC operators.

**Key Capabilities:**
- **G-Line Management:** Add, remove, list, and synchronize network-wide bans (GLINE, UNGLINE, GLINELIST)
- **Trust System:** Manage connection trust rules for IP-based access control (TRUSTADD, TRUSTDEL, TRUSTGET, TRUSTSET, TRUSTLIST)
- **Network Statistics:** View network stats, active G-Lines, and trust rules (STATS)
- **Channel Management:** List all channels with user counts (CHANLIST)
- **User Management:** KILL command for disconnecting abusive users
- **Raw Commands:** Send raw IRC protocol commands for advanced operations (RAW)
- **Automatic Cleanup:** Timer-based cleanup of expired G-Lines
- **Network Synchronization:** Sync G-Lines with network on startup
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

- **IRC Daemons:** Designed for **JIRCd** (Java IRC Daemon) using P10 protocol
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
  - Dual detection modes (Normal/Lax) with configurable thresholds
  - Advanced similarity detection and cross-channel spam tracking
  - Suspicious ident and TLD detection
  - Score-based system with decay for behavior rehabilitation
  - Automatic G-Line enforcement after repeated violations
  - Comprehensive operator commands and statistics
- **HostServ** (`hostserv`): Hidden host management for authenticated users
- **NickServ** (`nickserv`): Nickname protection and authentication enforcement
- **SaslServ** (`saslserv`): SASL authentication validation (mechanism `PLAIN`)
  - Implements server-to-server SASL protocol for JIRCd
  - Supports database and config-based authentication
  - Optional relay mode for external authentication services
  - Configurable account parameters via `config-saslserv.json`
- **ChanServ** (`chanserv`): Channel registration and management service
  - Channel registration with founder privileges
  - User access level management (OP, VOICE, BAN)
  - Automatic mode setting based on user flags
  - Channel settings (AUTOOP, PROTECT, AUTOLIMIT)
  - Topic protection and management
  - Ban list management
  - Channel information and user list commands
- **AuthServ** (`authserv`): Account management and authentication service
  - User account registration with email verification
  - Secure password management (change, reset, recovery)
  - Account identification and authentication
  - User flags and permission management
  - Account deletion and information display
  - Email notification system for password changes
  - Integration with ChanServ for automatic channel mode application
- **OperServ** (`operserv`): Operator service for network management
  - Network statistics and monitoring
  - G-Line management (add, remove, list, sync)
  - Trust management for connection control (TRUSTADD, TRUSTDEL, TRUSTLIST)
  - Channel and user listing
  - Raw command sending for advanced operations
  - Kill command for user management
  - Automatic G-Line cleanup and synchronization

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

### Core Configuration
- `config.json` - Main JServ configuration (server connection, numeric, database settings, SMTP)
- `config-modules.json` - Basic module configuration (deprecated, use extended version)
- `config-modules-extended.json` - Extended module configuration with class names, numeric suffixes, and config file mappings

### Module Configuration
- `config-spamscan.json` - SpamScan module settings (nick, servername, description, identd, detection thresholds)
- `config-hostserv.json` - HostServ module settings (nick, servername, description, identd)
- `config-nickserv.json` - NickServ module settings (nick, servername, description, identd, grace period)
- `config-saslserv.json` - SaslServ module settings (account name, ID, relay settings, timeout)
- `config-chanserv.json` - ChanServ module settings (nick, servername, description, identd)
- `config-authserv.json` - AuthServ module settings (nick, servername, description, identd)
- `config-operserv.json` - OperServ module settings (nick, servername, description, identd)
- `config-trustcheck.json` - TrustCheck allow rules (legacy fallback if database has no trust rules)

### Data Files
- `badwords-spamscan.json` - Badword list for SpamScan spam detection
- `email-templates.json` - Email templates for AuthServ notifications (registration, password changes)

## TrustCheck (TC/TR)

JServ can act as a **TrustCheck server** for JIRCd's TC/TR protocol.

- Enable TrustCheck by setting `trustserver` in `config.json`.
- JServ only evaluates TC requests when `servername` equals `trustserver`.

Add to `config.json`:

```json
{"name":"trustserver","value":"trust.example.net"},
{"name":"trustcheck_timeout_ms","value":"2000"}
```

Allow rules are primarily evaluated from the database table `operserv.trusts` (managed via OperServ commands). Based on the evaluation, JServ replies:
- `TR ... OK` - Connection allowed (rule matched)
- `TR ... IGNORE` - No rule matched, but not explicitly denied (default behavior)
- `TR ... FAIL` - Connection explicitly denied (fail-closed mode, if configured)

OperServ commands:

- `TRUSTADD <mask> [maxconn] [ident]`
- `TRUSTDEL <mask>`
- `TRUSTGET <mask>`
- `TRUSTSET <mask> [maxconn] [ident|noident]`
- `TRUSTLIST [limit]`

Rule masks support `*` and `?` and typically use `ident@ip` (example: `*admin*@192.0.2.*`).
`maxconn` is the maximum simultaneous connections for that IP (0 = unlimited). `ident` forces a non-empty ident.

If the database contains **no** trust rules, JServ falls back to `config-trustcheck.json` for backward compatibility.

Example `config-trustcheck.json`:

```json
[
  {"name":"rule1","value":"trustedident@203.0.113.*"},
  {"name":"rule2","value":"vpn*@2001:db8:*"}
]
```

## License

MIT License © 2024-2025 Andreas Pschorn
MIT License © 2024-2025 Andreas Pschorn
