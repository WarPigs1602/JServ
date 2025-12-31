/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package net.midiandmore.jserv;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


public final class JServ implements Software {
    
    private static Logger fileLogger = null;
    private volatile boolean running = true;
    private static boolean daemonMode = false;

    static {
        try {
            fileLogger = Logger.getLogger("JServFileLogger");
            FileHandler fh = new FileHandler("jserv.log", true);
            fh.setFormatter(new SimpleFormatter());
            fileLogger.addHandler(fh);
            fileLogger.setUseParentHandlers(false);
        } catch (IOException e) {
            Logger.getLogger(JServ.class.getName()).log(Level.SEVERE, "Could not initialize file logger", e);
        }
    }

    /**
     * @return the waitThread
     */
    public WaitThread getWaitThread() {
        return waitThread;
    }

    /**
     * @param waitThread the waitThread to set
     */
    public void setWaitThread(WaitThread waitThread) {
        this.waitThread = waitThread;
    }

    /**
     * @return the socketThread
     */
    public SocketThread getSocketThread() {
        return socketThread;
    }

    /**
     * @param socketThread the socketThread to set
     */
    public void setSocketThread(SocketThread socketThread) {
        this.socketThread = socketThread;
    }

    /**
     * @return the config
     */
    public Config getConfig() {
        return config;
    }

    /**
     * @param config the config to set
     */
    public void setConfig(Config config) {
        this.config = config;
    }

    private Config config;
    private SocketThread socketThread;
    private WaitThread waitThread;
    private Database db;
    private Homoglyphs homoglyphs;
    
    // Lock for user registration to prevent race conditions
    private final Object userRegistrationLock = new Object();
    
