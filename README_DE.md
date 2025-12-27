# JServ

**JServ** ist ein robuster Java-basierter Dienst, der drei essenzielle IRC-Module—**SpamScan**, **HostServ**, **SaslServ** und **NickServ**—in einem einzigen, effizienten Paket integriert. JServ wurde speziell für die Verwendung mit `midircd` und `snircd` entwickelt und optimiert Spam-Erkennung, versteckte Host-Verwaltung und Nickname-Schutz für IRC-Netzwerke.

## Hauptfunktionen

### Modulare Architektur
- **Plugin-System:** SpamScan, HostServ und NickServ sind als unabhängige Module implementiert
- **Dynamische Modulverwaltung:** Module können über die Konfiguration aktiviert/deaktiviert werden, ohne Code-Änderungen
- **Erweiterte Modul-Konfiguration:** Umfassende JSON-basierte Modul-Definition mit Klassenname, Numeric-Suffix und Config-Datei-Zuordnung
- **Automatisches Modul-Laden:** Module werden automatisch basierend auf der Konfiguration instanziiert und registriert
- **Modul-Lebenszyklus:** Jedes Modul hat seinen eigenen Initialisierungs-, Handshake- und Shutdown-Prozess
- **Erweiterbares Design:** Neue Module können einfach durch Implementierung des Module-Interface und Hinzufügen eines Konfigurationseintrags hinzugefügt werden
- **Zentralisierte Verwaltung:** ModuleManager verwaltet alle Modul-Registrierungen, Aktivierung/Deaktivierung und Nachrichten-Routing

### SpamScan-Modul
- **Automatische Spam-Erkennung:** Scannt aktiv IRC-Nachrichten auf Spam unter Verwendung einer anpassbaren Badword-Liste
- **Knocker-Bot-Erkennung:** Fortgeschrittene Mustererkennung zur Identifizierung und automatischen Blockierung von Knocker-Spambots beim Verbinden
- **Homoglyph-Erkennung:** Identifiziert und blockiert Nachrichten mit Homoglyphen, die für Spam verwendet werden
- **Flood-Schutz:** Überwacht Nachrichtenhäufigkeit und ergreift automatisch Maßnahmen gegen floodende Benutzer
- **Wiederholungserkennung:** Verfolgt und bestraft Benutzer, die wiederholt identische Nachrichten senden
- **Zeitbasierter Schutz:** Erweiterte Spam-Prüfungen für Benutzer, die kürzlich Kanälen beigetreten sind (konfigurierbares Zeitfenster, Standard 5 Minuten)
- **Duale Erkennungsmodi:** Normale und Lax Spam-Erkennungsmodi mit separaten Schwellenwerten
- **Benutzerklassifizierung:** Unterscheidet zwischen neuen und etablierten Benutzern mit unterschiedlichen Schwellenwerten
- **Konfigurierbare Schwellenwerte:** Separate Wiederholungs- und Flood-Schwellenwerte für neue vs. etablierte Benutzer in beiden Erkennungsmodi
- **Ähnlichkeitserkennung:** Erweiterte Nachrichtenähnlichkeitserkennung mit konfigurierbarem Ähnlichkeitsschwellenwert (Standard 0.8)
- **Kanalübergreifende Spam-Erkennung:** Erkennt Spam-Muster über mehrere Kanäle mit dediziertem Zeitfenster und Ähnlichkeitsschwellenwert
- **Verdächtige Ident-Erkennung:** Erkennt und blockiert automatisch Benutzer mit verdächtigen Idents (root, admin, etc.)
- **Verdächtige TLD-Erkennung:** Überwacht und markiert Nachrichten mit verdächtigen Top-Level-Domains (tk, ml, ga, etc.)
- **Score-basiertes System:** Dynamisches Spam-Scoring mit konfigurierbarer Verfallsrate und Intervall zur Rehabilitation guten Verhaltens
- **Extremer Spam-Schwellenwert:** Konfigurierbarer Schwellenwert für sofortige Maßnahmen bei extremem Spam-Verhalten
- **G-Line-Unterstützung:** Automatische netzwerkweite Bans (G-Lines) nach wiederholten Verstößen
  - Konfigurierbarer Kill-Schwellenwert vor G-Line-Aktivierung (Standard: 3 Kills)
  - Einstellbare G-Line-Dauer (Standard: 24 Stunden)
  - Benutzerdefinierter G-Line-Grund mit Unterstützung für Verstoß-URL
