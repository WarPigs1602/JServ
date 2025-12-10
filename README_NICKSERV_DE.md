# NickServ Modul

## Übersicht
NickServ ist ein Service-Modul für JServ, das registrierte Nicknames vor unbefugter Nutzung schützt. Es trennt automatisch Benutzer, die registrierte Nicknames ohne Authentifizierung verwenden, nach einer konfigurierbaren Schonfrist.

## Funktionen
- **Nickname-Schutz**: Erkennt automatisch, wenn sich Benutzer mit registrierten Nicknamen verbinden
- **Gnadenfrist**: Gibt Benutzern Zeit zur Authentifizierung, bevor Maßnahmen ergriffen werden (konfigurierbar, Standard 60 Sekunden)
- **Automatische Durchsetzung**: Trennt nicht authentifizierte Benutzer nach Ablauf der Gnadenfrist
- **PROTECT-Flag**: Benutzer mit PROTECT-Flag können jeden Nick ohne Durchsetzung verwenden
- **Oper/Service-Ausnahme**: IRC-Operatoren und Services sind automatisch von der Durchsetzung ausgenommen
- **Burst-Erkennung**: Behandelt Benutzer während des initialen Server-Bursts korrekt
- **Account-Abgleich**: Überprüft, ob authentifizierte Benutzer mit dem registrierten Nickname-Besitzer übereinstimmen
- **Dummy-Nick-System**: Erstellt Platzhalter-Nicks, um Nicknamen zu blockieren, anstatt netzwerkweite Bans zu verwenden
- **Automatische Freigabe**: Dummy-Nicks werden automatisch freigegeben, wenn sich der legitime Benutzer authentifiziert
- **Nickname-Änderungs-Überwachung**: Verfolgt Nickname-Änderungen und erzwingt Schutz auf registrierte Nicks
- **P10-Protokoll**: Vollständige Kompatibilität mit dem P10-IRC-Protokoll
- **Echtzeit-Überwachung**: Hintergrund-Timer prüft Durchsetzung alle 10 Sekunden
- **Benutzerbefehle**: Stellt Informationsbefehle bereit (INFO, HELP, SHOWCOMMANDS, VERSION)
- **Datenbankintegration**: Nahtlose Integration mit PostgreSQL-Datenbank
- **Ordnungsgemäßes Herunterfahren**: Sendet korrekten QUIT-Befehl beim Herunterfahren

## Konfiguration

### Modul-Konfiguration (`config-modules-extended.json`)

NickServ muss in der Modul-Konfiguration aktiviert sein:
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

### NickServ-Konfiguration (`config-nickserv.json`)
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

**Parameter:**
- `nick`: Der Nickname für den NickServ-Bot (Standard: "NickServ")
- `servername`: Der Servername für NickServ (Standard: "nickserv.example.net")
- `description`: Service-Beschreibung, die in WHOIS angezeigt wird
- `identd`: Der Ident-String für den Service
- `grace_period`: Zeit in Sekunden vor dem Killen nicht-authentifizierter Benutzer (Standard: 60)
- `check_interval`: Wie oft auf Durchsetzung geprüft wird in Millisekunden (Standard: 10000)

## P10 Protokoll-Implementierung

### Numerik-Zuweisung
NickServ verwendet das Numerik-Suffix **AAD** (z.B. wenn Server-Numerik "AB" ist, wird NickServ "ABAAD" sein).

### Verarbeitete Protokoll-Befehle

### Behandelte Protokollbefehle

#### Benutzerverbindung (N-Befehl)
```
:AB N TestNick 2 1234567890 ident host.example.com +ir TestUser:1234567890 IP ABCDE :Real Name
```
Wenn sich ein Benutzer mit einem registrierten Nickname verbindet, führt NickServ folgendes aus:
1. Prüft, ob Burst-Modus aktiv ist (ignoriert während Burst)
2. Prüft, ob Benutzer ein Oper oder Service ist (nimmt aus, wenn wahr)
3. Prüft, ob Benutzer PROTECT-Flag hat (nimmt aus, wenn wahr)
4. Prüft, ob authentifizierter Account mit registriertem Nickname-Besitzer übereinstimmt (nimmt aus, wenn übereinstimmend)
5. Falls keines der obigen zutrifft, fügt zur Durchsetzungsliste hinzu und sendet Warnung
6. Startet Gnadenfrist-Timer

