/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Logger;


public final class SpamScan implements Software {

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
    private String nick;
    private String identd;
    private String servername;
    private String description;
    private byte[] ip;
    private boolean reg;
    private SocketThread st;

    public SpamScan(JServ mi, SocketThread st, PrintWriter pw, BufferedReader br) {
        setMi(mi);
        setReg(false);
        setPw(pw);
        setBr(br);
        setSt(st);
    }

    protected void handshake(String nick, String servername, String description, String numeric, String identd) {
        setServername(servername);
        setNick(nick);
        setIdentd(identd);
        setDescription(description);
        setNumeric(numeric);
        System.out.println("Registering nick: " + getNick());
        sendText("%s N %s 2 %d %s %s +oikr %s U]AEC %sAAC :%s", getNumeric(), getNick(), time(), getIdentd(), getServername(), getNick(), getNumeric(), getDescription());
    }

    /**
     * Turns an IP address into an integer and returns this
     *
     * @param addr
     * @return
     */
    private int ipToInt(String addr) {
        var addrArray = addr.split("\\.");
        var num = new int[]{
            Integer.parseInt(addrArray[0]),
            Integer.parseInt(addrArray[1]),
            Integer.parseInt(addrArray[2]),
            Integer.parseInt(addrArray[3])
        };
        var result = ((num[0] & 255) << 24);
        result = result | ((num[1] & 255) << 16);
        result = result | ((num[2] & 255) << 8);
        result = result | (num[3] & 255);
        return result;
    }

    protected void sendText(String text, Object... args) {
        getPw().println(text.formatted(args));
        getPw().flush();
        if (getMi().getConfig().getConfigFile().getProperty("debug", "false").equalsIgnoreCase("true")) {
            System.out.printf("DEBUG sendText: %s\n", text.formatted(args));
        }
    }

