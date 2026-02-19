# HelpServ Modul

## Überblick
HelpServ ist das integrierte Helpdesk-Modul von JServ, inspiriert von NewServ `helpmod2`.
Es kombiniert:
- hierarchische FAQ-/Hilfenavigation,
- durchsuchbare Begriffserklärungen (`TERM`),
- Support-Queue-Verwaltung,
- ticketbasierte Invite-Workflows,
- Moderations- und Channel-Werkzeuge,
- Staff-Statistiken und Laufzeitdiagnose.

HelpServ kann sowohl als reiner FAQ-Bot als auch als vollständiger Support-Workflow für Staff/Operatoren eingesetzt werden.

## Kernfunktionen

### 1) Mehrsprachiger Themenbaum
- Lädt alle `help_*.txt` aus dem konfigurierten Verzeichnis (`help_dir`, Standard `help`).
- Parst einrückungsbasierte Themen in einen hierarchischen Baum.
- Führt mehrere Sprach-/Themendateien in einer In-Memory-Struktur zusammen.
- Unterstützt zustandsbehaftete Navigation pro Benutzer (`1..N`, `0` = zurück).

### 2) Kommando-ACL und Benutzerlevel
- Jeder Befehl wird gegen einen internen Command-Katalog geprüft.
- Unbekannte Befehle liefern eine klare Meldung (`SHOWCOMMANDS`), außer bei unterdrückter Ausgabe per Account-Config.
- Befehlslevel:
  - `LAMER (0)`
  - `PEON (1)` (= USER)
  - `FRIEND (2)` (= HELPER)
  - `TRIAL (3)`
  - `STAFF (4)`
  - `OPER (5)`
  - `ADMIN (6)` (= DEV)

### 3) Live-Support-Workflow
- Queue pro Channel (`QUEUE`, `NEXT`, `DONE`, `ON/OFF`, `MAINTAIN`).
- Ticket-Lifecycle (`TICKET`, `RESOLVE`, `TICKETS`, `SHOWTICKET`, `TICKETMSG`).
- Invite-Flow mit optionaler Ticketpflicht (`INVITE`, Channel-Flag 19).

### 4) Channel-Moderationshilfen
- Channel-Modusaktionen (`OP`, `DEOP`, `VOICE`, `DEVOICE`, `KICK`, `OUT`).
- Bann- und Inhaltsmoderation (`BAN`, `CHANBAN`, `CENSOR`).
- Topic- und Welcome-Automatisierung (`TOPIC`, `WELCOME`).
- Channel-Richtlinien per Bitflag-Konfiguration (`CHANCONF`).

### 5) Persistenz und Reload zur Laufzeit
- Optionales Restore aus `helpmod.db` im `help_dir` (kein Legacy-Fallback-Pfad).
- Speichert/lädt Channels, Flags, Queue/Tickets, Terms, Account-Config/Level, Report-Routen und Statistik-Snapshots.
- `RELOAD` aktualisiert Daten zur Laufzeit ohne JServ-Neustart.
- Sicheres Fallback-Verhalten bei fehlenden Help-Dateien oder fehlender DB-Datei.

---

## Modul-Konfiguration

### Modul in `config-modules-extended.json` aktivieren
```json
{
  "name": "HelpServ",
  "enabled": true,
  "className": "net.midiandmore.jserv.HelpServ",
  "numericSuffix": "AAG",
  "configFile": "config-helpserv.json"
}
```

### `config-helpserv.json`
```json
[
  {"name":"nick","value":"HelpServ"},
  {"name":"servername","value":"services.example.com"},
  {"name":"description","value":"Help and Information Service"},
  {"name":"identd","value":"helpserv"},
  {"name":"help_dir","value":"help"}
]
```

### Konfigurationsschlüssel
- `nick` - Service-Nickname.
- `servername` - Service-Servername bei Registrierung.
- `description` - Service-Beschreibung (z. B. für WHOIS).
- `identd` - Ident für den Service-Handshake.
- `help_dir` - Verzeichnis mit `help_*.txt` und optional `helpmod.db`.