#### Account-Authentifizierung (AC-Befehl)
```
:ABCDE AC TestUser
```
Wenn sich ein Benutzer authentifiziert, führt NickServ folgendes aus:
1. Entfernt ihn aus der Durchsetzungsliste
2. Gibt Dummy-Nick frei, wenn der authentifizierte Account übereinstimmt
3. Keine weiteren Maßnahmen erforderlich

#### Nickname-Änderung (N-Befehl - Kurzform)
```
:ABCDE N NewNick
```
Überwacht Nick-Änderungen und:
1. Prüft, ob neuer Nick registriert ist
2. Wendet dieselbe Schutzlogik wie bei Verbindung an
3. Entfernt aus Durchsetzungsliste, wenn von geschütztem Nick weggewechselt wird

#### Quit/Kill (Q/D-Befehle)
```
:ABCDE Q :Quit-Nachricht
```
Entfernt Benutzer automatisch aus der Durchsetzungsverfolgung.

#### Kill-Befehl (D-Befehl - gesendet von NickServ)
```
:ABAAD D ABCDE 1234567890 :Nickname ist registriert und Sie haben sich nicht innerhalb von 60 Sekunden authentifiziert
```
NickServ sendet dies, um nicht authentifizierte Benutzer nach Ablauf der Gnadenfrist zu trennen.

#### Account-Authentifizierung (AC-Befehl)
```
:ABCDE AC TestUser
```
Wenn sich ein Benutzer authentifiziert, entfernt NickServ ihn aus der Durchsetzungsliste.

#### Nickname-Änderung (N-Befehl - Kurzform)
```
:ABCDE N NewNick
```
Überwacht Nickname-Änderungen und wendet Schutz erneut an, wenn zu einem registrierten Nick gewechselt wird.

#### Kill-Befehl (D-Befehl)
```
:ABAAD D ABCDE 1234567890 :Nickname ist registriert und Sie haben sich nicht innerhalb von 60 Sekunden authentifiziert
```
NickServ sendet dies, um nicht-authentifizierte Benutzer zu trennen.

## Benutzer-Befehle

Benutzer können private Nachrichten an NickServ senden:
```
/msg NickServ <befehl>
```

### Verfügbare Befehle

#### HELP
Zeigt Hilfeinformationen über NickServ.
```
/msg NickServ HELP
```

#### INFO <nickname>
Zeigt umfassende Informationen über einen registrierten Nickname an.
```
/msg NickServ INFO TestNick
```
Zeigt:
- Angefragter Nickname
- Account-Besitzer (Hauptaccount-Name)
- Nickname-Typ (Hauptaccount oder reservierter Nickname)
- Registrierungsstatus
- Formatiertes Registrierungsdatum und Zeitstempel
- Aktueller Authentifizierungsstatus
- Vollständige Liste aller reservierten Nicknames für diesen Account

**Beispiel-Ausgabe:**
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

Der INFO-Befehl funktioniert sowohl für Hauptaccounts als auch für reservierte Nicknames - er zeigt immer die vollständigen Informationen für den gesamten Account an.

#### SHOWCOMMANDS
Listet alle verfügbaren Befehle auf.
```
/msg NickServ SHOWCOMMANDS
```

#### VERSION
Zeigt NickServ-Versionsinformationen.
```
/msg NickServ VERSION
```

## Funktionsweise

### 1. Benutzerverbindungs-Ablauf
1. Benutzer verbindet sich mit IRC mit Nickname "JohnDoe"
2. NickServ prüft, ob Burst-Modus aktiv ist (überspringt Durchsetzung, wenn wahr)
3. Prüft, ob "JohnDoe" in der Datenbank registriert ist
4. Prüft, ob Benutzer Oper, Service oder PROTECT-Flag hat (überspringt, wenn wahr)
5. Prüft, ob Benutzer authentifiziert ist und Account übereinstimmt (überspringt, wenn übereinstimmend)
6. Falls registriert und nicht ausgenommen:
   - Benutzer wird zur Durchsetzungsliste mit Zeitstempel hinzugefügt
   - NickServ sendet Warnhinweise
   - Gnadenfrist-Countdown beginnt

