package net.midiandmore.jserv;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * OperServ - Operator service node
 * Provides a small set of operator-focused commands (help, stats, oper list, channel list, raw send, kill).
 */
public final class OperServ extends AbstractModule implements Software {

    private java.util.Timer glineCleanupTimer;

    public OperServ(JServ jserv, SocketThread socketThread, PrintWriter pw, BufferedReader br) {
        initialize(jserv, socketThread, pw, br);
    }

    @Override
    public String getModuleName() {
        return "OperServ";
    }

    private String resolveFromOperConfig(String currentValue, String key) {
        if (currentValue != null && !currentValue.isBlank()) {
            return currentValue;
        }
        if (mi == null || mi.getConfig() == null || mi.getConfig().getOperFile() == null) {
            return currentValue;
        }
        String v = mi.getConfig().getOperFile().getProperty(key);
        return (v != null && !v.isBlank()) ? v : currentValue;
    }

    @Override
    public void handshake(String nick, String servername, String description, String numeric, String identd) {
        if (!enabled) {
            getLogger().log(Level.WARNING, "OperServ handshake called but module is disabled");
            return;
        }

        String resolvedNick = resolveFromOperConfig(nick, "nick");
        String resolvedServername = resolveFromOperConfig(servername, "servername");
        String resolvedDescription = resolveFromOperConfig(description, "description");
        String resolvedIdentd = resolveFromOperConfig(identd, "identd");

        if (resolvedNick == null || resolvedNick.isBlank()
                || resolvedServername == null || resolvedServername.isBlank()
                || resolvedDescription == null || resolvedDescription.isBlank()) {
            getLogger().log(Level.WARNING, "Cannot perform handshake for OperServ: missing configuration");
            return;
        }
        if (resolvedIdentd == null || resolvedIdentd.isBlank()) {
            resolvedIdentd = resolvedNick.toLowerCase();
        }

        this.numeric = numeric;
        getLogger().log(Level.INFO, "Registering OperServ nick: {0}", resolvedNick);
        sendText("%s N %s 2 %d %s %s +oikrd - %s:%d U]AEB %s%s :%s",
                this.numeric,
                resolvedNick,
                time(),
                resolvedIdentd,
                resolvedServername,
                resolvedNick,
                time(),
                this.numeric,
                getNumericSuffix(),
                resolvedDescription);

        // Ensure operserv tables exist before using them
        if (mi != null && mi.getDb() != null) {
            mi.getDb().ensureOperServTables();
        }
        
        // Synchronize GLines with network
        syncGlinesToNetwork();
        
        // Start GLine cleanup timer (check every 5 minutes)
        startGlineCleanupTimer();
    }

    @Override
    public void registerBurstChannels(java.util.HashMap<String, Burst> bursts, String serverNumeric) {
        if (!enabled) {
            return;
        }
        String channel = "#twilightzone";
        if (!bursts.containsKey(channel.toLowerCase())) {
            bursts.put(channel.toLowerCase(), new Burst(channel));
        }
        bursts.get(channel.toLowerCase()).getUsers().add(serverNumeric + getNumericSuffix());
        getLogger().log(Level.INFO, "OperServ registered burst channel: {0}", channel);
    }
    
    private void syncGlinesToNetwork() {
        if (mi == null || mi.getDb() == null) {
            return;
        }
        
        java.util.ArrayList<String[]> glines = mi.getDb().getAllGlines();
        if (glines.isEmpty()) {
            getLogger().log(Level.INFO, "No GLines to synchronize");
            return;
        }
        
        long now = System.currentTimeMillis() / 1000;
        int syncCount = 0;
        
        for (String[] gline : glines) {
            String mask = gline[0];
            String reason = gline[1];
            long created = Long.parseLong(gline[3]);
            long expires = Long.parseLong(gline[4]);
            
            // Skip expired GLines
            if (expires > 0 && expires < now) {
                continue;
            }
            
            // Calculate remaining duration
            long duration = expires == 0 ? 0 : expires - now;
            
            // Propagate GLine to network
            sendText("%s GL * +%s %d %d :%s", numeric + getNumericSuffix(), mask, duration, created, reason);
            syncCount++;
        }
        
        getLogger().log(Level.INFO, "Synchronized {0} GLine(s) to network", syncCount);
    }
    
