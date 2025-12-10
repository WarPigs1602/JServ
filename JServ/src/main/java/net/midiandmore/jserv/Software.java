/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package net.midiandmore.jserv;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 *
 * @author Andreas Pschorn
 */
public interface Software {

    String VERSION = "1.0";
    String VENDOR = "MidiAndMore.Net";
    String AUTHOR = "Andreas Pschorn (WarPigs)";
    
    static BuildInfo getBuildInfo() {
        Properties props = new Properties();
        try (InputStream in = Software.class.getClassLoader().getResourceAsStream("build.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            // Build info nicht verfügbar
        }
        return new BuildInfo(
            props.getProperty("build.number", "unknown"),
            props.getProperty("build.timestamp", "unknown"),
            props.getProperty("build.version", VERSION)
        );
    }
    
    record BuildInfo(String buildNumber, String buildTimestamp, String version) {
        public String getFullVersion() {
            return version + " (Build #" + buildNumber + ")";
        }
    }
}
