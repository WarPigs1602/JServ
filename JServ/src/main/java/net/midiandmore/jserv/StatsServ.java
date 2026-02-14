/*
 * StatsServ - Channel Statistics Service
 * Provides IRC channel statistics using the a4stats schema
 * Based on a4stats Lua module by Gunnar Beutner
 */
package net.midiandmore.jserv;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * StatsServ Module - Channel Statistics and Rankings
 * 
 * Privacy Levels:
 * 0 = Public (visible to everyone)
 * 1 = Members Only (visible only to users in the channel)
 * 2 = ChanServ Only (only ChanServ can view)
 * 
 * @author Andreas Pschorn
 */
public final class StatsServ extends AbstractModule implements Software {

    private static final int PRIVACY_PUBLIC = 0;
    private static final int PRIVACY_MEMBERS = 1;
    private static final int PRIVACY_CHANSERV = 2;
    
    // Cleanup constants (from C a4stats module)
    private static final int CLEANUP_KEEP = 10; // keep this many topics and kicks per channel
    private static final long CLEANUP_INTERVAL = 86400000; // 24 hours in milliseconds
    private static final int CLEANUP_INACTIVE_DAYS = 30; // disable channels where nothing happened for this many days
    private static final int CLEANUP_DELETE_DAYS = 5; // delete data for channels disabled for this many days
    
    private Timer cleanupTimer;
    
    // Smiley patterns
    private static final String[] HAPPY_SMILEYS = {":)", ":-)", ":p", ":-p", ":P", ":-P", ":D", ":-D", ":}", ":-}", ":]", ":-]", ";)", ";-)", ";p", ";-p", ";P", ";-P", ";D", ";-D", ";}", ";-}", ";]", ";-]"};
    private static final String[] SAD_SMILEYS = {":(", ":-(", ":c", ":-c", ":C", ":-C", ":[", ":-[", ":{", ":-{", ";(", ";-(", ";c", ";-c", ";C", ";-C", ";[", ";-[", ";{", ";-{"};
    
    // Foul words
    private static final String[] FOUL_WORDS = {"fuck", "bitch", "shit", "cock", "dick", "stfu", "idiot", "moron", "cunt", "fag", "nigger", "prick", "retard", "twat", "wanker", "bastard", "fick", "schlampe", "hure", "schwuchtel", "fotz", "wichs", "wix"};
    
    // Channel state tracking
    private final Map<String, ChannelState> channelStates = new ConcurrentHashMap<>();
    
    // Channel ID cache (channelName.toLowerCase() -> channelId)
    private final Map<String, Integer> channelIdCache = new ConcurrentHashMap<>();

    public StatsServ(JServ jserv, SocketThread socketThread, PrintWriter pw, BufferedReader br) {
        initialize(jserv, socketThread, pw, br);
    }

    @Override
    public String getModuleName() {
        return "StatsServ";
    }

    @Override
    public void shutdown() {
        if (cleanupTimer != null) {
            cleanupTimer.cancel();
            cleanupTimer = null;
        }
        getLogger().log(Level.INFO, "StatsServ shutting down");
    }

    @Override
    public void handshake(String nick, String servername, String description, String numeric, String identd) {
        if (!enabled) {
            getLogger().log(Level.WARNING, "StatsServ handshake called but module is disabled");
            return;
        }

        if (nick == null || nick.isBlank() || servername == null || servername.isBlank() 
                || description == null || description.isBlank()) {
            getLogger().log(Level.WARNING, "Cannot perform handshake for StatsServ: missing configuration");
            return;
        }
        
        if (identd == null || identd.isBlank()) {
            identd = nick.toLowerCase();
        }

        this.numeric = numeric;
        getLogger().log(Level.INFO, "Registering StatsServ nick: {0}", nick);
        sendText("%s N %s 2 %d %s %s +oikr - %s U]AEB %s%s :%s",
                this.numeric,
                nick,
                time(),
                identd,
                servername,
                nick,
                this.numeric,
                getNumericSuffix(),
                description);
    }

    @Override
    public void registerBurstChannels(HashMap<String, Burst> bursts, String serverNumeric) {
        if (!enabled) {
            return;
        }
        
        // Load and join all channels from a4stats database
        String sql = "SELECT id, name FROM a4stats.channels WHERE active = 1 ORDER BY name";
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            int count = 0;
            while (rs.next()) {
                int channelId = rs.getInt("id");
                String channelName = rs.getString("name");
                if (channelName != null && channelName.startsWith("#")) {
                    String chanLower = channelName.toLowerCase();
                    channelIdCache.put(chanLower, channelId);
                    if (!bursts.containsKey(chanLower)) {
                        bursts.put(chanLower, new Burst(channelName));
                    }
                    
                    Burst burst = bursts.get(chanLower);
                    if (!burst.getUsers().contains(serverNumeric + getNumericSuffix())) {
                        burst.getUsers().add(serverNumeric + getNumericSuffix());
                        // Initialize channel state
                        channelStates.put(chanLower, new ChannelState());
                        count++;
                    }
                }
            }
            
            if (count > 0) {
                getLogger().log(Level.INFO, "StatsServ registered {0} channels from a4stats database", count);
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to load channels from a4stats database", e);
        }
        
        // Start cleanup timer
        startCleanupTimer();
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

        String command = elem[1];
        
        // Handle different IRC events
        switch (command) {
            case "P": // Private message to StatsServ (not channel messages)
                if (elem.length >= 4 && !elem[2].startsWith("#") && elem[2].equals(getNumeric() + getNumericSuffix())) {
                    handlePrivateMessage(line, elem);
                }
                break;
            case "J": // JOIN
                if (elem.length >= 3) {
                    handleJoin(elem[0], elem[2]);
                }
                break;
            case "L": // PART
                if (elem.length >= 3) {
                    String partMsg = extractMessage(line, 3);
                    handlePart(elem[0], elem[2], partMsg);
                }
                break;
            case "K": // KICK
                if (elem.length >= 4) {
                    String kickMsg = extractMessage(line, 4);
                    handleKick(elem[2], elem[3], elem[0], kickMsg);
                }
                break;
            case "T": // TOPIC
                if (elem.length >= 3) {
                    String topic = extractMessage(line, 3);
                    handleTopic(elem[2], elem[0], topic);
                }
                break;
            case "M": // MODE
                if (elem.length >= 4) {
                    handleMode(elem[2], elem[3], elem);
                }
                break;
            case "Q": // QUIT
                handleQuit(elem[0]);
                break;
            case "D": // KILL
                if (elem.length >= 3) {
                    handleQuit(elem[2]); // elem[2] is the target being killed
                }
                break;
            case "N": // NICK change (when user already exists)
                if (elem.length >= 3 && elem[2].length() > 0) {
                    handleNick(elem[0], elem[2]);
                }
                break;
        }
        
        // Check for channel messages (format: NUMERIC P #channel :message)
        if ("P".equals(command) && elem.length >= 4 && elem[2].startsWith("#")) {
            String channel = elem[2];
            String message = extractMessage(line, 3);
            
            // Check if it's a CTCP ACTION (/me command)
            // ACTION format: \x01ACTION message\x01
            if (message.startsWith("\u0001ACTION ")) {
                // Strip CTCP markers and treat as action
                int endPos = message.lastIndexOf('\u0001');
                if (endPos > 8) {
                    message = message.substring(8, endPos); // Remove \x01ACTION and trailing \x01
                } else {
                    message = message.substring(8); // No trailing \x01
                }
                handleAction(elem[0], channel, message);
            } else {
                // Regular channel message (ignore other CTCP commands)
                if (!message.startsWith("\u0001")) {
                    handleChannelMessage(elem[0], channel, message);
                }
            }
        }
    }
    
    /**
     * Extract message from line starting at given position
     */
    private String extractMessage(String line, int position) {
        String[] parts = line.split(" ", position + 1);
        if (parts.length > position) {
            String msg = parts[position];
            return msg.startsWith(":") ? msg.substring(1) : msg;
        }
        return "";
    }
    