    private void startGlineCleanupTimer() {
        if (glineCleanupTimer != null) {
            glineCleanupTimer.cancel();
        }
        glineCleanupTimer = new java.util.Timer("GLineCleanup", true);
        glineCleanupTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                if (mi != null && mi.getDb() != null) {
                    int deleted = mi.getDb().removeExpiredGlines();
                    if (deleted > 0) {
                        getLogger().log(Level.INFO, "GLine cleanup: removed {0} expired GLine(s)", deleted);
                    }
                }
            }
        }, 60000, 300000); // Start after 1 minute, repeat every 5 minutes
        getLogger().log(Level.INFO, "GLine cleanup timer started");
    }

    @Override
    public void parseLine(String text) {
        if (!enabled) {
            return;
        }
        String line = text == null ? "" : text.trim();
        if (line.isEmpty()) {
            return;
        }

        String[] elem = line.split(" ");
        if (elem.length < 2) {
            return;
        }

        if ("P".equals(elem[1]) && elem.length >= 4) {
            handlePrivateMessage(line, elem);
        }
    }

    private void handlePrivateMessage(String line, String[] elem) {
        String senderNumeric = elem[0];
        String targetNumeric = elem[2];
        String myNumeric = numeric + getNumericSuffix();
        if (!myNumeric.equals(targetNumeric)) {
            return;
        }
        if (!elem[3].startsWith(":")) {
            return;
        }

        String message = line.substring(line.indexOf(elem[3]) + 1);
        String[] parts = message.split(" ");
        if (parts.length == 0) {
            return;
        }

        Users sender = getSt().getUsers().get(senderNumeric);
        if (sender == null) {
            return;
        }

        String senderAccount = sender.getAccount();
        
        // Check if this is the REGISTER command
        String command = parts[0].toUpperCase();
        if ("REGISTER".equals(command) && sender.isOper()) {
            // REGISTER bypasses authentication and oper checks
            handleRegister(senderNumeric, senderAccount, parts);
            return;
        }
        
        // For all other commands, require authentication
        if (senderAccount == null || senderAccount.isBlank()) {
            sendNotice(senderNumeric, "Access denied: identify with your account first.");
            return;
        }

        // User muss als Oper eingeloggt sein
        if (!sender.isOper()) {
            sendNotice(senderNumeric, "Access denied: operator login required.");
            return;
        }

        int flags = mi.getDb().getFlags(senderAccount);
        boolean isOper = Userflags.hasFlag(flags, Userflags.Flag.OPER)
            || Userflags.hasFlag(flags, Userflags.Flag.HELPER)
            || Userflags.hasFlag(flags, Userflags.Flag.STAFF)
            || Userflags.hasFlag(flags, Userflags.Flag.DEV);
        if (!isOper) {
            sendNotice(senderNumeric, "Access denied: operator privileges required.");
            return;
        }

        switch (command) {
            case "HELP":
                handleHelp(senderNumeric, parts);
                break;
            case "SHOWCOMMANDS":
                handleShowCommands(senderNumeric);
                break;
            case "STATS":
                handleStats(senderNumeric, senderAccount);
                break;
            case "OPERLIST":
                handleOperList(senderNumeric, senderAccount);
                break;
            case "CHANLIST":
                handleChanList(senderNumeric, senderAccount, parts);
                break;
            case "RAW":
                handleRaw(senderNumeric, senderAccount, parts);
                break;
            case "KILL":
                handleKill(senderNumeric, senderAccount, parts);
                break;
            case "GLINE":
                handleGline(senderNumeric, senderAccount, parts);
                break;
            case "UNGLINE":
                handleUngline(senderNumeric, senderAccount, parts);
                break;
            case "GLIST":
                handleGlist(senderNumeric, senderAccount, parts);
                break;
            case "TRUSTADD":
                handleTrustAdd(senderNumeric, senderAccount, parts);
                break;
            case "TRUSTDEL":
                handleTrustDel(senderNumeric, senderAccount, parts);
                break;
            case "TRUSTGET":
                handleTrustGet(senderNumeric, senderAccount, parts);
                break;
            case "TRUSTSET":
                handleTrustSet(senderNumeric, senderAccount, parts);
                break;
            case "TRUSTLIST":
                handleTrustList(senderNumeric, senderAccount, parts);
                break;
            case "WHOIS":
                handleWhois(senderNumeric, parts);
                break;
            case "REGISTER":
                handleRegister(senderNumeric, senderAccount, parts);
                break;
            case "MIGRATE":
                handleMigratePasswords(senderNumeric, senderAccount);
                break;
            case "CLEANPASS":
                handleCleanPlaintextPasswords(senderNumeric, senderAccount);
                break;
            default:
                sendNotice(senderNumeric, "Unknown command: " + command + ". Use SHOWCOMMANDS.");
        }
    }

    private void handleHelp(String senderNumeric, String[] parts) {
        if (parts.length == 1) {
            sendNotice(senderNumeric, "Syntax: HELP <command>");
            sendNotice(senderNumeric, "Use SHOWCOMMANDS for a list.");
            return;
        }
        String cmd = parts[1].toUpperCase();
        switch (cmd) {
            case "STATS":
                sendNotice(senderNumeric, "STATS - shows user/channel/module counts.");
                break;
            case "OPERLIST":
                sendNotice(senderNumeric, "OPERLIST - lists currently opered users.");
                break;
            case "CHANLIST":
                sendNotice(senderNumeric, "CHANLIST [limit] - lists channels (default 10).");
                break;
            case "RAW":
                sendNotice(senderNumeric, "RAW <line> - send a raw line to uplink as OperServ.");
                break;
            case "KILL":
                sendNotice(senderNumeric, "KILL <nick> <reason> - kill a user by nick.");
                break;
            case "GLINE":
                sendNotice(senderNumeric, "GLINE <ident@host> <duration> <reason> - add a global ban (duration in seconds, 0=permanent).");
                break;
            case "UNGLINE":
                sendNotice(senderNumeric, "UNGLINE <ident@host> - remove a global ban.");
                break;
            case "GLIST":
                sendNotice(senderNumeric, "GLIST [limit] - list active glines (default 20).");
                break;
            case "TRUSTADD":
                sendNotice(senderNumeric, "TRUSTADD <mask> - add a TrustCheck allow rule (mask supports * and ?).");
                sendNotice(senderNumeric, "Optional: TRUSTADD <mask> [maxconn] [ident] [maxidentsperhost]");
                sendNotice(senderNumeric, "Typical: <ident@ip> e.g. *admin*@192.0.2.*");
                break;
            case "TRUSTDEL":
                sendNotice(senderNumeric, "TRUSTDEL <mask> - remove a TrustCheck allow rule.");
                break;
            case "TRUSTGET":
                sendNotice(senderNumeric, "TRUSTGET <mask> - show details of a TrustCheck allow rule.");
                break;
            case "TRUSTSET":
                sendNotice(senderNumeric, "TRUSTSET <mask> - update an existing TrustCheck allow rule.");
                sendNotice(senderNumeric, "Optional: TRUSTSET <mask> [maxconn] [ident|noident] [maxidentsperhost]");
                sendNotice(senderNumeric, "Example: TRUSTSET *admin*@192.0.2.* 5 noident 3");
                break;
            case "TRUSTLIST":
                sendNotice(senderNumeric, "TRUSTLIST [limit] - list TrustCheck allow rules (default 50).");
                break;
            case "WHOIS":
                sendNotice(senderNumeric, "WHOIS <nick> - show user information (account, host, oper status, channels).");
                break;
            case "REGISTER":
                sendNotice(senderNumeric, "REGISTER <username> <password> - register the first admin user (only available when no admin exists).");
                sendNotice(senderNumeric, "This command is automatically disabled after first use.");
                break;
            case "MIGRATE":
                sendNotice(senderNumeric, "MIGRATE - migrate all plaintext passwords to hashed format (SHA-256).");
                sendNotice(senderNumeric, "This command automatically hashes plaintext passwords and removes the cleartext versions.");
                break;
            case "CLEANPASS":
                sendNotice(senderNumeric, "CLEANPASS - delete all remaining plaintext passwords from database.");
                sendNotice(senderNumeric, "WARNING: Only use this after ensuring all passwords are migrated to hashed format!");
                break;
            default:
                sendNotice(senderNumeric, "No detailed help for " + cmd + ".");
        }
    }

    private void handleShowCommands(String senderNumeric) {
        sendNotice(senderNumeric, "***** OperServ Commands *****");
        sendNotice(senderNumeric, "   HELP          Show help");
        sendNotice(senderNumeric, "   SHOWCOMMANDS  List commands");
        sendNotice(senderNumeric, "   STATS         Show users/channels/modules counts");
        sendNotice(senderNumeric, "   OPERLIST      List current opers");
        sendNotice(senderNumeric, "   CHANLIST      List channels (optionally limited)");
        sendNotice(senderNumeric, "   RAW           Send raw line as OperServ");
        sendNotice(senderNumeric, "   KILL          Kill a user by nick");
        sendNotice(senderNumeric, "   GLINE         Add a global ban");
        sendNotice(senderNumeric, "   UNGLINE       Remove a global ban");
        sendNotice(senderNumeric, "   GLIST         List active global bans");
        sendNotice(senderNumeric, "   TRUSTADD      Add a TrustCheck allow rule");
        sendNotice(senderNumeric, "   TRUSTDEL      Remove a TrustCheck allow rule");
        sendNotice(senderNumeric, "   TRUSTGET      Show a TrustCheck allow rule");
        sendNotice(senderNumeric, "   TRUSTSET      Update a TrustCheck allow rule");
        sendNotice(senderNumeric, "   TRUSTLIST     List TrustCheck allow rules");
        sendNotice(senderNumeric, "   WHOIS         Show user information");
        if (mi != null && mi.getDb() != null && !mi.getDb().hasAdminUser()) {
            sendNotice(senderNumeric, "   REGISTER      Register first admin user (only available now)");
        }
        sendNotice(senderNumeric, "   MIGRATE       Migrate plaintext passwords to hashed format");
        sendNotice(senderNumeric, "   CLEANPASS     Delete all plaintext passwords (use after MIGRATE)");
        sendNotice(senderNumeric, "***** End of List *****");
    }

    private void handleStats(String senderNumeric, String senderAccount) {
        int users = getSt().getUsers() != null ? getSt().getUsers().size() : 0;
        int channels = getSt().getChannel() != null ? getSt().getChannel().size() : 0;
        int enabledModules = getSt().getModuleManager() != null ? getSt().getModuleManager().getEnabledModuleCount() : 0;
        sendNotice(senderNumeric, String.format("Users: %d Channels: %d Enabled modules: %d", users, channels, enabledModules));
        getLogger().log(Level.INFO, "OperServ STATS command executed by {0}", senderAccount);
    }

    private void handleOperList(String senderNumeric, String senderAccount) {
        List<String> opers = new ArrayList<>();
        if (getSt().getUsers() != null) {
            for (Users u : getSt().getUsers().values()) {
                if (u.isOper()) {
                    String operAccount = u.getAccount();
                    opers.add(operAccount != null && !operAccount.isEmpty() ? operAccount : u.getNick());
                }
            }
        }
        sendNotice(senderNumeric, opers.isEmpty() ? "No opers online." : "Opers: " + String.join(", ", opers));
        getLogger().log(Level.INFO, "OperServ OPERLIST command executed by {0}", senderAccount);
    }

    private void handleChanList(String senderNumeric, String senderAccount, String[] parts) {
        int limit = 10;
        if (parts.length >= 2) {
            try {
                limit = Math.max(1, Integer.parseInt(parts[1]));
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        if (getSt().getChannel() == null || getSt().getChannel().isEmpty()) {
            sendNotice(senderNumeric, "No channels known.");
            return;
        }
        List<String> names = new ArrayList<>(getSt().getChannel().keySet());
        int count = names.size();
        names.sort(String::compareToIgnoreCase);
        if (names.size() > limit) {
            names = names.subList(0, limit);
        }
        sendNotice(senderNumeric, String.format("Channels (%d total): %s%s", count, String.join(", ", names), count > limit ? " ..." : ""));
        getLogger().log(Level.INFO, "OperServ CHANLIST command executed by {0}", senderAccount);
    }

    private void handleRaw(String senderNumeric, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: RAW <line>");
            return;
        }
        String raw = String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        sendText("%s", raw);
        sendNotice(senderNumeric, "Sent: " + raw);
        getLogger().log(Level.INFO, "OperServ RAW command executed by {0}: {1}", new Object[]{senderAccount, raw});
    }

    private void handleKill(String senderNumeric, String senderAccount, String[] parts) {
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: KILL <nick> <reason>");
            return;
        }
        String nick = parts[1];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(parts, 2, parts.length));
        String targetNumeric = findNumericByNick(nick);
        if (targetNumeric == null) {
            sendNotice(senderNumeric, "User not found: " + nick);
            return;
        }
        sendText("%s D %s :%s", numeric + getNumericSuffix(), targetNumeric, reason);
        getLogger().log(Level.INFO, "OperServ KILL command executed by {0} on {1}: {2}", new Object[]{senderAccount, nick, reason});
    }

    private void handleGline(String senderNumeric, String senderAccount, String[] parts) {
        if (parts.length < 4) {
            sendNotice(senderNumeric, "Syntax: GLINE <ident@host> <duration> <reason>");
            sendNotice(senderNumeric, "Duration in seconds (0 = permanent).");
            return;
        }
        String mask = parts[1];
        long duration;
        try {
            duration = Long.parseLong(parts[2]);
            if (duration < 0) {
                sendNotice(senderNumeric, "Duration must be 0 or positive.");
                return;
            }
        } catch (NumberFormatException e) {
            sendNotice(senderNumeric, "Invalid duration. Must be a number in seconds.");
            return;
        }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(parts, 3, parts.length));

        // Validate mask format
        if (!mask.contains("@")) {
            sendNotice(senderNumeric, "Invalid mask format. Use <ident@host>");
            return;
        }

        // Add GLine to database
        boolean success = mi.getDb().addGline(mask, reason, senderAccount, duration);
        if (success) {
            // Propagate GLine to network (P10 GL command)
            long now = System.currentTimeMillis() / 1000;
            sendText("%s GL * +%s %d %d :%s", numeric + getNumericSuffix(), mask, duration, now, reason);
            
            String durationStr = duration == 0 ? "permanent" : duration + " seconds";
            sendNotice(senderNumeric, "GLINE added: " + mask + " (" + durationStr + ") - Reason: " + reason);
            getLogger().log(Level.INFO, "OperServ GLINE command executed by {0} on {1} ({2}s): {3}", 
                    new Object[]{senderAccount, mask, duration, reason});
        } else {
            sendNotice(senderNumeric, "Failed to add GLINE. Mask may already exist.");
        }
    }

    private void handleUngline(String senderNumeric, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: UNGLINE <ident@host>");
            return;
        }
        String mask = parts[1];

        // Remove GLine from database
        boolean success = mi.getDb().removeGline(mask);
        if (success) {
            // Propagate GLine removal to network (P10 GL command)
            sendText("%s GL * -%s", numeric + getNumericSuffix(), mask);
            
            sendNotice(senderNumeric, "GLINE removed: " + mask);
            getLogger().log(Level.INFO, "OperServ UNGLINE command executed by {0} on {1}", 
                    new Object[]{senderAccount, mask});
        } else {
            sendNotice(senderNumeric, "Failed to remove GLINE. Mask not found.");
        }
    }

    private void handleGlist(String senderNumeric, String senderAccount, String[] parts) {
        int limit = 20;
        if (parts.length >= 2) {
            try {
                limit = Math.max(1, Integer.parseInt(parts[1]));
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }

        // Retrieve GLines from database
        java.util.ArrayList<String[]> glines = mi.getDb().getGlines(limit);
        if (glines.isEmpty()) {
            sendNotice(senderNumeric, "No active GLines.");
        } else {
            sendNotice(senderNumeric, "Active GLines (showing " + glines.size() + "):");
            for (String[] gline : glines) {
                long expires = Long.parseLong(gline[4]);
                String expireStr = expires == 0 ? "Permanent" : "Expires in " + ((expires - System.currentTimeMillis() / 1000) / 3600) + " hours";
                sendNotice(senderNumeric, "  " + gline[0] + " - " + gline[1] + " (by " + gline[2] + ") [" + expireStr + "]");
            }
        }
        getLogger().log(Level.INFO, "OperServ GLIST command executed by {0}", senderAccount);
    }

    private void handleTrustAdd(String senderNumeric, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: TRUSTADD <mask>");
            sendNotice(senderNumeric, "Optional: TRUSTADD <mask> [maxconn] [ident] [maxidentsperhost]");
            sendNotice(senderNumeric, "Example: TRUSTADD *admin*@192.0.2.* 10 ident 3");
            return;
        }
        String mask = parts[1];
        if (mask == null || mask.isBlank()) {
            sendNotice(senderNumeric, "Mask must not be empty.");
            return;
        }

        int maxConn = 0;
        boolean requireIdent = false;
        int maxIdentsPerHost = 0;
        boolean identParsed = false;

        // Parse optional args after mask
        for (int i = 2; i < parts.length; i++) {
            String p = parts[i];
            if (p == null) {
                continue;
            }
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.equalsIgnoreCase("ident") || t.equalsIgnoreCase("require_ident") || t.equalsIgnoreCase("ident=1") || t.equals("1") || t.equalsIgnoreCase("true") || t.equalsIgnoreCase("yes")) {
                requireIdent = true;
                identParsed = true;
                continue;
            }
            if (t.equalsIgnoreCase("noident") || t.equalsIgnoreCase("ident=0") || t.equals("0") || t.equalsIgnoreCase("false") || t.equalsIgnoreCase("no")) {
                requireIdent = false;
                identParsed = true;
                continue;
            }
            try {
                int val = Integer.parseInt(t);
                if (maxConn == 0 && !identParsed) {
                    maxConn = val;
                } else {
                    maxIdentsPerHost = val;
                }
            } catch (NumberFormatException ignored) {
                // ignore unknown token
            }
        }

        boolean success = mi.getDb().addTrust(mask, senderAccount, maxConn, requireIdent, maxIdentsPerHost);
        if (success) {
            String extra = " (maxconn=" + Math.max(0, maxConn) + ", ident=" + (requireIdent ? "required" : "optional") + ", maxidentsperhost=" + Math.max(0, maxIdentsPerHost) + ")";
            sendNotice(senderNumeric, "Trust rule added: " + mask + extra);
            getLogger().log(Level.INFO, "OperServ TRUSTADD executed by {0}: {1} maxconn={2} requireIdent={3} maxIdentsPerHost={4}", new Object[]{senderAccount, mask, Math.max(0, maxConn), requireIdent, Math.max(0, maxIdentsPerHost)});
        } else {
            sendNotice(senderNumeric, "Failed to add trust rule. Mask may already exist.");
        }
    }

    private void handleTrustDel(String senderNumeric, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: TRUSTDEL <mask>");
            return;
        }
        String mask = parts[1];
        boolean success = mi.getDb().removeTrust(mask);
        if (success) {
            sendNotice(senderNumeric, "Trust rule removed: " + mask);
            getLogger().log(Level.INFO, "OperServ TRUSTDEL executed by {0}: {1}", new Object[]{senderAccount, mask});
        } else {
            sendNotice(senderNumeric, "Failed to remove trust rule. Mask not found.");
        }
    }

    private void handleTrustGet(String senderNumeric, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: TRUSTGET <mask>");
            return;
        }

        String mask = parts[1];
        if (mask == null || mask.isBlank()) {
            sendNotice(senderNumeric, "Mask must not be empty.");
            return;
        }

        String[] tr = mi.getDb().getTrustRule(mask);
        if (tr == null) {
            sendNotice(senderNumeric, "Trust rule not found: " + mask);
            return;
        }

        String maxConn = tr.length > 1 ? tr[1] : "0";
        String reqIdent = tr.length > 2 ? tr[2] : "false";
        String maxIdentsPerHost = tr.length > 3 ? tr[3] : "0";
        sendNotice(senderNumeric, "Trust rule: " + tr[0] + " (maxconn " + maxConn + ", ident " + reqIdent + ", maxidentsperhost " + maxIdentsPerHost + ")");
        getLogger().log(Level.INFO, "OperServ TRUSTGET executed by {0}: {1}", new Object[]{senderAccount, mask});
    }

    private void handleTrustSet(String senderNumeric, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: TRUSTSET <mask> [maxconn] [ident|noident] [maxidentsperhost]");
            sendNotice(senderNumeric, "Example: TRUSTSET *admin*@192.0.2.* 5 noident 3");
            return;
        }

        String mask = parts[1];
        if (mask == null || mask.isBlank()) {
            sendNotice(senderNumeric, "Mask must not be empty.");
            return;
        }

        String[] current = mi.getDb().getTrustRule(mask);
        if (current == null) {
            sendNotice(senderNumeric, "Trust rule not found: " + mask);
            return;
        }

        int maxConn;
        try {
            maxConn = Integer.parseInt(current[1]);
        } catch (NumberFormatException ignored) {
            maxConn = 0;
        }
        boolean requireIdent = Boolean.parseBoolean(current[2]);
        int maxIdentsPerHost = 0;
        if (current.length >= 4) {
            try {
                maxIdentsPerHost = Integer.parseInt(current[3]);
            } catch (NumberFormatException ignored) {
                maxIdentsPerHost = 0;
            }
        }

        boolean changed = false;
        boolean identParsed = false;
        for (int i = 2; i < parts.length; i++) {
            String p = parts[i];
            if (p == null) {
                continue;
            }
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.equalsIgnoreCase("ident") || t.equalsIgnoreCase("require_ident") || t.equalsIgnoreCase("ident=1") || t.equals("1") || t.equalsIgnoreCase("true") || t.equalsIgnoreCase("yes")) {
                requireIdent = true;
                identParsed = true;
                changed = true;
                continue;
            }
            if (t.equalsIgnoreCase("noident") || t.equalsIgnoreCase("ident=0") || t.equals("0") || t.equalsIgnoreCase("false") || t.equalsIgnoreCase("no")) {
                requireIdent = false;
                identParsed = true;
                changed = true;
                continue;
            }
            try {
                int val = Math.max(0, Integer.parseInt(t));
                if (maxConn == 0 && !identParsed) {
                    maxConn = val;
                } else {
                    maxIdentsPerHost = val;
                }
                changed = true;
            } catch (NumberFormatException ignored) {
                // ignore unknown token
            }
        }

        if (!changed) {
            sendNotice(senderNumeric, "No changes specified. Current: maxconn=" + Math.max(0, maxConn) + ", ident=" + (requireIdent ? "required" : "optional") + ", maxidentsperhost=" + Math.max(0, maxIdentsPerHost));
            return;
        }

        boolean success = mi.getDb().updateTrust(mask, maxConn, requireIdent, maxIdentsPerHost);
        if (success) {
            String extra = " (maxconn=" + Math.max(0, maxConn) + ", ident=" + (requireIdent ? "required" : "optional") + ", maxidentsperhost=" + Math.max(0, maxIdentsPerHost) + ")";
            sendNotice(senderNumeric, "Trust rule updated: " + mask + extra);
            getLogger().log(Level.INFO, "OperServ TRUSTSET executed by {0}: {1} maxconn={2} requireIdent={3} maxIdentsPerHost={4}", new Object[]{senderAccount, mask, Math.max(0, maxConn), requireIdent, Math.max(0, maxIdentsPerHost)});
        } else {
            sendNotice(senderNumeric, "Failed to update trust rule. Mask not found.");
        }
    }

    private void handleTrustList(String senderNumeric, String senderAccount, String[] parts) {
        int limit = 50;
        if (parts.length >= 2) {
            try {
                limit = Math.max(1, Integer.parseInt(parts[1]));
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }

        java.util.ArrayList<String[]> trusts = mi.getDb().getTrusts(limit);
        if (trusts.isEmpty()) {
            sendNotice(senderNumeric, "No TrustCheck allow rules.");
        } else {
            sendNotice(senderNumeric, "TrustCheck allow rules (showing " + trusts.size() + "):");
            for (String[] tr : trusts) {
                String mask = tr[0];
                String setby = tr[1];
                String created = tr[2];
                String maxConn = tr.length > 3 ? tr[3] : "0";
                String reqIdent = tr.length > 4 ? tr[4] : "false";
                String maxIdentsPerHost = tr.length > 5 ? tr[5] : "0";
                sendNotice(senderNumeric, "  " + mask + " (by " + setby + ", created " + created + ", maxconn " + maxConn + ", ident " + reqIdent + ", maxidentsperhost " + maxIdentsPerHost + ")");
            }
        }
        getLogger().log(Level.INFO, "OperServ TRUSTLIST command executed by {0}", senderAccount);
    }

    private String findNumericByNick(String nick) {
        if (nick == null || nick.isBlank() || getSt().getUsers() == null) {
            return null;
        }
        for (Users u : getSt().getUsers().values()) {
            if (u.getNick() != null && u.getNick().equalsIgnoreCase(nick)) {
                return u.getId();
            }
        }
        return null;
    }

    private void sendNotice(String targetNumeric, String message) {
        sendText("%s%s O %s :%s", numeric, getNumericSuffix(), targetNumeric, message);
    }
    
    private void handleWhois(String senderNumeric, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: WHOIS <nick>");
            return;
        }
        String nick = parts[1];
        String targetNumeric = findNumericByNick(nick);
        if (targetNumeric == null) {
            sendNotice(senderNumeric, "User not found: " + nick);
            return;
        }
        Users u = getSt().getUsers().get(targetNumeric);
        if (u == null) {
            sendNotice(senderNumeric, "User not found: " + nick);
            return;
        }
        sendNotice(senderNumeric, "Nick: " + u.getNick());
        sendNotice(senderNumeric, "Account: " + (u.getAccount() == null || u.getAccount().isBlank() ? "(none)" : u.getAccount()));
        sendNotice(senderNumeric, "Host: " + u.getHost());
        sendNotice(senderNumeric, "Oper: " + (u.isOper() ? "yes" : "no"));
        sendNotice(senderNumeric, "Channels: " + u.getChannels().size());
    }

    private void handleRegister(String senderNumeric, String senderAccount, String[] parts) {
        // Check if user is an IRC operator
        Users sender = getSt().getUsers().get(senderNumeric);
        if (sender == null || !sender.isOper()) {
            sendNotice(senderNumeric, "Access denied: You must be an IRC operator (/OPER) to use this command.");
            return;
        }
        
        // Ensure ChanServ tables exist
        if (mi == null || mi.getDb() == null) {
            sendNotice(senderNumeric, "Database not available.");
            return;
        }
        
        mi.getDb().ensureChanServTables();
        
        // Check if an admin already exists
        if (mi.getDb().hasAdminUser()) {
            sendNotice(senderNumeric, "REGISTER command is disabled. An admin user already exists.");
            getLogger().log(Level.WARNING, "OperServ REGISTER attempted by {0} but admin already exists", sender.getNick());
            return;
        }
        
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: REGISTER <username> <password>");
            sendNotice(senderNumeric, "This registers the first admin user. Command will be disabled after use.");
            return;
        }
        
        String username = parts[1];
        String password = parts[2];
        
        // Validate username
        if (username.length() > 30 || username.length() < 2) {
            sendNotice(senderNumeric, "Username must be between 2 and 30 characters.");
            return;
        }
        
        if (!username.matches("[a-zA-Z0-9_\\-]+")) {
            sendNotice(senderNumeric, "Username can only contain letters, numbers, underscore and dash.");
            return;
        }
        
        // Validate password
        if (password.length() > 11 || password.length() < 4) {
            sendNotice(senderNumeric, "Password must be between 4 and 11 characters.");
            return;
        }
        
        // Check if username already exists
        if (mi.getDb().isRegistered(username)) {
            sendNotice(senderNumeric, "Username already registered.");
            return;
        }
        
        long now = System.currentTimeMillis() / 1000;
        
        // Register user with admin flag (+d = 512)
        // Flags: +d (512) = developer/admin
        boolean success = mi.getDb().addUser(username, password, 36);
        
        if (success) {
            sendNotice(senderNumeric, "Successfully registered admin user: " + username);
            sendNotice(senderNumeric, "You can now authenticate with /msg AuthServ auth " + username + " <password>");
            sendNotice(senderNumeric, "REGISTER command is now disabled.");
            getLogger().log(Level.INFO, "OperServ REGISTER: First admin user created: {0} by {1}", new Object[]{username, senderAccount});
        } else {
            sendNotice(senderNumeric, "Failed to register user. Check logs for details.");
            getLogger().log(Level.SEVERE, "OperServ REGISTER failed for username: {0}", username);
        }
    }

    /**
     * Handles the MIGRATE command - migrates plaintext passwords to hashed format
     */
    private void handleMigratePasswords(String senderNumeric, String senderAccount) {
        if (mi == null || mi.getDb() == null) {
            sendNotice(senderNumeric, "Database not available.");
            return;
        }
        
        sendNotice(senderNumeric, "Starting password migration...");
        getLogger().log(Level.INFO, "OperServ MIGRATE command executed by {0} - starting password migration", senderAccount);
        
        try {
            mi.getDb().migratePasswordsToSecure();
            sendNotice(senderNumeric, "Password migration completed successfully.");
            sendNotice(senderNumeric, "All plaintext passwords have been hashed with SHA-256 and removed from cleartext.");
            sendNotice(senderNumeric, "Check server logs for details.");
            getLogger().log(Level.INFO, "OperServ MIGRATE command completed successfully by {0}", senderAccount);
        } catch (Exception ex) {
            sendNotice(senderNumeric, "Password migration failed: " + ex.getMessage());
            getLogger().log(Level.SEVERE, "OperServ MIGRATE command failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Handles the CLEANPASS command - deletes all remaining plaintext passwords
     */
    private void handleCleanPlaintextPasswords(String senderNumeric, String senderAccount) {
        if (mi == null || mi.getDb() == null) {
            sendNotice(senderNumeric, "Database not available.");
            return;
        }
        
        sendNotice(senderNumeric, "WARNING: This will permanently delete all plaintext passwords!");
        sendNotice(senderNumeric, "Ensure all passwords are migrated first (use MIGRATE command).");
        sendNotice(senderNumeric, "Starting cleanup...");
        getLogger().log(Level.WARNING, "OperServ CLEANPASS command executed by {0} - deleting plaintext passwords", senderAccount);
        
        try {
            int deleted = mi.getDb().deleteAllPlaintextPasswords();
            sendNotice(senderNumeric, "Cleanup completed: " + deleted + " plaintext passwords deleted.");
            sendNotice(senderNumeric, "Check server logs for details.");
            getLogger().log(Level.INFO, "OperServ CLEANPASS command completed by {0}: {1} passwords deleted", new Object[]{senderAccount, deleted});
        } catch (Exception ex) {
            sendNotice(senderNumeric, "Cleanup failed: " + ex.getMessage());
            getLogger().log(Level.SEVERE, "OperServ CLEANPASS command failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void shutdown() {
        if (glineCleanupTimer != null) {
            glineCleanupTimer.cancel();
            getLogger().log(Level.INFO, "GLine cleanup timer stopped");
        }
    }
}
