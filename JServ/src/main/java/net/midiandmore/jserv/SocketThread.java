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
import java.util.logging.Logger;

public final class SocketThread implements Runnable, Software {

    private final long serverStartTime = System.currentTimeMillis() / 1000;

    protected void joinChannel(String channel, String numeric, String service) {
        if (getChannel().containsKey(channel.toLowerCase())) {
            sendText("%s J %s %d", service, channel, time());
        } else {
            sendText("%s C %s %d", service, channel, time());
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

    public SocketThread(JServ mi) {
        setMi(mi);
        setUsers(new HashMap<>());
        setChannel(new HashMap<>());
        setAuthed(new HashMap<>());
        setBurst(false);
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
     * P10 N command format: <server> N <nick> <hopcount> <timestamp> <ident> <host> <modes> <base64ip> <numeric> :<realname>
     * Example: AB N WarPigs 1 1766478053 warpigs localhost +i B]AAAB ABAAA :realname
     *          In this example, ABAAA is the user token/numeric, which is the last element before ":"
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
     * P10 N command format: <server> N <nick> <hopcount> <timestamp> <ident> <host> <modes> <base64ip> <numeric> :<realname>
     * Example: AB N WarPigs 1 1766478053 warpigs localhost +i B]AAAB ABAAA :realname
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
    
    /**
     * Critical section: Check for duplicates, parse user info, and register user
     * Returns true if registration succeeded, false if user was killed/handled
     */
    private boolean checkAndRegisterUser(String[] elem, String rawLine, String jnumeric) {
        // Parse user info
        // elem[2] = nickname (WarPigs)
        // elem[7] = user modes (+i, +r for authenticated, +k for service, +x for hidden host, +o for oper)
        // User token/numeric is extracted from rawLine (last element before " :")
        String nickname = elem[2];
        var priv = elem[7].contains("r");  // +r means user is authenticated
        var service = elem[7].contains("k");
        var x = elem[7].contains("x");
        var o = elem[7].contains("o");
        String acc = null;
        String userToken = extractNumericFromNCommand(rawLine);
        
        if (priv) {
            if (elem[9].contains(":")) {
                acc = elem[9].split(":", 2)[0];
            } else if (elem[8].contains(":")) {
                acc = elem[8].split(":", 2)[0];
            } else {
                acc = "";
            }
        } else if (elem[9].contains("@")) {
            acc = "";
        } else {
            acc = "";
        }
        var hosts = elem[5] + "@" + elem[6];
        
        // Check if user token already exists (prevent duplicates)
        if (getUsers().containsKey(userToken)) {
            System.out.printf("User token %s already exists - possible duplicate, updating...\n", userToken);
            // Update existing user instead of creating duplicate
            getUsers().get(userToken).setNick(nickname);
            getUsers().get(userToken).setAccount(acc);
            getUsers().get(userToken).setHost(hosts);
            getUsers().get(userToken).setX(x);
            getUsers().get(userToken).setService(service);
            getUsers().get(userToken).setOper(o);
            return false; // Don't propagate, just updated existing
        }
        
        // Let modules handle new user (e.g., SpamScan checks for bots, HostServ sets vhost)
        boolean userHandled = false;
        for (Module module : getModuleManager().getAllModules().values()) {
            if (module.isEnabled()) {
                if (module.handleNewUser(userToken, nickname, elem[5], elem[6], acc, jnumeric)) {
                    userHandled = true;
                    break; // User was killed/handled by module
                }
            }
        }
        
        if (!userHandled) {
            // ATOMIC: Register user (within lock)
            getUsers().put(userToken, new Users(userToken, nickname, acc, hosts));
            getUsers().get(userToken).setX(x);
            getUsers().get(userToken).setService(service);
            getUsers().get(userToken).setOper(o);
            if (!acc.isBlank()) {
                getMi().getDb().updateData("lastuserhost", acc, hosts);
                getMi().getDb().updateData("lastpasschng", acc, time());
            }
            return true; // Proceed with propagation
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
            
            var list = getMi().getDb().getChannels();
            var nicks = getMi().getDb().getData();
            for (var channel : list) {
                if (channel[1].startsWith("#")) {
                    if (!getBursts().containsKey(channel[1].toLowerCase())) {
                        getBursts().put(channel[1].toLowerCase(), new Burst(channel[1]));
                    }
                    if (channel[27] != null && !channel[27].isBlank()) {
                        getBursts().get(channel[1].toLowerCase()).setTime(Long.parseLong(channel[27]));
                    } else {
                        getBursts().get(channel[1].toLowerCase()).setTime(time());
                    }
                    var cid = channel[0];
                    var ua = new ArrayList<String>();
                    for (var nick : nicks) {
                        var nid = nick[0];
                        var auth = getMi().getDb().getChanUser(Long.parseLong(nid), Long.parseLong(cid));
                        if (auth != null) {
                            var users = getUsers().keySet();
                            for (var user : users) {
                                var u = getUsers().get(user);
                                if (ua.contains(u.getId())) {
                                    continue;
                                }
                                ua.add(u.getId());
                                if (u.getAccount().equalsIgnoreCase(nick[1]) && getChannel().get(channel[1].toLowerCase()).getUsers().contains(user)) {
                                    if (isOp(Integer.parseInt(auth[0]))) {
                                        getBursts().get(channel[1].toLowerCase()).getUsers().add(user + ":o");
                                    } else if (isVoice(Integer.parseInt(auth[0]))) {
                                        getBursts().get(channel[1].toLowerCase()).getUsers().add(user + ":v");
                                    }
                                }
                            }

                        }
                    }
                }
            }
            System.out.println("Successfully connected...");
            sendText("%s EB", jnumeric);
            while (!getSocket().isClosed() && (content = getBr().readLine()) != null && isRuns()) {
                try {
                    var elem = content.split(" ");
                    if (content.startsWith("SERVER")) {
                        setServerNumeric(content.split(" ")[SERVERNAME_INDEX].substring(0, 1));
                        System.out.println("Getting SERVER response...");
                    } else if (elem[1].equals("EB") && !isBurst()) {
                        setBurst(true);
                        sendText("%s EA", jnumeric);
                        System.out.printf("Joining %d channels for the services...\r\n", list.size());
                        var bursts = getBursts().keySet();
                        for (var burst : bursts) {
                            var nicks1 = getBursts().get(burst).getUsers().toArray();
                            for (var nameObj : nicks1) {
                                var name = String.valueOf(nameObj);
                                if (name.startsWith(jnumeric)) {
                                    joinChannel(burst, jnumeric, name);
                                }
                            }
                        }
                        System.out.println("Channels joined...");
                    } else if (elem[1].equals("J") || elem[1].equals("C")) {
                        var channel = elem[2];
                        var names = elem[0];
                        var user = new String[1];
                        user[0] = names;
                        if (getChannel().containsKey(channel.toLowerCase())) {
                            getChannel().get(channel.toLowerCase()).addUser(names);
                            getChannel().get(channel.toLowerCase()).getLastJoin().put(names, time());
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
                    } else if (elem[1].equals("N") && elem.length == 4) {
                        getUsers().get(elem[0]).setNick(elem[2]);
                    } else if (elem[1].equals("B") && elem.length == 7) {
                        var channel = elem[2].toLowerCase();
                        var modes = elem[4];
                        var names = elem[6].split(",");
                        getChannel().put(channel.toLowerCase(), new Channel(channel, modes, names));
                        // Add channel to each user's channel list
                        for (var userName : names) {
                            var userNumeric = userName.split(":")[0]; // Strip mode suffixes
                            if (getUsers().containsKey(userNumeric)) {
                                getUsers().get(userNumeric).addChannel(channel);
                            }
                        }
                    } else if (elem[1].equals("B") && elem.length >= 6) {
                        var channel = elem[2].toLowerCase();
                        var modes = elem[4];
                        var names = elem[5].split(",");
                        getChannel().put(channel.toLowerCase(), new Channel(channel, modes, names));
                        // Add channel to each user's channel list
                        for (var userName : names) {
                            var userNumeric = userName.split(":")[0]; // Strip mode suffixes
                            if (getUsers().containsKey(userNumeric)) {
                                getUsers().get(userNumeric).addChannel(channel);
                            }
                        }
                    } else if (elem[1].equals("B") && elem.length == 5) {
                        var channel = elem[2].toLowerCase();
                        var modes = "";
                        var names = elem[4].split(",");
                        getChannel().put(channel.toLowerCase(), new Channel(channel, modes, names));
                        // Add channel to each user's channel list
                        for (var userName : names) {
                            var userNumeric = userName.split(":")[0]; // Strip mode suffixes
                            if (getUsers().containsKey(userNumeric)) {
                                getUsers().get(userNumeric).addChannel(channel);
                            }
                        }
                    } else if (elem[1].equals("C")) {
                        var channel = elem[2].toLowerCase();
                        var names = new String[1];
                        names[0] = elem[0] + ":q";
                        getChannel().put(channel.toLowerCase(), new Channel(channel, "", names));
                        // Add channel to user's channel list
                        if (getUsers().containsKey(elem[0])) {
                            getUsers().get(elem[0]).addChannel(channel);
                        }
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
                    } else if (elem[1].equals("M") && elem.length == 4) {
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
            // Send SQUIT before closing connection
            if (getPw() != null && !getSocket().isClosed()) {
                try {
                    System.out.println("Sending SQUIT command...");
                    sendText("SQUIT %s :Service shutting down", getServername());
                    getPw().flush();
                    Thread.sleep(500); // Give time for SQUIT to be sent
                } catch (Exception e) {
                    LOG.warning("Failed to send SQUIT: " + e.getMessage());
                }
            }
            
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
        sendText("%s%s L %s", numeric, service, channel);
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
        if (!nick.isBlank()) {
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
        return false;
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
