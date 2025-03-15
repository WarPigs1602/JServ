/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package net.midiandmore.jserv;

/**
 *
 * @author windo
 */
public interface Userflags {

    int QUFLAG_INACTIVE = 0x0001;
    /* +I */
    int QUFLAG_GLINE = 0x0002;
    /* +g */
    int QUFLAG_NOTICE = 0x0004;
    /* +n */
    int QUFLAG_STAFF = 0x0008;
    /* +q */
    int QUFLAG_SUSPENDED = 0x0010;
    /* +z */
    int QUFLAG_OPER = 0x0020;
    /* +o */
    int QUFLAG_DEV = 0x0040;
    /* +d */
    int QUFLAG_PROTECT = 0x0080;
    /* +p */
    int QUFLAG_HELPER = 0x0100;
    /* +h */
    int QUFLAG_ADMIN = 0x0200;
    /* +a */
    int QUFLAG_INFO = 0x0400;
    /* +i */
    int QUFLAG_DELAYEDGLINE = 0x0800;
    /* +G */
    int QUFLAG_NOAUTHLIMIT = 0x1000;
    /* +L */
    int QUFLAG_ACHIEVEMENTS = 0x2000;
    /* +c */
    int QUFLAG_CLEANUPEXEMPT = 0x4000;
    /* +D */
    int QUFLAG_TRUST = 0x8000;
    /* +T */
    int QUFLAG_ALL = 0xffff;

    //chanlev
    int QCUFLAG_OWNER = 0x8000;
    /* +n */
    int QCUFLAG_MASTER = 0x4000;
    /* +m */
    int QCUFLAG_OP = 0x2000;
    /* +o */
    int QCUFLAG_VOICE = 0x1000;
    /* +v */
    int QCUFLAG_AUTOOP = 0x0001;
    /* +a */
    int QCUFLAG_BANNED = 0x0002;
    /* +b */
    int QCUFLAG_DENY = 0x0004;
    /* +d */
    int QCUFLAG_AUTOVOICE = 0x0008;
    /* +g */
    int QCUFLAG_QUIET = 0x0010;
    /* +q */
    int QCUFLAG_NOINFO = 0x0020;
    /* +s */
    int QCUFLAG_TOPIC = 0x0040;
    /* +t */
    int QCUFLAG_HIDEWELCOME = 0x0080;
    /* +w */
    int QCUFLAG_PROTECT = 0x0100;
    /* +p */
    int QCUFLAG_INFO = 0x0200;
    /* +i */
    int QCUFLAG_KNOWN = 0x0400;
    /* +k */
    int QCUFLAG_AUTOINVITE = 0x0800;
    /* +j */

    char[][] userFlags = {
        {'a', QUFLAG_ADMIN},
        {'c', QUFLAG_ACHIEVEMENTS},
        {'d', QUFLAG_DEV},
        {'D', QUFLAG_CLEANUPEXEMPT},
        {'g', QUFLAG_GLINE},
        {'G', QUFLAG_DELAYEDGLINE},
        {'h', QUFLAG_HELPER},
        {'i', QUFLAG_INFO},
        {'L', QUFLAG_NOAUTHLIMIT},
        {'n', QUFLAG_NOTICE},
        {'o', QUFLAG_OPER},
        {'p', QUFLAG_PROTECT},
        {'q', QUFLAG_STAFF},
        //  { 's',  QUFLAG_NOINFO },
        {'I', QUFLAG_INACTIVE},
        {'T', QUFLAG_TRUST},
        {'z', QUFLAG_SUSPENDED}};
}
