/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;
import java.net.InetAddress;

public final class SocketThread implements Runnable, Software {

    private final long serverStartTime = System.currentTimeMillis() / 1000;

    protected void joinChannel(String channel, String numeric, String service) {
        // Use timestamp from burst if available, otherwise use current time
        long timestamp = time();
        if (getBursts().containsKey(channel.toLowerCase())) {
            Burst burstData = getBursts().get(channel.toLowerCase());
            if (burstData.getTime() > 0) {
                timestamp = burstData.getTime();
            }
        }
        
        if (getChannel().containsKey(channel.toLowerCase())) {
            sendText("%s J %s %d", service, channel, timestamp);
        } else {
            sendText("%s C %s %d", service, channel, timestamp);
        }
        sendText("%s M %s +o %s", numeric, channel, service);
    }

    /**
     * @return the burst
     */
    public boolean isBurst() {
        return burst;
    }

    /**
     * @param burst the burst to set
     */
    public void setBurst(boolean burst) {
        this.burst = burst;
    }

    /**
     * @return the users
     */
    public HashMap<String, Users> getUsers() {
        return users;
    }

    /**
     * @param users the users to set
     */
    public void setUsers(HashMap<String, Users> users) {
        this.users = users;
    }

    /**
     * @return the channel
     */
    public HashMap<String, Channel> getChannel() {
        return channel;
    }

    /**
     * @param channel the channel to set
     */
    public void setChannel(HashMap<String, Channel> channel) {
        this.channel = channel;
    }

