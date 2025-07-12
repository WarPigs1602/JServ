/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 *
 * @author The database class
 */
public final class DatabaseGnuworld {

    private JServ mi;
    private Connection conn;

    protected DatabaseGnuworld(JServ mi) {
        setMi(mi);
    }

    private static final List<String> ALLOWED_USER_COLUMNS = List.of(
        "id", "user_name", "created", "lastauth", "lastemailchng", "flags", "password", "email",
        "lastemail", "lastpasschng", "language", "suspendby", "suspendexp", "suspendtime", "lockuntil",
        "lastuserhost", "suspendreason", "comment", "info"
    );

    /**
     * Fetching userdata
     *
     * @param key The key
     * @param nick The nick
     * @return The data
     */
    public String getData(String key, String nick) {
        if (!ALLOWED_USER_COLUMNS.contains(key)) {
            throw new IllegalArgumentException("Invalid key: " + key);
        }
        connect();
        String flag = null;
        String sql = "SELECT " + key + " FROM public.users WHERE LOWER(user_name) = LOWER(?)";
        try (var statement = getConn().prepareStatement(sql)) {
            statement.setString(1, nick);
            try (var resultset = statement.executeQuery()) {
                if (resultset.next()) {
                    flag = resultset.getString(key);
                }
            }
        } catch (SQLException ex) {
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
        connect();
        try (var statement = getConn().prepareStatement("UPDATE public.users SET " + key + " = ? WHERE LOWER(user_name) = LOWER(?)")) {
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
        connect();
        try (var statement = getConn().prepareStatement("UPDATE public.users SET " + key + " = ? WHERE LOWER(user_name) = LOWER(?)")) {
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
        return getData("user_name", nick) != null;
    }

    public int getIndex() {
        connect();
        var index = 0;
        try (var statement = getConn().prepareStatement("SELECT id FROM public.users ORDER BY id DESC;")) {
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
        connect();
        var index = 0;
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
        connect();
        var index = 0;
        try (var statement = getConn().prepareStatement("SELECT id FROM public.users WHERE LOWER(user_name) = LOWER(?);")) {
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
        connect();
        var flag = false;
        try (var statement = getConn().prepareStatement("SELECT * FROM public.users WHERE email=?")) {
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
        connect();
        try {
            var index = getIndex() + 1;
            try (var statement = getConn().prepareStatement("INSERT INTO public.users (user_name, created, lastauth, lastemailchng, flags, password, email, "
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

    public void addAuthHistory(String auth, String nick, String email, String user_name, String host) {
        connect();
        try {
            var index = getNumeric() + 1;
            try (var statement = getConn().prepareStatement("INSERT INTO chanserv.authhistory (userid, nick, user_name, host, authtime, disconnecttime, numeric)"
                    + " VALUES (?,?,?,?,?,?,?);")) {
                statement.setInt(1, getIndex(auth));
                statement.setString(2, nick);
                statement.setString(3, user_name);
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
        connect();
        try {
            try (var statement = getConn().prepareStatement("SELECT id FROM public.users WHERE email = ?")) {
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
        connect();
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
        var sb = new StringBuilder();
        var id = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
        var r = ThreadLocalRandom.current();
        for (int i = 0; i < 10; i++) {
            sb.append(id[r.nextInt(id.length)]);
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
        connect();
        var list = new ArrayList<String[]>();
        try (var statement = getConn().prepareStatement("SELECT * FROM public.users")) {
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    var dat = new String[19];
                    dat[0] = resultset.getString("id");
                    dat[1] = resultset.getString("user_name");
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
        connect();
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
        connect();
        try (var statement = getConn().prepareStatement("SELECT " + key + " FROM chanserv.channels WHERE name=?")) {
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
        connect();
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
            if (getConn() == null) {
                System.out.println("Connecting to database...");
                setConn(DriverManager.getConnection(url, props));
                System.out.println("Successfully connected to databse...");
            } else if (getConn().isClosed()) {
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
        connect();
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
        connect();
        try {
            try (var statement = getConn().prepareStatement("CREATE SCHEMA IF NOT EXISTS spamscan;")) {
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            System.out.println("Access to database failed: " + ex.getMessage());
        }
    }

    protected void addId(String reason) {
        connect();
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
        connect();
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
        connect();
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
        connect();
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
        connect();
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
        connect();
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
    public void beginTransaction() throws SQLException {
        connect();
        getConn().setAutoCommit(false);
    }

    public void commitTransaction() throws SQLException {
        connect();
        getConn().commit();
        getConn().setAutoCommit(true);
    }

    /**
     * Fetching flags
     *
     * @return The data
     */
    protected int getFlags(String nick) {
        connect();
        try (var statement = getConn().prepareStatement("SELECT flags FROM public.users WHERE LOWER(user_name) = LOWER(?);")) {
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
        connect();
        var dat = new HashMap<String, Integer>();
        try (var statement = getConn().prepareStatement("SELECT flags, user_name FROM public.users WHERE flags > 4;")) {
            try (var resultset = statement.executeQuery()) {
                while (resultset.next()) {
                    dat.put(resultset.getString("user_name"), resultset.getInt("flags"));
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
        connect();
        String dat = null;
        try (var statement = getConn().prepareStatement("SELECT lastauth FROM public.users WHERE LOWER(user_name) = LOWER(?);")) {
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
        connect();
        String dat = null;
        try (var statement = getConn().prepareStatement("SELECT id FROM public.users WHERE LOWER(user_name) = LOWER(?);")) {
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
        connect();
        var dat = false;
        try (var statement = getConn().prepareStatement("SELECT * FROM public.users WHERE LOWER(user_name) = LOWER(?) AND password = ?;")) {
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
    private static final Logger LOG = Logger.getLogger(DatabaseGnuworld.class.getName());

    public Optional<String> getDataOptional(String key, String nick) {
        String result = getData(key, nick);
        return Optional.ofNullable(result);
    }
}