### 2. Durchsetzungs-Ablauf
1. Hintergrund-Timer läuft alle 10 Sekunden (konfigurierbar)
2. Überspringt Durchsetzung, wenn Burst-Modus aktiv ist
3. Für jeden nicht authentifizierten Benutzer:
   - Berechnet verstrichene Zeit seit Verbindung
   - Falls verstrichene Zeit >= Gnadenfrist (60 Sekunden):
     - Überprüft, ob Benutzer noch online ist
     - Überprüft, ob Benutzer noch nicht authentifiziert ist
     - Überprüft, ob Nickname noch registriert ist
     - Überprüft Account-Nichtübereinstimmung (falls authentifiziert)
     - Sendet KILL-Befehl, um Benutzer zu trennen
     - Entfernt aus Durchsetzungsliste
     - Protokolliert Durchsetzungsaktion

### 3. Authentifizierungs-Ablauf
1. Benutzer authentifiziert sich mit Q-Bot oder anderem Auth-Service
2. Auth-Service sendet AC (Account)-Befehl
3. NickServ empfängt AC-Befehl und prüft:
   - Ob Benutzer in Durchsetzungsliste ist
   - Ob authentifizierter Account mit registriertem Nickname-Besitzer übereinstimmt
4. Falls Account übereinstimmt:
   - Benutzer wird aus Durchsetzungsliste entfernt
   - Dummy-Nick für diesen Account wird freigegeben
   - Benutzer kann Nick weiter verwenden
5. Falls Account nicht übereinstimmt:
   - Benutzer bleibt in Durchsetzungsliste
   - Wird getrennt, wenn Gnadenfrist abläuft

### 4. Nickname-Änderungs-Ablauf
1. Benutzer wechselt von "JohnDoe" zu "JaneDoe"
2. Entfernt alten Nick aus Durchsetzungsliste
3. Falls "JaneDoe" registriert ist:
   - Wendet dieselbe Schutzlogik wie bei Verbindung an
   - Prüft Ausnahmen (Oper, Service, PROTECT-Flag)
   - Prüft Account-Übereinstimmung
   - Fügt zur Durchsetzungsliste hinzu, wenn nicht ausgenommen
4. Falls von registriertem Nick weggewechselt wird:
   - Entfernt einfach aus Durchsetzungsliste

### 5. Burst-Modus-Behandlung
1. Während Server-Burst (EB nicht empfangen):
   - Alle Durchsetzung ist deaktiviert
   - Benutzer werden nicht zur Durchsetzungsliste hinzugefügt
   - Keine Warnungen werden gesendet
2. Nach Burst-Ende (EA empfangen):
   - Normale Durchsetzung wird fortgesetzt
   - Bestehende verbundene Benutzer werden geprüft
   - Timer beginnt mit Durchsetzungsprüfungen

## Datenbank-Integration

NickServ verwendet die bestehende JServ-Datenbank, um zu prüfen, ob Nicknames registriert sind. Es fragt die `chanserv.users`-Tabelle ab:

```java
int userId = jserv.getDb().getIndex(nickname);
boolean isRegistered = (userId > 0);
```

Dies integriert sich nahtlos mit bestehenden Benutzer-Registrierungssystemen.

## Technische Details

### Thread-Sicherheit
- Verwendet `ConcurrentHashMap` zur Verfolgung nicht-authentifizierter Benutzer
- Thread-sichere Operationen für gleichzeitigen Zugriff
- Timer läuft in dediziertem Daemon-Thread

### Leistung
- Effiziente Suche mit Hash-Maps
- Periodische Prüfungen reduzieren CPU-Nutzung
- Verarbeitet nur Benutzer, die Durchsetzung benötigen

### Fehlerbehandlung
- Elegante Behandlung von Datenbankfehlern
- Protokollierung aller Durchsetzungsaktionen
- Sicheres Herunterfahren und Aufräumen

## Modul-Lebenszyklus

### Initialisierung
```java
NickServ nickServ = new NickServ(jserv, socketThread, pw, br);
```

### Registrierung
```java
moduleManager.registerModule(nickServ);
```

### Aktivierung
```java
moduleManager.enableModule("NickServ");
```

### Handshake (P10-Registrierung)
```java
nickServ.handshake(nick, servername, description, numeric, identd);
```

