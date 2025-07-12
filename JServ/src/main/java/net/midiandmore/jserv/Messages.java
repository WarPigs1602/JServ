/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package net.midiandmore.jserv;

import java.util.ResourceBundle;

public class Messages {
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("net.midiandmore.jserv.messages");

    public static String get(String key, Object... args) {
        String pattern = BUNDLE.getString(key);
        return String.format(pattern, args);
    }
}