---

## Help-Inhaltsdateien (`help_*.txt`)

### Lade-Reihenfolge
HelpServ lädt Themen aus:
1. automatischer Suche in `help_dir` mit `help_*.txt`.

Sprachdateien werden priorisiert sortiert:
1. `help_en.txt`
2. `help_de.txt`
3. alle weiteren alphabetisch

### Encoding
- Bevorzugt: UTF-8.
- Falls keine gültige UTF-8-Datei: Fallback auf `windows-1252`.

### Formatregeln
- Themenüberschriften sind einrückungsbasiert.
- Zeilen mit Präfix `*` sind Inhaltszeilen des aktuellen Themas.
- Zeilen mit Präfix `$` werden ignoriert.
- Leere Zeilen werden ignoriert.

### Beispiel
```text
Deutsche Hilfe
 *Willkommen bei HelpServ.
 Online-Support
  *Wobei brauchst du Hilfe?
  AuthServ
   *Hilfe zu AuthServ-Account und Login.
```

### Benutzer-Navigation
- `HELP` startet am Root.
- `HELP <thema>` springt direkt zu einem Thema (Pfad/Fuzzy-Match).
- Nummernauswahl (`1..N`) öffnet Unterthemen.
- `0` geht eine Ebene zurück.

---

## Befehlsreferenz

### Öffentliche/Benutzer-Befehle
- `HELP [thema]` - Themenbaum oder spezifischen Knoten anzeigen.
- `TOPICS [thema]` / `MENU` - Unterthemen auflisten.
- `SEARCH <text>` / `FIND <text>` - Titel und Inhalte durchsuchen.
- `SHOWCOMMANDS [level]` - verfügbare Befehle je Level anzeigen.
- `COMMAND <name>` - Kurzbeschreibung eines Befehls.
- `VERSION` - Modulversion anzeigen.
- `INVITE [#channel]` - Einladung anfragen (mit Queue-/Ticket-Logik).
- `WHOAMI` - Account, Befehlslevel und Account-Config anzeigen.
- `?`, `?+`, `?-` - Kurzformen für Term-Lookup und optionale Queue-Aktion.

### Term/Glossar-Befehle
- `TERM [#channel] FIND <pattern>`
- `TERM [#channel] GET <term>`
- `TERM [#channel] LIST [pattern]`
- `TERM [#channel] LISTFULL`
- `TERM [#channel] ADD <term> <beschreibung>` (staff+)
- `TERM [#channel] DEL <term...>` (staff+)

### Queue-Befehle
- `QUEUE [#channel] [LIST|SUMMARY|STATUS]`
- `QUEUE [#channel] NEXT [n]`
- `QUEUE [#channel] DONE [nick..]`
- `QUEUE [#channel] ON|OFF`
- `QUEUE [#channel] MAINTAIN [on|off|n]`
- `QUEUE [#channel] RESET`
- Aliase: `NEXT`, `DONE`, `ENQUEUE`, `DEQUEUE`, `AUTOQUEUE`

### Ticket-Befehle
- `TICKET <nick> [#channel] [minutes]`
- `RESOLVE <nick|#account> [#channel]`
- `TICKETS [#channel]`
- `SHOWTICKET <nick|#account> [#channel]`
- `TICKETMSG [#channel] [text]`

### Account/Staff-Befehle
- `WHOIS <nick|#account>`
- `SEEN <nick|#account>`
- `LISTUSER [level] [pattern]`
- `ADDUSER <account|nick> [level]`
- `MODUSER <account|nick> [level]`
- `DELUSER <account>`
- `LEVEL <account> [USER|HELPER|TRIAL|STAFF|OPER|ADMIN|DEV]`
- Kurzbefehle: `PEON`, `FRIEND`, `TRIAL`, `STAFF`, `OPER`, `ADMIN`, `IMPROPER`

