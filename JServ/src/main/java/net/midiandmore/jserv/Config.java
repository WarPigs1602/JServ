/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import jakarta.json.Json;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Properties;
import java.util.logging.Logger;


public final class Config {

    /**
     * @return the saslFile
     */
    public Properties getSaslFile() {
        return saslFile;
    }

    public Properties getOperFile() {
        return operFile;
    }

    public Properties getTrustcheckFile() {
        return trustcheckFile;
    }

    /**
     * @param saslFile the saslFile to set
     */
    public void setSaslFile(Properties saslFile) {
        this.saslFile = saslFile;
    }

    public void setOperFile(Properties operFile) {
        this.operFile = operFile;
    }

    public void setTrustcheckFile(Properties trustcheckFile) {
        this.trustcheckFile = trustcheckFile;
    }

    /**
     * @return the modulesFile
     */
    public Properties getModulesFile() {
        return modulesFile;
    }

    /**
     * @param modulesFile the modulesFile to set
     */
    public void setModulesFile(Properties modulesFile) {
        this.modulesFile = modulesFile;
    }

    /**
     * @return the spamFile
     */
    public Properties getSpamFile() {
        return spamFile;
    }

    /**
     * @param spamFile the spamFile to set
     */
    public void setSpamFile(Properties spamFile) {
        this.spamFile = spamFile;
    }

    /**
     * @return the hostFile
     */
    public Properties getHostFile() {
        return hostFile;
    }

    /**
     * @param hostFile the hostFile to set
     */
    public void setHostFile(Properties hostFile) {
        this.hostFile = hostFile;
    }

    /**
     * @return the nickFile
     */
    public Properties getNickFile() {
        return nickFile;
    }

    /**
     * @param nickFile the nickFile to set
     */
    public void setNickFile(Properties nickFile) {
        this.nickFile = nickFile;
    }

    private JServ mi;
    private Properties configFile;
    private Properties badwordFile; 
    private Properties modulesFile;
    private Properties spamFile;     
    private Properties hostFile;  
    private Properties nickFile;  
    private Properties saslFile;
    private Properties chanServFile;
    private Properties authFile;
    private Properties operFile;
    private Properties trustcheckFile;
    private Properties statsServFile;
         
    /**
     * Initiales the class
     *
     * @param mi The JServ class
     * @param configFile
     */
    protected Config(JServ mi, String configFile) {
        System.out.println("Loading config...");                
        setMi(mi);
        loadConfig();
        System.out.println("Config loaded...");       
    }

    protected void createFileIfNotExists(String file) {
        File f = new File(file);
        if (!f.exists()) {
            try (FileWriter fw = new FileWriter(f)) {
                fw.write("[]");
            } catch (IOException ex) {
                LOG.severe("Fehler beim Erstellen der Datei " + file + ": " + ex.getMessage());
            }
        }
    }

    protected void saveDataToJSON(String file, Properties ar, String name, String value) {
        createFileIfNotExists(file);
        try (FileWriter fw = new FileWriter(file);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println("[");
            int i = 0;
            int size = ar.size();
            for (String key : ar.stringPropertyNames()) {
                var obj = Json.createObjectBuilder()
                    .add(name, key)
                    .add(value, ar.getProperty(key))
                    .build();
                pw.print(obj.toString());
                i++;
                pw.println(i < size ? "," : "");
            }
            pw.println("]");
        } catch (IOException ex) {
            LOG.severe("Fehler beim Speichern in " + file + ": " + ex.getMessage());
        }
    }

    /**
     * Loads the config files in the Properties
     */
    private void loadConfig() {
        setConfigFile(loadDataFromJSONasProperties("config.json", "name", "value"));
        setModulesFile(loadDataFromJSONasProperties("config-modules.json", "name", "value")); 
        setHostFile(loadDataFromJSONasProperties("config-hostserv.json", "name", "value"));   
        setSpamFile(loadDataFromJSONasProperties("config-spamscan.json", "name", "value"));   
        setNickFile(loadDataFromJSONasProperties("config-nickserv.json", "name", "value"));   
        setSaslFile(loadDataFromJSONasProperties("config-saslserv.json", "name", "value"));
        setChanServFile(loadDataFromJSONasProperties("config-chanserv.json", "name", "value"));
        setAuthFile(loadDataFromJSONasProperties("config-authserv.json", "name", "value"));
        setOperFile(loadDataFromJSONasProperties("config-operserv.json", "name", "value"));
        setTrustcheckFile(loadDataFromJSONasProperties("config-trustcheck.json", "name", "value"));
        setStatsServFile(loadDataFromJSONasProperties("config-statsserv.json", "name", "value"));
        setBadwordFile(loadDataFromJSONasProperties("badwords-spamscan.json", "name", "value")); 
    }

    /**
     * Loads the config data from a JSON file
     *
     * @param file The file
     * @param obj First element
     * @param obj2 Second element
     * @return The properties
     */
    protected Properties loadDataFromJSONasProperties(String file, String obj, String obj2) {
        createFileIfNotExists(file);
        Properties ar = new Properties();
        try (InputStream is = new FileInputStream(file);
             var rdr = Json.createReader(is)) {
            var results = rdr.readArray();
            for (var jsonValue : results) {
                var jobj = jsonValue.asJsonObject();
                ar.put(jobj.getString(obj), jobj.getString(obj2));
            }
        } catch (IOException ex) {
            LOG.severe("Fehler beim Laden von " + file + ": " + ex.getMessage());
        }
        return ar;
    }

    /**
     * @return the mi
     */
    public JServ getMi() {
        return mi;
    }

    /**
     * @param mi the mi to set
     */
    public void setMi(JServ mi) {
        this.mi = mi;
    }

    /**
     * @return the configFile
     */
    public Properties getConfigFile() {
        return configFile;
    }

    /**
     * @param configFile the configFile to set
     */
    public void setConfigFile(Properties configFile) {
        this.configFile = configFile;
    }

    /**
     * @return the badwordFile
     */
    public Properties getBadwordFile() {
        return badwordFile;
    }

    /**
     * @param badwordFile the badwordFile to set
     */
    public void setBadwordFile(Properties badwordFile) {
        this.badwordFile = badwordFile;
    }

    /**
     * @return the chanServFile
     */
    public Properties getChanServFile() {
        return chanServFile;
    }

    /**
     * @param chanServFile the chanServFile to set
     */
    public void setChanServFile(Properties chanServFile) {
        this.chanServFile = chanServFile;
    }

    /**
     * @return the authFile
     */
    public Properties getAuthFile() {
        return authFile;
    }

    /**
     * @param authFile the authFile to set
     */
    public void setAuthFile(Properties authFile) {
        this.authFile = authFile;
    }

    /**
     * @return the statsServFile
     */
    public Properties getStatsServFile() {
        return statsServFile;
    }

    /**
     * @param statsServFile the statsServFile to set
     */
    public void setStatsServFile(Properties statsServFile) {
        this.statsServFile = statsServFile;
    }

    private static final Logger LOG = Logger.getLogger(Config.class.getName());
}