- **Badword-Verwaltung:** Ermöglicht IRC-Operatoren (Opers), Badwords über benutzerfreundliche Befehle hinzuzufügen, aufzulisten oder zu entfernen
- **Kanal-Verwaltung:** Opers können Kanäle zur SpamScan-Überwachung hinzufügen/entfernen
- **Operator-Befehle:** Umfassende Hilfe-, Konfigurations- und Verwaltungsbefehle inklusive GLINESTATS
- **JSON-Konfigurationsspeicher:** Spam-Erkennungsregeln und Badword-Listen werden in einfach editierbaren JSON-Dateien gespeichert
- **Selbstheilende Konfigurationen:** Fehlende Konfigurationsdateien werden automatisch generiert und gepflegt
- **Kill-Tracking:** Führt Statistiken über getötete Benutzer für G-Line-Durchsetzung
- **Laufzeit-Steuerung:** Kann über die Konfiguration dynamisch aktiviert/deaktiviert werden
- **Sauberer Logout:** Sendet ordnungsgemäßen QUIT-Befehl beim Herunterfahren

### HostServ-Modul
- **Verwaltung versteckter Hosts:** Ermöglicht authentifizierten Benutzern das Setzen und Verwalten ihrer versteckten Hosts für Privatsphäre.
- **Automatische VHost-Anwendung:** Wendet automatisch virtuelle Hosts bei Benutzerauthentifizierung an (AC-Befehl).
- **Automatischer VHost beim Verbinden:** Setzt virtuelle Hosts für Benutzer, die sich mit registrierten Accounts verbinden.
- **Wöchentliche Host-Änderung:** Benutzer können ihren versteckten Host einmal alle sieben Tage ändern.
- **Operator-Privilegien:** Opers können versteckte Hosts für jeden Benutzer manuell setzen oder aktualisieren.
- **Sichere Speicherung:** Host-Informationen werden sicher über PostgreSQL gespeichert und verwaltet.
- **Laufzeit-Steuerung:** Kann über die Konfiguration dynamisch aktiviert/deaktiviert werden
- **Sauberer Logout:** Sendet ordnungsgemäßen QUIT-Befehl beim Herunterfahren

### NickServ-Modul
- **Nickname-Schutz:** Schützt registrierte Nicknames vor unbefugter Nutzung.
- **Authentifizierungs-Durchsetzung:** Benutzer müssen sich innerhalb von 60 Sekunden authentifizieren oder werden getrennt.
- **Account-Abgleich:** Überprüft, dass authentifizierte Benutzer mit dem registrierten Nickname-Besitzer übereinstimmen.
- **Gnadenfrist:** Konfigurierbare Warnzeit vor automatischer Trennung.
- **PROTECT Flag:** Benutzer mit PROTECT Flag können jeglichen Nickname-Schutz umgehen.
- **Oper/Service-Ausnahme:** IRC-Operatoren und Services sind automatisch vom Schutz ausgenommen.
- **Nick-Wechsel-Erkennung:** Überwacht und setzt Schutz durch, wenn Benutzer zu registrierten Nicknames wechseln.
- **Dummy-Nick System:** Erstellt Platzhalter-User zum Blockieren von Nicknames statt netzwerkweiter Bans.
- **Datenbank-Integration:** Prüft registrierte Nicknames gegen die PostgreSQL-Datenbank.
- **Benutzer-Benachrichtigungen:** Sendet Warnungen und Erfolgsmeldungen zum Authentifizierungsstatus.
- **INFO-Befehl:** Umfassende Nickname-Informationen inklusive aller reservierten Nicks und formatierter Daten.
- **Laufzeit-Steuerung:** Kann über die Konfiguration dynamisch aktiviert/deaktiviert werden
- **Sauberer Logout:** Sendet ordnungsgemäßen QUIT-Befehl beim Herunterfahren

