/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Logger;

/**
 *
 * @author The database class
 */
public final class Database {

    private static final String RECONNECT_MSG = "Database access error, trying reconnect: ";

    /**
     * Checks if the connection is valid and tries to reconnect if necessary.
     */
    private void ensureConnection() {
        try {
            if (getConn() == null || getConn().isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            connect();
        }
    }

    private static final Logger LOG = Logger.getLogger(Database.class.getName());
    private static final HashSet<String> USER_COLUMNS = new HashSet<>(Arrays.asList(
            "id", "username", "created", "lastauth", "lastemailchng", "flags", "language", "suspendby",
            "suspendexp", "suspendtime", "lockuntil", "password", "email", "lastemail", "lastuserhost",
            "suspendreason", "comment", "info", "lastpasschng"
    ));

    private static final String USER_TABLE = "chanserv.users";
    private static final String CHANNEL_TABLE = "chanserv.channels";

    private JServ mi;
    private Connection conn;

    protected Database(JServ mi) {
        setMi(mi);
    }

    private boolean isValidUserColumn(String column) {
        return USER_COLUMNS.contains(column);
    }

    /**
     * Fetching userdata
     *
     * @param key The key
     * @param nick The nick
     * @return The data
     */
    public long getLongData(String key, String nick) {
        if (!isValidUserColumn(key)) {
            throw new IllegalArgumentException("Invalid key: " + key);
        }
        long flag = 0;
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT " + key + " FROM " + USER_TABLE + " WHERE LOWER(username) = LOWER(?)")) {
                statement.setString(1, nick);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        flag = resultset.getLong(key);
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
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
    public String getData(String key, String nick) {
        if (!isValidUserColumn(key)) {
            throw new IllegalArgumentException("Invalid key: " + key);
        }
        String flag = null;
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT " + key + " FROM " + USER_TABLE + " WHERE LOWER(username) = LOWER(?)")) {
                statement.setString(1, nick);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        flag = resultset.getString(key);
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
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
    public String getAccountHistory(String key, int userId) {
        String flag = null;
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT " + key + " FROM chanserv.accounthistory WHERE userID = ?;")) {
                statement.setInt(1, userId);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        flag = resultset.getString(key);
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
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
        if (!isValidUserColumn(key)) {
            throw new IllegalArgumentException("Invalid key: " + key);
        }
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("UPDATE " + USER_TABLE + " SET " + key + " = ? WHERE LOWER(username) = LOWER(?)")) {
                statement.setString(1, data);
                statement.setString(2, nick);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    /**
     * Fetching userdata
     *
     * @param key The key
     * @param nick The nick
     * @return The data
     */
    public void deleteAccountHistory(int userId) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("DELETE FROM chanserv.accounthistory WHERE userID = ?;")) {
                statement.setInt(1, userId);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
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
        if (!isValidUserColumn(key)) {
            throw new IllegalArgumentException("Invalid key: " + key);
        }
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("UPDATE " + USER_TABLE + " SET " + key + " = ? WHERE LOWER(username) = LOWER(?)")) {
                statement.setLong(1, data);
                statement.setString(2, nick);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
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
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT id FROM " + USER_TABLE + " ORDER BY id DESC;")) {
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        index = resultset.getInt(1);
                        break;
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return index;
    }

    public int getNumeric() {
        int index = 0;
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT numeric FROM chanserv.authhistory ORDER BY numeric DESC;")) {
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        index = resultset.getInt(1);
                        break;
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return index;
    }

    public int getUserId(String nick) {
        int index = 0;
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT id FROM " + USER_TABLE + " WHERE LOWER(username) = LOWER(?);")) {
                statement.setString(1, nick);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        index = resultset.getInt(1);
                        break;
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
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

        boolean flag = false;
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT * FROM " + USER_TABLE + " WHERE LOWER(email) = LOWER(?)")) {
                statement.setString(1, email);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        flag = true;
                        break;
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return flag;
    }

    public void addUser(String nick, String email) {

        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try {
                int index = getIndex() + 1;
                try (var statement = getConn().prepareStatement("INSERT INTO " + USER_TABLE + " (username, created, lastauth, lastemailchng, flags, password, email, "
                        + "lastemail, lastpasschng, id, language, suspendby, suspendexp, suspendtime, lockuntil, lastuserhost, suspendreason, comment, info)"
                        + " VALUES (?,?,?,?,?,?,?,'',?,?,0,0,0,0,0,'','','','');")) {
                    statement.setString(1, nick);
                    statement.setLong(2, getCurrentTime());
                    statement.setLong(3, 0);
                    statement.setLong(4, 0);
                    statement.setInt(5, 4);
                    statement.setString(6, createRandomId());
                    statement.setString(7, email);
                    statement.setLong(8, 0);
                    statement.setInt(9, index);
                    statement.executeUpdate();
                }
                try (var statement = getConn().prepareStatement("INSERT INTO chanserv.email (userid, emailtype, prevemail) VALUES (?,?,LOWER(?));")) {
                    statement.setInt(1, index);
                    statement.setInt(2, 1);
                    statement.setString(3, email);
                    statement.executeUpdate();
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    public String getHost(String nick) {

        long index = getUserId(nick);
        String host = null;
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT ident,host FROM hostserv.hosts WHERE uid = ?;")) {
                statement.setLong(1, index);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        host = "%s@%s".formatted(resultset.getString("ident"), resultset.getString("host"));
                        break;
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return host;
    }

    public long getHostTimestamp(String nick) {

        long index = getUserId(nick);
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT timestamp FROM hostserv.hosts WHERE uid = ?;")) {
                statement.setLong(1, index);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        return resultset.getLong("timestamp");
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return 0;
    }

    public void addHost(String nick, String ident, String host) {

        int index = getUserId(nick);
        boolean isHost = false;
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT uid FROM hostserv.hosts WHERE uid = ?;")) {
                statement.setLong(1, index);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        isHost = true;
                        break;
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        if (!isHost) {
            tries = 0;
            while (tries < 2) {
                ensureConnection();
                try (var statement = getConn().prepareStatement("INSERT INTO hostserv.hosts (uid, ident, host, timestamp) VALUES (?,?,?,?);")) {
                    statement.setInt(1, index);
                    statement.setString(2, ident);
                    statement.setString(3, host);
                    statement.setLong(4, getCurrentTime());
                    statement.executeUpdate();
                    break;
                } catch (SQLException ex) {
                    if (tries == 0) {
                        LOG.warning(RECONNECT_MSG + ex.getMessage());
                        connect();
                    } else {
                        ex.printStackTrace();
                    }
                }
                tries++;
            }
        } else {
            tries = 0;
            while (tries < 2) {
                ensureConnection();
                try (var statement = getConn().prepareStatement("UPDATE hostserv.hosts SET ident = ?, host = ?, timestamp = ? WHERE uid = ?")) {
                    statement.setString(1, ident);
                    statement.setString(2, host);
                    statement.setLong(3, getCurrentTime());
                    statement.setInt(4, index);
                    statement.executeUpdate();
                    break;
                } catch (SQLException ex) {
                    if (tries == 0) {
                        LOG.warning(RECONNECT_MSG + ex.getMessage());
                        connect();
                    } else {
                        ex.printStackTrace();
                    }
                }
                tries++;
            }
        }
    }

    private long numericToLong(String numeric, int numericlen) {
        long mynumeric = 0;
        int i;
        var numerictab = numeric.toCharArray();
        for (i = 0; i < numericlen; i++) {
            mynumeric = (mynumeric << 6) + numerictab[i++];
        }

        return mynumeric;
    }

    public void addAuthHistory(String auth, String nick, String username, String host, String numeric) {

        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("INSERT INTO chanserv.authhistory (userid, nick, username, host, authtime, disconnecttime, numeric)"
                    + " VALUES (?,?,?,?,?,?,?);")) {
                statement.setInt(1, getUserId(auth));
                statement.setString(2, nick);
                statement.setString(3, username);
                statement.setString(4, host);
                statement.setLong(5, getCurrentTime());
                statement.setLong(6, 0);
                statement.setLong(7, numericToLong(numeric, 5));
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    public void submitNewPassword(String email) {

        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT id FROM " + USER_TABLE + " WHERE LOWER(email) = LOWER(?)")) {
                statement.setString(1, email);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        submitPassword(email, resultset.getInt(1), 2);
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    protected void submitPassword(String email, int index, int type) {

        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("INSERT INTO chanserv.email (userid, emailtype, prevemail) VALUES (?,?,LOWER(?));")) {
                statement.setInt(1, index);
                statement.setInt(2, type);
                statement.setString(3, email);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    protected String createRandomId() {
        var sb = new StringBuilder();
        var r = new Random(System.currentTimeMillis());
        var id = "ABCDEFGHIJKLMNPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz123456789!-".toCharArray();
        for (var i = 0; i < 10; i++) {
            sb.append(id[Math.round(r.nextFloat() * (id.length - 1))]);
        }
        sb.append('\0');
        return sb.toString();
    }

    private long getCurrentTime() {
        return System.currentTimeMillis() / 1000;
    }

    public void setAccountHistory(int id, String oldPassword, String newPassword, String oldMail, String newMail) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("INSERT INTO chanserv.accounthistory (userID, changetime, authtime, oldpassword, newpassword, oldemail, newemail) VALUES (?, ?, ?, ?, ?, LOWER(?), LOWER(?));")) {
                statement.setInt(1, id);
                statement.setLong(2, getCurrentTime());
                statement.setLong(3, getCurrentTime());
                statement.setString(4, oldPassword);
                statement.setString(5, newPassword);
                statement.setString(6, oldMail);
                statement.setString(7, newMail);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    /**
     * Fetching userdata
     *
     * @return The data as array list
     */
    public ArrayList<String[]> getData() {

        var list = new ArrayList<String[]>();
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT * FROM " + USER_TABLE)) {
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
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
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
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT * FROM " + CHANNEL_TABLE)) {
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
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return list;
    }

    /**
     * Fetching userdata
     *
     * @return The data as array list
     */
    public String getChannel(String key, String name) {

        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT " + key + " FROM " + CHANNEL_TABLE + " WHERE LOWER(name) = LOWER(?)")) {
                statement.setString(1, name);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        return resultset.getString(key);
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return null;
    }

    /**
     * Fetching userdata
     *
     * @return The data as array list
     */
    public String[] getChanUser(long id, long chanid) {

        int tries = 0;
        while (tries < 2) {
            ensureConnection();
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
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return null;
    }

    protected void connect() {
        var config = getMi().getConfig().getConfigFile();
        var url = "jdbc:postgresql://%s/%s".formatted(config.get("dbhost"), config.get("db"));
        var props = new Properties();
        props.setProperty("user", (String) config.get("dbuser"));
        props.setProperty("password", (String) config.get("dbpassword"));
        props.setProperty("ssl", (String) config.get("dbssl"));
        try {
            if (getConn() == null || getConn().isClosed()) {
                System.out.println("Reconnecting to database...");
                setConn(DriverManager.getConnection(url, props));
                System.out.println("Successfully reconnected to databse...");
            }
        } catch (SQLException ex) {
            System.out.println("Connection to database failed: " + ex.getMessage());
        }
    }

    /**
     * Fetching channels
     *
     * @return The data
     */
    protected ArrayList<String> getSpamScanChannels() {
        var dat = new ArrayList<String>();
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT channel FROM spamscan.channels")) {
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        var data = resultset.getString("channel");
                        dat.add(data);
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return dat;
    }

    protected int getSpamScanIdCount() {
        int dat = 0;
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT COUNT(*) FROM spamscan.id")) {
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        dat = resultset.getInt(1);
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return dat;
    }

    /**
     * Create schema
     */
    protected void createSchema() {

        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try {
                try (var statement = getConn().prepareStatement("CREATE SCHEMA IF NOT EXISTS spamscan;")) {
                    statement.executeUpdate();
                }
                try (var statement = getConn().prepareStatement("CREATE SCHEMA IF NOT EXISTS hostserv")) {
                    statement.executeUpdate();
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    protected void addId(String reason) {

        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("INSERT INTO spamscan.id (reason, created_at) VALUES (?, ?);")) {
                statement.setString(1, reason);
                statement.setLong(2, getCurrentTime());
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    protected void addChan(String channel) {

        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("INSERT INTO spamscan.channels (channel) VALUES (?);")) {
                statement.setString(1, channel);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    protected boolean isSpamScanChannel(String channel) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT 1 FROM spamscan.channels WHERE LOWER(channel) = LOWER(?);")) {
                statement.setString(1, channel);
                try (var resultset = statement.executeQuery()) {
                    return resultset.next();
                }
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    protected void removeChan(String channel) {

        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("DELETE FROM spamscan.channels WHERE LOWER(channel) = LOWER(?);")) {
                statement.setString(1, channel);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    /**
     * Create table
     */
    protected void createTable() {

        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try {
                try (var statement = getConn().prepareStatement("CREATE TABLE IF NOT EXISTS spamscan.channels (id SERIAL PRIMARY KEY, channel VARCHAR(255));")) {
                    statement.executeUpdate();
                }
                try (var statement = getConn().prepareStatement("CREATE TABLE IF NOT EXISTS spamscan.lax_channels (id SERIAL PRIMARY KEY, channel VARCHAR(255));")) {
                    statement.executeUpdate();
                }
                try (var statement = getConn().prepareStatement("CREATE TABLE IF NOT EXISTS spamscan.id (id SERIAL PRIMARY KEY, reason VARCHAR(255), created_at BIGINT NOT NULL DEFAULT EXTRACT(EPOCH FROM NOW())::BIGINT);")) {
                    statement.executeUpdate();
                }
                try (var statement = getConn().prepareStatement("CREATE TABLE IF NOT EXISTS spamscan.kill_tracking (id SERIAL PRIMARY KEY, userhost VARCHAR(255) UNIQUE NOT NULL, kill_count INTEGER DEFAULT 1, first_kill BIGINT, last_kill BIGINT, glined BOOLEAN DEFAULT FALSE);")) {
                    statement.executeUpdate();
                }
                try (var statement = getConn().prepareStatement("CREATE TABLE IF NOT EXISTS hostserv.hosts (uid INTEGER, ident VARCHAR(10), host VARCHAR(63), timestamp INTEGER);")) {
                    statement.executeUpdate();
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    /**
     * Ensures the created_at column exists in spamscan.id table
     * Adds it automatically if missing
     */
    protected void ensureCreatedAtColumn() {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try {
                // Check if column exists
                try (var statement = getConn().prepareStatement(
                    "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_schema = 'spamscan' AND table_name = 'id' AND column_name = 'created_at';"
                )) {
                    try (var resultset = statement.executeQuery()) {
                        if (!resultset.next()) {
                            // Column doesn't exist, add it
                            try (var alterStatement = getConn().prepareStatement(
                                "ALTER TABLE spamscan.id ADD COLUMN created_at BIGINT NOT NULL DEFAULT EXTRACT(EPOCH FROM NOW())::BIGINT;"
                            )) {
                                alterStatement.executeUpdate();
                                LOG.info("Added created_at column to spamscan.id table");
                            }
                        }
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    /**
     * Commits
     */
    protected void commit() {

        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("COMMIT")) {
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    /**
     * Begins a transaction
     */
    public void beginTransaction() throws SQLException {
        getConn().setAutoCommit(false);
    }

    public void commitTransaction() throws SQLException {
        getConn().commit();
        getConn().setAutoCommit(true);
    }

    /**
     * Fetching flags
     *
     * @return The data
     */
    protected int getFlags(String nick) {

        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT flags FROM " + USER_TABLE + " WHERE LOWER(username) = LOWER(?);")) {
                statement.setString(1, nick);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        return resultset.getInt("flags");
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
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
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT flags, username FROM " + USER_TABLE + " WHERE flags > 4;")) {
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        dat.put(resultset.getString("username"), resultset.getInt("flags"));
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
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
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT lastauth FROM " + USER_TABLE + " WHERE LOWER(username) = LOWER(?);")) {
                statement.setString(1, nick);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        dat = resultset.getString("lastauth");
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return dat;
    }

    /**
     * Is registered
     *
     * @return The data
     */
    protected boolean isRegistered(String nick, String password) {

        boolean dat = false;
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT * FROM " + USER_TABLE + " WHERE LOWER(username) = LOWER(?) AND password = ?;")) {
                statement.setString(1, nick);
                statement.setString(2, password);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        if (!resultset.getString("password").isBlank()) {
                            dat = true;
                        }
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
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

    public String getUserField(String field, String nick) {
        if (!isValidUserColumn(field)) {
            throw new IllegalArgumentException("Invalid key: " + field);
        }
        String sql = "SELECT " + field + " FROM " + USER_TABLE + " WHERE LOWER(username) = LOWER(?)";
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement(sql)) {
                statement.setString(1, nick);
                try (var resultset = statement.executeQuery()) {
                    if (resultset.next()) {
                        return resultset.getString(field);
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    System.out.println("Database access error, trying reconnect: " + ex.getMessage());
                    connect();
                } else {
                    LOG.severe("SQL error: " + ex.getMessage());
                }
            }
            tries++;
        }
        return null;
    }

    public Optional<String> getUserFieldOptional(String field, String nick) {
        String result = getUserField(field, nick);
        return Optional.ofNullable(result);
    }

    /**
     * Ensures the nick reservation table exists in the database
     */
    public void ensureNickReservationTableExists() {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try {
                // Create schema if not exists
                try (var statement = getConn().prepareStatement("CREATE SCHEMA IF NOT EXISTS nickserv;")) {
                    statement.executeUpdate();
                }
                
                // Create reserved_nicks table
                try (var statement = getConn().prepareStatement(
                    "CREATE TABLE IF NOT EXISTS nickserv.reserved_nicks (" +
                    "nickname VARCHAR(30) PRIMARY KEY, " +
                    "account VARCHAR(30) NOT NULL, " +
                    "reserved_time BIGINT NOT NULL, " +
                    "last_seen BIGINT NOT NULL);"
                )) {
                    statement.executeUpdate();
                }
                
                // Create index for account lookups
                try (var statement = getConn().prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_reserved_account " +
                    "ON nickserv.reserved_nicks(account);"
                )) {
                    statement.executeUpdate();
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    /**
     * Reserves a nickname for an account
     *
     * @param nick The nickname to reserve
     * @param account The account name that owns the reservation
     * @return true if reservation was successful, false if nick already reserved
     */
    public boolean reserveNick(String nick, String account) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement(
                "INSERT INTO nickserv.reserved_nicks (nickname, account, reserved_time, last_seen) " +
                "VALUES (LOWER(?), LOWER(?), ?, ?) " +
                "ON CONFLICT (nickname) DO NOTHING;"
            )) {
                long currentTime = System.currentTimeMillis() / 1000;
                statement.setString(1, nick);
                statement.setString(2, account);
                statement.setLong(3, currentTime);
                statement.setLong(4, currentTime);
                int rows = statement.executeUpdate();
                return rows > 0;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Unreserves a nickname
     *
     * @param nick The nickname to unreserve
     * @return true if unreservation was successful
     */
    public boolean unreserveNick(String nick) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement(
                "DELETE FROM nickserv.reserved_nicks WHERE LOWER(nickname) = LOWER(?);"
            )) {
                statement.setString(1, nick);
                int rows = statement.executeUpdate();
                return rows > 0;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Checks if a nickname is reserved
     *
     * @param nick The nickname to check
     * @return true if the nickname is reserved
     */
    public boolean isNickReserved(String nick) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement(
                "SELECT 1 FROM nickserv.reserved_nicks WHERE LOWER(nickname) = LOWER(?);"
            )) {
                statement.setString(1, nick);
                try (var resultset = statement.executeQuery()) {
                    return resultset.next();
                }
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Gets the account that has reserved a specific nickname
     *
     * @param nick The nickname to check
     * @return The account name that owns the reservation, or null if not reserved
     */
    public String getReservedAccount(String nick) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement(
                "SELECT account FROM nickserv.reserved_nicks WHERE LOWER(nickname) = LOWER(?);"
            )) {
                statement.setString(1, nick);
                try (var resultset = statement.executeQuery()) {
                    if (resultset.next()) {
                        return resultset.getString("account");
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return null;
    }

    /**
     * Gets all reserved nicknames for an account
     *
     * @param account The account name
     * @return List of reserved nicknames
     */
    public ArrayList<String> getReservedNicks(String account) {
        var list = new ArrayList<String>();
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement(
                "SELECT nickname FROM nickserv.reserved_nicks WHERE LOWER(account) = LOWER(?) ORDER BY nickname;"
            )) {
                statement.setString(1, account);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        list.add(resultset.getString("nickname"));
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return list;
    }

    /**
     * Updates the last_seen timestamp for a reserved nickname
     *
     * @param nick The nickname
     */
    public void updateReservedNickLastSeen(String nick) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement(
                "UPDATE nickserv.reserved_nicks SET last_seen = ? WHERE LOWER(nickname) = LOWER(?);"
            )) {
                long currentTime = System.currentTimeMillis() / 1000;
                statement.setLong(1, currentTime);
                statement.setString(2, nick);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    /**
     * Counts how many nicknames an account has reserved
     *
     * @param account The account name
     * @return Number of reserved nicknames
     */
    public int countReservedNicks(String account) {
        int count = 0;
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement(
                "SELECT COUNT(*) FROM nickserv.reserved_nicks WHERE LOWER(account) = LOWER(?);"
            )) {
                statement.setString(1, account);
                try (var resultset = statement.executeQuery()) {
                    if (resultset.next()) {
                        count = resultset.getInt(1);
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return count;
    }

    /**
     * Ensures the NickServ failed_attempts table exists in the database
     */
    public void ensureFailedAttemptsTableExists() {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try {
                // Create schema if not exists
                try (var statement = getConn().prepareStatement("CREATE SCHEMA IF NOT EXISTS nickserv;")) {
                    statement.executeUpdate();
                }
                
                // Create failed_attempts table for G-Line tracking
                try (var statement = getConn().prepareStatement(
                    "CREATE TABLE IF NOT EXISTS nickserv.failed_attempts (" +
                    "userhost VARCHAR(255) PRIMARY KEY, " +
                    "attempts INT NOT NULL DEFAULT 0, " +
                    "last_attempt BIGINT NOT NULL, " +
                    "glined_until BIGINT DEFAULT 0);"
                )) {
                    statement.executeUpdate();
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    /**
     * Tracks a failed authentication attempt for a user host
     *
     * @param userHost The user host (nick!user@host)
     * @param currentTime Current Unix timestamp
     */
    public void trackFailedAttempt(String userHost, long currentTime) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement(
                "INSERT INTO nickserv.failed_attempts (userhost, attempts, last_attempt) " +
                "VALUES (?, 1, ?) " +
                "ON CONFLICT (userhost) DO UPDATE " +
                "SET attempts = nickserv.failed_attempts.attempts + 1, " +
                "    last_attempt = EXCLUDED.last_attempt;"
            )) {
                statement.setString(1, userHost);
                statement.setLong(2, currentTime);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    /**
     * Gets failed attempt information for a user host
     *
     * @param userHost The user host to check
     * @return Array with [attempts, glined_until] or null if not found
     */
    public long[] getFailedAttempts(String userHost) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement(
                "SELECT attempts, glined_until FROM nickserv.failed_attempts WHERE userhost = ?;"
            )) {
                statement.setString(1, userHost);
                try (var resultset = statement.executeQuery()) {
                    if (resultset.next()) {
                        long[] result = new long[2];
                        result[0] = resultset.getLong("attempts");
                        result[1] = resultset.getLong("glined_until");
                        return result;
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return null;
    }

    /**
     * Updates the G-Line expiration time for a user host
     *
     * @param userHost The user host
     * @param glineUntil Unix timestamp when G-Line expires
     */
    public void setGLineExpiration(String userHost, long glineUntil) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement(
                "UPDATE nickserv.failed_attempts SET glined_until = ? WHERE userhost = ?;"
            )) {
                statement.setLong(1, glineUntil);
                statement.setString(2, userHost);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }
    
    /**
     * Clears failed authentication attempts for a user host
     * Called when user successfully authenticates
     */
    public void clearFailedAttempts(String userHost) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement(
                "DELETE FROM nickserv.failed_attempts WHERE userhost = ?;"
            )) {
                statement.setString(1, userHost);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }
    
    /**
     * Adds a channel to the lax spam detection list
     *
     * @param channel The channel name to add
     */
    protected void addLaxChannel(String channel) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("INSERT INTO spamscan.lax_channels (channel) VALUES (?);")) {
                statement.setString(1, channel);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    /**
     * Removes a channel from the lax spam detection list
     *
     * @param channel The channel name to remove
     */
    protected void removeLaxChannel(String channel) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("DELETE FROM spamscan.lax_channels WHERE LOWER(channel) = LOWER(?);")) {
                statement.setString(1, channel);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    /**
     * Checks if a channel has lax spam detection enabled
     *
     * @param channel The channel name to check
     * @return true if lax spam detection is enabled for this channel
     */
    protected boolean isLaxChannel(String channel) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT 1 FROM spamscan.lax_channels WHERE LOWER(channel) = LOWER(?);")) {
                statement.setString(1, channel);
                try (var resultset = statement.executeQuery()) {
                    return resultset.next();
                }
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Gets all channels with lax spam detection enabled
     *
     * @return List of channel names
     */
    protected ArrayList<String> getLaxChannels() {
        var channels = new ArrayList<String>();
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement("SELECT channel FROM spamscan.lax_channels")) {
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        channels.add(resultset.getString("channel"));
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return channels;
    }
    
    /**
     * Tracks a kill event for G-Line purposes
     * 
     * @param userHost The user@host to track
     * @param currentTime Current Unix timestamp
     * @return The current kill count for this user@host
     */
    protected int trackKillForGLine(String userHost, long currentTime) {
        int killCount = 0;
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try {
                // First try to insert or update
                try (var statement = getConn().prepareStatement(
                    "INSERT INTO spamscan.kill_tracking (userhost, kill_count, first_kill, last_kill) " +
                    "VALUES (?, 1, ?, ?) " +
                    "ON CONFLICT (userhost) DO UPDATE " +
                    "SET kill_count = spamscan.kill_tracking.kill_count + 1, " +
                    "    last_kill = EXCLUDED.last_kill " +
                    "RETURNING kill_count;"
                )) {
                    statement.setString(1, userHost);
                    statement.setLong(2, currentTime);
                    statement.setLong(3, currentTime);
                    try (var resultset = statement.executeQuery()) {
                        if (resultset.next()) {
                            killCount = resultset.getInt("kill_count");
                        }
                    }
                }
                break;
            } catch (SQLException ex) {
                // If ON CONFLICT fails due to missing constraint, try manual approach
                if (ex.getMessage().contains("no unique or exclusion constraint")) {
                    try {
                        // Check if entry exists
                        try (var checkStmt = getConn().prepareStatement(
                            "SELECT kill_count FROM spamscan.kill_tracking WHERE userhost = ?;"
                        )) {
                            checkStmt.setString(1, userHost);
                            try (var resultset = checkStmt.executeQuery()) {
                                if (resultset.next()) {
                                    // Update existing entry
                                    killCount = resultset.getInt("kill_count") + 1;
                                    try (var updateStmt = getConn().prepareStatement(
                                        "UPDATE spamscan.kill_tracking SET kill_count = ?, last_kill = ? WHERE userhost = ?;"
                                    )) {
                                        updateStmt.setInt(1, killCount);
                                        updateStmt.setLong(2, currentTime);
                                        updateStmt.setString(3, userHost);
                                        updateStmt.executeUpdate();
                                    }
                                } else {
                                    // Insert new entry
                                    try (var insertStmt = getConn().prepareStatement(
                                        "INSERT INTO spamscan.kill_tracking (userhost, kill_count, first_kill, last_kill) VALUES (?, 1, ?, ?);"
                                    )) {
                                        insertStmt.setString(1, userHost);
                                        insertStmt.setLong(2, currentTime);
                                        insertStmt.setLong(3, currentTime);
                                        insertStmt.executeUpdate();
                                        killCount = 1;
                                    }
                                }
                            }
                        }
                        break;
                    } catch (SQLException ex2) {
                        LOG.warning("Failed to track kill: " + ex2.getMessage());
                    }
                } else if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return killCount;
    }
    
    /**
     * Marks a user@host as G-Lined
     * 
     * @param userHost The user@host that was G-Lined
     */
    protected void markAsGLined(String userHost) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement(
                "UPDATE spamscan.kill_tracking SET glined = TRUE WHERE userhost = ?;"
            )) {
                statement.setString(1, userHost);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }
    
    /**
     * Checks if a user@host is already G-Lined
     * 
     * @param userHost The user@host to check
     * @return true if already G-Lined
     */
    protected boolean isGLined(String userHost) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement(
                "SELECT glined FROM spamscan.kill_tracking WHERE userhost = ?;"
            )) {
                statement.setString(1, userHost);
                try (var resultset = statement.executeQuery()) {
                    if (resultset.next()) {
                        return resultset.getBoolean("glined");
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }
    
    /**
     * Cleans up old kill tracking entries (older than 7 days)
     * Should be called periodically
     */
    protected void cleanupOldKillTracking() {
        long sevenDaysAgo = (System.currentTimeMillis() / 1000) - (7 * 24 * 60 * 60);
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement(
                "DELETE FROM spamscan.kill_tracking WHERE last_kill < ? AND glined = FALSE;"
            )) {
                statement.setLong(1, sevenDaysAgo);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }
    
    /**
     * Resets all kill tracking data (called on restart)
     * Clears all tracking entries that haven't resulted in a G-Line
     */
    protected void resetKillTracking() {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (var statement = getConn().prepareStatement(
                "DELETE FROM spamscan.kill_tracking WHERE glined = FALSE;"
            )) {
                int deleted = statement.executeUpdate();
                LOG.info("Reset kill tracking: removed " + deleted + " entries");
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    connect();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }
}
