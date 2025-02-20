/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * Starts a new Thread
 *
 * @author Andreas Pschorn
 */
public class SocketThread implements Runnable, Userflags {

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
     * @return the as
     */
    public AuthServ getAs() {
        return as;
    }

    /**
     * @param as the as to set
     */
    public void setAs(AuthServ as) {
        this.as = as;
    }

    /**
     * @return the hs
     */
    public HostServ getHs() {
        return hs;
    }

    /**
     * @param hs the hs to set
     */
    public void setHs(HostServ hs) {
        this.hs = hs;
    }

    /**
     * @return the ss
     */
    public SpamScan getSs() {
        return ss;
    }

    /**
     * @param ss the ss to set
     */
    public void setSs(SpamScan ss) {
        this.ss = ss;
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

    private Thread thread;
    private JServ mi;
    private Socket socket;
    private PrintWriter pw;
    private BufferedReader br;
    private boolean runs;
    private String serverNumeric;
    private String numeric;
    private String nick;
    private String identd;
    private String servername;
    private String description;
    private HashMap<String, String> authed;
    private HashMap<String, Users> users;
    private HashMap<String, Channel> channel;
    private AuthServ as;
    private HostServ hs;
    private SpamScan ss;
    private byte[] ip;
    private boolean reg;

    public SocketThread(JServ mi) {
        setMi(mi);
        setUsers(new HashMap<>());
        setChannel(new HashMap<>());
        setAuthed(new HashMap<>());
        (thread = new Thread(this)).start();
    }

    protected void handshake(String password, String servername, String description, String numeric) {
        System.out.println("Starting handshake...");
        sendText("PASS :%s", password);
        sendText("SERVER %s %d %d %d J10 %s]]] +hs6n :%s", servername, 2, time(), time(), numeric, description);
    }

    protected void sendText(String text, Object... args) {
        getPw().println(text.formatted(args));
        getPw().flush();
        if (getMi().getConfig().getConfigFile().getProperty("debug", "false").equalsIgnoreCase("true")) {
            System.out.printf("DEBUG sendText: %s\n", text.formatted(args));
        }
    }

    protected String getUser(String nick) {
        for (String session : getUsers().keySet()) {
            if (getUsers().get(session).getNick().equalsIgnoreCase(nick)) {
                return session;
            }
        }
        return null;
    }

    private long time() {
        return System.currentTimeMillis() / 1000;
    }

    @Override
    public void run() {
        System.out.println("Connecting to server...");
        setRuns(true);
        var host = getMi().getConfig().getConfigFile().getProperty("host");
        var port = getMi().getConfig().getConfigFile().getProperty("port");
        var password = getMi().getConfig().getConfigFile().getProperty("password");
        var jservername = getMi().getConfig().getConfigFile().getProperty("servername");
        var anick = getMi().getConfig().getAuthFile().getProperty("nick");
        var aservername = getMi().getConfig().getAuthFile().getProperty("servername");
        var adescription = getMi().getConfig().getAuthFile().getProperty("description");
        var hnick = getMi().getConfig().getHostFile().getProperty("nick");
        var hservername = getMi().getConfig().getHostFile().getProperty("servername");
        var hdescription = getMi().getConfig().getHostFile().getProperty("description");
        var snick = getMi().getConfig().getSpamFile().getProperty("nick");
        var sservername = getMi().getConfig().getSpamFile().getProperty("servername");
        var sdescription = getMi().getConfig().getSpamFile().getProperty("description");
        var jdescription = getMi().getConfig().getConfigFile().getProperty("description");
        var jnumeric = getMi().getConfig().getConfigFile().getProperty("numeric");
        var aidentd = getMi().getConfig().getAuthFile().getProperty("identd");
        var hidentd = getMi().getConfig().getHostFile().getProperty("identd");
        var sidentd = getMi().getConfig().getSpamFile().getProperty("identd");
        var modules = getMi().getConfig().getModulesFile().getProperty("spamscan");
        var moduleh = getMi().getConfig().getModulesFile().getProperty("hostserv");
        var modulea = getMi().getConfig().getModulesFile().getProperty("authserv");
        try {
            setSocket(new Socket(host, Integer.parseInt(port)));
            setPw(new PrintWriter(getSocket().getOutputStream()));
            setBr(new BufferedReader(new InputStreamReader(getSocket().getInputStream())));
            var content = "";
            handshake(password, jservername, jdescription, jnumeric);
            setSs(new SpamScan(getMi(), this, getPw(), getBr()));
            setHs(new HostServ(getMi(), this, getPw(), getBr()));
            setAs(new AuthServ(getMi(), this, getPw(), getBr()));
            if (modules.equalsIgnoreCase("true")) {
                getSs().handshake(snick, sservername, sdescription, jnumeric, sidentd);
            }
            if (moduleh.equalsIgnoreCase("true")) {
                getHs().handshake(hnick, hservername, hdescription, jnumeric, hidentd);
            }
            if (modulea.equalsIgnoreCase("true")) {
                getAs().handshake(anick, aservername, adescription, jnumeric, aidentd);
            }
            sendText("%s EB", jnumeric);
            while (!getSocket().isClosed() && (content = getBr().readLine()) != null && isRuns()) {
                try {
                    if (modules.equalsIgnoreCase("true")) {
                        getSs().parseLine(content);
                    }
                    if (moduleh.equalsIgnoreCase("true")) {
                        getHs().parseLine(content);
                    }
                    if (modulea.equalsIgnoreCase("true")) {
                        getAs().parseLine(content);
                    }
                    var elem = content.split(" ", 4);
                    if (elem[1].equals("EB")) {
                        sendText("%s EA", jnumeric);
                        System.out.println("Handshake complete...");
                        if (moduleh.equalsIgnoreCase("true")) {
                            System.out.printf("Joining 1 channel for %s...\r\n", hnick);
                            getHs().joinChannel("#twilightzone");
                            System.out.printf("%s has successfully joined...\r\n", hnick);
                        }
                        if (modulea.equalsIgnoreCase("true")) {
                            System.out.printf("Joining 1 channel for %s...\r\n", anick);
                            getAs().joinChannel("#twilightzone");
                            System.out.printf("%s has successfully joined...\r\n", anick);
                        }
                        if (modules.equalsIgnoreCase("true")) {
                            var list = getMi().getDb().getChannel();
                            System.out.printf("Joining %d channels for %s...\r\n", list.size(), snick);
                            for (var channel : list) {
                                getSs().joinChannel(channel.toLowerCase());
                            }
                            System.out.println("Channels joined...");
                        }
                        System.out.println("Successfully connected...");
                    } else if (content.startsWith("SERVER")) {
                        setServerNumeric(content.split(" ")[6].substring(0, 1));
                        System.out.println("Getting SERVER response...");
                    } else if (elem[1].equals("G")) {
                        sendText("%s Z %s", jnumeric, content.substring(5));
                    }
                    if (getMi().getConfig().getConfigFile().getProperty("debug", "false").equalsIgnoreCase("true")) {
                        System.out.printf("DEBUG get text: %s\n", content);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException | NumberFormatException ex) {
        }
        if (getPw() != null) {
            try {
                getPw().close();
            } catch (Exception ex) {
            }
        }
        if (getBr() != null) {
            try {
                getBr().close();
            } catch (IOException ex) {
            }
        }
        if (getSocket() != null && !getSocket().isClosed()) {
            try {
                getSocket().close();
            } catch (IOException ex) {
            }
        }
        setPw(null);
        setBr(null);
        setSocket(null);
        setRuns(false);
        System.out.println("Disconnected...");
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
            return oper;
        }
        return false;
    }

    protected boolean isPrivileged(String nick) {
        if (!nick.isBlank()) {
            var flags = getMi().getDb().getFlags(nick);
            var oper = flags & (QUFLAG_OPER | QUFLAG_DEV | QUFLAG_PROTECT | QUFLAG_HELPER | QUFLAG_ADMIN | QUFLAG_STAFF);
            System.out.printf("%d\r\n", oper);
            return oper != 0;
        }
        return false;
    }

    protected boolean isNoInfo(int flags) {
        return flags == 0;
    }

    protected boolean isInactive(int flags) {
        return (flags & QUFLAG_INACTIVE) != 0;
    }

    protected boolean isGline(int flags) {
        return (flags & QUFLAG_GLINE) != 0;
    }

    protected boolean isNotice(int flags) {
        return (flags & QUFLAG_NOTICE) != 0;
    }

    protected boolean isSuspended(int flags) {
        return (flags & QUFLAG_SUSPENDED) != 0;
    }

    protected boolean isOper(int flags) {
        return (flags & QUFLAG_OPER) != 0;
    }

    protected boolean isDev(int flags) {
        return (flags & QUFLAG_DEV) != 0;
    }

    protected boolean isProtect(int flags) {
        return (flags & QUFLAG_PROTECT) != 0;
    }

    protected boolean isHelper(int flags) {
        return (flags & QUFLAG_HELPER) != 0;
    }

    protected boolean isAdmin(int flags) {
        return (flags & QUFLAG_ADMIN) != 0;
    }

    protected boolean isInfo(int flags) {
        return (flags & QUFLAG_INFO) != 0;
    }

    protected boolean isDelayedGline(int flags) {
        return (flags & QUFLAG_DELAYEDGLINE) != 0;
    }

    protected boolean isNoAuthLimit(int flags) {
        return (flags & QUFLAG_NOAUTHLIMIT) != 0;
    }

    protected boolean isCleanupExempt(int flags) {
        return (flags & QUFLAG_CLEANUPEXEMPT) != 0;
    }

    protected boolean isStaff(int flags) {
        return (flags & QUFLAG_STAFF) != 0;
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

}