    /**
     * Get the lock object for user registration synchronization
     * @return The user registration lock
     */
    public Object getUserRegistrationLock() {
        return userRegistrationLock;
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // Parse command line arguments
        boolean shouldDaemonize = false;
        boolean isChildProcess = false;
        
        for (String arg : args) {
            if (arg.equals("--daemon") || arg.equals("-d")) {
                shouldDaemonize = true;
                daemonMode = true;
            } else if (arg.equals("--daemon-child")) {
                isChildProcess = true;
                daemonMode = true;
            } else if (arg.equals("--help") || arg.equals("-h")) {
                printHelp();
                return;
            }
        }
        
        // If daemon mode is requested and this is the parent process, spawn child
        if (shouldDaemonize && !isChildProcess) {
            try {
                daemonize();
                return; // Parent process exits here
            } catch (Exception e) {
                System.err.println("[ERROR] Failed to daemonize: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }
        
        if (!daemonMode) {
            System.out.println("[STARTUP] Starting JServ...");
        }
        
        try {
            new JServ(args);
        } catch (Exception e) {
            if (fileLogger != null) {
                fileLogger.log(Level.SEVERE, "Fatal error in JServ", e);
            }
            if (!daemonMode) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    protected JServ(String[] args) {
        init(args);
    }

    protected void init(String[] args) {
        // Install shutdown hook first
        installShutdownHook();
        
        Software.BuildInfo buildInfo = Software.getBuildInfo();
        logInfo("JServ %s", buildInfo.getFullVersion());
        logInfo("By %s", AUTHOR);
        logInfo("");
        
        setConfig(new Config(this, "config-jserv.json"));
        logDebug("Config loaded successfully");
        
        setHomoglyphs(new Homoglyphs(this));
        logDebug("Homoglyphs initialized");
        
        setDb(new Database(this));
        logDebug("Database connection established");
        
        setWaitThread(new WaitThread(this));
        logInfo("JServ started successfully");
        logInfo("Press CTRL+C to stop the application gracefully");
        
        // Main loop - keep running until shutdown signal
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logInfo("Main loop interrupted");
                break;
            }
        }
        
        logInfo("Main loop exited - shutting down");
    }

    /**
     * @return the db
     */
    public Database getDb() {
        return db;
    }

    /**
     * @param db the db to set
     */
    public void setDb(Database db) {
        this.db = db;
    }

    /**
     * @return the homoglyphs
     */
    public Homoglyphs getHomoglyphs() {
        return homoglyphs;
    }

    /**
     * @param homoglyphs the homoglyphs to set
     */
    public void setHomoglyphs(Homoglyphs homoglyphs) {
        this.homoglyphs = homoglyphs;
    }
    
    /**
     * Logs an info message.
     */
    private void logInfo(String msg, Object... args) {
        if (fileLogger != null) {
            String formattedMsg = String.format(msg, args);
            fileLogger.log(Level.INFO, formattedMsg);
            if (!daemonMode) {
                System.out.println("[INFO] " + formattedMsg);
            }
        }
    }

    /**
     * Logs an error message.
     */
    private void logError(String msg, Throwable t) {
        if (fileLogger != null) {
            fileLogger.log(Level.SEVERE, msg, t);
            if (!daemonMode) {
                System.err.println("[ERROR] " + msg);
                if (t != null) {
                    t.printStackTrace(System.err);
                }
            }
        }
    }

    /**
     * Logs a debug message.
     */
    private void logDebug(String msg, Object... args) {
        if (fileLogger != null) {
            String formattedMsg = String.format(msg, args);
            fileLogger.log(Level.FINE, formattedMsg);
            if (!daemonMode) {
                System.out.println("[DEBUG] " + formattedMsg);
            }
        }
    }
    
    /**
     * Installs the shutdown hook to handle CTRL+C and terminal closure
     */
    private void installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!daemonMode) {
                System.out.println("\n==============================================");
                System.out.println("[SHUTDOWN] SHUTDOWN SIGNAL RECEIVED");
                System.out.println("==============================================");
                System.out.flush();
            }
            shutdown();
        }, "ShutdownHook"));
        logDebug("Shutdown hook installed");
    }
    
    /**
     * Gracefully shuts down the JServ service.
     */
    private void shutdown() {
        if (!daemonMode) {
            System.out.println("[SHUTDOWN] Initiating graceful shutdown...");
            System.out.flush();
        }
        
        if (fileLogger != null) {
            fileLogger.log(Level.INFO, "SHUTDOWN SIGNAL RECEIVED - Initiating graceful shutdown");
        }
        
        running = false;
        
        try {
            if (!daemonMode) {
                System.out.println("[SHUTDOWN] Waiting for current operations to complete...");
                System.out.flush();
            }
            Thread.sleep(2000); // Give some time for threads to finish
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!daemonMode) {
                System.err.println("[SHUTDOWN ERROR] Shutdown interrupted: " + e.getMessage());
            }
            if (fileLogger != null) {
                fileLogger.log(Level.SEVERE, "Shutdown interrupted", e);
            }
        }
        
        // Shutdown all modules
        if (socketThread != null && socketThread.getModuleManager() != null) {
            logInfo("Shutting down all modules...");
            socketThread.getModuleManager().shutdownAll();
            
            // Give modules time to send QUIT commands
            try {
                if (!daemonMode) {
                    System.out.println("[SHUTDOWN] Waiting for services to log out...");
                    System.out.flush();
                }
                Thread.sleep(1000); // Give time for QUIT commands to be sent
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Close database connection
        if (db != null) {
            logInfo("Closing database connection pool...");
            db.shutdown();
        }
        
        // Stop threads
        if (socketThread != null) {
            logInfo("Stopping socket thread...");
            socketThread.setRuns(false);
        }
        
        if (waitThread != null) {
            logInfo("Stopping wait thread...");
            // Add waitThread.stop() if needed
        }
        
        if (!daemonMode) {
            System.out.println("==============================================");
            System.out.println("[SHUTDOWN] SHUTDOWN COMPLETE - Application terminated");
            System.out.println("==============================================");
            System.out.flush();
        }
        
        if (fileLogger != null) {
            fileLogger.log(Level.INFO, "SHUTDOWN COMPLETE - Application terminated");
            // Flush all handlers
            for (var handler : fileLogger.getHandlers()) {
                handler.flush();
                handler.close();
            }
        }
    }
    
    /**
     * Daemonizes the process by spawning a detached child process
     */
    private static void daemonize() throws Exception {
        // Get the current JAR file path
        String jarPath = JServ.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
                .getPath();
        
        // Build command to restart in background
        ProcessBuilder pb = new ProcessBuilder(
            "java",
            "-jar",
            jarPath,
            "--daemon-child"
        );
        
        // Redirect output to log files
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new java.io.File("jserv.out")));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(new java.io.File("jserv.err")));
        
        // Start the detached process
        Process process = pb.start();
        
        // Write PID file
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter("jserv.pid"))) {
            writer.println(process.pid());
        }
        
        System.out.println("[DAEMON] Started in background with PID: " + process.pid());
        System.out.println("[DAEMON] Output logged to: jserv.out and jserv.err");
        System.out.println("[DAEMON] Process ID saved to: jserv.pid");
        System.out.println("[DAEMON] Use 'kill $(cat jserv.pid)' to stop");
    }
    
    /**
     * Prints help text
     */
    private static void printHelp() {
        System.out.println("JServ " + VERSION + " - IRC Services");
        System.out.println("By " + AUTHOR);
        System.out.println();
        System.out.println("Usage: java -jar jserv.jar [OPTIONS]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -d, --daemon    Run in daemon mode (detached background process)");
        System.out.println("  -h, --help      Show this help message");
        System.out.println();
        System.out.println("Daemon mode:");
        System.out.println("  When started with --daemon, the process detaches and runs in the background.");
        System.out.println("  PID is saved to 'jserv.pid' for easy management.");
        System.out.println("  Stop: kill $(cat jserv.pid)");
        System.out.println();
        System.out.println("Configuration files:");
        System.out.println("  config-jserv.json    - Main configuration");
        System.out.println();
        System.out.println("Press CTRL+C to stop the daemon gracefully.");
    }
    
    private static final Logger LOG = Logger.getLogger(JServ.class.getName());
}
