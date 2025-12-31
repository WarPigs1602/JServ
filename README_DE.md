# JServ

**JServ** ist ein robuster Java-basierter Dienst, der essenzielle IRC-Module—**SpamScan**, **HostServ**, **NickServ**, **SaslServ**, **ChanServ**, **AuthServ** und **OperServ**—in einem einzigen, effizienten Paket integriert. JServ wurde speziell für die Verwendung mit `midircd` und `snircd` entwickelt und optimiert Spam-Erkennung, versteckte Host-Verwaltung, Nickname-Schutz, Authentifizierung, Channel-Verwaltung und Operator-Services für IRC-Netzwerke.

## Hauptfunktionen

### Modulare Architektur
- **Plugin-System:** SpamScan, HostServ, NickServ, SaslServ, ChanServ, AuthServ und OperServ sind als unabhängige Module implementiert
- **Dynamische Modulverwaltung:** Module können über die Konfiguration aktiviert/deaktiviert werden, ohne Code-Änderungen
- **Erweiterte Modul-Konfiguration:** Umfassende JSON-basierte Modul-Definition mit Klassenname, Numeric-Suffix und Config-Datei-Zuordnung
- **Automatisches Modul-Laden:** Module werden automatisch basierend auf der Konfiguration instanziiert und registriert
- **Modul-Lebenszyklus:** Jedes Modul hat seinen eigenen Initialisierungs-, Handshake- und Shutdown-Prozess
- **Erweiterbares Design:** Neue Module können einfach durch Implementierung des Module-Interface und Hinzufügen eines Konfigurationseintrags hinzugefügt werden
- **Zentralisierte Verwaltung:** ModuleManager verwaltet alle Modul-Registrierungen, Aktivierung/Deaktivierung und Nachrichten-Routing

### SpamScan-Modul

**Zweck:** Bietet automatisierte Spam-Erkennung und -Prävention für IRC-Channels. SpamScan überwacht aktiv Channel-Nachrichten, Benutzerverhalten und Verbindungsmuster, um Spam, Flood-Angriffe und bösartige Bots in Echtzeit zu identifizieren und zu blockieren.

**Hauptfunktionen:**
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

**Zweck:** Verwaltet virtuelle Hosts (vhosts) für authentifizierte Benutzer und bietet Privatsphäre- und Anpassungsoptionen. HostServ ermöglicht es Benutzern, ihre echten IP-Adressen hinter benutzerdefinierten Hostnamen zu verstecken, was die Sicherheit erhöht und eine personalisierte Netzwerkpräsenz ermöglicht.

**Hauptfunktionen:**
- **Verwaltung versteckter Hosts:** Ermöglicht authentifizierten Benutzern das Setzen und Verwalten ihrer versteckten Hosts für Privatsphäre.
- **Automatische VHost-Anwendung:** Wendet automatisch virtuelle Hosts bei Benutzerauthentifizierung an (AC-Befehl).
- **Automatischer VHost beim Verbinden:** Setzt virtuelle Hosts für Benutzer, die sich mit registrierten Accounts verbinden.
- **Wöchentliche Host-Änderung:** Benutzer können ihren versteckten Host einmal alle sieben Tage ändern.
- **Operator-Privilegien:** Opers können versteckte Hosts für jeden Benutzer manuell setzen oder aktualisieren.
- **Sichere Speicherung:** Host-Informationen werden sicher über PostgreSQL gespeichert und verwaltet.
- **Laufzeit-Steuerung:** Kann über die Konfiguration dynamisch aktiviert/deaktiviert werden
- **Sauberer Logout:** Sendet ordnungsgemäßen QUIT-Befehl beim Herunterfahren

### NickServ-Modul

**Zweck:** Schützt registrierte Nicknames vor unbefugter Nutzung durch automatisierte Durchsetzung. NickServ stellt sicher, dass nur die rechtmäßigen Besitzer ihre registrierten Nicknames verwenden können, indem eine Authentifizierung innerhalb einer Gnadenfrist erforderlich ist.

**Hauptfunktionen:**
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

**Zweck:** Implementiert das Server-zu-Server SASL-Authentifizierungsprotokoll für sichere Pre-Connection-Authentifizierung. SaslServ validiert Benutzeranmeldeinformationen, bevor sie vollständig mit dem Netzwerk verbunden sind, und bietet erhöhte Sicherheit und nahtlose Authentifizierung für IRC-Clients.

**Hauptfunktionen:**
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

### ChanServ-Modul

**Zweck:** Verwaltet Channel-Registrierung, Besitz und Zugriffskontrolle. ChanServ ermöglicht es Benutzern, Channels zu registrieren, persistente Channel-Einstellungen zu pflegen und den Benutzerzugriff über ein umfassendes Berechtigungssystem zu steuern.