    /**
     * @return the module manager
     */
    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    /**
     * @param moduleManager the module manager to set
     */
    public void setModuleManager(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    /**
     * @return the hs (deprecated - use ModuleManager)
     * @deprecated Use getModuleManager().getModule("HostServ") instead
     */
    @Deprecated
    public HostServ getHs() {
        return (HostServ) getModuleManager().getModule("HostServ");
    }

    /**
     * @param hs the hs to set (deprecated - use ModuleManager)
     * @deprecated Modules are managed by ModuleManager
     */
    @Deprecated
    public void setHs(HostServ hs) {
        // No-op - modules are managed by ModuleManager
    }

    /**
     * @return the ss (deprecated - use ModuleManager)
     * @deprecated Use getModuleManager().getModule("SpamScan") instead
     */
    @Deprecated
    public SpamScan getSs() {
        return (SpamScan) getModuleManager().getModule("SpamScan");
    }

    /**
     * @param ss the ss to set (deprecated - use ModuleManager)
     * @deprecated Modules are managed by ModuleManager
     */
    @Deprecated
    public void setSs(SpamScan ss) {
        // No-op - modules are managed by ModuleManager
    }

    /**
     * @return the authed
     */
    public HashMap<String, String> getAuthed() {
        return authed;
    }

    /**
     * @param authed the authed to set
     */
    public void setAuthed(HashMap<String, String> authed) {
        this.authed = authed;
    }

    /**
     * @return the anick
     */
    public String getNick() {
        return nick;
    }

    /**
     * @param nick the anick to set
     */
    public void setNick(String nick) {
        this.nick = nick;
    }

    /**
     * @return the aidentd
     */
    public String getIdentd() {
        return identd;
    }

    /**
     * @param identd the aidentd to set
     */
    public void setIdentd(String identd) {
        this.identd = identd;
    }

    /**
     * @return the aservername
     */
    public String getServername() {
        return servername;
    }

    /**
     * @param servername the aservername to set
     */
    public void setServername(String servername) {
        this.servername = servername;
    }

    /**
     * @return the adescription
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the adescription to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the ip
     */
    public byte[] getIp() {
        return ip;
    }

    /**
     * @param ip the ip to set
     */
    public void setIp(byte[] ip) {
        this.ip = ip;
    }

    private final Thread thread;
    private JServ mi;
    private Socket socket;
    private PrintWriter pw;
    private BufferedReader br;
    private boolean runs;
    private boolean burst;
    private String serverNumeric;
    private String numeric;
    private String nick;
    private String identd;
    private String servername;
    private String description;
    private HashMap<String, String> authed;
    private HashMap<String, Users> users;
    private HashMap<String, Channel> channel;
    private HashMap<String, Burst> bursts;
    private ModuleManager moduleManager;
    private byte[] ip;
    private boolean reg;

    private static final String P10_B64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789[]";

    private static boolean isProbablyP10Base64Ip(String s) {
        if (s == null) {
            return false;
        }
        int len = s.length();
        if (len != 6 && len != 24) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            if (P10_B64_ALPHABET.indexOf(s.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeIpString(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String s = ip.trim();
        if (isProbablyP10Base64Ip(s)) {
            String decoded = decodeP10Base64Ip(s);
            if (decoded != null && !decoded.isBlank()) {
                return decoded;
            }
        }
        // If it's already an IP address (contains dots or colons), return as-is
        // to avoid expensive DNS lookups on external addresses
        if (s.contains(".") || s.contains(":")) {
            return s;
        }
        // Only try DNS resolution for hostnames
        try {
            return InetAddress.getByName(s).getHostAddress();
        } catch (Exception ignored) {
            return s;
        }
    }

    private static int p10Base64Value(char c) {
        return P10_B64_ALPHABET.indexOf(c);
    }

    private static String decodeP10Base64Ip(String b64) {
        if (b64 == null) {
            return null;
        }
        String s = b64.trim();
        if (s.isEmpty()) {
            return null;
        }

        // P10 base64 IP encoding uses 6 chars for IPv4 and 24 chars for IPv6.
        if (s.length() != 6 && s.length() != 24) {
            return null;
        }

        if (s.length() == 6) {
            long acc = 0;
            for (int i = 0; i < 6; i++) {
                int v = p10Base64Value(s.charAt(i));
                if (v < 0) {
                    return null;
                }
                acc = (acc << 6) | v;
            }
            // 6 * 6 = 36 bits; in practice the useful IPv4 bits are the lower 32 bits.
            // (Padding, if present, is on the MSB side.)
            long v32 = acc & 0xFFFFFFFFL;
            byte[] bytes = new byte[] {
                (byte) ((v32 >> 24) & 0xFF),
                (byte) ((v32 >> 16) & 0xFF),
                (byte) ((v32 >> 8) & 0xFF),
                (byte) (v32 & 0xFF)
            };
            try {
                return InetAddress.getByAddress(bytes).getHostAddress();
            } catch (Exception ignored) {
                return null;
            }
        }

        // IPv6: 24 * 6 = 144 bits; IPv6 is 128 bits with 16 padding bits at the start.
        byte[] out = new byte[16];
        int bitPos = 0;
        int totalBits = 24 * 6;
        int paddingBits = totalBits - 128;
        int usefulBits = 128;

        int outBitIndex = 0;
        int outByte = 0;
        int outBytePos = 0;

        for (int i = 0; i < 24; i++) {
            int v = p10Base64Value(s.charAt(i));
            if (v < 0) {
                return null;
            }
            for (int b = 5; b >= 0; b--) {
                int bit = (v >> b) & 1;
                // Skip padding bits at the start; then consume exactly 128 bits.
                if (bitPos >= paddingBits && (outBytePos < 16)) {
                    outByte = (outByte << 1) | bit;
                    outBitIndex++;
                    if (outBitIndex == 8) {
                        out[outBytePos++] = (byte) (outByte & 0xFF);
                        outBitIndex = 0;
                        outByte = 0;
                        if (outBytePos == 16) {
                            break;
                        }
                    }
                }
                bitPos++;
            }
            if (outBytePos == 16) {
                break;
            }
        }

        if (outBytePos != 16) {
            return null;
        }
        try {
            return InetAddress.getByAddress(out).getHostAddress();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String extractBase64IpFromNCommand(String rawLine) {
        if (rawLine == null) {
            return null;
        }
        int colonPos = rawLine.indexOf(" :");
        if (colonPos > 0) {
            String beforeColon = rawLine.substring(0, colonPos);
            String[] parts = beforeColon.split(" ");
            if (parts.length >= 3) {
                // ... <ip> <numeric>
                return parts[parts.length - 2];
            }
        }

        // Fallback: split the entire line and find the element before the numeric (which is right before ":")
        String[] elem = rawLine.split(" ");
        for (int i = elem.length - 1; i >= 0; i--) {
            if (elem[i].startsWith(":")) {
                // ... <ip> <numeric> :realname
                if (i >= 3) {
                    return elem[i - 2];
                }
                break;
            }
        }

        return null;
    }

    public SocketThread(JServ mi) {
        setMi(mi);
        setUsers(new HashMap<>());
        setChannel(new HashMap<>());
        setAuthed(new HashMap<>());
        // We start in incoming netburst until the uplink sends EB.
        setBurst(true);
        setBursts(new HashMap<>());
        setModuleManager(new ModuleManager(mi, this));
        (thread = new Thread(this)).start();
    }

    protected void handshake(String password, String servername, String description, String numeric) {
        System.out.println("Starting handshake...");
        sendText("PASS :%s", password);
        sendText("SERVER %s 2 %d %d J10 %s]]] +hs6n :%s", servername, time(), time(), numeric, description);
    }

    protected void sendText(String text, Object... args) {
        getPw().println(text.formatted(args));
        getPw().flush();
        if (getMi().getConfig().getConfigFile().getProperty("debug", "false").equalsIgnoreCase("true")) {
            System.out.printf("DEBUG sendText: %s\n", text.formatted(args));
        }
    }

    protected void sendNotice(String numeric, String user, String notice, String nick, String text, Object... args) {
        var data = text.formatted(args).split("\n");
        for (var content : data) {
            sendText("%s%s %s %s :%s", numeric, user, notice, nick, content);
        }
    }

    protected String getUser(String nick) {
        for (var session : getUsers().keySet()) {
            if (getUsers().get(session).getNick().equalsIgnoreCase(nick)) {
                return session;
            }
        }
        return null;
    }

    protected String getUserId(String auth) {
        for (var session : getUsers().values()) {
            if (session.getAccount().equalsIgnoreCase(auth)) {
                return session.getId();
            }
        }
        return null;
    }

    protected String getUserNumeric(String nick) {
        for (var session : getUsers().keySet()) {
            if (getUsers().get(session).getNick().equalsIgnoreCase(nick)) {
                return session;
            }
        }
        return null;
    }

    protected String getUserName2(String nick) {
        return getUsers().get(nick).getNick();
    }

    protected String getUserName(String nick) {
        for (var session : getUsers().values()) {
            if (session.getNick().equalsIgnoreCase(nick)) {
                return session.getId();
            }
        }
        return null;
    }

    protected String getUserAccount(String nick) {
        for (var session : getUsers().values()) {
            if (session.getNick().equalsIgnoreCase(nick)) {
                return session.getAccount();
            }
        }
        return null;
    }

    private long time() {
        return System.currentTimeMillis() / 1000;
    }
    
    /**
     * Extracts the user numeric/token from a P10 N command.
     * The user token is always the last element before the ":" (realname field).
     * 
     * P10 N command format (variable fields depending on modes):
     * <server> N <nick> <hopcount> <timestamp> <ident> <host> <modes> [opername] [account:ts:ts] [hiddenhost] <base64ip> <numeric> :<realname>
     * 
     * Examples:
     * Simple: AB N WarPigs 1 1766478053 warpigs localhost +i B]AAAB ABAAA :realname
     * With account: AA N DevPigs 1 1766923017 warpigs localhost +r Source:1766918427:1780715 B]AAAB AAAAG :Source
     * With oper+account+hidden: AA N DevPigs__ 1 1766923017 warpigs localhost +irohz opername Source:1766918427:1780715 S@urce B]AAAB AAAAG :Source
     * Full flags: AA N WarPigs 1 1771005495 ~WarPigs 127.0.0.1 +oiwsrh WarPigs WarPigs:1771005382:1780713 W@rPigs B]AAAB AAAAC :...
     * 
     * The numeric is always the second-to-last field before " :" (realname)
     * 
     * @param rawLine The complete raw line from the server
     * @return The user numeric/token (e.g., "ABAAA")
     */
    private String extractNumericFromNCommand(String rawLine) {
        // Find the position of " :" (space followed by colon) which marks the start of the realname
        int colonPos = rawLine.indexOf(" :");
        if (colonPos > 0) {
            // Extract the part before " :" and get the last word (which is the numeric)
            String beforeColon = rawLine.substring(0, colonPos);
            String[] parts = beforeColon.split(" ");
            if (parts.length > 0) {
                return parts[parts.length - 1];
            }
        }
        
        // Fallback: split the entire line and find the element before one starting with ":"
        String[] elem = rawLine.split(" ");
        for (int i = elem.length - 1; i >= 0; i--) {
            if (elem[i].startsWith(":")) {
                if (i > 0) {
                    return elem[i - 1];
                }
            }
        }
        
        // Last resort fallback
        return elem.length > 9 ? elem[9] : "";
    }

    /**
     * Process P10 N command following the ServerCommand schema:
     * Critical section (synchronized) for checking and registering user,
     * then propagation outside the lock for parallel processing.
     * 
     * P10 N command format (variable fields depending on modes):
     * <server> N <nick> <hopcount> <timestamp> <ident> <host> <modes> [opername] [account:ts:ts] [hiddenhost] <base64ip> <numeric> :<realname>
     * 
     * Examples:
     * Simple: AB N WarPigs 1 1766478053 warpigs localhost +i B]AAAB ABAAA :realname
     * With account: AA N DevPigs 1 1766923017 warpigs localhost +r Source:1766918427:1780715 B]AAAB AAAAG :Source
     * With oper+account+hidden: AA N DevPigs__ 1 1766923017 warpigs localhost +irohz opername Source:1766918427:1780715 S@urce B]AAAB AAAAG :Source
     * Full flags: AA N WarPigs 1 1771005495 ~WarPigs 127.0.0.1 +oiwsrh WarPigs WarPigs:1771005382:1780713 W@rPigs B]AAAB AAAAC :...
     */
    private void processNCommand(String[] elem, String rawLine, String jnumeric) {
        // Critical section: Check for duplicates, parse info, and register user
        boolean canProceed;
        synchronized (getMi().getUserRegistrationLock()) {
            canProceed = checkAndRegisterUser(elem, rawLine, jnumeric);
        }
        
        if (canProceed) {
            // Send propagation outside the lock to allow other registrations in parallel
            propagateUserToOthers(elem, rawLine, jnumeric);
        }
    }

    private boolean shouldPropagateRegisteredUser(String userToken, String jnumeric) {
        if (userToken == null || userToken.isBlank() || jnumeric == null || jnumeric.isBlank()) {
            return false;
        }

        // Existing users learned during server burst or users that belong to this
        // local server must not be forwarded again.
        if (isBurst() || userToken.startsWith(jnumeric)) {
            return false;
        }

        return true;
    }
    
    /**
     * Critical section: Check for duplicates, parse user info, and register user
    * Returns true if registration succeeded and may be propagated, false if the
    * user was handled locally or must not be propagated further.
     * 
     * Variable P10 N fields:
     * - elem[7] contains modes (+i, +r, +o, +h, +z, +k, +x, +w, +s)
     * - If +o: elem[8] = oper name
     * - If +r: account info field (format: AccountName:timestamp:timestamp)
     * - If +h: hidden host field
     * - Base64 IP and numeric are always the last two fields before ":"
     * 
     * Example: AA N WarPigs 1 1771005495 ~WarPigs 127.0.0.1 +oiwsrh WarPigs WarPigs:1771005382:1780713 W@rPigs B]AAAB AAAAC :...
     *   elem[8] = "WarPigs" (oper name, because +o)
     *   elem[9] = "WarPigs:1771005382:1780713" (account, because +r)
     *   elem[10] = "W@rPigs" (hidden host, because +h)
     *   elem[11] = "B]AAAB" (base64 IP)
     *   elem[12] = "AAAAC" (numeric)
     */
    private boolean checkAndRegisterUser(String[] elem, String rawLine, String jnumeric) {
        boolean debugMode = getMi() != null && getMi().getConfig() != null && 
            "true".equalsIgnoreCase(getMi().getConfig().getConfigFile().getProperty("debug", "false"));
        
        // Parse user info
        String nickname = elem[2];
        var priv = elem[7].contains("r");  // +r means user is authenticated
        var service = elem[7].contains("k");
        var x = elem[7].contains("x");
        var h = elem[7].contains("h");  // +h means user has a hidden host
        var o = elem[7].contains("o");  // +o means user is an IRC operator
        String acc = null;
        String hiddenHost = null;
        String userToken = extractNumericFromNCommand(rawLine);
        String rawIpField = extractBase64IpFromNCommand(rawLine);
        String decodedIp = normalizeIpString(rawIpField);
        
        if (debugMode) {
            System.out.printf("[DEBUG N-Command] Nick=%s, Modes=%s, UserToken=%s, RawIP=%s%n", 
                nickname, elem[7], userToken, rawIpField);
        }
        
        // P10 N field order after modes:
        // [opername (if +o)] [account:ts:ts (if +r)] [hiddenhost (if +h)] <base64ip> <numeric> :<realname>
        int fieldIndex = 8;
        
        // If +o is set, oper name is at elem[8]
        if (o) {
            if (debugMode && fieldIndex < elem.length) {
                System.out.printf("[DEBUG N-Command] Oper name at elem[%d]: %s%n", fieldIndex, elem[fieldIndex]);
            }
            fieldIndex++;  // Skip oper name field
        }
        
        // If +r is set, look for account field (contains ":" but doesn't start with ":")
        if (priv) {
            // Search within reasonable range for the account field
            for (int i = fieldIndex; i < elem.length && i < fieldIndex + 5; i++) {
                if (elem[i].contains(":") && !elem[i].startsWith(":")) {
                    String[] accountParts = elem[i].split(":", 2);
                    acc = accountParts[0];
                    fieldIndex = i + 1;
                    if (debugMode) {
                        System.out.printf("[DEBUG N-Command] Account found at elem[%d]: %s (extracted: %s)%n", 
                            i, elem[i], acc);
                    }
                    break;
                }
            }
            if (acc == null) {
                acc = "";
                if (debugMode) {
                    System.out.println("[DEBUG N-Command] Warning: +r flag set but no account field found");
                }
            }
        } else {
            acc = "";
        }
        
        // If +h is set, hidden host is at next field position
        if (h && fieldIndex < elem.length && !elem[fieldIndex].startsWith(":")) {
            hiddenHost = elem[fieldIndex];
            if (debugMode) {
                System.out.printf("[DEBUG N-Command] Hidden host at elem[%d]: %s%n", fieldIndex, hiddenHost);
            }
            fieldIndex++;  // Move past hidden host
        }
        
        if (debugMode) {
            System.out.printf("[DEBUG N-Command] Parsed: Nick=%s, Account=%s, HiddenHost=%s, IP=%s, Token=%s%n",
                nickname, acc != null ? acc : "(none)", hiddenHost != null ? hiddenHost : "(none)", 
                decodedIp, userToken);
        }
        
        var hosts = elem[5] + "@" + elem[6];
        
        // Check if user token already exists (prevent duplicates)
        if (getUsers().containsKey(userToken)) {
            Users existingUser = getUsers().get(userToken);
            String existingNick = existingUser.getNick();
            boolean wasService = existingUser.isService();
            boolean wasOper = existingUser.isOper();
            
            // Enhanced logging for different user types
            if (service || wasService) {
                System.out.printf("WARNING: Duplicate SERVICE detected!\n");
                System.out.printf("  Token: %s\n", userToken);
                System.out.printf("  Existing: %s (Service=%b, Oper=%b)\n", existingNick, wasService, wasOper);
                System.out.printf("  New:      %s (Service=%b, Oper=%b)\n", nickname, service, o);
                System.out.printf("  During BURST: %s\n", isBurst());
                LOG.warning(String.format("Duplicate service user token %s: existing=%s, new=%s", 
                    userToken, existingNick, nickname));
            } else if (o || wasOper) {
                System.out.printf("WARNING: Duplicate OPER detected!\n");
                System.out.printf("  Token: %s\n", userToken);
                System.out.printf("  Existing: %s (Service=%b, Oper=%b)\n", existingNick, wasService, wasOper);
                System.out.printf("  New:      %s (Service=%b, Oper=%b)\n", nickname, service, o);
                System.out.printf("  During BURST: %s\n", isBurst());
                LOG.warning(String.format("Duplicate oper user token %s: existing=%s, new=%s", 
                    userToken, existingNick, nickname));
            } else if (debugMode || isBurst()) {
                System.out.printf("NOTICE: Duplicate user token %s detected - updating existing user\n", userToken);
                System.out.printf("  Existing: %s\n", existingNick);
                System.out.printf("  New:      %s\n", nickname);
                System.out.printf("  During BURST: %s\n", isBurst());
            }
            
            // Update existing user instead of creating duplicate
            existingUser.setNick(nickname);
            existingUser.setIdent(elem[5]);
            existingUser.setAccount(acc);
            existingUser.setHost(elem[6]);
            existingUser.setHiddenHost(hiddenHost);
            existingUser.setClientIp(decodedIp);
            existingUser.setX(x);
            existingUser.setService(service);
            existingUser.setOper(o);
            return false; // Don't propagate, just updated existing
        }
        
        // Existing users received during netburst are state sync, not fresh logins.
        // Modules should only see actual post-burst new connections.
        boolean userHandled = false;
        if (!isBurst()) {
            for (Module module : getModuleManager().getAllModules().values()) {
                if (module.isEnabled()) {
                    if (module.handleNewUser(userToken, nickname, elem[5], elem[6], acc, jnumeric, hiddenHost)) {
                        userHandled = true;
                        break; // User was killed/handled by module
                    }
                }
            }
        }
        
        if (!userHandled) {
            // ATOMIC: Register user (within lock)
            getUsers().put(userToken, new Users(userToken, nickname, elem[5], acc, elem[6]));
            getUsers().get(userToken).setHiddenHost(hiddenHost);
            getUsers().get(userToken).setClientIp(decodedIp);
            getUsers().get(userToken).setX(x);
            getUsers().get(userToken).setService(service);
            getUsers().get(userToken).setOper(o);
            if (!acc.isBlank()) {
                getMi().getDb().updateData("lastuserhost", acc, hosts);
                getMi().getDb().updateData("lastpasschng", acc, time());
            }
            return shouldPropagateRegisteredUser(userToken, jnumeric);
        }
        
        return false; // User was handled/killed by module
    }
    
    /**
     * Propagate user to all other connected servers (OUTSIDE the lock)
     * This allows parallel processing while maintaining data integrity
     */
    private void propagateUserToOthers(String[] elem, String rawLine, String jnumeric) {
        // TODO: Implement propagation when SERVER connection support is added
        // For now, this is a placeholder for future server-to-server propagation
        // When implemented, this will forward the N command to other connected servers:
        // Format: <our-numeric> N <nick> <hopcount+1> <timestamp> <ident> <host> <modes> <base64ip> <numeric> :<realname>
        
        if (getMi().getConfig().getConfigFile().getProperty("debug", "false").equalsIgnoreCase("true")) {
            System.out.printf("DEBUG: Would propagate user %s to other servers (not yet implemented)\n", elem[2]);
        }
    }

    private static final class BurstUserEntry {
        private final String numeric;
        private final boolean op;
        private final boolean voice;

        private BurstUserEntry(String numeric, boolean op, boolean voice) {
            this.numeric = numeric;
            this.op = op;
            this.voice = voice;
        }
    }

    private static String[] buildBurstUsersArray(List<BurstUserEntry> entries) {
        String[] users = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            BurstUserEntry entry = entries.get(i);
            if (entry.op && entry.voice) {
                users[i] = entry.numeric + ":ov";
            } else if (entry.op) {
                users[i] = entry.numeric + ":o";
            } else if (entry.voice) {
                users[i] = entry.numeric + ":v";
            } else {
                users[i] = entry.numeric;
            }
        }
        return users;
    }

    private static String buildOutgoingBurstUserList(List<BurstUserEntry> entries, String jnumeric, boolean includeStatus) {
        StringBuilder userList = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            BurstUserEntry entry = entries.get(i);
            if (i > 0) {
                userList.append(",");
            }

            boolean op = includeStatus && entry.op;
            boolean voice = includeStatus && entry.voice;

            // Preserve previous behavior: local service users are opped in outgoing burst.
            // This is required so services keep their control role after burst sync.
            if (!op && !voice && entry.numeric.startsWith(jnumeric)) {
                op = true;
            }

            userList.append(entry.numeric);
            if (op && voice) {
                userList.append(":ov");
            } else if (op) {
                userList.append(":o");
            } else if (voice) {
                userList.append(":v");
            }
        }
        return userList.toString();
    }

    private List<BurstUserEntry> parseBurstUsers(String userListStr, String channel, boolean debugMode) {
        LinkedHashMap<String, BurstUserEntry> uniqueUsers = new LinkedHashMap<>();
        if (userListStr == null || userListStr.isBlank()) {
            return new ArrayList<>();
        }

        boolean currentOp = false;
        boolean currentVoice = false;

        String[] rawUsers = userListStr.split("[,\\s]+");
        for (String rawUser : rawUsers) {
            if (rawUser == null || rawUser.isBlank()) {
                continue;
            }

            String userNumeric = rawUser;
            String marker = null;
            int markerPos = rawUser.indexOf(':');
            if (markerPos >= 0) {
                userNumeric = rawUser.substring(0, markerPos);
                marker = markerPos + 1 < rawUser.length() ? rawUser.substring(markerPos + 1) : "";
            }

            if (userNumeric.isBlank()) {
                continue;
            }

            if (marker != null && !marker.isBlank()) {
                boolean hasVoice = marker.indexOf('v') >= 0;
                boolean hasOp = marker.indexOf('o') >= 0;
                boolean hasDigit = marker.chars().anyMatch(Character::isDigit);

                if (hasOp || hasDigit) {
                    currentOp = true;
                } else if (hasVoice) {
                    currentOp = false;
                }

                if (hasVoice) {
                    currentVoice = true;
                } else if (hasOp) {
                    currentVoice = false;
                }
            }

            BurstUserEntry parsed = new BurstUserEntry(userNumeric, currentOp, currentVoice);
            BurstUserEntry previous = uniqueUsers.get(userNumeric);
            if (previous == null) {
                uniqueUsers.put(userNumeric, parsed);
            } else {
                BurstUserEntry merged = new BurstUserEntry(userNumeric, previous.op || parsed.op, previous.voice || parsed.voice);
                uniqueUsers.put(userNumeric, merged);

                if (getUsers().containsKey(userNumeric)) {
                    Users user = getUsers().get(userNumeric);
                    if (user.isService()) {
                        LOG.warning(String.format("Duplicate service in burst for %s: %s (%s)", channel, userNumeric, user.getNick()));
                    } else if (user.isOper()) {
                        LOG.warning(String.format("Duplicate oper in burst for %s: %s (%s)", channel, userNumeric, user.getNick()));
                    } else if (debugMode) {
                        System.out.printf("NOTICE: Duplicate user in BURST for channel %s: %s (%s)\n", channel, userNumeric, user.getNick());
                    }
                } else if (debugMode) {
                    System.out.printf("NOTICE: Duplicate unknown user in BURST for channel %s: %s\n", channel, userNumeric);
                }
            }
        }

        return new ArrayList<>(uniqueUsers.values());
    }

    private void updateKnownUsersChannelMembership(List<BurstUserEntry> usersInBurst, String channel) {
        for (BurstUserEntry entry : usersInBurst) {
            Users user = getUsers().get(entry.numeric);
            if (user != null) {
                user.addChannel(channel);
            }
        }
    }

    private void applyBurstToExistingChannel(Channel existingChannel, List<BurstUserEntry> usersInBurst, boolean applyModes) {
        for (BurstUserEntry entry : usersInBurst) {
            if (!existingChannel.getUsers().contains(entry.numeric)) {
                existingChannel.addUser(entry.numeric);
            }
            if (applyModes) {
                if (entry.op && !existingChannel.getOp().contains(entry.numeric)) {
                    existingChannel.addOp(entry.numeric);
                }
                if (entry.voice && !existingChannel.getVoice().contains(entry.numeric)) {
                    existingChannel.addVoice(entry.numeric);
                }
            }
        }
    }

    private Channel buildChannelFromBurst(String channel, String modes, List<BurstUserEntry> usersInBurst, long channelTs) {
        Channel channelObj = new Channel(channel, modes != null ? modes : "", buildBurstUsersArray(usersInBurst));
        if (channelTs > 0) {
            channelObj.setCreatedTimestamp(channelTs);
        }
        return channelObj;
    }

    @Override
    public void run() {
        System.out.println("Connecting to server...");
        setRuns(true);
        var host = getMi().getConfig().getConfigFile().getProperty("host");
        var port = getMi().getConfig().getConfigFile().getProperty("port");
        var password = getMi().getConfig().getConfigFile().getProperty("password");
        var jservername = getMi().getConfig().getConfigFile().getProperty("servername");
        var jdescription = getMi().getConfig().getConfigFile().getProperty("description");
        var jnumeric = getMi().getConfig().getConfigFile().getProperty("numeric");
        try {
            setSocket(new Socket(host, Integer.parseInt(port)));
            setPw(new PrintWriter(getSocket().getOutputStream()));
            setBr(new BufferedReader(new InputStreamReader(getSocket().getInputStream())));

            var content = "";
            handshake(password, jservername, jdescription, jnumeric);
            
            // Initialize ModuleManager with streams
            getModuleManager().setStreams(getPw(), getBr());
            
            // Load modules from configuration file
            // This replaces the old manual registration and enabling
            String moduleConfigFile = "config-modules-extended.json";
            getModuleManager().loadModulesFromConfig(moduleConfigFile);
            
            // Perform post-load initialization for all enabled modules
            for (Module module : getModuleManager().getAllModules().values()) {
                if (module.isEnabled()) {
                    module.postLoadInitialization();
                }
            }
            
            // Perform handshakes for enabled modules
            getModuleManager().performHandshakes(jnumeric);
            
            System.out.println("Handshake complete...");
            
            // Let modules register their burst channels
            for (Module module : getModuleManager().getAllModules().values()) {
                if (module.isEnabled()) {
                    module.registerBurstChannels(getBursts(), jnumeric);
                }
            }
            
            System.out.println("Successfully connected...");
            sendText("%s EB", jnumeric);
            while (!getSocket().isClosed() && (content = getBr().readLine()) != null && isRuns()) {
                try {
                    var elem = content.split(" ");
                    if (elem.length < 2) {
                        continue;
                    }
                    if (content.startsWith("SERVER")) {
                        setServerNumeric(content.split(" ")[SERVERNAME_INDEX].substring(0, 1));
                        System.out.println("Getting SERVER response...");
                    } else if (elem[1].equals("EB") && isBurst()) {
                        
                        // Now that all users are loaded, check database for channel permissions
                        var list = getMi().getDb().getChannels();
                        var nicks = getMi().getDb().getData();
                        for (var channel : list) {
                            if (channel[1].startsWith("#")) {
                                var chanLower = channel[1].toLowerCase();
                                if (!getBursts().containsKey(chanLower)) {
                                    continue; // Skip channels not registered by modules
                                }
                                var cid = channel[0];
                                System.out.println("[DEBUG] Processing channel: " + channel[1] + " (ID: " + cid + ")");
                                for (var nick : nicks) {
                                    var nid = nick[0];
                                    var auth = getMi().getDb().getChanUser(Long.parseLong(nid), Long.parseLong(cid));
                                    if (auth != null && auth.length > 0 && auth[0] != null) {
                                        int flags = 0;
                                        try {
                                            flags = Integer.parseInt(auth[0]);
                                        } catch (NumberFormatException e) {
                                            System.out.println("[DEBUG] Invalid flags for user " + nick[1] + " in channel " + channel[1]);
                                            continue;
                                        }
                                        
                                        // Check if user has AUTOOP, AUTOVOICE, or BANNED flags
                                        boolean hasAutoOp = Userflags.hasQCUFlag(flags, Userflags.QCUFlag.AUTOOP);
                                        boolean hasAutoVoice = Userflags.hasQCUFlag(flags, Userflags.QCUFlag.AUTOVOICE);
                                        boolean isBanned = Userflags.hasQCUFlag(flags, Userflags.QCUFlag.BANNED);
                                        
                                        System.out.println("[DEBUG] User " + nick[1] + " in channel " + channel[1] + 
                                            " - Flags: " + flags + " - AutoOp: " + hasAutoOp + " - AutoVoice: " + hasAutoVoice + " - Banned: " + isBanned);
                                        
                                        if (!hasAutoOp && !hasAutoVoice && !isBanned) {
                                            continue; // User has no auto-rights or ban
                                        }
                                        
                                        var users = getUsers().keySet();
                                        for (var user : users) {
                                            var u = getUsers().get(user);
                                            if (u.getAccount() != null && u.getAccount().equalsIgnoreCase(nick[1])) {
                                                // Only apply DB-derived burst status for users who are actually
                                                // in this channel (known channel state or user channel list).
                                                boolean userInChannel = false;
                                                Channel knownChannel = getChannel().get(chanLower);
                                                if (knownChannel != null && knownChannel.getUsers().contains(user)) {
                                                    userInChannel = true;
                                                }
                                                if (!userInChannel && u.getChannels() != null && u.getChannels().contains(chanLower)) {
                                                    userInChannel = true;
                                                }

                                                if (!userInChannel) {
                                                    if (getMi().getConfig().getConfigFile().getProperty("debug", "false").equalsIgnoreCase("true")) {
                                                        System.out.println("[DEBUG] Skip DB-rights burst add for " + user + " in " + chanLower + " (not a channel member)");
                                                    }
                                                    continue;
                                                }

                                                System.out.println("[DEBUG] Found online user " + user + " matching account " + nick[1]);
                                                
                                                // Handle BANNED flag - mark for ban+kick (to be processed by ChanServ)
                                                if (isBanned) {
                                                    getBursts().get(chanLower).getUsers().add(user + ":b");
                                                    System.out.println("[DEBUG] Added " + user + " with ban flag to " + chanLower);
                                                    continue; // Don't add op/voice modes for banned users
                                                }
                                                
                                                // Check if user already added to this channel's burst
                                                boolean alreadyAdded = false;
                                                for (Object obj : getBursts().get(chanLower).getUsers()) {
                                                    String entry = String.valueOf(obj);
                                                    if (entry.startsWith(user + ":") || entry.equals(user)) {
                                                        alreadyAdded = true;
                                                        // Enhanced logging for services/opers
                                                        Users userObj = getUsers().get(user);
                                                        if (userObj != null) {
                                                            if (userObj.isService()) {
                                                                System.out.printf("WARNING: Service %s (%s) already in burst list for %s\n", 
                                                                    user, userObj.getNick(), chanLower);
                                                            } else if (userObj.isOper()) {
                                                                System.out.printf("WARNING: Oper %s (%s) already in burst list for %s\n", 
                                                                    user, userObj.getNick(), chanLower);
                                                            }
                                                        }
                                                        break;
                                                    }
                                                }
                                                if (alreadyAdded) {
                                                    System.out.println("[DEBUG] User " + user + " already added to " + chanLower);
                                                    continue;
                                                }
                                                
                                                // Add with appropriate mode
                                                if (hasAutoOp) {
                                                    getBursts().get(chanLower).getUsers().add(user + ":o");
                                                    System.out.println("[DEBUG] Added " + user + " with +o to " + chanLower);
                                                } else if (hasAutoVoice) {
                                                    getBursts().get(chanLower).getUsers().add(user + ":v");
                                                    System.out.println("[DEBUG] Added " + user + " with +v to " + chanLower);
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                // Add local connected users to channels they are in (even if not registered in DB)
                                var onlineUsers = getUsers().keySet();
                                for (var onlineUser : onlineUsers) {
                                    // Only include local users from this server in outgoing burst.
                                    if (!onlineUser.startsWith(jnumeric)) {
                                        continue;
                                    }
                                    Users userData = getUsers().get(onlineUser);
                                    if (userData.getChannels() != null && userData.getChannels().contains(chanLower)) {
                                        // Check if this user is already added to burst
                                        boolean alreadyInBurst = false;
                                        for (Object obj : getBursts().get(chanLower).getUsers()) {
                                            String entry = String.valueOf(obj);
                                            String userInBurst;
                                            if (entry.contains(":")) {
                                                userInBurst = entry.split(":")[0];
                                            } else {
                                                userInBurst = entry;
                                            }
                                            if (userInBurst.equals(onlineUser)) {
                                                alreadyInBurst = true;
                                                // Enhanced logging for services/opers
                                                if (userData.isService()) {
                                                    System.out.printf("NOTICE: Service %s (%s) already in burst for %s - skipping duplicate\n",
                                                        onlineUser, userData.getNick(), chanLower);
                                                } else if (userData.isOper()) {
                                                    System.out.printf("NOTICE: Oper %s (%s) already in burst for %s - skipping duplicate\n",
                                                        onlineUser, userData.getNick(), chanLower);
                                                }
                                                break;
                                            }
                                        }
                                        
                                        if (!alreadyInBurst) {
                                            getBursts().get(chanLower).getUsers().add(onlineUser);
                                            System.out.println("[DEBUG] Added unregistered user " + onlineUser + " to " + chanLower);
                                        }
                                    }
                                }
                            }
                        }
                        
                        sendText("%s EA", jnumeric);
                        System.out.printf("Sending BURST for %d channels for the services...\r\n", list.size());
                        var bursts = getBursts().keySet();
                        for (var burst : bursts) {
                            Burst burstData = getBursts().get(burst);
                            boolean debugMode = getMi() != null && getMi().getConfig() != null
                                    && "true".equalsIgnoreCase(getMi().getConfig().getConfigFile().getProperty("debug", "false"));
                            List<BurstUserEntry> outgoingUsers = parseBurstUsers(String.join(",", burstData.getUsers()), burst, debugMode);

                            // Ignore foreign users in server-side outgoing burst.
                            List<BurstUserEntry> localOutgoingUsers = new ArrayList<>();
                            for (BurstUserEntry entry : outgoingUsers) {
                                if (entry.numeric != null && entry.numeric.startsWith(jnumeric)) {
                                    localOutgoingUsers.add(entry);
                                } else if (debugMode) {
                                    System.out.println("[DEBUG] Ignoring foreign user in outgoing BURST for " + burst + ": " + entry.numeric);
                                }
                            }
                            
                            Channel existingChannel = getChannel().get(burst.toLowerCase());
                            // Use real channel TS for outgoing burst whenever possible.
                            long burstTs = 0;
                            if (existingChannel != null && existingChannel.getCreatedTimestamp() != null
                                    && existingChannel.getCreatedTimestamp() > 0) {
                                burstTs = existingChannel.getCreatedTimestamp();
                            } else if (burstData.getTime() > 0) {
                                burstTs = burstData.getTime();
                            } else {
                                String createdTs = getMi().getDb().getChannel("created", burst);
                                if (createdTs != null && !createdTs.isBlank()) {
                                    try {
                                        burstTs = Long.parseLong(createdTs);
                                    } catch (NumberFormatException ex) {
                                        LOG.warning("Failed to parse DB created timestamp for channel " + burst + ": " + createdTs);
                                    }
                                }
                            }
                            if (burstTs <= 0) {
                                burstTs = time();
                            }
                            burstData.setTime(burstTs);

                            String burstModes = burstData.getModes();

                            long localTs = 0;
                            if (existingChannel != null && existingChannel.getCreatedTimestamp() != null) {
                                localTs = existingChannel.getCreatedTimestamp();
                            }

                            // ircu2-style TS rule for outgoing burst:
                            // - newer TS loses modes/status,
                            // - older/equal TS keeps modes/status.
                            boolean keepModesAndStatus = !(burstTs > 0 && localTs > 0 && burstTs > localTs);

                            List<BurstUserEntry> effectiveOutgoingUsers = new ArrayList<>(localOutgoingUsers.size());
                            if (keepModesAndStatus) {
                                effectiveOutgoingUsers.addAll(localOutgoingUsers);
                            } else {
                                for (BurstUserEntry entry : localOutgoingUsers) {
                                    effectiveOutgoingUsers.add(new BurstUserEntry(entry.numeric, false, false));
                                }
                            }

                            String modesToSend = keepModesAndStatus ? burstModes : "";
                            String userList = buildOutgoingBurstUserList(effectiveOutgoingUsers, jnumeric, keepModesAndStatus);
                            String[] effectiveBurstUsers = userList.isBlank() ? new String[0] : userList.split(",");
                            
                            // Send BURST command for the channel
                            // Format: <numeric> B <channel> <timestamp> [+modes] :<users>
                            if (modesToSend != null && !modesToSend.isEmpty() && modesToSend.startsWith("+")) {
                                sendText("%s B %s %d %s %s", jnumeric, burst, burstTs, modesToSend, userList);
                            } else {
                                sendText("%s B %s %d %s", jnumeric, burst, burstTs, userList);
                            }
                            
                            System.out.printf("BURST: %s with %d users at timestamp %d\r\n", burst, effectiveBurstUsers.length, burstTs);
                            
                            // Keep local channel state consistent with TS rule used for outgoing BURST.
                            if (existingChannel == null) {
                                Channel newChannel = buildChannelFromBurst(
                                        burst,
                                        keepModesAndStatus ? (burstModes != null ? burstModes : "") : "",
                                    effectiveOutgoingUsers,
                                        burstTs);
                                getChannel().put(burst.toLowerCase(), newChannel);
                            } else {
                                if (keepModesAndStatus) {
                                    if (burstModes != null && !burstModes.isEmpty()) {
                                        existingChannel.setModes(burstModes);
                                    }
                                    if (burstTs > 0 && (existingChannel.getCreatedTimestamp() == null || burstTs <= existingChannel.getCreatedTimestamp())) {
                                        existingChannel.setCreatedTimestamp(burstTs);
                                    }
                                }
                                applyBurstToExistingChannel(existingChannel, effectiveOutgoingUsers, keepModesAndStatus);
                            }
                            
                            // Add channel to each user's channel list
                            for (var userObj : effectiveBurstUsers) {
                                String userEntry = String.valueOf(userObj);
                                String userNumeric;
                                if (userEntry.endsWith(":o") || userEntry.endsWith(":v") || userEntry.endsWith(":b")) {
                                    userNumeric = userEntry.substring(0, userEntry.length() - 2);
                                } else if (userEntry.contains(":")) {
                                    userNumeric = userEntry.split(":")[0];
                                } else {
                                    userNumeric = userEntry;
                                }
                                if (getUsers().containsKey(userNumeric)) {
                                    getUsers().get(userNumeric).addChannel(burst.toLowerCase());
                                }
                            }

                        }
                        setBurst(false);
                        System.out.println("Channels joined...");
                    } else if (elem[1].equals("J") || elem[1].equals("C")) {
                        var channel = elem[2];
                        var names = elem[0];
                        var user = new String[1];
                        
                        // If it's a CREATE (C), give the creator OP status
                        if (elem[1].equals("C")) {
                            user[0] = names + ":o";
                            System.out.println("[DEBUG] Channel CREATE: " + channel + " by " + names + " - giving OP status");
                        } else {
                            user[0] = names;
                        }
                        
                        // Check if channel is suspended - if so, kick the user
                        String suspendBy = getMi().getDb().getChannel("suspendby", channel);
                        if (suspendBy != null && !suspendBy.isEmpty() && !suspendBy.equals("0")) {
                            String reason = getMi().getDb().getChannel("suspendreason", channel);
                            String kickReason = "Channel suspended: " + (reason != null ? reason : "No reason given");
                            sendText("%s K %s %s :%s", "AA", channel, names, kickReason);
                            continue;
                        }
                        
                        if (getChannel().containsKey(channel.toLowerCase())) {
                            getChannel().get(channel.toLowerCase()).addUser(names);
                            getChannel().get(channel.toLowerCase()).getLastJoin().put(names, time());
                            
                            // If it's a CREATE and channel already exists, add OP
                            if (elem[1].equals("C")) {
                                getChannel().get(channel.toLowerCase()).addOp(names);
                            }
                        } else {
                            getChannel().put(channel.toLowerCase(), new Channel(channel, "", user));
                        }
                        // Add channel to user's channel list
                        if (getUsers().containsKey(names)) {
                            getUsers().get(names).addChannel(channel.toLowerCase());
                        }
                    } else if (elem[1].equals("N") && elem.length >= 10) {
                        // P10 N command - new user registration
                        // Critical section: Check + Parse + Register (must be atomic)
                        // Propagation is done after lock is released
                        processNCommand(elem, content, jnumeric);
                    } else if (elem[1].equals("N") && elem.length >= 3) {
                        Users nickChangeUser = getUsers().get(elem[0]);
                        if (nickChangeUser != null) {
                            nickChangeUser.setNick(elem[2]);
                        } else if (getMi().getConfig().getConfigFile().getProperty("debug", "false").equalsIgnoreCase("true")) {
                            System.out.printf("DEBUG: Ignoring NICK change for unknown numeric %s -> %s\n", elem[0], elem[2]);
                        }
                    } else if (elem[1].equals("B") && elem.length >= 5) {
                        // P10 Burst format: <numeric> B <channel> <timestamp> [+flags] <users...> [:<bans/exceptions>]
                        String channel = elem[2].toLowerCase();
                        String modes = "";
                        int userStartIndex;

                        if (elem.length > 4 && elem[4].startsWith("+")) {
                            modes = elem[4];
                            userStartIndex = 5;
                        } else {
                            userStartIndex = 4;
                        }

                        long burstTs = 0;
                        try {
                            burstTs = Long.parseLong(elem[3]);
                        } catch (NumberFormatException ex) {
                            LOG.warning("Failed to parse BURST timestamp for channel " + channel + ": " + elem[3]);
                        }

                        StringBuilder userListBuilder = new StringBuilder();
                        for (int i = userStartIndex; i < elem.length; i++) {
                            String token = elem[i];
                            if (token.startsWith(":")) {
                                break;
                            }
                            if (userListBuilder.length() > 0) {
                                userListBuilder.append(" ");
                            }
                            userListBuilder.append(token);
                        }

                        boolean debugMode = getMi() != null && getMi().getConfig() != null
                                && "true".equalsIgnoreCase(getMi().getConfig().getConfigFile().getProperty("debug", "false"));
                        List<BurstUserEntry> usersInBurst = parseBurstUsers(userListBuilder.toString(), channel, debugMode);

                        Channel existingChannel = getChannel().get(channel);
                        if (existingChannel == null) {
                            Channel newChannel = buildChannelFromBurst(channel, modes, usersInBurst, burstTs);
                            getChannel().put(channel, newChannel);
                        } else {
                            Long localTsObj = existingChannel.getCreatedTimestamp();
                            long localTs = localTsObj != null ? localTsObj : 0;

                            if (burstTs > 0 && localTs > 0 && burstTs < localTs) {
                                // Remote side has older TS -> remote channel state wins.
                                Channel mergedFromBurst = buildChannelFromBurst(channel, modes, usersInBurst, burstTs);

                                // Preserve any local-only members as plain users.
                                for (String existingUser : existingChannel.getUsers()) {
                                    if (!mergedFromBurst.getUsers().contains(existingUser)) {
                                        mergedFromBurst.addUser(existingUser);
                                    }
                                }

                                getChannel().put(channel, mergedFromBurst);
                            } else if (burstTs > 0 && localTs > 0 && burstTs > localTs) {
                                // Remote side has newer TS -> it loses channel modes/user-status.
                                applyBurstToExistingChannel(existingChannel, usersInBurst, false);
                            } else {
                                // Equal TS (or unknown TS locally/remotely): merge members and status.
                                if (!modes.isEmpty()) {
                                    existingChannel.setModes(modes);
                                }
                                if (burstTs > 0 && localTs <= 0) {
                                    existingChannel.setCreatedTimestamp(burstTs);
                                }
                                applyBurstToExistingChannel(existingChannel, usersInBurst, true);
                            }
                        }

                        updateKnownUsersChannelMembership(usersInBurst, channel);
                    } else if (elem[1].equals("AC") && getUsers().containsKey(elem[2])) {
                        var acc = elem[3];
                        var nick = elem[2];
                        if (getUsers().get(nick).getAccount().isBlank()) {
                            getUsers().get(nick).setAccount(acc);
                        }
                        
                        // Let modules handle authentication (e.g., HostServ sets vhost)
                        for (Module module : getModuleManager().getAllModules().values()) {
                            if (module.isEnabled()) {
                                module.handleAuthentication(nick, acc, jnumeric);
                            }
                        }
                    } else if (elem.length >= 2 && elem[1].equals("G")) {
                        // Reply to server ping; be tolerant of short/malformed lines
                        String payload = "";
                        if (elem.length >= 3) {
                            int idx = content.indexOf(elem[2]);
                            payload = idx >= 0 ? content.substring(idx) : "";
                        }
                        sendText("%s Z %s", jnumeric, payload);
                    } else if (elem[1].equals("M") && elem.length >= 4) {
                        var nick = elem[0];
                        if (elem[3].contains("x")) {
                            getUsers().get(nick).setX(true);
                        }
                        if (elem[3].contains("k")) {
                            getUsers().get(nick).setService(true);
                        }
                        if (elem[3].contains("o")) {
                            getUsers().get(nick).setOper(true);
                        }
                    } else if (elem[1].equals("M")) {
                        var channel = elem[2].toLowerCase();
                        if (channel.startsWith("#")) {
                            var flags = elem[3].split("");
                            var set = false;
                            for (var mode : flags) {
                                if (mode.equals("-")) {
                                    set = false;
                                } else if (mode.equals("+")) {
                                    set = true;
                                }
                                if (set && mode.equals("o")) {
                                    var users = elem[4].split(" ");
                                    getChannel().get(channel.toLowerCase()).addOp(users[0]);
                                }
                                if (set && mode.equals("v")) {
                                    var users = elem[4].split(" ");
                                    getChannel().get(channel.toLowerCase()).addVoice(users[0]);
                                }
                                if (!set && mode.equals("o")) {
                                    var users = elem[4].split(" ");
                                    getChannel().get(channel.toLowerCase()).removeOp(users[0]);
                                }
                                if (!set && mode.equals("v")) {
                                    var users = elem[4].split(" ");
                                    getChannel().get(channel.toLowerCase()).removeVoice(users[0]);
                                }
                            }
                        }
                    } else if (elem[1].equals("T") && elem.length >= 4) {
                        // Topic command: AA T #channel timestamp :topic text
                        String channel = elem[2].toLowerCase();
                        String topic = content.substring(content.indexOf(':', 1) + 1);
                        
                        Channel channelObj = getChannel().get(channel);
                        if (channelObj != null) {
                            channelObj.setTopic(topic);
                            System.out.println("[DEBUG] Topic set for " + channel + ": " + topic);
                        }
                    } else if (elem[1].equals("Q")) {
                        var nick = elem[0];
                        for (var users : getUsers().values()) {
                            var channels = users.getChannels().toArray();
                            for (var channel : channels) {
                                removeUser(nick, channel.toString());
                            }
                        }
                        if (getAuthed().containsKey(nick)) {
                            getAuthed().remove(nick);
                        }
                        var nn = getUsers().containsKey(nick) ? getUsers().get(nick).getAccount() : null;
                        if (nn != null && getAuthed().containsKey(nn)) {
                            getAuthed().remove(nn);
                        }
                        getUsers().remove(nick);
                    } else if (elem[1].equals("D")) {
                        var nick = elem[2];
                        for (var users : getUsers().values()) {
                            var channels = users.getChannels().toArray();
                            for (var channel : channels) {
                                removeUser(nick, channel.toString());
                            }
                        }
                        if (getAuthed().containsKey(nick)) {
                            getAuthed().remove(nick);
                        }
                        // Check if user exists before accessing nick
                        if (getUsers().containsKey(nick)) {
                            var nn = getUsers().get(nick).getNick();
                            if (getAuthed().containsKey(nn)) {
                                getAuthed().remove(nn);
                            }
                        }
                        getUsers().remove(nick);
                    }
                    // Route line to all enabled modules - MUST be called for every line
                    getModuleManager().routeLine(content);
                    
                    if (getMi().getConfig().getConfigFile().getProperty("debug", "false").equalsIgnoreCase("true")) {
                        System.out.printf("DEBUG get text: %s\n", content);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException | NumberFormatException ex) {
            LOG.severe("Fehler beim Verbindungsaufbau: " + ex.getMessage());
        } finally {
            if (getPw() != null) {
                getPw().close();
            }
            if (getBr() != null) try {
                getBr().close();
            } catch (IOException ignored) {
            }
            if (getSocket() != null) try {
                getSocket().close();
            } catch (IOException ignored) {
            }
            setPw(null);
            setBr(null);
            setSocket(null);
            setRuns(false);
            System.out.println("Disconnected...");
        }
    }

    protected void partChannel(String channel, String numeric, String service) {
        // Send PART (L) with timestamp; include a short reason for readability
        sendText("%s%s L %s :Leaving", numeric, service, channel);
    }

    protected void removeUser(String nick, String channel) {
        if (!getChannel().containsKey(channel.toLowerCase())) {
            return;
        }
        var ch = getChannel().get(channel.toLowerCase());
        ch.removeUser(nick);
        ch.removeOp(nick);
        ch.removeVoice(nick);
        if (ch.getLastJoin().containsKey(nick)) {
            ch.getLastJoin().remove(nick);
        }
        if (ch.getUsers().isEmpty()) {
            getChannel().remove(channel.toLowerCase());
        }
        // Check if user exists before accessing channels
        if (getUsers().containsKey(nick) && getUsers().get(nick) != null && 
            getUsers().get(nick).getChannels().contains(channel.toLowerCase())) {
            getUsers().get(nick).removeChannel(channel.toLowerCase());
        }
    }

    protected boolean isAuthed(String nick) {
        return !checkAuthed(nick).isEmpty();
    }

    protected ArrayList<String> checkAuthed(String nick) {
        var list = new ArrayList<String>();
        var users = getUsers().keySet();
        for (var user : users) {
            if (getUsers().get(user) != null && !getUsers().get(user).getAccount().isBlank() && getUsers().get(user).getAccount().equalsIgnoreCase(nick)) {
                list.add(user);
            }
        }
        return list;
    }

    protected boolean isNotice(String nick) {
        if (!nick.isBlank()) {
            var flags = getMi().getDb().getFlags(nick);
            return isNotice(flags);
        }
        return true;
    }

    protected boolean isPrivileged(int flags) {
        var oper = isOper(flags);
        if (oper == false) {
            oper = isAdmin(flags);
        }
        if (oper == false) {
            oper = isDev(flags);
        }
        if (oper == false) {
            oper = isStaff(flags);
        }
        return oper;
    }

    protected boolean isPrivileged(String nick) {
        if (!nick.isBlank()) {
            var flags = getMi().getDb().getFlags(nick);
            var oper = (Userflags.hasFlag(flags, Userflags.Flag.OPER) | Userflags.hasFlag(flags, Userflags.Flag.DEV) | Userflags.hasFlag(flags, Userflags.Flag.PROTECT) | Userflags.hasFlag(flags, Userflags.Flag.HELPER) | Userflags.hasFlag(flags, Userflags.Flag.ADMIN) | Userflags.hasFlag(flags, Userflags.Flag.STAFF));
            return oper;
        }
        return false;
    }

    protected boolean isHelper(String nick) {
        if (!nick.isBlank()) {
            var flags = getMi().getDb().getFlags(nick);
            var oper = (Userflags.hasFlag(flags, Userflags.Flag.HELPER));
            return oper;
        }
        return false;
    }

    protected boolean isStaff(String nick) {
        if (!nick.isBlank()) {
            var flags = getMi().getDb().getFlags(nick);
            var oper = (Userflags.hasFlag(flags, Userflags.Flag.HELPER) | Userflags.hasFlag(flags, Userflags.Flag.STAFF));
            return oper;
        }
        return false;
    }

    protected boolean isOper(String nick) {
        if (!nick.isBlank()) {
            var flags = getMi().getDb().getFlags(nick);
            var oper = (Userflags.hasFlag(flags, Userflags.Flag.OPER) | Userflags.hasFlag(flags, Userflags.Flag.HELPER) | Userflags.hasFlag(flags, Userflags.Flag.STAFF));
            return oper;
        }
        return false;
    }

    protected boolean isAdmin(String nick) {
        if (!nick.isBlank()) {
            var flags = getMi().getDb().getFlags(nick);
            var oper = (Userflags.hasFlag(flags, Userflags.Flag.OPER) | Userflags.hasFlag(flags, Userflags.Flag.HELPER) | Userflags.hasFlag(flags, Userflags.Flag.ADMIN) | Userflags.hasFlag(flags, Userflags.Flag.STAFF));
            return oper;
        }
        return false;
    }

    protected boolean isDev(String nick) {
        if (!nick.isBlank()) {
            var flags = getMi().getDb().getFlags(nick);
            var oper = (Userflags.hasFlag(flags, Userflags.Flag.OPER) | Userflags.hasFlag(flags, Userflags.Flag.DEV) | Userflags.hasFlag(flags, Userflags.Flag.HELPER) | Userflags.hasFlag(flags, Userflags.Flag.ADMIN) | Userflags.hasFlag(flags, Userflags.Flag.STAFF));
            return oper;
        }
        return false;
    }

    protected boolean isMaster(int flags) {
        return Userflags.hasQCUFlag(flags, Userflags.QCUFlag.MASTER);
    }

    protected boolean isOwner(int flags) {
        return Userflags.hasQCUFlag(flags, Userflags.QCUFlag.OWNER);
    }

    protected boolean isOp(int flags) {
        return Userflags.hasQCUFlag(flags, Userflags.QCUFlag.OP);
    }

    protected boolean isVoice(int flags) {
        return Userflags.hasQCUFlag(flags, Userflags.QCUFlag.VOICE);
    }

    protected boolean isNoInfo(int flags) {
        return flags == 0;
    }

    protected boolean isInactive(int flags) {
        return Userflags.hasFlag(flags, Userflags.Flag.INACTIVE);
    }

    protected boolean isGline(int flags) {
        return Userflags.hasFlag(flags, Userflags.Flag.GLINE);
    }

    protected boolean isNotice(int flags) {
        return Userflags.hasFlag(flags, Userflags.Flag.NOTICE);
    }

    protected boolean isSuspended(int flags) {
        return Userflags.hasFlag(flags, Userflags.Flag.SUSPENDED);
    }

    protected boolean isOper(int flags) {
        return Userflags.hasFlag(flags, Userflags.Flag.OPER);
    }

    protected boolean isDev(int flags) {
        return Userflags.hasFlag(flags, Userflags.Flag.DEV);
    }

    protected boolean isProtect(int flags) {
        return Userflags.hasFlag(flags, Userflags.Flag.PROTECT);
    }

    protected boolean isHelper(int flags) {
        return Userflags.hasFlag(flags, Userflags.Flag.HELPER);
    }

    protected boolean isAdmin(int flags) {
        return Userflags.hasFlag(flags, Userflags.Flag.ADMIN);
    }

    protected boolean isInfo(int flags) {
        return Userflags.hasFlag(flags, Userflags.Flag.INFO);
    }

    protected boolean isDelayedGline(int flags) {
        return Userflags.hasFlag(flags, Userflags.Flag.DELAYEDGLINE);
    }

    protected boolean isNoAuthLimit(int flags) {
        return Userflags.hasFlag(flags, Userflags.Flag.NOAUTHLIMIT);
    }

    protected boolean isCleanupExempt(int flags) {
        return Userflags.hasFlag(flags, Userflags.Flag.CLEANUPEXEMPT);
    }

    protected boolean isStaff(int flags) {
        return Userflags.hasFlag(flags, Userflags.Flag.STAFF);
    }

    /**
     * @return the mi
     */
    public JServ getMi() {
        return mi;
    }

    /**
     * @param mi the mi to set
     */
    public void setMi(JServ mi) {
        this.mi = mi;
    }

    /**
     * @return the socket
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * @param socket the socket to set
     */
    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    /**
     * @return the pw
     */
    public PrintWriter getPw() {
        return pw;
    }

    /**
     * @param pw the pw to set
     */
    public void setPw(PrintWriter pw) {
        this.pw = pw;
    }

    /**
     * @return the br
     */
    public BufferedReader getBr() {
        return br;
    }

    /**
     * @param br the br to set
     */
    public void setBr(BufferedReader br) {
        this.br = br;
    }

    /**
     * @return the runs
     */
    public boolean isRuns() {
        return runs;
    }

    /**
     * @param runs the runs to set
     */
    public void setRuns(boolean runs) {
        this.runs = runs;
    }

    /**
     * @return the serverNumeric
     */
    public String getServerNumeric() {
        return serverNumeric;
    }

    /**
     * @param serverNumeric the serverNumeric to set
     */
    public void setServerNumeric(String serverNumeric) {
        this.serverNumeric = serverNumeric;
    }

    /**
     * @return the reg
     */
    public boolean isReg() {
        return reg;
    }

    /**
     * @param reg the reg to set
     */
    public void setReg(boolean reg) {
        this.reg = reg;
    }

    /**
     * @return the bursts
     */
    public HashMap<String, Burst> getBursts() {
        return bursts;
    }

    /**
     * @param bursts the bursts to set
     */
    public void setBursts(HashMap<String, Burst> bursts) {
        this.bursts = bursts;
    }
    private static final Logger LOG = Logger.getLogger(SocketThread.class.getName());
    private static final int SERVERNAME_INDEX = 6;
}
