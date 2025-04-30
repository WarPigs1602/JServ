/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import java.util.ArrayList;
import java.util.logging.Logger;


public final class Users {

    /**
     * @return the oper
     */
    public boolean isOper() {
        return oper;
    }

    /**
     * @param oper the oper to set
     */
    public void setOper(boolean oper) {
        this.oper = oper;
    }

    /**
     * @return the realHost
     */
    public String getRealHost() {
        return realHost;
    }

    /**
     * @param realHost the realHost to set
     */
    public void setRealHost(String realHost) {
        this.realHost = realHost;
    }

    /**
     * @return the x
     */
    public boolean isX() {
        return x;
    }

    /**
     * @param x the x to set
     */
    public void setX(boolean x) {
        this.x = x;
    }

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
     * @return the account
     */
    public String getAccount() {
        return account;
    }

    /**
     * @param account the account to set
     */
    public void setAccount(String account) {
        this.account = account;
    }

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @param host the host to set
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * @return the line
     */
    public String getLine() {
        return line;
    }

    /**
     * @param line the line to set
     */
    public void setLine(String line) {
        this.line = line;
    }

    /**
     * @return the flood
     */
    public int getFlood() {
        return flood;
    }

    /**
     * @param flood the flood to set
     */
    public void setFlood(int flood) {
        this.flood = flood;
    }

    /**
     * @return the repeat
     */
    public int getRepeat() {
        return repeat;
    }

    /**
     * @param repeat the repeat to set
     */
    public void setRepeat(int repeat) {
        this.repeat = repeat;
    }

    /**
     * @return the channels
     */
    public ArrayList<String> getChannels() {
        return channels;
    }

    /**
     * @param channels the channels to set
     */
    public void setChannels(ArrayList<String> channels) {
        this.channels = channels;
    }

    private String id;    
    private String nick;
    private String account;
    private String host;
    private String realHost;
    private String line;    
    private int flood;
    private int repeat;
    private ArrayList<String> channels;
    private boolean oper;    
    private boolean reg;    
    private boolean service;    
    private boolean x;    
    public Users(String id, String nick, String account, String host) {
        setId(id);
        setNick(nick);
        setAccount(account);
        setHost(host);
        setRealHost(host);
        setLine("");
        setFlood(0);
        setRepeat(0);
        setReg(false);
        setChannels(new ArrayList<>());
        setX(false);
        setService(false);
        setOper(false);
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
     * @return the service
     */
    public boolean isService() {
        return service;
    }

    /**
     * @param service the service to set
     */
    public void setService(boolean service) {
        this.service = service;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }
    private static final Logger LOG = Logger.getLogger(Users.class.getName());
}