### Channel/Moderations-Befehle
- `ADDCHAN <#channel>`
- `DELCHAN <#channel> YesImSure`
- `CHANCONF [#channel] [±flagId ...]`
- `WELCOME [#channel] [text]`
- `TOPIC [#channel] [ADD <text>|DEL <n>|ERASE|SET <text>|REFRESH]`
- `OP|DEOP|VOICE|DEVOICE <#channel> [nick1..nick6]`
- `KICK <#channel> <nick..> [:reason]`
- `OUT <nick..> [:reason]`
- `MESSAGE <#channel> <text>`
- `CHECKCHANNEL <#channel> [summary]`
- `CHANNEL <#channel>`
- `EVERYONEOUT [#channel] [all|unauthed] [:reason]`
- `BAN`, `CHANBAN`, `CENSOR`, `DNMO`, `IDLEKICK`

### Laufzeit/Reporting/Statistik-Befehle
- `STATUS`
- `REPORT <#source> <#target|OFF>`
- `WRITEDB`
- `RELOAD`
- `STATS`, `WEEKSTATS`, `TOP10`, `RATING`, `TERMSTATS`, `CHANSTATS`, `ACTIVESTAFF`
- `STATSDUMP`, `STATSREPAIR`, `STATSRESET`

---

## Befehlslevel (Minimum)

- **Level 0**: `COMMAND`, `SHOWCOMMANDS`, `WHOAMI`
- **Level 1**: `HELP`, `TOPICS`, `SEARCH`, `?`, `VERSION`, `INVITE`
- **Level 3**: Queue-Steuerung/Kurzbefehle (`QUEUE`, `NEXT`, `DONE`, `ENQUEUE`, `DEQUEUE`, `AUTOQUEUE`), `KICK`, `MESSAGE`, `TICKET`, `ACCONF`, `RATING`, `VOICE`, `DEVOICE`, `CHANNEL`, `CHANBAN`
- **Level 4**: `TERM`, `WHOIS`, `SEEN`, `LISTUSER`, `WELCOME`, `OP`, `DEOP`, `OUT`, `BAN`, `CENSOR`, `CHECKCHANNEL`, `CHANSTATS`, `TOP10`, `TICKETS`, `SHOWTICKET`, `RESOLVE`, `TICKETMSG`, `CHANCONF`, `TOPIC`, `DNMO`, `EVERYONEOUT`, `PEON`, `FRIEND`, `IMPROPER`
- **Level 5**: `STATUS`, `RELOAD`, `WRITEDB`, `REPORT`, `LAMERCONTROL`, `IDLEKICK`, `TRIAL`, `STAFF`, `OPER`, `TERMSTATS`
- **Level 6**: `ADDUSER`, `MODUSER`, `ADDCHAN`, `DELCHAN`, `LEVEL`, `ADMIN`, `LCEDIT`, `WEEKSTATS`, `ACTIVESTAFF`, `STATSDUMP`, `STATSREPAIR`, `STATSRESET`

---

## `CHANCONF` Flags (0..21)

`CHANCONF` verwendet Bitflags mit numerischer ID:

0. Passive state  
1. Welcome message  
2. JoinFlood protection  
3. Queue  
4. Verbose queue (requires queue)  
5. Auto queue (requires queue)  
6. Channel status reporting  
7. Pattern censor  
8. Lamer control  
9. Idle user removal  
10. Keep channel moderated (`+m`)  
11. Keep channel invite only (`+i`)  
12. Handle channel topic  
13. Calculate statistic  
14. Remove joining trojans  
15. Channel commands  
16. Oper only channel  
17. Disallow bold/underline formatting  
18. Queue inactivity deactivation  
19. Require a ticket to join  
20. Send a message on ticket issue  
21. Excessive highlight prevention

### Beispiele
- Alle Flags anzeigen:
  - `CHANCONF #help`
- Queue + verbose Queue aktivieren:
  - `CHANCONF #help +3 +4`
- Ticketpflicht deaktivieren:
  - `CHANCONF #help -19`
