# Password Migration - Quick Guide

## Overview

JServ now automatically migrates plaintext passwords to SHA-256 hashed passwords and deletes the plaintext versions.

## Automatic Migration

### 1. On System Start
- All plaintext passwords are automatically hashed and removed
- Check logs: `INFO: Migrated X passwords to secure format and deleted plaintexts`

### 2. On User Login
- Users logging in with old passwords are automatically migrated
- Check logs: `INFO: Auto-migrated password for user: USERNAME and deleted plaintext password`

### 3. Manual Migration (OperServ)

```
/msg OperServ MIGRATE
```

**Requirements:**
- Must be authenticated
- Must have operator privileges (+o in IRC)
- Must have OPER/HELPER/STAFF/DEV flags in database

**Output:**
```
-OperServ- Starting password migration...
-OperServ- Password migration completed successfully.
-OperServ- All plaintext passwords have been hashed with SHA-256 and removed from cleartext.
```

### 4. Cleanup Plaintext Passwords

```
/msg OperServ CLEANPASS
```

**Warning:** Only use after successful migration! This deletes all remaining plaintext passwords where a hash already exists.

**Output:**
```
-OperServ- Cleanup completed: X plaintext passwords deleted.
```

## Hash Algorithm

- **Algorithm:** SHA-256
- **Salt:** User creation timestamp (`created` field)
- **Format:** 64-character hex string

## Database Schema

| Column | Type | Description |
|--------|------|-------------|
| `password` | VARCHAR(11) | Legacy plaintext password (set to NULL after migration) |
| `pwd` | VARCHAR(64) | SHA-256 hash of password |
| `created` | BIGINT | Unix timestamp (used as salt) |

## Recommended Workflow

1. **System Start**: Automatic migration runs
2. **User Logins**: Remaining users are migrated on login
3. **Manual Migration** (optional): `/msg OperServ MIGRATE`
4. **Verification**: Check server logs
5. **Cleanup** (optional): `/msg OperServ CLEANPASS`

## New OperServ Commands

| Command | Description |
|---------|-------------|
| `MIGRATE` | Migrate all plaintext passwords to SHA-256 hashes |
| `CLEANPASS` | Delete all remaining plaintext passwords |
| `HELP MIGRATE` | Show help for MIGRATE command |
| `HELP CLEANPASS` | Show help for CLEANPASS command |

## Security Features

✅ **Automatic Migration**: Passwords are automatically migrated on start and login  
✅ **Immediate Deletion**: Plaintext passwords are deleted immediately after migration  
✅ **No Data Loss**: Users can still login with their old password - it's just stored securely  
✅ **Comprehensive Logging**: All migrations are logged  
✅ **Unique Salt**: Each password uses the user's creation timestamp as salt  
✅ **Strong Algorithm**: SHA-256 is used for hashing  

## Technical Details

### API Methods (Database.java)

```java
// Migrate all plaintext passwords to hashed format
public void migratePasswordsToSecure()

// Delete all plaintext passwords (where hash exists)
public int deleteAllPlaintextPasswords()

// Authenticate user (auto-migrates on success)
public boolean authenticateUser(String username, String password)
```

## Compatibility

- ✅ **Backward Compatible**: Old users can still login - passwords are migrated automatically
- ✅ **Forward Compatible**: New users get hashed passwords from the start
- ✅ **No Database Changes Required**: Uses existing columns

## Troubleshooting

### Migration fails

**Check:**
1. Database connection
2. Server logs for detailed error messages
3. Ensure `pwd` column exists in `chanserv.users` table
4. Retry with `/msg OperServ MIGRATE`

### CLEANPASS deletes 0 passwords

**Reason:**
- All plaintext passwords already deleted, or
- Users without hashes still have plaintext passwords

**Solution:**
1. Run `/msg OperServ MIGRATE` first
2. Then retry `/msg OperServ CLEANPASS`

---

For detailed documentation, see [PASSWORD_MIGRATION_DE.md](PASSWORD_MIGRATION_DE.md)
