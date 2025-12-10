/*
 * NickServ - Nickname Protection Service
 * Protects registered nicknames and enforces authentication
 */
package net.midiandmore.jserv;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NickServ Module - Enforces nickname protection
 * Kills unauthenticated users using registered nicknames after a grace period
 * 
 * @author Andreas Pschorn
 */
public final class NickServ implements Software, Module {
    
    private static final Logger LOG = Logger.getLogger(NickServ.class.getName());
    
    // Default values for configuration
    private static final int DEFAULT_GRACE_PERIOD = 60; // seconds
    private static final int DEFAULT_CHECK_INTERVAL = 10000; // milliseconds
    private static final int DEFAULT_GLINE_ATTEMPTS = 3;
    private static final int DEFAULT_GLINE_DURATION = 600; // seconds
    private static final String OPS_CHANNEL = "#twilightzone";
    private static final String DEFAULT_AUTH_SERVICE = "M@services.midiandmore.net";
    
    private boolean enabled = false;
    private JServ jserv;
    private SocketThread socketThread;
    private PrintWriter pw;
    private String numeric;
    private String numericSuffix;
    private String nick;
    private String servername;
    private String description;
    
    // Configurable settings
    private int gracePeriod = DEFAULT_GRACE_PERIOD;
    private int checkInterval = DEFAULT_CHECK_INTERVAL;
    private boolean glineEnabled = true;
    private int glineAttempts = DEFAULT_GLINE_ATTEMPTS;
    private int glineDuration = DEFAULT_GLINE_DURATION;
    private String authService = DEFAULT_AUTH_SERVICE;
    
    // Track burst state internally
    private boolean inBurst = true;
    
    // Track users that need to authenticate (numeric -> timestamp when they connected)
    private final ConcurrentHashMap<String, Long> unauthenticatedUsers;
    
    // Track dummy nicks created for protection (nick -> dummy numeric)
    private final ConcurrentHashMap<String, String> dummyNicks;
    
    // Counter for generating unique dummy numerics
    private int dummyCounter = 0;
    
    // Timer for periodic checks
    private Timer enforcementTimer;
    
    public NickServ(JServ jserv, SocketThread socketThread, PrintWriter pw, BufferedReader br) {
        this.unauthenticatedUsers = new ConcurrentHashMap<>();
        this.dummyNicks = new ConcurrentHashMap<>();
        initialize(jserv, socketThread, pw, br);
    }
    
    @Override
    public void initialize(JServ jserv, SocketThread socketThread, PrintWriter pw, BufferedReader br) {
        this.jserv = jserv;
        this.socketThread = socketThread;
        this.pw = pw;
        this.enabled = false;
        
        // Load G-Line configuration
        loadGLineConfiguration();
        
        LOG.log(Level.INFO, "NickServ module initialized");
    }
    
    @Override
    public void handshake(String nick, String servername, String description, String numeric, String identd) {
        if (!enabled) {
            LOG.log(Level.WARNING, "NickServ handshake called but module is disabled");
            return;
        }
        
        this.numeric = numeric;
        // Use nick from configuration if not provided
        this.nick = (nick != null && !nick.isEmpty()) ? nick : loadNickFromConfig();
        this.servername = servername;
        this.description = description;
        
        // Ensure database tables exist
        ensureNickServTableExists();
        
        LOG.log(Level.INFO, "Registering NickServ nick: {0}", nick);
        
        // Register NickServ bot with P10 protocol
        // N <nick> <hop> <timestamp> <user> <host> <modes> <base64-ip> <numeric> :<realname>
        sendText("%s N %s 2 %d %s %s +oikr - %s:%d U]AEB %s%s :%s", 
                numeric, nick, time(), identd, servername, nick, time(), numeric, getNumericSuffix(), description);
        
        // Start the enforcement timer
        startEnforcementTimer();
    }
    
    @Override
    public void parseLine(String text) {
        if (!enabled) {
            return;
        }
        
        try {
            final String line = text.trim();
            String[] elem = line.split(" ");
            
            if (elem.length < 2) {
                return;
            }
            
            // Debug logging for private messages
            if (jserv.getConfig().getConfigFile().getProperty("debug", "false").equalsIgnoreCase("true") &&
                elem.length >= 3 && elem[1].equals("P")) {
                LOG.log(Level.INFO, "DEBUG NickServ parseLine: target={0}, expectedNumeric={1}, expectedNick@Host={2}@{3}", 
                        new Object[]{elem[2], numeric + getNumericSuffix(), nick, servername});
            }
            
            // Track end of burst
            if (elem[1].equals("EB")) {
                inBurst = false;
                LOG.log(Level.INFO, "End of burst detected, NickServ protection now active");
                return;
            }
            
            // Handle new user connections during burst (check for reserved nicks)
            if (inBurst && elem[1].equals("N") && elem.length >= 8) {
                handleNewUserDuringBurst(elem);
                return;
            }
            
            // Skip other processing during burst (before EB command)
            if (inBurst) {
                return;
            }
            
            // Handle nick changes (N command with 3-4 parameters: source, N, newnick, [timestamp])
            if (elem[1].equals("N") && (elem.length == 3 || elem.length == 4)) {
                handleNickChange(elem);
            }
            
            // Handle new user connections (N command with many parameters)
            else if (elem[1].equals("N") && elem.length >= 8) {
                handleNewUser(elem);
            }
            
            // Handle account authentication (AC command)
            else if (elem[1].equals("AC") && elem.length >= 3) {
                handleAuthentication(elem);
            }
            
            // Handle user quit (Q command)
            else if (elem[1].equals("Q")) {
                handleQuit(elem);
            }
            
            // Handle user kill (D command)
            else if (elem[1].equals("D") && elem.length >= 3) {
                handleKill(elem);
            }
            
            // Handle private messages to NickServ (ignore channel messages)
            // Support both numeric format (ABAA) and "BotName@Host" format
            // For "BotName@Host", only check the nickname part before @ to support different service hostnames
            else if (socketThread.getServerNumeric() != null && 
                     elem[1].equals("P") && elem.length >= 3 &&
                     !elem[2].startsWith("#")) {
                boolean isTargetingThisBot = elem[2].equals(numeric + getNumericSuffix());
                
                // Check if target is "SomeNick@SomeHost" and nickname matches
                if (!isTargetingThisBot && nick != null && elem[2].contains("@")) {
                    String targetNick = elem[2].substring(0, elem[2].indexOf("@"));
                    isTargetingThisBot = targetNick.equalsIgnoreCase(nick);
                }
                
                if (isTargetingThisBot) {
                    if (jserv.getConfig().getConfigFile().getProperty("debug", "false").equalsIgnoreCase("true")) {
                        LOG.log(Level.FINE, "DEBUG NickServ: Handling private message - target={0}, numeric={1}, nick={2}", 
                                new Object[]{elem[2], numeric + getNumericSuffix(), nick});
                    }
                    handlePrivateMessage(elem);
                }
            }
            
        } catch (Exception e) {
            LOG.log(Level.SEVERE, () -> "Error parsing line in NickServ: " + text);
            LOG.log(Level.SEVERE, "Exception details", e);
        }
    }
    
