package net.midiandmore.jserv;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * SaslServ - SASL authentication service node (P10 short name: SA/SR)
 *
 * Implements the server-to-server SASL validation protocol used by JIRCd:
 *   <senderToken> SA <targetUserToken> <mechanism> <data>
 *
 * For mechanism PLAIN:
 *   data = base64(authzid\0authcid\0password)
 *
 * Replies:
 *   <thisServerToken> SR <targetUserToken> <OK|FAIL> [account] [:message]
 */
public final class SaslServ extends AbstractModule implements Software {

    private static final String MECH_PLAIN = "PLAIN";
    private static final String DEFAULT_RELAY_CONTROL_NICK = "N";
    private static final long DEFAULT_RELAY_TIMEOUT_MS = 2_000L;
    private static final String REMOTEAUTH_PREFIX = "REMOTEAUTH";

    private static final class PendingRelayAuth {
        private final String targetToken;
        private final String account;          // Original case-preserved account name
        private final String accountLower;     // Lowercase for matching
        private final long startedAtMs;

        private PendingRelayAuth(String targetToken, String account, String accountLower, long startedAtMs) {
            this.targetToken = targetToken;
            this.account = account;
            this.accountLower = accountLower;
            this.startedAtMs = startedAtMs;
        }
    }

    // account(lowercase) -> pending SASL request (lowercase key for matching with mIAuthd)
    private final Map<String, PendingRelayAuth> pendingRelay = new HashMap<>();

    private String resolveFromSaslConfig(String currentValue, String key) {
        if (currentValue != null && !currentValue.isBlank()) {
            return currentValue;
        }
        if (mi == null || mi.getConfig() == null || mi.getConfig().getSaslFile() == null) {
            return currentValue;
        }
        String v = mi.getConfig().getSaslFile().getProperty(key);
        return (v != null && !v.isBlank()) ? v : currentValue;
    }

    public SaslServ(JServ jserv, SocketThread socketThread, PrintWriter pw, BufferedReader br) {
        initialize(jserv, socketThread, pw, br);
    }

    @Override
    public String getModuleName() {
        return "SaslServ";
    }

    @Override
    public void handshake(String nick, String servername, String description, String numeric, String identd) {
        // Handshake like HostServ/SpamScan: register a service nick via P10 "N" line.
        if (!enabled) {
            getLogger().log(Level.WARNING, "SaslServ handshake called but module is disabled");
            return;
        }

        String resolvedNick = resolveFromSaslConfig(nick, "nick");
        String resolvedServername = resolveFromSaslConfig(servername, "servername");
        String resolvedDescription = resolveFromSaslConfig(description, "description");
        String resolvedIdentd = resolveFromSaslConfig(identd, "identd");

        if (resolvedNick == null || resolvedNick.isBlank()
                || resolvedServername == null || resolvedServername.isBlank()
                || resolvedDescription == null || resolvedDescription.isBlank()) {
            getLogger().log(Level.WARNING, "Cannot perform handshake for SaslServ: missing configuration (nick/servername/description)");
            return;
        }
        if (resolvedIdentd == null || resolvedIdentd.isBlank()) {
            resolvedIdentd = resolvedNick.toLowerCase();
        }

        this.numeric = numeric;
        getLogger().log(Level.INFO, "Registering SaslServ nick: {0}", resolvedNick);
        sendText("%s N %s 2 %d %s %s +oikrd - A:%d:666 U]AEB %s%s :%s",
                this.numeric,
                resolvedNick,
                time(),
                resolvedIdentd,
                resolvedServername,
                time(),
                this.numeric,
                getNumericSuffix(),
                resolvedDescription);
    }

    @Override
    public void parseLine(String text) {
        if (!enabled) {
            return;
        }

        expirePendingRelay();

        String line = text == null ? "" : text.trim();
        if (line.isEmpty()) {
            return;
        }

        // Handle relay responses first (P/O from control service)
        if (handleRelayResponse(line)) {
            return;
        }

        handleSaLine(line);
    }

