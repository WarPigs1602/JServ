/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import java.util.*;
import java.util.logging.Logger;

public final class Channel {

    /**
     * @param moderated the moderated to set
     */
    public void setModerated(boolean moderated) {
        this.moderated = moderated;
    }

    private final String name;
    private String modes;
    private boolean moderated;
    private final List<String> users;
    private final List<String> hop = new ArrayList<>();
    private final List<String> admin = new ArrayList<>();
    private final List<String> service = new ArrayList<>();
    private final List<String> op = new ArrayList<>();
    private final List<String> voice = new ArrayList<>();
    private final List<String> owner = new ArrayList<>();
    private final Map<String, Long> lastJoin = new HashMap<>();
    private static final Logger LOG = Logger.getLogger(Channel.class.getName());

    public Channel(String name, String modes, String[] users) {
        this.name = name;
        this.modes = modes;
        this.moderated = modes.contains("m");
        this.users = new ArrayList<>();
        Collections.addAll(this.users, users);
        for (String nick : users) {
            boolean voice = false, op = false, hop = false, service = false, owner = false, admin = false;
            if (nick.contains(":")) {
                String[] elem = nick.split(":", 2);
                nick = elem[0];
                for (char status : elem[1].toCharArray()) {
                    switch (status) {
                        case 'O': service = true; break;
                        case 'q': owner = true; break;
                        case 'a': admin = true; break;
                        case 'o': op = true; break;
                        case 'h': hop = true; break;
                        case 'v': voice = true; break;
                    }
                }
            }
            if (op) this.op.add(nick);
            if (voice) this.voice.add(nick);
            if (hop) this.hop.add(nick);
            if (service) this.service.add(nick);
            if (admin) this.admin.add(nick);
            if (owner) this.owner.add(nick);
            this.lastJoin.put(nick, System.currentTimeMillis() / 1000);
        }
    }

    public String getName() { return name; }
    public String getModes() { return modes; }
    public boolean isModerated() { return moderated; }
    public List<String> getUsers() { return Collections.unmodifiableList(users); }
    public List<String> getOp() { return Collections.unmodifiableList(op); }
    public List<String> getVoice() { return Collections.unmodifiableList(voice); }
    public List<String> getHop() { return Collections.unmodifiableList(hop); }
    public List<String> getAdmin() { return Collections.unmodifiableList(admin); }
    public List<String> getService() { return Collections.unmodifiableList(service); }
    public List<String> getOwner() { return Collections.unmodifiableList(owner); }
    public Map<String, Long> getLastJoin() { return lastJoin; }

    /**
     * @param modes the modes to set
     */
    public void setModes(String modes) {
        this.modes = modes;
    }

    public void addUser(String user) { users.add(user); }
    public void removeUser(String user) { users.remove(user); }

    public void addOp(String user) { op.add(user); }
    public void removeOp(String user) { op.remove(user); }

    public void addVoice(String user) { voice.add(user); }
    public void removeVoice(String user) { voice.remove(user); }

    public void addHop(String user) { hop.add(user); }
    public void removeHop(String user) { hop.remove(user); }

    public void addAdmin(String user) { admin.add(user); }
    public void removeAdmin(String user) { admin.remove(user); }

    public void addService(String user) { service.add(user); }
    public void removeService(String user) { service.remove(user); }

    public void addOwner(String user) { owner.add(user); }
    public void removeOwner(String user) { owner.remove(user); }
}
