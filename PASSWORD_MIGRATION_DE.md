# Automatische Passwortmigration - Dokumentation

## Übersicht

Das JServ-System unterstützt nun die **automatische Migration von Klartextpasswörtern zu gehashten Passwörtern** mit SHA-256. Diese Funktion erhöht die Sicherheit erheblich, indem Passwörter nicht mehr im Klartext in der Datenbank gespeichert werden.

## Funktionsweise

### 1. Automatische Migration beim Start

Bei jedem Start des JServ-Systems wird automatisch `migratePasswordsToSecure()` aufgerufen. Diese Methode:
- Sucht alle Benutzer mit Klartextpasswörtern (`password IS NOT NULL`) ohne gehashte Passwörter (`pwd IS NULL`)
- Hasht die Klartextpasswörter mit SHA-256 unter Verwendung des `created`-Timestamps als Salt
- Speichert das gehashte Passwort in der Spalte `pwd`
- **Löscht das Klartextpasswort durch Setzen von `password = NULL`**
- Protokolliert die Anzahl der migrierten Passwörter im Server-Log

### 2. Automatische Migration beim Login

Wenn sich ein Benutzer mit einem alten Klartextpasswort anmeldet:
- Das System erkennt, dass noch ein Klartextpasswort existiert
- Authentifiziert den Benutzer mit dem Klartextpasswort
- Erstellt automatisch einen SHA-256-Hash des Passworts
- Speichert den Hash in der `pwd`-Spalte
- **Löscht das Klartextpasswort automatisch**
- Protokolliert die Auto-Migration im Server-Log

### 3. Manuelle Migration über OperServ

Administratoren können die Migration manuell auslösen:

```
/msg OperServ MIGRATE
```

**Voraussetzungen:**
- Benutzer muss als Operator eingeloggt sein (`+o` im IRC)
- Benutzer muss authentifiziert sein
- Benutzer muss Operator-Flags in der Datenbank haben (OPER, HELPER, STAFF oder DEV)

**Was passiert:**
- Alle verbleibenden Klartextpasswörter werden zu SHA-256-Hashes migriert
- Klartextpasswörter werden automatisch gelöscht
- Erfolgs- oder Fehlermeldungen werden an den Administrator gesendet
- Detaillierte Informationen werden im Server-Log protokolliert

### 4. Bereinigung verbleibender Klartextpasswörter

Nach erfolgreicher Migration können Administratoren alle verbleibenden Klartextpasswörter löschen:

```
/msg OperServ CLEANPASS
```

**Voraussetzungen:**
- Benutzer muss als Operator eingeloggt sein (`+o` im IRC)
- Benutzer muss authentifiziert sein
- Benutzer muss Operator-Flags in der Datenbank haben (OPER, HELPER, STAFF oder DEV)

**WARNUNG:** Dieser Befehl löscht nur Klartextpasswörter von Benutzern, die bereits ein gehashtes Passwort haben. Benutzer ohne gehashtes Passwort werden nicht berührt.

**Was passiert:**
- Löscht alle `password`-Einträge, bei denen `pwd` bereits gesetzt ist
- Gibt die Anzahl der gelöschten Klartextpasswörter zurück
- Protokolliert die Aktion im Server-Log

## Hash-Algorithmus

**Algorithmus:** SHA-256  
**Salt:** Unix-Timestamp der Benutzererstellung (`created`-Feld)  
**Format:** Hex-String (64 Zeichen)

**Beispiel:**
```java
String saltedPassword = password + created;
byte[] hash = MessageDigest.getInstance("SHA-256").digest(saltedPassword.getBytes());
String hexString = convertToHex(hash); // 64 Zeichen
```

## Datenbank-Schema