    protected void parseLine(String text) {
        try {
            text = text.trim();
            if (getSt().getServerNumeric() != null) {
                var elem = text.split(" ");
                if (elem[1].equals("P") && elem[2].equals(getNumeric() + "AAC")) {
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
                    if (auth[0].equalsIgnoreCase("AUTH")) {
                        if (auth.length >= 3 && auth[1].equals(getMi().getConfig().getSpamFile().getProperty("authuser")) && auth[2].equals(getMi().getConfig().getSpamFile().getProperty("authpassword"))) {
                            setReg(true);
                            getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], Messages.get("QM_DONE"));
                        } else {
                            getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], Messages.get("QM_UNKNOWNCMD", auth[0]));
                        }
                    } else if ((getSt().isOper(nick) || isReg()) && auth.length >= 2 && auth[0].equalsIgnoreCase("ADDCHAN")) {

                        var channel = auth[1];
                        if (!getSt().getChannel().containsKey(channel.toLowerCase())) {
                            getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], Messages.get("QM_EMPTYCHAN", channel));
                        } else if (!getMi().getDb().isChan(channel)) {
                            getMi().getDb().addChan(channel);
                            getSt().joinChannel(channel, getNumeric(), getNumeric() + "AAC");
                            setReg(false);
                            getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], Messages.get("QM_DONE"));
                        } else {
                            setReg(false);
                            getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "Cannot add channel %s: %s is already on that channel.", channel, getMi().getConfig().getSpamFile().get("nick"));
                        }
                    } else if ((getSt().isOper(nick) || isReg()) && auth.length >= 2 && auth[0].equalsIgnoreCase("DELCHAN")) {
                        var channel = auth[1];
                        if (!getSt().getChannel().containsKey(channel.toLowerCase())) {
                            getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], Messages.get("QM_EMPTYCHAN", channel));
                        } else if (getMi().getDb().isChan(channel)) {
                            getMi().getDb().removeChan(channel);
                            getSt().partChannel(channel, getNumeric(), "AAC");
                            setReg(false);
                            getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], Messages.get("QM_DONE"));
                        } else {
                            setReg(false);
                            getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "%s isn't in the channel.", channel, getMi().getConfig().getSpamFile().get("nick"));
                        }
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
                                    getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "Badword (%s) allready exists.", parsed);
                                } else if (flag.equalsIgnoreCase("DELETE")) {
                                    b.remove(parsed.toLowerCase());
                                    getMi().getConfig().saveDataToJSON("badwords-spamscan.json", b, "name", "value");
                                    getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "Badword (%s) successfully removed.", parsed);
                                }
                            } else {
                                if (flag.equalsIgnoreCase("ADD")) {
                                    b.put(parsed.toLowerCase(), "");
                                    getMi().getConfig().saveDataToJSON("badwords-spamscan.json", b, "name", "value");
                                    getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "Badword (%s) successfully added.", parsed);
                                } else if (flag.equalsIgnoreCase("DELETE")) {
                                    getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "Badword (%s) doesn't exists.", parsed);
                                }
                            }
                        } else if (flag.equalsIgnoreCase("LIST")) {
                            getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "--- Badwords ---");
                            if (b.isEmpty()) {
                                getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "  No badwords specified.");
                            } else {
                                for (var key : b.keySet()) {
                                    getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "%s", key);
                                }
                            }
                            getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "--- End of list ---");
                        } else {
                            getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "Unknown flag.");
                        }
                    } else if (auth[0].equalsIgnoreCase("SHOWCOMMANDS")) {
                        getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], Messages.get("QM_COMMANDLIST"));
                        if (getSt().isOper(nick)) {
                            getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "+o ADDCHAN      Addds a channel");
                            getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "+o BADWORD      Manage badwords");
                            getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "+o DELCHAN      Removes a channel");
                        }
                        getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "   HELP         Show a help for an command");
                        getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "   SHOWCOMMANDS This message");
                        getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "   VERSION      Shows version information");
                        getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], Messages.get("QM_ENDOFLIST"));
                    } else if (auth[0].equalsIgnoreCase("VERSION")) {
                        getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "SpamScan v%s by %s", VERSION, VENDOR);
                        getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "Based on JServ v%s", VERSION);
                        getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "Created by %s", AUTHOR);
                    } else if (getSt().isOper(nick) && auth.length == 2 && auth[0].equalsIgnoreCase("HELP") && auth[1].equalsIgnoreCase("ADDCHAN")) {
                        getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "ADDCHAN <#channel>");
                    } else if (getSt().isOper(nick) && auth.length == 2 && auth[0].equalsIgnoreCase("HELP") && auth[1].equalsIgnoreCase("AUTH")) {
                        getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "AUTH <requestname> <requestpassword>");
                    } else if (getSt().isOper(nick) && auth.length == 2 && auth[0].equalsIgnoreCase("HELP") && auth[1].equalsIgnoreCase("BADWORD")) {
                        getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "BADWORD <ADD|LIST|DELETE> [badword]");
                    } else if (getSt().isOper(nick) && auth.length == 2 && auth[0].equalsIgnoreCase("HELP") && auth[1].equalsIgnoreCase("DELCHAN")) {
                        getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], "DELCHAN <#channel>");
                    } else {
                        getSt().sendNotice(getNumeric(), "AAC", notice, elem[0], Messages.get("QM_UNKNOWNCMD", auth[0].toUpperCase()));
                    }
                } else if ((elem[1].equals("P") || elem[1].equals("O")) && getSt().getChannel().containsKey(elem[2].toLowerCase()) && !getSt().getChannel().get(elem[2].toLowerCase()).getOwner().contains(elem[0]) && !getSt().getChannel().get(elem[2].toLowerCase()).getService().contains(elem[0]) && !getSt().getUsers().get(elem[0]).isOper() && !getSt().getUsers().get(elem[0]).isService()) {
                    if (!getSt().isOper(getSt().getUsers().get(elem[0]).getAccount())) {
                        var sb = new StringBuilder();
                        for (var i = 3; i < elem.length; i++) {
                            if (elem[3].startsWith(":")) {
                                elem[3] = elem[3].substring(1);
                            }
                            sb.append(elem[i]);
                            sb.append(" ");
                        }
                        var command = sb.toString().trim();
                        var nick = elem[0];
                        var list = getSt().getUsers().get(nick).getChannels();
                        if (!list.contains(elem[2].toLowerCase())) {
                            list.add(elem[2].toLowerCase());
                        }
                        if (getMi().getHomoglyphs().scanForHomoglyphs(command) && time() - getSt().getChannel().get(elem[2].toLowerCase()).getLastJoin().get(nick) < 300) {
                            var count = getMi().getDb().getId();
                            count++;
                            getMi().getDb().addId("Try to spamming with homoglyphs!");
                            if (getSt().getChannel().get(elem[2].toLowerCase()).isModerated() && getSt().getChannel().get(elem[2].toLowerCase()).getVoice().contains(nick)) {
                                sendText("%sAAC M %s -v %s", getNumeric(), elem[2].toLowerCase(), nick);
                                getSt().getUsers().get(nick).setRepeat(0);
                            } else {
                                sendText("%sAAC D %s %d : (You are violating network rules, ID: %d)", getNumeric(), nick, time(), count);
                            }
                            return;
                        } else if (getSt().getUsers().get(nick).getLine().equalsIgnoreCase(command)) {
                            var info = getSt().getUsers().get(nick).getRepeat();
                            info = info + 1;
                            getSt().getUsers().get(nick).setRepeat(info);
                            if ((info > 3 && time() - getSt().getChannel().get(elem[2].toLowerCase()).getLastJoin().get(nick) < 300) || info > 7) {
                                var count = getMi().getDb().getId();
                                count++;
                                getMi().getDb().addId("Repeating lines!");
                                if (getSt().getChannel().get(elem[2].toLowerCase()).isModerated() && getSt().getChannel().get(elem[2].toLowerCase()).getVoice().contains(nick)) {
                                    sendText("%sAAC M %s -v %s", getNumeric(), elem[2].toLowerCase(), nick);
                                    getSt().getUsers().get(nick).setRepeat(0);
                                } else {
                                    sendText("%sAAC D %s %d : (You are violating network rules, ID: %d)", getNumeric(), nick, time(), count);
                                }
                                return;
                            }
                        } else {
                            getSt().getUsers().get(nick).setRepeat(0);
                            getSt().getUsers().get(nick).setLine(command);
                        }
                        var info = getSt().getUsers().get(nick).getFlood();
                        info = info + 1;
                        getSt().getUsers().get(nick).setFlood(info);
                        if ((time() - getSt().getChannel().get(elem[2].toLowerCase()).getLastJoin().get(nick) < 300 && info > 2) || info > 5) {
                            var count = getMi().getDb().getId();
                            count++;
                            getMi().getDb().addId("Flooding!");
                            if (getSt().getChannel().get(elem[2].toLowerCase()).isModerated() && getSt().getChannel().get(elem[2].toLowerCase()).getVoice().contains(nick)) {
                                sendText("%sAAC M %s -v %s", getNumeric(), elem[2].toLowerCase(), nick);
                                getSt().getUsers().get(nick).setFlood(0);
                            } else {
                                sendText("%sAAC D %s %d : (You are violating network rules, ID: %d)", getNumeric(), nick, time(), count);
                            }
                            return;
                        }
                        var b = getMi().getConfig().getBadwordFile();
                        for (var key : b.keySet()) {
                            var key1 = (String) key;
                            if (command.toLowerCase().contains(key1.toLowerCase())) {
                                var count = getMi().getDb().getId();
                                count++;
                                getMi().getDb().addId("Using of badword: " + key1.toLowerCase() + "!");
                                if (getSt().getChannel().get(elem[2].toLowerCase()).isModerated() && getSt().getChannel().get(elem[2].toLowerCase()).getVoice().contains(nick)) {
                                    sendText("%sAAC M %s -v %s", getNumeric(), elem[2].toLowerCase(), nick);
                                } else {
                                    sendText("%sAAC D %s %d : (You are violating network rules, ID: %d)", getNumeric(), nick, time(), count);
                                }
                                return;
                            }
                        }
                    }
                } else if (elem[1].equals("L") || elem[1].equals("K")) {
                    removeUserFromChannel(elem[0], elem[2].toLowerCase());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isShorterAs5Minutes(String nick, String channel) {
        var flag = false;
        if (getSt().getChannel().get(channel).getLastJoin().containsKey(nick)) {
            flag = (System.currentTimeMillis() / 1000) - getSt().getChannel().get(channel).getLastJoin().get(nick) < 300;
        }
        return flag;
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
    private static final Logger LOG = Logger.getLogger(SpamScan.class.getName());
    private static final int FIVE_MINUTES = 300;
    private static final String SERVICE_SUFFIX = "AAC";

    private void removeUserFromChannel(String nick, String channel) {
        getSt().removeUser(nick, channel);
    }
}