    private void handleSaLine(String line) {

        // Format: <senderToken> SA <targetUserToken> <mechanism> <data> [<host>]
        // Note: Some uplinks may provide an additional host/userhost field after the SASL payload.
        String[] elem = line.split(" ");
        
        // First check if this is actually a SA command before doing anything else
        if (elem.length < 2 || !"SA".equals(elem[1])) {
            return;
        }
        
        getLogger().log(Level.INFO, "SaslServ received SA command: elem.length={0}", elem.length);
        
        if (elem.length < 5) {
            getLogger().log(Level.WARNING, "SaslServ: SA command too short (need at least 5 elements): {0}", line);
            return;
        }

        String senderToken = elem[0];
        String targetToken = elem[2];
        String mechanism = elem[3].toUpperCase();
        String data = elem[4];
        String requestHost = elem.length >= 6 ? elem[5] : null;
        String requestIdent = elem.length >= 7 ? elem[6] : null;

        if (!MECH_PLAIN.equals(mechanism)) {
            reply(targetToken, "FAIL", null, "Unsupported mechanism");
            return;
        }

        String authcid;
        String password;
        String authzid;
        try {
            byte[] decoded = Base64.getDecoder().decode(data);
            String authData = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = authData.split("\u0000", -1);
            if (parts.length != 3) {
                reply(targetToken, "FAIL", null, "Invalid PLAIN payload");
                return;
            }
            authzid = parts[0];
            authcid = parts[1];
            password = parts[2];
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "SASL(SA): Error decoding PLAIN payload", e);
            reply(targetToken, "FAIL", null, "Decode error");
            return;
        }

        if (authcid == null || authcid.isBlank()) {
            reply(targetToken, "FAIL", null, "Empty username");
            return;
        }

        // If relay mode is enabled, do not block waiting for response.
        // Instead, send remoteauth request and complete the SASL handshake when the control service answers.
        if (useRelay()) {
            boolean queued = queueRelayAuth(targetToken, authzid, authcid, password, requestHost, requestIdent);
            if (!queued) {
                reply(targetToken, "FAIL", null, "Relay unavailable");
            }
            return;
        }

