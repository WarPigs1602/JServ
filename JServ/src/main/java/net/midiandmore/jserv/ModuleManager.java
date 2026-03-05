/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package net.midiandmore.jserv;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages all IRC service modules (SpamScan, HostServ, etc.)
 * Handles module lifecycle, enabling/disabling, and message routing
 * 
 * @author Andreas Pschorn
 */
public class ModuleManager {
    
    private static final Logger LOG = Logger.getLogger(ModuleManager.class.getName());
    private static final int DEFAULT_IRCU2_NICKLEN = 12;
    
    private final Map<String, Module> modules = new HashMap<>();
    private final JServ jserv;
    private final SocketThread socketThread;
    private PrintWriter printWriter;
    private BufferedReader bufferedReader;
    
    /**
     * Creates a new ModuleManager
     * 
     * @param jserv Main JServ instance
     * @param socketThread Socket thread for IRC communication
     */
    public ModuleManager(JServ jserv, SocketThread socketThread) {
        this.jserv = jserv;
        this.socketThread = socketThread;
        LOG.log(Level.INFO, "ModuleManager initialized");
    }
    
    /**
     * Load modules from configuration file
     * Automatically instantiates and registers modules defined in config
     * 
     * @param configFile Path to module configuration file
     */
    public void loadModulesFromConfig(String configFile) {
        ModuleConfig config = ModuleConfig.load(configFile);
        
        for (ModuleConfig.ModuleDefinition def : config.getModules()) {
            try {
                // Load module class
                Class<?> moduleClass = Class.forName(def.getClassName());
                
                // Check if it implements Module interface
                if (!Module.class.isAssignableFrom(moduleClass)) {
                    LOG.log(Level.WARNING, "Class {0} does not implement Module interface", def.getClassName());
                    continue;
                }
                
                // Instantiate module using constructor (JServ, SocketThread, PrintWriter, BufferedReader)
                Module module = (Module) moduleClass
                        .getConstructor(JServ.class, SocketThread.class, PrintWriter.class, BufferedReader.class)
                        .newInstance(jserv, socketThread, printWriter, bufferedReader);
                
                // Set the numeric suffix from configuration
                if (module instanceof AbstractModule) {
                    ((AbstractModule) module).setNumericSuffix(def.getNumericSuffix());
                } else if (module instanceof SpamScan) {
                    ((SpamScan) module).setNumericSuffix(def.getNumericSuffix());
                } else if (module instanceof HostServ) {
                    ((HostServ) module).setNumericSuffix(def.getNumericSuffix());
                } else if (module instanceof NickServ) {
                    ((NickServ) module).setNumericSuffix(def.getNumericSuffix());
                }
                
                // Register module
                registerModule(module);
                
                // Enable module if configured
                if (def.isEnabled()) {
                    enableModule(module.getModuleName());
                }
                
                LOG.log(Level.INFO, "Loaded module {0} from class {1}", 
                        new Object[]{def.getName(), def.getClassName()});
                
            } catch (Exception e) {
                LOG.log(Level.SEVERE, () -> "Failed to load module " + def.getName() + " from class " + def.getClassName());
                LOG.log(Level.SEVERE, "Exception details", e);
            }
        }
    }
    
    /**
     * Set the IO streams for modules
     * 
     * @param pw PrintWriter for output
     * @param br BufferedReader for input
     */
    public void setStreams(PrintWriter pw, BufferedReader br) {
        this.printWriter = pw;
        this.bufferedReader = br;
    }
    
    /**
     * Register a new module
     * 
     * @param module The module to register
     */
    public void registerModule(Module module) {
        String moduleName = module.getModuleName();
        if (modules.containsKey(moduleName)) {
            LOG.log(Level.WARNING, "Module {0} is already registered", moduleName);
            return;
        }
        
        module.initialize(jserv, socketThread, printWriter, bufferedReader);
        modules.put(moduleName, module);
        LOG.log(Level.INFO, "Module {0} registered successfully", moduleName);
    }
    
    /**
     * Enable a module by name
     * 
     * @param moduleName Name of the module to enable
     * @return true if enabled successfully, false otherwise
     */
    public boolean enableModule(String moduleName) {
        Module module = modules.get(moduleName);
        if (module == null) {
            LOG.log(Level.WARNING, "Cannot enable module {0}: not found", moduleName);
            return false;
        }
        
        if (module.isEnabled()) {
            LOG.log(Level.INFO, "Module {0} is already enabled", moduleName);
            return true;
        }
        
        module.enable();
        LOG.log(Level.INFO, "Module {0} enabled", moduleName);
        return true;
    }
    
    /**
     * Disable a module by name
     * 
     * @param moduleName Name of the module to disable
     * @return true if disabled successfully, false otherwise
     */
    public boolean disableModule(String moduleName) {
        Module module = modules.get(moduleName);
        if (module == null) {
            LOG.log(Level.WARNING, "Cannot disable module {0}: not found", moduleName);
            return false;
        }
        
        if (!module.isEnabled()) {
            LOG.log(Level.INFO, "Module {0} is already disabled", moduleName);
            return true;
        }
        
        module.disable();
        LOG.log(Level.INFO, "Module {0} disabled", moduleName);
        return true;
    }
    
    /**
     * Get a module by name
     * 
     * @param moduleName Name of the module
     * @return The module, or null if not found
     */
    public Module getModule(String moduleName) {
        return modules.get(moduleName);
    }
    
