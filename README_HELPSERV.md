# HelpServ Module

## Overview
HelpServ is JServ's integrated helpdesk-style module inspired by NewServ `helpmod2`.
It combines:
- hierarchical FAQ/help navigation,
- searchable glossary/term responses,
- support queue handling,
- ticket-based invite workflows,
- moderation and channel tooling,
- staff statistics and runtime diagnostics.

HelpServ can be used as a simple FAQ bot for users or as a full support workflow engine for staff and operators.

## Core Capabilities

### 1) Multi-language topic tree
- Loads all `help_*.txt` files from the configured help directory (`help_dir`, default `help`).
- Parses indentation-based topics into a hierarchical tree.
- Merges multiple language/topic files into one in-memory tree.
- Supports stateful menu navigation per user (`1..N`, `0` for previous menu).

### 2) Command ACL and user levels
- Every command is validated against an internal command catalog.
- Unknown commands return a helpful message (`SHOWCOMMANDS`) unless suppressed by account config.
- Command levels:
  - `LAMER (0)`
  - `PEON (1)` (= USER)
  - `FRIEND (2)` (= HELPER)
  - `TRIAL (3)`
  - `STAFF (4)`
  - `OPER (5)`
  - `ADMIN (6)` (= DEV)

### 3) Live support workflow
- Queue per channel (`QUEUE`, `NEXT`, `DONE`, `ON/OFF`, `MAINTAIN`).
- Ticket lifecycle (`TICKET`, `RESOLVE`, `TICKETS`, `SHOWTICKET`, `TICKETMSG`).
- Invite flow that can enforce valid tickets (`INVITE`, channel flag 19).

### 4) Channel moderation helpers
- Channel mode actions (`OP`, `DEOP`, `VOICE`, `DEVOICE`, `KICK`, `OUT`).
- Ban and content moderation (`BAN`, `CHANBAN`, `CENSOR`).
- Topic and welcome automation (`TOPIC`, `WELCOME`).
- Channel policy and behavior bitflags (`CHANCONF`).

### 5) Runtime persistence and reload
- Optional state restore from `helpmod.db` in `help_dir` (no legacy fallback path).
- Tracks channels, flags, queue/tickets, terms, account config/levels, report routes, and stats snapshots.
- Runtime reload without restart via `RELOAD`.
- Safe fallback behavior if help files or DB are missing.

---

## Module Configuration

### Enable module in `config-modules-extended.json`
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

### Configuration keys
- `nick` - service nickname.
- `servername` - service servername in registration.
- `description` - service description shown in WHOIS-like contexts.
- `identd` - ident used for service introduction.
- `help_dir` - directory containing `help_*.txt` and optionally `helpmod.db`.

---

## Help Content Files (`help_*.txt`)

### Discovery order
HelpServ resolves help content from:
1. auto-discovery in `help_dir` using `help_*.txt`.

Language files are sorted with preference:
1. `help_en.txt`
2. `help_de.txt`
3. all others alphabetically

### Encoding
- Preferred: UTF-8.
- If a file is not valid UTF-8, HelpServ falls back to `windows-1252` for compatibility.

### Format rules
- Topic titles are indentation-based.
- Lines starting with `*` are content/body lines for the current topic.
- Lines starting with `$` are ignored.
- Empty lines are ignored.

### Example
```text
English support
 *Welcome to HelpServ.
 Online support
  *What do you need help with?
  AuthServ
   *AuthServ account and login help.
```

### User navigation behavior
- `HELP` starts at root.
- `HELP <topic>` jumps to a topic (path and fuzzy matching supported).
- Numeric selection (`1..N`) enters subtopics.
- `0` goes back one level.

---

## Command Reference

### Public/User Commands
- `HELP [topic]` - show topic tree or specific help node.
- `TOPICS [topic]` / `MENU` - list subtopics.
- `SEARCH <text>` / `FIND <text>` - search titles and topic text.
- `SHOWCOMMANDS [level]` - list commands available at level.
- `COMMAND <name>` - short help for one command.
- `VERSION` - module version.
- `INVITE [#channel]` - request invite (queue/ticket logic applied).
- `WHOAMI` - show account, command level and account config.
- `?`, `?+`, `?-` - shortcuts for term lookup and optional queue actions.

### Term/Glossary Commands
- `TERM [#channel] FIND <pattern>`
- `TERM [#channel] GET <term>`
- `TERM [#channel] LIST [pattern]`
- `TERM [#channel] LISTFULL`
- `TERM [#channel] ADD <term> <description>` (staff+)
- `TERM [#channel] DEL <term...>` (staff+)

### Queue Commands
- `QUEUE [#channel] [LIST|SUMMARY|STATUS]`
- `QUEUE [#channel] NEXT [n]`
- `QUEUE [#channel] DONE [nick..]`
- `QUEUE [#channel] ON|OFF`
- `QUEUE [#channel] MAINTAIN [on|off|n]`
- `QUEUE [#channel] RESET`
- Aliases: `NEXT`, `DONE`, `ENQUEUE`, `DEQUEUE`, `AUTOQUEUE`

### Ticket Commands
- `TICKET <nick> [#channel] [minutes]`
- `RESOLVE <nick|#account> [#channel]`
- `TICKETS [#channel]`
- `SHOWTICKET <nick|#account> [#channel]`
- `TICKETMSG [#channel] [text]`

### Account/Staff Commands
- `WHOIS <nick|#account>`
- `SEEN <nick|#account>`
- `LISTUSER [level] [pattern]`
- `ADDUSER <account|nick> [level]`
- `MODUSER <account|nick> [level]`
- `DELUSER <account>`
- `LEVEL <account> [USER|HELPER|TRIAL|STAFF|OPER|ADMIN|DEV]`
- Shortcuts: `PEON`, `FRIEND`, `TRIAL`, `STAFF`, `OPER`, `ADMIN`, `IMPROPER`

