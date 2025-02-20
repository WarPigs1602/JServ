/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author Andreas Pschorn
 */
public class Channel {

    /**
     * @return the lastJoin
     */
    public HashMap<String, Long> getLastJoin() {
        return lastJoin;
    }

    /**
     * @param lastJoin the lastJoin to set
     */
    public void setLastJoin(HashMap<String, Long> lastJoin) {
        this.lastJoin = lastJoin;
    }

    /**
     * @return the moderated
     */
    public boolean isModerated() {
        return moderated;
    }

    /**
     * @param moderated the moderated to set
     */
    public void setModerated(boolean moderated) {
        this.moderated = moderated;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the modes
     */
    public String getModes() {
        return modes;
    }

    /**
     * @param modes the modes to set
     */
    public void setModes(String modes) {
        this.modes = modes;
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

    /**
     * @return the op
     */
    public ArrayList<String> getOp() {
        return op;
    }

    /**
     * @param op the op to set
     */
    public void setOp(ArrayList<String> op) {
        this.op = op;
    }

    /**
     * @return the voice
     */
    public ArrayList<String> getVoice() {
        return voice;
    }

    /**
     * @param voice the voice to set
     */
    public void setVoice(ArrayList<String> voice) {
        this.voice = voice;
    }

    private String name;
    private String modes;
    private boolean moderated;
    private ArrayList<String> users;
    private ArrayList<String> op;
    private ArrayList<String> voice;
    private HashMap<String, Long> lastJoin;

    public Channel(String name, String modes, String[] names) {
        setName(name);
        setModes(modes);
        setUsers(new ArrayList<>());
        setOp(new ArrayList<>());
        setVoice(new ArrayList<>());
        setLastJoin(new HashMap<>());
        setModerated(modes.contains("m"));
        var voice = false;
        var op = false;
        for (var nick : names) {
            if (nick.contains(":")) {
                var elem = nick.split(":", 2);
                nick = elem[0];
                var status = elem[1];
                if(status.equals("o")) {
                    op = true;
                    voice = false;
                } else if(status.equals("v")) {
                    op = false;
                    voice = true;
                } else if(status.equals("ov") || status.equals("vo")) {
                    op = true;
                    voice = true;
                } 
            }
            if(op) {
                getOp().add(nick);
            }
            if(voice) {
                getVoice().add(nick);
            }
            getUsers().add(nick);
            getLastJoin().put(nick, System.currentTimeMillis() / 1000);
        } 
    }
}
