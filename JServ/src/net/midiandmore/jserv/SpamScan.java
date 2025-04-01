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

/**
 * Starts a new Thread
 *
 * @author Andreas Pschorn
 */
public class SpamScan implements Software {

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
        sendText("%s N %s 2 %d %s %s +oikr - %s U]AEC %sAAC :%s", getNumeric(), getNick(), time(), getIdentd(), getServername(), getNick(), getNumeric(), getDescription());
    }

    /**
     * Turns an IP address into an integer and returns this
     *
     * @param addr
     * @return
     */
    private int ipToInt(String addr) {
        String[] addrArray = addr.split("\\.");
        int[] num = new int[]{
            Integer.parseInt(addrArray[0]),
            Integer.parseInt(addrArray[1]),
            Integer.parseInt(addrArray[2]),
            Integer.parseInt(addrArray[3])
        };

        int result = ((num[0] & 255) << 24);
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
                    StringBuilder sb = new StringBuilder();
                    for (int i = 3; i < elem.length; i++) {
                        sb.append(elem[i]);
                        sb.append(" ");
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
                            sendText("%sAAC %s %s :Successfully authed.", getNumeric(), notice, elem[0]);
                        } else {
                            sendText("%sAAC %s %s :Unknown command, or access denied.", getNumeric(), notice, elem[0]);
                        }
                    } else if ((getSt().isOper(nick) || isReg()) && auth.length >= 2 && auth[0].equalsIgnoreCase("ADDCHAN")) {

                        var channel = auth[1];
                        if (getSt().getChannel().containsKey(channel.toLowerCase()) && !getMi().getDb().isChan(channel.toLowerCase())) {
                            getMi().getDb().addChan(channel.toLowerCase());
                            joinChannel(channel.toLowerCase());
                            setReg(false);
                            sendText("%sAAC %s %s :Added channel %s", getNumeric(), notice, elem[0], channel.toLowerCase());
                        } else {
                            setReg(false);
                            sendText("%sAAC %s %s :Cannot add channel %s: The channel doesn't exists or %s is allready in the channel...", getNumeric(), notice, elem[0], channel, getMi().getConfig().getSpamFile().get("nick"));
                        }
                    } else if ((getSt().isOper(nick) || isReg()) && auth.length >= 2 && auth[0].equalsIgnoreCase("DELCHAN")) {
                        var channel = auth[1];
                        if (getSt().getChannel().containsKey(channel.toLowerCase()) && getMi().getDb().isChan(channel.toLowerCase())) {
                            getMi().getDb().removeChan(channel.toLowerCase());
                            partChannel(channel.toLowerCase());
                            setReg(false);
                            sendText("%sAAC %s %s :Removed channel %s", getNumeric(), notice, elem[0], channel.toLowerCase());
                        } else {
                            setReg(false);
                            sendText("%sAAC %s %s :Cannot delete channel %s: The channel doesn't exists or %s isn't in the channel...", getNumeric(), notice, elem[0], channel, getMi().getConfig().getSpamFile().get("nick"));
                        }
                    } else if (getSt().isOper(nick) && auth.length >= 2 && auth[0].equalsIgnoreCase("BADWORD")) {
                        var flag = auth[1];
                        var b = getMi().getConfig().getBadwordFile();
                        if (flag.equalsIgnoreCase("ADD") || flag.equalsIgnoreCase("DELETE")) {
                            StringBuilder sb1 = new StringBuilder();
                            for (int i = 2; i < auth.length; i++) {
                                sb1.append(auth[i]);
                                sb1.append(" ");
                            }
                            var parsed = sb1.toString().trim();
                            if (b.containsKey(parsed.toLowerCase())) {
                                if (flag.equalsIgnoreCase("ADD")) {
                                    sendText("%sAAC %s %s :Badword (%s) allready exists.", getNumeric(), notice, elem[0], parsed);
                                } else if (flag.equalsIgnoreCase("DELETE")) {
                                    b.remove(parsed.toLowerCase());
                                    getMi().getConfig().saveDataToJSON("badwords-spamscan.json", b, "name", "value");
                                    sendText("%sAAC %s %s :Badword (%s) successfully removed.", getNumeric(), notice, elem[0], parsed);
                                }
                            } else {
                                if (flag.equalsIgnoreCase("ADD")) {
                                    b.put(parsed.toLowerCase(), "");
                                    getMi().getConfig().saveDataToJSON("badwords-spamscan.json", b, "name", "value");
                                    sendText("%sAAC %s %s :Badword (%s) successfully added.", getNumeric(), notice, elem[0], parsed);
                                } else if (flag.equalsIgnoreCase("DELETE")) {
                                    sendText("%sAAC %s %s :Badword (%s) doesn't exists.", getNumeric(), notice, elem[0], parsed);
                                }
                            }
                        } else if (flag.equalsIgnoreCase("LIST")) {
                            sendText("%sAAC %s %s :--- Badwords ---", getNumeric(), notice, elem[0]);
                            for (var key : b.keySet()) {
                                sendText("%sAAC %s %s :%s", getNumeric(), notice, elem[0], key);
                            }
                            sendText("%sAAC %s %s :--- End of list ---", getNumeric(), notice, elem[0]);
                        } else {
                            sendText("%sAAC %s %s :Unknown flag.", getNumeric(), notice, elem[0]);
                        }
                    } else if (auth[0].equalsIgnoreCase("SHOWCOMMANDS")) {
                        sendText("%sAAC %s %s :SpamScan Version %s", getNumeric(), notice, elem[0], VERSION);
                        sendText("%sAAC %s %s :The following commands are available to you:", getNumeric(), notice, elem[0]);
                        if (getSt().isOper(nick)) {
                            sendText("%sAAC %s %s :--- Commands available for opers ---", getNumeric(), notice, elem[0]);
                            sendText("%sAAC %s %s :+o ADDCHAN      Addds a channel", getNumeric(), notice, elem[0]);
                            sendText("%sAAC %s %s :+o BADWORD      Manage badwords", getNumeric(), notice, elem[0]);
                            sendText("%sAAC %s %s :+o DELCHAN      Removes a channel", getNumeric(), notice, elem[0]);
                        } else {
                            sendText("%sAAC %s %s :--- Commands available for users ---", getNumeric(), notice, elem[0]);
                        }
                        sendText("%sAAC %s %s :   HELP         Show a help for an command", getNumeric(), notice, elem[0]);
                        sendText("%sAAC %s %s :   SHOWCOMMANDS This message", getNumeric(), notice, elem[0]);
                        sendText("%sAAC %s %s :   VERSION      Shows version information", getNumeric(), notice, elem[0]);
                        sendText("%sAAC %s %s :End of list.", getNumeric(), notice, elem[0]);
                    } else if (auth[0].equalsIgnoreCase("VERSION")) {
                        sendText("%sAAC %s %s :SpamScan v%s by %s", getNumeric(), notice, elem[0], VERSION, VENDOR);
                        sendText("%sAAC %s %s :By %s", getNumeric(), notice, elem[0], AUTHOR);
                    } else if (getSt().isOper(nick) && auth.length == 2 && auth[0].equalsIgnoreCase("HELP") && auth[1].equalsIgnoreCase("ADDCHAN")) {
                        sendText("%sAAC %s %s :ADDCHAN <#channel>", getNumeric(), notice, elem[0]);
                    } else if (getSt().isOper(nick) && auth.length == 2 && auth[0].equalsIgnoreCase("HELP") && auth[1].equalsIgnoreCase("AUTH")) {
                        sendText("%sAAC %s %s :AUTH <requestname> <requestpassword>", getNumeric(), notice, elem[0]);
                    } else if (getSt().isOper(nick) && auth.length == 2 && auth[0].equalsIgnoreCase("HELP") && auth[1].equalsIgnoreCase("BADWORD")) {
                        sendText("%sAAC %s %s :BADWORD <ADD|LIST|DELETE> [badword]", getNumeric(), notice, elem[0]);
                    } else if (getSt().isOper(nick) && auth.length == 2 && auth[0].equalsIgnoreCase("HELP") && auth[1].equalsIgnoreCase("DELCHAN")) {
                        sendText("%sAAC %s %s :DELCHAN <#channel>", getNumeric(), notice, elem[0]);
                    } else {
                        sendText("%sAAC %s %s :Unknown command, or access denied.", getNumeric(), notice, elem[0]);
                    }
                } else if ((elem[1].equals("P") || elem[1].equals("O")) && getSt().getChannel().containsKey(elem[2].toLowerCase()) && !getSt().getChannel().get(elem[2].toLowerCase()).getOwner().contains(elem[0]) && !getSt().getChannel().get(elem[2].toLowerCase()).getService().contains(elem[0]) && !getSt().getUsers().get(elem[0]).isOper() && !getSt().getUsers().get(elem[0]).isService()) {
                    if (!getSt().isOper(getSt().getUsers().get(elem[0]).getAccount())) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 3; i < elem.length; i++) {
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
                            int count = getMi().getDb().getId();
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
                            if (info > 3) {
                                int count = getMi().getDb().getId();
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
                        if (((time() - getSt().getChannel().get(elem[2].toLowerCase()).getLastJoin().get(nick) < 300) && info > 2) || info > 5) {
                            int count = getMi().getDb().getId();
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
                                int count = getMi().getDb().getId();
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
                } else if (elem[1].equals("L")) {
                    var nick = elem[0];
                    var channel = elem[2].toLowerCase();
                    getSt().removeUser(nick, channel.toLowerCase());
                } else if (elem[1].equals("K")) {
                    var nick = elem[0];
                    var channel = elem[2].toLowerCase();
                    getSt().removeUser(nick, channel.toLowerCase());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isShorterAs5Minutes(String nick, String channel) {
        var flag = false;
        if (getSt().getChannel().get(channel.toLowerCase()).getLastJoin().containsKey(nick)) {
            flag = (System.currentTimeMillis() / 1000) - getSt().getChannel().get(channel.toLowerCase()).getLastJoin().get(nick) < 300;
        }
        return flag;
    }

    protected void joinChannel(String channel) {
        if (channel.startsWith("#")) {
            if (getSt().getChannel().containsKey(channel.toLowerCase())) {
                sendText("%sAAC J %s %d", getNumeric(), channel.toLowerCase(), time());
            } else {
                sendText("%sAAC C %s %d", getNumeric(), channel.toLowerCase(), time());
            }
            sendText("%s M %s +O %sAAC", getNumeric(), channel.toLowerCase(), getNumeric());
        }
    }

    private void partChannel(String channel) {
        sendText("%sAAC L %s", getNumeric(), channel.toLowerCase());
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
}