**Hauptfunktionen:**
- **Channel-Registrierung:** Registrierung von Channels mit Founder-Privilegien und persistentem Besitz
- **Zugriffsverwaltung:** Steuerung des Benutzerzugriffs mit OP-, VOICE- und BAN-Flags
- **Automatische Modus-Anwendung:** Automatisches Anwenden von Channel-Modi basierend auf Zugriffslevel beim Betreten
- **Channel-Einstellungen:** Konfiguration von AUTOOP, PROTECT, AUTOLIMIT und anderen Channel-Verhaltensweisen
- **Topic-Verwaltung:** Setzen, Sperren und Verwalten von Channel-Topics mit Schutz
- **Ban-Listen-Verwaltung:** Pflege und Durchsetzung von Channel-Ban-Listen
- **Benutzer-Befehle:** REGISTER, ADDUSER, DELUSER, MODUSER, LISTUSERS, SET, INFO, UNREGISTER
- **Operator-Befehle:** CHANLIST für netzwerkweite Channel-Übersicht
- **Laufzeit-Steuerung:** Kann über die Konfiguration dynamisch aktiviert/deaktiviert werden
- **Sauberer Logout:** Sendet ordnungsgemäßen QUIT-Befehl beim Herunterfahren

### AuthServ-Modul

**Zweck:** Bietet zentralisierte Benutzerkontenverwaltung und Authentifizierungsdienste. AuthServ übernimmt die Benutzerregistrierung, Passwortverwaltung und Authentifizierung und dient als Grundlage für andere Services wie ChanServ und HostServ.

**Hauptfunktionen:**
- **Account-Registrierung:** Registrierung neuer Accounts mit E-Mail-Verifizierung und automatisch generierten Passwörtern
- **Sichere Authentifizierung:** IDENTIFY-Befehl für Account-Login mit Datenbankvalidierung
- **Passwortverwaltung:** Passwörter ändern (PASSWD), neue Passwörter anfordern (REQUESTPASSWORD) und ausstehende Änderungen abbrechen (RESETPASSWORD)
- **E-Mail-Benachrichtigungen:** Automatisches E-Mail-System für Registrierung, Passwortänderungen und Wiederherstellung
- **Benutzer-Flags-System:** Umfassendes Berechtigungssystem (+n, +i, +c, +h, +q, +o, +a, +d, +p, +T Flags)
- **Account-Informationen:** Anzeige von Account-Details, Erstellungsdatum und letztem Login (INFO, STATUS Befehle)
- **Account-Löschung:** Sichere Account-Löschung mit Passwortbestätigung
- **Eine Registrierung pro Sitzung:** Verhindert Missbrauch durch Begrenzung der Registrierungen pro Verbindung
- **Integration:** Wendet automatisch Channel-Modi über ChanServ nach Authentifizierung an
- **Laufzeit-Steuerung:** Kann über die Konfiguration dynamisch aktiviert/deaktiviert werden
- **Sauberer Logout:** Sendet ordnungsgemäßen QUIT-Befehl beim Herunterfahren

### OperServ-Modul

**Zweck:** Bietet Netzwerkoperator-Tools für Netzwerkverwaltung, Überwachung und Sicherheitsdurchsetzung. OperServ bietet Befehle für G-Line-Verwaltung, Trust-Kontrolle und Netzwerkstatistiken, die nur für IRC-Operatoren zugänglich sind.

**Hauptfunktionen:**
- **G-Line-Verwaltung:** Hinzufügen, Entfernen, Auflisten und Synchronisieren netzwerkweiter Bans (GLINE, UNGLINE, GLINELIST)
- **Trust-System:** Verwaltung von Verbindungs-Trust-Regeln für IP-basierte Zugriffskontrolle (TRUSTADD, TRUSTDEL, TRUSTGET, TRUSTSET, TRUSTLIST)
- **Netzwerkstatistiken:** Anzeige von Netzwerkstatistiken, aktiven G-Lines und Trust-Regeln (STATS)
- **Channel-Verwaltung:** Auflistung aller Channels mit Benutzerzahlen (CHANLIST)
- **Benutzerverwaltung:** KILL-Befehl zum Trennen von missbräuchlichen Benutzern
- **Raw-Befehle:** Senden von Raw-IRC-Protokollbefehlen für erweiterte Operationen (RAW)
- **Automatische Bereinigung:** Timer-basierte Bereinigung abgelaufener G-Lines
- **Netzwerk-Synchronisierung:** Synchronisierung von G-Lines mit dem Netzwerk beim Start
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
- **ChanServ** (`chanserv`): Channel-Registrierungs- und Verwaltungs-Service
  - Channel-Registrierung mit Founder-Privilegien
  - Benutzerzugriffsverwaltung (OP, VOICE, BAN)
  - Automatisches Setzen von Modi basierend auf Benutzer-Flags
  - Channel-Einstellungen (AUTOOP, PROTECT, AUTOLIMIT)
  - Topic-Schutz und -Verwaltung
  - Ban-Listen-Verwaltung
  - Channel-Informationen und Benutzerlisten-Befehle
