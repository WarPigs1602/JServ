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
    
    char[][] userFlags = {
  { 'a',  QUFLAG_ADMIN },
  { 'c',  QUFLAG_ACHIEVEMENTS },
  { 'd',  QUFLAG_DEV },
  { 'D',  QUFLAG_CLEANUPEXEMPT },
  { 'g',  QUFLAG_GLINE },
  { 'G',  QUFLAG_DELAYEDGLINE },
  { 'h',  QUFLAG_HELPER },
  { 'i',  QUFLAG_INFO },
  { 'L',  QUFLAG_NOAUTHLIMIT },
  { 'n',  QUFLAG_NOTICE },
  { 'o',  QUFLAG_OPER },
  { 'p',  QUFLAG_PROTECT },
  { 'q',  QUFLAG_STAFF },
//  { 's',  QUFLAG_NOINFO },
  { 'I',  QUFLAG_INACTIVE },
  { 'T',  QUFLAG_TRUST },
  { 'z',  QUFLAG_SUSPENDED } };    
}
