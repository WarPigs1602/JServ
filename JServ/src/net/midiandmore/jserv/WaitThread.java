/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import java.util.logging.Level;
import java.util.logging.Logger;


public final class WaitThread implements Runnable {

    public WaitThread(JServ mi) {
        setMi(mi);
        (thread = new Thread(this)).start();
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

    private final Thread thread;
    private JServ mi;

    @Override
    public void run() {
        while (true) {
            try {
                if (getMi().getSocketThread() == null) {
                    getMi().setSocketThread(new SocketThread(getMi()));
                } else if (getMi().getSocketThread().getSocket() == null) {
                    getMi().getSocketThread().setRuns(false);
                    getMi().setSocketThread(null);
                } else if (getMi().getSocketThread().isRuns()
                        && getMi().getSocketThread().getSocket().isClosed()) {
                    getMi().getSocketThread().setRuns(false);
                    getMi().setSocketThread(null);
                } else {
                    if (getMi().getSocketThread().getUsers() != null) {
                        var set = getMi().getSocketThread().getUsers().keySet();
                        for (var nick : set) {
                            var flood = getMi().getSocketThread().getUsers().get(nick).getFlood();
                            if (flood != 0) {
                                getMi().getSocketThread().getUsers().get(nick).setFlood(flood - 1);
                            }
                        }
                    } else {
                        getMi().getSocketThread().setRuns(false);
                        getMi().setSocketThread(null);
                        getMi().setSocketThread(new SocketThread(getMi()));
                    }
                }
                thread.sleep(2000L);
            } catch (InterruptedException ex) {
                Logger.getLogger(WaitThread.class.getName()).log(Level.SEVERE, null, ex);
                System.exit(1);
            }
        }
    }
    private static final Logger LOG = Logger.getLogger(WaitThread.class.getName());

}