- **AuthServ** (`authserv`): Account-Verwaltungs- und Authentifizierungs-Service
  - Benutzer-Account-Registrierung mit E-Mail-Verifizierung
  - Sichere Passwortverwaltung (Ändern, Zurücksetzen, Wiederherstellung)
  - Account-Identifizierung und Authentifizierung
  - Benutzer-Flags und Berechtigungsverwaltung
  - Account-Löschung und Informationsanzeige
  - E-Mail-Benachrichtigungssystem für Passwortänderungen
  - Integration mit ChanServ für automatische Channel-Modus-Anwendung
- **OperServ** (`operserv`): Operator-Service für Netzwerkverwaltung
  - Netzwerkstatistiken und Überwachung
  - G-Line-Verwaltung (Hinzufügen, Entfernen, Auflisten, Synchronisieren)
  - Trust-Verwaltung für Verbindungskontrolle (TRUSTADD, TRUSTDEL, TRUSTLIST)
  - Channel- und Benutzerauflistung
  - Raw-Befehle senden für erweiterte Operationen
  - Kill-Befehl für Benutzerverwaltung
  - Automatische G-Line-Bereinigung und Synchronisierung

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

### Kern-Konfiguration
- `config.json` - Hauptkonfiguration von JServ (Server-Verbindung, Numeric, Datenbank-Einstellungen, SMTP)
- `config-modules.json` - Basis-Modul-Konfiguration (veraltet, verwenden Sie die erweiterte Version)
- `config-modules-extended.json` - Erweiterte Modul-Konfiguration mit Klassennamen, Numeric-Suffixen und Config-Datei-Zuordnungen

### Modul-Konfiguration
- `config-spamscan.json` - SpamScan-Modul-Einstellungen (Nick, Servername, Beschreibung, Identd, Erkennungsschwellenwerte)
- `config-hostserv.json` - HostServ-Modul-Einstellungen (Nick, Servername, Beschreibung, Identd)
- `config-nickserv.json` - NickServ-Modul-Einstellungen (Nick, Servername, Beschreibung, Identd, Gnadenfrist)
- `config-saslserv.json` - SaslServ-Modul-Einstellungen (Account-Name, ID, Relay-Einstellungen, Timeout)
- `config-chanserv.json` - ChanServ-Modul-Einstellungen (Nick, Servername, Beschreibung, Identd)
- `config-authserv.json` - AuthServ-Modul-Einstellungen (Nick, Servername, Beschreibung, Identd)
- `config-operserv.json` - OperServ-Modul-Einstellungen (Nick, Servername, Beschreibung, Identd)
- `config-trustcheck.json` - TrustCheck Allow-Regeln (Legacy-Fallback, falls Datenbank keine Trust-Regeln enthält)

### Datendateien
- `badwords-spamscan.json` - Badword-Liste für SpamScan Spam-Erkennung
- `email-templates.json` - E-Mail-Vorlagen für AuthServ-Benachrichtigungen (Registrierung, Passwortänderungen)

## TrustCheck (TC/TR)

JServ kann als **TrustCheck-Server** für JIRCd's TC/TR-Protokoll laufen.

- TrustCheck wird über `trustserver` in `config.json` aktiviert.
- JServ wertet TC-Anfragen nur aus, wenn `servername` exakt `trustserver` entspricht.

In `config.json` ergänzen:

```json
{"name":"trustserver","value":"trust.example.net"},
{"name":"trustcheck_timeout_ms","value":"2000"}
```

Die Allow-Regeln werden primär aus der Datenbanktabelle `operserv.trusts` ausgewertet (verwaltbar per OperServ-Kommandos). Basierend auf der Auswertung antwortet JServ:
- `TR ... OK` - Verbindung erlaubt (Regel gefunden)
- `TR ... IGNORE` - Keine Regel gefunden, aber nicht explizit abgelehnt (Standardverhalten)
- `TR ... FAIL` - Verbindung explizit abgelehnt (fail-closed Modus, falls konfiguriert)

OperServ-Kommandos:

- `TRUSTADD <maske> [maxconn] [ident]`
- `TRUSTDEL <maske>`
- `TRUSTGET <maske>`
- `TRUSTSET <maske> [maxconn] [ident|noident]`
- `TRUSTLIST [limit]`

Masken unterstützen `*` und `?` und sind typischerweise `ident@ip` (Beispiel: `*admin*@192.0.2.*`).
`maxconn` ist die maximale gleichzeitige Verbindungsanzahl für diese IP (0 = unbegrenzt). Mit `ident` wird ein nicht-leeres Ident erzwungen.

Wenn die Datenbank **keine** Trust-Regeln enthält, fällt JServ aus Kompatibilitätsgründen auf `config-trustcheck.json` zurück.

Beispiel für `config-trustcheck.json`:

```json
[
  {"name":"rule1","value":"trustedident@203.0.113.*"},
  {"name":"rule2","value":"vpn*@2001:db8:*"}
]
```

## Logdateien

- `jserv.log` - Hauptprotokoll mit allen Aktivitäten
- `jserv.out` - Standardausgabe (nur im Daemon-Modus)
- `jserv.err` - Fehlerausgabe (nur im Daemon-Modus)
- `jserv.pid` - Prozess-ID des laufenden Daemons

## Lizenz

MIT License © 2024-2025 Andreas Pschorn