### SaslServ-Modul
- **SASL-Authentifizierung:** Server-zu-Server SASL-Validierungsprotokoll für JIRCd
- **PLAIN-Mechanismus-Unterstützung:** Implementiert SASL PLAIN Authentifizierungsmechanismus
- **Datenbank-Integration:** Authentifiziert Benutzer gegen PostgreSQL-Datenbank
- **Relay-Modus-Unterstützung:** Optionale Weiterleitungsauthentifizierung an externe Kontrolldienste (z.B. mIAuthd)
- **Account-Token-Generierung:** Generiert erweiterte Account-Token (username:timestamp:id) für JIRCd
- **Konfigurierbare Account-Parameter:** Anpassbarer Account-Name und ID in der N-Line-Registrierung
- **Timeout-Behandlung:** Konfigurierbarer Relay-Timeout mit automatischer Fehlerbehandlung
- **Remote-Authentifizierung:** MD5-basierte Digest-Authentifizierung für Relay-Modus
- **Config-Fallback:** Optionaler Rückfall auf config-basierte Authentifizierung bei Datenbankausfall
- **Laufzeit-Steuerung:** Kann über die Konfiguration dynamisch aktiviert/deaktiviert werden
- **Sauberer Logout:** Sendet ordnungsgemäßen QUIT-Befehl beim Herunterfahren

### Kanal- und Benutzerverwaltung
- **Kanalmodi und Berechtigungen:** Verwalten Sie Kanalmodi, Benutzerrollen (op, voice) und Moderationsstatus.
- **Benutzerverfolgung:** Verfolgen Sie Benutzer, ihren Flood/Repeat-Status, Kanäle, Registrierung, Operator-Status und Service-Flags.
- **Bidirektionale Benutzer-Kanal-Verfolgung:** Benutzer verfolgen ihre Kanäle und Kanäle verfolgen ihre Benutzer für zuverlässige Spam-Erkennung.
- **Join-Zeitstempel-Verfolgung:** Zeichnet exakte Beitrittszeiten für zeitbasierten Spam-Schutz auf.
- **Account-Historie:** Protokollieren Sie Änderungen an Benutzerkonten, einschließlich Passwort- und E-Mail-Updates.
- **Kanalstatistiken:** Verfolgen Sie Kanalerstellung, letzte Aktivität, Stats-Resets, Ban-Dauer, Gründer und Benutzer-Join-Statistiken.
- **Korrekte Benutzer-Hinzufügung:** Benutzer und Kanäle werden bei CONNECT, JOIN und BURST-Befehlen korrekt synchronisiert.

### Datenbankintegration
- **PostgreSQL-Backend:** Alle persistenten Daten (Host-Infos, Kanal-/Benutzerdetails, Konfigurationen) werden über PostgreSQL für Zuverlässigkeit und Sicherheit verwaltet.
- **Transaktionsunterstützung:** Robuste Transaktionsverarbeitung (begin, commit) für Datenintegrität.
- **Schema-Verwaltung:** Automatische Erstellung und Verwaltung von Datenbankschema und Tabellen.

### Konfiguration und Erweiterbarkeit
- **Konfigurierbar via JSON:** Sowohl der Kerndienst als auch die Spam-/Badword-Verwaltung werden über JSON-Dateien konfiguriert, um eine einfache Anpassung zu ermöglichen.
- **Selbstheilende Einrichtung:** Automatische Generierung fehlender Konfigurationen und Datenbanktabellen beim Start.
- **Daemon-Unterstützung:** Ausführung als Hintergrund-Daemon mit automatischer PID-Dateiverwaltung und Log-Rotation.
- **Sauberes Herunterfahren:** Saubere Shutdown-Behandlung mit ordnungsgemäßer Ressourcen-Bereinigung.
- **Modulares Design:** Unabhängige Module können über `config-modules-extended.json` aktiviert/deaktiviert werden
- **Hot-Konfiguration:** Modul-Zustände können ohne Neukompilierung der Anwendung geändert werden

## Kompatibilität

- **IRC-Daemons:** Erfordert `midircd` und `snircd`
- **Java-Laufzeitumgebung:** JRE 17 oder neuer
- **Datenbank:** PostgreSQL

## Installation

### 1. Aus Quellcode erstellen
- Empfohlen: Verwenden Sie eine IDE mit Maven-Unterstützung für nahtlose Kompilierung.
- Repository klonen und mit Maven erstellen:
  ```sh
  git clone https://github.com/WarPigs1602/JServ.git
  cd JServ
  mvn package
  ```

