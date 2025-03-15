/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author The database class
 */
public class Database {

    private JServ mi;
    private boolean connected;
    private Connection conn;

    protected Database(JServ mi) {
        setMi(mi);
        setConnected(false);
        connect();
    }

    /**
     * Fetching userdata
     *
     * @param key The key
     * @param nick The nick
     * @return The data
     */
    public String getData(String key, String nick) {
        String flag = null;
        try (var statement = getConn().prepareStatement("SELECT " + key + " FROM chanserv.users WHERE username=?")) {
            statement.setString(1, nick);
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    flag = resultset.getString(key);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return flag;
    }

    /**
     * Fetching userdata
     *
     * @param key The key
     * @param nick The nick
     * @return The data
     */
    public void updateData(String key, String nick, String data) {
        try (var statement = getConn().prepareStatement("UPDATE chanserv.users SET " + key + " = ? WHERE username = ?")) {
            statement.setString(1, data);
            statement.setString(2, nick);
            var rows = statement.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Fetching userdata
     *
     * @param key The key
     * @param nick The nick
     * @return The data
     */
    public void updateData(String key, String nick, long data) {
        try (var statement = getConn().prepareStatement("UPDATE chanserv.users SET " + key + " = ? WHERE username = ?")) {
            statement.setLong(1, data);
            statement.setString(2, nick);
            var rows = statement.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Checks that the user is registered
     *
     * @param nick The user
     * @return If true or false
     */
    public boolean isRegistered(String nick) {
        return getData("username", nick) != null;
    }

    public int getIndex() {
        int index = 0;
        try (var statement = getConn().prepareStatement("SELECT id FROM chanserv.users ORDER BY id DESC;")) {
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    index = resultset.getInt(1);
                    break;
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return index;
    }

    public int getNumeric() {
        int index = 0;
        try (var statement = getConn().prepareStatement("SELECT numeric FROM chanserv.authhistory ORDER BY numeric DESC;")) {
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    index = resultset.getInt(1);
                    break;
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return index;
    }

    public int getIndex(String nick) {
        int index = 0;
        try (var statement = getConn().prepareStatement("SELECT id FROM chanserv.users WHERE username=?;")) {
            statement.setString(1, nick);
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    index = resultset.getInt(1);
                    break;
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return index;
    }

    /**
     * Checks that the user is registered
     *
     * @param nick The user
     * @return If true or false
     */
    public boolean isMail(String email) {
        var flag = false;
        try (var statement = getConn().prepareStatement("SELECT * FROM chanserv.users WHERE email=?")) {
            statement.setString(1, email);
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    flag = true;
                    break;
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return flag;
    }

    public void addUser(String nick, String email) {
        try {
            var index = getIndex() + 1;
            try (var statement = getConn().prepareStatement("INSERT INTO chanserv.users (username, created, lastauth, lastemailchng, flags, password, email, "
                    + "lastemail, lastpasschng, id, language, suspendby, suspendexp, suspendtime, lockuntil, lastuserhost, suspendreason, comment, info)"
                    + " VALUES (?,?,?,?,?,?,?,'',?,?,0,0,0,0,0,'','','','');")) {
                statement.setString(1, nick);
                statement.setLong(2, getCurrentTime());
                statement.setLong(3, getCurrentTime());
                statement.setLong(4, getCurrentTime());
                statement.setInt(5, 4);
                statement.setString(6, createRandomId());
                statement.setString(7, email);
                statement.setLong(8, getCurrentTime());
                statement.setInt(9, index);
                statement.executeUpdate();
            }
            try (var statement = getConn().prepareStatement("INSERT INTO chanserv.email (userid, emailtype, prevemail) VALUES (?,?,?);")) {
                statement.setInt(1, index);
                statement.setInt(2, 1);
                statement.setString(3, email);
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public void addAuthHistory(String auth, String nick, String email, String username, String host) {
        try {
            var index = getNumeric() + 1;
            try (var statement = getConn().prepareStatement("INSERT INTO chanserv.authhistory (userid, nick, username, host, authtime, disconnecttime, numeric)"
                    + " VALUES (?,?,?,?,?,?,?);")) {
                statement.setInt(1, getIndex(auth));
                statement.setString(2, nick);
                statement.setString(3, username);
                statement.setString(4, host);
                statement.setLong(5, getCurrentTime());
                statement.setLong(6, 0);
                statement.setInt(7, index);
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public void submitNewPassword(String email) {
        try {
            try (var statement = getConn().prepareStatement("SELECT id FROM chanserv.users WHERE email = ?")) {
                statement.setString(1, email);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        submitPassword(email, resultset.getInt(1));
                    }
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void submitPassword(String email, int index) {
        try {
            try (var statement = getConn().prepareStatement("INSERT INTO chanserv.email (userid, emailtype, prevemail) VALUES (?,?,?);")) {
                statement.setInt(1, index);
                statement.setInt(2, 2);
                statement.setString(3, email);
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private String createRandomId() {
        StringBuilder sb = new StringBuilder();
        Random r = new Random(System.currentTimeMillis());
        char[] id = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
        for (int i = 0; i < 10; i++) {
            char col = id[Math.round(r.nextFloat() * (id.length - 1))];
            sb.append(col);
        }
        return sb.toString();
    }

    private long getCurrentTime() {
        return System.currentTimeMillis() / 1000;
    }

    /**
     * Fetching userdata
     *
     * @return The data as array list
     */
    public ArrayList<String[]> getData() {
        var list = new ArrayList<String[]>();
        try (var statement = getConn().prepareStatement("SELECT * FROM chanserv.users")) {
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    var dat = new String[19];
                    dat[0] = resultset.getString("id");
                    dat[1] = resultset.getString("username");
                    dat[2] = resultset.getString("created");
                    dat[3] = resultset.getString("lastauth");
                    dat[4] = resultset.getString("lastemailchng");
                    dat[5] = resultset.getString("flags");
                    dat[6] = resultset.getString("language");
                    dat[7] = resultset.getString("suspendby");
                    dat[8] = resultset.getString("suspendexp");
                    dat[9] = resultset.getString("suspendtime");
                    dat[10] = resultset.getString("lockuntil");
                    dat[11] = resultset.getString("password");
                    dat[12] = resultset.getString("email");
                    dat[13] = resultset.getString("lastemail");
                    dat[14] = resultset.getString("lastuserhost");
                    dat[15] = resultset.getString("suspendreason");
                    dat[16] = resultset.getString("comment");
                    dat[17] = resultset.getString("info");
                    dat[18] = resultset.getString("lastpasschng");
                    list.add(dat);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    /**
     * Fetching userdata
     *
     * @return The data as array list
     */
    public ArrayList<String[]> getChannels() {
        var list = new ArrayList<String[]>();
        try (var statement = getConn().prepareStatement("SELECT * FROM chanserv.channels")) {
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    var dat = new String[28];

                    dat[0] = resultset.getString("id");
                    dat[1] = resultset.getString("name").toLowerCase();
                    dat[2] = resultset.getString("forcemodes");
                    dat[3] = resultset.getString("denymodes");
                    dat[4] = resultset.getString("chanlimit");
                    dat[5] = resultset.getString("flags");
                    dat[6] = resultset.getString("autolimit");
                    dat[7] = resultset.getString("banstyle");
                    dat[8] = resultset.getString("created");
                    dat[9] = resultset.getString("lastactive");
                    dat[10] = resultset.getString("statsreset");
                    dat[11] = resultset.getString("banduration");
                    dat[12] = resultset.getString("founder");
                    dat[13] = resultset.getString("addedby");
                    dat[14] = resultset.getString("suspendby");
                    dat[15] = resultset.getString("suspendtime");
                    dat[16] = resultset.getString("chantype");
                    dat[17] = resultset.getString("totaljoins");
                    dat[18] = resultset.getString("tripjoins");
                    dat[19] = resultset.getString("maxusers");
                    dat[20] = resultset.getString("tripusers");
                    dat[21] = resultset.getString("welcome");
                    dat[22] = resultset.getString("topic");
                    dat[23] = resultset.getString("chankey");
                    dat[24] = resultset.getString("suspendreason");
                    dat[25] = resultset.getString("topic");
                    dat[26] = resultset.getString("comment");
                    dat[27] = resultset.getString("lasttimestamp");
                    list.add(dat);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    /**
     * Fetching userdata
     *
     * @return The data as array list
     */
    public String getChannel(String key, String name) {
        try (var statement = getConn().prepareStatement("SELECT "+key+" FROM chanserv.channels WHERE name=?")) {
            statement.setString(1, name);
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    return resultset.getString(key);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return null;
    }
    
    /**
     * Fetching userdata
     *
     * @return The data as array list
     */
    public String[] getChanUser(long id, long chanid) {
        try (var statement = getConn().prepareStatement("SELECT * FROM chanserv.chanusers WHERE userid=? AND channelid=?")) {
            statement.setLong(1, id);
            statement.setLong(2, chanid);
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    var dat = new String[4];
                    dat[0] = resultset.getString("flags");
                    dat[1] = resultset.getString("changetime");
                    dat[2] = resultset.getString("usetime");
                    dat[3] = resultset.getString("info");
                    return dat;
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private void connect() {
        var config = getMi().getConfig().getConfigFile();
        var url = "jdbc:postgresql://%s/%s".formatted(config.get("dbhost"), config.get("db"));
        var props = new Properties();
        props.setProperty("user", (String) config.get("dbuser"));
        props.setProperty("password", (String) config.get("dbpassword"));
        props.setProperty("ssl", (String) config.get("dbssl"));
        try {
            System.out.println("Connecting to database...");
            setConn(DriverManager.getConnection(url, props));
            setConnected(true);
            System.out.println("Successfully connected to databse...");
        } catch (SQLException ex) {
            System.out.println("Connection to database failed: " + ex.getMessage());
            setConnected(false);
        }
    }

    /**
     * Fetching channels
     *
     * @return The data
     */
    protected ArrayList<String> getChannel() {
        var dat = new ArrayList<String>();
        try (var statement = getConn().prepareStatement("SELECT channel FROM spamscan.channels")) {
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    var data = resultset.getString("channel");
                    dat.add(data);
                }
            }
        } catch (SQLException ex) {
            System.out.println("Access to database failed: " + ex.getMessage());
        }
        return dat;
    }

    protected int getId() {
        var dat = 0;
        try (var statement = getConn().prepareStatement("SELECT COUNT(*) FROM spamscan.id")) {
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    dat = resultset.getInt(1);
                }
            }
        } catch (SQLException ex) {
            System.out.println("Access to database failed: " + ex.getMessage());
        }
        return dat;
    }

    /**
     * Create schema
     */
    protected void createSchema() {
        try {
            try (var statement = getConn().prepareStatement("CREATE SCHEMA IF NOT EXISTS spamscan;")) {
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            System.out.println("Access to database failed: " + ex.getMessage());
        }
    }

    protected void addId(String reason) {
        try {
            try (var statement = getConn().prepareStatement("INSERT INTO spamscan.id (reason) VALUES (?);")) {
                statement.setString(1, reason);
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            System.out.println("Access to database failed: " + ex.getMessage());
        }
    }

    protected void addChan(String channel) {
        try {
            try (var statement = getConn().prepareStatement("INSERT INTO spamscan.channels (channel) VALUES (?);")) {
                statement.setString(1, channel);
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            System.out.println("Access to database failed: " + ex.getMessage());
        }
    }

    protected boolean isChan(String channel) {
        String dat = null;
        try (var statement = getConn().prepareStatement("SELECT channel FROM spamscan.channels WHERE channel = ?;")) {
            statement.setString(1, channel);
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    dat = resultset.getString("channel");
                    break;
                }
            }
        } catch (SQLException ex) {
            System.out.println("Access to database failed: " + ex.getMessage());
        }
        return dat != null;
    }

    protected void removeChan(String channel) {
        try {
            try (var statement = getConn().prepareStatement("DELETE FROM spamscan.channels WHERE channel = ?;")) {
                statement.setString(1, channel);
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            System.out.println("Access to database failed: " + ex.getMessage());
        }
    }

    /**
     * Create table
     */
    protected void createTable() {
        try {
            try (var statement = getConn().prepareStatement("CREATE TABLE IF NOT EXISTS spamscan.channels (id SERIAL PRIMARY KEY, channel VARCHAR(255));")) {
                statement.executeUpdate();
            }
            try (var statement = getConn().prepareStatement("CREATE TABLE IF NOT EXISTS spamscan.id (id SERIAL PRIMARY KEY, reason VARCHAR(255));")) {
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            System.out.println("Access to database failed: " + ex.getMessage());
        }
    }

    /**
     * Commits
     */
    protected void commit() {
        try {
            try (var statement = getConn().prepareStatement("COMMIT")) {
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            System.out.println("Access to database failed: " + ex.getMessage());
        }
    }

    /**
     * Begins a transaction
     */
    protected void transcation() {
        try {
            try (var statement = getConn().prepareStatement("BEGIN TRANSACTION")) {
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            System.out.println("Access to database failed: " + ex.getMessage());
        }
    }

    /**
     * Fetching flags
     *
     * @return The data
     */
    protected int getFlags(String nick) {
        try (var statement = getConn().prepareStatement("SELECT flags FROM chanserv.users WHERE username = ?;")) {
            statement.setString(1, nick);
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    return resultset.getInt("flags");
                }
            }
        } catch (SQLException ex) {
            System.out.println("Access to database failed: " + ex.getMessage());
        }
        return 0;
    }

    /**
     * Fetching flags
     *
     * @return The data
     */
    protected HashMap<String, Integer> getFlags() {
        var dat = new HashMap<String, Integer>();
        try (var statement = getConn().prepareStatement("SELECT flags, username FROM chanserv.users WHERE flags > 4;")) {
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    dat.put(resultset.getString("username"), resultset.getInt("flags"));
                }
            }
        } catch (SQLException ex) {
            System.out.println("Access to database failed: " + ex.getMessage());
        }
        return dat;
    }

    /**
     * Is registered
     *
     * @return The data
     */
    protected String getTimestamp(String nick) {
        String dat = null;
        try (var statement = getConn().prepareStatement("SELECT lastauth FROM chanserv.users WHERE username = ?;")) {
            statement.setString(1, nick);
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    dat = resultset.getString("lastauth");
                }
            }
        } catch (SQLException ex) {
            System.out.println("Access to database failed: " + ex.getMessage());
        }
        return dat;
    }

    /**
     * Is registered
     *
     * @return The data
     */
    protected String getId(String nick) {
        String dat = null;
        try (var statement = getConn().prepareStatement("SELECT id FROM chanserv.users WHERE username = ?;")) {
            statement.setString(1, nick);
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    dat = resultset.getString("id");
                }
            }
        } catch (SQLException ex) {
            System.out.println("Access to database failed: " + ex.getMessage());
        }
        return dat;
    }

    /**
     * Is registered
     *
     * @return The data
     */
    protected boolean isRegistered(String nick, String password) {
        var dat = false;
        try (var statement = getConn().prepareStatement("SELECT * FROM chanserv.users WHERE username = ? AND password = ?;")) {
            statement.setString(1, nick);
            statement.setString(2, password);
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    if (!resultset.getString("password").isBlank()) {
                        dat = true;
                    }
                }
            }
        } catch (SQLException ex) {
            System.out.println("Access to database failed: " + ex.getMessage());
        }
        return dat;
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

    /**
     * @return the connected
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * @param connected the connected to set
     */
    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    /**
     * @return the conn
     */
    public Connection getConn() {
        return conn;
    }

    /**
     * @param conn the conn to set
     */
    public void setConn(Connection conn) {
        this.conn = conn;
    }
}