### Relevante Spalten in `chanserv.users`

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| `password` | VARCHAR(11) | **Legacy-Spalte** für Klartextpasswörter (wird auf NULL gesetzt nach Migration) |
| `pwd` | VARCHAR(64) | SHA-256-Hash des Passworts (als Hex-String) |
| `new_pwd` | VARCHAR(64) | Temporärer Hash für Passwort-Reset (wird aktiviert beim ersten Login) |
| `reset_token` | VARCHAR(32) | Token für Passwort-Reset-Links |
| `generated_pwd` | VARCHAR(20) | Temporäres Klartext-Passwort für Reset (nur bis zur Aktivierung) |
| `created` | BIGINT | Unix-Timestamp der Erstellung (wird als Salt verwendet) |

## OperServ-Befehle

### MIGRATE

**Syntax:**
```
/msg OperServ MIGRATE
```

**Beschreibung:**
Migriert alle verbleibenden Klartextpasswörter zu SHA-256-Hashes.

**Ausgabe:**
```
-OperServ- Starting password migration...
-OperServ- Password migration completed successfully.
-OperServ- All plaintext passwords have been hashed with SHA-256 and removed from cleartext.
-OperServ- Check server logs for details.
```

**Server-Log:**
```
INFO: OperServ MIGRATE command executed by AdminUser - starting password migration
INFO: Migrated 15 passwords to secure format and deleted plaintexts
INFO: OperServ MIGRATE command completed successfully by AdminUser
```

### CLEANPASS

**Syntax:**
```
/msg OperServ CLEANPASS
```

**Beschreibung:**
Löscht alle verbleibenden Klartextpasswörter aus der Datenbank (nur für Benutzer mit vorhandenem Hash).

**Ausgabe:**
```
-OperServ- WARNING: This will permanently delete all plaintext passwords!
-OperServ- Ensure all passwords are migrated first (use MIGRATE command).
-OperServ- Starting cleanup...
-OperServ- Cleanup completed: 0 plaintext passwords deleted.
-OperServ- Check server logs for details.
```

**Server-Log:**
```
WARNING: OperServ CLEANPASS command executed by AdminUser - deleting plaintext passwords
INFO: No plaintext passwords found - database is clean
INFO: OperServ CLEANPASS command completed by AdminUser: 0 passwords deleted
```

### HELP MIGRATE

**Syntax:**
```
/msg OperServ HELP MIGRATE
```

**Ausgabe:**
```
-OperServ- MIGRATE - migrate all plaintext passwords to hashed format (SHA-256).
-OperServ- This command automatically hashes plaintext passwords and removes the cleartext versions.
```

### HELP CLEANPASS

**Syntax:**
```
/msg OperServ HELP CLEANPASS
```

**Ausgabe:**
```
-OperServ- CLEANPASS - delete all remaining plaintext passwords from database.
-OperServ- WARNING: Only use this after ensuring all passwords are migrated to hashed format!
```

## Empfohlener Migrations-Workflow

### Schritt 1: System-Start
Das System führt automatisch die erste Migration durch. Überprüfen Sie die Logs:
```
INFO: Migrated X passwords to secure format and deleted plaintexts
```

### Schritt 2: Benutzer-Logins
Benutzer, die sich anmelden, werden automatisch migriert. Überprüfen Sie die Logs:
```
INFO: Auto-migrated password for user: USERNAME and deleted plaintext password
```

### Schritt 3: Manuelle Migration (optional)
Führen Sie als Administrator aus:
```
/msg OperServ MIGRATE
```

### Schritt 4: Überprüfung
Prüfen Sie die Logs auf erfolgreiche Migration aller Benutzer.

### Schritt 5: Bereinigung (optional)
Nach erfolgreicher Migration aller Passwörter:
```
/msg OperServ CLEANPASS
```

Dies ist ein zusätzlicher Sicherheitsschritt, um sicherzustellen, dass keine Klartextpasswörter mehr in der Datenbank verbleiben.

## Sicherheitshinweise

1. **Automatische Migration:** Das System migriert Passwörter automatisch beim Start und bei jedem Login. Dies minimiert das Sicherheitsrisiko.

2. **Sofortige Löschung:** Klartextpasswörter werden sofort nach der Migration gelöscht (auf NULL gesetzt).

