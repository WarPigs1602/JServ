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
    private String topic;
    private final List<String> users;
    private final List<String> op = new ArrayList<>();
    private final List<String> voice = new ArrayList<>();
    private final Map<String, Long> lastJoin = new HashMap<>();
    private Long createdTimestamp;
    private static final Logger LOG = Logger.getLogger(Channel.class.getName());

    public Channel(String name, String modes, String[] users) {
        this.name = name;
        this.modes = modes;
        this.moderated = modes.contains("m");
        this.users = new ArrayList<>();
        Collections.addAll(this.users, users);
        for (String nick : users) {
            boolean voice = false, op = false;
            if (nick.contains(":")) {
                String[] elem = nick.split(":", 2);
                nick = elem[0];
                for (char status : elem[1].toCharArray()) {
                    switch (status) {
                        case 'o': op = true; break;
                        case 'v': voice = true; break;
                    }
                }
            }
            if (op) this.op.add(nick);
            if (voice) this.voice.add(nick);
            this.lastJoin.put(nick, System.currentTimeMillis() / 1000);
        }
    }

    public String getName() { return name; }
    public String getModes() { return modes; }
    public boolean isModerated() { return moderated; }
    public String getTopic() { return topic; }
    public List<String> getUsers() { return Collections.unmodifiableList(users); }
    public List<String> getOp() { return Collections.unmodifiableList(op); }
    public List<String> getVoice() { return Collections.unmodifiableList(voice); }
    public Map<String, Long> getLastJoin() { return lastJoin; }
    public Long getCreatedTimestamp() { return createdTimestamp; }
    public void setCreatedTimestamp(Long timestamp) { this.createdTimestamp = timestamp; }

    /**
     * @param modes the modes to set
     */
    public void setModes(String modes) {
        this.modes = modes;
    }

    /**
     * @param topic the topic to set
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    public void addUser(String user) { users.add(user); }
    public void removeUser(String user) { users.remove(user); }

    public void addOp(String user) { op.add(user); }
    public void removeOp(String user) { op.remove(user); }

    public void addVoice(String user) { voice.add(user); }
    public void removeVoice(String user) { voice.remove(user); }
}