### 2. Vorkompiliertes Release verwenden
- Laden Sie das neueste Release herunter: [JServ.zip](https://github.com/WarPigs1602/JServ/releases/download/JServ/JServ.zip)
- Stellen Sie sicher, dass Sie JRE 17+ und PostgreSQL installiert haben.

## Verwendung

### JServ ausführen

JServ kann in zwei Modi gestartet werden:

**Vordergrund-Modus (Standard):**
```sh
java -jar JServ.jar
```
Drücken Sie `CTRL+C`, um die Anwendung sauber zu beenden.

**Daemon-Modus (Hintergrundprozess):**
```sh
java -jar JServ.jar --daemon
```

Im Daemon-Modus:
- Der Prozess läuft abgetrennt im Hintergrund
- Die Prozess-ID (PID) wird in `jserv.pid` gespeichert
- Ausgaben werden in `jserv.log`, `jserv.out` und `jserv.err` protokolliert
- Daemon beenden: `kill $(cat jserv.pid)`

**Kommandozeilenoptionen:**
- `-d, --daemon` - Im Daemon-Modus ausführen (abgetrennter Hintergrundprozess)
- `-h, --help` - Hilfenachricht anzeigen und beenden

### Sauberes Herunterfahren

JServ implementiert eine saubere Shutdown-Behandlung:
- Reagiert auf `CTRL+C` (SIGINT) und `SIGTERM`-Signale
- Sendet QUIT-Befehle für alle Service-Bots (SpamScan, HostServ, NickServ)
- Sendet SQUIT-Befehl für ordnungsgemäße Trennung vom IRC-Server
- Fährt alle aktivierten Module sauber über den ModuleManager herunter
- Wartet auf Übertragung der QUIT/SQUIT-Befehle (insgesamt 1,5 Sekunden)
- Schließt Datenbankverbindungen sauber
- Stoppt alle Threads ordnungsgemäß
- Leert alle Log-Puffer
- Vollständige saubere Trennung vom IRC-Netzwerk vor Beendigung

## Modul-Konfiguration

Module werden in `config-modules-extended.json` konfiguriert:

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

**Konfigurations-Parameter:**
- `name`: Anzeigename des Moduls
- `enabled`: Auf `false` setzen, um das Modul zu deaktivieren
- `className`: Vollqualifizierter Klassenname für automatische Instanziierung
- `numericSuffix`: P10-Protokoll Numeric-Suffix (z.B. AAC, AAB, AAD)
- `configFile`: Modulspezifischer Konfigurationsdateiname

Änderungen erfordern einen Neustart.

### Verfügbare Module

- **SpamScan** (`spamscan`): Automatische Spam-Erkennung und Badword-Filterung
  - Duale Erkennungsmodi (Normal/Lax) mit konfigurierbaren Schwellenwerten
  - Erweiterte Ähnlichkeitserkennung und kanalübergreifendes Spam-Tracking
  - Verdächtige Ident- und TLD-Erkennung
  - Score-basiertes System mit Verfall zur Verhaltensrehabilitation
  - Automatische G-Line-Durchsetzung nach wiederholten Verstößen
  - Umfassende Operator-Befehle und Statistiken
- **HostServ** (`hostserv`): Verwaltung versteckter Hosts für authentifizierte Benutzer
- **NickServ** (`nickserv`): Nickname-Schutz und Authentifizierungs-Durchsetzung
- **SaslServ** (`saslserv`): SASL-Authentifizierungsprüfung (Mechanismus `PLAIN`)
  - Implementiert Server-zu-Server SASL-Protokoll für JIRCd
  - Unterstützt Datenbank- und config-basierte Authentifizierung
  - Optionaler Relay-Modus für externe Authentifizierungsdienste
  - Konfigurierbare Account-Parameter über `config-saslserv.json`

### Neue Module hinzufügen

Um ein neues Modul hinzuzufügen:

1. Implementieren Sie das `Module`-Interface
2. Implementieren Sie die erforderlichen Methoden: `initialize()`, `handshake()`, `parseLine()`, `shutdown()`, etc.
3. Fügen Sie einen Konfigurationseintrag in `config-modules-extended.json` hinzu:
   ```json
   {
     "name": "MyModule",
     "enabled": true,
     "className": "net.midiandmore.jserv.MyModule",
     "numericSuffix": "AAE",
     "configFile": "config-mymodule.json"
   }
   ```
4. Erstellen Sie bei Bedarf eine modulspezifische Konfigurationsdatei
5. Starten Sie JServ neu - das Modul wird automatisch geladen und registriert

Beispiel für eine Modul-Implementierung:

```java
public class MyModule implements Module {
    private boolean enabled = false;
    
    @Override
    public void initialize(JServ jserv, SocketThread st, PrintWriter pw, BufferedReader br) {
        // Modul initialisieren
    }
    
    @Override
    public void enable() {
        this.enabled = true;
    }
    
    @Override
    public void parseLine(String text) {
        if (!enabled) return;
        // IRC-Protokoll-Zeile verarbeiten
    }
    
    @Override
    public String getNumericSuffix() {
        return "AAE"; // Muss mit Config übereinstimmen
    }
    
    // Weitere erforderliche Methoden implementieren...
}
```

Der ModuleManager instanziiert Ihr Modul automatisch mittels Reflection basierend auf dem `className`-Feld.

## Operator- und Benutzerbefehle

### NickServ Benutzerbefehle
- **INFO <nickname>** - Zeigt umfassende Informationen über einen registrierten Nickname, einschließlich Account-Details, Registrierungsdatum und alle reservierten Nicknames
- **HELP** - Zeigt Hilfeinformationen
- **SHOWCOMMANDS** - Listet alle verfügbaren Befehle auf
- **VERSION** - Zeigt Versionsinformationen

Für detaillierte Informationen zu NickServ-Befehlen und Features siehe [README_NICKSERV_DE.md](README_NICKSERV_DE.md).

### SpamScan & HostServ Befehle
Weitere Informationen finden Sie im integrierten Hilfesystem oder im Projekt-Wiki für eine vollständige Liste der Operator- und Benutzerbefehle für SpamScan und HostServ.

## Wichtige Hinweise

- Stellen Sie sicher, dass PostgreSQL läuft und für JServ erreichbar ist, bevor Sie den Dienst starten.
- Konfigurationsdateien werden automatisch generiert, falls sie fehlen, aber eine manuelle Überprüfung wird zur Anpassung empfohlen.
- Module müssen in `config-modules-extended.json` aktiviert sein, um aktiv zu sein.
- Jedes Modul hat seine eigene Konfigurationsdatei (z.B. `config-spamscan.json`, `config-hostserv.json`).
- Modul-Numeric-Suffixe (AAC, AAB, AAD) müssen eindeutig sein und P10-Protokoll-Standards folgen.
- Das Deaktivieren eines Moduls verhindert die Verarbeitung von IRC-Nachrichten und reduziert den Ressourcenverbrauch.
- Alle Services melden sich ordnungsgemäß mit QUIT-Befehlen ab beim Herunterfahren.
- SpamScan benötigt korrekte Kanal- und Benutzerverfolgung für ordnungsgemäße Funktion.
- Im Daemon-Modus werden alle Logs in Dateien geschrieben; verwenden Sie `tail -f jserv.log` zum Überwachen.

## Konfigurationsdateien

- `config-jserv.json` - Hauptkonfiguration (Server-Verbindung, Numeric, etc.)
- `config-modules-extended.json` - Erweiterte Modul-Konfiguration mit Klassennamen und Numeric-Suffixen
- `config-spamscan.json` - SpamScan-Modul-Konfiguration
- `config-hostserv.json` - HostServ-Modul-Konfiguration
- `config-nickserv.json` - NickServ-Modul-Konfiguration
- `config-saslserv.json` - SaslServ-Modul-Konfiguration (Account-Name, ID, Relay-Einstellungen)
- `badwords-spamscan.json` - Badword-Liste für SpamScan

## Logdateien

- `jserv.log` - Hauptprotokoll mit allen Aktivitäten
- `jserv.out` - Standardausgabe (nur im Daemon-Modus)
- `jserv.err` - Fehlerausgabe (nur im Daemon-Modus)
- `jserv.pid` - Prozess-ID des laufenden Daemons

## Lizenz

MIT License © 2024-2025 Andreas Pschorn
