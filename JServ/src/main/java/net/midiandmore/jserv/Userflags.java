/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package net.midiandmore.jserv;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author windo
 */
public class Userflags {

    public enum QCUFlag {
        OWNER('n', 0x8000),
        MASTER('m', 0x4000),
        OP('o', 0x2000),
        VOICE('v', 0x1000),
        AUTOOP('a', 0x0001),
        BANNED('b', 0x0002),
        DENY('d', 0x0004),
        AUTOVOICE('g', 0x0008),
        QUIET('q', 0x0010),
        NOINFO('s', 0x0020),
        TOPIC('t', 0x0040),
        HIDEWELCOME('w', 0x0080),
        PROTECT('p', 0x0100),
        INFO('i', 0x0200),
        KNOWN('k', 0x0400),
        AUTOINVITE('j', 0x0800);

        public final char code;
        public final int value;

        QCUFlag(char code, int value) {
            this.code = code;
            this.value = value;
        }
    }

    private static final Map<Character, QCUFlag> QCUFLAG_MAP;
    static {
        Map<Character, QCUFlag> map = new HashMap<>();
        for (QCUFlag flag : QCUFlag.values()) {
            map.put(flag.code, flag);
        }
        QCUFLAG_MAP = Collections.unmodifiableMap(map);
    }

    public static QCUFlag qcuFlagFromChar(char c) {
        return QCUFLAG_MAP.get(c);
    }

    public static int setQCUFlag(int flags, QCUFlag flag) {
        return flags | flag.value;
    }

    public static int clearQCUFlag(int flags, QCUFlag flag) {
        return flags & ~flag.value;
    }

    public static boolean hasQCUFlag(int flags, QCUFlag flag) {
        return (flags & flag.value) != 0;
    }

    
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
