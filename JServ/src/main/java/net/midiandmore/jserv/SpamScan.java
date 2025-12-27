/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;


public final class SpamScan implements Software, Module {
    
    private boolean enabled = false;

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
     * @return the identd
     */
    public String getIdentd() {
        return identd;
    }

    /**
     * @param identd the identd to set
     */
    public void setIdentd(String identd) {
        this.identd = identd;
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

    private Thread thread;
    private JServ mi;
    private Socket socket;
    private PrintWriter pw;
    private BufferedReader br;
    private boolean runs;
    private String serverNumeric;
    private String numeric;
    private String numericSuffix;
    private String nick;
    private String identd;
    private String servername;
    private String description;
    private byte[] ip;
    private boolean reg;
    private SocketThread st;

    public SpamScan(JServ mi, SocketThread st, PrintWriter pw, BufferedReader br) {
        initialize(mi, st, pw, br);
    }
    
    @Override
    public void initialize(JServ jserv, SocketThread socketThread, PrintWriter pw, BufferedReader br) {
        setMi(jserv);
        setReg(false);
        setPw(pw);
        setBr(br);
        setSt(socketThread);
        this.enabled = false;
        LOG.log(Level.INFO, "SpamScan module initialized");
    }

    @Override
    public void handshake(String nick, String servername, String description, String numeric, String identd) {
        if (!enabled) {
            LOG.log(Level.WARNING, "SpamScan handshake called but module is disabled");
            return;
        }
        setServername(servername);
        setNick(nick);
        setIdentd(identd);
        setDescription(description);
        setNumeric(numeric);
        LOG.log(Level.INFO, "Registering SpamScan nick: {0}", getNick());
        sendText("%s N %s 2 %d %s %s +oikr - %s:%d U]AEB %s%s :%s", getNumeric(), getNick(), time(), getIdentd(), getServername(), getNick(), time(), getNumeric(), getNumericSuffix(), getDescription());
    }
    
    @Override
    public void shutdown() {
        LOG.log(Level.INFO, "SpamScan module shutting down");
        if (enabled && getNumeric() != null && getPw() != null) {
            // Send QUIT command to properly log out the service
            try {
                sendText("%s%s Q :Service shutting down", getNumeric(), getNumericSuffix());
                LOG.log(Level.INFO, "SpamScan sent QUIT command");
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to send QUIT command for SpamScan", e);
            }
        }
        this.enabled = false;
        // Clean up resources if needed
    }
    
    @Override
    public String getModuleName() {
        return "SpamScan";
    }
    
    @Override
    public void registerBurstChannels(java.util.HashMap<String, Burst> bursts, String serverNumeric) {
        if (!enabled) {
            return;
        }
        // SpamScan joins channels from database
        java.util.List<String> channels = getMi().getDb().getSpamScanChannels();
        for (String channel : channels) {
            if (!bursts.containsKey(channel.toLowerCase())) {
                bursts.put(channel.toLowerCase(), new Burst(channel));
            }
            bursts.get(channel.toLowerCase()).getUsers().add(serverNumeric + getNumericSuffix());
        }
        LOG.log(Level.INFO, "SpamScan registered {0} burst channels", channels.size());
    }
    
    @Override
    public void postLoadInitialization() {
        if (!enabled) {
            return;
        }
        // Create database schema for SpamScan
        try {
            getMi().getDb().createSchema();
            getMi().getDb().createTable();
            getMi().getDb().ensureCreatedAtColumn();
            getMi().getDb().commit();
            LOG.log(Level.INFO, "SpamScan database schema initialized");
            
            // Reset kill tracking on restart (clears non-glined entries)
            getMi().getDb().resetKillTracking();
            LOG.log(Level.INFO, "SpamScan kill tracking reset on startup");
            
            // Cleanup old kill tracking entries (older than 7 days)
            getMi().getDb().cleanupOldKillTracking();
            LOG.log(Level.INFO, "SpamScan cleanup completed");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to initialize SpamScan database schema", e);
        }
    }
    
    @Override
    public boolean handleNewUser(String numeric, String nick, String ident, String host, String account, String serverNumeric) {
        if (!enabled) {
            return false;
        }
        // Check for suspicious idents (security risk)
        if (handleSuspiciousIdent(numeric, nick, ident)) {
            return true;
        }
        // Check for Knocker spambots
        return handleKnockerDetection(numeric, nick, ident);
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public void enable() {
        this.enabled = true;
        LOG.log(Level.INFO, "SpamScan module enabled");
    }
    
    @Override
    public void disable() {
        this.enabled = false;
        LOG.log(Level.INFO, "SpamScan module disabled");
    }

    protected void sendText(String text, Object... args) {
        if (!enabled) {
            return;
        }
        getPw().println(text.formatted(args));
        getPw().flush();
        if (getMi().getConfig().getConfigFile().getProperty("debug", "false").equalsIgnoreCase("true")) {
            LOG.log(Level.FINE, "DEBUG sendText: {0}", text.formatted(args));
        }
    }

    @Override
    public void parseLine(String text) {
        if (!enabled) {
            return;
        }
        try {
            text = text.trim();
            if (getSt().getServerNumeric() != null) {
                var elem = text.split(" ");
                // Handle private messages to SpamScan
                if (elem[1].equals("P") && elem[2].equals(getNumeric() + getNumericSuffix())) {
                    var sb = new StringBuilder();
                    for (var i = 3; i < elem.length; i++) {
                        sb.append(elem[i]).append(" ");
                    }
                    var command = sb.toString().trim();
                    if (command.startsWith(":")) {
                        command = command.substring(1);
                    }
                    var nick = getSt().getUsers().get(elem[0]).getAccount();
                    var notice = "O";
                    if (!getSt().isNotice(nick)) {
                        notice = "P";
                    }
                    var auth = command.split(" ");
                    
                    // AUTH command - authenticate for temporary elevated privileges
                    if (auth[0].equalsIgnoreCase("AUTH")) {
                        if (auth.length >= 3 && auth[1].equals(getMi().getConfig().getSpamFile().getProperty("authuser")) && auth[2].equals(getMi().getConfig().getSpamFile().getProperty("authpassword"))) {
                            setReg(true);
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], Messages.get("QM_DONE"));
                        } else {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], Messages.get("QM_UNKNOWNCMD", auth[0]));
                        }
                    // ADDCHAN command - add channel to spam monitoring
                    } else if ((getSt().isOper(nick) || isReg()) && auth.length >= 2 && auth[0].equalsIgnoreCase("ADDCHAN")) {
                        var channel = auth[1];
                        if (!getSt().getChannel().containsKey(channel.toLowerCase())) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], Messages.get("QM_EMPTYCHAN", channel));
                        } else if (!getMi().getDb().isSpamScanChannel(channel)) {
                            getMi().getDb().addChan(channel);
                            getSt().joinChannel(channel, getNumeric(), getNumeric() + getNumericSuffix());
                            setReg(false);
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], Messages.get("QM_DONE"));
                        } else {
                            setReg(false);
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Cannot add channel %s: %s is already on that channel.", channel, getMi().getConfig().getSpamFile().get("nick"));
                        }
                    // DELCHAN command - remove channel from spam monitoring
                    } else if ((getSt().isOper(nick) || isReg()) && auth.length >= 2 && auth[0].equalsIgnoreCase("DELCHAN")) {
                        var channel = auth[1];
                        if (!getSt().getChannel().containsKey(channel.toLowerCase())) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], Messages.get("QM_EMPTYCHAN", channel));
                        } else if (getMi().getDb().isSpamScanChannel(channel)) {
                            getMi().getDb().removeChan(channel);
                            getSt().partChannel(channel, getNumeric(), getNumericSuffix());
                            setReg(false);
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], Messages.get("QM_DONE"));
                        } else {
                            setReg(false);
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "%s isn't in the channel.", channel, getMi().getConfig().getSpamFile().get("nick"));
                        }
                    // ADDLAXCHAN command - add channel to lax spam detection list
                    } else if ((getSt().isOper(nick) || isReg()) && auth.length >= 2 && auth[0].equalsIgnoreCase("ADDLAXCHAN")) {
                        var channel = auth[1];
                        if (!getSt().getChannel().containsKey(channel.toLowerCase())) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], Messages.get("QM_EMPTYCHAN", channel));
                        } else if (!getMi().getDb().isSpamScanChannel(channel)) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Cannot enable lax mode for %s: %s is not monitoring that channel.", channel, getMi().getConfig().getSpamFile().get("nick"));
                        } else if (!getMi().getDb().isLaxChannel(channel)) {
                            getMi().getDb().addLaxChannel(channel);
                            setReg(false);
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Lax spam detection enabled for %s.", channel);
                        } else {
                            setReg(false);
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Lax spam detection already enabled for %s.", channel);
                        }
                    // DELLAXCHAN command - remove channel from lax spam detection list
                    } else if ((getSt().isOper(nick) || isReg()) && auth.length >= 2 && auth[0].equalsIgnoreCase("DELLAXCHAN")) {
                        var channel = auth[1];
                        if (!getSt().getChannel().containsKey(channel.toLowerCase())) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], Messages.get("QM_EMPTYCHAN", channel));
                        } else if (getMi().getDb().isLaxChannel(channel)) {
                            getMi().getDb().removeLaxChannel(channel);
                            setReg(false);
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Lax spam detection disabled for %s.", channel);
                        } else {
                            setReg(false);
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Lax spam detection not enabled for %s.", channel);
                        }
                    // BADWORD command - manage badword list
                    } else if (getSt().isOper(nick) && auth.length >= 2 && auth[0].equalsIgnoreCase("BADWORD")) {
                        var flag = auth[1];
                        var b = getMi().getConfig().getBadwordFile();
                        if (flag.equalsIgnoreCase("ADD") || flag.equalsIgnoreCase("DELETE")) {
                            var sb1 = new StringBuilder();
                            for (var i = 2; i < auth.length; i++) {
                                sb1.append(auth[i]);
                                sb1.append(" ");
                            }
                            var parsed = sb1.toString().trim();
                            if (b.containsKey(parsed.toLowerCase())) {
                                if (flag.equalsIgnoreCase("ADD")) {
                                    getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Badword (%s) already exists.", parsed);
                                } else if (flag.equalsIgnoreCase("DELETE")) {
                                    b.remove(parsed.toLowerCase());
                                    getMi().getConfig().saveDataToJSON("badwords-spamscan.json", b, "name", "value");
                                    getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Badword (%s) successfully removed.", parsed);
                                }
                            } else {
                                if (flag.equalsIgnoreCase("ADD")) {
                                    b.put(parsed.toLowerCase(), "");
                                    getMi().getConfig().saveDataToJSON("badwords-spamscan.json", b, "name", "value");
                                    getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Badword (%s) successfully added.", parsed);
                                } else if (flag.equalsIgnoreCase("DELETE")) {
                                    getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Badword (%s) doesn't exist.", parsed);
                                }
                            }
                        } else if (flag.equalsIgnoreCase("LIST")) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "--- Badwords ---");
                            if (b.isEmpty()) {
                                getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "  No badwords specified.");
                            } else {
                                for (var key : b.keySet()) {
                                    getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "%s", key);
                                }
                            }
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "--- End of list ---");
                        } else {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Unknown flag.");
                        }
                    // SPAMSCORE - check spam score of a user
                    } else if (getSt().isOper(nick) && auth.length >= 2 && auth[0].equalsIgnoreCase("SPAMSCORE")) {
                        var targetNick = auth[1];
                        var targetNumeric = getSt().getUserNumeric(targetNick);
                        
                        if (targetNumeric == null) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "User '%s' not found.", targetNick);
                        } else {
                            var targetUser = getSt().getUsers().get(targetNumeric);
                            if (targetUser == null) {
                                getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "User data not available.");
                            } else {
                                getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "--- Spam Score for %s ---", targetUser.getNick());
                                getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Current Score: %.2f", targetUser.getSpamScore());
                                getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Repeat Count: %d", targetUser.getRepeat());
                                getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Flood Count: %d", targetUser.getFlood());
                                getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Caps Count: %d", targetUser.getCapsCount());
                                getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Message History: %d messages", targetUser.getMessageHistory().size());
                                getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Channels: %d", targetUser.getChannels().size());
                                getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "--- End of spam score ---");
                            }
                        }
                    // SHOWCOMMANDS - display available commands
                    } else if (auth[0].equalsIgnoreCase("SHOWCOMMANDS")) {
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], Messages.get("QM_COMMANDLIST"));
                        if (getSt().isOper(nick)) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "+o ADDCHAN      Adds a channel to spam monitoring");
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "+o ADDLAXCHAN   Enables lax spam detection for a channel");
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "+o BADWORD      Manage badwords");
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "+o DELCHAN      Removes a channel from spam monitoring");
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "+o DELLAXCHAN   Disables lax spam detection for a channel");
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "+o GLINESTATS   Shows G-Line configuration and statistics");
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "+o SPAMSCORE    Check spam score of a user");
                        }
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "   HELP         Show help for a command");
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "   SHOWCOMMANDS This message");
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "   VERSION      Shows version information");
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], Messages.get("QM_ENDOFLIST"));
                    // VERSION - show version information
                    } else if (auth[0].equalsIgnoreCase("VERSION")) {
                        Software.BuildInfo buildInfo = Software.getBuildInfo();
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "SpamScan v%s by %s", buildInfo.getFullVersion(), VENDOR);
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Based on JServ v%s", buildInfo.getFullVersion());
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Created by %s", AUTHOR);
                    // HELP commands
                    } else if (getSt().isOper(nick) && auth.length == 2 && auth[0].equalsIgnoreCase("HELP") && auth[1].equalsIgnoreCase("ADDCHAN")) {
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "ADDCHAN <#channel>");
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Adds a channel to spam monitoring.");
                    } else if (getSt().isOper(nick) && auth.length == 2 && auth[0].equalsIgnoreCase("HELP") && auth[1].equalsIgnoreCase("ADDLAXCHAN")) {
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "ADDLAXCHAN <#channel>");
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Enables lax spam detection for a channel (higher thresholds, more lenient).");
                    } else if (getSt().isOper(nick) && auth.length == 2 && auth[0].equalsIgnoreCase("HELP") && auth[1].equalsIgnoreCase("AUTH")) {
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "AUTH <requestname> <requestpassword>");
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Authenticates for temporary elevated privileges.");
                    } else if (getSt().isOper(nick) && auth.length == 2 && auth[0].equalsIgnoreCase("HELP") && auth[1].equalsIgnoreCase("BADWORD")) {
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "BADWORD <ADD|LIST|DELETE> [badword]");
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Manages the badword filter list.");
                    } else if (getSt().isOper(nick) && auth.length == 2 && auth[0].equalsIgnoreCase("HELP") && auth[1].equalsIgnoreCase("DELCHAN")) {
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "DELCHAN <#channel>");
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Removes a channel from spam monitoring.");
                    } else if (getSt().isOper(nick) && auth.length == 2 && auth[0].equalsIgnoreCase("HELP") && auth[1].equalsIgnoreCase("DELLAXCHAN")) {
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "DELLAXCHAN <#channel>");
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Disables lax spam detection for a channel (returns to normal detection).");
                    } else if (getSt().isOper(nick) && auth.length == 2 && auth[0].equalsIgnoreCase("HELP") && auth[1].equalsIgnoreCase("GLINESTATS")) {
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "GLINESTATS");
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Shows G-Line configuration and statistics.");
                    } else if (getSt().isOper(nick) && auth.length == 2 && auth[0].equalsIgnoreCase("HELP") && auth[1].equalsIgnoreCase("SPAMSCORE")) {
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "SPAMSCORE <nickname>");
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Shows the spam score and detection statistics for a user.");
                    } else if (getSt().isOper(nick) && auth[0].equalsIgnoreCase("GLINESTATS")) {
                        var config = getMi().getConfig().getSpamFile();
                        boolean glineEnabled = Boolean.parseBoolean(config.getProperty("enableGLine", "true"));
                        int glineThreshold = Integer.parseInt(config.getProperty("glineAfterKills", "3"));
                        int glineDuration = Integer.parseInt(config.getProperty("glineDuration", "86400"));
                        String glineReason = config.getProperty("glineReason", "Repeated spam violations");
                        
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "--- G-Line Statistics ---");
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Status: %s", glineEnabled ? "Enabled" : "Disabled");
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Threshold: %d kills", glineThreshold);
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Duration: %d seconds (%d hours)", glineDuration, glineDuration / 3600);
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Reason: %s", glineReason);
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "--- End of statistics ---");
                    } else {
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], Messages.get("QM_UNKNOWNCMD", auth[0].toUpperCase()));
                    }
                // Handle channel messages for spam detection
                } else if ((elem[1].equals("P") || elem[1].equals("O")) && getSt().getChannel().containsKey(elem[2].toLowerCase()) && !getSt().getUsers().get(elem[0]).isOper() && !getSt().getUsers().get(elem[0]).isService()) {
                    if (!getSt().isOper(getSt().getUsers().get(elem[0]).getAccount())) {
                        var sb = new StringBuilder();
                        for (var i = 3; i < elem.length; i++) {
                            if (elem[3].startsWith(":")) {
                                elem[3] = elem[3].substring(1);
                            }
                            sb.append(elem[i]);
                            sb.append(" ");
                        }
                        var message = sb.toString().trim();
                        var userNumeric = elem[0];
                        var channelName = elem[2].toLowerCase();
                        
                        // Add channel to user's channel list if not already there
                        if (!getSt().getUsers().get(userNumeric).getChannels().contains(channelName)) {
                            getSt().getUsers().get(userNumeric).addChannel(channelName);
                        }
                        
                        // Get user's last join timestamp, skip checks if not available
                        var lastJoin = getSt().getChannel().get(channelName).getLastJoin().get(userNumeric);
                        if (lastJoin == null) {
                            return; // User not in lastJoin map yet, skip spam checks
                        }
                        
                        // Check if channel has lax spam detection enabled
                        boolean isLaxMode = getMi().getDb().isLaxChannel(channelName);
                        
                        // Load configurable thresholds from config file
                        var config = getMi().getConfig().getSpamFile();
                        int newUserTimeWindow = Integer.parseInt(config.getProperty("newUserTimeWindow", "300"));
                        
                        // Determine if user is "new" based on join time
                        long timeSinceJoin = time() - lastJoin;
                        boolean isNewUser = timeSinceJoin < newUserTimeWindow;
                        
                        var user = getSt().getUsers().get(userNumeric);
                        long currentTime = time();
                        
                        // === NEW INTELLIGENT SPAM DETECTION SYSTEM ===
                        
                        // Apply score decay (rehabilitate good behavior over time)
                        applyScoreDecay(user, currentTime);
                        
                        // Add message to history for pattern analysis
                        user.addMessageToHistory(message, currentTime, channelName);
                        user.setChannelLastMessage(channelName, message, currentTime);
                        
                        // Calculate comprehensive spam score
                        double spamScore = calculateSpamScore(user, message, channelName, currentTime);
                        
                        // Add to user's cumulative spam score
                        user.increaseSpamScore(spamScore);
                        
                        // Update last message time
                        user.setLastMessageTime(currentTime);
                        
                        // === CHECK FOR EXTREME SPAMMING (IMMEDIATE G-LINE) ===
                        double extremeSpamThreshold = Double.parseDouble(config.getProperty("extremeSpamThreshold", "100.0"));
                        
                        if (user.getSpamScore() >= extremeSpamThreshold) {
                            // Extreme spamming detected - apply immediate G-Line
                            String reason = String.format("Extreme spam detected (Score: %.1f/%.1f)", 
                                user.getSpamScore(), extremeSpamThreshold);
                            kickOrKillUserWithImmediateGLine(userNumeric, channelName, reason);
                            user.setSpamScore(0.0);
                            return;
                        }
                        
                        // Determine action threshold based on mode and user status
                        double actionThreshold;
                        if (isLaxMode) {
                            actionThreshold = isNewUser ? 60.0 : 80.0; // More lenient in lax mode
                        } else {
                            actionThreshold = isNewUser ? 40.0 : 60.0; // Stricter for new users
                        }
                        
                        // Check if action should be taken
                        if (user.getSpamScore() >= actionThreshold) {
                            String reason = String.format("Spam score threshold exceeded (%.1f/%.1f)", 
                                user.getSpamScore(), actionThreshold);
                            kickOrKillUser(userNumeric, channelName, reason);
                            user.setSpamScore(0.0); // Reset after action
                            return;
                        }
                        
                        // === LEGACY CHECKS (fallback for specific patterns) ===
                        
                        // Still check for immediate threats (very high single-message scores)
                        if (spamScore > 50.0) {
                            String reason = "High-risk spam pattern detected";
                            kickOrKillUser(userNumeric, channelName, reason);
                            user.setSpamScore(0.0);
                            return;
                        }
                        
                        // Legacy repeat check (for backward compatibility)
                        int normalRepeatNew = Integer.parseInt(config.getProperty("normalRepeatThresholdNew", "3"));
                        int normalRepeatEstablished = Integer.parseInt(config.getProperty("normalRepeatThresholdEstablished", "7"));
                        int laxRepeatNew = Integer.parseInt(config.getProperty("laxRepeatThresholdNew", "6"));
                        int laxRepeatEstablished = Integer.parseInt(config.getProperty("laxRepeatThresholdEstablished", "10"));
                        
                        int repeatThreshold;
                        if (isLaxMode) {
                            repeatThreshold = isNewUser ? laxRepeatNew : laxRepeatEstablished;
                        } else {
                            repeatThreshold = isNewUser ? normalRepeatNew : normalRepeatEstablished;
                        }
                        
                        if (user.getLine().equalsIgnoreCase(message)) {
                            var repeatCount = user.getRepeat();
                            repeatCount = repeatCount + 1;
                            user.setRepeat(repeatCount);
                            if (repeatCount > repeatThreshold) {
                                kickOrKillUser(userNumeric, channelName, "Repeating lines!");
                                user.setSpamScore(0.0);
                                return;
                            }
                        } else {
                            user.setRepeat(0);
                            user.setLine(message);
                        }
                        
                        // Legacy flood check with decay
                        int normalFloodNew = Integer.parseInt(config.getProperty("normalFloodThresholdNew", "2"));
                        int normalFloodEstablished = Integer.parseInt(config.getProperty("normalFloodThresholdEstablished", "5"));
                        int laxFloodNew = Integer.parseInt(config.getProperty("laxFloodThresholdNew", "5"));
                        int laxFloodEstablished = Integer.parseInt(config.getProperty("laxFloodThresholdEstablished", "8"));
                        
                        int floodThreshold;
                        if (isLaxMode) {
                            floodThreshold = isNewUser ? laxFloodNew : laxFloodEstablished;
                        } else {
                            floodThreshold = isNewUser ? normalFloodNew : normalFloodEstablished;
                        }
                        
                        var floodCount = user.getFlood();
                        floodCount = floodCount + 1;
                        user.setFlood(floodCount);
                        
                        // Decay flood counter over time
                        if (user.getLastMessageTime() > 0 && currentTime - user.getLastMessageTime() > 10) {
                            user.setFlood(Math.max(0, floodCount - 1));
                        }
                        
                        if (floodCount > floodThreshold) {
                            kickOrKillUser(userNumeric, channelName, "Flooding!");
                            user.setSpamScore(0.0);
                            return;
                        }
                    }
                // Handle user leaving/being kicked from channel
                } else if (elem[1].equals("L") || elem[1].equals("K")) {
                    removeUserFromChannel(elem[0], elem[2].toLowerCase());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private long time() {
        return System.currentTimeMillis() / 1000;
    }
    
    /**
     * Checks if a user has a suspicious ident that poses a security risk
     * (e.g., root, admin, administrator)
     * 
     * @param userNumeric The numeric of the user to check
     * @param nick The nickname of the user
     * @param ident The ident to check (will strip leading ~ if present)
     * @return true if user has suspicious ident and was killed, false otherwise
     */
    public boolean handleSuspiciousIdent(String userNumeric, String nick, String ident) {
        if (!enabled) {
            return false;
        }
        
        var config = getMi().getConfig().getSpamFile();
        boolean killSuspicious = Boolean.parseBoolean(config.getProperty("killSuspiciousIdents", "true"));
        
        if (!killSuspicious) {
            return false;
        }
        
        // Strip leading ~ from ident if present
        String cleanIdent = ident.startsWith("~") ? ident.substring(1) : ident;
        
        // Get list of suspicious idents from config
        String suspiciousIdentsList = config.getProperty("suspiciousIdents", 
            "root,admin,administrator,sysadmin,webmaster,hostmaster,postmaster,operator,oper,staff,moderator,mod");
        
        String[] suspiciousIdents = suspiciousIdentsList.split(",");
        
        // Check if ident matches any suspicious pattern
        for (String suspiciousIdent : suspiciousIdents) {
            if (cleanIdent.equalsIgnoreCase(suspiciousIdent.trim())) {
                var count = getMi().getDb().getSpamScanIdCount();
                count++;
                getMi().getDb().addId("Suspicious ident: " + cleanIdent);
                
                // Get user's host for sourceString
                var user = getSt().getUsers().get(userNumeric);
                String userHost = user != null ? user.getHost() : "unknown";
                
                // Build user string: host!nick
                String sourceString = String.format("%s!%s", userHost, nick);
                
                // Build reason
                String baseReason = String.format("Suspicious ident '%s' detected, potential security risk, ID: %d", cleanIdent, count);
                String killMessage = String.format("%s (%s)", sourceString, baseReason);
                
                getSt().sendText("%s D %s %d :%s", 
                        getNumeric(), userNumeric, time(), killMessage);
                
                // Remove user from internal tracking to prevent ghost
                getSt().getUsers().remove(userNumeric);
                
                LOG.log(Level.WARNING, "Killed user with suspicious ident: {0} (numeric: {1}, ident: {2})", 
                        new Object[]{nick, userNumeric, ident});
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * AntiKnocker detection - identifies Knocker spambots
     * Checks if nick and ident match a specific pattern but are not identical
     * 
     * @param nick The nickname to check
     * @param ident The ident to check (will strip leading ~ if present)
     * @return true if detected as Knocker bot, false otherwise
     */
    public boolean isKnockerBot(String nick, String ident) {
        if (ident.startsWith("~")) {
            ident = ident.substring(1);
        }
        var regex = "^(st|sn|cr|pl|pr|fr|fl|qu|br|gr|sh|sk|tr|kl|wr|bl|[bcdfgklmnprstvwz])([aeiou][aeiou][bcdfgklmnprstvwz])(ed|est|er|le|ly|y|ies|iest|ian|ion|est|ing|led|inger?|[abcdfgklmnprstvwz])$";
        return !ident.equalsIgnoreCase(nick) && nick.matches(regex) && ident.matches(regex);
    }
    
    /**
     * Handles detection and killing of Knocker spambots
     * 
     * @param userNumeric The numeric of the user to kill
     * @param nick The nickname of the suspected bot
     * @param ident The ident of the suspected bot
     * @return true if bot was detected and killed, false otherwise
     */
    public boolean handleKnockerDetection(String userNumeric, String nick, String ident) {
        if (!enabled) {
            return false;
        }
        
        if (isKnockerBot(nick, ident)) {
            var count = mi.getDb().getSpamScanIdCount();
            count++;
            mi.getDb().addId("Spambot!");
            
            // Get user's host for sourceString
            var user = getSt().getUsers().get(userNumeric);
            String userHost = user != null ? user.getHost() : "unknown";
            
            // Build user string: host!nick
            String sourceString = String.format("%s!%s", userHost, nick);
            
            // Build reason
            String baseReason = String.format("You are detected as Knocker Spambot, ID: %d", count);
            String killMessage = String.format("%s (%s)", sourceString, baseReason);
            
            getSt().sendText("%s D %s %d :%s", 
                    numeric, userNumeric, time(), killMessage);
            
            // Remove user from internal tracking to prevent ghost
            getSt().getUsers().remove(userNumeric);
            
            LOG.log(Level.INFO, "Killed Knocker spambot: {0} (numeric: {1}, ident: {2})", 
                    new Object[]{nick, userNumeric, ident});
            return true;
        }
        return false;
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
     * @return the numeric
     */
    public String getNumeric() {
        return numeric;
    }

    /**
     * @param numeric the numeric to set
     */
    public void setNumeric(String numeric) {
        this.numeric = numeric;
    }
    
    @Override
    public String getNumericSuffix() {
        return numericSuffix;
    }
    
    public void setNumericSuffix(String numericSuffix) {
        this.numericSuffix = numericSuffix;
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
     * @return the st
     */
    public SocketThread getSt() {
        return st;
    }

    /**
     * @param st the st to set
     */
    public void setSt(SocketThread st) {
        this.st = st;
    }
    private static final Logger LOG = Logger.getLogger(SpamScan.class.getName());

    private void removeUserFromChannel(String nick, String channel) {
        getSt().removeUser(nick, channel);
    }
    
    /**
     * Kicks or kills a user who violated spam rules with IMMEDIATE G-Line (no threshold)
     * Used for extreme spamming cases like spambots
     * 
     * @param userNumeric The numeric ID of the user
     * @param channelName The name of the channel
     * @param reason The reason for the action
     */
    private void kickOrKillUserWithImmediateGLine(String userNumeric, String channelName, String reason) {
        var count = getMi().getDb().getSpamScanIdCount();
        count++;
        getMi().getDb().addId(reason);
        
        var user = getSt().getUsers().get(userNumeric);
        if (user != null) {
            String userHost = user.getHost();
            String userNick = user.getNick();
            
            // Check if already G-Lined
            if (getMi().getDb().isGLined(userHost)) {
                LOG.log(Level.INFO, "User already G-Lined, skipping: {0} ({1})", new Object[]{userNick, userHost});
                return;
            }
            
            // Apply immediate G-Line without checking threshold
            applyImmediateGLine(userHost, userNick, reason, count);
            LOG.log(Level.WARNING, "IMMEDIATE G-Line applied for extreme spam: {0} ({1})", new Object[]{userNick, userHost});
        }
    }
    
    /**
     * Helper method to kick or kill a user based on channel moderation status
     * Also tracks violations for G-Line purposes
     * 
     * @param userNumeric The user's numeric
     * @param channelName The channel name
     * @param reason The reason for the action
     */
    private void kickOrKillUser(String userNumeric, String channelName, String reason) {
        var count = getMi().getDb().getSpamScanIdCount();
        count++;
        getMi().getDb().addId(reason);
        
        if (getSt().getChannel().get(channelName).isModerated() && 
            getSt().getChannel().get(channelName).getVoice().contains(userNumeric)) {
            // Remove voice in moderated channel
            sendText("%s%s M %s -v %s", getNumeric(), getNumericSuffix(), channelName, userNumeric);
            getSt().getUsers().get(userNumeric).setRepeat(0);
            getSt().getUsers().get(userNumeric).setFlood(0);
        } else {
            // Track violations and check if G-Line should be applied
            var user = getSt().getUsers().get(userNumeric);
            if (user != null) {
                String userHost = user.getHost();
                String userNick = user.getNick();
                
                // Check if already G-Lined - if so, don't kill (G-Line already handles it)
                if (getMi().getDb().isGLined(userHost)) {
                    LOG.log(Level.INFO, "User already G-Lined, skipping kill: {0} ({1})", new Object[]{userNick, userHost});
                    return;
                }
                
                // Check if threshold reached and apply G-Line if necessary
                boolean shouldGLine = checkAndApplyGLine(userHost, userNick, reason, count);
                
                if (shouldGLine) {
                    // G-Line was applied, no need to kill (G-Line will disconnect them)
                    LOG.log(Level.INFO, "User G-Lined (no kill sent): {0} ({1})", new Object[]{userNick, userHost});
                } else {
                    // Kill the user (not yet reached threshold for G-Line)
                    var config = getMi().getConfig().getSpamFile();
                    String violationUrl = config.getProperty("violationUrl", "");
                    
                    // Build user string: host!nick
                    String sourceString = String.format("%s!%s", userHost, userNick);
                    
                    // Build reason string with ID and optional URL
                    String baseReason = violationUrl.isEmpty() 
                        ? String.format("You are violating network rules (ID: %d)", count)
                        : String.format("You are violating network rules (ID: %d) - %s%d for more details!", count, violationUrl, count);
                    
                    // Combine: sourceString (reason)
                    String killMessage = String.format("%s (%s)", sourceString, baseReason);
                    
                    getSt().sendText("%s%s D %s %d :%s", 
                            getNumeric(), getNumericSuffix(), userNumeric, time(), killMessage);
                    
                    // Remove user from internal tracking to prevent ghost
                    getSt().getUsers().remove(userNumeric);
                    LOG.log(Level.INFO, "Killed user: {0} ({1}) - {2}", new Object[]{userNick, userHost, reason});
                }
            } else {
                // Fallback: just kill if user object not found
                var config = getMi().getConfig().getSpamFile();
                String violationUrl = config.getProperty("violationUrl", "");
                
                // Build reason string with ID and optional URL
                String killMessage = violationUrl.isEmpty() 
                    ? String.format("You are violating network rules (ID: %d)", count)
                    : String.format("You are violating network rules (ID: %d) - %s%d for more details!", count, violationUrl, count);
                
                getSt().sendText("%s%s D %s %d :%s", 
                        getNumeric(), getNumericSuffix(), userNumeric, time(), killMessage);
                
                // Remove user from internal tracking to prevent ghost
                getSt().getUsers().remove(userNumeric);
                LOG.log(Level.INFO, "Killed user (fallback): {0}", userNumeric);
            }
        }
    }
    
    /**
     * Checks if a user@host should be G-Lined based on kill count
     * 
     * @param userHost The user@host string (ident@host)
     * @param nick The user's nickname
     * @param reason The reason for the action
     * @param idCount The ID count for the reason
     * @return true if G-Line was applied, false otherwise
     */
    private boolean checkAndApplyGLine(String userHost, String nick, String reason, int idCount) {
        var config = getMi().getConfig().getSpamFile();
        boolean glineEnabled = Boolean.parseBoolean(config.getProperty("enableGLine", "true"));
        
        if (!glineEnabled) {
            return false;
        }
        
        // Check if already G-Lined
        if (getMi().getDb().isGLined(userHost)) {
            return false;
        }
        
        // Track this violation and get count
        int killCount = getMi().getDb().trackKillForGLine(userHost, time());
        int glineThreshold = Integer.parseInt(config.getProperty("glineAfterKills", "3"));
        
        // Apply G-Line if threshold reached
        if (killCount >= glineThreshold) {
            int glineDuration = Integer.parseInt(config.getProperty("glineDuration", "86400"));
            
            // Extract ident and host for G-Line pattern: nick!*ident@host
            String glinePattern;
            if (userHost.contains("@")) {
                String[] parts = userHost.split("@", 2);
                String ident = parts[0];
                String host = parts[1];
                
                // Strip leading ~ from ident if present
                if (ident.startsWith("~")) {
                    ident = ident.substring(1);
                }
                
                // Format: nick!*ident@host (exact nick, wildcard partial ident, exact host)
                glinePattern = nick + "!*" + ident + "@" + host;
            } else {
                // Fallback if no @ found (shouldn't happen)
                glinePattern = nick + "!*@" + userHost;
            }
            
            // Apply Global G-Line via server numeric
            // P10 Format: <server> GL <target-server> !+<mask> <duration> <timestamp> <lastmod> :<reason>
            // ! prefix = force global G-Line across all servers
            // Target server * = all servers
            long currentTime = time();
            
            String violationUrl = config.getProperty("violationUrl", "");
            String glineMessage = violationUrl.isEmpty() 
                ? String.format("You are violating network rules (Violations: %d, ID: %d)", killCount, idCount)
                : String.format("You are violating network rules (Violations: %d, ID: %d) - %s%d", killCount, idCount, violationUrl, idCount);
            
            getSt().sendText("%s GL * !+%s %d %d %d :%s", 
                    getNumeric(), glinePattern, glineDuration, currentTime, currentTime, glineMessage);
            
            // Mark as G-Lined in database
            getMi().getDb().markAsGLined(userHost);
            
            LOG.log(Level.WARNING, "Applied GLOBAL G-Line to {0} for {1} seconds (Violations: {2}, Reason: {3})", 
                    new Object[]{glinePattern, glineDuration, killCount, reason});
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Applies an immediate G-Line without checking kill count threshold
     * Used for extreme spamming cases like spambots
     * 
     * @param userHost The user@host string (ident@host)
     * @param nick The user's nickname
     * @param reason The reason for the action
     * @param idCount The ID count for the reason
     */
    private void applyImmediateGLine(String userHost, String nick, String reason, int idCount) {
        var config = getMi().getConfig().getSpamFile();
        boolean glineEnabled = Boolean.parseBoolean(config.getProperty("enableGLine", "true"));
        
        if (!glineEnabled) {
            LOG.log(Level.WARNING, "G-Line disabled, cannot apply immediate G-Line for: {0}", userHost);
            return;
        }
        
        // Check if already G-Lined
        if (getMi().getDb().isGLined(userHost)) {
            return;
        }
        
        int glineDuration = Integer.parseInt(config.getProperty("glineDuration", "86400"));
        
        // Extract ident and host for G-Line pattern: nick!*ident@host
        String glinePattern;
        if (userHost.contains("@")) {
            String[] parts = userHost.split("@", 2);
            String ident = parts[0];
            String host = parts[1];
            
            // Strip leading ~ from ident if present
            if (ident.startsWith("~")) {
                ident = ident.substring(1);
            }
            
            // Format: nick!*ident@host (exact nick, wildcard partial ident, exact host)
            glinePattern = nick + "!*" + ident + "@" + host;
        } else {
            // Fallback if no @ found (shouldn't happen)
            glinePattern = nick + "!*@" + userHost;
        }
        
        // Apply Global G-Line via server numeric
        long currentTime = time();
        
        String violationUrl = config.getProperty("violationUrl", "");
        String glineMessage = violationUrl.isEmpty() 
            ? String.format("EXTREME SPAM DETECTED - Immediate ban (ID: %d)", idCount)
            : String.format("EXTREME SPAM DETECTED - Immediate ban (ID: %d) - %s%d", idCount, violationUrl, idCount);
        
        getSt().sendText("%s GL * !+%s %d %d %d :%s", 
                getNumeric(), glinePattern, glineDuration, currentTime, currentTime, glineMessage);
        
        // Mark as G-Lined in database (with extreme spam tracking)
        getMi().getDb().markAsGLined(userHost);
        getMi().getDb().trackKillForGLine(userHost, currentTime); // Track for statistics
        
        LOG.log(Level.SEVERE, "IMMEDIATE GLOBAL G-Line applied to {0} for {1} seconds (EXTREME SPAM: {2})", 
                new Object[]{glinePattern, glineDuration, reason});
    }
    
    /**
     * Checks if a message contains excessive caps lock (spam indicator)
     * 
     * @param message The message to check
     * @return true if message has >70% uppercase letters
     */
    private boolean isExcessiveCaps(String message) {
        if (message == null || message.length() < 10) {
            return false; // Too short to judge
        }
        
        int uppercaseCount = 0;
        int letterCount = 0;
        
        for (char c : message.toCharArray()) {
            if (Character.isLetter(c)) {
                letterCount++;
                if (Character.isUpperCase(c)) {
                    uppercaseCount++;
                }
            }
        }
        
        // Need at least 5 letters to make a judgment
        if (letterCount < 5) {
            return false;
        }
        
        // Calculate percentage - letterCount is guaranteed >= 5 here
        double capsPercentage = (double) uppercaseCount / letterCount;
        
        // More than 70% caps is considered excessive
        return capsPercentage > 0.70;
    }
    
    /**
     * Checks if a message contains multiple URLs (common spam pattern)
     * 
     * @param message The message to check
     * @return true if message contains 2 or more URLs
     */
    private boolean containsMultipleUrls(String message) {
        // Simple URL detection pattern
        String urlPattern = "(?i)(https?://|www\\.)\\S+";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(urlPattern);
        java.util.regex.Matcher matcher = pattern.matcher(message);
        
        int urlCount = 0;
        while (matcher.find()) {
            urlCount++;
            if (urlCount >= 2) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Calculate Levenshtein distance between two strings
     * Used for similarity detection of spam messages
     * 
     * @param s1 First string
     * @param s2 Second string
     * @return The Levenshtein distance
     */
    private int calculateLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1),     // insertion
                    dp[i - 1][j - 1] + cost); // substitution
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    /**
     * Calculate similarity percentage between two strings
     * 
     * @param s1 First string
     * @param s2 Second string
     * @return Similarity percentage (0.0 to 1.0)
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) {
            return 1.0;
        }
        
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return 1.0;
        }
        
        int distance = calculateLevenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLen);
    }
    
    /**
     * Check if message is similar to previous messages (spam detection)
     * 
     * @param user The user object
     * @param message The current message
     * @param currentTime Current timestamp
     * @return true if similar spam detected
     */
    private boolean isSimilarSpam(Users user, String message, long currentTime) {
        var config = getMi().getConfig().getSpamFile();
        double similarityThreshold = Double.parseDouble(config.getProperty("similarityThreshold", "0.8"));
        int timeWindow = Integer.parseInt(config.getProperty("similarityTimeWindow", "60"));
        
        // Get recent messages
        var recentMessages = user.getRecentMessages(timeWindow, currentTime);
        
        // Check similarity with recent messages
        int similarCount = 0;
        for (Users.MessageRecord msgRecord : recentMessages) {
            double similarity = calculateSimilarity(message.toLowerCase(), msgRecord.getMessage().toLowerCase());
            if (similarity >= similarityThreshold) {
                similarCount++;
                if (similarCount >= 2) {
                    return true; // Multiple similar messages detected
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check for cross-channel spam (same message in multiple channels)
     * 
     * @param user The user object
     * @param message The current message
     * @param channel The current channel
     * @param currentTime Current timestamp
     * @return true if cross-channel spam detected
     */
    private boolean isCrossChannelSpam(Users user, String message, String channel, long currentTime) {
        var config = getMi().getConfig().getSpamFile();
        int timeWindow = Integer.parseInt(config.getProperty("crossChannelTimeWindow", "30"));
        double similarityThreshold = Double.parseDouble(config.getProperty("crossChannelSimilarity", "0.9"));
        
        int matchCount = 0;
        for (Users.MessageRecord lastMsg : user.getChannelLastMessages().values()) {
            // Skip same channel
            if (lastMsg.getChannel().equalsIgnoreCase(channel)) {
                continue;
            }
            
            // Check if message is recent
            if (currentTime - lastMsg.getTimestamp() <= timeWindow) {
                // Check similarity
                double similarity = calculateSimilarity(message.toLowerCase(), lastMsg.getMessage().toLowerCase());
                if (similarity >= similarityThreshold) {
                    matchCount++;
                    if (matchCount >= 2) {
                        return true; // Same message in 3+ channels
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Analyze URL for suspicious patterns
     * 
     * @param message The message to analyze
     * @return Spam score contribution (0.0 = clean, higher = more suspicious)
     */
    private double analyzeUrlSuspicion(String message) {
        double suspicionScore = 0.0;
        
        var config = getMi().getConfig().getSpamFile();
        String suspiciousTLDs = config.getProperty("suspiciousTLDs", "tk,ml,ga,cf,gq,pw,top,xyz");
        String[] tldList = suspiciousTLDs.split(",");
        
        // Extract URLs
        String urlPattern = "(?i)(https?://|www\\.)[^\\s]+";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(urlPattern);
        java.util.regex.Matcher matcher = pattern.matcher(message);
        
        while (matcher.find()) {
            String url = matcher.group().toLowerCase();
            
            // Check for suspicious TLDs
            for (String tld : tldList) {
                if (url.contains("." + tld.trim() + "/") || url.endsWith("." + tld.trim())) {
                    suspicionScore += 15.0; // High suspicion for these TLDs
                }
            }
            
            // Check for URL shorteners (common in spam)
            String[] shorteners = {"bit.ly", "tinyurl.com", "goo.gl", "ow.ly", "is.gd", "t.co", "buff.ly"};
            for (String shortener : shorteners) {
                if (url.contains(shortener)) {
                    suspicionScore += 8.0; // Medium suspicion for shorteners
                }
            }
            
            // Check for IP addresses in URLs (suspicious)
            if (url.matches(".*\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}.*")) {
                suspicionScore += 12.0;
            }
            
            // Check for excessive subdomain levels (common in phishing)
            long dotCount = url.chars().filter(ch -> ch == '.').count();
            if (dotCount > 4) {
                suspicionScore += 5.0;
            }
        }
        
        return suspicionScore;
    }
    
    /**
     * Calculate comprehensive spam score for a message
     * Combines multiple factors to determine spam likelihood
     * 
     * @param user The user object
     * @param message The message content
     * @param channel The channel name
     * @param currentTime Current timestamp
     * @return Spam score (0-100+, higher = more likely spam)
     */
    private double calculateSpamScore(Users user, String message, String channel, long currentTime) {
        double score = 0.0;
        var config = getMi().getConfig().getSpamFile();
        
        // Factor 1: Message rate (time between messages)
        if (user.getLastMessageTime() > 0) {
            long timeDiff = currentTime - user.getLastMessageTime();
            if (timeDiff < 2) { // Less than 2 seconds between messages
                score += 10.0;
            } else if (timeDiff < 5) { // Less than 5 seconds
                score += 5.0;
            }
        }
        
        // Factor 2: Excessive caps
        if (isExcessiveCaps(message)) {
            score += 15.0;
        }
        
        // Factor 3: Multiple URLs
        if (containsMultipleUrls(message)) {
            score += 20.0;
        }
        
        // Factor 4: URL suspicion analysis
        score += analyzeUrlSuspicion(message);
        
        // Factor 5: Homoglyphs
        if (getMi().getHomoglyphs().scanForHomoglyphs(message)) {
            score += 25.0;
        }
        
        // Factor 6: Similar spam (repetitive similar messages)
        if (isSimilarSpam(user, message, currentTime)) {
            score += 20.0;
        }
        
        // Factor 7: Cross-channel spam
        if (isCrossChannelSpam(user, message, channel, currentTime)) {
            score += 30.0;
        }
        
        // Factor 8: Badword detection
        var badwordList = getMi().getConfig().getBadwordFile();
        for (var key : badwordList.keySet()) {
            var badword = (String) key;
            if (message.toLowerCase().contains(badword.toLowerCase())) {
                score += 40.0; // High score for badwords
                break; // Only count once
            }
        }
        
        // Factor 9: Message length (very short or very long)
        if (message.length() < 5) {
            score += 5.0; // Short messages can be spam
        } else if (message.length() > 400) {
            score += 10.0; // Very long messages can be spam
        }
        
        // Factor 10: Number repetition (common in spam)
        long digitCount = message.chars().filter(Character::isDigit).count();
        if (digitCount > message.length() * 0.3) { // More than 30% digits
            score += 8.0;
        }
        
        return score;
    }
    
    /**
     * Apply spam score decay over time (rehabilitate users)
     * Called periodically to decrease spam scores
     * 
     * @param user The user object
     * @param currentTime Current timestamp
     */
    private void applyScoreDecay(Users user, long currentTime) {
        double decayRate = Double.parseDouble(getMi().getConfig().getSpamFile().getProperty("scoreDecayRate", "0.5"));
        int decayInterval = Integer.parseInt(getMi().getConfig().getSpamFile().getProperty("scoreDecayInterval", "30"));
        
        // Decay score if enough time has passed
        if (user.getLastMessageTime() > 0) {
            long timeSinceLastMessage = currentTime - user.getLastMessageTime();
            int decayPeriods = (int) (timeSinceLastMessage / decayInterval);
            
            if (decayPeriods > 0) {
                double totalDecay = decayRate * decayPeriods;
                user.decreaseSpamScore(totalDecay);
            }
        }
        
        // Clean up old messages from history
        user.cleanupOldMessages(300, currentTime); // Keep last 5 minutes
    }
}
