/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.digest.DigestUtils;

public final class HostServ implements Software, Module {
    
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
    private HashMap<String, String> nicks;
    private HashMap<String, String> hosts;
    private HashMap<String, String> accounts;
    private HashMap<String, String> x;
    private SocketThread st;
    private boolean reg;

    public HostServ(JServ mi, SocketThread st, PrintWriter pw, BufferedReader br) {
        initialize(mi, st, pw, br);
    }
    
    @Override
    public void initialize(JServ jserv, SocketThread socketThread, PrintWriter pw, BufferedReader br) {
        setMi(jserv);
        setPw(pw);
        setBr(br);
        setSt(socketThread);
        setReg(false);
        this.enabled = false;
        LOG.log(Level.INFO, "HostServ module initialized");
    }

    @Override
    public void handshake(String nick, String servername, String description, String numeric, String identd) {
        if (!enabled) {
            LOG.log(Level.WARNING, "HostServ handshake called but module is disabled");
            return;
        }
        setServername(servername);
        setNick(nick);
        setIdentd(identd);
        setDescription(description);
        setNumeric(numeric);
        LOG.log(Level.INFO, "Registering HostServ nick: {0}", getNick());
        sendText("%s N %s 2 %d %s %s +oikr - %s:%d U]AEB %s%s :%s", getNumeric(), getNick(), time(), getIdentd(), getServername(), getNick(), time(), getNumeric(), getNumericSuffix(), getDescription());
    }
    
    @Override
    public void shutdown() {
        LOG.log(Level.INFO, "HostServ module shutting down");
        if (enabled && getNumeric() != null && getPw() != null) {
            // Send QUIT command to properly log out the service
            try {
                sendText("%s%s Q :Service shutting down", getNumeric(), getNumericSuffix());
                LOG.log(Level.INFO, "HostServ sent QUIT command");
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to send QUIT command for HostServ", e);
            }
        }
        this.enabled = false;
        // Clean up resources if needed
    }
    
    @Override
    public String getModuleName() {
        return "HostServ";
    }
    
    @Override
    public String getNumericSuffix() {
        return numericSuffix;
    }
    
    public void setNumericSuffix(String numericSuffix) {
        this.numericSuffix = numericSuffix;
    }
    
    @Override
    public void registerBurstChannels(java.util.HashMap<String, Burst> bursts, String serverNumeric) {
        if (!enabled) {
            return;
        }
        // HostServ joins #twilightzone
        String channel = "#twilightzone";
        if (!bursts.containsKey(channel.toLowerCase())) {
            bursts.put(channel.toLowerCase(), new Burst(channel));
        }
        bursts.get(channel.toLowerCase()).getUsers().add(serverNumeric + getNumericSuffix());
        LOG.log(Level.INFO, "HostServ registered burst channel: {0}", channel);
    }
    
    @Override
    public boolean handleNewUser(String numeric, String nick, String ident, String host, String account, String serverNumeric) {
        if (!enabled || account == null || account.isBlank()) {
            return false;
        }
        LOG.log(Level.INFO, "HostServ.handleNewUser: numeric={0}, nick={1}, account={2}", 
                new Object[]{numeric, nick, account});
        // Set virtual host if user has one configured
        setVirtualHost(numeric, account, serverNumeric);
        return false; // User not killed, just vhost set
    }
    
    @Override
    public void handleAuthentication(String numeric, String account, String serverNumeric) {
        if (!enabled || account == null || account.isBlank()) {
            return;
        }
        // Set virtual host if user has one configured
        setVirtualHost(numeric, account, serverNumeric);
    }
    
