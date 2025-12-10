# NickServ Module

## Overview
NickServ is a service module for JServ that protects registered nicknames from unauthorized use. It automatically disconnects users who use registered nicknames without authentication after a configurable grace period.

## Features
- **Nickname Protection**: Automatically detects when users connect with registered nicknames
- **Grace Period**: Gives users time to authenticate before enforcement (configurable, default 60 seconds)
- **Automatic Enforcement**: Kills (disconnects) unauthenticated users after grace period expires
- **PROTECT Flag**: Users with PROTECT flag can use any nick without enforcement
- **Oper/Service Exemption**: IRC operators and services are automatically exempted from protection
- **Burst Detection**: Correctly handles users during initial server burst
- **Account Matching**: Verifies authenticated users match the registered nickname owner
- **Dummy Nick System**: Creates placeholder nicks to block nicknames instead of network-wide bans
- **Automatic Release**: Dummy nicks are automatically released when legitimate user authenticates
- **Nick Change Monitoring**: Tracks nickname changes and enforces protection on registered nicks
- **P10 Protocol**: Full compatibility with the P10 IRC protocol
- **Real-time Monitoring**: Background timer checks enforcement every 10 seconds
- **User Commands**: Provides information commands (INFO, HELP, SHOWCOMMANDS, VERSION)
- **Database Integration**: Seamless integration with PostgreSQL database
- **Graceful Shutdown**: Sends proper QUIT command when shutting down

## Configuration

### Module Configuration (`config-modules-extended.json`)
Enable NickServ in the modules configuration:
```json
{
  "modules": [
    {
      "name": "NickServ",
      "enabled": true,
      "className": "net.midiandmore.jserv.NickServ",
      "numericSuffix": "AAD",
      "configFile": "config-nickserv.json"
    }
  ]
}
```

### NickServ Configuration (`config-nickserv.json`)
```json
[
  {"name":"nick","value":"NickServ"},
  {"name":"servername","value":"nickserv.example.net"},
  {"name":"description","value":"Nickname Protection Service"},
  {"name":"identd","value":"nickserv"},
  {"name":"grace_period","value":"60"},
  {"name":"check_interval","value":"10000"}
]
```

**Parameters:**
- `nick`: The nickname for the NickServ bot (default: "NickServ")
- `servername`: The server name for NickServ (default: "nickserv.example.net")
- `description`: Service description displayed in WHOIS
- `identd`: The ident string for the service
- `grace_period`: Time in seconds before killing unauthenticated users (default: 60)
- `check_interval`: How often to check for enforcement in milliseconds (default: 10000)

## P10 Protocol Implementation

### Numeric Assignment
NickServ uses numeric suffix **AAD** (e.g., if server numeric is "AB", NickServ will be "ABAAD").

### Protocol Commands Handled

#### User Connection (N command)
```
:AB N TestNick 2 1234567890 ident host.example.com +ir TestUser:1234567890 IP ABCDE :Real Name
```
When a user connects with a registered nickname, NickServ:
1. Checks if in burst mode (ignores if during burst)
2. Checks if user is an oper or service (exempts if true)
3. Checks if user has PROTECT flag (exempts if true)
4. Checks if authenticated account matches registered nickname owner (exempts if matches)
5. If none of the above, adds to enforcement list and sends warning
6. Starts grace period timer

#### Account Authentication (AC command)
```
:ABCDE AC TestUser
```
When a user authenticates, NickServ:
1. Removes them from enforcement list
2. Releases any dummy nick if the authenticated account matches
3. No further action needed

#### Nickname Change (N command - short form)
```
:ABCDE N NewNick
```
Monitors nick changes and:
1. Checks if new nick is registered
2. Applies same protection logic as connection
3. Removes from enforcement list if changing away from protected nick

#### Quit/Kill (Q/D commands)
```
:ABCDE Q :Quit message
```
Automatically removes user from enforcement tracking.

#### Kill Command (D command - sent by NickServ)
```
:ABAAD D ABCDE 1234567890 :Nickname is registered and you failed to authenticate within 60 seconds
```
NickServ sends this to disconnect unauthenticated users after grace period.

## User Commands

Users can send private messages to NickServ:
```
/msg NickServ <command>
```

### Available Commands

#### HELP
Shows help information about NickServ.
```
/msg NickServ HELP
```

#### INFO <nickname>
Displays comprehensive information about a registered nickname.
```
/msg NickServ INFO TestNick
```
Shows:
- Nickname being queried
- Account owner (main account name)
- Nickname type (main account or reserved nickname)
- Registration status
- Formatted registration date and timestamp
- Current authentication status
- Complete list of all reserved nicknames for this account

**Example Output:**
```
***** Nickname Information *****
Nickname   : moep
Account    : WarPigs
Type       : Reserved nickname
Status     : Registered
Registered : 2024-07-22 14:30:37 (timestamp: 1721665037)
Currently  : Not authenticated
Reserved Nicks:
   - moep
   - test
   - foo
***** End of Info *****
```

The INFO command works for both main accounts and reserved nicknames - it will always show the complete information for the entire account.

#### SHOWCOMMANDS
Lists all available commands.
```
/msg NickServ SHOWCOMMANDS
```

#### VERSION
Shows NickServ version information.
```
/msg NickServ VERSION
```

## How It Works

### 1. User Connection Flow
1. User connects to IRC with nickname "JohnDoe"
2. NickServ checks if during burst mode (skips enforcement if true)
3. Checks if "JohnDoe" is registered in the database
4. Checks if user is oper, service, or has PROTECT flag (skips if true)
5. Checks if user is authenticated and account matches (skips if matches)
6. If registered and not exempted:
   - User is added to enforcement list with timestamp
   - NickServ sends warning notices
   - Grace period countdown begins

