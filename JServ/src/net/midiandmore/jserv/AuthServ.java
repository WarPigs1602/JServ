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
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.digest.DigestUtils;
import static org.apache.commons.codec.digest.HmacAlgorithms.HMAC_SHA_256;
import org.apache.commons.codec.digest.HmacUtils;

/**
 * Starts a new Thread
 *
 * @author Andreas Pschorn
 */
public class AuthServ implements Userflags, Messages, Software {

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

    public int checkPasswordQuality(String pwd) {
        int cntweak = 0, cntdigits = 0, cntletters = 0;
        var password = pwd.toCharArray();
        if (password.length < 6) {
            return 1;
        }
        if (password.length > 10) {
            return 2;
        }
        for (var i = 0; i < password.length; i++) {
            if (password.length != i + 1 && (password[i] == password[i + 1] || password[i] + 1 == password[i + 1] || password[i] - 1 == password[i + 1])) {
                cntweak++;
            }
            if (Character.isDigit(password[i])) {
                cntdigits++;
            }
            if (Character.isLowerCase(password[i]) || Character.isUpperCase(password[i])) {
                cntletters++;
            }
            if (password[i] < 32 || password[i] > 127) {
                return 3;
            }
        }
        if (cntweak > 3 || cntdigits == 0 || cntletters == 0) {
            return 4;
        }
        return 0;
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
        sendText("%s N %s 2 %d %s %s +oikrd %s U]AEA %sAAA :%s", getNumeric(), getNick(), time(), getIdentd(), getServername(), getNick(), getNumeric(), getDescription());
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
                if (elem[1].equals("P") && (elem[2].equals(getNumeric() + "AAA") || server.equalsIgnoreCase(elem[2]))) {
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
                        if (auth.length < 3) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_NOTENOUGHPARAMS, auth[0].toUpperCase());
                        } else if (!auth[1].equalsIgnoreCase(auth[2])) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_EMAILDONTMATCH);
                        } else if (authed != null) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_UNAUTHEDONLY, auth[0].toUpperCase());
                        } else if (getMi().getDb().isRegistered(nickname)) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_AUTHNAMEINUSE, nickname);
                        } else if (getMi().getDb().isMail(auth[1])) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_ADDRESSLIMIT);
                        } else if (!isValidMail(auth[1])) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_INVALIDEMAIL, auth[1]);
                        } else if (!getSt().getUsers().get(nick).getAccount().isBlank()) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_MAXHELLOLIMIT);
                        } else {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_NEWACCOUNT, nickname, auth[1]);
                            getSt().getUsers().get(nick).setReg(true);
                            getMi().getDb().addUser(nickname, auth[1]);
                        }
                    } else if (auth[0].equalsIgnoreCase("auth")) {
                        if (!target.equalsIgnoreCase(server)) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_SECUREONLY, auth[0].toUpperCase(), server);
                        } else if (auth.length < 3) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_NOTENOUGHPARAMS, auth[0].toUpperCase());
                        } else if (getSt().getUsers().containsKey(nick) && !getSt().getUsers().get(nick).getAccount().isBlank()) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_UNAUTHEDONLY, auth[0].toUpperCase());
                        } else if (getMi().getDb().isRegistered(auth[1], auth[2])) {
                            getSt().getUsers().get(nick).setAccount(auth[1]);
                            getMi().getDb().updateData("lastuserhost", auth[1], getSt().getUsers().get(nick).getRealHost());
                            getMi().getDb().updateData("lastpasschng", auth[1], time());
                            getMi().getDb().updateData("lastauth", auth[1], time());
                            var host = getSt().getUsers().get(nick).getHost();
                            getMi().getDb().addAuthHistory(auth[1], getSt().getUsers().get(nick).getNick(), getMi().getDb().getData("email", auth[1]), getSt().getUsers().get(nick).getRealHost().split("@")[0], getSt().getUsers().get(nick).getRealHost().split("@")[1]);
                            if (getSt().getUsers().get(nick).isX()) {
                                sendText("%s SH %s %s %s", getNumeric(), nick, host.split("@")[0], getSt().getUsers().get(nick).getAccount() + getMi().getConfig().getConfigFile().getProperty("reg_host"));
                            }
                            if (getMi().getConfig().getAuthFile().getOrDefault("is_ircu", "false").equals("false")) {
                                sendText("%s AC %s %s %s %s", getNumeric(), nick, auth[1], getMi().getDb().getTimestamp(auth[1]), getMi().getDb().getId(auth[1]));
                            } else {
                                sendText("%s AC %s %s %s %s", getNumeric(), nick, auth[1], getMi().getDb().getId(auth[1]), getMi().getDb().getFlags(auth[1]));
                            }
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_AUTHOK, auth[1], getMi().getConfig().getConfigFile().getProperty("network"), server);
                        } else {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_AUTHFAIL);
                        }
                    } else if (auth[0].equalsIgnoreCase("email")) {
                        if (auth.length < 4) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_NOTENOUGHPARAMS, auth[0].toUpperCase());
                        } else if (!isValidMail(auth[2])) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_INVALIDEMAIL, auth[1]);
                        } else if (getSt().getUsers().get(nick).getAccount().isBlank()) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_AUTHEDONLY, auth[0].toUpperCase());
                        } else if (!auth[2].equalsIgnoreCase(auth[3])) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_EMAILDONTMATCH);
                        } else if (!getMi().getDb().isMail(auth[1])) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_BADEMAIL);
                        } else {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_EMAILCHANGED, auth[2]);
                            getMi().getDb().updateData("email", getSt().getUsers().get(nick).getAccount(), auth[2]);
                        }
                    } else if (auth[0].equalsIgnoreCase("newpass")) {
                        if (!target.equalsIgnoreCase(server)) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_SECUREONLY, auth[0].toUpperCase(), server);
                        } else if (auth.length < 4) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_NOTENOUGHPARAMS, auth[0].toUpperCase());
                        } else if (getSt().getUsers().get(nick).getAccount().isBlank()) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_UNAUTHEDONLY, auth[0].toUpperCase());
                        } else if (checkPasswordQuality(auth[2]) != 0) {
                            var quality = checkPasswordQuality(auth[2]);
                            if (quality == 1) {
                                getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_PWTOSHORT);
                            } else if (quality == 2) {
                                getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_PWTOLONG);
                            } else if (quality == 3) {
                                getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_PWINVALID);
                            } else if (quality == 4) {
                                getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_PWTOWEAK);
                            }
                        } else if (!auth[2].equals(auth[3])) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_EMAILDONTMATCH);
                        } else if (!getMi().getDb().getData("password", getSt().getUsers().get(nick).getAccount()).equals(auth[1])) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_AUTHFAIL);
                        } else if (getMi().getDb().getLongData("lockuntil", getSt().getUsers().get(nick).getAccount()) > time() + 7 * 24 * 3600) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_ACCOUNTLOCKED, new Date(getMi().getDb().getLongData("lockuntil", getSt().getUsers().get(nick).getAccount())).toString());
                        } else {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_PWCHANGED);
                            getMi().getDb().updateData("password", getSt().getUsers().get(nick).getAccount(), auth[2]);
                            getMi().getDb().updateData("lockuntil", getSt().getUsers().get(nick).getAccount(), time() + 7 * 24 * 3600);
                            getMi().getDb().updateData("lastpasschng", getSt().getUsers().get(nick).getAccount(), time() + 7 * 24 * 3600);
                        }
                    } else if (auth[0].equalsIgnoreCase("requestpassword")) {
                        if (auth.length < 2) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_NOTENOUGHPARAMS, auth[0].toUpperCase());
                        } else if (!getSt().getUsers().get(nick).getAccount().isBlank()) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_UNAUTHEDONLY, auth[0].toUpperCase());
                        } else if (!getMi().getDb().isMail(auth[1])) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_BADEMAIL);
                        } else {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_MAILQUEUED);
                            getMi().getDb().submitNewPassword(auth[1]);
                        }
                    } else if (auth[0].equalsIgnoreCase("reset")) {
                        if (auth.length < 3) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_NOTENOUGHPARAMS, auth[0].toUpperCase());
                        } else if (!getSt().getUsers().get(nick).getAccount().isBlank()) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_UNAUTHEDONLY, auth[0].toUpperCase());
                        } else if (getMi().getDb().getLongData("lockuntil", getSt().getUsers().get(nick).getAccount()) > time() + 7 * 24 * 3600) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_ACCOUNTNOTLOCKED, auth[0].toUpperCase());
                        } else {
                            var data = getMi().getDb().getData();
                            for (var userData : data) {
                                var code = new HmacUtils(HMAC_SHA_256, "%s:codegenerator".formatted(getMi().getConfig().getAuthFile().getProperty("q9secret"))).hmacHex("%s:%s".formatted(userData[1], userData[10]));
                                var user = userData[1].startsWith("#") ? userData[1].substring(1) : userData[1];
                                var userId = getMi().getDb().getId(user);
                                if (userId == null) {
                                    continue;
                                }
                                var oldpass = getMi().getDb().getAccountHistory("oldpassword", Integer.valueOf(userId));
                                if (oldpass != null && user.equalsIgnoreCase(auth[1]) && code.equals(auth[2])) {
                                    getMi().getDb().updateData("password", user, oldpass);
                                    getMi().getDb().updateData("lockuntil", user, 0);
                                    getMi().getDb().deleteAccountHistory(Integer.valueOf(userId));
                                    getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_PWCHANGED);
                                    return;
                                }
                            }
                        }
                    } else if (auth[0].equalsIgnoreCase("userflags")) {
                        if (getSt().getUsers().get(nick).getAccount().isBlank()) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_AUTHEDONLY, auth[0].toUpperCase());
                        } else if (auth.length == 1) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_CURUSERFLAGS, getSt().getUsers().get(nick).getAccount(), getUserFlags(getSt().getUsers().get(nick).getAccount()));
                        } else if (auth.length == 2) {
                            if (!getSt().isOper(getSt().getUsers().get(nick).getAccount())) {
                                getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_NOACCESSONUSER, auth[0], auth[1]);
                                return;
                            }
                            if (auth[1].startsWith("+")) {
                                setUserFlags(getSt().getUsers().get(nick).getAccount(), auth[1], true, false);
                                getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_CURUSERFLAGS, getSt().getUsers().get(nick).getAccount(), getUserFlags(getSt().getUsers().get(nick).getAccount()));
                                return;
                            } else if (auth[1].startsWith("-")) {
                                setUserFlags(getSt().getUsers().get(nick).getAccount(), auth[1], false, false);
                                getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_CURUSERFLAGS, getSt().getUsers().get(nick).getAccount(), getUserFlags(getSt().getUsers().get(nick).getAccount()));
                                return;
                            }
                            var user = getSt().getUser(auth[1]);
                            if (user != null) {
                                getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_CURUSERFLAGS, getSt().getUsers().get(user).getAccount(), getUserFlags(getSt().getUsers().get(user).getAccount()));
                            } else {
                                getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_USERNOTAUTHED, auth[1]);
                            }

                        } else {
                            if (!getSt().isOper(getSt().getUsers().get(nick).getAccount())) {
                                getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_NOACCESSONUSER, auth[0], auth[1]);
                                return;
                            }
                            var user = getSt().getUser(auth[1]);
                            if (user != null) {
                                if (auth[2].startsWith("+")) {
                                    setUserFlags(getSt().getUsers().get(user).getAccount(), auth[2], true, true);
                                } else if (auth[2].startsWith("-")) {
                                    setUserFlags(getSt().getUsers().get(user).getAccount(), auth[2], false, true);
                                }
                                getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_CURUSERFLAGS, getSt().getUsers().get(user).getAccount(), getUserFlags(getSt().getUsers().get(user).getAccount()));
                            } else {
                                getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_USERNOTAUTHED, auth[1]);
                            }
                        }
                    } else if (auth[0].equalsIgnoreCase("showcommands")) {
                        var authed = !getSt().getUsers().get(nick).getAccount().isBlank();
                        getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_COMMANDLIST);
                        if (!authed) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, "   AUTH             Authenticates you on the bot");
                        }
                        if (authed) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, "   EMAIL            Change your email address.");
                        }
                        if (!authed) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, "   HELLO            Creates a new user account.");
                        }                        
                        getSt().sendNotice(getNumeric(), "AAA", notice, nick, "   HELP             Shows a specific help to a command.");
                        if (!authed) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, "   NEWPASS          Set your new password.");
                        }
                        getSt().sendNotice(getNumeric(), "AAA", notice, nick, "   SHOWCOMMANDS     Shows this list.");
                        if (!authed) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, "   REQUESTPASSWORD  Requests the current password by email.");
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, "   RESET            Restores the old details on an account after a change.");
                        }
                        if (getSt().isOper(nick)) {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, "+o USERFLAGS        Shows and sets user flags.");
                        } else {
                            getSt().sendNotice(getNumeric(), "AAA", notice, nick, "   USERFLAGS        Shows you user flags.");

                        }
                        getSt().sendNotice(getNumeric(), "AAA", notice, nick, "   VERSION          Print version info.");
                        getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_ENDOFLIST);
                    } else if (auth[0].equalsIgnoreCase("VERSION")) {
                        getSt().sendNotice(getNumeric(), "AAA", notice, nick, "AuthServ v%s by %s", VERSION, VENDOR);
                        getSt().sendNotice(getNumeric(), "AAA", notice, nick, "Based on JServ v%s", VERSION);
                        getSt().sendNotice(getNumeric(), "AAA", notice, nick, "Created by %s", AUTHOR);
                    } else {
                        getSt().sendNotice(getNumeric(), "AAA", notice, nick, QM_UNKNOWNCMD, auth[0].toUpperCase());
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
            } else if (add && flag.contains(String.valueOf(chr[0]))) {
                flags += chr[1];
            } else if (!add && flag.contains(String.valueOf(chr[0]))) {
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
        if (getSt().getChannel().containsKey(channel.toLowerCase())) {
            sendText("%sAAA J %s %d", getNumeric(), channel.toLowerCase(), time());
        } else {
            sendText("%sAAA C %s %d", getNumeric(), channel.toLowerCase(), time());
        }
        sendText("%s M %s +o %sAAA", getNumeric(), channel.toLowerCase(), getNumeric());
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
