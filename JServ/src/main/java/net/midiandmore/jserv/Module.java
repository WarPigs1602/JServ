/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package net.midiandmore.jserv;

import java.io.BufferedReader;
import java.io.PrintWriter;

/**
 * Interface for JServ modules (SpamScan, HostServ, etc.)
 * Modules can be enabled/disabled dynamically and handle their own lifecycle
 * 
 * @author Andreas Pschorn
 */
public interface Module {
    
    /**
     * Initialize the module
     * Called when the module is loaded
     * 
     * @param jserv The main JServ instance
     * @param socketThread The socket thread
     * @param pw PrintWriter for sending data
     * @param br BufferedReader for receiving data
     */
    void initialize(JServ jserv, SocketThread socketThread, PrintWriter pw, BufferedReader br);
    
    /**
     * Perform handshake with IRC server
     * Registers the service nickname
     * 
     * @param nick Service nickname
     * @param servername Server name
     * @param description Service description
     * @param numeric Server numeric
     * @param identd Ident string
     */
    void handshake(String nick, String servername, String description, String numeric, String identd);
    
    /**
     * Parse incoming IRC protocol line
     * Called for each line received from the server
     * 
     * @param text The raw IRC protocol line
     */
    void parseLine(String text);
    
    /**
     * Shutdown the module gracefully
     * Called when module is being disabled or JServ is shutting down
     */
    void shutdown();
    
    /**
     * Get the module name
     * 
     * @return Module name (e.g. "SpamScan", "HostServ")
     */
    String getModuleName();
    
    /**
     * Get the service numeric suffix
     * 
     * @return Numeric suffix (e.g. "AAC" for SpamScan, "AAB" for HostServ)
     */
    String getNumericSuffix();
    
    /**
     * Check if module is enabled
     * 
     * @return true if enabled, false otherwise
     */
    boolean isEnabled();
    
    /**
     * Enable the module
     */
    void enable();
    
    /**
     * Disable the module
     */
    void disable();
    
    /**
     * Get the module's numeric prefix
     * 
     * @return The server numeric prefix
     */
    String getNumeric();
    
    /**
     * Register channels that this module should join during burst
     * Each module can specify which channels it needs to be in
     * 
     * @param bursts The burst map to register channels in
     * @param serverNumeric The server numeric prefix
     */
    default void registerBurstChannels(java.util.HashMap<String, Burst> bursts, String serverNumeric) {
        // Default implementation does nothing - modules override if they need channels
    }
    
    /**
     * Handle new user connection (N command during burst or after)
     * Modules can perform actions when a new user connects
     * 
     * @param numeric User numeric
     * @param nick User nickname
     * @param ident User ident
     * @param host User host
     * @param account User account (empty if not authed)
     * @param serverNumeric Server numeric prefix
     * @return true if user was handled/killed by module, false otherwise
     */
    default boolean handleNewUser(String numeric, String nick, String ident, String host, String account, String serverNumeric) {
        return false; // Default: user not handled
    }
    
    /**
     * Handle user authentication (AC command)
     * Modules can perform actions when a user authenticates
     * 
     * @param numeric User numeric
     * @param account Account name
     * @param serverNumeric Server numeric prefix
     */
    default void handleAuthentication(String numeric, String account, String serverNumeric) {
        // Default implementation does nothing
    }
    
    /**
     * Perform module-specific initialization after module loading
     * Called after all modules are loaded but before handshakes
     */
    default void postLoadInitialization() {
        // Default implementation does nothing
    }
}