### 2. Enforcement Flow
1. Background timer runs every 10 seconds (configurable)
2. Skips enforcement if in burst mode
3. For each unauthenticated user:
   - Calculate elapsed time since connection
   - If elapsed time >= grace period (60 seconds):
     - Verify user is still online
     - Verify user is still not authenticated
     - Verify nickname is still registered
     - Verify account mismatch (if authenticated)
     - Send KILL command to disconnect user
     - Remove from enforcement list
     - Log enforcement action

### 3. Authentication Flow
1. User authenticates with Q bot or other auth service
2. Auth service sends AC (Account) command
3. NickServ receives AC command and checks:
   - If user is in enforcement list
   - If authenticated account matches registered nickname owner
4. If account matches:
   - User is removed from enforcement list
   - Any dummy nick for this account is released
   - User can continue using the nick
5. If account doesn't match:
   - User remains in enforcement list
   - Will be killed when grace period expires

### 4. Nickname Change Flow
1. User changes from "JohnDoe" to "JaneDoe"
2. Remove old nick from enforcement list
3. If "JaneDoe" is registered:
   - Apply same protection logic as connection
   - Check exemptions (oper, service, PROTECT flag)
   - Check account matching
   - Add to enforcement list if not exempted
4. If changing away from registered nick:
   - Simply remove from enforcement list

### 5. Burst Mode Handling
1. During server burst (EB not received):
   - All enforcement is disabled
   - Users are not added to enforcement list
   - No warnings are sent
2. After burst ends (EA received):
   - Normal enforcement resumes
   - Existing connected users are checked
   - Timer begins enforcement checks

## Database Integration

NickServ uses the existing JServ database to check if nicknames are registered. It queries the `chanserv.users` table:

```java
int userId = jserv.getDb().getIndex(nickname);
boolean isRegistered = (userId > 0);
```

This integrates seamlessly with existing user registration systems.

## Technical Details

### Thread Safety
- Uses `ConcurrentHashMap` for tracking unauthenticated users
- Thread-safe operations for concurrent access
- Timer runs in dedicated daemon thread

### Performance
- Efficient lookup using hash maps
- Periodic checks reduce CPU usage
- Only processes users who need enforcement

### Error Handling
- Graceful handling of database errors
- Logging of all enforcement actions
- Safe shutdown and cleanup

## Module Lifecycle

### Initialization
```java
NickServ nickServ = new NickServ(jserv, socketThread, pw, br);
```

### Registration
```java
moduleManager.registerModule(nickServ);
```

### Enabling
```java
moduleManager.enableModule("NickServ");
```

### Handshake (P10 Registration)
```java
nickServ.handshake(nick, servername, description, numeric, identd);
```

### Shutdown
```java
nickServ.shutdown();
// Cancels timer, clears tracking data
```

## Logging

NickServ logs important events:
- Module initialization and shutdown
- User connections with registered nicks
- Authentication events
- Enforcement actions (kills)
- Errors and warnings

Log level: INFO, WARNING, SEVERE

## Security Considerations

1. **Grace Period**: Allows legitimate users time to authenticate
2. **PROTECT Flag**: Users with PROTECT flag (`'p', 0x0080`) are completely exempt from nick protection
   - No warning notices sent
   - Not tracked in enforcement list
   - Can use any nickname without restrictions
   - Applies to both connection and nick changes
3. **Re-authentication Check**: Verifies user is still not authenticated before killing
4. **Registration Check**: Confirms nickname is still registered before enforcement
5. **Race Condition Handling**: Multiple checks prevent false positives
6. **Dummy Nick System**: Blocks nickname with placeholder user instead of network-wide ban
   - Only the nick is blocked, not the entire IP/host
   - Automatic release upon authentication
   - Users can connect with different nick and authenticate

## Integration with Other Modules

NickServ works alongside:
- **HostServ**: Virtual host management
- **SpamScan**: Spam detection and prevention
- Uses shared `SocketThread` for IRC communication
- Uses shared `Database` for user lookups

## Example Scenarios

### Scenario 1: Legitimate User
1. User "Alice" connects (registered nick)
2. NickServ sends warning: "You have 60 seconds to authenticate"
3. User authenticates with Q bot within 30 seconds
4. NickServ removes from enforcement list
5. User continues normally

### Scenario 2: Unauthorized User
1. User "Bob" connects with registered nick "Alice"
2. NickServ sends warning: "You have 60 seconds to authenticate"
3. User does not authenticate
4. After 60 seconds, NickServ sends KILL command
5. User is disconnected: "Nickname is registered and you failed to authenticate"

### Scenario 3: Nickname Change
1. User "Bob" connects (not registered)
2. User changes nick to "Alice" (registered)
3. NickServ detects change and sends warning
4. Grace period starts for "Alice"
5. User must authenticate or be killed

## Troubleshooting

### Users not being killed
- Check if module is enabled in `config-modules-extended.json`
- Verify grace period is correctly configured
- Check logs for database errors
- Ensure timer is running

### False positives
- Increase grace period in configuration
- Check database connectivity
- Verify authentication system is working

### Performance issues
- Increase check interval
- Optimize database queries
- Monitor log file for errors

## Future Enhancements

Possible improvements:
- Configurable warning messages
- Multiple warning notices before kill
- Temporary grace period extensions
- Integration with email notifications
- Web interface for monitoring

## License
Part of JServ - see main LICENSE file

## Author
Andreas Pschorn (WarPigs)

## Version
1.0 - Initial release