    /**
     * Set virtual host for authenticated user
     * 
     * @param numeric User numeric
     * @param account User account
     * @param serverNumeric Server numeric prefix
     */
    private void setVirtualHost(String numeric, String account, String serverNumeric) {
        LOG.log(Level.INFO, "setVirtualHost: numeric={0}, account={1}, registered={2}", 
                new Object[]{numeric, account, getMi().getDb().isRegistered(account)});
        
        if (!getMi().getDb().isRegistered(account)) {
            return;
        }
        
        String vhost = getMi().getDb().getHost(account);
        LOG.log(Level.INFO, "setVirtualHost: vhost={0}", vhost);
        
        if (vhost != null && vhost.contains("@")) {
            String[] sethost = vhost.split("@", 2);
            sendText("%s SH %s %s %s", serverNumeric, numeric, sethost[0], sethost[1]);
            LOG.log(Level.INFO, "Set vhost for {0}: {1}", new Object[]{account, vhost});
        }
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public void enable() {
        this.enabled = true;
        LOG.log(Level.INFO, "HostServ module enabled");
    }
    
    @Override
    public void disable() {
        this.enabled = false;
        LOG.log(Level.INFO, "HostServ module disabled");
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

    protected String parseCloak(String host) {
        var sb = new StringBuilder();
        try {
            if (host.contains(":")) {
                var tokens = host.split(":");
                for (var elem : tokens) {
                    if (elem.isBlank()) {
                        continue;
                    }
                    sb.append(parse(elem));
                    sb.append(".");
                }
                sb.append("ip");

            } else {
                var add = InetAddress.getByName(host).getHostAddress();
                if (add.contains(".")) {
                    var tokens = add.split("\\.");
                    for (var elem : tokens) {
                        sb.append(parse(elem));
                        sb.append(".");
                    }
                    sb.append("ip");
                } else if (add.contains(":")) {
                    var tokens = add.split(":");
                    for (var elem : tokens) {
                        if (elem.isBlank() || elem.equals("0")) {
                            continue;
                        }
                        sb.append(parse(elem));
                        sb.append(".");
                    }
                    sb.append("ip");
                }
            }
        } catch (UnknownHostException ex) {
        }
        if (sb.isEmpty()) {
            sb.append(host);
        }
        return sb.toString();
    }

    private String parse(String text) {
        var buf = DigestUtils.sha256Hex(text).toCharArray();
        var sb = new StringBuilder();
        var i = 0;
        for (var chr : buf) {
            sb.append(chr);
            if (i >= 3) {
                break;
            }
            i++;
        }
        return sb.toString();
    }

    @Override
    public void parseLine(String text) {
        if (!enabled) {
            return;
        }
        try {
            text = text.trim();
            var elem = text.split(" ");
            if (getSt().getServerNumeric() != null) {
                if (elem[1].equals("P") && elem[2].equals(getNumeric() + getNumericSuffix())) {
                    var target = elem[2];
                    if (target.startsWith("#") || target.startsWith("!") || target.startsWith("&")) {
                        return;
                    }
                    var sb = new StringBuilder();
                    for (var i = 3; i < elem.length; i++) {
                        sb.append(elem[i]);
                        sb.append(" ");
                    }
                    var command = String.join(" ", java.util.Arrays.copyOfRange(elem, 3, elem.length)).trim();
                    if (command.startsWith(":")) {
                        command = command.substring(1);
                    }
                    var nick = getSt().getUsers().get(elem[0]).getAccount();
                    var notice = "O";
                    if (!getSt().isNotice(nick)) {
                        notice = "P";
                    }
                    var auth = command.split(" ");
                    if (auth[0].equalsIgnoreCase("VHOST")) {
                        if (auth.length <= 2) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "To few parameters...");
                        } else if (!getSt().isAuthed(nick)) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "You are not authed");
                        } else if (!isMoreAsAnWeek(getMi().getDb().getHostTimestamp(nick)) && !getSt().isPrivileged(nick)) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "You cannot change currently the VHost, please try again in few days...");
                        } else if (checkHostChars(auth[2]) == 0 && checkIdentChars(auth[1]) == 0) {
                            sendText("%s SH %s %s %s", getNumeric(), elem[0], auth[1], auth[2]);
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "%s@%s is now your VHost...", auth[1], auth[2]);
                            getMi().getDb().addHost(nick, auth[1], auth[2]);
                        } else if (checkHostChars(auth[2]) == 2 && checkIdentChars(auth[1]) == 2) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Your VHost is too long...");
                        } else if (checkHostChars(auth[2]) == 4 && checkIdentChars(auth[1]) == 4) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Your VHost contains invalid characters...");
                        } else if (checkHostChars(auth[2]) == 3 && checkIdentChars(auth[1]) == 3) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Your VHost is too weak...");
                        } else {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Invalid host...");
                        }
                    } else if (auth[0].equalsIgnoreCase("UHOST")) {
                        if (!getSt().isAuthed(nick)) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], Messages.get("QM_UNKNOWNCMD", auth[0].toUpperCase()));
                        } else if (!getSt().isPrivileged(nick)) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], Messages.get("QM_UNKNOWNCMD", auth[0].toUpperCase()));
                        } else if (auth.length <= 3) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "To few parameters...");
                        } else if (checkHostChars(auth[3]) == 0 && checkIdentChars(auth[2]) == 0) {
                            var users = getSt().getUserName(auth[1]);
                            var unu = getSt().getUserAccount(auth[1]);
                            sendText("%s SH %s %s %s", getNumeric(), users, auth[2], auth[3]);
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "%s has now the VHost: %s@%s", auth[1], auth[2], auth[3]);
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, users, "%s has changed your VHost to %s@%s", nick, auth[2], auth[3]);
                            if (getSt().isAuthed(auth[1])) {
                                getMi().getDb().addHost(unu, auth[2], auth[3]);
                            }
                        } else if (checkHostChars(auth[3]) == 2 && checkIdentChars(auth[2]) == 2) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "The VHost is too long...");
                        } else if (checkHostChars(auth[3]) == 4 && checkIdentChars(auth[2]) == 4) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "The VHost contains invalid characters...");
                        } else if (checkHostChars(auth[3]) == 3 && checkIdentChars(auth[2]) == 3) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "The VHost is too weak...");
                        } else {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Invalid host...");
                        }
                    } else if (auth[0].equalsIgnoreCase("SHOWCOMMANDS")) {
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], Messages.get("QM_COMMANDLIST"));
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "   HELP             Shows a specific help to a command.");
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "   SHOWCOMMANDS     Shows this list.");
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "   VHOST            Sets your VHost. (You must be authed)");
                        if (getSt().isAuthed(nick) && getSt().isPrivileged(nick)) {
                            getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "   UHOST            Sets VHost for other users.");
                        }
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "   VERSION          Print version info.");
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], Messages.get("QM_ENDOFLIST"));
                    } else if (auth[0].equalsIgnoreCase("VERSION")) {
                        Software.BuildInfo buildInfo = Software.getBuildInfo();
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "HostServ v%s by %s", buildInfo.getFullVersion(), VENDOR);
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Based on JServ v%s", buildInfo.getFullVersion());
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], "Created by %s", AUTHOR);
                    } else {
                        getSt().sendNotice(getNumeric(), getNumericSuffix(), notice, elem[0], Messages.get("QM_UNKNOWNCMD", auth[0].toUpperCase()));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isMoreAsAnWeek(long timestamp) {
        return timestamp <= (time() - 3600 * 24 * 7);
    }

    private static final int HOST_OK = 0;
    private static final int HOST_TOO_LONG = 2;
    private static final int HOST_TOO_SHORT = 3;
    private static final int HOST_INVALID = 4;

    private int checkHostChars(String host) {
        var vhost = host.toCharArray();
        if (vhost.length > 63) {
            return HOST_TOO_LONG;
        }
        if (vhost.length < 1) {
            return HOST_TOO_SHORT;
        }
        if (host.matches("[a-zA-Z0-9.:\\-\\/_\\´\\[\\]|]*")) {
            return HOST_OK;
        }
        return HOST_INVALID;
    }

    private int checkIdentChars(String host) {
        if (host.length() > 10) {
            return HOST_TOO_LONG;
        }
        if (host.length() < 1) {
            return HOST_TOO_SHORT;
        }
        if (host.matches("[a-zA-Z0-9.:\\-\\/_\\´\\[\\]|]*")) {
            return HOST_OK;
        }
        return HOST_INVALID;
    }

    private long time() {
        return System.currentTimeMillis() / 1000;
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
    private static final Logger LOG = Logger.getLogger(HostServ.class.getName());
}
