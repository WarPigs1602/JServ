/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import java.util.ArrayList;

/**
 *
 * @author windo
 */
public class Burst {

    /**
     * @return the channel
     */
    public String getChannel() {
        return channel;
    }

    /**
     * @param channel the channel to set
     */
    public void setChannel(String channel) {
        this.channel = channel;
    }

    /**
     * @return the users
     */
    public ArrayList<String> getUsers() {
        return users;
    }

    /**
     * @param users the users to set
     */
    public void setUsers(ArrayList<String> users) {
        this.users = users;
    }
    private String channel;
    private ArrayList<String> users;
    private long time;    
    
    public Burst(String channel) {
        setChannel(channel);
        setUsers(new ArrayList<>());
        setTime(System.currentTimeMillis() / 1000);
    }

    /**
     * @return the time
     */
    public long getTime() {
        return time;
    }

    /**
     * @param time the time to set
     */
    public void setTime(long time) {
        this.time = time;
    }
}
