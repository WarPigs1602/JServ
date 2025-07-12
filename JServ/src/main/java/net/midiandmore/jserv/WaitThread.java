package net.midiandmore.jserv;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WaitThread implements Runnable {

    public WaitThread(JServ mi) {
        setMi(mi);
        (thread = new Thread(this)).start();
    }

    public JServ getMi() {
        return mi;
    }

    public void setMi(JServ mi) {
        this.mi = mi;
    }

    private final Thread thread;
    private JServ mi;
    private static final Logger LOG = Logger.getLogger(WaitThread.class.getName());

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                SocketThread socketThread = getMi().getSocketThread();
                if (socketThread == null) {
                    getMi().setSocketThread(new SocketThread(getMi()));
                } else if (socketThread.getSocket() == null) {
                    socketThread.setRuns(false);
                    getMi().setSocketThread(null);
                } else if (socketThread.isRuns() && socketThread.getSocket().isClosed()) {
                    socketThread.setRuns(false);
                    getMi().setSocketThread(null);
                } else {
                    Map<String, Users> users = socketThread.getUsers();
                    if (users != null) {
                        for (String nick : users.keySet()) {
                            Users user = users.get(nick);
                            int flood = user.getFlood();
                            if (flood != 0) {
                                user.setFlood(flood - 1);
                            }
                        }
                    } else {
                        socketThread.setRuns(false);
                        getMi().setSocketThread(null);
                        getMi().setSocketThread(new SocketThread(getMi()));
                    }
                }
                Thread.sleep(3000L);
            } catch (InterruptedException ex) {
                LOG.log(Level.SEVERE, "Thread interrupted", ex);
                Thread.currentThread().interrupt();
            }
        }
    }
}
