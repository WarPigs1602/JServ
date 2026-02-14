/*
 * Abstract base class for JServ modules
 * Provides common functionality shared by all modules
 */
package net.midiandmore.jserv;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract base class for modules
 * Implements common functionality like initialization, logging, and utility methods
 * 
 * @author Andreas Pschorn
 */
public abstract class AbstractModule implements Module {
    
    private static final Logger LOG = Logger.getLogger(AbstractModule.class.getName());
    
    protected boolean enabled = false;
    protected JServ mi;
    protected SocketThread st;
    protected PrintWriter pw;
    protected BufferedReader br;
    protected String numeric;
    protected String numericSuffix;
    
    @Override
    public void initialize(JServ jserv, SocketThread socketThread, PrintWriter pw, BufferedReader br) {
        this.mi = jserv;
        this.st = socketThread;
        this.pw = pw;
        this.br = br;
        this.enabled = false;
        LOG.log(Level.INFO, "{0} module initialized", getModuleName());
    }
    
    @Override
    public void handshake(String nick, String servername, String description, String numeric, String identd) {
        if (!enabled) {
            LOG.log(Level.WARNING, "{0} handshake called but module is disabled", getModuleName());
            return;
        }
        
        this.numeric = numeric;
        
        LOG.log(Level.INFO, "Registering {0} nick: {1}", new Object[]{getModuleName(), nick});
        
        // Register service bot with P10 protocol
        // N <nick> <hop> <timestamp> <user> <host> <modes> <base64-ip> <numeric> :<realname>
        sendText("%s N %s 2 %d %s %s +oikr - %s:%d U]AEB %s%s :%s", 
                numeric, nick, time(), identd, servername, nick, time(), numeric, getNumericSuffix(), description);
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public void enable() {
        this.enabled = true;
        LOG.log(Level.INFO, "{0} module enabled", getModuleName());
    }
    
    @Override
    public void disable() {
        this.enabled = false;
        LOG.log(Level.INFO, "{0} module disabled", getModuleName());
    }
    
    @Override
    public String getNumeric() {
        return numeric;
    }
    
    @Override
    public String getNumericSuffix() {
        return numericSuffix;
    }
    
    public void setNumericSuffix(String numericSuffix) {
        this.numericSuffix = numericSuffix;
    }
    
    // Utility methods
    
    /**
     * Get current Unix timestamp
     * @return Current time in seconds since epoch
     */
    protected long time() {
        return System.currentTimeMillis() / 1000;
    }
    
    /**
     * Send formatted text to IRC server
     * @param format Format string
     * @param args Arguments for format string
     */
    protected void sendText(String format, Object... args) {
        if (pw != null) {
            String text = String.format(format, args);
            pw.print(text + "\r\n");
            pw.flush();
        }
    }
    
    // Getter methods
    
    public JServ getMi() {
        return mi;
    }
    
    public void setMi(JServ mi) {
        this.mi = mi;
    }
    
    public SocketThread getSt() {
        return st;
    }
    
    public void setSt(SocketThread st) {
        this.st = st;
    }
    
    public PrintWriter getPw() {
        return pw;
    }
    
    public void setPw(PrintWriter pw) {
        this.pw = pw;
    }
    
    public BufferedReader getBr() {
        return br;
    }
    
    public void setBr(BufferedReader br) {
        this.br = br;
    }
    
    /**
     * Get logger for this module
     * @return Logger instance
     */
    protected Logger getLogger() {
        return Logger.getLogger(getClass().getName());
    }
    
    /**
     * Send notification to logged-in privileged users (opers/staff/admin/dev) with oper mode
     * @param message Message to send
     */
    protected void sendOperNotice(String message) {
        if (numeric == null || numericSuffix == null) {
            getLogger().log(Level.WARNING, "Cannot send oper notice: numeric not set");
            return;
        }
        
        if (st == null || st.getUsers() == null || st.getUsers().isEmpty()) {
            getLogger().log(Level.WARNING, "Cannot send oper notice: no users available");
            return;
        }
        
        if (mi == null || mi.getDb() == null) {
            getLogger().log(Level.WARNING, "Cannot send oper notice: database not available");
            return;
        }
        
        String myNumeric = numeric + numericSuffix;
        int noticesSent = 0;
        
        // Iterate through all connected users
        for (var entry : st.getUsers().entrySet()) {
            String userNumeric = entry.getKey();
            Users user = entry.getValue();
            
            // Skip users without account or oper mode
            if (user == null || user.getAccount() == null || user.getAccount().isEmpty()) {
                continue;
            }
            
            if (!user.isOper()) {
                continue;
            }
            
            // Check if user has privileged flags
            String account = user.getAccount();
            int flags = mi.getDb().getFlags(account);
            
            boolean isPrivileged = Userflags.hasFlag(flags, Userflags.Flag.OPER)
                    || Userflags.hasFlag(flags, Userflags.Flag.STAFF)
                    || Userflags.hasFlag(flags, Userflags.Flag.ADMIN)
                    || Userflags.hasFlag(flags, Userflags.Flag.DEV);
            
            if (isPrivileged) {
                // Send private NOTICE to this user
                sendText("%s O %s :%s", myNumeric, userNumeric, message);
                noticesSent++;
            }
        }
        
        if (noticesSent > 0) {
            getLogger().log(Level.INFO, "Oper notice sent to {0} privileged user(s)", noticesSent);
        }
    }
}
