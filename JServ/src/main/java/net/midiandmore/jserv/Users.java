/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Logger;


public final class Users {

    private final String id;    
    private String nick;
    private String ident;
    private String account;
    private String host;
    private String realHost;
    private String hiddenHost;
    private String clientIp;
    private String line = "";    
    private int flood = 0;
    private int repeat = 0;
    private int capsCount = 0;
    private List<String> channels = new ArrayList<>();
    private boolean oper = false;    
    private boolean reg = false;    
    private boolean service = false;    
    private boolean x = false;
    private boolean registrationAttempted = false;
    
    // Advanced spam detection fields
    private double spamScore = 0.0;
    private Queue<MessageRecord> messageHistory = new LinkedList<>();
    private Map<String, MessageRecord> channelLastMessages = new HashMap<>();
    private int urlCount = 0;
    private long lastMessageTime = 0;
    
    /**
     * Inner class to track message history with timestamps
     */
    public static class MessageRecord {
        private final String message;
        private final long timestamp;
        private final String channel;
        
        public MessageRecord(String message, long timestamp, String channel) {
            this.message = message;
            this.timestamp = timestamp;
            this.channel = channel;
        }
        
        public String getMessage() { return message; }
        public long getTimestamp() { return timestamp; }
        public String getChannel() { return channel; }
    }

    private static final Logger LOG = Logger.getLogger(Users.class.getName());    

    public Users(String id, String nick, String ident, String account, String host) {
        this.id = id;
        this.nick = nick;
        this.ident = ident;
        this.account = account;
        this.host = host;
        this.realHost = host;
        this.hiddenHost = null;
    }

    public String getId() { return id; }
    public String getNick() { return nick; }
    public void setNick(String nick) { this.nick = nick; }
    public String getIdent() { return ident; }
    public void setIdent(String ident) { this.ident = ident; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getRealHost() { return realHost; }
    public void setRealHost(String realHost) { this.realHost = realHost; }
    public String getHiddenHost() { return hiddenHost; }
    public void setHiddenHost(String hiddenHost) { this.hiddenHost = hiddenHost; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public String getLine() { return line; }
    public void setLine(String line) { this.line = line; }
    public int getFlood() { return flood; }
    public void setFlood(int flood) { this.flood = flood; }
    public int getRepeat() { return repeat; }
    public void setRepeat(int repeat) { this.repeat = repeat; }
    public int getCapsCount() { return capsCount; }
    public void setCapsCount(int capsCount) { this.capsCount = capsCount; }
    public List<String> getChannels() { return Collections.unmodifiableList(channels); }
    public void setChannels(List<String> channels) { this.channels = new ArrayList<>(channels); }
    
    /**
     * Add a channel to the user's channel list
     * @param channel Channel name (will be converted to lowercase)
     */
    public void addChannel(String channel) {
        if (channel != null && !channels.contains(channel.toLowerCase())) {
            channels.add(channel.toLowerCase());
        }
    }
    
    /**
     * Remove a channel from the user's channel list
     * @param channel Channel name (will be converted to lowercase)
     */
    public void removeChannel(String channel) {
        if (channel != null) {
            channels.remove(channel.toLowerCase());
        }
    }
    
    public boolean isOper() { return oper; }
    public void setOper(boolean oper) { this.oper = oper; }
    public boolean isReg() { return reg; }
    public void setReg(boolean reg) { this.reg = reg; }
    public boolean isService() { return service; }
    public void setService(boolean service) { this.service = service; }
    public boolean isX() { return x; }
    public void setX(boolean x) { this.x = x; }
    public boolean hasAttemptedRegistration() { return registrationAttempted; }
    public void setRegistrationAttempted(boolean attempted) { this.registrationAttempted = attempted; }
    
    // Advanced spam detection getters/setters
    public double getSpamScore() { return spamScore; }
    public void setSpamScore(double spamScore) { this.spamScore = spamScore; }
    
    /**
     * Increase spam score by the given amount
     * @param amount Amount to increase the score
     */
    public void increaseSpamScore(double amount) {
        this.spamScore += amount;
    }
    
    /**
     * Decrease spam score (decay over time)
     * @param amount Amount to decrease the score
     */
    public void decreaseSpamScore(double amount) {
        this.spamScore = Math.max(0, this.spamScore - amount);
    }
    
    public Queue<MessageRecord> getMessageHistory() { return messageHistory; }
    
    /**
     * Add a message to the user's history (keeps last 10 messages)
     * @param message The message content
     * @param timestamp Unix timestamp
     * @param channel Channel name
     */
    public void addMessageToHistory(String message, long timestamp, String channel) {
        messageHistory.add(new MessageRecord(message, timestamp, channel));
        // Keep only last 10 messages
        while (messageHistory.size() > 10) {
            messageHistory.poll();
        }
    }
    
    /**
     * Get messages from history within a time window
     * @param timeWindow Time window in seconds
     * @param currentTime Current unix timestamp
     * @return List of messages within the time window
     */
    public List<MessageRecord> getRecentMessages(int timeWindow, long currentTime) {
        List<MessageRecord> recentMessages = new ArrayList<>();
        for (MessageRecord msgRecord : messageHistory) {
            if (currentTime - msgRecord.getTimestamp() <= timeWindow) {
                recentMessages.add(msgRecord);
            }
        }
        return recentMessages;
    }
    
    /**
     * Clean up old messages from history
     * @param maxAge Maximum age in seconds
     * @param currentTime Current unix timestamp
     */
    public void cleanupOldMessages(int maxAge, long currentTime) {
        messageHistory.removeIf(msgRecord -> currentTime - msgRecord.getTimestamp() > maxAge);
    }
    
    public Map<String, MessageRecord> getChannelLastMessages() { return channelLastMessages; }
    
    /**
     * Set the last message for a specific channel
     * @param channel Channel name
     * @param message Message content
     * @param timestamp Unix timestamp
     */
    public void setChannelLastMessage(String channel, String message, long timestamp) {
        channelLastMessages.put(channel.toLowerCase(), new MessageRecord(message, timestamp, channel));
    }
    
    /**
     * Get the last message for a specific channel
     * @param channel Channel name
     * @return MessageRecord or null if no message stored
     */
    public MessageRecord getChannelLastMessage(String channel) {
        return channelLastMessages.get(channel.toLowerCase());
    }
    
    public int getUrlCount() { return urlCount; }
    public void setUrlCount(int urlCount) { this.urlCount = urlCount; }
    public void incrementUrlCount() { this.urlCount++; }
    public void resetUrlCount() { this.urlCount = 0; }
    
    public long getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(long lastMessageTime) { this.lastMessageTime = lastMessageTime; }
}