        boolean ok;
        try {
            ok = authenticateUser(authcid, password);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, e, () -> "SASL(SA): Error while authenticating user " + authcid);
            reply(targetToken, "FAIL", null, "Internal error");
            return;
        }

        if (ok) {
            getLogger().log(Level.INFO, "SASL(SA): OK user={0} requester={1}", new Object[]{authcid, senderToken});
            reply(targetToken, "OK", buildAccountToken(authcid), null);
        } else {
            getLogger().log(Level.INFO, "SASL(SA): FAIL user={0} requester={1}", new Object[]{authcid, senderToken});
            reply(targetToken, "FAIL", null, "Authentication failed");
        }
    }

    private boolean useRelay() {
        if (mi == null || mi.getConfig() == null || mi.getConfig().getSaslFile() == null) {
            return false;
        }
        return "true".equalsIgnoreCase(mi.getConfig().getSaslFile().getProperty("use_relay", "false"));
    }

    private long relayTimeoutMs() {
        if (mi == null || mi.getConfig() == null || mi.getConfig().getSaslFile() == null) {
            return DEFAULT_RELAY_TIMEOUT_MS;
        }
        try {
            return Long.parseLong(mi.getConfig().getSaslFile().getProperty("relay_timeout_ms", Long.toString(DEFAULT_RELAY_TIMEOUT_MS)));
        } catch (Exception ignored) {
            return DEFAULT_RELAY_TIMEOUT_MS;
        }
    }

    private String relayControlNick() {
        if (mi == null || mi.getConfig() == null || mi.getConfig().getSaslFile() == null) {
            return DEFAULT_RELAY_CONTROL_NICK;
        }
        String v = mi.getConfig().getSaslFile().getProperty("relay_control_nick", DEFAULT_RELAY_CONTROL_NICK);
        return (v == null || v.isBlank()) ? DEFAULT_RELAY_CONTROL_NICK : v;
    }

    private Users findUserByToken(String userToken) {
        if (getSt() == null) {
            return null;
        }
        Map<String, Users> usersByToken = getSt().getUsers();
        if (usersByToken == null || userToken == null || userToken.isBlank()) {
            return null;
        }
        return usersByToken.get(userToken);
    }

    private static String pickDisplayNick(String authcid, Users targetUser) {
        if (targetUser != null) {
            String nick = targetUser.getNick();
            if (nick != null && !nick.isBlank()) {
                return nick;
            }
        }
        return authcid == null ? "" : authcid.trim();
    }

    private static String pickUserHost(String requestHost, Users targetUser) {
        String requestHostTrimmed = requestHost == null ? "" : requestHost.trim();
        if (!requestHostTrimmed.isBlank()) {
            return requestHostTrimmed;
        }
        if (targetUser != null) {
            String host = targetUser.getHost();
            if (host != null && !host.isBlank()) {
                return host;
            }
        }
        return "unknown";
    }

    private boolean queueRelayAuth(String targetToken, String authzid, String authcid, String password, String requestHost, String requestIdent) {
        if (getSt() == null) {
            return false;
        }

        String controlNumeric = getSt().getUserNumeric(relayControlNick());
        if (controlNumeric == null || controlNumeric.isBlank()) {
            getLogger().log(Level.WARNING, "SASL relay enabled but control nick is not present: {0}", relayControlNick());
            return false;
        }

        String accountKey = authcid == null ? "" : authcid.trim();
        if (accountKey.isBlank()) {
            return false;
        }
        
        String accountKeyLower = accountKey.toLowerCase();

        Users targetUser = findUserByToken(targetToken);
        String displayNick = pickDisplayNick(authcid, targetUser);
        String userHost = pickUserHost(requestHost, targetUser);

        String authzidDisplay = authzid != null && !authzid.isBlank() ? authzid : displayNick;

        long now = time();
        // Use lowercase for digest calculation to match mIAuthd expectations
        String hash = generateHashPass(accountKeyLower, password, Long.toString(now));

        // Mirrors mIAuthd's relay style: PRIVMSG to control service containing 'remoteauth ...'
        // Use lowercase account name for digest matching with mIAuthd
        // For display/logging fields (authzid/user/host), keep original nick case and real host.
        sendText("%s%s P %s :remoteauth %s %s %d %s %s %s", numeric, getNumericSuffix(), controlNumeric,
                accountKeyLower, hash, now, authzidDisplay, requestIdent, userHost);

        pendingRelay.put(accountKeyLower, new PendingRelayAuth(targetToken, accountKey, accountKeyLower, System.currentTimeMillis()));
        getLogger().log(Level.FINE, "SASL relay queued: account={0} (lower={1}) target={2}", new Object[]{accountKey, accountKeyLower, targetToken});
        return true;
    }

    private boolean handleRelayResponse(String line) {
        if (!useRelay()) {
            return false;
        }
        if (getSt() == null) {
            return false;
        }

        Privmsg msg = parsePrivmsg(line);
        if (msg == null) {
            return false;
        }

        String controlNumeric = getSt().getUserNumeric(relayControlNick());
        if (controlNumeric == null || controlNumeric.isBlank()) {
            return false;
        }
        if (!msg.from.equalsIgnoreCase(controlNumeric)) {
            return false;
        }

        // Extract the actual REMOTEAUTH message, removing any service prefix
        String remoteauthText = extractRemoteauthFromMessage(msg.text);
        getLogger().log(Level.INFO, "SASL relay response: extracted=[{0}] from=[{1}]", new Object[]{remoteauthText, msg.text});
        if (remoteauthText == null || !remoteauthText.startsWith(REMOTEAUTH_PREFIX)) {
            getLogger().log(Level.WARNING, "SASL relay: Could not extract REMOTEAUTH from message: {0}", msg.text);
            return false;
        }

        handleRemoteauthMessage(remoteauthText);
        return true;
    }

    private static final class Privmsg {
        private final String from;
        private final String text;

        private Privmsg(String from, String text) {
            this.from = from;
            this.text = text;
        }
    }

    private Privmsg parsePrivmsg(String line) {
        // Expect: <from> P|O <to> :<message>
        String[] elem = line.split(" ", 4);
        if (elem.length < 4) {
            return null;
        }
        String cmd = elem[1];
        if (!"P".equals(cmd) && !"O".equals(cmd)) {
            return null;
        }
        if (!elem[3].startsWith(":")) {
            return null;
        }
        return new Privmsg(elem[0], elem[3].substring(1));
    }

    /**
     * Extract REMOTEAUTH message from a line that may contain service prefixes.
     * Example: "$A$ From: SaslServ!...@.../A: REMOTEAUTH FAILREASON digest"
     * Returns: "REMOTEAUTH FAILREASON digest"
     */
    private String extractRemoteauthFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        
        // Check if message starts with REMOTEAUTH directly
        if (message.startsWith(REMOTEAUTH_PREFIX)) {
            return message;
        }
        
        // Look for REMOTEAUTH after service prefix patterns like "$A$ From: ... /A:"
        int idx = message.indexOf(REMOTEAUTH_PREFIX);
        if (idx > 0) {
            return message.substring(idx);
        }
        
        return null;
    }

    private void handleRemoteauthMessage(String message) {
        // Parse: REMOTEAUTH OK <account> <timestamp> <uid>
        //        REMOTEAUTH FAILREASON <reason>
        //        REMOTEAUTH FAIL <account>
        getLogger().log(Level.INFO, "SASL handleRemoteauthMessage: {0}", message);
        String[] parts = message.split(" ");
        getLogger().log(Level.INFO, "SASL parsed parts: length={0} parts={1}", new Object[]{parts.length, String.join("|", parts)});
        if (parts.length < 2 || !REMOTEAUTH_PREFIX.equalsIgnoreCase(parts[0])) {
            getLogger().log(Level.WARNING, "SASL: Invalid REMOTEAUTH message format");
            return;
        }

        if (parts.length >= 5 && "OK".equalsIgnoreCase(parts[1])) {
            getLogger().log(Level.INFO, "SASL: Handling OK response");
            handleRemoteauthOk(parts[2], parts[3], parts[4]);
        } else if (parts.length >= 2 && "FAILREASON".equalsIgnoreCase(parts[1])) {
            // FAILREASON doesn't include account name, fail the oldest pending request
            String reason = parts.length >= 3 ? parts[2] : "Unknown";
            getLogger().log(Level.INFO, "SASL: Handling FAILREASON: {0}", reason);
            handleRemoteauthFailReason(reason);
        } else if (parts.length >= 3 && "FAIL".equalsIgnoreCase(parts[1])) {
            // FAIL with account name
            getLogger().log(Level.INFO, "SASL: Handling FAIL for account: {0}", parts[2]);
            handleRemoteauthFail(parts[2]);
        } else {
            getLogger().log(Level.WARNING, "SASL: Unknown REMOTEAUTH response type: {0}", parts.length > 1 ? parts[1] : "<none>");
        }
    }

    private void handleRemoteauthOk(String accountRaw, String timestamp, String uid) {
        String account = accountRaw == null ? "" : accountRaw.toLowerCase();
        PendingRelayAuth pending = pendingRelay.remove(account);
        if (pending == null) {
            return;
        }
        // Use original case-preserved account name for the token
        String token = buildAccountTokenFromFields(pending.account, timestamp, uid);
        reply(pending.targetToken, "OK", token, null);
    }

    private void handleRemoteauthFail(String accountRaw) {
        String account = accountRaw == null ? "" : accountRaw.toLowerCase();
        PendingRelayAuth pending = pendingRelay.remove(account);
        if (pending == null) {
            return;
        }
        reply(pending.targetToken, "FAIL", null, "Authentication failed");
    }

    private void handleRemoteauthFailReason(String reason) {
        // FAILREASON doesn't specify which account failed.
        // If exactly one request is pending, fail it. Otherwise log a warning.
        getLogger().log(Level.INFO, "SASL handleRemoteauthFailReason: reason={0} pendingCount={1}", new Object[]{reason, pendingRelay.size()});
        if (pendingRelay.isEmpty()) {
            getLogger().log(Level.WARNING, "SASL: Received FAILREASON but no pending requests");
            return;
        }
        
        if (pendingRelay.size() == 1) {
            Map.Entry<String, PendingRelayAuth> entry = pendingRelay.entrySet().iterator().next();
            PendingRelayAuth pending = entry.getValue();
            pendingRelay.remove(entry.getKey());
            String msg = reason != null && !reason.isBlank() ? "Authentication failed: " + reason : "Authentication failed";
            getLogger().log(Level.INFO, "SASL relay FAILREASON: account={0} reason={1} calling reply now...", new Object[]{entry.getKey(), reason});
            try {
                reply(pending.targetToken, "FAIL", null, msg);
                getLogger().log(Level.INFO, "SASL relay FAILREASON: reply() completed successfully");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "SASL relay FAILREASON: Exception in reply()", e);
            }
        } else {
            // Multiple pending requests - fail the oldest one
            PendingRelayAuth oldest = null;
            String oldestKey = null;
            for (Map.Entry<String, PendingRelayAuth> entry : pendingRelay.entrySet()) {
                if (oldest == null || entry.getValue().startedAtMs < oldest.startedAtMs) {
                    oldest = entry.getValue();
                    oldestKey = entry.getKey();
                }
            }
            if (oldest != null && oldestKey != null) {
                pendingRelay.remove(oldestKey);
                String msg = reason != null && !reason.isBlank() ? "Authentication failed: " + reason : "Authentication failed";
                reply(oldest.targetToken, "FAIL", null, msg);
                getLogger().log(Level.WARNING, "SASL relay FAILREASON with multiple pending requests ({0} total). Failed oldest: account={1} reason={2}",
                        new Object[]{pendingRelay.size() + 1, oldestKey, reason});
            }
        }
    }

    private void expirePendingRelay() {
        if (pendingRelay.isEmpty()) {
            return;
        }
        long timeout = relayTimeoutMs();
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PendingRelayAuth>> it = pendingRelay.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PendingRelayAuth> e = it.next();
            PendingRelayAuth p = e.getValue();
            if ((now - p.startedAtMs) > timeout) {
                it.remove();
                reply(p.targetToken, "FAIL", null, "Relay timeout");
            }
        }
    }

    private static String generateHashPass(String username, String password, String junk) {
        String u = username == null ? "" : username;
        String p = password == null ? "" : password;
        String buf = "%s %s%s".formatted(u, p, junk != null && !junk.isBlank() ? " " + junk : "");
        MessageDigest md5 = DigestUtils.getMd5Digest();
        md5.update(buf.getBytes(StandardCharsets.UTF_8));
        return Hex.encodeHexString(md5.digest());
    }

    private String buildAccountTokenFromFields(String username, String timestamp, String uid) {
        if (username == null || username.isBlank()) {
            return username;
        }
        if (timestamp != null && timestamp.matches("\\d+") && uid != null && uid.matches("\\d+")) {
            return username + ":" + timestamp + ":" + uid;
        }
        return username;
    }

    @Override
    public void registerBurstChannels(java.util.HashMap<String, Burst> bursts, String serverNumeric) {
        if (!enabled) {
            return;
        }
        // Join the ops/service channel like HostServ does.
        String channel = "#twilightzone";
        if (!bursts.containsKey(channel.toLowerCase())) {
            bursts.put(channel.toLowerCase(), new Burst(channel));
        }
        bursts.get(channel.toLowerCase()).getUsers().add(serverNumeric + getNumericSuffix());
        getLogger().log(Level.INFO, "SaslServ registered burst channel: {0}", channel);
    }

    private boolean authenticateUser(String username, String password) {
        // Prefer DB auth by default (chanserv.users password column).
        boolean useDb = true;
        boolean allowConfigFallback = false;
        if (mi != null && mi.getConfig() != null && mi.getConfig().getSaslFile() != null) {
            useDb = "true".equalsIgnoreCase(mi.getConfig().getSaslFile().getProperty("use_db", "true"));
            allowConfigFallback = "true".equalsIgnoreCase(mi.getConfig().getSaslFile().getProperty("allow_config_fallback", "false"));
        }

        if (useDb && mi != null && mi.getDb() != null) {
            boolean ok = mi.getDb().isRegistered(username, password);
            if (ok) {
                return true;
            }
            if (!allowConfigFallback) {
                return false;
            }
        }

        if (mi != null && mi.getConfig() != null && mi.getConfig().getSaslFile() != null) {
            String configPassword = mi.getConfig().getSaslFile().getProperty("ACCOUNT_" + username);
            return configPassword != null && configPassword.equals(password);
        }

        return false;
    }

    /**
     * Builds an account token compatible with JIRCd's SASL schema.
     *
     * JIRCd accepts either plain account name or "account:timestamp:id".
     * If DB contains usable values for "created" and "id", return the extended token.
     */
    private String buildAccountToken(String username) {
        if (username == null || username.isBlank()) {
            return username;
        }

        if (mi != null && mi.getDb() != null) {
            try {
                String created = Long.toString(System.currentTimeMillis() / 1000);
                String id = mi.getDb().getData("id", username);
                if (created != null && !created.isBlank() && created.matches("\\d+")
                        && id != null && !id.isBlank() && id.matches("\\d+")) {
                    return username + ":" + created + ":" + id;
                }
            } catch (Exception e) {
                // Keep backward compatible: fall back to plain username.
                getLogger().log(Level.FINE, e, () -> "SASL: Could not build extended account token for " + username);
            }
        }

        return username;
    }

    private void reply(String targetToken, String result, String account, String message) {
        String sender = (mi != null && mi.getConfig() != null && mi.getConfig().getConfigFile() != null)
                ? mi.getConfig().getConfigFile().getProperty("numeric")
                : null;
        if (sender == null || sender.isBlank()) {
            sender = (numeric != null && !numeric.isBlank()) ? numeric : "*";
        }

        StringBuilder line = new StringBuilder();
        line.append(sender).append(" SR ").append(targetToken).append(" ").append(result);
        if (account != null && !account.isBlank()) {
            line.append(" ").append(account);
        }
        if (message != null && !message.isBlank()) {
            line.append(" :").append(message);
        }
        getLogger().log(Level.INFO, "SASL reply: targetToken={0} result={1} account={2} message={3} line=[{4}]",
                new Object[]{targetToken, result, account, message, line.toString()});
        sendText("%s", line);
    }

    @Override
    public void shutdown() {
        // Nothing to do.
    }
}