    /**
     * Strip IRC control codes (colors, bold, underline, etc.)
     * Removes: \x02 (bold), \x1F (underline), \x16 (reverse), \x0F (reset)
     *          \x03[0-9]{1,2}(,[0-9]{1,2})? (colors)
     */
    private String stripControlCodes(String text) {
        if (text == null) return "";
        
        // Remove color codes (\x03 followed by optional numbers)
        text = text.replaceAll("\\x03[0-9]{1,2}(,[0-9]{1,2})?", "");
        text = text.replaceAll("\u0003[0-9]{1,2}(,[0-9]{1,2})?", "");
        
        // Remove formatting codes
        text = text.replaceAll("[\\x02\\x1F\\x16\\x0F]", ""); // \x02=bold, \x1F=underline, \x16=reverse, \x0F=reset
        text = text.replaceAll("[\u0002\u001F\u0016\u000F]", "");
        
        return text;
    }

    /**
     * Handle private messages sent to StatsServ
     */
    private void handlePrivateMessage(String line, String[] elem) {
        String senderNumeric = elem[0];
        String[] messageParts = line.split(":", 2);
        if (messageParts.length < 2) {
            return;
        }

        String message = messageParts[1].trim();
        String[] cmdParts = message.split("\\s+");
        if (cmdParts.length == 0) {
            return;
        }

        String command = cmdParts[0].toUpperCase();
        
        switch (command) {
            case "STATS":
                handleStats(senderNumeric, cmdParts);
                break;
            case "TOP10":
            case "TOP":
                handleTop10(senderNumeric, cmdParts);
                break;
            case "CHSTATS":
                handleChannelStats(senderNumeric, cmdParts);
                break;
            case "QUOTE":
                handleQuote(senderNumeric, cmdParts);
                break;
            case "PRIVACY":
                handlePrivacy(senderNumeric, cmdParts);
                break;
            case "ADDCHAN":
                handleAddChannel(senderNumeric, cmdParts);
                break;
            case "DELCHAN":
                handleDelChannel(senderNumeric, cmdParts);
                break;
            case "HELP":
                handleHelp(senderNumeric);
                break;
            default:
                sendNotice(senderNumeric, "Unknown command. Use HELP for available commands.");
                break;
        }
    }

    /**
     * Handle STATS command - Show user statistics for a channel
     */
    private void handleStats(String senderNumeric, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Usage: STATS <#channel> [nickname]");
            return;
        }

        String channel = parts[1];
        String targetNick = parts.length > 2 ? parts[2] : getNickFromNumeric(senderNumeric);
        
        if (!channel.startsWith("#")) {
            sendNotice(senderNumeric, "Invalid channel name.");
            return;
        }

        // Check privacy settings
        int privacy = getChannelPrivacy(channel);
        if (!canViewStats(senderNumeric, channel, privacy)) {
            sendNotice(senderNumeric, "Statistics for " + channel + " are not public.");
            return;
        }

        // Get user stats
        UserStats stats = getUserStats(channel, targetNick);
        if (stats == null) {
            sendNotice(senderNumeric, "No statistics found for " + targetNick + " in " + channel);
            return;
        }