    /**
     * Handles new user connections during server burst
     * Checks if user is using a reserved dummy nick or registered nick without auth
     */
    private void handleNewUserDuringBurst(String[] elem) {
        if (elem.length < 10) {
            return; // Not enough elements for a valid N command
        }
        
        String userNumeric = extractNumericFromNCommand(elem);
        String userNick = elem[2];
        String modes = elem[6];
        String authInfo = elem[7];
        if(!authInfo.equals("-")) {
            modes = elem[7];
            authInfo = elem[8];
        }
        
        // Skip opers and services
        if (modes.contains("o") || modes.contains("k")) {
            return;
        }
        
        // Check if this nick is currently reserved by a dummy
        if (dummyNicks.containsKey(userNick)) {
            String dummyNumeric = dummyNicks.get(userNick);
            LOG.log(Level.INFO, "User {0} ({1}) tried to use reserved dummy nick {2} during burst", 
                    new Object[]{userNick, userNumeric, dummyNumeric});
            
            // Kill the user immediately - nick is already reserved
            socketThread.sendText("%s D %s %d : (This nickname is protected by services)", 
                    numeric, userNumeric, time());
            
            LOG.log(Level.INFO, "Killed user {0} for using reserved dummy nick during burst", userNumeric);
            return;
        }
        
        // Check if the nick is registered
        if (!isNickRegistered(userNick)) {
            return; // Nick is not registered, nothing to do
        }
        
        // Check if user has PROTECT flag - if yes, skip enforcement completely
        int userFlags = jserv.getDb().getFlags(userNick);
        if (Userflags.hasFlag(userFlags, Userflags.Flag.PROTECT)) {
            LOG.log(Level.FINE, "User {0} has PROTECT flag during burst, skipping nick protection", userNumeric);
            return;
        }
        
        // Nick is registered - check if user is authenticated for THIS nick
        boolean isAuthenticatedForThisNick = false;
        
        if (!authInfo.equals("-") && authInfo.contains(":")) {
            String[] authParts = authInfo.split(":");
            if (authParts.length >= 1 && !authParts[0].isEmpty()) {
                String accountName = authParts[0];
                // User is authenticated if their account name matches the nick (case-insensitive)
                // OR if the nick is reserved by their account
                isAuthenticatedForThisNick = accountName.equalsIgnoreCase(userNick) || 
                                            isNickOwnedByAccount(userNick, accountName);
                LOG.log(Level.FINE, "User {0} during burst is authenticated as {1}, matches nick: {2}", 
                        new Object[]{userNick, accountName, isAuthenticatedForThisNick});
            }
        }
        
        // If not authenticated for this nick during burst, kill immediately
        if (!isAuthenticatedForThisNick) {
            LOG.log(Level.INFO, "User {0} ({1}) connected during burst with registered nick without matching authentication - killing immediately", 
                    new Object[]{userNick, userNumeric});
            
            // Get user host for tracking (we need to reconstruct it from elem)
            // P10 N command format: server N nick hopcount timestamp ident host +modes [base64ip] numeric :realname
            // Positions: [0] server [1] N [2] nick [3] hop [4] timestamp [5] ident [6] host [7] modes
            String ident = elem[5];
            String host = elem[6];
            String userHost = String.format("%s@%s", ident, host);
            
            // Track this failed attempt
            LOG.log(Level.INFO, "UserHost for tracking (burst): {0}", userHost);
            trackFailedAttempt(userHost);
            
            // Kill the user
            socketThread.sendText("%s D %s %d : (This nickname is registered. Please authenticate first.)", 
                    numeric, userNumeric, time());
            
            // Check if we should create a dummy nick after killing
            if (shouldCreateDummy(userHost)) {
                // Create dummy after longer delay to prevent collision
                Timer delayTimer = new Timer("DummyCreation-Burst-" + userNick, false);
                delayTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        createDummyNick(userNick, userHost);
                    }
                }, 1000); // 1000ms delay to prevent collision
            }
        }
    }
    
    /**
     * Handles new user connections
     */
    private void handleNewUser(String[] elem) {
        // elem[0] = source (server numeric)
        // elem[2] = nick
        // elem[6] = modes (+oikr, etc.)
        // elem[7] = base64 IP or account info (format: account:timestamp:flags or -)
        // User numeric is extracted before the ":" in realname
        
        if (elem.length < 10) {
            return; // Not enough elements for a valid N command
        }
        
        String userNumeric = extractNumericFromNCommand(elem);
        String userNick = elem[2];
        String modes = elem[6];       
        String authInfo = elem[7];
        if(!authInfo.equals("-")) {
            modes = elem[7];
            authInfo = elem[8];
        }

        
        // Skip opers and services
        if (modes.contains("o") || modes.contains("k")) {
            LOG.log(Level.FINE, "Skipping nick protection for oper/service: {0}", userNick);
            return;
        }
        
        // Check if the nick is registered
        if (!isNickRegistered(userNick)) {
            return; // Nick is not registered, nothing to do
        }
        
        // Check if user has PROTECT flag - if yes, skip enforcement completely
        int userFlags = jserv.getDb().getFlags(userNick);
        if (Userflags.hasFlag(userFlags, Userflags.Flag.PROTECT)) {
            LOG.log(Level.FINE, "User {0} has PROTECT flag, skipping nick protection", userNumeric);
            return;
        }
        
        // Nick is registered - check if user is authenticated for THIS nick
        boolean isAuthenticatedForThisNick = false;
        
        if (!authInfo.equals("-") && authInfo.contains(":")) {
            String[] authParts = authInfo.split(":");
            if (authParts.length >= 1 && !authParts[0].isEmpty()) {
                String accountName = authParts[0];
                // User is authenticated if their account name matches the nick (case-insensitive)
                // OR if the nick is reserved by their account
                isAuthenticatedForThisNick = accountName.equalsIgnoreCase(userNick) || 
                                            isNickOwnedByAccount(userNick, accountName);
                LOG.log(Level.FINE, "User {0} is authenticated as {1}, matches nick: {2}", 
                        new Object[]{userNick, accountName, isAuthenticatedForThisNick});
            }
        }
        
        // If authenticated for this nick, no warning needed
        if (isAuthenticatedForThisNick) {
            return;
        }
        
        // User is either not authenticated, or authenticated as different account
        LOG.log(Level.INFO, "User {0} ({1}) connected with registered nick without matching authentication", 
                new Object[]{userNick, userNumeric});
        
        // Add to unauthenticated users list with current timestamp
        unauthenticatedUsers.putIfAbsent(userNumeric, time());
        
        // Send warning notice to user numeric (not server numeric)
        String notice = "O";
        Users user = socketThread.getUsers().get(userNumeric);
        if (user != null && !socketThread.isNotice(user.getAccount())) {
            notice = "P";
        }
        
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "*** This nickname is registered and protected ***");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "If this is your nickname, please authenticate now using:");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "    /msg %s AUTH <username> <password>", authService);
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "If this is not your nickname, please change it now using: /nick newnick");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "You have %d seconds to comply, or you will be disconnected.", gracePeriod);
    }
    
    /**
     * Handles user authentication
     * Format: SP AC userNumeric accountName timestamp flags
     */
    private void handleAuthentication(String[] elem) {
        // AC command comes from service (SP), not from user
        // elem[0] = service numeric (SP, S2, etc.)
        // elem[1] = AC
        // elem[2] = user numeric
        // elem[3] = account name
        
        if (elem.length < 4) {
            return;
        }
        
        String userNumeric = elem[2];
        String accountName = elem[3];
        
        // Get user info
        Users user = socketThread.getUsers().get(userNumeric);
        if (user != null) {
            // Clear failed attempts for this user's host
            // Format: ident@host (without nick, since tracking is host-based)
            String userHost = user.getHost();
            jserv.getDb().clearFailedAttempts(userHost);
            LOG.log(Level.INFO, "Cleared failed attempts for {0} after successful authentication", userHost);
        }
        
        // Check if user was in enforcement list
        if (unauthenticatedUsers.containsKey(userNumeric)) {
            if (user != null) {
                String userNick = user.getNick();
                
                // Check if the current nick belongs to the authenticated account
                if (accountName.equalsIgnoreCase(userNick) || isNickOwnedByAccount(userNick, accountName)) {
                    // Nick belongs to this account - remove from enforcement and send success notice
                    unauthenticatedUsers.remove(userNumeric);
                    
                    LOG.log(Level.INFO, "User {0} authenticated as {1} with matching nick {2}, removed from enforcement list", 
                            new Object[]{userNumeric, accountName, userNick});
                    
                    String notice = "O";
                    if (!socketThread.isNotice(user.getAccount())) {
                        notice = "P";
                    }
                    
                    socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                            "Authentication successful. You may now use the nickname %s.", userNick);
                } else {
                    // Nick does NOT belong to this account - keep in enforcement list!
                    LOG.log(Level.INFO, "User {0} authenticated as {1} but uses non-matching protected nick {2}, keeping in enforcement list", 
                            new Object[]{userNumeric, accountName, userNick});
                    
                    String notice = "O";
                    if (!socketThread.isNotice(user.getAccount())) {
                        notice = "P";
                    }
                    
                    socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                            "You are authenticated as %s, but nickname %s belongs to a different account.", accountName, userNick);
                    socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                            "Please change your nickname or you will be disconnected.");
                }
            }
        }
        
        // Release ONLY dummy nicks that belong to this authenticated account
        // This handles cases where user authenticates with a different nick
        releaseAllDummiesForAccount(accountName);
    }
    
    /**
     * Handles nick changes
     */
    private void handleNickChange(String[] elem) {
        // elem[0] = user numeric
        // elem[1] = N
        // elem[2] = new nick
        
        String userNumeric = elem[0];
        String newNick = elem[2];
        
        LOG.log(Level.FINE, "User {0} changed nick to {1}", new Object[]{userNumeric, newNick});
        
        // Check if user exists and get their info
        Users user = socketThread.getUsers().get(userNumeric);
        if (user == null) {
            LOG.log(Level.WARNING, "User object not found for numeric {0}", userNumeric);
            return;
        }
        
        // Skip opers and services
        if (user.isOper() || user.isService()) {
            LOG.log(Level.FINE, "Skipping nick protection for oper/service {0}", userNumeric);
            return;
        }
        
        // Check if new nick is registered in database
        boolean isNewNickRegistered = isNickRegistered(newNick);
        
        if (!isNewNickRegistered) {
            // User changed to unregistered nick - remove from enforcement list if present
            if (unauthenticatedUsers.remove(userNumeric) != null) {
                LOG.log(Level.INFO, "User {0} removed from enforcement list (changed to unregistered nick)", userNumeric);
            }
            return;
        }
        
        // Check if new nick has PROTECT flag - if yes, skip enforcement completely
        int userFlags = jserv.getDb().getFlags(newNick);
        if (Userflags.hasFlag(userFlags, Userflags.Flag.PROTECT)) {
            LOG.log(Level.FINE, "Nick {0} has PROTECT flag, skipping nick protection", newNick);
            // Remove from enforcement list if present
            if (unauthenticatedUsers.remove(userNumeric) != null) {
                LOG.log(Level.INFO, "User {0} removed from enforcement list (nick has PROTECT flag)", userNumeric);
            }
            return;
        }
        
        // New nick is registered - check if user is authenticated for THIS nick
        boolean isAuthed = socketThread.isAuthed(user.getAccount());
        boolean isAuthenticatedForThisNick = false;
        
        if (isAuthed) {
            // Check if account name matches the new nick
            String accountName = user.getAccount();
            if (accountName != null && !accountName.isEmpty()) {
                isAuthenticatedForThisNick = accountName.equalsIgnoreCase(newNick) ||
                                            isNickOwnedByAccount(newNick, accountName);
            }
        }
        
        LOG.log(Level.FINE, "Nick change: {0} -> {1}, registered: {2}, authed: {3}, matches: {4}", 
                new Object[]{user.getNick(), newNick, isNewNickRegistered, isAuthed, isAuthenticatedForThisNick});
        
        // If authenticated for this specific nick, remove from enforcement list
        if (isAuthenticatedForThisNick) {
            if (unauthenticatedUsers.remove(userNumeric) != null) {
                LOG.log(Level.INFO, "User {0} removed from enforcement list (authenticated for nick {1})", 
                        new Object[]{userNumeric, newNick});
            }
            return;
        }
        
        // User is changing to a registered nick without matching authentication
        LOG.log(Level.INFO, "User {0} changed to registered nick {1} without matching authentication", 
                new Object[]{userNumeric, newNick});
        
        // Check if user is already in enforcement list
        boolean wasAlreadyInEnforcement = unauthenticatedUsers.containsKey(userNumeric);
        
        // Add to unauthenticated users list with current timestamp
        // Use putIfAbsent to avoid resetting the timer if user is already in the list
        unauthenticatedUsers.putIfAbsent(userNumeric, time());
        
        // Send warning notice only if not already in enforcement list
        // This prevents spam when user changes nicks multiple times
        if (!wasAlreadyInEnforcement) {
            String notice = "O";
            if (!socketThread.isNotice(user.getAccount())) {
                notice = "P";
            }
            
            // Check if user is authenticated with a different account
            if (isAuthed) {
                String accountName = user.getAccount();
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "*** This nickname is registered and protected ***");
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "You are authenticated as '%s', but '%s' belongs to a different account.", accountName, newNick);
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "Please change your nickname now using: /nick %s", user.getNick());
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "You have %d seconds to comply, or you will be disconnected.", gracePeriod);
            } else {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "*** This nickname is registered and protected ***");
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "If this is your nickname, please authenticate now using:");
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "    /msg %s AUTH <username> <password>", authService);
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "If this is not your nickname, please change it now using: /nick newnick");
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "You have %d seconds to comply, or you will be disconnected.", gracePeriod);
            }
        }
    }
    
    /**
     * Handles user quit
     */
    private void handleQuit(String[] elem) {
        // elem[0] = user numeric
        String userNumeric = elem[0];
        unauthenticatedUsers.remove(userNumeric);
        
        // Check if this was a dummy nick
        cleanupDummyIfExists(userNumeric);
    }
    
    /**
     * Handles user kill (D command)
     * Format: AB D <target> :<reason>
     */
    private void handleKill(String[] elem) {
        // elem[0] = source (who killed)
        // elem[2] = target numeric (who was killed)
        if (elem.length < 3) {
            return;
        }
        
        String targetNumeric = elem[2];
        unauthenticatedUsers.remove(targetNumeric);
        
        // Check if this was a dummy nick that got killed
        cleanupDummyIfExists(targetNumeric);
    }
    
    /**
     * Cleans up dummy nick tracking if the given numeric is a dummy
     */
    private void cleanupDummyIfExists(String userNumeric) {
        // Find if this numeric is a dummy
        for (var entry : dummyNicks.entrySet()) {
            if (entry.getValue().equals(userNumeric)) {
                String nick = entry.getKey();
                dummyNicks.remove(nick);
                LOG.log(Level.INFO, "Cleaned up dummy nick {0} (numeric: {1}) - user was removed from network", 
                        new Object[]{nick, userNumeric});
                break;
            }
        }
    }
    
    /**
     * Handles private messages sent to NickServ
     */
    private void handlePrivateMessage(String[] elem) {
        // elem[0] = sender numeric
        // elem[2] = target (NickServ)
        // elem[3...] = message
        
        String senderNumeric = elem[0];
        
        StringBuilder sb = new StringBuilder();
        for (int i = 3; i < elem.length; i++) {
            sb.append(elem[i]).append(" ");
        }
        
        String message = sb.toString().trim();
        if (message.startsWith(":")) {
            message = message.substring(1);
        }
        
        String[] args = message.split(" ");
        
        Users user = socketThread.getUsers().get(senderNumeric);
        if (user == null) {
            return;
        }
        
        String notice = "O";
        if (!socketThread.isNotice(user.getAccount())) {
            notice = "P";
        }
        
        // Handle commands
        if (args[0].equalsIgnoreCase("HELP")) {
            sendHelp(senderNumeric, notice);
        } else if (args[0].equalsIgnoreCase("INFO")) {
            if (args.length < 2) {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                        "Usage: INFO <nickname>");
            } else {
                sendNickInfo(senderNumeric, notice, args[1]);
            }
        } else if (args[0].equalsIgnoreCase("RESERVE")) {
            if (args.length < 2) {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                        "Usage: RESERVE <nickname>");
            } else {
                handleReserveCommand(senderNumeric, notice, user, args[1]);
            }
        } else if (args[0].equalsIgnoreCase("UNRESERVE")) {
            if (args.length < 2) {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                        "Usage: UNRESERVE <nickname>");
            } else {
                handleUnreserveCommand(senderNumeric, notice, user, args[1]);
            }
        } else if (args[0].equalsIgnoreCase("LISTRES") || args[0].equalsIgnoreCase("LISTRESERVE")) {
            handleListReserveCommand(senderNumeric, notice, user);
        } else if (args[0].equalsIgnoreCase("GHOST")) {
            if (args.length < 2) {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                        "Usage: GHOST <nickname>");
            } else {
                handleGhostCommand(senderNumeric, notice, user, args[1]);
            }
        } else if (args[0].equalsIgnoreCase("RELEASE")) {
            if (args.length < 2) {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                        "Usage: RELEASE <nickname>");
            } else {
                handleReleaseCommand(senderNumeric, notice, user, args[1]);
            }
        } else if (args[0].equalsIgnoreCase("RECOVER") || args[0].equalsIgnoreCase("REGAIN")) {
            if (args.length < 2) {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                        "Usage: RECOVER <nickname>");
            } else {
                handleRecoverCommand(senderNumeric, notice, user, args[1]);
            }
        } else if (args[0].equalsIgnoreCase("STATUS")) {
            if (args.length < 2) {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                        "Usage: STATUS <nickname>");
            } else {
                handleStatusCommand(senderNumeric, notice, user, args[1]);
            }
        } else if (args[0].equalsIgnoreCase("VERSION")) {
            Software.BuildInfo buildInfo = Software.getBuildInfo();
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "NickServ v%s by %s", buildInfo.getFullVersion(), VENDOR);
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "Based on JServ v%s", buildInfo.getFullVersion());
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "Created by %s", AUTHOR);
        } else if (args[0].equalsIgnoreCase("SHOWCOMMANDS")) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    Messages.get("QM_COMMANDLIST"));
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "   HELP             Shows help information.");
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "   INFO             Shows information about a registered nickname.");
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "   RESERVE          Reserve an additional nickname for your account.");
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "   UNRESERVE        Remove a nickname reservation.");
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "   LISTRESERVE      List all your reserved nicknames.");
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "   GHOST            Disconnect a ghosted session using your nickname.");
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "   RELEASE          Release a protected nickname from services.");
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "   RECOVER          Recover your nickname (GHOST + nick change).");
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "   STATUS           Check if a nickname is online and authenticated.");
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "   SHOWCOMMANDS     Shows this list.");
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "   VERSION          Print version info.");
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    Messages.get("QM_ENDOFLIST"));
        } else {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    Messages.get("QM_UNKNOWNCMD", args[0].toUpperCase()));
        }
    }
    
    /**
     * Sends help information
     */
    private void sendHelp(String userNumeric, String notice) {
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "***** %s Help *****", nick);
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "%s protects registered nicknames from unauthorized use.", nick);
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "When you connect using a registered nickname, you must authenticate");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "within %d seconds. If you don't, you will be disconnected from the network.", gracePeriod);
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "To authenticate, use: /msg %s AUTH <username> <password>", authService);
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "Available commands:");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "   INFO <nick>      - Show information about a registered nickname");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "   STATUS <nick>    - Check if a nickname is online and authenticated");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "   GHOST <nick>     - Disconnect a ghosted session using your nickname");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "   RELEASE <nick>   - Release a protected nickname from services");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "   RECOVER <nick>   - Recover your nickname (GHOST + nick change)");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "   RESERVE <nick>   - Reserve an additional nickname for your account");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "   UNRESERVE <nick> - Remove a nickname reservation");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "   LISTRESERVE      - List all your reserved nicknames");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "   SHOWCOMMANDS     - Show all available commands");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "   VERSION          - Show version information");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "");
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "For more information on a specific command, use: /msg %s HELP <command>", nick);
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                "***** End of Help *****");
    }
    
    /**
     * Sends information about a registered nickname
     */
    private void sendNickInfo(String userNumeric, String notice, String targetNick) {
        if (isNickRegistered(targetNick)) {
            // Get registration timestamp
            long created = jserv.getDb().getLongData("created", targetNick);
            
            // Determine if this is a reserved nick or main account
            String account = jserv.getDb().getReservedAccount(targetNick);
            boolean isReservedNick = (account != null);
            if (!isReservedNick) {
                // targetNick is the main account itself
                account = targetNick;
            }
            
            // Header
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "***** Nickname Information *****");
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "Nickname   : %s", targetNick);
            
            // Account information
            if (isReservedNick) {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "Account    : %s", account);
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "Type       : Reserved nickname");
            } else {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "Account    : %s (main account)", account);
            }
            
            // Status
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "Status     : Registered");
            
            // Registration date
            if (created > 0) {
                java.time.Instant instant = java.time.Instant.ofEpochSecond(created);
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                        .ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(java.time.ZoneId.systemDefault());
                String formattedDate = formatter.format(instant);
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "Registered : %s (timestamp: %d)", formattedDate, created);
            } else {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "Registered : Unknown");
            }
            
            // Current authentication status
            String userAccount = socketThread.getUserAccount(targetNick);
            if (userAccount != null && socketThread.isAuthed(userAccount)) {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "Currently  : Authenticated");
            } else {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "Currently  : Not authenticated");
            }
            
            // List all reserved nicknames for this account
            var reservedNicks = jserv.getDb().getReservedNicks(account);
            if (reservedNicks != null && !reservedNicks.isEmpty()) {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "Reserved Nicks:");
                for (String reservedNick : reservedNicks) {
                    socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                            "   - %s", reservedNick);
                }
            }
            
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "***** End of Info *****");
        } else {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "The nickname '%s' is not registered.", targetNick);
        }
    }
    
    /**
     * Handles RESERVE command - reserves an additional nickname for the account
     */
    private void handleReserveCommand(String userNumeric, String notice, Users user, String nickToReserve) {
        String account = user.getAccount();
        
        // Check if user is authenticated
        if (!socketThread.isAuthed(account)) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "You must be authenticated to reserve nicknames.");
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "Please authenticate first with Q/AuthServ.");
            return;
        }
        
        // Validate nickname format (basic validation)
        if (!isValidNickname(nickToReserve)) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "Invalid nickname format: %s", nickToReserve);
            return;
        }
        
        // Check if nickname is the user's main account
        if (nickToReserve.equalsIgnoreCase(account)) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "You cannot reserve your main account nickname '%s'.", account);
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "Your main account is automatically protected.");
            return;
        }
        
        // Check if nickname is already reserved
        if (jserv.getDb().isNickReserved(nickToReserve)) {
            String reservedBy = jserv.getDb().getReservedAccount(nickToReserve);
            if (reservedBy != null && reservedBy.equalsIgnoreCase(account)) {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "You have already reserved the nickname '%s'.", nickToReserve);
            } else {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "The nickname '%s' is already reserved by another account.", nickToReserve);
            }
            return;
        }
        
        // Check if nickname is registered as main account
        if (jserv.getDb().isRegistered(nickToReserve)) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "The nickname '%s' is already registered as a main account.", nickToReserve);
            return;
        }
        
        // Check reservation limit (max 5 additional nicknames per account)
        int currentReservations = jserv.getDb().countReservedNicks(account);
        if (currentReservations >= 5) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "You have reached the maximum of 5 reserved nicknames.");
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "Please unreserve a nickname before reserving a new one.");
            return;
        }
        
        // Reserve the nickname
        boolean success = jserv.getDb().reserveNick(nickToReserve, account);
        
        if (success) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "Nickname '%s' has been successfully reserved for your account.", nickToReserve);
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "This nickname is now protected and requires authentication.");
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "You can use it by authenticating with your account credentials.");
            
            LOG.log(Level.INFO, "Nickname {0} reserved by account {1}", 
                    new Object[]{nickToReserve, account});
        } else {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "Failed to reserve nickname. Please try again later.");
        }
    }
    
    /**
     * Handles UNRESERVE command - removes a nickname reservation
     */
    private void handleUnreserveCommand(String userNumeric, String notice, Users user, String nickToUnreserve) {
        String account = user.getAccount();
        
        // Check if user is authenticated
        if (!socketThread.isAuthed(account)) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "You must be authenticated to unreserve nicknames.");
            return;
        }
        
        // Check if nickname is reserved
        if (!jserv.getDb().isNickReserved(nickToUnreserve)) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "The nickname '%s' is not reserved.", nickToUnreserve);
            return;
        }
        
        // Get the account that owns this reservation
        String reservedAccount = jserv.getDb().getReservedAccount(nickToUnreserve);
        
        // Check if user owns this reservation or is an oper
        if (!account.equalsIgnoreCase(reservedAccount) && !user.isOper()) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "You cannot unreserve nickname '%s' - you don't own it.", nickToUnreserve);
            return;
        }
        
        // Unreserve the nickname
        boolean success = jserv.getDb().unreserveNick(nickToUnreserve);
        
        if (success) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "Nickname '%s' has been successfully unreserved.", nickToUnreserve);
            
            LOG.log(Level.INFO, "Nickname {0} unreserved by {1}", 
                    new Object[]{nickToUnreserve, account});
        } else {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "Failed to unreserve nickname. Please try again later.");
        }
    }
    
    /**
     * Handles LISTRESERVE command - lists all reserved nicknames for the user's account
     */
    private void handleListReserveCommand(String userNumeric, String notice, Users user) {
        String account = user.getAccount();
        
        // Check if user is authenticated
        if (!socketThread.isAuthed(account)) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "You must be authenticated to view your reserved nicknames.");
            return;
        }
        
        // Get reserved nicknames
        var reservedNicks = jserv.getDb().getReservedNicks(account);
        
        if (reservedNicks.isEmpty()) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "You have no reserved nicknames.");
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "Use 'RESERVE <nickname>' to reserve additional nicknames.");
        } else {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "Reserved nicknames for account '%s': (%d/5)", account, reservedNicks.size());
            
            for (String nick : reservedNicks) {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                        "   - %s", nick);
            }
            
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, userNumeric,
                    "End of reserved nicknames list.");
        }
    }
    
    /**
     * Handles GHOST command - disconnects a user using your nickname
     */
    private void handleGhostCommand(String senderNumeric, String notice, Users user, String targetNick) {
        String account = user.getAccount();
        
        // Check if user is authenticated
        if (!socketThread.isAuthed(account)) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "You must be authenticated to use GHOST.");
            return;
        }
        
        // Check if target nick belongs to this account
        if (!account.equalsIgnoreCase(targetNick) && !isNickOwnedByAccount(targetNick, account)) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "You cannot ghost nickname '%s' - you don't own it.", targetNick);
            return;
        }
        
        // Find the user with target nickname
        String targetNumeric = null;
        Users targetUser = null;
        
        for (var entry : socketThread.getUsers().entrySet()) {
            Users u = entry.getValue();
            if (u.getNick().equalsIgnoreCase(targetNick)) {
                targetNumeric = entry.getKey();
                targetUser = u;
                break;
            }
        }
        
        if (targetNumeric == null) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "Nickname '%s' is not currently in use.", targetNick);
            return;
        }
        
        // Don't ghost yourself
        if (targetNumeric.equals(senderNumeric)) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "You cannot ghost yourself. Use /QUIT if you want to disconnect.");
            return;
        }
        
        // Check if target is authenticated as same account (to prevent ghosting legitimate reconnect)
        if (targetUser != null && socketThread.isAuthed(targetUser.getAccount())) {
            String targetAccount = targetUser.getAccount();
            if (targetAccount.equalsIgnoreCase(account)) {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                        "Warning: '%s' is authenticated as your account. This might be your other connection.", targetNick);
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                        "If you want to proceed anyway, use RECOVER instead.");
                return;
            }
        }
        
        // Kill the target user
        socketThread.sendText("%s D %s %d :GHOST command used by %s", 
                numeric, targetNumeric, time(), account);
        
        // Remove from tracking
        unauthenticatedUsers.remove(targetNumeric);
        
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                "Nickname '%s' has been disconnected.", targetNick);
        
        LOG.log(Level.INFO, "User {0} ({1}) used GHOST on {2} ({3})", 
                new Object[]{user.getNick(), account, targetNick, targetNumeric});
    }
    
    /**
     * Handles RELEASE command - releases a dummy nick
     */
    private void handleReleaseCommand(String senderNumeric, String notice, Users user, String targetNick) {
        String account = user.getAccount();
        
        // Check if user is authenticated
        if (!socketThread.isAuthed(account)) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "You must be authenticated to use RELEASE.");
            return;
        }
        
        // Check if target nick belongs to this account
        if (!account.equalsIgnoreCase(targetNick) && !isNickOwnedByAccount(targetNick, account)) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "You cannot release nickname '%s' - you don't own it.", targetNick);
            return;
        }
        
        // Check if it's a dummy nick
        if (!dummyNicks.containsKey(targetNick)) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "Nickname '%s' is not held by services.", targetNick);
            return;
        }
        
        String dummyNumeric = dummyNicks.get(targetNick);
        
        // Send QUIT command for the dummy
        sendText("%s Q :Released by %s", dummyNumeric, account);
        
        // Remove dummy from local Users map
        jserv.getSocketThread().getUsers().remove(dummyNumeric);
        
        // Remove from tracking
        dummyNicks.remove(targetNick);
        
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                "Nickname '%s' has been released.", targetNick);
        
        LOG.log(Level.INFO, "User {0} ({1}) released dummy nick {2}", 
                new Object[]{user.getNick(), account, targetNick});
    }
    
    /**
     * Handles RECOVER command - GHOSTs the nick and changes to it
     */
    private void handleRecoverCommand(String senderNumeric, String notice, Users user, String targetNick) {
        String account = user.getAccount();
        
        // Check if user is authenticated
        if (!socketThread.isAuthed(account)) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "You must be authenticated to use RECOVER.");
            return;
        }
        
        // Check if target nick belongs to this account
        if (!account.equalsIgnoreCase(targetNick) && !isNickOwnedByAccount(targetNick, account)) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "You cannot recover nickname '%s' - you don't own it.", targetNick);
            return;
        }
        
        // Check if user already has this nick
        if (user.getNick().equalsIgnoreCase(targetNick)) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "You are already using nickname '%s'.", targetNick);
            return;
        }
        
        // Find if someone is using this nick
        String targetNumeric = null;
        boolean isDummy = dummyNicks.containsKey(targetNick);
        
        for (var entry : socketThread.getUsers().entrySet()) {
            Users u = entry.getValue();
            if (u.getNick().equalsIgnoreCase(targetNick)) {
                targetNumeric = entry.getKey();
                break;
            }
        }
        
        if (targetNumeric != null && !targetNumeric.equals(senderNumeric)) {
            // Someone is using the nick - kill them first
            if (isDummy) {
                // It's a dummy - send QUIT first
                sendText("%s Q :Released for %s (RECOVER)", targetNumeric, account);
                
                // Remove dummy from local Users map immediately
                jserv.getSocketThread().getUsers().remove(targetNumeric);
                dummyNicks.remove(targetNick);
                
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                        "Services released nickname '%s'.", targetNick);
                
                // Wait longer before sending nick change to ensure QUIT is fully processed
                Timer delayTimer = new Timer("NickChange-RECOVER-" + targetNick, false);
                final String finalSenderNumeric = senderNumeric;
                final String finalNotice = notice;
                delayTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        // Change user's nick (P10 format: <numeric> N <newnick> <timestamp>)
                        sendText("%s N %s %d", finalSenderNumeric, targetNick, time());
                        
                        socketThread.sendNotice(numeric, getNumericSuffix(), finalNotice, finalSenderNumeric,
                                "You have recovered nickname '%s'.", targetNick);
                        
                        LOG.log(Level.INFO, "User {0} ({1}) recovered nickname {2}", 
                                new Object[]{user.getNick(), account, targetNick});
                    }
                }, 500); // 500ms delay to ensure QUIT is fully processed and prevent collision
                
                return;
            } else {
                // It's a real user - ghost them
                sendText("%s D %s %d :RECOVER command used by %s", 
                        numeric, targetNumeric, time(), account);
                
                unauthenticatedUsers.remove(targetNumeric);
                
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                        "Ghosted user on nickname '%s'.", targetNick);
            }
        } else if (isDummy) {
            // Only a dummy, release it
            String dummyNumeric = dummyNicks.get(targetNick);
            sendText("%s Q :Released for %s (RECOVER)", dummyNumeric, account);
            
            // Remove dummy from local Users map immediately
            jserv.getSocketThread().getUsers().remove(dummyNumeric);
            dummyNicks.remove(targetNick);
            
            // Wait longer before sending nick change to prevent collision
            Timer delayTimer = new Timer("NickChange-RECOVER-" + targetNick, false);
            final String finalSenderNumeric = senderNumeric;
            final String finalNotice = notice;
            delayTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    // Change user's nick (P10 format: <numeric> N <newnick> <timestamp>)
                    sendText("%s N %s %d", finalSenderNumeric, targetNick, time());
                    
                    socketThread.sendNotice(numeric, getNumericSuffix(), finalNotice, finalSenderNumeric,
                            "You have recovered nickname '%s'.", targetNick);
                    
                    LOG.log(Level.INFO, "User {0} ({1}) recovered nickname {2}", 
                            new Object[]{user.getNick(), account, targetNick});
                }
            }, 500); // 500ms delay to prevent collision
            
            return;
        }
        
        // Change user's nick immediately if no one was using it (P10 format: <numeric> N <newnick> <timestamp>)
        sendText("%s N %s %d", senderNumeric, targetNick, time());
        
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                "You have recovered nickname '%s'.", targetNick);
        
        LOG.log(Level.INFO, "User {0} ({1}) recovered nickname {2}", 
                new Object[]{user.getNick(), account, targetNick});
    }
    
    /**
     * Handles STATUS command - checks if a nickname is online and authenticated
     */
    private void handleStatusCommand(String senderNumeric, String notice, Users user, String targetNick) {
        // Find the user with target nickname
        Users targetUser = null;
        String targetNumeric = null;
        
        for (var entry : socketThread.getUsers().entrySet()) {
            Users u = entry.getValue();
            if (u.getNick().equalsIgnoreCase(targetNick)) {
                targetUser = u;
                targetNumeric = entry.getKey();
                break;
            }
        }
        
        if (targetUser == null) {
            // Check if it's a registered nick
            if (isNickRegistered(targetNick)) {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                        "%s is not currently online.", targetNick);
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                        "%s is a registered nickname.", targetNick);
            } else {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                        "%s is not a registered nickname.", targetNick);
            }
            return;
        }
        
        // User is online
        socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                "%s is currently online.", targetNick);
        
        // Check authentication status
        String account = targetUser.getAccount();
        if (socketThread.isAuthed(account)) {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "%s is authenticated as %s.", targetNick, account);
            
            // Check if using own nick
            if (account.equalsIgnoreCase(targetNick) || isNickOwnedByAccount(targetNick, account)) {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                        "%s is using their own nickname.", targetNick);
            } else {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                        "%s is authenticated but using a different nickname.", targetNick);
            }
        } else {
            socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                    "%s is not authenticated.", targetNick);
            
            // Check if nick is registered
            if (isNickRegistered(targetNick)) {
                socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                        "%s is a registered nickname.", targetNick);
                
                // Check if in enforcement list
                if (unauthenticatedUsers.containsKey(targetNumeric)) {
                    long connectTime = unauthenticatedUsers.get(targetNumeric);
                    long elapsed = time() - connectTime;
                    long remaining = gracePeriod - elapsed;
                    
                    if (remaining > 0) {
                        socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                                "%s has %d seconds to authenticate.", targetNick, remaining);
                    } else {
                        socketThread.sendNotice(numeric, getNumericSuffix(), notice, senderNumeric,
                                "%s will be disconnected shortly.", targetNick);
                    }
                }
            }
        }
    }
    
    /**
     * Validates nickname format (basic validation)
     */
    private boolean isValidNickname(String nick) {
        if (nick == null || nick.isEmpty()) {
            return false;
        }
        
        // Check length (typically 1-30 characters)
        if (nick.isEmpty() || nick.length() > 30) {
            return false;
        }
        
        // Check if starts with letter
        char first = nick.charAt(0);
        if (!Character.isLetter(first)) {
            return false;
        }
        
        // Check for valid characters (letters, numbers, some special chars)
        for (char c : nick.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '_' && c != '[' && c != ']' && c != '{' && c != '}' && c != '\\' && c != '`' && c != '^' && c != '|') {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Loads the NickServ nickname from configuration file
     */
    private String loadNickFromConfig() {
        try {
            var config = jserv.getConfig().getNickFile();
            if (config != null) {
                String configNick = config.getProperty("nick", "NickServ");
                LOG.log(Level.INFO, "Loaded NickServ nickname from configuration: {0}", configNick);
                return configNick;
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error loading NickServ nickname from configuration, using default", e);
        }
        return "NickServ";
    }
    
    /**
     * Loads G-Line configuration from config file
     */
    private void loadGLineConfiguration() {
        try {
            var config = jserv.getConfig().getNickFile();
            if (config != null) {
                // Load grace period (in seconds)
                String gracePeriodStr = config.getProperty("grace_period", String.valueOf(DEFAULT_GRACE_PERIOD));
                this.gracePeriod = Integer.parseInt(gracePeriodStr);
                
                // Load check interval (in milliseconds)
                String checkIntervalStr = config.getProperty("check_interval", String.valueOf(DEFAULT_CHECK_INTERVAL));
                this.checkInterval = Integer.parseInt(checkIntervalStr);
                
                // Load G-Line enabled flag
                String glineEnabledStr = config.getProperty("gline_enabled", "true");
                this.glineEnabled = Boolean.parseBoolean(glineEnabledStr);
                
                // Load G-Line attempts threshold
                String glineAttemptsStr = config.getProperty("gline_attempts", String.valueOf(DEFAULT_GLINE_ATTEMPTS));
                this.glineAttempts = Integer.parseInt(glineAttemptsStr);
                
                // Load G-Line duration (in seconds)
                String glineDurationStr = config.getProperty("gline_duration", String.valueOf(DEFAULT_GLINE_DURATION));
                this.glineDuration = Integer.parseInt(glineDurationStr);
                
                // Load auth service address
                this.authService = config.getProperty("auth_service", DEFAULT_AUTH_SERVICE);
                
                LOG.log(Level.INFO, "NickServ configuration loaded: grace_period={0}s, check_interval={1}ms, gline_enabled={2}, gline_attempts={3}, gline_duration={4}s, auth_service={5}", 
                        new Object[]{gracePeriod, checkInterval, glineEnabled, glineAttempts, glineDuration, authService});
            } else {
                LOG.log(Level.WARNING, "NickServ configuration not found, using defaults");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error loading NickServ configuration, using defaults", e);
        }
    }
    
    /**
     * Ensures the NickServ database table exists
     */
    private void ensureNickServTableExists() {
        jserv.getDb().ensureFailedAttemptsTableExists();
        jserv.getDb().ensureNickReservationTableExists();
        LOG.log(Level.INFO, "NickServ database tables verified/created");
    }
    
    /**
     * Tracks a failed authentication attempt for a user host
     */
    private void trackFailedAttempt(String userHost) {
        try {
            long currentTime = time();
            jserv.getDb().trackFailedAttempt(userHost, currentTime);
            LOG.log(Level.INFO, "Tracked failed attempt for {0}", userHost);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, () -> "Error tracking failed attempt for " + userHost);
            LOG.log(Level.SEVERE, "Exception details", e);
        }
    }
    
    /**
     * Checks if a user host should have a dummy nick created based on configured attempts threshold
     */
    private boolean shouldCreateDummy(String userHost) {
        // Check if dummy creation is enabled
        if (!glineEnabled) {
            return false;
        }
        
        try {
            long[] result = jserv.getDb().getFailedAttempts(userHost);
            if (result != null) {
                int attempts = (int) result[0];
                long glinedUntil = result[1];
                long currentTime = time();
                
                // Check if already has dummy active
                if (glinedUntil > currentTime) {
                    LOG.log(Level.FINE, "User {0} already has dummy active until {1}", 
                            new Object[]{userHost, glinedUntil});
                    return false;
                }
                
                // Create dummy after configured number of attempts
                if (attempts >= glineAttempts) {
                    LOG.log(Level.INFO, "User {0} has {1} failed attempts (threshold: {2}), creating dummy nick", 
                            new Object[]{userHost, attempts, glineAttempts});
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, () -> "Error checking dummy creation status for " + userHost);
            LOG.log(Level.SEVERE, "Exception details", e);
        }
        return false;
    }
    
    /**
     * Creates a dummy nick to block the protected nickname with retry logic
     * Instead of G-Line, we create a placeholder user that holds the nick
     */
    private void createDummyNick(String nick, String userHost) {
        createDummyNickWithRetry(nick, userHost, 0);
    }
    
    /**
     * Creates a dummy nick with retry logic if nick is still in use
     */
    private void createDummyNickWithRetry(String nick, String userHost, int retryCount) {
        try {
            // Check if dummy already exists for this nick
            if (dummyNicks.containsKey(nick)) {
                LOG.log(Level.FINE, "Dummy nick {0} already exists", nick);
                return;
            }
            
            // Check if nick is currently in use by a real user
            Users currentUser = null;
            String currentUserNumeric = null;
            for (var entry : socketThread.getUsers().entrySet()) {
                if (entry.getValue().getNick().equalsIgnoreCase(nick)) {
                    currentUser = entry.getValue();
                    currentUserNumeric = entry.getKey();
                    break;
                }
            }
            
            if (currentUser != null) {
                // Nick is still in use - check if we should retry
                if (retryCount < 3) {
                    LOG.log(Level.INFO, "Cannot create dummy for {0} - nick is currently in use by {1}, retry {2}/3", 
                            new Object[]{nick, currentUserNumeric, retryCount + 1});
                    
                    // Check if current user is authenticated for this nick
                    boolean isAuthenticated = false;
                    if (socketThread.isAuthed(currentUser.getAccount())) {
                        String accountName = currentUser.getAccount();
                        isAuthenticated = accountName.equalsIgnoreCase(nick) || 
                                        isNickOwnedByAccount(nick, accountName);
                    }
                    
                    if (!isAuthenticated) {
                        // User is still not authenticated - kill them again
                        LOG.log(Level.INFO, "Killing unauthenticated user {0} again (retry attempt)", currentUserNumeric);
                        
                        // Send KILL command
                        socketThread.sendText("%s D %s %d :This nickname is protected by services", 
                                numeric, currentUserNumeric, time());
                        
                        // Remove user from local map immediately to prevent issues
                        socketThread.getUsers().remove(currentUserNumeric);
                        unauthenticatedUsers.remove(currentUserNumeric);
                        
                        // Retry after longer delay to ensure kill is processed
                        Timer retryTimer = new Timer("DummyRetry-" + nick + "-" + retryCount, false);
                        final int nextRetry = retryCount + 1;
                        retryTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                createDummyNickWithRetry(nick, userHost, nextRetry);
                            }
                        }, 2000); // 2 second delay for retry (increased from 1.5s)
                        return;
                    } else {
                        // User is now authenticated - don't create dummy
                        LOG.log(Level.INFO, "User {0} is now authenticated for nick {1}, not creating dummy", 
                                new Object[]{currentUserNumeric, nick});
                        return;
                    }
                } else {
                    // Max retries reached - do final kill and force longer dummy creation
                    LOG.log(Level.WARNING, "Cannot create dummy for {0} after {1} retries - forcing final kill", 
                            new Object[]{nick, retryCount});
                    
                    // Final kill attempt
                    socketThread.sendText("%s D %s %d :Repeated violation of nickname protection - disconnected", 
                            numeric, currentUserNumeric, time());
                    
                    // Remove from local map
                    socketThread.getUsers().remove(currentUserNumeric);
                    unauthenticatedUsers.remove(currentUserNumeric);
                    
                    // Try one more time after a longer delay
                    Timer finalTimer = new Timer("DummyFinal-" + nick, false);
                    finalTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            // Final attempt - if still occupied, log error
                            boolean stillOccupied = false;
                            for (Users u : socketThread.getUsers().values()) {
                                if (u.getNick().equalsIgnoreCase(nick)) {
                                    stillOccupied = true;
                                    LOG.log(Level.SEVERE, "Nickname {0} still occupied after final kill - possible server issue", nick);
                                    break;
                                }
                            }
                            
                            if (!stillOccupied) {
                                // Try to create dummy one last time
                                createDummyNickFinal(nick, userHost);
                            }
                        }
                    }, 3000); // 3 second delay for final attempt
                    return;
                }
            }
            
            // Nick is free - proceed with dummy creation
            createDummyNickFinal(nick, userHost);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, () -> "Error creating dummy nick for " + nick);
            LOG.log(Level.SEVERE, "Exception details", e);
        }
    }
    
    /**
     * Final dummy creation without retry logic
     */
    private void createDummyNickFinal(String nick, String userHost) {
        try {
            // Double-check nick is not in use
            for (Users user : socketThread.getUsers().values()) {
                if (user.getNick().equalsIgnoreCase(nick)) {
                    LOG.log(Level.WARNING, "Cannot create dummy for {0} - nick is still in use", nick);
                    return;
                }
            }
            
            // Find an available numeric (check if numeric is already in use)
            String dummyNumeric;
            int attempts = 0;
            do {
                dummyCounter++;
                dummyNumeric = numeric + String.format("D%02d", dummyCounter % 100);
                attempts++;
                
                // Safety check: avoid infinite loop
                if (attempts > 100) {
                    LOG.log(Level.SEVERE, "Could not find available dummy numeric after 100 attempts");
                    return;
                }
            } while (jserv.getSocketThread().getUsers().containsKey(dummyNumeric));
            
            long currentTime = time();
            
            // Update database to mark as "dummy blocked"
            jserv.getDb().setGLineExpiration(userHost, currentTime + glineDuration);
            
            // Register dummy user in local Users map FIRST (before sending to network)
            Users dummyUser = new Users(dummyNumeric, nick, "", "services.protected");
            dummyUser.setService(true);
            jserv.getSocketThread().getUsers().put(dummyNumeric, dummyUser);
            
            // Track the dummy nick with its numeric
            dummyNicks.put(nick, dummyNumeric);
            
            // Now create dummy user on IRC network
            // Format: <server> N <nick> <hopcount> <timestamp> <ident> <host> <modes> <base64ip> <numeric> :<realname>
            sendText("%s N %s 1 %d NickProtect services.protected +ik B64AAAAAA %s :This nickname is protected", 
                    numeric, nick, currentTime, dummyNumeric);
            
            LOG.log(Level.INFO, "Created dummy nick {0} (numeric: {1}) to protect nickname (triggered by {2})", 
                    new Object[]{nick, dummyNumeric, userHost});
            
            // Send notice to ops channel (without host information for privacy)
            // Format: <source> P <target> :<message>
            sendText("%s%s P %s :Created dummy nick %s to protect nickname (%d+ failed authentication attempts)", 
                    numeric, getNumericSuffix(), OPS_CHANNEL, nick, glineAttempts);
            
        } catch (Exception e) {
            LOG.log(Level.SEVERE, () -> "Error creating dummy nick for " + nick);
            LOG.log(Level.SEVERE, "Exception details", e);
        }
    }
    
    /**
     * Releases a dummy nick when the legitimate owner authenticates
     */
    /**
     * Releases all dummy nicks that belong to the authenticated account
     * Checks all dummy nicks and releases those owned by this account
     */
    private void releaseAllDummiesForAccount(String accountName) {
        try {
            // Iterate through all dummy nicks and check ownership
            var iterator = dummyNicks.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                String dummyNick = entry.getKey();
                
                // Check if this account owns this nick
                if (accountName.equalsIgnoreCase(dummyNick) || isNickOwnedByAccount(dummyNick, accountName)) {
                    String dummyNumeric = entry.getValue();
                    
                    // Send QUIT command for the dummy
                    sendText("%s Q :Released for authenticated user %s", dummyNumeric, accountName);
                    
                    // Remove dummy from local Users map
                    jserv.getSocketThread().getUsers().remove(dummyNumeric);
                    
                    // Remove from tracking
                    iterator.remove();
                    
                    LOG.log(Level.INFO, "Released dummy nick {0} (numeric: {1}) for authenticated user {2}", 
                            new Object[]{dummyNick, dummyNumeric, accountName});
                    
                    // Send notice to ops channel
                    socketThread.sendNotice(numeric, getNumericSuffix(), "P", OPS_CHANNEL,
                            "Released dummy nick %s for authenticated user %s", dummyNick, accountName);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, () -> "Error releasing dummy nicks for account " + accountName);
            LOG.log(Level.SEVERE, "Exception details", e);
        }
    }
    
    /**
     * Starts the timer that periodically checks for users that need to be killed
     */
    private void startEnforcementTimer() {
        if (enforcementTimer != null) {
            enforcementTimer.cancel();
        }
        
        enforcementTimer = new Timer("NickServ-Enforcement", true);
        enforcementTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                enforceNickProtection();
            }
        }, checkInterval, checkInterval);
        
        LOG.log(Level.INFO, "NickServ enforcement timer started (check interval: {}ms)", checkInterval);
    }
    
    /**
     * Checks all unauthenticated users and kills those that exceeded the grace period
     */
    private void enforceNickProtection() {
        if (!enabled) {
            return;
        }
        
        long currentTime = time();
        
        // Iterate through unauthenticated users
        unauthenticatedUsers.forEach((userNumeric, connectTime) -> {
            long elapsed = currentTime - connectTime;
            
            if (elapsed >= gracePeriod) {
                // Grace period expired, kill the user
                Users user = socketThread.getUsers().get(userNumeric);
                
                if (user != null) {
                    // Skip opers and services (double-check)
                    if (user.isOper() || user.isService()) {
                        unauthenticatedUsers.remove(userNumeric);
                        return;
                    }
                    
                    // Check if user is now authenticated (race condition check)
                    if (socketThread.isAuthed(user.getAccount())) {
                        unauthenticatedUsers.remove(userNumeric);
                        return;
                    }
                    
                    // Check if nick is still registered (could have been unregistered)
                    if (!isNickRegistered(user.getNick())) {
                        unauthenticatedUsers.remove(userNumeric);
                        return;
                    }
                    
                    // Check if user has PROTECT flag - if yes, skip enforcement
                    int userFlags = jserv.getDb().getFlags(user.getNick());
                    if (Userflags.hasFlag(userFlags, Userflags.Flag.PROTECT)) {
                        LOG.log(Level.FINE, "User {0} has PROTECT flag, skipping nick protection enforcement", 
                                userNumeric);
                        unauthenticatedUsers.remove(userNumeric);
                        return;
                    }
                    
                    LOG.log(Level.INFO, "Killing unauthenticated user {0} ({1}) using protected nick {2}", 
                            new Object[]{userNumeric, user.getAccount(), user.getNick()});
                    
                    // Track failed authentication attempt
                    // Format: ident@host (without nick, since user can change nicks)
                    // user.getHost() already contains ident@host from P10 protocol
                    String userHost = user.getHost();
                    String protectedNick = user.getNick();
                    
                    LOG.log(Level.INFO, "UserHost for tracking: {0}", userHost);
                    
                    trackFailedAttempt(userHost);
                    
                    // Send KILL command FIRST (D in P10 protocol)
                    // Format: <source> D <target> <timestamp> :<reason>
                    sendText("%s%s D %s %d : (Nickname is registered and you failed to authenticate within %d seconds)", 
                            numeric, getNumericSuffix(), userNumeric, currentTime, gracePeriod);
                    
                    // Check if we should create a dummy nick AFTER killing the user
                    boolean shouldCreateDummy = shouldCreateDummy(userHost);
                    
                    // Remove from tracking
                    unauthenticatedUsers.remove(userNumeric);
                    
                    // Create dummy nick after a longer delay to ensure user is fully disconnected
                    if (shouldCreateDummy) {
                        Timer delayTimer = new Timer("DummyCreation-" + protectedNick, false);
                        delayTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                createDummyNick(protectedNick, userHost);
                            }
                        }, 1000); // 1000ms delay to prevent collision
                    }
                }
            }
        });
    }
    
    /**
     * Checks if a nickname is registered in the database
     */
    private boolean isNickRegistered(String nick) {
        try {
            // Check if user exists in database
            int userId = jserv.getDb().getUserId(nick);
            if (userId > 0) {
                return true;
            }
            
            // Also check if nick is reserved
            return jserv.getDb().isNickReserved(nick);
        } catch (Exception e) {
            LOG.log(Level.WARNING, () -> "Error checking if nick is registered: " + nick);
            LOG.log(Level.WARNING, "Exception details", e);
            return false;
        }
    }
    
    /**
     * Checks if a nickname is owned by a specific account
     * This includes both main account names and reserved nicknames
     */
    private boolean isNickOwnedByAccount(String nick, String account) {
        if (nick == null || account == null) {
            return false;
        }
        
        // Check if nick matches account name (main account)
        if (nick.equalsIgnoreCase(account)) {
            return true;
        }
        
        // Check if nick is reserved by this account
        String reservedAccount = jserv.getDb().getReservedAccount(nick);
        if (reservedAccount != null && reservedAccount.equalsIgnoreCase(account)) {
            // Update last seen timestamp for reserved nick
            jserv.getDb().updateReservedNickLastSeen(nick);
            return true;
        }
        
        return false;
    }
    
    /**
     * Sends a raw IRC protocol line
     */
    private void sendText(String text, Object... args) {
        if (!enabled) {
            return;
        }
        pw.println(text.formatted(args));
        pw.flush();
        if (jserv.getConfig().getConfigFile().getProperty("debug", "false").equalsIgnoreCase("true")) {
            LOG.log(Level.FINE, "DEBUG sendText: {0}", text.formatted(args));
        }
    }
    
    /**
     * Extracts the user numeric from a P10 N command
     * The numeric is always the element directly before the ":" in the realname part
     */
    private String extractNumericFromNCommand(String[] elem) {
        // Find the element that starts with ":" (realname)
        for (int i = elem.length - 1; i >= 0; i--) {
            if (elem[i].startsWith(":")) {
                // The numeric is the element before this one
                if (i > 0) {
                    return elem[i - 1];
                }
            }
        }
        // Fallback to old method if no ":" found
        return elem[elem.length - 1];
    }
    
    /**
     * Gets current Unix timestamp
     */
    private long time() {
        return System.currentTimeMillis() / 1000;
    }
    
    @Override
    public void shutdown() {
        LOG.log(Level.INFO, "NickServ module shutting down");
        
        // Send QUIT command to properly log out the service
        if (enabled && numeric != null && pw != null) {
            try {
                sendText("%s%s Q :Service shutting down", numeric, getNumericSuffix());
                LOG.log(Level.INFO, "NickServ sent QUIT command");
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to send QUIT command for NickServ", e);
            }
        }
        
        this.enabled = false;
        this.inBurst = true; // Reset for next start
        
        // Cancel enforcement timer
        if (enforcementTimer != null) {
            enforcementTimer.cancel();
            enforcementTimer = null;
        }
        
        // Clear tracking data
        unauthenticatedUsers.clear();
    }
    
    @Override
    public String getModuleName() {
        return "NickServ";
    }
    
    @Override
    public String getNumericSuffix() {
        return numericSuffix;
    }
    
    public void setNumericSuffix(String numericSuffix) {
        this.numericSuffix = numericSuffix;
    }
    
    /**
     * @return the nick
     */
    public String getNick() {
        return nick;
    }

    /**
     * @param nick the nick to set
     */
    public void setNick(String nick) {
        this.nick = nick;
    }

    /**
     * @return the servername
     */
    public String getServername() {
        return servername;
    }

    /**
     * @param servername the servername to set
     */
    public void setServername(String servername) {
        this.servername = servername;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }
    
    @Override
    public void registerBurstChannels(java.util.HashMap<String, Burst> bursts, String serverNumeric) {
        if (!enabled) {
            return;
        }
        // NickServ joins ops channel
        if (!bursts.containsKey(OPS_CHANNEL.toLowerCase())) {
            bursts.put(OPS_CHANNEL.toLowerCase(), new Burst(OPS_CHANNEL));
        }
        bursts.get(OPS_CHANNEL.toLowerCase()).getUsers().add(serverNumeric + getNumericSuffix());
        LOG.log(Level.INFO, "NickServ registered burst channel: {0}", OPS_CHANNEL);
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public void enable() {
        this.enabled = true;
        LOG.log(Level.INFO, "NickServ module enabled");
    }
    
    @Override
    public void disable() {
        this.enabled = false;
        if (enforcementTimer != null) {
            enforcementTimer.cancel();
        }
        LOG.log(Level.INFO, "NickServ module disabled");
    }
    
    @Override
    public String getNumeric() {
        return numeric;
    }
}
