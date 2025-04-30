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
import java.util.ArrayList;
import java.util.logging.Logger;


public final class Homoglyphs {

    private JServ mi;
    private ArrayList<Character> homoglyphs;

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
        setHomoglyphs(new ArrayList<>());
        parseHomoglyphs();
    }

    private void parseHomoglyphs() {
        System.out.printf("Loading Homoglyphs...\n");
        var f = new File("chars.txt");
        if (f.exists()) {
            try {
                var fis = new FileInputStream(f);
                var isr = new InputStreamReader(fis);
                var br = new BufferedReader(isr);
                var line = "";
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    var arr = line.toCharArray();
                    for (var elem : arr) {
                        getHomoglyphs().add(elem);
                    }
                }
                System.out.printf("Loaded %d Homoglyphs...\n", getHomoglyphs().size());
            } catch (IOException e) {

            }
        } else {
            System.err.printf("File chars.txt not found...\n");
            System.exit(1);
        }
    }

    protected boolean scanForHomoglyphs(String text) {
        var chars = text.trim().replace("\\s", "").toCharArray();
        var cl = chars.length;
        for (var i = 0; i < cl; i++) {
            if (getHomoglyphs().contains(chars[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the homoglyphs
     */
    public ArrayList<Character> getHomoglyphs() {
        return homoglyphs;
    }

    /**
     * @param homoglyphs the homoglyphs to set
     */
    public void setHomoglyphs(ArrayList<Character> homoglyphs) {
        this.homoglyphs = homoglyphs;
    }
    private static final Logger LOG = Logger.getLogger(Homoglyphs.class.getName());
}