### Herunterfahren
```java
nickServ.shutdown();
// Bricht Timer ab, löscht Tracking-Daten
```

## Protokollierung

NickServ protokolliert wichtige Ereignisse:
- Modul-Initialisierung und Herunterfahren
- Benutzer-Verbindungen mit registrierten Nicks
- Authentifizierungsereignisse
- Durchsetzungsaktionen (Kills)
- Fehler und Warnungen

Log-Level: INFO, WARNING, SEVERE

## Sicherheitsüberlegungen

1. **Schonfrist**: Ermöglicht legitimen Benutzern Zeit zur Authentifizierung
2. **PROTECT Flag**: Benutzer mit gesetztem PROTECT Flag (`'p', 0x0080`) sind komplett von Nick-Schutz ausgenommen
   - Keine Warnmeldungen
   - Kein Tracking in der Enforcement-Liste
   - Können jeden Nick verwenden ohne Einschränkungen
   - Gilt bei Verbindung und Nick-Änderung
3. **Erneute Authentifizierungsprüfung**: Überprüft, ob Benutzer noch nicht authentifiziert ist, bevor gekillt wird
4. **Registrierungsprüfung**: Bestätigt, dass Nickname noch registriert ist vor Durchsetzung
5. **Race-Condition-Behandlung**: Mehrfache Prüfungen verhindern falsch-positive Ergebnisse
6. **Dummy-Nick System**: Blockiert Nickname durch Platzhalter-User statt Netzwerk-Ban
   - Nur der Nick ist blockiert, nicht die ganze IP/Host
   - Automatische Freigabe bei Authentifizierung
   - User können sich mit anderem Nick verbinden und authentifizieren

## Integration mit anderen Modulen

NickServ arbeitet zusammen mit:
- **HostServ**: Virtual-Host-Verwaltung
- **SpamScan**: Spam-Erkennung und -Prävention
- Verwendet gemeinsamen `SocketThread` für IRC-Kommunikation
- Verwendet gemeinsame `Database` für Benutzer-Lookups

## Beispiel-Szenarien

### Szenario 1: Legitimer Benutzer
1. Benutzer "Alice" verbindet sich (registrierter Nick)
2. NickServ sendet Warnung: "Sie haben 60 Sekunden zur Authentifizierung"
3. Benutzer authentifiziert sich bei Q-Bot innerhalb von 30 Sekunden
4. NickServ entfernt aus Durchsetzungsliste
5. Benutzer fährt normal fort

### Szenario 2: Unbefugter Benutzer
1. Benutzer "Bob" verbindet sich mit registriertem Nick "Alice"
2. NickServ sendet Warnung: "Sie haben 60 Sekunden zur Authentifizierung"
3. Benutzer authentifiziert sich nicht
4. Nach 60 Sekunden sendet NickServ KILL-Befehl
5. Benutzer wird getrennt: "Nickname ist registriert und Sie haben sich nicht authentifiziert"

### Szenario 3: Nickname-Änderung
1. Benutzer "Bob" verbindet sich (nicht registriert)
2. Benutzer wechselt Nick zu "Alice" (registriert)
3. NickServ erkennt Änderung und sendet Warnung
4. Schonfrist startet für "Alice"
5. Benutzer muss sich authentifizieren oder wird gekillt

## Fehlerbehebung

### NickServ funktioniert nicht

- Prüfen, ob Modul in `config-modules-extended.json` aktiviert ist
- PostgreSQL-Datenbankverbindung überprüfen

### Falsch-positive Ergebnisse
- Schonfrist in Konfiguration erhöhen
- Datenbank-Konnektivität prüfen
- Authentifizierungssystem funktioniert überprüfen

### Leistungsprobleme
- Check-Intervall erhöhen
- Datenbankabfragen optimieren
- Log-Datei auf Fehler überwachen

## Zukünftige Verbesserungen

Mögliche Verbesserungen:
- Konfigurierbare Warnmeldungen
- Mehrfache Warnungen vor Kill
- Temporäre Schonfrist-Verlängerungen
- Integration mit E-Mail-Benachrichtigungen
- Web-Interface zur Überwachung

## Lizenz
Teil von JServ - siehe Haupt-LICENSE-Datei

## Autor
Andreas Pschorn (WarPigs)

## Version
1.0 - Erste Veröffentlichung
