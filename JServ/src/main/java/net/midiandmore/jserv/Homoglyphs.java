/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


public final class Homoglyphs {

    private JServ mi;
    private Set<Character> homoglyphs;

    private static final Logger LOG = Logger.getLogger(Homoglyphs.class.getName());

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

    public Homoglyphs(JServ mi) {
        setMi(mi);
        setHomoglyphs(new HashSet<>());
        parseHomoglyphs();
    }

    private void parseHomoglyphs() {
        LOG.info("Loading Homoglyphs...");
        var f = new File("chars.txt");
        if (f.exists()) {
            try (var fis = new FileInputStream(f);
                 var isr = new InputStreamReader(fis);
                 var br = new BufferedReader(isr)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    for (char elem : line.toCharArray()) {
                        homoglyphs.add(elem);
                    }
                }
                LOG.info(String.format("Loaded %d Homoglyphs...", homoglyphs.size()));
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Fehler beim Laden der Homoglyphen", e);
                throw new RuntimeException("Fehler beim Laden der Homoglyphen", e);
            }
        } else {
            LOG.severe("File chars.txt not found...");
            throw new IllegalStateException("File chars.txt not found...");
        }
    }

    protected boolean scanForHomoglyphs(String text) {
        var chars = text.trim().replaceAll("\\s", "").toCharArray();
        for (char c : chars) {
            if (homoglyphs.contains(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the homoglyphs
     */
    public Set<Character> getHomoglyphs() {
        return homoglyphs;
    }

    /**
     * @param homoglyphs the homoglyphs to set
     */
    public void setHomoglyphs(Set<Character> homoglyphs) {
        this.homoglyphs = homoglyphs;
    }
}
