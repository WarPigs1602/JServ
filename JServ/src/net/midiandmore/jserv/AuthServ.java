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
public class AuthServ implements Userflags {

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
    private SocketThread st;
    private byte[] ip;
    private boolean reg;

    public AuthServ(JServ mi, SocketThread st, PrintWriter pw, BufferedReader br) {
        setMi(mi);
        setSt(st);
        setPw(pw);
        setBr(br);

    }

    protected void handshake(String nick, String servername, String description, String numeric, String identd) {
        setServername(servername);
        setNick(nick);
        setIdentd(identd);
        setDescription(description);
        setNumeric(numeric);
        System.out.println("Registering nick: " + getNick());
        sendText("%s N %s 2 %d %s %s +oikrd - %s:%d U]AAEA %sAAA :%s", getNumeric(), getNick(), time(), getIdentd(), getServername(), getNick(), time(), getNumeric(), getDescription());
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

    private boolean isValidMail(String mail) {
        return mail.matches("(?:(?:\\r\\n)?[ \\t])*(?:(?:(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*))*@(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*|(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)*\\<(?:(?:\\r\\n)?[ \\t])*(?:@(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*(?:,@(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*)*:(?:(?:\\r\\n)?[ \\t])*)?(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*))*@(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*\\>(?:(?:\\r\\n)?[ \\t])*)|(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)*:(?:(?:\\r\\n)?[ \\t])*(?:(?:(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*))*@(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*|(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)*\\<(?:(?:\\r\\n)?[ \\t])*(?:@(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*(?:,@(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*)*:(?:(?:\\r\\n)?[ \\t])*)?(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*))*@(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*\\>(?:(?:\\r\\n)?[ \\t])*)(?:,\\s*(?:(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*))*@(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*|(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)*\\<(?:(?:\\r\\n)?[ \\t])*(?:@(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*(?:,@(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*)*:(?:(?:\\r\\n)?[ \\t])*)?(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*))*@(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*\\>(?:(?:\\r\\n)?[ \\t])*))*)?;\\s*)");
    }

    protected void parseLine(String text) {
        try {
            text = text.trim();
            var elem = text.split(" ");
            if (getSt().getServerNumeric() != null) {
                var server = getMi().getConfig().getAuthFile().getProperty("nick") + "@" + getMi().getConfig().getConfigFile().getProperty("servername");
                if (elem[1].equals("P") && (elem[2].equals(getNumeric() + "AAA") || server.equalsIgnoreCase(elem[2]) || elem[3].equalsIgnoreCase(":SASL"))) {
                    var target = elem[2];
                    StringBuilder sb = new StringBuilder();
                    for (int i = 3; i < elem.length; i++) {
                        sb.append(elem[i]);
                        sb.append(" ");
                    }
                    var command = sb.toString().trim();
                    if (command.startsWith(":")) {
                        command = command.substring(1);
                    }
                    var nick = elem[0];
                    var nickname = getSt().getUsers().containsKey(nick) ? getSt().getUsers().get(nick).getNick() : null;
                    var auth = command.split(" ");
                    var network = getMi().getConfig().getConfigFile().getProperty("network");
                    var notice = "O";
                    if (getSt().getUsers().containsKey(nick) && !getSt().getUsers().get(nick).getAccount().isBlank() && !getSt().isNotice(getSt().getUsers().get(nick).getAccount())) {
                        notice = "P";
                    }
                    if (auth[0].equalsIgnoreCase("hello")) {
                        var authed = getSt().getUsers().get(nick).getAccount();
                        if (auth.length == 1) {
                            sendText("%sAAA %s %s :You didn't provide enough parameters for %s.", getNumeric(), notice, nick, auth[0].toUpperCase());
                        } else if (auth.length < 3) {
                            sendText("%sAAA %s %s :You didn't provide enough parameters for %s.", getNumeric(), notice, nick, auth[0].toUpperCase());
                        } else if (!auth[1].equalsIgnoreCase(auth[2])) {
                            sendText("%sAAA %s %s :Sorry, but first and second email addresses don't match", getNumeric(), notice, nick);
                        } else if (authed != null) {
                            sendText("%sAAA %s %s :%s is not available once you have authed.", getNumeric(), notice, nick, auth[0].toUpperCase());
                        } else if (getMi().getDb().isRegistered(nickname)) {
                            sendText("%sAAA %s %s :Someone already has the account name $0!", getNumeric(), notice, nick, nickname);
                            sendText("%sAAA %s %s :If this is your account use AUTH to login, otherwise please change your nick using /NICK and try again.", getNumeric(), notice, nick);
                        } else if (getMi().getDb().isMail(auth[1])) {
                            sendText("%sAAA %s %s :Too many accounts  exist from this email address.", getNumeric(), notice, nick);
                        } else if (!isValidMail(auth[1])) {
                            sendText("%sAAA %s %s :%s is not a valid email address", getNumeric(), notice, nick, auth[1]);
                        } else if (!getSt().getUsers().get(nick).getAccount().isBlank()) {
                            sendText("%sAAA %s %s :Sorry, the registration service is unavailable to you at this time. Please try again later.", getNumeric(), notice, nick);
                        } else {
                            sendText("%sAAA %s %s :Account %s created successfully.", getNumeric(), notice, nick, nickname);
                            sendText("%sAAA %s %s :Information about how to access and use your new account will be sent to your email address, %s.", getNumeric(), notice, nick, auth[1]);
                            sendText("%sAAA %s %s :If you do not see an email soon be sure to check your spam folder.", getNumeric(), notice, nick);
                            getSt().getUsers().get(nick).setReg(true);
                            getMi().getDb().addUser(nickname, auth[1]);
                        }
                    } else if (auth[0].equalsIgnoreCase("sasl")) {
                        if (auth.length < 4) {
                            sendText("%sAAA AUTHENTICATE %s PARAM %s", getNumeric(), nick, auth[1]);
                        } else if (getSt().getAuthed().containsKey(nick)) {
                            sendText("%sAAA AUTHENTICATE %s ALREADY %s", getNumeric(), nick, auth[1]);
                        } else if (getMi().getDb().isRegistered(auth[2], auth[3])) {
                            getSt().getAuthed().put(nick, auth[2]);
                            sendText("%s AC %s %s %s %s", getNumeric(), nick, auth[2], getMi().getDb().getTimestamp(auth[2]), getMi().getDb().getId(auth[2]));
                            sendText("%s AUTHENTICATE %s SUCCESS %s %s", getNumeric(), nick, auth[1], auth[2]);
                        } else {
                            sendText("%s AUTHENTICATE %s NOTYOU %s %s", getNumeric(), nick, auth[2], auth[3]);
                        }
                    } else if (auth[0].equalsIgnoreCase("auth")) {
                        if (!target.equalsIgnoreCase(server)) {
                            sendText("%sAAA %s %s :To prevent sensitive information being accidentally send to malicious users", getNumeric(), notice, nick);
                            sendText("%sAAA %s %s :non other networks, when using the %s command, you must use", getNumeric(), notice, nick, auth[0].toUpperCase());
                            sendText("%sAAA %s %s :/msg %s.", getNumeric(), notice, nick, server);
                        } else if (auth.length < 3) {
                            sendText("%sAAA %s %s :You didn't provide enough parameters for %s.", getNumeric(), notice, nick, auth[0].toUpperCase());
                        } else if (!getSt().getUsers().get(nick).getAccount().isBlank()) {
                            sendText("%sAAA %s %s :%s is not available once you have authed.", getNumeric(), notice, nick, auth[0].toUpperCase());
                        } else if (getMi().getDb().isRegistered(auth[1], auth[2])) {
                            getSt().getUsers().get(nick).setAccount(auth[1]);
                            getMi().getDb().updateData("lastuserhost", auth[1], getSt().getUsers().get(nick).getRealHost());
                            getMi().getDb().updateData("lastpasschng", auth[1], time());
                            var host = getSt().getUsers().get(nick).getHost();
                            if (getSt().getUsers().get(nick).isX()) {
                                sendText("%s SH %s %s %s", getNumeric(), nick, host.split("@")[0], getSt().getUsers().get(nick).getAccount() + getMi().getConfig().getConfigFile().getProperty("reg_host"));
                            }
                            sendText("%s AC %s %s %s %s", getNumeric(), nick, auth[1], getMi().getDb().getTimestamp(auth[1]), getMi().getDb().getId(auth[1]));
                            sendText("%sAAA %s %s :You are now logged in as %s.", getNumeric(), notice, nick, auth[1]);
                            sendText("%sAAA %s %s :Remember: NO-ONE from %s will ever ask for your password.  NEVER send your password to ANYONE except %s", getNumeric(), notice, nick, network, server);
                        } else {
                            sendText("%sAAA %s %s :Username or password incorrect.", getNumeric(), notice, nick);
                        }
                    } else if (auth[0].equalsIgnoreCase("email")) {
                        if (auth.length < 4) {
                            sendText("%sAAA %s %s :You didn't provide enough parameters for %s.", getNumeric(), notice, nick, auth[0].toUpperCase());
                        } else if (!isValidMail(auth[2])) {
                            sendText("%sAAA %s %s :%s is not a valid email address", getNumeric(), notice, nick, auth[2]);
                        } else if (getSt().getUsers().get(nick).getAccount().isBlank()) {
                            sendText("%sAAA %s %s :%s is only available to authed users. Try AUTH to authenticate with your", getNumeric(), notice, nick, auth[0].toUpperCase());
                            sendText("%sAAA %s %s :account, or HELLO to create an account.", getNumeric(), notice, nick);
                        } else if (!auth[2].equalsIgnoreCase(auth[3])) {
                            sendText("%sAAA %s %s :Sorry, but first and second email addresses don't match", getNumeric(), notice, nick);
                        } else if (!getMi().getDb().isMail(auth[1])) {
                            sendText("%sAAA %s %s :Sorry, no accounts have that email address.", getNumeric(), notice, nick);
                        } else {
                            sendText("%sAAA %s %s :Ok, email changed to \"%s\".", getNumeric(), notice, nick, auth[2]);
                            getMi().getDb().updateData("email", getSt().getUsers().get(nick).getAccount(), auth[2]);
                        }
                    } else if (auth[0].equalsIgnoreCase("newpass")) {
                        if (!target.equalsIgnoreCase(server)) {
                            sendText("%sAAA %s %s :To prevent sensitive information being accidentally send to malicious users", getNumeric(), notice, nick);
                            sendText("%sAAA %s %s :non other networks, when using the %s command, you must use", getNumeric(), notice, nick, auth[0].toUpperCase());
                            sendText("%sAAA %s %s :/msg %s.", getNumeric(), notice, nick, server);
                        } else if (auth.length < 4) {
                            sendText("%sAAA %s %s :You didn't provide enough parameters for %s.", getNumeric(), notice, nick, auth[0].toUpperCase());
                        } else if (getSt().getUsers().get(nick).getAccount().isBlank()) {
                            sendText("%sAAA %s %s :%s is only available to authed users. Try AUTH to authenticate with your", getNumeric(), notice, nick, auth[0].toUpperCase());
                            sendText("%sAAA %s %s :account, or HELLO to create an account.", getNumeric(), notice, nick);
                        } else if (!auth[2].equals(auth[3])) {
                            sendText("%sAAA %s %s :Sorry, but first and second new password don't match", getNumeric(), notice, nick);
                        } else if (!getMi().getDb().getData("password", getSt().getUsers().get(nick).getAccount()).equals(auth[1])) {
                            sendText("%sAAA %s %s :Password incorrect.", getNumeric(), notice, nick);
                        } else {
                            sendText("%sAAA %s %s :Ok, password changed", getNumeric(), notice, nick);
                            getMi().getDb().updateData("password", getSt().getUsers().get(nick).getAccount(), auth[2]);
                        }
                    } else if (auth[0].equalsIgnoreCase("requestpassword")) {
                        if (auth.length < 2) {
                            sendText("%sAAA %s %s :You didn't provide enough parameters for %s.", getNumeric(), notice, nick, auth[0].toUpperCase());
                        } else if (!getSt().getUsers().get(nick).getAccount().isBlank()) {
                            sendText("%sAAA %s %s :%s is not available once you have authed.", getNumeric(), notice, nick, auth[0].toUpperCase());
                            sendText("%sAAA %s %s :account, or HELLO to create an account.", getNumeric(), notice, nick);
                        } else if (!getMi().getDb().isMail(auth[1])) {
                            sendText("%sAAA %s %s :Sorry, no accounts have that email address.", getNumeric(), notice, nick);
                        } else {
                            sendText("%sAAA %s %s :Mail queued for delivery", getNumeric(), notice, nick);
                            getMi().getDb().submitNewPassword(auth[1]);
                        }
                    } else if (auth[0].equalsIgnoreCase("userflags")) {
                        if (getSt().getUsers().get(nick).getAccount().isBlank()) {
                            sendText("%sAAA %s %s :%s is only available to authed users. Try AUTH to authenticate with your", getNumeric(), notice, nick, auth[0].toUpperCase());
                            sendText("%sAAA %s %s :account, or HELLO to create an account.", getNumeric(), notice, nick);
                        } else if (auth.length == 1) {
                            sendText("%sAAA %s %s :User flags for %s: %s", getNumeric(), notice, nick, getSt().getUsers().get(nick).getAccount(), getUserFlags(nickname));
                        } else if (auth.length == 2) {
                            if (auth[1].startsWith("+")) {
                                setUserFlags(getSt().getUsers().get(nick).getAccount(), auth[1], true, false);
                                sendText("%sAAA %s %s :User flags for %s: %s", getNumeric(), notice, nick, getSt().getUsers().get(nick).getAccount(), getUserFlags(getSt().getUsers().get(nick).getAccount()));
                                return;
                            } else if (auth[1].startsWith("-")) {
                                setUserFlags(getSt().getUsers().get(nick).getAccount(), auth[1], false, false);
                                sendText("%sAAA %s %s :User flags for %s: %s", getNumeric(), notice, nick, getSt().getUsers().get(nick).getAccount(), getUserFlags(getSt().getUsers().get(nick).getAccount()));
                                return;
                            }
                            if (!getSt().isPrivileged(getSt().getUsers().get(nick).getAccount())) {
                                sendText("%sAAA %s %s :You do not have sufficient privileges to use %s.", getNumeric(), notice, nick, auth[0]);
                                return;
                            }
                            var user = getSt().getUser(auth[1]);
                            if (user != null) {
                                sendText("%sAAA %s %s :User flags for %s: %s", getNumeric(), notice, nick, getSt().getUsers().get(user).getAccount(), getUserFlags(getSt().getUsers().get(user).getAccount()));
                            } else {
                                sendText("%sAAA %s %s :%s is not authed...", getNumeric(), notice, nick, auth[1]);
                            }

                        } else {
                            if (!getSt().isPrivileged(getSt().getUsers().get(nick).getAccount())) {
                                sendText("%sAAA %s %s :You do not have sufficient privileges to use %s.", getNumeric(), notice, nick, auth[0]);
                                return;
                            }
                            var user = getSt().getUser(auth[1]);
                            if (user != null) {
                                if (auth[2].startsWith("+")) {
                                    setUserFlags(getSt().getUsers().get(user).getAccount(), auth[2], true, true);
                                } else if (auth[2].startsWith("-")) {
                                    setUserFlags(getSt().getUsers().get(user).getAccount(), auth[2], false, true);
                                }
                                sendText("%sAAA %s %s :User flags for %s: %s", getNumeric(), notice, nick, getSt().getUsers().get(user).getAccount(), getUserFlags(getSt().getUsers().get(user).getAccount()));
                            } else {
                                sendText("%sAAA %s %s :%s is not authed...", getNumeric(), notice, nick, auth[1]);
                            }
                        }
                    } else {
                        sendText("%sAAA %s %s :Unknown command %s. Type SHOWCOMMANDS for a list of available commands.", getNumeric(), notice, nick, auth[0].toUpperCase());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setUserFlags(String nick, String flag, boolean add, boolean privs) {
        var flags = getMi().getDb().getFlags(nick);
        var priv = getSt().isPrivileged(nick);
        var userflags = getUserFlags(nick);
        if (!priv && !privs) {
            if (flag.startsWith("+") && flag.contains("n")) {
                flag = "+n";
            } else if (flag.startsWith("-") && flag.contains("n")) {
                flag = "-n";
            } else {
                return;
            }
        }
        for (char chr[] : userFlags) {
            if (add && flag.contains(String.valueOf(chr[0])) && !userflags.contains(String.valueOf(chr[0]))) {
                flags += chr[1];
            } else if (!add && flag.contains(String.valueOf(chr[0])) && userflags.contains(String.valueOf(chr[0]))) {
                flags -= chr[1];
            }
        }
        if (flags >= 0) {
            getMi().getDb().updateData("flags", nick, flags);
        }
    }

    private String getUserFlags(String nick) {
        StringBuilder sb = new StringBuilder();
        var flags = getMi().getDb().getFlags(nick);
        for (char chr[] : userFlags) {
            if ((chr[1] & flags) != 0) {
                sb.append(chr[0]);
            }
        }
        if (sb.isEmpty()) {
            return "none";
        } else {
            return "+" + sb.toString();
        }
    }

    protected void joinChannel(String channel) {
        sendText("%sAAA J %s", getNumeric(), channel);
        sendText("%s M %s +o %sAAA", getNumeric(), channel, getNumeric());
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