        // Send statistics
        sendNotice(senderNumeric, "=== Statistics for " + targetNick + " in " + channel + " ===");
        sendNotice(senderNumeric, String.format("Lines: %d | Words: %d | Characters: %d", 
            stats.lines, stats.words, stats.chars));
        sendNotice(senderNumeric, String.format("Rating: %d | Actions: %d", 
            stats.rating, stats.actions));
        sendNotice(senderNumeric, String.format("Questions: %d | Yelling: %d | Caps: %d", 
            stats.questions, stats.yelling, stats.caps));
        sendNotice(senderNumeric, String.format("Mood: Happy=%d Sad=%d", 
            stats.moodHappy, stats.moodSad));
        sendNotice(senderNumeric, String.format("Kicks given: %d | Kicked: %d", 
            stats.kicks, stats.kicked));
        if (stats.quote != null && !stats.quote.isEmpty()) {
            sendNotice(senderNumeric, "Quote: " + stats.quote);
        }
    }

    /**
     * Handle TOP10 command - Show top 10 users in channel
     */
    private void handleTop10(String senderNumeric, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Usage: TOP10 <#channel> [lines|words|chars]");
            return;
        }

        String channel = parts[1];
        String sortBy = parts.length > 2 ? parts[2].toLowerCase() : "lines";
        
        if (!channel.startsWith("#")) {
            sendNotice(senderNumeric, "Invalid channel name.");
            return;
        }

        // Check privacy settings
        int privacy = getChannelPrivacy(channel);
        if (!canViewStats(senderNumeric, channel, privacy)) {
            sendNotice(senderNumeric, "Statistics for " + channel + " are not public.");
            return;
        }

        // Get top 10 users
        List<TopUser> topUsers = getTop10Users(channel, sortBy);
        if (topUsers.isEmpty()) {
            sendNotice(senderNumeric, "No statistics found for " + channel);
            return;
        }

        sendNotice(senderNumeric, "=== Top 10 users in " + channel + " (by " + sortBy + ") ===");
        int rank = 1;
        for (TopUser user : topUsers) {
            sendNotice(senderNumeric, String.format("%d. %s - Lines: %d, Words: %d, Chars: %d", 
                rank++, user.account, user.lines, user.words, user.chars));
        }
    }

    /**
     * Handle CHSTATS command - Show channel statistics
     */
    private void handleChannelStats(String senderNumeric, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Usage: CHSTATS <#channel>");
            return;
        }

        String channel = parts[1];
        
        if (!channel.startsWith("#")) {
            sendNotice(senderNumeric, "Invalid channel name.");
            return;
        }

        // Check privacy settings
        int privacy = getChannelPrivacy(channel);
        if (!canViewStats(senderNumeric, channel, privacy)) {
            sendNotice(senderNumeric, "Statistics for " + channel + " are not public.");
            return;
        }

        ChannelStats stats = getChannelStats(channel);
        if (stats == null) {
            sendNotice(senderNumeric, "No statistics found for " + channel);
            return;
        }

        sendNotice(senderNumeric, "=== Channel Statistics for " + channel + " ===");
        sendNotice(senderNumeric, String.format("Total users: %d | Active: %s", 
            stats.totalUsers, stats.active ? "Yes" : "No"));
        sendNotice(senderNumeric, String.format("Privacy: %s", getPrivacyName(stats.privacy)));
        sendNotice(senderNumeric, String.format("Total lines: %d | Total words: %d", 
            stats.totalLines, stats.totalWords));
    }

    /**
     * Handle QUOTE command - Show random quote from user
     */
    private void handleQuote(String senderNumeric, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Usage: QUOTE <#channel> [nickname]");
            return;
        }

        String channel = parts[1];
        String targetNick = parts.length > 2 ? parts[2] : null;
        
        if (!channel.startsWith("#")) {
            sendNotice(senderNumeric, "Invalid channel name.");
            return;
        }

        // Check privacy settings
        int privacy = getChannelPrivacy(channel);
        if (!canViewStats(senderNumeric, channel, privacy)) {
            sendNotice(senderNumeric, "Statistics for " + channel + " are not public.");
            return;
        }

        String quote = getRandomQuote(channel, targetNick);
        if (quote == null || quote.isEmpty()) {
            sendNotice(senderNumeric, "No quote available.");
            return;
        }

        sendNotice(senderNumeric, "Quote: " + quote);
    }

    /**
     * Handle PRIVACY command - Set channel privacy level (ChanServ only)
     */
    private void handlePrivacy(String senderNumeric, String[] parts) {
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Usage: PRIVACY <#channel> <0|1|2>");
            sendNotice(senderNumeric, "0=Public, 1=Members Only, 2=ChanServ Only");
            return;
        }

        String channel = parts[1];
        int privacyLevel;
        
        try {
            privacyLevel = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            sendNotice(senderNumeric, "Invalid privacy level. Use 0, 1, or 2.");
            return;
        }

        if (privacyLevel < 0 || privacyLevel > 2) {
            sendNotice(senderNumeric, "Invalid privacy level. Use 0, 1, or 2.");
            return;
        }

        // Check if user has permission (should be channel owner/master or Oper)
        if (!hasChannelPermissionOrOper(senderNumeric, channel)) {
            sendNotice(senderNumeric, "You don't have permission to change privacy settings.");
            return;
        }

        if (setChannelPrivacy(channel, privacyLevel)) {
            sendNotice(senderNumeric, "Privacy level for " + channel + " set to " + getPrivacyName(privacyLevel));
        } else {
            sendNotice(senderNumeric, "Failed to set privacy level.");
        }
    }

    /**
     * Handle HELP command
     */
    private void handleHelp(String senderNumeric) {
        sendNotice(senderNumeric, "=== StatsServ Help ===");
        sendNotice(senderNumeric, "STATS <#channel> [nick] - Show user statistics");
        sendNotice(senderNumeric, "TOP10 <#channel> [lines|words|chars] - Show top 10 users");
        sendNotice(senderNumeric, "CHSTATS <#channel> - Show channel statistics");
        sendNotice(senderNumeric, "QUOTE <#channel> [nick] - Show random quote");
        sendNotice(senderNumeric, "PRIVACY <#channel> <0|1|2> - Set privacy level");
        sendNotice(senderNumeric, "  0=Public, 1=Members Only, 2=ChanServ Only");
        sendNotice(senderNumeric, "ADDCHAN <#channel> [0|1|2] - Add channel to statistics tracking");
        sendNotice(senderNumeric, "DELCHAN <#channel> - Remove channel from statistics tracking");
    }

    /**
     * Handle ADDCHAN command - Add channel to statistics tracking
     */
    private void handleAddChannel(String senderNumeric, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Usage: ADDCHAN <#channel> [0|1|2]");
            sendNotice(senderNumeric, "Privacy: 0=Public, 1=Members Only, 2=ChanServ Only");
            return;
        }

        String channel = parts[1];
        int privacy = PRIVACY_PUBLIC; // Default
        
        // Parse optional privacy parameter
        if (parts.length >= 3) {
            try {
                privacy = Integer.parseInt(parts[2]);
                if (privacy < 0 || privacy > 2) {
                    sendNotice(senderNumeric, "Invalid privacy level. Use 0, 1, or 2.");
                    return;
                }
            } catch (NumberFormatException e) {
                sendNotice(senderNumeric, "Invalid privacy level. Use 0, 1, or 2.");
                return;
            }
        }
        
        if (!channel.startsWith("#")) {
            sendNotice(senderNumeric, "Invalid channel name.");
            return;
        }

        // Check if user has permission (Oper or Channel Op)
        if (!hasChannelPermissionOrOper(senderNumeric, channel)) {
            sendNotice(senderNumeric, "You don't have permission to add this channel.");
            return;
        }

        // Check if channel already exists
        if (channelExists(channel)) {
            sendNotice(senderNumeric, "Channel " + channel + " is already being tracked.");
            return;
        }

        // Add channel to database with specified privacy
        if (addChannel(channel, privacy)) {
            // Initialize channel state
            channelStates.put(channel.toLowerCase(), new ChannelState());
            
            // Join the channel
            getSt().joinChannel(channel, this.numeric, this.numeric + getNumericSuffix());
            
            sendNotice(senderNumeric, "Channel " + channel + " has been added with privacy level " + getPrivacyName(privacy) + ".");
            getLogger().info("Channel " + channel + " added by " + getNickFromNumeric(senderNumeric) + " with privacy " + privacy);
        } else {
            sendNotice(senderNumeric, "Failed to add channel to database.");
        }
    }

    /**
     * Handle DELCHAN command - Remove channel from statistics tracking
     */
    private void handleDelChannel(String senderNumeric, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Usage: DELCHAN <#channel>");
            return;
        }

        String channel = parts[1];
        
        if (!channel.startsWith("#")) {
            sendNotice(senderNumeric, "Invalid channel name.");
            return;
        }

        // Check if user has permission (channel owner/master or Oper)
        if (!hasChannelPermissionOrOper(senderNumeric, channel)) {
            sendNotice(senderNumeric, "You don't have permission to remove this channel.");
            return;
        }

        // Check if channel exists
        if (!channelExists(channel)) {
            sendNotice(senderNumeric, "Channel " + channel + " is not being tracked.");
            return;
        }

        // Part the channel first
        getSt().partChannel(channel, this.numeric, getNumericSuffix());
        
        // Remove channel from database
        if (deleteChannel(channel)) {
            // Remove channel state
            channelStates.remove(channel.toLowerCase());
            sendNotice(senderNumeric, "Channel " + channel + " has been removed from statistics tracking.");
            getLogger().info("Channel " + channel + " removed by " + getNickFromNumeric(senderNumeric));
        } else {
            sendNotice(senderNumeric, "Failed to remove channel from database.");
        }
    }

    // Database access methods

    /**
     * Check if channel exists in database
     */
    private boolean channelExists(String channelName) {
        String sql = "SELECT 1 FROM a4stats.channels WHERE LOWER(name) = LOWER(?)";
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, channelName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to check if channel exists", e);
            return false;
        }
    }

    /**
     * Add channel to database
     */
    private boolean addChannel(String channelName, int privacy) {
        String sql = "INSERT INTO a4stats.channels (name, privacy, timestamp) VALUES (?, ?, ?) RETURNING id";
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, channelName);
            stmt.setInt(2, privacy);
            stmt.setInt(3, (int) time());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int channelId = rs.getInt("id");
                    // Add to cache
                    channelIdCache.put(channelName.toLowerCase(), channelId);
                    getLogger().log(Level.INFO, "Added channel {0} with ID {1}", new Object[]{channelName, channelId});
                    return true;
                }
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to add channel", e);
            return false;
        }
        return false;
    }

    /**
     * Delete channel and all associated data from database
     */
    private boolean deleteChannel(String channelName) {
        // Remove from cache first
        channelIdCache.remove(channelName.toLowerCase());
        // Get channel id first
        String getIdSql = "SELECT id FROM a4stats.channels WHERE LOWER(name) = LOWER(?)";
        String deleteUsersSql = "DELETE FROM a4stats.users WHERE channelid = ?";
        String deleteKicksSql = "DELETE FROM a4stats.kicks WHERE channelid = ?";
        String deleteRelationsSql = "DELETE FROM a4stats.relations WHERE channelid = ?";
        String deleteTopicsSql = "DELETE FROM a4stats.topics WHERE channelid = ?";
        String deleteChannelSql = "DELETE FROM a4stats.channels WHERE id = ?";
        
        try (Connection conn = mi.getDb().getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get channel id
                int channelId;
                try (PreparedStatement stmt = conn.prepareStatement(getIdSql)) {
                    stmt.setString(1, channelName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        channelId = rs.getInt("id");
                    }
                }
                
                // Delete in correct order (child tables first)
                try (PreparedStatement stmt = conn.prepareStatement(deleteUsersSql)) {
                    stmt.setInt(1, channelId);
                    stmt.executeUpdate();
                }
                
                try (PreparedStatement stmt = conn.prepareStatement(deleteKicksSql)) {
                    stmt.setInt(1, channelId);
                    stmt.executeUpdate();
                }
                
                try (PreparedStatement stmt = conn.prepareStatement(deleteRelationsSql)) {
                    stmt.setInt(1, channelId);
                    stmt.executeUpdate();
                }
                
                try (PreparedStatement stmt = conn.prepareStatement(deleteTopicsSql)) {
                    stmt.setInt(1, channelId);
                    stmt.executeUpdate();
                }
                
                // Finally delete channel
                try (PreparedStatement stmt = conn.prepareStatement(deleteChannelSql)) {
                    stmt.setInt(1, channelId);
                    stmt.executeUpdate();
                }
                
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to delete channel", e);
            return false;
        }
    }

    /**
     * Get channel privacy level
     */
    private int getChannelPrivacy(String channelName) {
        String sql = "SELECT privacy FROM a4stats.channels WHERE LOWER(name) = LOWER(?)";
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, channelName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("privacy");
                }
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to get channel privacy", e);
        }
        return PRIVACY_PUBLIC; // Default to public
    }

    /**
     * Set channel privacy level
     */
    private boolean setChannelPrivacy(String channelName, int privacy) {
        String sql = "UPDATE a4stats.channels SET privacy = ? WHERE LOWER(name) = LOWER(?)";
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, privacy);
            stmt.setString(2, channelName);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to set channel privacy", e);
            return false;
        }
    }

    /**
     * Check if user can view statistics based on privacy level
     */
    private boolean canViewStats(String userNumeric, String channel, int privacy) {
        switch (privacy) {
            case PRIVACY_PUBLIC:
                return true;
            case PRIVACY_MEMBERS:
                return isUserInChannel(userNumeric, channel);
            case PRIVACY_CHANSERV:
                // Only ChanServ or channel operators can view
                return hasChannelPermission(userNumeric, channel);
            default:
                return false;
        }
    }

    /**
     * Check if user is in the channel
     */
    private boolean isUserInChannel(String userNumeric, String channel) {
        // Check if user is currently in the channel
        if (st != null && st.getChannel() != null) {
            Channel ch = st.getChannel().get(channel.toLowerCase());
            if (ch != null && ch.getUsers() != null) {
                return ch.getUsers().stream().anyMatch(u -> u.equals(userNumeric));
            }
        }
        return false;
    }

    /**
     * Check if user has permission (is Oper or has channel permission)
     */
    private boolean hasChannelPermissionOrOper(String userNumeric, String channel) {
        Users user = st.getUsers().get(userNumeric);
        if (user == null) {
            getLogger().log(Level.WARNING, "hasChannelPermissionOrOper: User not found for numeric: " + userNumeric);
            return false;
        }
        
        // Check if user is an Oper
        boolean isOper = isOperAccount(user);
        getLogger().log(Level.INFO, "hasChannelPermissionOrOper: User " + user.getNick() + " isOper=" + isOper + " account=" + user.getAccount());
        if (isOper) {
            return true;
        }
        
        // Otherwise check channel permissions
        boolean hasChannelPerm = hasChannelPermission(userNumeric, channel);
        getLogger().log(Level.INFO, "hasChannelPermissionOrOper: User " + user.getNick() + " hasChannelPerm=" + hasChannelPerm);
        return hasChannelPerm;
    }

    /**
     * Check if user is an IRC Operator or has privileged flags
     */
    private boolean isOperAccount(Users user) {
        if (user == null) {
            return false;
        }
        
        // Check IRC Oper mode
        if (user.isOper()) {
            getLogger().log(Level.INFO, "isOperAccount: User " + user.getNick() + " has IRC oper mode");
            return true;
        }
        
        // Check if user has privileged flags in database (STAFF, OPER, ADMIN, DEV)
        String account = user.getAccount();
        if (account != null && !account.isEmpty()) {
            int userFlags = mi.getDb().getFlags(account);
            getLogger().log(Level.INFO, "isOperAccount: User " + user.getNick() + " account=" + account + " flags=" + userFlags);
            if (Userflags.hasFlag(userFlags, Userflags.Flag.STAFF) ||
                Userflags.hasFlag(userFlags, Userflags.Flag.OPER) ||
                Userflags.hasFlag(userFlags, Userflags.Flag.ADMIN) ||
                Userflags.hasFlag(userFlags, Userflags.Flag.DEV)) {
                getLogger().log(Level.INFO, "isOperAccount: User has privileged flag");
                return true;
            }
        } else {
            getLogger().log(Level.INFO, "isOperAccount: User " + user.getNick() + " has no account");
        }
        
        return false;
    }

    /**
     * Check if user has channel permission (owner/master)
     */
    private boolean hasChannelPermission(String userNumeric, String channel) {
        // Get account name
        if (st == null || st.getUsers() == null) {
            return false;
        }
        
        Users user = st.getUsers().get(userNumeric);
        if (user == null || user.getAccount() == null) {
            return false;
        }

        // Check if user is channel owner/master via ChanServ
        String sql = "SELECT flags FROM chanserv.chanusers cu " +
                    "JOIN chanserv.channels c ON cu.channelid = c.id " +
                    "WHERE LOWER(c.name) = LOWER(?) AND cu.userid = " +
                    "(SELECT id FROM chanserv.users WHERE LOWER(username) = LOWER(?))";
        
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, channel);
            stmt.setString(2, user.getAccount());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long flags = rs.getLong("flags");
                    // Check for owner (+n) or master (+m) flag
                    return (flags & 0x800000) != 0 || (flags & 0x400000) != 0;
                }
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to check channel permission", e);
        }
        return false;
    }

    /**
     * Get user statistics from database
     */
    private UserStats getUserStats(String channelName, String nickname) {
        String sql = "SELECT u.* FROM a4stats.users u " +
                    "JOIN a4stats.channels c ON u.channelid = c.id " +
                    "WHERE LOWER(c.name) = LOWER(?) AND LOWER(u.account) = LOWER(?)";
        
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, channelName);
            stmt.setString(2, nickname);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    UserStats stats = new UserStats();
                    stats.lines = rs.getInt("lines");
                    stats.words = rs.getInt("words");
                    stats.chars = rs.getInt("chars");
                    stats.rating = rs.getInt("rating");
                    stats.actions = rs.getInt("actions");
                    stats.questions = rs.getInt("questions");
                    stats.yelling = rs.getInt("yelling");
                    stats.caps = rs.getInt("caps");
                    stats.moodHappy = rs.getInt("mood_happy");
                    stats.moodSad = rs.getInt("mood_sad");
                    stats.kicks = rs.getInt("kicks");
                    stats.kicked = rs.getInt("kicked");
                    stats.quote = rs.getString("quote");
                    return stats;
                }
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to get user stats", e);
        }
        return null;
    }

    /**
     * Get top 10 users for channel
     */
    private List<TopUser> getTop10Users(String channelName, String sortBy) {
        List<TopUser> users = new ArrayList<>();
        String orderColumn = switch (sortBy) {
            case "words" -> "words";
            case "chars", "characters" -> "chars";
            default -> "lines";
        };
        
        String sql = "SELECT u.account, u.lines, u.words, u.chars FROM a4stats.users u " +
                    "JOIN a4stats.channels c ON u.channelid = c.id " +
                    "WHERE LOWER(c.name) = LOWER(?) ORDER BY u." + orderColumn + " DESC LIMIT 10";
        
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, channelName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    TopUser user = new TopUser();
                    user.account = rs.getString("account");
                    user.lines = rs.getInt("lines");
                    user.words = rs.getInt("words");
                    user.chars = rs.getInt("chars");
                    users.add(user);
                }
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to get top users", e);
        }
        return users;
    }

    /**
     * Get channel statistics
     */
    private ChannelStats getChannelStats(String channelName) {
        String sql = "SELECT c.*, " +
                    "(SELECT COUNT(*) FROM a4stats.users u WHERE u.channelid = c.id) as user_count, " +
                    "(SELECT SUM(lines) FROM a4stats.users u WHERE u.channelid = c.id) as total_lines, " +
                    "(SELECT SUM(words) FROM a4stats.users u WHERE u.channelid = c.id) as total_words " +
                    "FROM a4stats.channels c WHERE LOWER(c.name) = LOWER(?)";
        
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, channelName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ChannelStats stats = new ChannelStats();
                    stats.totalUsers = rs.getInt("user_count");
                    stats.active = rs.getInt("active") == 1;
                    stats.privacy = rs.getInt("privacy");
                    stats.totalLines = rs.getLong("total_lines");
                    stats.totalWords = rs.getLong("total_words");
                    return stats;
                }
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to get channel stats", e);
        }
        return null;
    }

    /**
     * Get random quote from channel
     */
    private String getRandomQuote(String channelName, String nickname) {
        String sql;
        if (nickname != null) {
            sql = "SELECT quote FROM a4stats.users u " +
                  "JOIN a4stats.channels c ON u.channelid = c.id " +
                  "WHERE LOWER(c.name) = LOWER(?) AND LOWER(u.account) = LOWER(?) " +
                  "AND quote IS NOT NULL AND quote != '' LIMIT 1";
        } else {
            sql = "SELECT quote FROM a4stats.users u " +
                  "JOIN a4stats.channels c ON u.channelid = c.id " +
                  "WHERE LOWER(c.name) = LOWER(?) " +
                  "AND quote IS NOT NULL AND quote != '' " +
                  "ORDER BY RANDOM() LIMIT 1";
        }
        
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, channelName);
            if (nickname != null) {
                stmt.setString(2, nickname);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("quote");
                }
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to get quote", e);
        }
        return null;
    }

    // Helper methods

    /**
     * Get nickname from numeric
     */
    private String getNickFromNumeric(String numeric) {
        if (st != null && st.getUsers() != null) {
            Users user = st.getUsers().get(numeric);
            return user != null ? user.getNick() : "Unknown";
        }
        return "Unknown";
    }

    /**
     * Get privacy level name
     */
    private String getPrivacyName(int privacy) {
        return switch (privacy) {
            case PRIVACY_PUBLIC -> "Public";
            case PRIVACY_MEMBERS -> "Members Only";
            case PRIVACY_CHANSERV -> "ChanServ Only";
            default -> "Unknown";
        };
    }

    /**
     * Send notice to user
     */
    private void sendNotice(String target, String message) {
        sendText("%s%s O %s :%s", numeric, getNumericSuffix(), target, message);
    }

    // IRC Event Handlers
    
    /**
     * Handle JOIN event
     */
    private void handleJoin(String numeric, String channel) {
        if (!isStatsChannel(channel)) {
            return;
        }
        // Join is tracked automatically when user sends first message
        updateChannelTimestamp(channel);
    }
    
    /**
     * Handle PART event
     */
    private void handlePart(String numeric, String channel, String message) {
        if (!isStatsChannel(channel)) {
            return;
        }
        
        updateChannelTimestamp(channel);
        
        Users user = st.getUsers().get(numeric);
        if (user == null) return;
        
        List<String> updates = new ArrayList<>();
        touchUser(updates, user);
        updates.add("last = '" + escapeString("PART " + (message != null ? message : "")) + "'");
        
        updateUserStats(channel, getAccount(user), getAccountId(user), updates);
    }
    
    /**
     * Handle KICK event
     */
    private void handleKick(String channel, String kickedNumeric, String kickerNumeric, String reason) {
        if (!isStatsChannel(channel)) {
            return;
        }
        
        updateChannelTimestamp(channel);
        
        Users kicker = st.getUsers().get(kickerNumeric);
        Users kicked = st.getUsers().get(kickedNumeric);
        
        if (kicker != null) {
            List<String> updates = new ArrayList<>();
            touchUser(updates, kicker);
            updates.add("kicks = kicks + 1");
            updates.add("last = '" + escapeString("KICK " + (kicked != null ? kicked.getNick() : "Unknown") + " " + reason) + "'");
            updateUserStats(channel, getAccount(kicker), getAccountId(kicker), updates);
        }
        
        if (kicked != null) {
            List<String> updates = new ArrayList<>();
            touchUser(updates, kicked);
            updates.add("kicked = kicked + 1");
            updates.add("last = '" + escapeString("KICKED " + (kicker != null ? kicker.getNick() : "Unknown") + " " + reason) + "'");
            updateUserStats(channel, getAccount(kicked), getAccountId(kicked), updates);
        }
        
        // Log kick to database
        if (kicker != null && kicked != null) {
            logKick(channel, getAccount(kicker), getAccountId(kicker), getAccount(kicked), getAccountId(kicked), reason);
        }
    }
    
    /**
     * Handle TOPIC event
     */
    private void handleTopic(String channel, String numeric, String topic) {
        if (!isStatsChannel(channel) || numeric == null) {
            return;
        }
        
        updateChannelTimestamp(channel);
        
        Users user = st.getUsers().get(numeric);
        if (user == null) return;
        
        List<String> updates = new ArrayList<>();
        touchUser(updates, user);
        updates.add("last = '" + escapeString("TOPIC " + topic) + "'");
        updateUserStats(channel, getAccount(user), getAccountId(user), updates);
        
        // Log topic to database
        logTopic(channel, getAccount(user), getAccountId(user), topic);
    }
    
    /**
     * Handle MODE event
     */
    private void handleMode(String channel, String modes, String[] elem) {
        if (!isStatsChannel(channel) || elem.length < 5) {
            return;
        }
        
        updateChannelTimestamp(channel);
        
        String numeric = elem[0];
        Users user = st.getUsers().get(numeric);
        if (user == null) return;
        
        // Parse mode changes
        boolean adding = true;
        int paramIndex = 4;
        
        for (char mode : modes.toCharArray()) {
            if (mode == '+') {
                adding = true;
            } else if (mode == '-') {
                adding = false;
            } else if (mode == 'o' && paramIndex < elem.length) {
                String targetNumeric = elem[paramIndex++];
                Users target = st.getUsers().get(targetNumeric);
                
                List<String> updates = new ArrayList<>();
                touchUser(updates, user);
                
                if (adding) {
                    updates.add("ops = ops + 1");
                    updates.add("last = '" + escapeString("MODE +o " + (target != null ? target.getNick() : "Unknown")) + "'");
                } else {
                    updates.add("deops = deops + 1");
                    updates.add("last = '" + escapeString("MODE -o " + (target != null ? target.getNick() : "Unknown")) + "'");
                }
                
                updateUserStats(channel, getAccount(user), getAccountId(user), updates);
            }
        }
    }
    
    /**
     * Handle QUIT event
     */
    private void handleQuit(String numeric) {
        Users user = st.getUsers().get(numeric);
        if (user == null) return;
        
        // Update all channels where user was present
        for (String channel : st.getChannel().keySet()) {
            if (isStatsChannel(channel)) {
                Channel ch = st.getChannel().get(channel);
                if (ch != null && ch.getUsers().contains(numeric)) {
                    List<String> updates = new ArrayList<>();
                    touchUser(updates, user);
                    updates.add("last = 'QUIT'");
                    updateUserStats(channel, getAccount(user), getAccountId(user), updates);
                }
            }
        }
    }
    
    /**
     * Handle NICK change event
     */
    private void handleNick(String numeric, String newNick) {
        Users user = st.getUsers().get(numeric);
        if (user == null) return;
        
        // Update all channels where user is present
        for (String channel : st.getChannel().keySet()) {
            if (isStatsChannel(channel)) {
                Channel ch = st.getChannel().get(channel);
                if (ch != null && ch.getUsers().contains(numeric)) {
                    List<String> updates = new ArrayList<>();
                    touchUser(updates, user);
                    updates.add("last = 'NICK'");
                    updateUserStats(channel, getAccount(user), getAccountId(user), updates);
                }
            }
        }
    }
    
    /**
     * Handle channel message
     */
    private void handleChannelMessage(String numeric, String channel, String message) {
        if (!isStatsChannel(channel)) {
            return;
        }
        
        updateChannelTimestamp(channel);
        
        // Strip IRC control codes (colors, formatting)
        message = stripControlCodes(message);
        
        Users user = st.getUsers().get(numeric);
        if (user == null) return;
        
        String account = getAccount(user);
        int accountId = getAccountId(user);
        long currentTime = time();
        int hour = (int)((currentTime / 3600) % 24);
        
        List<String> updates = new ArrayList<>();
        touchUser(updates, user);
        
        // Track channel state for skitzo and relations
        ChannelState state = channelStates.computeIfAbsent(channel.toLowerCase(), k -> new ChannelState());
        
        // Skitzo checking (rapid messages from same user)
        if (numeric.equals(state.lastNumeric)) {
            state.skitzoCounter++;
            if (state.skitzoCounter > 4) {
                updates.add("skitzo = skitzo + 1");
                state.skitzoCounter = 0;
            }
        } else {
            state.lastNumeric = numeric;
            state.skitzoCounter = 0;
        }
        
        // Track relations (users talking to each other)
        state.addRecentMessage(account, accountId, currentTime);
        updateRelations(channel, account, accountId, state, currentTime);
        
        // Regular text message (ACTIONs are handled separately in parseLine)
        updates.add("last = '" + escapeString("TEXT " + message) + "'");
        
        // Check for highlights
        handleHighlights(channel, message, numeric);
        
        // Analyze message content
        analyzeMessage(message, updates, user.getNick(), false, currentTime);
        
        // Update hourly stats
        updates.add("h" + hour + " = h" + hour + " + 1");
        
        // Calculate rating (time-based activity bonus)
        long lastSeen = getLastSeen(channel, account, accountId);
        long timeDiff = currentTime - lastSeen;
        int ratingBonus = timeDiff > 600 ? 120 : (int)timeDiff;
        updates.add("rating = rating + " + ratingBonus);
        
        // Apply all updates
        updateUserStats(channel, account, accountId, updates);
        
        // Update channel hourly stats
        updateChannelHourlyStats(channel, hour);
    }
    
    /**
     * Handle ACTION (/me command)
     */
    private void handleAction(String numeric, String channel, String message) {
        if (!isStatsChannel(channel)) {
            return;
        }
        
        updateChannelTimestamp(channel);
        
        // Strip IRC control codes
        message = stripControlCodes(message);
        
        Users user = st.getUsers().get(numeric);
        if (user == null) return;
        
        String account = getAccount(user);
        int accountId = getAccountId(user);
        long currentTime = time();
        int hour = (int)((currentTime / 3600) % 24);
        
        List<String> updates = new ArrayList<>();
        touchUser(updates, user);
        
        // Count as action
        updates.add("actions = actions + 1");
        
        // Check for slap actions (e.g., "slaps Nick" or "slaps Nick with something")
        String lowerMessage = message.toLowerCase();
        if (lowerMessage.startsWith("slaps ") || lowerMessage.contains(" slaps ")) {
            updates.add("slaps = slaps + 1");
            
            // Try to find who was slapped
            String[] words = message.split("\\s+");
            for (int i = 0; i < words.length - 1; i++) {
                if (words[i].equalsIgnoreCase("slaps")) {
                    String victim = words[i + 1].replaceAll("[^a-zA-Z0-9_\\-\\[\\]\\{\\}\\\\`\\|]", "");
                    if (!victim.isEmpty()) {
                        // Find the victim's numeric and increment their slapped counter
                        for (Users u : st.getUsers().values()) {
                            if (u.getNick().equalsIgnoreCase(victim) && u.getAccount() != null && !u.getAccount().equals("0")) {
                                List<String> victimUpdates = new ArrayList<>();
                                touchUser(victimUpdates, u);
                                victimUpdates.add("slapped = slapped + 1");
                                updateUserStats(channel, getAccount(u), getAccountId(u), victimUpdates);
                                break;
                            }
                        }
                    }
                    break;
                }
            }
        }
        
        // Count words and characters
        int wordCount = message.isEmpty() ? 0 : message.trim().split("\\s+").length;
        int charCount = message.length();
        
        updates.add("lines = lines + 1");
        updates.add("chars = chars + " + charCount);
        updates.add("words = words + " + wordCount);
        updates.add("h" + hour + " = h" + hour + " + 1");
        
        // Last message
        updates.add("last = '" + escapeString("ACTION " + message) + "'");
        
        // Basic rating for actions
        int rating = 10 + wordCount * 5;
        updates.add("rating = rating + " + rating);
        
        updateUserStats(channel, account, accountId, updates);
        updateChannelHourlyStats(channel, hour);
    }
    
    /**
     * Analyze message content for statistics
     */
    private void analyzeMessage(String message, List<String> updates, String nick, boolean isAction, long currentTime) {
        // Check for happy smileys
        for (String smiley : HAPPY_SMILEYS) {
            if (message.contains(smiley)) {
                updates.add("mood_happy = mood_happy + 1");
                break;
            }
        }
        
        // Check for sad smileys
        for (String smiley : SAD_SMILEYS) {
            if (message.contains(smiley)) {
                updates.add("mood_sad = mood_sad + 1");
                break;
            }
        }
        
        // Check for foul language
        String lowerMessage = message.toLowerCase();
        for (String foul : FOUL_WORDS) {
            if (lowerMessage.contains(foul)) {
                updates.add("foul = foul + 1");
                break;
            }
        }
        
        // Check for questions
        if (message.endsWith("?")) {
            updates.add("questions = questions + 1");
        }
        
        // Check for yelling
        if (message.endsWith("!")) {
            updates.add("yelling = yelling + 1");
        }
        
        // Count lines, words, characters
        updates.add("lines = lines + 1");
        updates.add("chars = chars + " + message.length());
        
        int wordCount = message.split("\\s+").length;
        updates.add("words = words + " + wordCount);
        
        // Count capital letters
        int capsCount = 0;
        for (char c : message.toCharArray()) {
            if (Character.isUpperCase(c) || c == '!' || c == '?') {
                capsCount++;
            }
        }
        updates.add("caps = caps + " + capsCount);
        
        // Update quote (20-200 chars, random chance)
        if (message.length() > 20 && message.length() < 200) {
            Random random = new Random();
            int chance = random.nextInt(100);
            String quote = isAction ? "* " + nick + " " + message : message;
            quote = escapeString(quote);
            
            updates.add("quote = (CASE WHEN quotereset = 0 OR (" + currentTime + " - quotereset > 7200 AND " + chance + " > 70) THEN '" + quote + "' ELSE quote END)");
            updates.add("quotereset = (CASE WHEN quotereset = 0 OR (" + currentTime + " - quotereset > 7200 AND " + chance + " > 70) THEN " + currentTime + " ELSE quotereset END)");
        }
    }
    
    /**
     * Handle slap detection in ACTION messages
     * Returns true if anyone was slapped
     */
    private boolean handleSlaps(String channel, String message, String senderNumeric) {
        boolean slapped = false;
        
        for (String word : message.split("\\s+")) {
            Users target = findUserByNick(word);
            if (target != null && !target.getId().equals(senderNumeric) && isUserInChannelDirect(target.getId(), channel)) {
                slapped = true;
                
                // Target gets slapped
                List<String> targetUpdates = new ArrayList<>();
                targetUpdates.add("slapped = slapped + 1");
                targetUpdates.add("highlights = highlights + 1");
                updateUserStats(channel, getAccount(target), getAccountId(target), targetUpdates);
            }
        }
        
        return slapped;
    }
    
    /**
     * Handle highlight detection
     */
    private void handleHighlights(String channel, String message, String senderNumeric) {
        for (String word : message.split("\\s+")) {
            Users target = findUserByNick(word);
            if (target != null && !target.getId().equals(senderNumeric) && isUserInChannelDirect(target.getId(), channel)) {
                List<String> updates = new ArrayList<>();
                updates.add("highlights = highlights + 1");
                updateUserStats(channel, getAccount(target), getAccountId(target), updates);
            }
        }
    }
    
    /**
     * Update user relations
     */
    private void updateRelations(String channel, String account, int accountId, ChannelState state, long currentTime) {
        for (Map.Entry<String, Integer> entry : state.getRecentUsers(currentTime - 120).entrySet()) {
            String otherAccount = entry.getKey();
            int otherAccountId = entry.getValue();
            
            if (!account.equals(otherAccount) || accountId != otherAccountId) {
                updateRelation(channel, account, accountId, otherAccount, otherAccountId);
            }
        }
    }
    
    /**
     * Check if user is in channel (direct access)
     */
    private boolean isUserInChannelDirect(String userNumeric, String channel) {
        Channel ch = st.getChannel().get(channel.toLowerCase());
        if (ch != null) {
            return ch.getUsers().contains(userNumeric);
        }
        return false;
    }
    
    /**
     * Find user by nickname
     */
    private Users findUserByNick(String nick) {
        for (Users user : st.getUsers().values()) {
            if (user.getNick().equalsIgnoreCase(nick)) {
                return user;
            }
        }
        return null;
    }
    
    /**
     * Check if channel is a stats channel
     */
    private boolean isStatsChannel(String channel) {
        Integer channelId = getChannelId(channel);
        return channelId != null;
    }
    
    /**
     * Get channel ID from database (with cache)
     */
    private Integer getChannelId(String channelName) {
        String chanLower = channelName.toLowerCase();
        
        // Check cache first
        Integer cachedId = channelIdCache.get(chanLower);
        if (cachedId != null) {
            return cachedId;
        }
        
        // Not in cache, query database
        String sql = "SELECT id FROM a4stats.channels WHERE LOWER(name) = LOWER(?) AND active = 1";
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, channelName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    // Add to cache
                    channelIdCache.put(chanLower, id);
                    return id;
                }
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to get channel ID", e);
        }
        return null;
    }
    
    /**
     * Get or create user's last seen time
     */
    private long getLastSeen(String channel, String account, int accountId) {
        String sql = "SELECT seen FROM a4stats.users u " +
                    "JOIN a4stats.channels c ON u.channelid = c.id " +
                    "WHERE LOWER(c.name) = LOWER(?) AND u.account = ? AND u.accountid = ?";
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, channel);
            stmt.setString(2, account);
            stmt.setInt(3, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("seen");
                }
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to get last seen", e);
        }
        return 0;
    }
    
    /**
     * Get account name for user
     */
    private String getAccount(Users user) {
        if (user.getAccount() != null && !user.getAccount().isEmpty()) {
            return user.getAccount();
        }
        return maskHost(user.getId());
    }
    
    /**
     * Get account ID for user
     * Returns 0 for unauthenticated users
     */
    private int getAccountId(Users user) {
        // Users don't have an accountid field, so we return 0 for non-authenticated
        // In a full implementation, this would be fetched from the database
        if (user.getAccount() != null && !user.getAccount().isEmpty()) {
            // Try to get account ID from database
            return getAccountIdFromDatabase(user.getAccount());
        }
        return 0;
    }
    
    /**
     * Get account ID from database
     */
    private int getAccountIdFromDatabase(String account) {
        String sql = "SELECT id FROM chanserv.users WHERE LOWER(username) = LOWER(?)";
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, account);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            // Ignore, return 0
        }
        return 0;
    }
    
    /**
     * Mask host for unauthenticated users
     */
    private String maskHost(String numeric) {
        Users user = st.getUsers().get(numeric);
        if (user == null) return "*!*@*";
        
        String ident = user.getIdent() != null ? user.getIdent().replace("~", "*") : "*";
        String host = user.getHost() != null ? user.getHost() : "*";
        
        // Mask hostname
        int dotCount = host.length() - host.replace(".", "").length();
        if (dotCount >= 2) {
            // Check if it's an IP
            if (host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                int lastDot = host.lastIndexOf('.');
                host = host.substring(0, lastDot + 1) + "*";
            } else {
                int firstDot = host.indexOf('.');
                host = "*" + host.substring(firstDot);
            }
        }
        
        return "*!" + ident + "@" + host;
    }
    
    /**
     * Touch user (update seen time and current nick)
     */
    private void touchUser(List<String> updates, Users user) {
        updates.add("account = '" + escapeString(getAccount(user)) + "'");
        updates.add("accountid = " + getAccountId(user));
        updates.add("curnick = '" + escapeString(user.getNick()) + "'");
        updates.add("seen = " + time());
    }
    
    /**
     * Escape string for SQL
     */
    private String escapeString(String str) {
        if (str == null) return "";
        return str.replace("'", "''").replace("\\", "\\\\");
    }
    
    /**
     * Update user statistics in database
     */
    private void updateUserStats(String channel, String account, int accountId, List<String> updates) {
        Integer channelId = getChannelId(channel);
        if (channelId == null) return;
        
        // In PostgreSQL ON CONFLICT DO UPDATE, we need to qualify column references on the RIGHT side
        // of assignments to disambiguate between the existing table row and EXCLUDED (new values)
        // Format: column = a4stats.users.column + 1
        // Build columns and values for INSERT from updates
        List<String> insertColumns = new ArrayList<>();
        List<String> insertValues = new ArrayList<>();
        
        // Parse updates to extract column names and values for INSERT
        for (String update : updates) {
            int eqPos = update.indexOf('=');
            if (eqPos > 0) {
                String columnName = update.substring(0, eqPos).trim();
                String value = update.substring(eqPos + 1).trim();
                
                // Skip columns that are already in the base INSERT (channelid, account, accountid, seen, firstseen)
                if (columnName.equals("account") || columnName.equals("accountid") || 
                    columnName.equals("seen") || columnName.equals("firstseen") || columnName.equals("channelid")) {
                    continue;
                }
                
                // For INSERT, we need to handle expressions differently
                // If value contains table references or expressions, use COALESCE with default
                if (value.contains("a4stats.users.") || value.contains(" + ") || value.contains("CASE")) {
                    // For expressions that reference existing values, use 0 or '' as default for new inserts
                    if (columnName.matches("h\\d+|lines|words|chars|actions|kicks|kicked|questions|yelling|caps|foul|mood_happy|mood_sad|ops|deops|rating|slaps|slapped|highlights|skitzo")) {
                        insertValues.add("0");
                    } else if (columnName.equals("quotereset")) {
                        insertValues.add("0");
                    } else {
                        insertValues.add("''");
                    }
                } else {
                    insertValues.add(value);
                }
                insertColumns.add(columnName);
            }
        }
        
        // Build qualified updates for ON CONFLICT
        List<String> qualifiedUpdates = updates.stream()
            .map(update -> {
                // Split by single quotes to avoid replacing inside string literals
                StringBuilder result = new StringBuilder();
                String[] parts = update.split("'", -1);
                boolean inString = false;
                
                String[] columns = {"lines", "words", "chars", "actions", 
                                   "kicks", "kicked", "questions", "yelling", "caps", "foul", 
                                   "firstseen", "seen", "mood_happy", "mood_sad", "ops", "deops",
                                   "rating", "accountid", "curnick", "last",
                                   "quote", "quotereset", "account",
                                   "slaps", "slapped", "highlights", "skitzo",
                                   "h0", "h1", "h2", "h3", "h4", "h5", "h6", "h7", "h8", "h9",
                                   "h10", "h11", "h12", "h13", "h14", "h15", "h16", "h17", "h18", "h19",
                                   "h20", "h21", "h22", "h23"};
                
                for (int i = 0; i < parts.length; i++) {
                    if (inString) {
                        result.append(parts[i]);
                    } else {
                        String part = parts[i];
                        
                        // For the FIRST part, split by = and only qualify right side
                        // For all OTHER parts (after string literals), qualify everything
                        if (i == 0) {
                            int eqPos = part.indexOf('=');
                            if (eqPos >= 0) {
                                String left = part.substring(0, eqPos + 1);
                                String right = part.substring(eqPos + 1);
                                
                                for (String col : columns) {
                                    right = right.replaceAll("\\b" + col + "\\b", "a4stats.users." + col);
                                }
                                
                                part = left + right;
                            }
                        } else {
                            // Qualify all column references in subsequent parts
                            for (String col : columns) {
                                part = part.replaceAll("\\b" + col + "\\b", "a4stats.users." + col);
                            }
                        }
                        
                        result.append(part);
                    }
                    
                    if (i < parts.length - 1) {
                        result.append("'");
                        inString = !inString;
                    }
                }
                
                return result.toString();
            })
            .collect(java.util.stream.Collectors.toList());
        
        // Build INSERT columns list
        StringBuilder insertColumnsStr = new StringBuilder("channelid, account, accountid, seen, firstseen");
        if (!insertColumns.isEmpty()) {
            insertColumnsStr.append(", ").append(String.join(", ", insertColumns));
        }
        
        // Build INSERT values placeholders
        StringBuilder insertValuesStr = new StringBuilder("?, ?, ?, ?, ?");
        if (!insertValues.isEmpty()) {
            insertValuesStr.append(", ").append(String.join(", ", insertValues));
        }
        
        String sql = "INSERT INTO a4stats.users (" + insertColumnsStr + ") " +
                    "VALUES (" + insertValuesStr + ") " +
                    "ON CONFLICT (channelid, account, accountid) DO UPDATE SET " +
                    String.join(", ", qualifiedUpdates);
        
        long currentTime = time();
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, channelId);
            stmt.setString(2, account);
            stmt.setInt(3, accountId);
            stmt.setInt(4, (int) currentTime);
            stmt.setInt(5, (int) currentTime);
            stmt.executeUpdate();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to update user stats", e);
        }
    }
    
    /**
     * Update channel hourly statistics
     */
    private void updateChannelHourlyStats(String channel, int hour) {
        Integer channelId = getChannelId(channel);
        if (channelId == null) return;
        
        String sql = "UPDATE a4stats.channels SET h" + hour + " = h" + hour + " + 1, \"timestamp\" = ? WHERE id = ?";
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, (int) time());
            stmt.setInt(2, channelId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to update channel hourly stats", e);
        }
    }
    
    /**
     * Update user relation
     */
    private void updateRelation(String channel, String account1, int accountId1, String account2, int accountId2) {
        Integer channelId = getChannelId(channel);
        if (channelId == null) return;
        
        long currentTime = time();
        
        // Check if relation exists
        String checkSql = "SELECT score FROM a4stats.relations " +
                         "WHERE channelid = ? AND first = ? AND firstid = ? AND second = ? AND secondid = ?";
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setInt(1, channelId);
            checkStmt.setString(2, account1);
            checkStmt.setInt(3, accountId1);
            checkStmt.setString(4, account2);
            checkStmt.setInt(5, accountId2);
            
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    // Update existing relation
                    String updateSql = "UPDATE a4stats.relations SET score = score + 1, seen = ? " +
                                      "WHERE channelid = ? AND first = ? AND firstid = ? AND second = ? AND secondid = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, (int) currentTime);
                        updateStmt.setInt(2, channelId);
                        updateStmt.setString(3, account1);
                        updateStmt.setInt(4, accountId1);
                        updateStmt.setString(5, account2);
                        updateStmt.setInt(6, accountId2);
                        updateStmt.executeUpdate();
                    }
                } else {
                    // Insert new relation
                    String insertSql = "INSERT INTO a4stats.relations (channelid, first, firstid, second, secondid, seen, score) " +
                                      "VALUES (?, ?, ?, ?, ?, ?, 1)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, channelId);
                        insertStmt.setString(2, account1);
                        insertStmt.setInt(3, accountId1);
                        insertStmt.setString(4, account2);
                        insertStmt.setInt(5, accountId2);
                        insertStmt.setInt(6, (int) currentTime);
                        insertStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to update relation", e);
        }
    }
    
    /**
     * Log kick to database
     */
    private void logKick(String channel, String kicker, int kickerId, String victim, int victimId, String reason) {
        Integer channelId = getChannelId(channel);
        if (channelId == null) return;
        
        String sql = "INSERT INTO a4stats.kicks (channelid, kicker, kickerid, victim, victimid, \"timestamp\", reason) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, channelId);
            stmt.setString(2, kicker);
            stmt.setInt(3, kickerId);
            stmt.setString(4, victim);
            stmt.setInt(5, victimId);
            stmt.setInt(6, (int) time());
            stmt.setString(7, reason);
            stmt.executeUpdate();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to log kick", e);
        }
    }
    
    /**
     * Log topic to database
     */
    private void logTopic(String channel, String setBy, int setById, String topic) {
        Integer channelId = getChannelId(channel);
        if (channelId == null) return;
        
        String sql = "INSERT INTO a4stats.topics (channelid, setby, setbyid, \"timestamp\", topic) " +
                    "VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, channelId);
            stmt.setString(2, setBy);
            stmt.setInt(3, setById);
            stmt.setLong(4, time());
            stmt.setString(5, topic);
            stmt.executeUpdate();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to log topic", e);
        }
    }

    // Data classes

    private static class UserStats {
        int lines, words, chars, rating, actions;
        int questions, yelling, caps;
        int moodHappy, moodSad;
        int kicks, kicked;
        String quote;
    }

    private static class TopUser {
        String account;
        int lines, words, chars;
    }

    private static class ChannelStats {
        int totalUsers;
        boolean active;
        int privacy;
        long totalLines, totalWords;
    }
    
    /**
     * Channel state for tracking recent activity
     */
    private static class ChannelState {
        String lastNumeric;
        int skitzoCounter = 0;
        private final LinkedList<RecentMessage> recentMessages = new LinkedList<>();
        
        void addRecentMessage(String account, int accountId, long time) {
            recentMessages.add(new RecentMessage(account, accountId, time));
            // Keep only last 10 messages
            while (recentMessages.size() > 10) {
                recentMessages.removeFirst();
            }
        }
        
        Map<String, Integer> getRecentUsers(long since) {
            Map<String, Integer> users = new HashMap<>();
            for (RecentMessage msg : recentMessages) {
                if (msg.time > since) {
                    users.put(msg.account, msg.accountId);
                }
            }
            return users;
        }
    }
    
    private static class RecentMessage {
        String account;
        int accountId;
        long time;
        
        RecentMessage(String account, int accountId, long time) {
            this.account = account;
            this.accountId = accountId;
            this.time = time;
        }
    }
    
    /**
     * Start cleanup timer (runs every 24 hours)
     */
    private void startCleanupTimer() {
        if (cleanupTimer != null) {
            cleanupTimer.cancel();
        }
        
        // Perform initial cleanup immediately on startup
        new Thread(() -> {
            try {
                Thread.sleep(5000); // Wait 5 seconds for initialization
                performCleanup();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "StatsServ-InitialCleanup").start();
        
        cleanupTimer = new Timer("StatsServ-Cleanup", true);
        cleanupTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                performCleanup();
            }
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL); // Run every 24 hours
        
        getLogger().log(Level.INFO, "StatsServ cleanup timer started (interval: {0}ms)", CLEANUP_INTERVAL);
    }
    
    /**
     * Perform database cleanup
     * - Delete old kicks/topics (keep only last 10)
     * - Disable inactive channels (no activity for 30 days)
     * - Delete data for channels disabled for 5+ days
     */
    private void performCleanup() {
        long startTime = System.currentTimeMillis();
        int kicksDeleted = 0;
        int topicsDeleted = 0;
        int channelsDisabled = 0;
        int channelsDeleted = 0;
        
        getLogger().log(Level.INFO, "Starting StatsServ database cleanup");
        
        try (Connection conn = mi.getDb().getConnection()) {
            long now = System.currentTimeMillis() / 1000;
            long inactiveThreshold = now - (CLEANUP_INACTIVE_DAYS * 86400L);
            long deleteThreshold = now - (CLEANUP_DELETE_DAYS * 86400L);
            
            // Get all active channels
            String getChannelsSql = "SELECT id, name, timestamp, " +
                "(SELECT MAX(seen) FROM a4stats.users WHERE channelid = channels.id) as last_activity " +
                "FROM a4stats.channels WHERE active = 1";
            
            try (PreparedStatement stmt = conn.prepareStatement(getChannelsSql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    int channelId = rs.getInt("id");
                    String channelName = rs.getString("name");
                    long timestamp = rs.getLong("timestamp");
                    long lastActivity = rs.getLong("last_activity");
                    
                    // Use timestamp if there was never any activity
                    if (lastActivity == 0) {
                        lastActivity = timestamp;
                    }
                    
                    if (lastActivity < inactiveThreshold) {
                        // Disable inactive channel
                        String disableSql = "UPDATE a4stats.channels SET active = 0, deleted = ? WHERE id = ?";
                        try (PreparedStatement disableStmt = conn.prepareStatement(disableSql)) {
                            disableStmt.setLong(1, now);
                            disableStmt.setInt(2, channelId);
                            disableStmt.executeUpdate();
                            channelsDisabled++;
                            getLogger().log(Level.INFO, "Disabled inactive channel: {0}", channelName);
                        }
                    } else {
                        // Cleanup old kicks (keep only last 10)
                        String cleanupKicksSql = "DELETE FROM a4stats.kicks WHERE channelid = ? AND timestamp <= " +
                            "(SELECT timestamp FROM a4stats.kicks WHERE channelid = ? ORDER BY timestamp DESC OFFSET ? LIMIT 1)";
                        try (PreparedStatement cleanupStmt = conn.prepareStatement(cleanupKicksSql)) {
                            cleanupStmt.setInt(1, channelId);
                            cleanupStmt.setInt(2, channelId);
                            cleanupStmt.setInt(3, CLEANUP_KEEP);
                            kicksDeleted += cleanupStmt.executeUpdate();
                        }
                        
                        // Cleanup old topics (keep only last 10)
                        String cleanupTopicsSql = "DELETE FROM a4stats.topics WHERE channelid = ? AND timestamp <= " +
                            "(SELECT timestamp FROM a4stats.topics WHERE channelid = ? ORDER BY timestamp DESC OFFSET ? LIMIT 1)";
                        try (PreparedStatement cleanupStmt = conn.prepareStatement(cleanupTopicsSql)) {
                            cleanupStmt.setInt(1, channelId);
                            cleanupStmt.setInt(2, channelId);
                            cleanupStmt.setInt(3, CLEANUP_KEEP);
                            topicsDeleted += cleanupStmt.executeUpdate();
                        }
                    }
                }
            }
            
            // Delete data for channels that have been disabled for 5+ days
            String deleteSql = "DELETE FROM a4stats.channels WHERE active = 0 AND deleted < ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                stmt.setLong(1, deleteThreshold);
                channelsDeleted = stmt.executeUpdate();
            }
            
            long duration = System.currentTimeMillis() - startTime;
            getLogger().log(Level.INFO, "StatsServ cleanup completed in {0}ms: " +
                "Deleted {1} old kicks, {2} old topics. Disabled {3} inactive channels. Deleted {4} channels.",
                new Object[]{duration, kicksDeleted, topicsDeleted, channelsDisabled, channelsDeleted});
            
            // Notify opers if any changes were made
            if (kicksDeleted > 0 || topicsDeleted > 0 || channelsDisabled > 0 || channelsDeleted > 0) {
                String message = String.format("[StatsServ Cleanup] Completed in %dms: Deleted %d kicks, %d topics. Disabled %d inactive channels (>%d days). Deleted %d channels (disabled >%d days).",
                    duration, kicksDeleted, topicsDeleted, channelsDisabled, CLEANUP_INACTIVE_DAYS, channelsDeleted, CLEANUP_DELETE_DAYS);
                sendOperNotice(message);
            }
            
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "StatsServ cleanup failed", e);
            sendOperNotice("[StatsServ Cleanup] FAILED: " + e.getMessage());
        }
    }
    
    /**
     * Update channel timestamp (activity tracking)
     */
    private void updateChannelTimestamp(String channel) {
        String sql = "UPDATE a4stats.channels SET timestamp = ? WHERE LOWER(name) = LOWER(?)";
        try (Connection conn = mi.getDb().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis() / 1000);
            stmt.setString(2, channel);
            stmt.executeUpdate();
        } catch (SQLException e) {
            // Don't log - this is called very frequently
        }
    }
}

