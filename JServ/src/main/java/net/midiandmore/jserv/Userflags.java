/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package net.midiandmore.jserv;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author windo
 */
public class Userflags {

    //chanlev
    protected static int QCUFLAG_OWNER = 0x8000;
    /* +n */
    protected static int QCUFLAG_MASTER = 0x4000;
    /* +m */
    protected static int QCUFLAG_OP = 0x2000;
    /* +o */
    protected static int QCUFLAG_VOICE = 0x1000;
    /* +v */
    protected static int QCUFLAG_AUTOOP = 0x0001;
    /* +a */
    protected static int QCUFLAG_BANNED = 0x0002;
    /* +b */
    protected static int QCUFLAG_DENY = 0x0004;
    /* +d */
    protected static int QCUFLAG_AUTOVOICE = 0x0008;
    /* +g */
    protected static int QCUFLAG_QUIET = 0x0010;
    /* +q */
    protected static int QCUFLAG_NOINFO = 0x0020;
    /* +s */
    protected static int QCUFLAG_TOPIC = 0x0040;
    /* +t */
    protected static int QCUFLAG_HIDEWELCOME = 0x0080;
    /* +w */
    protected static int QCUFLAG_PROTECT = 0x0100;
    /* +p */
    protected static int QCUFLAG_INFO = 0x0200;
    /* +i */
    protected static int QCUFLAG_KNOWN = 0x0400;
    /* +k */
    protected static int QCUFLAG_AUTOINVITE = 0x0800;
    /* +j */
    
    public enum Flag {
        INACTIVE('I', 0x0001),
        GLINE('g', 0x0002),
        NOTICE('n', 0x0004),
        STAFF('q', 0x0008),
        SUSPENDED('z', 0x0010),
        OPER('o', 0x0020),
        DEV('d', 0x0040),
        PROTECT('p', 0x0080),
        HELPER('h', 0x0100),
        ADMIN('a', 0x0200),
        INFO('i', 0x0400),
        DELAYEDGLINE('G', 0x0800),
        NOAUTHLIMIT('L', 0x1000),
        ACHIEVEMENTS('c', 0x2000),
        CLEANUPEXEMPT('D', 0x4000),
        TRUST('T', 0x8000);

        public final char code;
        public final int value;

        Flag(char code, int value) {
            this.code = code;
            this.value = value;
        }
    }

    private static final Map<Character, Flag> FLAG_MAP = new HashMap<>();
    static {
        for (Flag flag : Flag.values()) {
            FLAG_MAP.put(flag.code, flag);
        }
    }

    public static Flag fromChar(char c) {
        return FLAG_MAP.get(c);
    }

    public static int setFlag(int flags, Flag flag) {
        return flags | flag.value;
    }

    public static int clearFlag(int flags, Flag flag) {
        return flags & ~flag.value;
    }

    public static boolean hasFlag(int flags, Flag flag) {
        return (flags & flag.value) != 0;
    }
}