### Channel/Moderation Commands
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

### Runtime/Reporting/Stats Commands
- `STATUS`
- `REPORT <#source> <#target|OFF>`
- `WRITEDB`
- `RELOAD`
- `STATS`, `WEEKSTATS`, `TOP10`, `RATING`, `TERMSTATS`, `CHANSTATS`, `ACTIVESTAFF`
- `STATSDUMP`, `STATSREPAIR`, `STATSRESET`

---

## Command Levels (minimum)

- **Level 0**: `COMMAND`, `SHOWCOMMANDS`, `WHOAMI`
- **Level 1**: `HELP`, `TOPICS`, `SEARCH`, `?`, `VERSION`, `INVITE`
- **Level 3**: queue shortcuts/control (`QUEUE`, `NEXT`, `DONE`, `ENQUEUE`, `DEQUEUE`, `AUTOQUEUE`), `KICK`, `MESSAGE`, `TICKET`, `ACCONF`, `RATING`, `VOICE`, `DEVOICE`, `CHANNEL`, `CHANBAN`
- **Level 4**: `TERM`, `WHOIS`, `SEEN`, `LISTUSER`, `WELCOME`, `OP`, `DEOP`, `OUT`, `BAN`, `CENSOR`, `CHECKCHANNEL`, `CHANSTATS`, `TOP10`, `TICKETS`, `SHOWTICKET`, `RESOLVE`, `TICKETMSG`, `CHANCONF`, `TOPIC`, `DNMO`, `EVERYONEOUT`, `PEON`, `FRIEND`, `IMPROPER`
- **Level 5**: `STATUS`, `RELOAD`, `WRITEDB`, `REPORT`, `LAMERCONTROL`, `IDLEKICK`, `TRIAL`, `STAFF`, `OPER`, `TERMSTATS`
- **Level 6**: `ADDUSER`, `MODUSER`, `ADDCHAN`, `DELCHAN`, `LEVEL`, `ADMIN`, `LCEDIT`, `WEEKSTATS`, `ACTIVESTAFF`, `STATSDUMP`, `STATSREPAIR`, `STATSRESET`

---

## `CHANCONF` Flags (0..21)

`CHANCONF` uses bit-flags controlled by numeric IDs:

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

### Examples
- View all flags:
  - `CHANCONF #help`
- Enable queue + verbose queue:
  - `CHANCONF #help +3 +4`
- Disable ticket requirement:
  - `CHANCONF #help -19`
- Query one flag only:
  - `CHANCONF #help 10`

---

## `ACCONF` Flags (0..5)

Per-account behavior settings:

0. Send replies as private messages  
1. Send replies as notices  
2. Do not send verbose messages  
3. Auto-op on join  
4. Auto-voice on join  
5. Suppress unknown command error

### Examples
- Show current config:
  - `ACCONF`
- Enable PM replies and auto-voice:
  - `ACCONF +0 +4`
- Disable unknown-command suppression:
  - `ACCONF -5`

---

## Typical Operational Flows

### User self-help
1. User sends `/msg HelpServ HELP`.
2. User navigates by numeric menu entries.
3. User uses `SEARCH` for free text lookup.
4. User uses `INVITE` if live support is needed.

### Staff queue handling
1. Enable queue on support channel (`QUEUE #help ON`).
2. Review queue (`QUEUE #help SUMMARY`).
3. Invite next user (`NEXT #help` or `QUEUE #help NEXT 1`).
4. Mark user done (`DONE #help <nick>`).
5. Optionally set maintain target (`QUEUE #help MAINTAIN 2`).

### Ticket-gated support channel
1. Staff creates ticket (`TICKET <nick> #help 30`).
2. User requests invite (`INVITE #help`).
3. HelpServ validates active ticket and invites user.
4. Staff closes ticket (`RESOLVE <nick> #help`) after handling.

---

## Persistence (`helpmod.db`)

If available in `help_dir`, HelpServ restores:
- managed channels,
- channel flags,
- welcome texts,
- queue/ticket state,
- report routes,
- term dictionaries (global/channel),
- account levels and account config,
- statistics snapshots.

If not available, HelpServ continues with configured defaults and in-memory runtime state.

---

## Runtime & Safety Notes

- HelpServ integrates with burst channel registration.
- Managed channels are joined and state-applied at startup/burst.
- Queue and channel mode maintenance can automatically enforce support workflow policies.
- `RELOAD` refreshes topic files and `helpmod.db` without restarting JServ.
- Unknown command error messages can be suppressed per account (`ACCONF +5`).

---

## Troubleshooting

### "No topics found" or fallback topics only
- Verify `help_dir` exists.
- Verify at least one `help_*.txt` file is present.
- Check file encoding (prefer UTF-8).

### `INVITE` denied due to ticket
- Check `CHANCONF +19`.
- Verify ticket with `SHOWTICKET` / `TICKETS`.

### Queue seems inactive
- Check channel queue state via `QUEUE #channel SUMMARY`.
- Ensure queue is enabled (`ON`) and relevant channel flags are set.

### Channel command triggers do not execute
- Ensure channel is managed.
- Ensure `CHANCONF +15` (channel commands enabled).

---

## Related Files
- `JServ/src/main/java/net/midiandmore/jserv/HelpServ.java`
- `config-helpserv.json`
- `help/help_en.txt`
- `help/help_de.txt`
- `help/helpmod.db` (optional, runtime/persistence)
