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
import java.util.Enumeration;
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
        setBurst(false);
        setBursts(new HashMap<>());
        (thread = new Thread(this)).start();
    }

    protected void handshake(String password, String servername, String description, String numeric) {
        System.out.println("Starting handshake...");
        sendText("PASS :%s", password);
        sendText("SERVER %s %d %d %d J10 %s]]] +hs6n 0 :%s", servername, 1, time(), time(), numeric, description);
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

    protected String getUserId(String auth) {
        for (Users session : getUsers().values()) {
            if (session.getAccount().equalsIgnoreCase(auth)) {
                return session.getId();
            }
        }
        return null;
    }

    protected String getUserName(String nick) {
        for (Users session : getUsers().values()) {
            if (session.getNick().equalsIgnoreCase(nick)) {
                return session.getId();
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
            System.out.println("Handshake complete...");
            if (moduleh.equalsIgnoreCase("true")) {
                System.out.printf("Adding 1 channel for %s...\r\n", hnick);
                if (!getBursts().containsKey("#twilightzone")) {
                    getBursts().put("#twilightzone", new Burst("#twilightzone"));
                }
                getBursts().get("#twilightzone").getUsers().add(jnumeric + "AAB");
                System.out.printf("%s has successfully addeed...\r\n", hnick);
            }
            if (modulea.equalsIgnoreCase("true")) {
                System.out.printf("Adding 1 channel for %s...\r\n", anick);
                if (!getBursts().containsKey("#twilightzone")) {
                    getBursts().put("#twilightzone", new Burst("#twilightzone"));
                }
                getBursts().get("#twilightzone").getUsers().add(jnumeric + "AAA");
                System.out.printf("%s has successfully added...\r\n", anick);
            }
            if (modules.equalsIgnoreCase("true")) {
                var list = getMi().getDb().getChannel();
                System.out.printf("Adding %d channels for %s...\r\n", list.size(), snick);
                for (var channel : list) {
                    if (channel.startsWith("#")) {
                        if (!getBursts().containsKey(channel.toLowerCase())) {
                            getBursts().put(channel.toLowerCase(), new Burst(channel.toLowerCase()));
                        }
                        getBursts().get(channel.toLowerCase()).getUsers().add(jnumeric + "AAC");
                    }
                }
                System.out.println("Channels added...");
            }
            var list = getMi().getDb().getChannels();
            var nicks = getMi().getDb().getData();
            var exist = new ArrayList();
            System.out.printf("Joining %d channels for the services with a burst...\r\n", list.size());
            for (var channel : list) {
                if (channel[1].startsWith("#")) {
                    if (!getBursts().containsKey(channel[1].toLowerCase())) {
                        getBursts().put(channel[1].toLowerCase(), new Burst(channel[1].toLowerCase()));
                    }
                    if (channel[27] != null && !channel[27].isBlank()) {
                        getBursts().get(channel[1].toLowerCase()).setTime(Long.parseLong(channel[27]) < 1270075989 ? time() : Long.parseLong(channel[27]));
                    } else {
                        getBursts().get(channel[1].toLowerCase()).setTime(time());
                    }
                    var cid = channel[0];
                    var ua = new ArrayList();
                    for (var nick : nicks) {
                        var nid = nick[0];
                        var auth = getMi().getDb().getChanUser(Long.parseLong(nid), Long.parseLong(cid));
                        if (auth != null) {
                            var users = getUsers().keySet();
                            for (var user : users) {
                                Users u = getUsers().get(user);
                                if (ua.contains(u.getId())) {
                                    continue;
                                }
                                ua.add(u.getId());
                                if (u.getAccount().equalsIgnoreCase(nick[1]) && getChannel().get(channel[1].toLowerCase()).getUsers().contains(user)) {
                                    if (u.isService()) {
                                        getBursts().get(channel[1].toLowerCase()).getUsers().add(user + ":O");
                                    } else if (isOwner(Integer.parseInt(auth[0]))) {
                                        getBursts().get(channel[1].toLowerCase()).getUsers().add(user + ":q");
                                    } else if (isMaster(Integer.parseInt(auth[0]))) {
                                        getBursts().get(channel[1].toLowerCase()).getUsers().add(user + ":a");
                                    } else if (isOp(Integer.parseInt(auth[0]))) {
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
            var bursts = getBursts().keySet();
            for (var burst : bursts) {
                var nicks1 = getBursts().get(burst).getUsers().toArray();
                var sb = new StringBuilder();
                for (int i = 0; i < nicks1.length; i++) {
                    sb.append(nicks1[i]);
                    if ((nicks1[i].equals(jnumeric + "AAA") || nicks1[i].equals(jnumeric + "AAB") || nicks1[i].equals(jnumeric + "AAC"))) {
                        sb.append(":O");
                    }
                    if (i + 1 < nicks1.length) {
                        sb.append(",");
                    }
                }
                if (!sb.isEmpty()) {
                    sendText("%s B %s %d %s", jnumeric, burst, getBursts().get(burst).getTime(), sb.toString());
                }
            }
            System.out.println("Channels joined...");
            System.out.println("Successfully connected...");
            sendText("%s EB", jnumeric);
            while (!getSocket().isClosed() && (content = getBr().readLine()) != null && isRuns()) {
                try {
                    var elem = content.split(" ");
                    if (content.startsWith("SERVER")) {
                        setServerNumeric(content.split(" ")[6].substring(0, 1));
                        System.out.println("Getting SERVER response...");
                    } else if (elem[1].equals("EB")) {
                        setBurst(true);
                        sendText("%s EA", jnumeric);
                    } else if (elem[1].equals("J")) {
                        var channel = elem[2].toLowerCase();
                        var names = elem[0];
                        var user = new String[1];
                        user[0] = names;
                        if (getChannel().containsKey(channel.toLowerCase())) {
                            getChannel().get(channel.toLowerCase()).getUsers().add(names);
                            getChannel().get(channel.toLowerCase()).getLastJoin().put(names, time());
                        } else {
                            getChannel().put(channel.toLowerCase(), new Channel(channel.toLowerCase(), "", user));
                        }
                        var c = getChannel().get(channel);
                        var cu = c.getUsers();
                        var sb = new StringBuilder();
                        for (var u : cu.toArray()) {
                            var u1 = getUsers().get(u);
                            if (u1 != null && u1.isService()) {
                                sendText("%s M %s +O %s", jnumeric, channel, u);
                            }
                        }
                    } else if (elem[1].equals("N") && elem.length > 4) {
                        var priv = elem[7].contains("r");
                        var hidden = elem[7].contains("h");
                        var service = elem[7].contains("k");
                        var x = elem[7].contains("x");
                        var o = elem[7].contains("o");
                        String acc = null;
                        String nick = null;
                        if (priv) {
                            var other = false;
                            if (elem[9].contains(":")) {
                                acc = elem[9].split(":", 2)[0];
                                other = true;
                            } else if (elem[8].contains(":")) {
                                acc = elem[8].split(":", 2)[0];
                            } else {
                                acc = "";
                            }
                            if (other) {
                                if (hidden) {
                                    nick = elem[12];
                                } else {
                                    nick = elem[11];
                                    sendText("%s SH %s %s %s", jnumeric, nick, elem[5], getHs().parseCloak(elem[6]));
                                }
                            } else {
                                if (hidden) {
                                    nick = elem[11];
                                } else {
                                    nick = elem[10];
                                    sendText("%s SH %s %s %s", jnumeric, nick, elem[5], getHs().parseCloak(elem[6]));
                                }
                            }
                            if (x) {
                                sendText("%s SH %s %s %s", jnumeric, nick, elem[5], acc + getMi().getConfig().getConfigFile().getProperty("reg_host"));
                            }
                        } else if (elem[9].contains("@")) {
                            acc = "";
                            nick = elem[11];
                        } else {
                            acc = "";
                            if (hidden) {
                                nick = elem[10];
                            } else {
                                nick = elem[9];
                                sendText("%s SH %s %s %s", jnumeric, nick, elem[5], getHs().parseCloak(elem[6]));
                            }
                        }
                        var hosts = elem[5] + "@" + elem[6];
                        // Antiknocker
                        if (!antiKnocker(elem[2], elem[5])) {
                            getUsers().put(nick, new Users(nick, elem[2], acc, hosts));
                            getUsers().get(nick).setX(x);
                            getUsers().get(nick).setService(service);
                            getUsers().get(nick).setOper(o);
                            if (!acc.isBlank()) {
                                getMi().getDb().updateData("lastuserhost", acc, hosts);
                                getMi().getDb().updateData("lastpasschng", acc, time());
                            }
                        } else {
                            int count = getMi().getDb().getId();
                            count++;
                            getMi().getDb().addId("Spambot!");
                            sendText("%sAAC D %s %d : (You are detected as as Knocker Spambot, ID: %d)", jnumeric, nick, time(), count);
                        }
                    } else if (elem[1].equals("N") && elem.length == 4) {
                        getUsers().get(elem[0]).setNick(elem[2]);
                    } else if (elem[1].equals("B") && elem.length == 7) {
                        var channel = elem[2].toLowerCase();
                        var modes = elem[4];
                        var names = elem[6].split(",");
                        getChannel().put(channel.toLowerCase(), new Channel(channel.toLowerCase(), modes, names));
                        var c = getChannel().get(channel);
                        var cu = c.getUsers();
                        for (var u : cu.toArray()) {
                            var u1 = getUsers().get(u);
                            if (u1 != null && u1.isService()) {
                                sendText("%s M %s +O %s", jnumeric, channel, u);
                            }
                        }
                    } else if (elem[1].equals("B") && elem.length >= 6) {
                        var channel = elem[2].toLowerCase();
                        var modes = elem[4];
                        var names = elem[5].split(",");
                        getChannel().put(channel.toLowerCase(), new Channel(channel.toLowerCase(), modes, names));
                        var c = getChannel().get(channel);
                        var cu = c.getUsers();
                        for (var u : cu.toArray()) {
                            var u1 = getUsers().get(u);
                            if (u1 != null && u1.isService()) {
                                sendText("%s M %s +O %s", jnumeric, channel, u);
                            }
                        }
                    } else if (elem[1].equals("B") && elem.length == 5) {
                        var channel = elem[2].toLowerCase();
                        var modes = "";
                        var names = elem[4].split(",");
                        getChannel().put(channel.toLowerCase(), new Channel(channel.toLowerCase(), modes, names));
                        var c = getChannel().get(channel);
                        var cu = c.getUsers();
                        for (var u : cu.toArray()) {
                            var u1 = getUsers().get(u);
                            if (u1 != null && u1.isService()) {
                                sendText("%s M %s +O %s", jnumeric, channel, u);
                            }
                        }
                    } else if (elem[1].equals("C")) {
                        var channel = elem[2].toLowerCase();
                        var names = new String[1];
                        names[0] = elem[0] + ":q";
                        getChannel().put(channel.toLowerCase(), new Channel(channel.toLowerCase(), "", names));
                    } else if (elem[1].equals("AC") && getUsers().containsKey(elem[2])) {
                        var acc = elem[3];
                        var nick = elem[2];
                        if (getUsers().get(nick).isX()) {
                            var hosts = getUsers().get(nick).getHost();
                            sendText("%s SH %s %s %s", jnumeric, nick, hosts.split("@")[0], acc + getMi().getConfig().getConfigFile().getProperty("reg_host"));
                        }
                        if (getUsers().get(nick).getAccount().isBlank()) {
                            getUsers().get(nick).setAccount(acc);
                        }
                    } else if (elem[1].equals("G")) {
                        sendText("%s Z %s", jnumeric, content.substring(5));
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
                        if (elem[3].contains("x") && getUsers().get(nick).getNick().equalsIgnoreCase(elem[2]) && !getUsers().get(nick).getAccount().isBlank()) {
                            var hosts = getUsers().get(nick).getHost();
                            sendText("%s SH %s %s %s", jnumeric, nick, hosts.split("@")[0], getUsers().get(nick).getAccount() + getMi().getConfig().getConfigFile().getProperty("reg_host"));
                        }
                    } else if (elem[1].equals("M")) {
                        var nick = elem[0];
                        var channel = elem[2].toLowerCase();
                        if (channel.startsWith("#")) {
                            var flags = elem[3].split("");
                            var set = false;
                            var cnt = 0;
                            for (var mode : flags) {
                                if (mode.equals("-")) {
                                    set = false;
                                } else if (mode.equals("+")) {
                                    set = true;
                                } else if (set && mode.equals("o")) {
                                    var users = elem[4].split(" ");
                                    getChannel().get(channel.toLowerCase()).getOp().add(users[0]);
                                } else if (set && mode.equals("v")) {
                                    var users = elem[4].split(" ");
                                    getChannel().get(channel.toLowerCase()).getVoice().add(users[0]);
                                } else if (set && mode.equals("h")) {
                                    var users = elem[4].split(" ");
                                    getChannel().get(channel.toLowerCase()).getHop().add(users[0]);
                                } else if (set && mode.equals("a")) {
                                    var users = elem[4].split(" ");
                                    getChannel().get(channel.toLowerCase()).getAdmin().add(users[0]);
                                } else if (set && mode.equals("O")) {
                                    var users = elem[4].split(" ");
                                    getChannel().get(channel.toLowerCase()).getService().add(users[0]);
                                } else if (set && mode.equals("q")) {
                                    var users = elem[4].split(" ");
                                    getChannel().get(channel.toLowerCase()).getOwner().add(users[0]);
                                } else if (!set && mode.equals("o")) {
                                    var users = elem[4].split(" ");
                                    getChannel().get(channel.toLowerCase()).getOp().remove(users[0]);
                                } else if (!set && mode.equals("v")) {
                                    var users = elem[4].split(" ");
                                    getChannel().get(channel.toLowerCase()).getVoice().remove(users[0]);
                                } else if (!set && mode.equals("h")) {
                                    var users = elem[4].split(" ");
                                    getChannel().get(channel.toLowerCase()).getHop().remove(users[0]);
                                } else if (!set && mode.equals("a")) {
                                    var users = elem[4].split(" ");
                                    getChannel().get(channel.toLowerCase()).getAdmin().remove(users[0]);
                                } else if (!set && mode.equals("O")) {
                                    var users = elem[4].split(" ");
                                    getChannel().get(channel.toLowerCase()).getService().remove(users[0]);
                                } else if (!set && mode.equals("q")) {
                                    var users = elem[4].split(" ");
                                    getChannel().get(channel.toLowerCase()).getOwner().remove(users[0]);
                                } else if (set) {
                                    getChannel().get(channel.toLowerCase()).setModes(getChannel().get(channel.toLowerCase()).getModes() + mode);
                                    if (mode.equals("m")) {
                                        getChannel().get(channel.toLowerCase()).setModerated(true);
                                    }
                                } else {
                                    getChannel().get(channel.toLowerCase()).setModes(getChannel().get(channel.toLowerCase()).getModes().replace(mode, ""));
                                    if (mode.equals("m")) {
                                        getChannel().get(channel.toLowerCase()).setModerated(false);
                                    }
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
                        var nn = getUsers().get(nick).getNick();
                        if (getAuthed().containsKey(nn)) {
                            getAuthed().remove(nn);
                        }
                        getUsers().remove(nick);
                    }
                    if (modules.equalsIgnoreCase("true")) {
                        getSs().parseLine(content);
                    }
                    if (moduleh.equalsIgnoreCase("true")) {
                        getHs().parseLine(content);
                    }
                    if (modulea.equalsIgnoreCase("true")) {
                        getAs().parseLine(content);
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

    // Antikocker
    protected boolean antiKnocker(String nick, String ident) {
        if (ident.startsWith("~")) {
            ident = ident.substring(1);
        }
        var regex = "^(st|sn|cr|pl|pr|fr|fl|qu|br|gr|sh|sk|tr|kl|wr|bl|[bcdfgklmnprstvwz])([aeiou][aeiou][bcdfgklmnprstvwz])(ed|est|er|le|ly|y|ies|iest|ian|ion|est|ing|led|inger?|[abcdfgklmnprstvwz])$";
        return !ident.equalsIgnoreCase(nick) && nick.matches(regex) && ident.matches(regex);
    }

    protected void removeUser(String nick, String channel) {
        if (!getChannel().containsKey(channel.toLowerCase())) {
            return;
        }
        if (getChannel().get(channel.toLowerCase()).getUsers().contains(nick)) {
            getChannel().get(channel.toLowerCase()).getUsers().remove(nick);
        }
        if (getChannel().get(channel.toLowerCase()).getOp().contains(nick)) {
            getChannel().get(channel.toLowerCase()).getOp().remove(nick);
        }
        if (getChannel().get(channel.toLowerCase()).getVoice().contains(nick)) {
            getChannel().get(channel.toLowerCase()).getVoice().remove(nick);
        }
        if (getChannel().get(channel.toLowerCase()).getLastJoin().containsKey(nick)) {
            getChannel().get(channel.toLowerCase()).getLastJoin().remove(nick);
        }
        if (getChannel().get(channel.toLowerCase()).getUsers().isEmpty()) {
            getChannel().remove(channel.toLowerCase());
        }
        if (getUsers().get(nick).getChannels().contains(channel.toLowerCase())) {
            getUsers().get(nick).getChannels().remove(channel.toLowerCase());
        }
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
            return oper != 0;
        }
        return false;
    }

    protected boolean isHelper(String nick) {
        if (!nick.isBlank()) {
            var flags = getMi().getDb().getFlags(nick);
            var oper = flags & (QUFLAG_HELPER);
            return oper != 0;
        }
        return false;
    }

    protected boolean isStaff(String nick) {
        if (!nick.isBlank()) {
            var flags = getMi().getDb().getFlags(nick);
            var oper = flags & (QUFLAG_HELPER | QUFLAG_STAFF);
            return oper != 0;
        }
        return false;
    }

    protected boolean isOper(String nick) {
        if (!nick.isBlank()) {
            var flags = getMi().getDb().getFlags(nick);
            var oper = flags & (QUFLAG_OPER | QUFLAG_HELPER | QUFLAG_STAFF);
            return oper != 0;
        }
        return false;
    }

    protected boolean isAdmin(String nick) {
        if (!nick.isBlank()) {
            var flags = getMi().getDb().getFlags(nick);
            var oper = flags & (QUFLAG_OPER | QUFLAG_HELPER | QUFLAG_ADMIN | QUFLAG_STAFF);
            return oper != 0;
        }
        return false;
    }

    protected boolean isDev(String nick) {
        if (!nick.isBlank()) {
            var flags = getMi().getDb().getFlags(nick);
            var oper = flags & (QUFLAG_OPER | QUFLAG_DEV | QUFLAG_HELPER | QUFLAG_ADMIN | QUFLAG_STAFF);
            return oper != 0;
        }
        return false;
    }

    protected boolean isMaster(int flags) {
        return (flags & QCUFLAG_MASTER) != 0;
    }

    protected boolean isOwner(int flags) {
        return (flags & QCUFLAG_OWNER) != 0;
    }

    protected boolean isOp(int flags) {
        return (flags & QCUFLAG_OP) != 0;
    }

    protected boolean isVoice(int flags) {
        return (flags & QCUFLAG_VOICE) != 0;
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

}