    /**
     * Check if a module is enabled
     * 
     * @param moduleName Name of the module
     * @return true if enabled, false otherwise
     */
    public boolean isModuleEnabled(String moduleName) {
        Module module = modules.get(moduleName);
        return module != null && module.isEnabled();
    }
    
    /**
     * Perform handshake for all enabled modules
     * 
     * @param jnumeric Server numeric
     */
    public void performHandshakes(String jnumeric) {
        for (Module module : modules.values()) {
            if (module.isEnabled()) {
                String rawNick = getModuleConfig(module.getModuleName(), "nick");
                String nick = sanitizeServiceNicknameForIrcu2(module.getModuleName(), rawNick);
                String servername = getModuleConfig(module.getModuleName(), "servername");
                String description = getModuleConfig(module.getModuleName(), "description");
                String identd = getModuleConfig(module.getModuleName(), "identd");

                if (rawNick != null && !rawNick.equals(nick)) {
                    LOG.log(Level.INFO, "Adjusted service nick for ircu2 compatibility ({0}): {1} -> {2}",
                            new Object[]{module.getModuleName(), rawNick, nick});
                }

                // Most modules require nick/servername/description from config.
                boolean hasRequired = nick != null && servername != null && description != null;

                if (hasRequired) {
                    module.handshake(nick, servername, description, jnumeric, identd);
                    LOG.log(Level.INFO, "Handshake completed for module: {0}", module.getModuleName());
                } else {
                    LOG.log(Level.WARNING, "Cannot perform handshake for {0}: missing configuration", module.getModuleName());
                }
            }
        }
    }

    private int getServiceNickMaxLength() {
        int maxLen = DEFAULT_IRCU2_NICKLEN;
        try {
            if (jserv != null && jserv.getConfig() != null && jserv.getConfig().getConfigFile() != null) {
                String configured = jserv.getConfig().getConfigFile().getProperty("service_nick_maxlen");
                if (configured != null && !configured.isBlank()) {
                    maxLen = Integer.parseInt(configured.trim());
                }
            }
        } catch (Exception ignored) {
            maxLen = DEFAULT_IRCU2_NICKLEN;
        }

        if (maxLen < 1) {
            return DEFAULT_IRCU2_NICKLEN;
        }
        return Math.min(maxLen, 30);
    }

    private static boolean isIrcu2NickChar(char c) {
        return (c >= 'A' && c <= '}') || c == '_' || c == '-';
    }

    private static boolean isIrcu2NickStartChar(char c) {
        return isIrcu2NickChar(c) && c != '-' && !Character.isDigit(c);
    }

    private String sanitizeServiceNicknameForIrcu2(String moduleName, String nick) {
        if (nick == null) {
            return null;
        }

        String trimmed = nick.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        int maxLen = getServiceNickMaxLength();
        StringBuilder filtered = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (isIrcu2NickChar(c)) {
                filtered.append(c);
            }
        }

        if (filtered.length() == 0) {
            filtered.append("S");
            if (moduleName != null && !moduleName.isBlank()) {
                String fallback = moduleName.replaceAll("[^A-Za-z0-9_\\-\\[\\]\\\\`\\^{}|~]", "");
                if (!fallback.isBlank()) {
                    filtered.append(fallback);
                }
            }
        }

        while (filtered.length() > 0 && !isIrcu2NickStartChar(filtered.charAt(0))) {
            filtered.deleteCharAt(0);
        }

        if (filtered.length() == 0) {
            filtered.append('S');
        }

        if (filtered.length() > maxLen) {
            filtered.setLength(maxLen);
        }

        return filtered.toString();
    }
    
    /**
     * Route incoming IRC line to all enabled modules
     * 
     * @param line IRC protocol line
     */
    public void routeLine(String line) {
        for (Module module : modules.values()) {
            if (module.isEnabled()) {
                try {
                    module.parseLine(line);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Error in module " + module.getModuleName() + " while parsing line", e);
                }
            }
        }
    }
    
    /**
     * Shutdown all modules gracefully
     */
    public void shutdownAll() {
        LOG.log(Level.INFO, "Shutting down all modules");
        for (Module module : modules.values()) {
            try {
                module.shutdown();
                LOG.log(Level.INFO, "Module {0} shut down successfully", module.getModuleName());
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Error shutting down module: " + module.getModuleName(), e);
            }
        }
        modules.clear();
    }
    
    /**
     * Get module configuration from appropriate config file
     * 
     * @param moduleName Name of the module
     * @param property Property name
     * @return Property value or null
     */
    private String getModuleConfig(String moduleName, String property) {
        if (moduleName.equalsIgnoreCase("SpamScan")) {
            return jserv.getConfig().getSpamFile().getProperty(property);
        } else if (moduleName.equalsIgnoreCase("HostServ")) {
            return jserv.getConfig().getHostFile().getProperty(property);
        } else if (moduleName.equalsIgnoreCase("NickServ")) {
            return jserv.getConfig().getNickFile().getProperty(property);
        }
        return null;
    }
    
    /**
     * Get all registered modules
     * 
     * @return Map of module name to module instance
     */
    public Map<String, Module> getAllModules() {
        return new HashMap<>(modules);
    }
    
    /**
     * Get count of enabled modules
     * 
     * @return Number of enabled modules
     */
    public int getEnabledModuleCount() {
        return (int) modules.values().stream()
                .filter(Module::isEnabled)
                .count();
    }
}
