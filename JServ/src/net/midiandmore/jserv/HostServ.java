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
public class HostServ implements Software {

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
    private HashMap<String, String> nicks;
    private HashMap<String, String> hosts;
    private HashMap<String, String> accounts;
    private HashMap<String, String> x;
    private SocketThread st;
    private boolean reg;

    public HostServ(JServ mi, SocketThread st, PrintWriter pw, BufferedReader br) {
        setMi(mi);
        setPw(pw);
        setBr(br);
        setSt(st);
        setReg(false);
    }

    protected void handshake(String nick, String servername, String description, String numeric, String identd) {
        setServername(servername);
        setNick(nick);
        setIdentd(identd);
        setDescription(description);
        setNumeric(numeric);
        System.out.println("Registering nick: " + getNick());
        sendText("%s N %s 2 %d %s %s +oikrd %s %sAAB :%s", getNumeric(), getNick(), time(), getIdentd(), getServername(), getNick(), getNumeric(), getDescription());
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
        int i = 0;
        for (var chr : buf) {
            sb.append(chr);
            if (i >= 3) {
                break;
            }
            i++;
        }
        return sb.toString();
    }

    protected void parseLine(String text) {
        try {
            text = text.trim();
            var elem = text.split(" ");
            if (getSt().getServerNumeric() != null) {
                if (elem[1].equals("P") && elem[2].equals(getNumeric() + "AAB")) {
                    var target = elem[2];
                    if (target.startsWith("#") || target.startsWith("!") || target.startsWith("&")) {
                        return;
                    }
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
                    if (auth[0].equalsIgnoreCase("SHOWCOMMANDS")) {
                        sendText("%sAAB %s %s :HostServ Version %s", getNumeric(), notice, elem[0], VERSION);
                        sendText("%sAAB %s %s :The following commands are available to you:", getNumeric(), notice, elem[0]);
                        if (getSt().isOper(nick)) {
                            sendText("%sAAB %s %s :--- Commands available for opers ---", getNumeric(), notice, elem[0]);
                            sendText("%sAAB %s %s :+o HOST         Enables or disables host hiding.", getNumeric(), notice, elem[0]);
                        } else {
                            sendText("%sAAB %s %s :--- Commands available for users ---", getNumeric(), notice, elem[0]);
                        }
                        sendText("%sAAB %s %s :   HELP         Generic help for a command", getNumeric(), notice, elem[0]);
                        sendText("%sAAB %s %s :   SHOWCOMMANDS View this list", getNumeric(), notice, elem[0]);
                        sendText("%sAAB %s %s :   VERSION      Prints version information", getNumeric(), notice, elem[0]);
                        sendText("%sAAB %s %s :End of list.", getNumeric(), notice, elem[0]);
                    } else if (auth[0].equalsIgnoreCase("VERSION")) {
                        sendText("%sAAB %s %s :HostServ v%s by %s", getNumeric(), notice, elem[0], VERSION, VENDOR);
                        sendText("%sAAB %s %s :By %s", getNumeric(), notice, elem[0], AUTHOR);
                    } else {
                        sendText("%sAAB %s %s :Unknown command, or access denied.", getNumeric(), notice, elem[0]);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void joinChannel(String channel) {
        if (getSt().getChannel().containsKey(channel.toLowerCase())) {
            sendText("%sAAB J %s %d", getNumeric(), channel.toLowerCase(), time());
        } else {
            sendText("%sAAB C %s %d", getNumeric(), channel.toLowerCase(), time());
        }
        sendText("%s M %s +o %sAAB", getNumeric(), channel.toLowerCase(), getNumeric());
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