- Einzelnes Flag abfragen:
  - `CHANCONF #help 10`

---

## `ACCONF` Flags (0..5)

Accountbezogene Verhaltensoptionen:

0. Send replies as private messages  
1. Send replies as notices  
2. Do not send verbose messages  
3. Auto-op on join  
4. Auto-voice on join  
5. Suppress unknown command error

### Beispiele
- Aktuelle Konfiguration anzeigen:
  - `ACCONF`
- PM-Antworten und Auto-Voice aktivieren:
  - `ACCONF +0 +4`
- Unterdrückung unbekannter Befehle deaktivieren:
  - `ACCONF -5`

---

## Typische Betriebsabläufe

### Benutzer-Self-Service
1. Benutzer sendet `/msg HelpServ HELP`.
2. Navigation über numerische Menüs.
3. `SEARCH` für Freitext-Suche.
4. Bei Bedarf `INVITE` für Live-Support.

### Staff-Queue-Betrieb
1. Queue im Support-Channel aktivieren (`QUEUE #help ON`).
2. Queue prüfen (`QUEUE #help SUMMARY`).
3. Nächsten Benutzer einladen (`NEXT #help` oder `QUEUE #help NEXT 1`).
4. Benutzer als erledigt markieren (`DONE #help <nick>`).
5. Optionales Auto-Queue-Ziel setzen (`QUEUE #help MAINTAIN 2`).

### Ticketpflichtiger Support-Channel
1. Staff erstellt Ticket (`TICKET <nick> #help 30`).
2. Benutzer fordert Invite an (`INVITE #help`).
3. HelpServ prüft Ticket und lädt ein.
4. Nach Bearbeitung Ticket schließen (`RESOLVE <nick> #help`).

---

## Persistenz (`helpmod.db`)

Wenn vorhanden in `help_dir`, stellt HelpServ wieder her:
- verwaltete Channels,
- Channel-Flags,
- Welcome-Texte,
- Queue-/Ticket-Status,
- Report-Routen,
- Term-Dictionaries (global/channel),
- Account-Level und Account-Config,
- Statistik-Snapshots.

Wenn die Datei fehlt, läuft HelpServ mit konfigurierten Defaults und In-Memory-Laufzeitdaten weiter.

---

## Laufzeit- & Sicherheits-Hinweise

- HelpServ ist in Burst-Channel-Registrierung integriert.
- Verwaltete Channels werden beim Start/Burst beigetreten und mit Zustand versorgt.
- Queue- und Modus-Maintenance kann Support-Policies automatisch durchsetzen.
- `RELOAD` aktualisiert Help-Dateien und `helpmod.db` ohne Neustart.
- Meldungen zu unbekannten Befehlen sind pro Account unterdrückbar (`ACCONF +5`).

---

## Troubleshooting

### "Keine Themen gefunden" / nur Fallback-Themen
- Prüfen, ob `help_dir` existiert.
- Prüfen, ob mindestens eine `help_*.txt` vorhanden ist.
- Dateikodierung prüfen (bevorzugt UTF-8).

### `INVITE` wegen Ticket abgelehnt
- `CHANCONF +19` prüfen.
- Ticket mit `SHOWTICKET` / `TICKETS` prüfen.

### Queue wirkt inaktiv
- Queue-Status mit `QUEUE #channel SUMMARY` prüfen.
- Sicherstellen, dass Queue aktiv ist (`ON`) und passende Flags gesetzt sind.

### Channel-Commands werden nicht ausgelöst
- Sicherstellen, dass der Channel als managed geführt wird.
- Sicherstellen, dass `CHANCONF +15` aktiv ist (Channel commands).

---

## Relevante Dateien
- `JServ/src/main/java/net/midiandmore/jserv/HelpServ.java`
- `config-helpserv.json`
- `help/help_en.txt`
- `help/help_de.txt`
- `help/helpmod.db` (optional, Persistenz/Laufzeitdaten)