3. **Kein Datenverlust:** Benutzer können sich weiterhin mit ihrem alten Passwort anmelden - es wird nur sicherer gespeichert.

4. **Logging:** Alle Migrationen werden protokolliert, sodass Administratoren den Fortschritt überwachen können.

5. **Salt:** Jedes Passwort verwendet einen eindeutigen Salt (den `created`-Timestamp), was Rainbow-Table-Angriffe verhindert.

6. **SHA-256:** Ein bewährter und sicherer Hash-Algorithmus wird verwendet.

## Kompatibilität

- **Rückwärtskompatibel:** Alte Benutzer können sich weiterhin anmelden - ihre Passwörter werden automatisch migriert
- **Vorwärtskompatibel:** Neue Benutzer erhalten von Anfang an gehashte Passwörter
- **Keine Datenbankänderungen erforderlich:** Die bestehenden Spalten werden verwendet

## Fehlerbehandlung

### Problem: Migration schlägt fehl

**Symptom:**
```
-OperServ- Password migration failed: [Fehlermeldung]
```

**Lösung:**
1. Überprüfen Sie die Datenbankverbindung
2. Prüfen Sie die Server-Logs für detaillierte Fehlermeldungen
3. Stellen Sie sicher, dass die Spalte `pwd` in der `chanserv.users`-Tabelle existiert
4. Versuchen Sie es erneut mit `/msg OperServ MIGRATE`

### Problem: CLEANPASS löscht keine Passwörter

**Symptom:**
```
-OperServ- Cleanup completed: 0 plaintext passwords deleted.
```

**Ursache:**
- Alle Klartextpasswörter wurden bereits gelöscht, oder
- Benutzer ohne gehashte Passwörter haben noch Klartextpasswörter

**Lösung:**
1. Führen Sie zuerst `/msg OperServ MIGRATE` aus
2. Dann versuchen Sie `/msg OperServ CLEANPASS` erneut

## Technische Details

### API-Methoden in Database.java

#### migratePasswordsToSecure()
```java
public void migratePasswordsToSecure()
```
- **Sichtbarkeit:** public (kann von außen aufgerufen werden)
- **Rückgabe:** void
- **Funktion:** Migriert alle Klartextpasswörter zu SHA-256-Hashes und löscht Klartextpasswörter

#### deleteAllPlaintextPasswords()
```java
public int deleteAllPlaintextPasswords()
```
- **Sichtbarkeit:** public
- **Rückgabe:** Anzahl der gelöschten Klartextpasswörter (int)
- **Funktion:** Löscht alle Klartextpasswörter, bei denen ein Hash bereits existiert

#### hashPassword(String password, long created)
```java
private String hashPassword(String password, long created)
```
- **Sichtbarkeit:** private
- **Parameter:** 
  - `password`: Klartext-Passwort
  - `created`: Unix-Timestamp als Salt
- **Rückgabe:** SHA-256-Hash als Hex-String (64 Zeichen)

#### authenticateUser(String username, String password)
```java
public boolean authenticateUser(String username, String password)
```
- **Funktion:** Authentifiziert einen Benutzer und migriert automatisch Klartextpasswörter

## Änderungshistorie

- **2025-12-30:** Initiale Implementierung der automatischen Passwortmigration
  - Automatische Migration beim Start
  - Automatische Migration beim Login
  - Manuelle Migration über OperServ MIGRATE
  - Bereinigung über OperServ CLEANPASS
  - Klartextpasswörter werden automatisch nach Migration gelöscht

## Unterstützung

Bei Fragen oder Problemen:
1. Überprüfen Sie die Server-Logs
2. Konsultieren Sie diese Dokumentation
3. Kontaktieren Sie den System-Administrator

---

**Hinweis:** Diese Funktionen sind standardmäßig aktiviert und erfordern keine Konfiguration. Die Migration erfolgt automatisch beim Neustart des Systems oder beim Login der Benutzer.
