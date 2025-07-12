/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;


public final class Users {

    private final String id;    
    private String nick;
    private String account;
    private String host;
    private String realHost;
    private String line = "";    
    private int flood = 0;
    private int repeat = 0;
    private List<String> channels = new ArrayList<>();
    private boolean oper = false;    
    private boolean reg = false;    
    private boolean service = false;    
    private boolean x = false;    

    private static final Logger LOG = Logger.getLogger(Users.class.getName());

    public Users(String id, String nick, String account, String host) {
        this.id = id;
        this.nick = nick;
        this.account = account;
        this.host = host;
        this.realHost = host;
    }

    public String getId() { return id; }
    public String getNick() { return nick; }
    public void setNick(String nick) { this.nick = nick; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getRealHost() { return realHost; }
    public void setRealHost(String realHost) { this.realHost = realHost; }
    public String getLine() { return line; }
    public void setLine(String line) { this.line = line; }
    public int getFlood() { return flood; }
    public void setFlood(int flood) { this.flood = flood; }
    public int getRepeat() { return repeat; }
    public void setRepeat(int repeat) { this.repeat = repeat; }
    public List<String> getChannels() { return Collections.unmodifiableList(channels); }
    public void setChannels(List<String> channels) { this.channels = new ArrayList<>(channels); }
    public boolean isOper() { return oper; }
    public void setOper(boolean oper) { this.oper = oper; }
    public boolean isReg() { return reg; }
    public void setReg(boolean reg) { this.reg = reg; }
    public boolean isService() { return service; }
    public void setService(boolean service) { this.service = service; }
    public boolean isX() { return x; }
    public void setX(boolean x) { this.x = x; }
}
