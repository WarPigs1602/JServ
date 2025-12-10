/*
 * Module configuration class
 * Loads module definitions from JSON configuration file
 */
package net.midiandmore.jserv;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles loading and managing module configurations
 * 
 * @author Andreas Pschorn
 */
public class ModuleConfig {
    
    private static final Logger LOG = Logger.getLogger(ModuleConfig.class.getName());
    
    private List<ModuleDefinition> modules = new ArrayList<>();
    
    /**
     * Load module configuration from JSON file
     * 
     * @param configFile Path to the JSON configuration file
     * @return ModuleConfig instance
     */
    public static ModuleConfig load(String configFile) {
        ModuleConfig config = new ModuleConfig();
        
        try (FileInputStream fis = new FileInputStream(configFile);
             JsonReader reader = Json.createReader(fis)) {
            
            JsonObject root = reader.readObject();
            JsonArray modulesArray = root.getJsonArray("modules");
            
            if (modulesArray != null) {
                for (int i = 0; i < modulesArray.size(); i++) {
                    JsonObject moduleObj = modulesArray.getJsonObject(i);
                    
                    ModuleDefinition def = new ModuleDefinition();
                    def.setName(moduleObj.getString("name"));
                    def.setEnabled(moduleObj.getBoolean("enabled", true));
                    def.setClassName(moduleObj.getString("className"));
                    def.setNumericSuffix(moduleObj.getString("numericSuffix"));
                    def.setConfigFile(moduleObj.getString("configFile", null));
                    
                    config.modules.add(def);
                }
                LOG.log(Level.INFO, "Loaded {0} module definitions from {1}", 
                        new Object[]{config.modules.size(), configFile});
            }
            
        } catch (IOException e) {
            LOG.log(Level.SEVERE, () -> "Failed to load module configuration from " + configFile);
            LOG.log(Level.SEVERE, "Exception details", e);
        }
        
        return config;
    }
    
    /**
     * Get all module definitions
     * 
     * @return List of module definitions
     */
    public List<ModuleDefinition> getModules() {
        return new ArrayList<>(modules);
    }
    
    /**
     * Get module definition by name
     * 
     * @param name Module name
     * @return Module definition or null if not found
     */
    public ModuleDefinition getModuleByName(String name) {
        return modules.stream()
                .filter(m -> m.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Represents a single module definition
     */
    public static class ModuleDefinition {
        private String name;
        private boolean enabled;
        private String className;
        private String numericSuffix;
        private String configFile;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public String getClassName() {
            return className;
        }
        
        public void setClassName(String className) {
            this.className = className;
        }
        
        public String getNumericSuffix() {
            return numericSuffix;
        }
        
        public void setNumericSuffix(String numericSuffix) {
            this.numericSuffix = numericSuffix;
        }
        
        public String getConfigFile() {
            return configFile;
        }
        
        public void setConfigFile(String configFile) {
            this.configFile = configFile;
        }
    }
}
