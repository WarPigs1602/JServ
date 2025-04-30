/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;


public final class Channel {

    /**
     * @return the owner
     */
    public ArrayList<String> getOwner() {
        return owner;
    }

    /**
     * @param owner the owner to set
     */
    public void setOwner(ArrayList<String> owner) {
        this.owner = owner;
    }

    /**
     * @return the hop
     */
    public ArrayList<String> getHop() {
        return hop;
    }

    /**
     * @param hop the hop to set
     */
    public void setHop(ArrayList<String> hop) {
        this.hop = hop;
    }

    /**
     * @return the service
     */
    public ArrayList<String> getService() {
        return service;
    }

    /**
     * @param service the service to set
     */
    public void setService(ArrayList<String> service) {
        this.service = service;
    }

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
    private ArrayList<String> hop;
    private ArrayList<String> admin;
    private ArrayList<String> service;
    private ArrayList<String> op;
    private ArrayList<String> voice;
    private ArrayList<String> owner;
    private HashMap<String, Long> lastJoin;

    public Channel(String name, String modes, String[] names) {
        setName(name);
        setModes(modes);
        setUsers(new ArrayList<>());
        setOp(new ArrayList<>());
        setVoice(new ArrayList<>());
        setHop(new ArrayList<>());
        setAdmin(new ArrayList<>());
        setService(new ArrayList<>());
        setOwner(new ArrayList<>());
        setLastJoin(new HashMap<>());
        setModerated(modes.contains("m"));
        for (var nick : names) {
            var voice = false;
            var op = false;
            var hop = false;
            var service = false;
            var owner = false;
            var admin = false;
            if (nick.contains(":")) {
                var elem = nick.split(":", 2);
                nick = elem[0];
                var stats = elem[1].split("");
                for (var status : stats) {
                    if (status.contains("O")) {
                        service = true;
                    }
                    if (status.contains("q")) {
                        owner = true;
                    }
                    if (status.contains("a")) {
                        admin = true;
                    }
                    if (status.contains("o")) {
                        op = true;
                    }
                    if (status.contains("h")) {
                        hop = true;
                    }
                    if (status.contains("v")) {
                        voice = true;
                    }
                }
            }
            if (op) {
                getOp().add(nick);
            }
            if (voice) {
                getVoice().add(nick);
            }
            if (hop) {
                getHop().add(nick);
            }
            if (service) {
                getService().add(nick);
            }
            if (admin) {
                getAdmin().add(nick);
            }
            if (owner) {
                getOwner().add(nick);
            }
            getUsers().add(nick);
            getLastJoin().put(nick, System.currentTimeMillis() / 1000);
        }
    }

    /**
     * @return the admin
     */
    public ArrayList<String> getAdmin() {
        return admin;
    }

    /**
     * @param admin the admin to set
     */
    public void setAdmin(ArrayList<String> admin) {
        this.admin = admin;
    }
    private static final Logger LOG = Logger.getLogger(Channel.class.getName());
}
