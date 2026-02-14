/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.jserv;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
            if (dataSource == null || dataSource.isClosed()) {
                initializeConnectionPool();
            }
        } catch (Exception e) {
            LOG.warning("Connection pool check failed: " + e.getMessage());
            initializeConnectionPool();
        }
    }

    /**
     * Initializes the HikariCP connection pool
     */
    private void initializeConnectionPool() {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                return; // Pool already initialized
            }

            var config = getMi().getConfig().getConfigFile();
            HikariConfig hikariConfig = new HikariConfig();
            
            String url = "jdbc:postgresql://%s/%s".formatted(config.get("dbhost"), config.get("db"));
            hikariConfig.setJdbcUrl(url);
            hikariConfig.setUsername((String) config.get("dbuser"));
            hikariConfig.setPassword((String) config.get("dbpassword"));
            
            // Connection pool settings
            hikariConfig.setMaximumPoolSize(10);  // Maximum 10 connections
            hikariConfig.setMinimumIdle(2);       // Minimum 2 idle connections
            hikariConfig.setConnectionTimeout(30000);  // 30 seconds
            hikariConfig.setIdleTimeout(600000);       // 10 minutes
            hikariConfig.setMaxLifetime(1800000);      // 30 minutes
            hikariConfig.setLeakDetectionThreshold(60000); // 60 seconds
            
            // PostgreSQL specific settings
            hikariConfig.addDataSourceProperty("ssl", config.get("dbssl"));
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            
            hikariConfig.setPoolName("JServ-PostgreSQL-Pool");
            
            dataSource = new HikariDataSource(hikariConfig);
            LOG.info("Connection pool initialized successfully");
        } catch (Exception ex) {
            LOG.severe("Failed to initialize connection pool: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Gets a connection from the pool
     * @return Database connection
     * @throws SQLException if connection cannot be obtained
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            initializeConnectionPool();
        }
        return dataSource.getConnection();
    }

    /**
     * Closes the connection pool
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOG.info("Connection pool closed");
        }
    }

    private static final Logger LOG = Logger.getLogger(Database.class.getName());
    private static final HashSet<String> USER_COLUMNS = new HashSet<>(Arrays.asList(
            "id", "username", "created", "lastauth", "lastemailchng", "flags", "language", "suspendby",
            "suspendexp", "suspendtime", "lockuntil", "password", "pwd", "new_pwd", "reset_token", "generated_pwd", "email", "lastemail", "lastuserhost",
            "suspendreason", "comment", "info", "lastpasschng"
    ));

    private static final String USER_TABLE = "chanserv.users";
    private static final String CHANNEL_TABLE = "chanserv.channels";

    private JServ mi;
    private HikariDataSource dataSource;

    protected Database(JServ mi) {
        setMi(mi);
        initializeConnectionPool();
        initializeAllSchemas();
    }
    
    /**
     * Initializes all database schemas
     */
    private void initializeAllSchemas() {
        try (Connection conn = getConnection()) {
            initializeA4StatsSchema(conn);
            initializeChanServSchema(conn);
            initializeCommunityForumSchema(conn);
            initializeFakeUsersSchema(conn);
            initializeHostServSchema(conn);
            initializeNickServSchema(conn);
            initializeNoperServSchema(conn);
            initializeSpamScanSchema(conn);
            initializePublicSchema(conn);
            LOG.info("All database schemas initialized successfully");
        } catch (SQLException ex) {
            LOG.severe("Failed to initialize database schemas: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Initializes the a4stats schema if it doesn't exist
     */
    private void initializeA4StatsSchema(Connection conn) throws SQLException {
        if (!schemaExists(conn, "a4stats")) {
            LOG.info("a4stats schema not found, creating...");
            createA4StatsSchema(conn);
            LOG.info("a4stats schema created successfully");
        }
    }

    /**
     * Checks if a schema exists in the database
     */
    private boolean schemaExists(Connection conn, String schemaName) throws SQLException {
        try (var statement = conn.prepareStatement(
                "SELECT EXISTS(SELECT 1 FROM pg_namespace WHERE nspname = ?)")) {
            statement.setString(1, schemaName);
            try (var rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
            }
        }
        return false;
    }

    /**
     * Creates the complete a4stats schema with all tables, sequences, indexes and constraints
     */
    private void createA4StatsSchema(Connection conn) throws SQLException {
        conn.setAutoCommit(false);
        try (var statement = conn.createStatement()) {
            // Create schema
            statement.execute("CREATE SCHEMA a4stats");
            
            // Create channels table
            statement.execute("""
                CREATE TABLE a4stats.channels (
                    id integer NOT NULL,
                    name character varying(256),
                    "timestamp" integer DEFAULT 0,
                    active integer DEFAULT 1,
                    deleted integer DEFAULT 0,
                    privacy integer DEFAULT 1,
                    h0 integer DEFAULT 0,
                    h1 integer DEFAULT 0,
                    h2 integer DEFAULT 0,
                    h3 integer DEFAULT 0,
                    h4 integer DEFAULT 0,
                    h5 integer DEFAULT 0,
                    h6 integer DEFAULT 0,
                    h7 integer DEFAULT 0,
                    h8 integer DEFAULT 0,
                    h9 integer DEFAULT 0,
                    h10 integer DEFAULT 0,
                    h11 integer DEFAULT 0,
                    h12 integer DEFAULT 0,
                    h13 integer DEFAULT 0,
                    h14 integer DEFAULT 0,
                    h15 integer DEFAULT 0,
                    h16 integer DEFAULT 0,
                    h17 integer DEFAULT 0,
                    h18 integer DEFAULT 0,
                    h19 integer DEFAULT 0,
                    h20 integer DEFAULT 0,
                    h21 integer DEFAULT 0,
                    h22 integer DEFAULT 0,
                    h23 integer DEFAULT 0
                )
            """);
            
            // Create sequence
            statement.execute("""
                CREATE SEQUENCE a4stats.channels_id_seq
                    AS integer
                    START WITH 1
                    INCREMENT BY 1
                    NO MINVALUE
                    NO MAXVALUE
                    CACHE 1
            """);
            
            statement.execute("ALTER SEQUENCE a4stats.channels_id_seq OWNED BY a4stats.channels.id");
            statement.execute("ALTER TABLE ONLY a4stats.channels ALTER COLUMN id SET DEFAULT nextval('a4stats.channels_id_seq'::regclass)");
            
            // Create kicks table
            statement.execute("""
                CREATE TABLE a4stats.kicks (
                    channelid integer,
                    kicker character varying(128),
                    kickerid integer,
                    victim character varying(128),
                    victimid integer,
                    "timestamp" integer,
                    reason character varying(256)
                )
            """);
            
            // Create relations table
            statement.execute("""
                CREATE TABLE a4stats.relations (
                    channelid integer,
                    first character varying(128),
                    firstid integer,
                    second character varying(128),
                    secondid integer,
                    seen integer,
                    score integer DEFAULT 1
                )
            """);
            
            // Create topics table
            statement.execute("""
                CREATE TABLE a4stats.topics (
                    channelid integer,
                    setby character varying(128),
                    setbyid integer,
                    "timestamp" integer,
                    topic character varying(512)
                )
            """);
            
            // Create users table
            statement.execute("""
                CREATE TABLE a4stats.users (
                    channelid integer,
                    account character varying(128),
                    accountid integer,
                    seen integer DEFAULT 0,
                    rating integer DEFAULT 0,
                    lines integer DEFAULT 0,
                    chars integer DEFAULT 0,
                    words integer DEFAULT 0,
                    h0 integer DEFAULT 0,
                    h1 integer DEFAULT 0,
                    h2 integer DEFAULT 0,
                    h3 integer DEFAULT 0,
                    h4 integer DEFAULT 0,
                    h5 integer DEFAULT 0,
                    h6 integer DEFAULT 0,
                    h7 integer DEFAULT 0,
                    h8 integer DEFAULT 0,
                    h9 integer DEFAULT 0,
                    h10 integer DEFAULT 0,
                    h11 integer DEFAULT 0,
                    h12 integer DEFAULT 0,
                    h13 integer DEFAULT 0,
                    h14 integer DEFAULT 0,
                    h15 integer DEFAULT 0,
                    h16 integer DEFAULT 0,
                    h17 integer DEFAULT 0,
                    h18 integer DEFAULT 0,
                    h19 integer DEFAULT 0,
                    h20 integer DEFAULT 0,
                    h21 integer DEFAULT 0,
                    h22 integer DEFAULT 0,
                    h23 integer DEFAULT 0,
                    last character varying(512),
                    quote character varying(512),
                    quotereset integer DEFAULT 0,
                    mood_happy integer DEFAULT 0,
                    mood_sad integer DEFAULT 0,
                    questions integer DEFAULT 0,
                    yelling integer DEFAULT 0,
                    caps integer DEFAULT 0,
                    slaps integer DEFAULT 0,
                    slapped integer DEFAULT 0,
                    highlights integer DEFAULT 0,
                    kicks integer DEFAULT 0,
                    kicked integer DEFAULT 0,
                    ops integer DEFAULT 0,
                    deops integer DEFAULT 0,
                    actions integer DEFAULT 0,
                    skitzo integer DEFAULT 0,
                    foul integer DEFAULT 0,
                    firstseen integer DEFAULT 0,
                    curnick character varying(16)
                )
            """);
            
            // Add constraints
            statement.execute("ALTER TABLE ONLY a4stats.channels ADD CONSTRAINT channels_name_key UNIQUE (name)");
            statement.execute("ALTER TABLE ONLY a4stats.channels ADD CONSTRAINT channels_pkey PRIMARY KEY (id)");
            
            // Create indexes
            statement.execute("CREATE INDEX kicks_channelid_index ON a4stats.kicks USING btree (channelid)");
            statement.execute("CREATE INDEX kicks_timestamp_index ON a4stats.kicks USING btree (\"timestamp\")");
            statement.execute("CREATE INDEX relations_channelid_index ON a4stats.relations USING btree (channelid)");
            statement.execute("CREATE INDEX relations_score_index ON a4stats.relations USING btree (score)");
            statement.execute("CREATE INDEX topics_channelid_index ON a4stats.topics USING btree (channelid)");
            statement.execute("CREATE INDEX users_account_index ON a4stats.users USING btree (account)");
            statement.execute("CREATE INDEX users_accountid_index ON a4stats.users USING btree (accountid)");
            statement.execute("CREATE UNIQUE INDEX users_channelid_account_accountid_index ON a4stats.users USING btree (channelid, account, accountid)");
            statement.execute("CREATE INDEX users_channelid_index ON a4stats.users USING btree (channelid)");
            statement.execute("CREATE INDEX users_channelid_lines_index ON a4stats.users USING btree (channelid, lines)");
            
            // Add foreign key constraints
            statement.execute("ALTER TABLE ONLY a4stats.kicks ADD CONSTRAINT kicks_channelid_fkey FOREIGN KEY (channelid) REFERENCES a4stats.channels(id) ON DELETE CASCADE");
            statement.execute("ALTER TABLE ONLY a4stats.relations ADD CONSTRAINT relations_channelid_fkey FOREIGN KEY (channelid) REFERENCES a4stats.channels(id) ON DELETE CASCADE");
            statement.execute("ALTER TABLE ONLY a4stats.topics ADD CONSTRAINT topics_channelid_fkey FOREIGN KEY (channelid) REFERENCES a4stats.channels(id) ON DELETE CASCADE");
            statement.execute("ALTER TABLE ONLY a4stats.users ADD CONSTRAINT users_channelid_fkey FOREIGN KEY (channelid) REFERENCES a4stats.channels(id) ON DELETE CASCADE");
            
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }
    
    /**
     * Initializes the chanserv schema if it doesn't exist
     */
    private void initializeChanServSchema(Connection conn) throws SQLException {
        if (schemaExists(conn, "chanserv")) {
            return;
        }
        LOG.info("chanserv schema not found, creating...");
        createChanServSchema(conn);
        LOG.info("chanserv schema created successfully");
    }
    
    /**
     * Creates the complete chanserv schema with all tables
     */
    private void createChanServSchema(Connection conn) throws SQLException {
        conn.setAutoCommit(false);
        try (var statement = conn.createStatement()) {
            statement.execute("CREATE SCHEMA chanserv");
            
            // Users table
            statement.execute("""
                CREATE TABLE chanserv.users (
                    id integer NOT NULL,
                    username character varying(16) NOT NULL,
                    created integer NOT NULL,
                    lastauth integer NOT NULL,
                    lastemailchng integer NOT NULL,
                    flags integer NOT NULL,
                    language integer NOT NULL,
                    suspendby integer NOT NULL,
                    suspendexp integer NOT NULL,
                    suspendtime integer NOT NULL,
                    lockuntil integer NOT NULL,
                    password character varying(11),
                    email character varying(100),
                    lastemail character varying(100),
                    lastuserhost character varying(75),
                    suspendreason character varying(250),
                    comment character varying(250),
                    info character varying(100),
                    lastpasschng integer NOT NULL
                )
            """);
            
            // Channels table
            statement.execute("""
                CREATE TABLE chanserv.channels (
                    id integer NOT NULL,
                    name character varying(250) NOT NULL,
                    flags integer NOT NULL,
                    forcemodes integer NOT NULL,
                    denymodes integer NOT NULL,
                    chanlimit integer NOT NULL,
                    autolimit integer NOT NULL,
                    banstyle integer NOT NULL,
                    created integer NOT NULL,
                    lastactive integer NOT NULL,
                    statsreset integer NOT NULL,
                    banduration integer NOT NULL,
                    founder integer NOT NULL,
                    addedby integer NOT NULL,
                    suspendby integer NOT NULL,
                    suspendtime integer NOT NULL,
                    chantype smallint NOT NULL,
                    totaljoins integer NOT NULL,
                    tripjoins integer NOT NULL,
                    maxusers integer NOT NULL,
                    tripusers integer NOT NULL,
                    welcome character varying(500),
                    topic character varying(250),
                    chankey character varying(23),
                    suspendreason character varying(250),
                    comment character varying(250),
                    lasttimestamp integer
                )
            """);
            
            // Chanusers table
            statement.execute("""
                CREATE TABLE chanserv.chanusers (
                    userid integer NOT NULL,
                    channelid integer NOT NULL,
                    flags integer NOT NULL,
                    changetime integer NOT NULL,
                    usetime integer NOT NULL,
                    info character varying(100) NOT NULL
                )
            """);
            
            // Bans table
            statement.execute("""
                CREATE TABLE chanserv.bans (
                    banid SERIAL PRIMARY KEY,
                    channelid integer NOT NULL,
                    userid integer NOT NULL,
                    hostmask character varying(100) NOT NULL,
                    expiry integer NOT NULL,
                    reason character varying(200)
                )
            """);
            
            // Account history table
            statement.execute("""
                CREATE TABLE chanserv.accounthistory (
                    userid integer NOT NULL,
                    changetime integer NOT NULL,
                    authtime integer NOT NULL,
                    oldpassword character varying(11),
                    newpassword character varying(11),
                    oldemail character varying(100),
                    newemail character varying(100)
                )
            """);
            
            // Auth history table
            statement.execute("""
                CREATE TABLE chanserv.authhistory (
                    userid integer NOT NULL,
                    nick character varying(15) NOT NULL,
                    username character varying(10) NOT NULL,
                    host character varying(63) NOT NULL,
                    authtime integer NOT NULL,
                    disconnecttime integer NOT NULL,
                    "numeric" integer NOT NULL,
                    quitreason character varying(100)
                )
            """);
            
            // Chanlev history table
            statement.execute("""
                CREATE TABLE chanserv.chanlevhistory (
                    userid integer NOT NULL,
                    channelid integer NOT NULL,
                    targetid integer NOT NULL,
                    changetime integer NOT NULL,
                    authtime integer NOT NULL,
                    oldflags integer NOT NULL,
                    newflags integer NOT NULL
                )
            """);
            
            // Email table with sequence
            statement.execute("""
                CREATE SEQUENCE chanserv.email_mailid_seq
                    AS integer
                    START WITH 1
                    INCREMENT BY 1
                    NO MINVALUE
                    NO MAXVALUE
                    CACHE 1
            """);
            
            statement.execute("""
                CREATE TABLE chanserv.email (
                    userid integer NOT NULL,
                    emailtype integer NOT NULL,
                    prevemail character varying(100),
                    mailid integer NOT NULL,
                    lang character(2)
                )
            """);
            
            statement.execute("ALTER SEQUENCE chanserv.email_mailid_seq OWNED BY chanserv.email.mailid");
            statement.execute("ALTER TABLE ONLY chanserv.email ALTER COLUMN mailid SET DEFAULT nextval('chanserv.email_mailid_seq'::regclass)");
            
            // Help table
            statement.execute("""
                CREATE TABLE chanserv.help (
                    commandid integer NOT NULL,
                    command character varying(30) NOT NULL,
                    languageid integer NOT NULL,
                    summary character varying(200) NOT NULL,
                    fullinfo text NOT NULL
                )
            """);
            
            // Languages table
            statement.execute("""
                CREATE TABLE chanserv.languages (
                    languageid integer NOT NULL,
                    code character varying(2) NOT NULL,
                    name character varying(30) NOT NULL
                )
            """);
            
            // Mail domain table
            statement.execute("""
                CREATE TABLE chanserv.maildomain (
                    id integer NOT NULL,
                    name character varying NOT NULL,
                    domainlimit integer NOT NULL,
                    actlimit integer NOT NULL,
                    flags integer NOT NULL
                )
            """);
            
            // Mail locks table
            statement.execute("""
                CREATE TABLE chanserv.maillocks (
                    id integer NOT NULL,
                    pattern character varying NOT NULL,
                    reason character varying NOT NULL,
                    createdby integer NOT NULL,
                    created integer NOT NULL
                )
            """);
            
            // Messages table
            statement.execute("""
                CREATE TABLE chanserv.messages (
                    languageid integer NOT NULL,
                    messageid integer NOT NULL,
                    message character varying(250) NOT NULL
                )
            """);
            
            // Primary keys and constraints
            statement.execute("ALTER TABLE ONLY chanserv.users ADD CONSTRAINT users_pkey PRIMARY KEY (id)");
            statement.execute("ALTER TABLE ONLY chanserv.users ADD CONSTRAINT users_id_key UNIQUE (id)");
            statement.execute("ALTER TABLE ONLY chanserv.users ADD CONSTRAINT userid UNIQUE (id)");
            statement.execute("ALTER TABLE ONLY chanserv.channels ADD CONSTRAINT channels_pkey PRIMARY KEY (id)");
            statement.execute("ALTER TABLE ONLY chanserv.chanusers ADD CONSTRAINT chanusers_pkey PRIMARY KEY (userid, channelid)");
            statement.execute("ALTER TABLE ONLY chanserv.bans ADD CONSTRAINT bans_pkey PRIMARY KEY (banid)");
            statement.execute("ALTER TABLE ONLY chanserv.accounthistory ADD CONSTRAINT accounthistory_pkey PRIMARY KEY (userid, changetime)");
            statement.execute("ALTER TABLE ONLY chanserv.authhistory ADD CONSTRAINT authhistory_pkey PRIMARY KEY (userid, authtime)");
            statement.execute("ALTER TABLE ONLY chanserv.email ADD CONSTRAINT email_pkey PRIMARY KEY (mailid)");
            statement.execute("ALTER TABLE ONLY chanserv.help ADD CONSTRAINT help_pkey PRIMARY KEY (commandid, languageid)");
            statement.execute("ALTER TABLE ONLY chanserv.maildomain ADD CONSTRAINT maildomain_pkey PRIMARY KEY (id)");
            statement.execute("ALTER TABLE ONLY chanserv.maillocks ADD CONSTRAINT maillocks_pkey PRIMARY KEY (id)");
            statement.execute("ALTER TABLE ONLY chanserv.messages ADD CONSTRAINT messages_pkey PRIMARY KEY (languageid, messageid)");
            
            // Indexes
            statement.execute("CREATE UNIQUE INDEX id ON chanserv.users USING btree (id)");
            statement.execute("CREATE INDEX user_username_index ON chanserv.users USING btree (username)");
            statement.execute("CREATE INDEX accounthistory_userid_index ON chanserv.accounthistory USING btree (userid)");
            statement.execute("CREATE INDEX authhistory_userid_index ON chanserv.authhistory USING btree (userid)");
            statement.execute("CREATE INDEX bans_channelid_index ON chanserv.bans USING btree (channelid)");
            statement.execute("CREATE INDEX chanlevhistory_channelid_index ON chanserv.chanlevhistory USING btree (channelid)");
            statement.execute("CREATE INDEX chanlevhistory_targetid_index ON chanserv.chanlevhistory USING btree (targetid)");
            statement.execute("CREATE INDEX chanlevhistory_userid_index ON chanserv.chanlevhistory USING btree (userid)");
            statement.execute("CREATE INDEX chanusers_channelid_index ON chanserv.chanusers USING btree (channelid)");
            statement.execute("CREATE INDEX chanusers_userid_index ON chanserv.chanusers USING btree (userid)");
            statement.execute("CREATE UNIQUE INDEX chanserv_languages_languageid_uidx ON chanserv.languages USING btree (languageid)");
            statement.execute("CREATE UNIQUE INDEX chanserv_messages_lang_msg_uidx ON chanserv.messages USING btree (languageid, messageid)");
            
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }
    
    /**
     * Initializes the community_forum schema if it doesn't exist
     */
    private void initializeCommunityForumSchema(Connection conn) throws SQLException {
        if (schemaExists(conn, "community_forum")) {
            return;
        }
        LOG.info("community_forum schema not found, creating...");
        createCommunityForumSchema(conn);
        LOG.info("community_forum schema created successfully");
    }
    
    /**
     * Creates the complete community_forum schema
     */
    private void createCommunityForumSchema(Connection conn) throws SQLException {
        conn.setAutoCommit(false);
        try (var statement = conn.createStatement()) {
            statement.execute("CREATE SCHEMA community_forum");
            
            // Category table with sequence
            statement.execute("""
                CREATE SEQUENCE community_forum.category_id_seq
                    AS integer
                    START WITH 1
                    INCREMENT BY 1
                    NO MINVALUE
                    NO MAXVALUE
                    CACHE 1
            """);
            
            statement.execute("""
                CREATE TABLE community_forum.category (
                    id integer NOT NULL,
                    name character varying(100) NOT NULL,
                    slug character varying(120) NOT NULL,
                    sort_index integer DEFAULT 0 NOT NULL,
                    parent_id integer,
                    locked boolean DEFAULT false NOT NULL
                )
            """);
            
            statement.execute("ALTER SEQUENCE community_forum.category_id_seq OWNED BY community_forum.category.id");
            statement.execute("ALTER TABLE ONLY community_forum.category ALTER COLUMN id SET DEFAULT nextval('community_forum.category_id_seq'::regclass)");
            
            // Thread table with sequence
            statement.execute("""
                CREATE SEQUENCE community_forum.thread_id_seq
                    START WITH 1
                    INCREMENT BY 1
                    NO MINVALUE
                    NO MAXVALUE
                    CACHE 1
            """);
            
            statement.execute("""
                CREATE TABLE community_forum.thread (
                    id bigint NOT NULL,
                    category_id integer NOT NULL,
                    title character varying(200) NOT NULL,
                    author_id integer,
                    author_name character varying(50) NOT NULL,
                    created_at timestamp with time zone DEFAULT now() NOT NULL,
                    updated_at timestamp with time zone DEFAULT now() NOT NULL,
                    locked boolean DEFAULT false NOT NULL,
                    pinned boolean DEFAULT false NOT NULL
                )
            """);
            
            statement.execute("ALTER SEQUENCE community_forum.thread_id_seq OWNED BY community_forum.thread.id");
            statement.execute("ALTER TABLE ONLY community_forum.thread ALTER COLUMN id SET DEFAULT nextval('community_forum.thread_id_seq'::regclass)");
            
            // Post table with sequence
            statement.execute("""
                CREATE SEQUENCE community_forum.post_id_seq
                    START WITH 1
                    INCREMENT BY 1
                    NO MINVALUE
                    NO MAXVALUE
                    CACHE 1
            """);
            
            statement.execute("""
                CREATE TABLE community_forum.post (
                    id bigint NOT NULL,
                    thread_id bigint NOT NULL,
                    author_id integer,
                    author_name character varying(50) NOT NULL,
                    content text NOT NULL,
                    created_at timestamp with time zone DEFAULT now() NOT NULL,
                    deleted_at timestamp with time zone,
                    edited_at timestamp with time zone,
                    edited_by character varying(50),
                    edited_count integer DEFAULT 0 NOT NULL
                )
            """);
            
            statement.execute("ALTER SEQUENCE community_forum.post_id_seq OWNED BY community_forum.post.id");
            statement.execute("ALTER TABLE ONLY community_forum.post ALTER COLUMN id SET DEFAULT nextval('community_forum.post_id_seq'::regclass)");
            
            // Primary keys
            statement.execute("ALTER TABLE ONLY community_forum.category ADD CONSTRAINT category_pkey PRIMARY KEY (id)");
            statement.execute("ALTER TABLE ONLY community_forum.thread ADD CONSTRAINT thread_pkey PRIMARY KEY (id)");
            statement.execute("ALTER TABLE ONLY community_forum.post ADD CONSTRAINT post_pkey PRIMARY KEY (id)");
            
            // Foreign keys
            statement.execute("ALTER TABLE ONLY community_forum.category ADD CONSTRAINT category_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES community_forum.category(id) ON DELETE SET NULL");
            statement.execute("ALTER TABLE ONLY community_forum.category ADD CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES community_forum.category(id) ON DELETE SET NULL");
            statement.execute("ALTER TABLE ONLY community_forum.thread ADD CONSTRAINT thread_category_id_fkey FOREIGN KEY (category_id) REFERENCES community_forum.category(id) ON DELETE CASCADE");
            statement.execute("ALTER TABLE ONLY community_forum.post ADD CONSTRAINT post_thread_id_fkey FOREIGN KEY (thread_id) REFERENCES community_forum.thread(id) ON DELETE CASCADE");
            
            // Indexes
            statement.execute("CREATE INDEX idx_category_parent ON community_forum.category USING btree (parent_id)");
            statement.execute("CREATE UNIQUE INDEX uq_category_parent_slug ON community_forum.category USING btree (COALESCE(parent_id, 0), slug)");
            statement.execute("CREATE INDEX idx_thread_cat ON community_forum.thread USING btree (category_id)");
            statement.execute("CREATE INDEX idx_post_thread ON community_forum.post USING btree (thread_id)");
            
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }
    
    /**
     * Initializes the fakeusers schema if it doesn't exist
     */
    private void initializeFakeUsersSchema(Connection conn) throws SQLException {
        if (schemaExists(conn, "fakeusers")) {
            return;
        }
        LOG.info("fakeusers schema not found, creating...");
        createFakeUsersSchema(conn);
        LOG.info("fakeusers schema created successfully");
    }
    
    /**
     * Creates the complete fakeusers schema
     */
    private void createFakeUsersSchema(Connection conn) throws SQLException {
        conn.setAutoCommit(false);
        try (var statement = conn.createStatement()) {
            statement.execute("CREATE SCHEMA fakeusers");
            
            statement.execute("""
                CREATE TABLE fakeusers.fakeusers (
                    nick character varying(15) NOT NULL,
                    ident character varying(10) NOT NULL,
                    host character varying(63) NOT NULL,
                    realname character varying(50) NOT NULL
                )
            """);
            
            statement.execute("ALTER TABLE ONLY fakeusers.fakeusers ADD CONSTRAINT fakeusers_pkey PRIMARY KEY (nick)");
            
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }
    
    /**
     * Initializes the hostserv schema if it doesn't exist
     */
    private void initializeHostServSchema(Connection conn) throws SQLException {
        if (schemaExists(conn, "hostserv")) {
            return;
        }
        LOG.info("hostserv schema not found, creating...");
        createHostServSchema(conn);
        LOG.info("hostserv schema created successfully");
    }
    
    /**
     * Creates the complete hostserv schema
     */
    private void createHostServSchema(Connection conn) throws SQLException {
        conn.setAutoCommit(false);
        try (var statement = conn.createStatement()) {
            statement.execute("CREATE SCHEMA hostserv");
            
            statement.execute("""
                CREATE TABLE hostserv.hosts (
                    uid integer,
                    ident character varying(10),
                    host character varying(63),
                    "timestamp" integer
                )
            """);
            
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }
    
    /**
     * Initializes the nickserv schema if it doesn't exist
     */
    private void initializeNickServSchema(Connection conn) throws SQLException {
        if (schemaExists(conn, "nickserv")) {
            return;
        }
        LOG.info("nickserv schema not found, creating...");
        createNickServSchema(conn);
        LOG.info("nickserv schema created successfully");
    }
    
    /**
     * Creates the complete nickserv schema
     */
    private void createNickServSchema(Connection conn) throws SQLException {
        conn.setAutoCommit(false);
        try (var statement = conn.createStatement()) {
            statement.execute("CREATE SCHEMA nickserv");
            
            // Failed attempts table
            statement.execute("""
                CREATE TABLE nickserv.failed_attempts (
                    userhost character varying(255) NOT NULL,
                    attempts integer DEFAULT 0 NOT NULL,
                    last_attempt bigint NOT NULL,
                    glined_until bigint DEFAULT 0
                )
            """);
            
            // Reserved nicks table
            statement.execute("""
                CREATE TABLE nickserv.reserved_nicks (
                    nickname character varying(30) NOT NULL,
                    account character varying(30) NOT NULL,
                    reserved_time bigint NOT NULL,
                    last_seen bigint NOT NULL
                )
            """);
            
            // Primary keys
            statement.execute("ALTER TABLE ONLY nickserv.failed_attempts ADD CONSTRAINT failed_attempts_pkey PRIMARY KEY (userhost)");
            statement.execute("ALTER TABLE ONLY nickserv.reserved_nicks ADD CONSTRAINT reserved_nicks_pkey PRIMARY KEY (nickname)");
            
            // Indexes
            statement.execute("CREATE INDEX idx_reserved_account ON nickserv.reserved_nicks USING btree (account)");
            
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }
    
    /**
     * Initializes the noperserv schema if it doesn't exist
     */
    private void initializeNoperServSchema(Connection conn) throws SQLException {
        if (schemaExists(conn, "noperserv")) {
            return;
        }
        LOG.info("noperserv schema not found, creating...");
        createNoperServSchema(conn);
        LOG.info("noperserv schema created successfully");
    }
    
    /**
     * Creates the complete noperserv schema
     */
    private void createNoperServSchema(Connection conn) throws SQLException {
        conn.setAutoCommit(false);
        try (var statement = conn.createStatement()) {
            statement.execute("CREATE SCHEMA noperserv");
            
            statement.execute("""
                CREATE TABLE noperserv.users (
                    userid integer NOT NULL,
                    authname character varying(15) NOT NULL,
                    flags integer NOT NULL,
                    noticelevel integer NOT NULL
                )
            """);
            
            statement.execute("ALTER TABLE ONLY noperserv.users ADD CONSTRAINT users_pkey PRIMARY KEY (userid)");
            
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }
    
    /**
     * Initializes the spamscan schema if it doesn't exist
     */
    private void initializeSpamScanSchema(Connection conn) throws SQLException {
        if (schemaExists(conn, "spamscan")) {
            return;
        }
        LOG.info("spamscan schema not found, creating...");
        createSpamScanSchema(conn);
        LOG.info("spamscan schema created successfully");
    }
    
    /**
     * Creates the complete spamscan schema
     */
    private void createSpamScanSchema(Connection conn) throws SQLException {
        conn.setAutoCommit(false);
        try (var statement = conn.createStatement()) {
            statement.execute("CREATE SCHEMA spamscan");
            
            // Channels table with sequence
            statement.execute("""
                CREATE SEQUENCE spamscan.channels_id_seq
                    AS integer
                    START WITH 1
                    INCREMENT BY 1
                    NO MINVALUE
                    NO MAXVALUE
                    CACHE 1
            """);
            
            statement.execute("""
                CREATE TABLE spamscan.channels (
                    id integer NOT NULL,
                    channel text
                )
            """);
            
            statement.execute("ALTER SEQUENCE spamscan.channels_id_seq OWNED BY spamscan.channels.id");
            statement.execute("ALTER TABLE ONLY spamscan.channels ALTER COLUMN id SET DEFAULT nextval('spamscan.channels_id_seq'::regclass)");
            
            // ID table with sequence
            statement.execute("""
                CREATE SEQUENCE spamscan.id_id_seq
                    AS integer
                    START WITH 1
                    INCREMENT BY 1
                    NO MINVALUE
                    NO MAXVALUE
                    CACHE 1
            """);
            
            statement.execute("""
                CREATE TABLE spamscan.id (
                    id integer NOT NULL,
                    reason character varying(255),
                    created_at bigint DEFAULT (EXTRACT(epoch FROM now()))::bigint NOT NULL
                )
            """);
            
            statement.execute("ALTER SEQUENCE spamscan.id_id_seq OWNED BY spamscan.id.id");
            statement.execute("ALTER TABLE ONLY spamscan.id ALTER COLUMN id SET DEFAULT nextval('spamscan.id_id_seq'::regclass)");
            
            // Kill tracking table with sequence
            statement.execute("""
                CREATE SEQUENCE spamscan.kill_tracking_id_seq
                    AS integer
                    START WITH 1
                    INCREMENT BY 1
                    NO MINVALUE
                    NO MAXVALUE
                    CACHE 1
            """);
            
            statement.execute("""
                CREATE TABLE spamscan.kill_tracking (
                    id integer NOT NULL,
                    userhost character varying(255) NOT NULL,
                    kill_count integer DEFAULT 1,
                    first_kill bigint,
                    last_kill bigint,
                    glined boolean DEFAULT false
                )
            """);
            
            statement.execute("ALTER SEQUENCE spamscan.kill_tracking_id_seq OWNED BY spamscan.kill_tracking.id");
            statement.execute("ALTER TABLE ONLY spamscan.kill_tracking ALTER COLUMN id SET DEFAULT nextval('spamscan.kill_tracking_id_seq'::regclass)");
            
            // Lax channels table with sequence
            statement.execute("""
                CREATE SEQUENCE spamscan.lax_channels_id_seq
                    AS integer
                    START WITH 1
                    INCREMENT BY 1
                    NO MINVALUE
                    NO MAXVALUE
                    CACHE 1
            """);
            
            statement.execute("""
                CREATE TABLE spamscan.lax_channels (
                    id integer NOT NULL,
                    channel character varying(255)
                )
            """);
            
            statement.execute("ALTER SEQUENCE spamscan.lax_channels_id_seq OWNED BY spamscan.lax_channels.id");
            statement.execute("ALTER TABLE ONLY spamscan.lax_channels ALTER COLUMN id SET DEFAULT nextval('spamscan.lax_channels_id_seq'::regclass)");
            
            // Primary keys
            statement.execute("ALTER TABLE ONLY spamscan.channels ADD CONSTRAINT channels_pkey PRIMARY KEY (id)");
            statement.execute("ALTER TABLE ONLY spamscan.id ADD CONSTRAINT id_pkey PRIMARY KEY (id)");
            statement.execute("ALTER TABLE ONLY spamscan.kill_tracking ADD CONSTRAINT kill_tracking_pkey PRIMARY KEY (id)");
            statement.execute("ALTER TABLE ONLY spamscan.kill_tracking ADD CONSTRAINT kill_tracking_userhost_key UNIQUE (userhost)");
            statement.execute("ALTER TABLE ONLY spamscan.lax_channels ADD CONSTRAINT lax_channels_pkey PRIMARY KEY (id)");
            
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }
    
    /**
     * Initializes public schema tables if they don't exist
     */
    private void initializePublicSchema(Connection conn) throws SQLException {
        conn.setAutoCommit(false);
        try (var statement = conn.createStatement()) {
            // Check and create openproxies table
            if (!tableExists(conn, "public", "openproxies")) {
                LOG.info("Creating public.openproxies table...");
                statement.execute("""
                    CREATE TABLE public.openproxies (
                        id bigint NOT NULL,
                        ip inet NOT NULL,
                        pm integer NOT NULL,
                        ts integer NOT NULL,
                        rh character varying NOT NULL
                    )
                """);
                statement.execute("ALTER TABLE ONLY public.openproxies ADD CONSTRAINT openproxies_pkey PRIMARY KEY (id)");
                statement.execute("CREATE INDEX openproxies_id_index ON public.openproxies USING btree (id)");
            }
            
            // Check and create spamscan table in public schema
            if (!tableExists(conn, "public", "spamscan")) {
                LOG.info("Creating public.spamscan table...");
                statement.execute("""
                    CREATE TABLE public.spamscan (
                        id integer NOT NULL,
                        channel text
                    )
                """);
                statement.execute("ALTER TABLE ONLY public.spamscan ADD CONSTRAINT spamscan_pkey PRIMARY KEY (id)");
            }
            
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }
    
    /**
     * Checks if a table exists in a specific schema
     */
    private boolean tableExists(Connection conn, String schemaName, String tableName) throws SQLException {
        try (var statement = conn.prepareStatement(
                "SELECT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?)")) {
            statement.setString(1, schemaName);
            statement.setString(2, tableName);
            try (var rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
            }
        }
        return false;
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT " + key + " FROM " + USER_TABLE + " WHERE LOWER(username) = LOWER(?)")) {
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT " + key + " FROM " + USER_TABLE + " WHERE LOWER(username) = LOWER(?)")) {
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT " + key + " FROM chanserv.accounthistory WHERE userID = ?;")) {
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
                    initializeConnectionPool();
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
        // For password updates, use the secure updateUserPassword method
        if (key.equals("password")) {
            updateUserPassword(nick, data);
            return;
        }
        
        if (!isValidUserColumn(key)) {
            throw new IllegalArgumentException("Invalid key: " + key);
        }
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("UPDATE " + USER_TABLE + " SET " + key + " = ? WHERE LOWER(username) = LOWER(?)")) {
                statement.setString(1, data);
                statement.setString(2, nick);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("DELETE FROM chanserv.accounthistory WHERE userID = ?;")) {
                statement.setInt(1, userId);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("UPDATE " + USER_TABLE + " SET " + key + " = ? WHERE LOWER(username) = LOWER(?)")) {
                statement.setLong(1, data);
                statement.setString(2, nick);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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

    /**
     * Check if an email address is already registered in the database
     *
     * @param email The email address to check
     * @return true if email exists, false otherwise
     */
    public boolean isEmailRegistered(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                     "SELECT COUNT(*) FROM " + USER_TABLE + " WHERE LOWER(email) = LOWER(?)")) {
                statement.setString(1, email.trim());
                try (var resultset = statement.executeQuery()) {
                    if (resultset.next()) {
                        return resultset.getInt(1) > 0;
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    public int getIndex() {
        int index = 0;
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT id FROM " + USER_TABLE + " ORDER BY id DESC;")) {
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT numeric FROM chanserv.authhistory ORDER BY numeric DESC;")) {
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT id FROM " + USER_TABLE + " WHERE LOWER(username) = LOWER(?);")) {
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT * FROM " + USER_TABLE + " WHERE LOWER(email) = LOWER(?)")) {
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
                    initializeConnectionPool();
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
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("INSERT INTO " + USER_TABLE + " (username, created, lastauth, lastemailchng, flags, password, email, "
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
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("INSERT INTO chanserv.email (userid, emailtype, prevemail) VALUES (?,?,LOWER(?));")) {
                    statement.setInt(1, index);
                    statement.setInt(2, 1);
                    statement.setString(3, email);
                    statement.executeUpdate();
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    /**
     * Adds an email entry to the email table
     * @param userId User ID
     * @param emailType Email type (1=registration, 2=password reset, etc.)
     * @param email Email address
     * @return true if successful, false otherwise
     */
    public boolean addEmail(int userId, int emailType, String email) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                    "INSERT INTO chanserv.email (userid, emailtype, prevemail) VALUES (?,?,LOWER(?))")) {
                statement.setInt(1, userId);
                statement.setInt(2, emailType);
                statement.setString(3, email);
                statement.executeUpdate();
                return true;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    LOG.warning("Failed to add email entry: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Adds a user with specific password and flags
     * @param username Username
     * @param password Password (max 11 chars)
     * @param flags User flags (e.g., 512 for admin/+d)
     * @return true if successful, false otherwise
     */
    public boolean addUser(String username, String password, int flags) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try {
                int index = getIndex() + 1;
                long now = getCurrentTime();
                String hashedPassword = hashPassword(password, now);
                
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                        "INSERT INTO " + USER_TABLE + " " +
                        "(username, created, lastauth, lastemailchng, flags, pwd, email, " +
                        "lastemail, lastpasschng, id, language, suspendby, suspendexp, suspendtime, " +
                        "lockuntil, lastuserhost, suspendreason, comment, info) " +
                        "VALUES (?,?,?,?,?,?,?,'',?,?,0,0,0,0,0,'','','','')")) {
                    statement.setString(1, username);
                    statement.setLong(2, now);
                    statement.setLong(3, 0);
                    statement.setLong(4, 0);
                    statement.setInt(5, flags);
                    statement.setString(6, hashedPassword);
                    statement.setString(7, "");  // No email for admin registration
                    statement.setLong(8, now);
                    statement.setInt(9, index);
                    statement.executeUpdate();
                }
                LOG.info("User added: " + username + " with flags: " + flags);
                return true;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    LOG.severe("Failed to add user: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    public String getHost(String nick) {

        long index = getUserId(nick);
        String host = null;
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT ident,host FROM hostserv.hosts WHERE uid = ?;")) {
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT timestamp FROM hostserv.hosts WHERE uid = ?;")) {
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT uid FROM hostserv.hosts WHERE uid = ?;")) {
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
                    initializeConnectionPool();
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
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("INSERT INTO hostserv.hosts (uid, ident, host, timestamp) VALUES (?,?,?,?);")) {
                    statement.setInt(1, index);
                    statement.setString(2, ident);
                    statement.setString(3, host);
                    statement.setLong(4, getCurrentTime());
                    statement.executeUpdate();
                    break;
                } catch (SQLException ex) {
                    if (tries == 0) {
                        LOG.warning(RECONNECT_MSG + ex.getMessage());
                        initializeConnectionPool();
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
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("UPDATE hostserv.hosts SET ident = ?, host = ?, timestamp = ? WHERE uid = ?")) {
                    statement.setString(1, ident);
                    statement.setString(2, host);
                    statement.setLong(3, getCurrentTime());
                    statement.setInt(4, index);
                    statement.executeUpdate();
                    break;
                } catch (SQLException ex) {
                    if (tries == 0) {
                        LOG.warning(RECONNECT_MSG + ex.getMessage());
                        initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("INSERT INTO chanserv.authhistory (userid, nick, username, host, authtime, disconnecttime, numeric)"
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT id FROM " + USER_TABLE + " WHERE LOWER(email) = LOWER(?)")) {
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("INSERT INTO chanserv.email (userid, emailtype, prevemail) VALUES (?,?,LOWER(?));")) {
                statement.setInt(1, index);
                statement.setInt(2, type);
                statement.setString(3, email);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("INSERT INTO chanserv.accounthistory (userID, changetime, authtime, oldpassword, newpassword, oldemail, newemail) VALUES (?, ?, ?, ?, ?, LOWER(?), LOWER(?));")) {
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT * FROM " + USER_TABLE)) {
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT * FROM " + CHANNEL_TABLE)) {
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT " + key + " FROM " + CHANNEL_TABLE + " WHERE LOWER(name) = LOWER(?)")) {
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT * FROM chanserv.chanusers WHERE userid=? AND channelid=?")) {
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
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return null;
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT channel FROM spamscan.channels")) {
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT COUNT(*) FROM spamscan.id")) {
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        dat = resultset.getInt(1);
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("CREATE SCHEMA IF NOT EXISTS spamscan;")) {
                    statement.executeUpdate();
                }
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("CREATE SCHEMA IF NOT EXISTS hostserv")) {
                    statement.executeUpdate();
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("INSERT INTO spamscan.id (reason, created_at) VALUES (?, ?);")) {
                statement.setString(1, reason);
                statement.setLong(2, getCurrentTime());
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("INSERT INTO spamscan.channels (channel) VALUES (?);")) {
                statement.setString(1, channel);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT 1 FROM spamscan.channels WHERE LOWER(channel) = LOWER(?);")) {
                statement.setString(1, channel);
                try (var resultset = statement.executeQuery()) {
                    return resultset.next();
                }
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("DELETE FROM spamscan.channels WHERE LOWER(channel) = LOWER(?);")) {
                statement.setString(1, channel);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("CREATE TABLE IF NOT EXISTS spamscan.channels (id SERIAL PRIMARY KEY, channel VARCHAR(255));")) {
                    statement.executeUpdate();
                }
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("CREATE TABLE IF NOT EXISTS spamscan.lax_channels (id SERIAL PRIMARY KEY, channel VARCHAR(255));")) {
                    statement.executeUpdate();
                }
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("CREATE TABLE IF NOT EXISTS spamscan.id (id SERIAL PRIMARY KEY, reason VARCHAR(255), created_at BIGINT NOT NULL DEFAULT EXTRACT(EPOCH FROM NOW())::BIGINT);")) {
                    statement.executeUpdate();
                }
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("CREATE TABLE IF NOT EXISTS spamscan.kill_tracking (id SERIAL PRIMARY KEY, userhost VARCHAR(255) UNIQUE NOT NULL, kill_count INTEGER DEFAULT 1, first_kill BIGINT, last_kill BIGINT, glined BOOLEAN DEFAULT FALSE);")) {
                    statement.executeUpdate();
                }
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("CREATE TABLE IF NOT EXISTS hostserv.hosts (uid INTEGER, ident VARCHAR(10), host VARCHAR(63), timestamp INTEGER);")) {
                    statement.executeUpdate();
                }
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("CREATE SCHEMA IF NOT EXISTS operserv;")) {
                    statement.executeUpdate();
                }
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("CREATE TABLE IF NOT EXISTS operserv.glines (id SERIAL PRIMARY KEY, mask VARCHAR(255) UNIQUE NOT NULL, reason VARCHAR(500), setby VARCHAR(16) NOT NULL, created BIGINT NOT NULL, expires BIGINT);")) {
                    statement.executeUpdate();
                }
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS operserv.trusts (" +
                        "id SERIAL PRIMARY KEY, " +
                        "mask VARCHAR(255) UNIQUE NOT NULL, " +
                        "setby VARCHAR(16) NOT NULL, " +
                        "created BIGINT NOT NULL, " +
                        "maxconn INTEGER NOT NULL DEFAULT 0, " +
                        "require_ident BOOLEAN NOT NULL DEFAULT FALSE" +
                        ");")) {
                    statement.executeUpdate();
                }
                ensureOperServTrustColumns();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                    "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_schema = 'spamscan' AND table_name = 'id' AND column_name = 'created_at';"
                )) {
                    try (var resultset = statement.executeQuery()) {
                        if (!resultset.next()) {
                            // Column doesn't exist, add it
                            try (var alterStatement = conn.prepareStatement(
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
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    /**
     * Ensures new columns exist in operserv.trusts (maxconn, require_ident)
     */
    protected void ensureOperServTrustColumns() {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try {
                // maxconn
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                        "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_schema = 'operserv' AND table_name = 'trusts' AND column_name = 'maxconn';")) {
                    try (var resultset = statement.executeQuery()) {
                        if (!resultset.next()) {
                            try (var alter = conn.prepareStatement(
                                    "ALTER TABLE operserv.trusts ADD COLUMN maxconn INTEGER NOT NULL DEFAULT 0;")) {
                                alter.executeUpdate();
                                LOG.info("Added maxconn column to operserv.trusts table");
                            }
                        }
                    }
                }

                // require_ident
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                        "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_schema = 'operserv' AND table_name = 'trusts' AND column_name = 'require_ident';")) {
                    try (var resultset = statement.executeQuery()) {
                        if (!resultset.next()) {
                            try (var alter = conn.prepareStatement(
                                    "ALTER TABLE operserv.trusts ADD COLUMN require_ident BOOLEAN NOT NULL DEFAULT FALSE;")) {
                                alter.executeUpdate();
                                LOG.info("Added require_ident column to operserv.trusts table");
                            }
                        }
                    }
                }

                // max_idents_per_host
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                        "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_schema = 'operserv' AND table_name = 'trusts' AND column_name = 'max_idents_per_host';")) {
                    try (var resultset = statement.executeQuery()) {
                        if (!resultset.next()) {
                            try (var alter = conn.prepareStatement(
                                    "ALTER TABLE operserv.trusts ADD COLUMN max_idents_per_host INTEGER NOT NULL DEFAULT 0;")) {
                                alter.executeUpdate();
                                LOG.info("Added max_idents_per_host column to operserv.trusts table");
                            }
                        }
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    /**
     * Ensures operserv schema + tables needed by OperServ/TrustCheck exist.
     */
    public void ensureOperServTables() {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try {
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("CREATE SCHEMA IF NOT EXISTS operserv;")) {
                    statement.executeUpdate();
                }
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS operserv.glines (" +
                        "id SERIAL PRIMARY KEY, " +
                        "mask VARCHAR(255) UNIQUE NOT NULL, " +
                        "reason VARCHAR(500), " +
                        "setby VARCHAR(16) NOT NULL, " +
                        "created BIGINT NOT NULL, " +
                        "expires BIGINT" +
                        ");")) {
                    statement.executeUpdate();
                }
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS operserv.trusts (" +
                        "id SERIAL PRIMARY KEY, " +
                        "mask VARCHAR(255) UNIQUE NOT NULL, " +
                        "setby VARCHAR(16) NOT NULL, " +
                        "created BIGINT NOT NULL, " +
                        "maxconn INTEGER NOT NULL DEFAULT 0, " +
                        "require_ident BOOLEAN NOT NULL DEFAULT FALSE" +
                        ");")) {
                    statement.executeUpdate();
                }
                ensureOperServTrustColumns();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    /**
     * Ensures chanserv schema + all required tables exist.
     * Creates schema and tables: users, channels, chanusers, bans, email
     */
    public void ensureChanServTables() {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try {
                // Create chanserv schema
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("CREATE SCHEMA IF NOT EXISTS chanserv;")) {
                    statement.executeUpdate();
                    LOG.info("ChanServ schema ensured");
                }

                // Create users table
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS chanserv.users (" +
                        "id INTEGER PRIMARY KEY, " +
                        "username VARCHAR(30) UNIQUE NOT NULL, " +
                        "created BIGINT NOT NULL, " +
                        "lastauth BIGINT, " +
                        "lastemailchng BIGINT, " +
                        "flags INTEGER NOT NULL DEFAULT 0, " +
                        "language INTEGER DEFAULT 0, " +
                        "suspendby INTEGER DEFAULT 0, " +
                        "suspendexp BIGINT DEFAULT 0, " +
                        "suspendtime BIGINT DEFAULT 0, " +
                        "lockuntil BIGINT DEFAULT 0, " +
                        "password VARCHAR(11), " +
                        "pwd VARCHAR(64), " +
                        "new_pwd VARCHAR(64), " +
                        "reset_token VARCHAR(64), " +
                        "generated_pwd VARCHAR(20), " +
                        "email VARCHAR(255), " +
                        "lastemail VARCHAR(255), " +
                        "lastuserhost VARCHAR(255), " +
                        "suspendreason VARCHAR(500), " +
                        "comment VARCHAR(500), " +
                        "info VARCHAR(500), " +
                        "lastpasschng BIGINT" +
                        ");")) {
                    statement.executeUpdate();
                    LOG.info("ChanServ users table ensured");
                }

                // Ensure pwd column exists (for existing tables)
                try (Connection conn = getConnection();
                     var checkStmt = conn.prepareStatement(
                        "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_schema = 'chanserv' AND table_name = 'users' AND column_name = 'pwd'")) {
                    try (var rs = checkStmt.executeQuery()) {
                        if (!rs.next()) {
                            try (var alterStmt = conn.prepareStatement(
                                    "ALTER TABLE chanserv.users ADD COLUMN pwd VARCHAR(64)")) {
                                alterStmt.executeUpdate();
                                LOG.info("Added pwd column to chanserv.users");
                            }
                        }
                    }
                }

                // Ensure new_pwd column exists (for existing tables)
                try (Connection conn = getConnection();
                     var checkStmt = conn.prepareStatement(
                        "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_schema = 'chanserv' AND table_name = 'users' AND column_name = 'new_pwd'")) {
                    try (var rs = checkStmt.executeQuery()) {
                        if (!rs.next()) {
                            try (var alterStmt = conn.prepareStatement(
                                    "ALTER TABLE chanserv.users ADD COLUMN new_pwd VARCHAR(64)")) {
                                alterStmt.executeUpdate();
                                LOG.info("Added new_pwd column to chanserv.users");
                            }
                        }
                    }
                }

                // Ensure reset_token column exists (for existing tables)
                try (Connection conn = getConnection();
                     var checkStmt = conn.prepareStatement(
                        "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_schema = 'chanserv' AND table_name = 'users' AND column_name = 'reset_token'")) {
                    try (var rs = checkStmt.executeQuery()) {
                        if (!rs.next()) {
                            try (var alterStmt = conn.prepareStatement(
                                    "ALTER TABLE chanserv.users ADD COLUMN reset_token VARCHAR(64)")) {
                                alterStmt.executeUpdate();
                                LOG.info("Added reset_token column to chanserv.users");
                            }
                        }
                    }
                }

                // Ensure generated_pwd column exists (for existing tables)
                try (Connection conn = getConnection();
                     var checkStmt = conn.prepareStatement(
                        "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_schema = 'chanserv' AND table_name = 'users' AND column_name = 'generated_pwd'")) {
                    try (var rs = checkStmt.executeQuery()) {
                        if (!rs.next()) {
                            try (var alterStmt = conn.prepareStatement(
                                    "ALTER TABLE chanserv.users ADD COLUMN generated_pwd VARCHAR(20)")) {
                                alterStmt.executeUpdate();
                                LOG.info("Added generated_pwd column to chanserv.users");
                            }
                        }
                    }
                }

                // Ensure password column is nullable (for migration from old schema)
                try (Connection conn = getConnection();
                     var checkStmt = conn.prepareStatement(
                        "SELECT is_nullable FROM information_schema.columns " +
                        "WHERE table_schema = 'chanserv' AND table_name = 'users' AND column_name = 'password'")) {
                    try (var rs = checkStmt.executeQuery()) {
                        if (rs.next() && "NO".equals(rs.getString("is_nullable"))) {
                            try (var alterStmt = conn.prepareStatement(
                                    "ALTER TABLE chanserv.users ALTER COLUMN password DROP NOT NULL")) {
                                alterStmt.executeUpdate();
                                LOG.info("Modified password column to allow NULL values for secure migration");
                            }
                        }
                    }
                }

                // Migrate old passwords to new secure format
                migratePasswordsToSecure();

                // Create channels table
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS chanserv.channels (" +
                        "id INTEGER PRIMARY KEY, " +
                        "name VARCHAR(64) UNIQUE NOT NULL, " +
                        "flags INTEGER NOT NULL DEFAULT 0, " +
                        "forcemodes INTEGER DEFAULT 0, " +
                        "denymodes INTEGER DEFAULT 0, " +
                        "chanlimit INTEGER DEFAULT 0, " +
                        "autolimit INTEGER DEFAULT 0, " +
                        "banstyle INTEGER DEFAULT 0, " +
                        "created BIGINT NOT NULL, " +
                        "lastactive BIGINT, " +
                        "statsreset BIGINT, " +
                        "banduration INTEGER DEFAULT 0, " +
                        "founder INTEGER NOT NULL, " +
                        "addedby INTEGER DEFAULT 0, " +
                        "suspendby INTEGER DEFAULT 0, " +
                        "suspendtime BIGINT DEFAULT 0, " +
                        "chantype INTEGER DEFAULT 0, " +
                        "totaljoins INTEGER DEFAULT 0, " +
                        "tripjoins INTEGER DEFAULT 0, " +
                        "maxusers INTEGER DEFAULT 0, " +
                        "tripusers INTEGER DEFAULT 0, " +
                        "welcome VARCHAR(500), " +
                        "topic VARCHAR(500), " +
                        "chankey VARCHAR(255), " +
                        "comment VARCHAR(500), " +
                        "lasttimestamp BIGINT, " +
                        "suspendreason VARCHAR(500)" +
                        ");")) {
                    statement.executeUpdate();
                    LOG.info("ChanServ channels table ensured");
                }

                // Ensure additional columns exist (for existing tables)
                try (Connection conn = getConnection()) {
                    String[] additionalColumns = {
                        "welcome VARCHAR(500)",
                        "topic VARCHAR(500)",
                        "chankey VARCHAR(255)",
                        "comment VARCHAR(500)",
                        "lasttimestamp BIGINT"
                    };
                    
                    for (String colDef : additionalColumns) {
                        String colName = colDef.split(" ")[0];
                        try (var checkStmt = conn.prepareStatement(
                                "SELECT column_name FROM information_schema.columns " +
                                "WHERE table_schema = 'chanserv' AND table_name = 'channels' AND column_name = ?")) {
                            checkStmt.setString(1, colName);
                            try (var rs = checkStmt.executeQuery()) {
                                if (!rs.next()) {
                                    try (var alterStmt = conn.prepareStatement(
                                            "ALTER TABLE chanserv.channels ADD COLUMN " + colDef)) {
                                        alterStmt.executeUpdate();
                                        LOG.info("Added " + colName + " column to chanserv.channels");
                                    }
                                }
                            }
                        }
                    }
                }

                // Create chanusers table (channel access list)
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS chanserv.chanusers (" +
                        "userid INTEGER NOT NULL, " +
                        "channelid INTEGER NOT NULL, " +
                        "flags INTEGER NOT NULL DEFAULT 0, " +
                        "changetime BIGINT, " +
                        "usetime BIGINT, " +
                        "info VARCHAR(500), " +
                        "PRIMARY KEY (userid, channelid)" +
                        ");")) {
                    statement.executeUpdate();
                    LOG.info("ChanServ chanusers table ensured");
                }

                // Create bans table
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS chanserv.bans (" +
                        "banid SERIAL PRIMARY KEY, " +
                        "channelid INTEGER NOT NULL, " +
                        "userid INTEGER NOT NULL, " +
                        "hostmask VARCHAR(255) NOT NULL, " +
                        "expiry BIGINT DEFAULT 0, " +
                        "reason VARCHAR(500)" +
                        ");")) {
                    statement.executeUpdate();
                    LOG.info("ChanServ bans table ensured");
                }

                // Create email table (for email change tracking)
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS chanserv.email (" +
                        "userid INTEGER NOT NULL, " +
                        "emailtype INTEGER NOT NULL DEFAULT 1, " +
                        "prevemail VARCHAR(255), " +
                        "PRIMARY KEY (userid, emailtype)" +
                        ");")) {
                    statement.executeUpdate();
                    LOG.info("ChanServ email table ensured");
                }

                // Create indexes for performance
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                        "CREATE INDEX IF NOT EXISTS idx_chanusers_channelid ON chanserv.chanusers(channelid);")) {
                    statement.executeUpdate();
                }
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                        "CREATE INDEX IF NOT EXISTS idx_bans_channelid ON chanserv.bans(channelid);")) {
                    statement.executeUpdate();
                }
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                        "CREATE INDEX IF NOT EXISTS idx_channels_name ON chanserv.channels(name);")) {
                    statement.executeUpdate();
                }
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                        "CREATE INDEX IF NOT EXISTS idx_users_username ON chanserv.users(username);")) {
                    statement.executeUpdate();
                }

                // Migrate banid column to SERIAL if it's not already
                try (Connection conn = getConnection()) {
                    // Check if bans table exists and if banid has a sequence
                    try (var checkStmt = conn.prepareStatement(
                            "SELECT pg_get_serial_sequence('chanserv.bans', 'banid') as seq_name")) {
                        try (var rs = checkStmt.executeQuery()) {
                            if (rs.next()) {
                                String seqName = rs.getString("seq_name");
                                if (seqName == null) {
                                    // banid is not SERIAL, migrate it
                                    LOG.info("Migrating banid column to SERIAL...");
                                    
                                    // Create sequence if it doesn't exist
                                    try (var createSeqStmt = conn.prepareStatement(
                                            "CREATE SEQUENCE IF NOT EXISTS chanserv.bans_banid_seq")) {
                                        createSeqStmt.executeUpdate();
                                    }
                                    
                                    // Set current value to max existing banid
                                    try (var maxStmt = conn.prepareStatement(
                                            "SELECT COALESCE(MAX(banid), 0) + 1 as next_val FROM chanserv.bans")) {
                                        try (var maxRs = maxStmt.executeQuery()) {
                                            if (maxRs.next()) {
                                                long nextVal = maxRs.getLong("next_val");
                                                try (var setValStmt = conn.prepareStatement(
                                                        "SELECT setval('chanserv.bans_banid_seq', ?)")) {
                                                    setValStmt.setLong(1, nextVal);
                                                    setValStmt.executeQuery();
                                                }
                                            }
                                        }
                                    }
                                    
                                    // Alter column to use sequence
                                    try (var alterStmt = conn.prepareStatement(
                                            "ALTER TABLE chanserv.bans ALTER COLUMN banid SET DEFAULT nextval('chanserv.bans_banid_seq')")) {
                                        alterStmt.executeUpdate();
                                    }
                                    
                                    // Set sequence owner
                                    try (var ownerStmt = conn.prepareStatement(
                                            "ALTER SEQUENCE chanserv.bans_banid_seq OWNED BY chanserv.bans.banid")) {
                                        ownerStmt.executeUpdate();
                                    }
                                    
                                    LOG.info("Successfully migrated banid to SERIAL");
                                }
                            }
                        }
                    }
                } catch (SQLException ex) {
                    LOG.warning("Could not check/migrate banid column: " + ex.getMessage());
                }

                LOG.info("ChanServ database schema and tables successfully ensured");
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    LOG.severe("Failed to create ChanServ tables: " + ex.getMessage());
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("COMMIT")) {
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
        }
    }

    public void commitTransaction() throws SQLException {
        try (Connection conn = getConnection()) {
            conn.commit();
            conn.setAutoCommit(true);
        }
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT flags FROM " + USER_TABLE + " WHERE LOWER(username) = LOWER(?);")) {
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT flags, username FROM " + USER_TABLE + " WHERE flags > 4;")) {
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        dat.put(resultset.getString("username"), resultset.getInt("flags"));
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT lastauth FROM " + USER_TABLE + " WHERE LOWER(username) = LOWER(?);")) {
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
                    initializeConnectionPool();
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
        // Use the secure authenticateUser method that handles pwd field
        return authenticateUser(nick, password);
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

    public String getUserField(String field, String nick) {
        // Map password to pwd for secure password retrieval
        String actualField = field.equals("password") ? "pwd" : field;
        
        if (!isValidUserColumn(actualField)) {
            throw new IllegalArgumentException("Invalid key: " + field);
        }
        String sql = "SELECT " + actualField + " FROM " + USER_TABLE + " WHERE LOWER(username) = LOWER(?)";
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(sql)) {
                statement.setString(1, nick);
                try (var resultset = statement.executeQuery()) {
                    if (resultset.next()) {
                        return resultset.getString(actualField);
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    System.out.println("Database access error, trying reconnect: " + ex.getMessage());
                    initializeConnectionPool();
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
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("CREATE SCHEMA IF NOT EXISTS nickserv;")) {
                    statement.executeUpdate();
                }
                
                // Create reserved_nicks table
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS nickserv.reserved_nicks (" +
                    "nickname VARCHAR(30) PRIMARY KEY, " +
                    "account VARCHAR(30) NOT NULL, " +
                    "reserved_time BIGINT NOT NULL, " +
                    "last_seen BIGINT NOT NULL);"
                )) {
                    statement.executeUpdate();
                }
                
                // Create index for account lookups
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_reserved_account " +
                    "ON nickserv.reserved_nicks(account);"
                )) {
                    statement.executeUpdate();
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "DELETE FROM nickserv.reserved_nicks WHERE LOWER(nickname) = LOWER(?);"
            )) {
                statement.setString(1, nick);
                int rows = statement.executeUpdate();
                return rows > 0;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "SELECT 1 FROM nickserv.reserved_nicks WHERE LOWER(nickname) = LOWER(?);"
            )) {
                statement.setString(1, nick);
                try (var resultset = statement.executeQuery()) {
                    return resultset.next();
                }
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
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
                    initializeConnectionPool();
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
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("CREATE SCHEMA IF NOT EXISTS nickserv;")) {
                    statement.executeUpdate();
                }
                
                // Create failed_attempts table for G-Line tracking
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "UPDATE nickserv.failed_attempts SET glined_until = ? WHERE userhost = ?;"
            )) {
                statement.setLong(1, glineUntil);
                statement.setString(2, userHost);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "DELETE FROM nickserv.failed_attempts WHERE userhost = ?;"
            )) {
                statement.setString(1, userHost);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("INSERT INTO spamscan.lax_channels (channel) VALUES (?);")) {
                statement.setString(1, channel);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("DELETE FROM spamscan.lax_channels WHERE LOWER(channel) = LOWER(?);")) {
                statement.setString(1, channel);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT 1 FROM spamscan.lax_channels WHERE LOWER(channel) = LOWER(?);")) {
                statement.setString(1, channel);
                try (var resultset = statement.executeQuery()) {
                    return resultset.next();
                }
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement("SELECT channel FROM spamscan.lax_channels")) {
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        channels.add(resultset.getString("channel"));
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection()) {
                // First try to insert or update
                try (var statement = conn.prepareStatement(
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
                    try (Connection conn2 = getConnection()) {
                        // Check if entry exists
                        try (var checkStmt = conn2.prepareStatement(
                            "SELECT kill_count FROM spamscan.kill_tracking WHERE userhost = ?;"
                        )) {
                            checkStmt.setString(1, userHost);
                            try (var resultset = checkStmt.executeQuery()) {
                                if (resultset.next()) {
                                    // Update existing entry
                                    killCount = resultset.getInt("kill_count") + 1;
                                    try (var updateStmt = conn2.prepareStatement(
                                        "UPDATE spamscan.kill_tracking SET kill_count = ?, last_kill = ? WHERE userhost = ?;"
                                    )) {
                                        updateStmt.setInt(1, killCount);
                                        updateStmt.setLong(2, currentTime);
                                        updateStmt.setString(3, userHost);
                                        updateStmt.executeUpdate();
                                    }
                                } else {
                                    // Insert new entry
                                    try (var insertStmt = conn2.prepareStatement(
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "UPDATE spamscan.kill_tracking SET glined = TRUE WHERE userhost = ?;"
            )) {
                statement.setString(1, userHost);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
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
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "DELETE FROM spamscan.kill_tracking WHERE last_kill < ? AND glined = FALSE;"
            )) {
                statement.setLong(1, sevenDaysAgo);
                statement.executeUpdate();
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
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
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "DELETE FROM spamscan.kill_tracking WHERE glined = FALSE;"
            )) {
                int deleted = statement.executeUpdate();
                LOG.info("Reset kill tracking: removed " + deleted + " entries");
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
    }

    /**
     * Sets the flags for a channel user
     *
     * @param userId The user ID
     * @param chanId The channel ID
     * @param flags The new flags value
     * @return true if successful
     */
    public boolean setChanUserFlags(long userId, long chanId, int flags) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "UPDATE chanserv.chanusers SET flags = ?, changetime = ? WHERE userid = ? AND channelid = ?"
            )) {
                statement.setInt(1, flags);
                statement.setLong(2, System.currentTimeMillis() / 1000);
                statement.setLong(3, userId);
                statement.setLong(4, chanId);
                int updated = statement.executeUpdate();
                return updated > 0;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Adds a user to a channel's access list
     *
     * @param userId The user ID
     * @param chanId The channel ID
     * @param flags Initial flags value
     * @return true if successful
     */
    public boolean addChanUser(long userId, long chanId, int flags) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "INSERT INTO chanserv.chanusers (userid, channelid, flags, changetime, usetime, info) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (userid, channelid) DO UPDATE SET flags = EXCLUDED.flags, changetime = EXCLUDED.changetime"
            )) {
                long now = System.currentTimeMillis() / 1000;
                statement.setLong(1, userId);
                statement.setLong(2, chanId);
                statement.setInt(3, flags);
                statement.setLong(4, now);
                statement.setLong(5, now);
                statement.setString(6, "ChanServ");
                statement.executeUpdate();
                return true;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Removes a user from a channel's access list
     *
     * @param userId The user ID
     * @param chanId The channel ID
     * @return true if successful
     */
    public boolean deleteChanUser(long userId, long chanId) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "DELETE FROM chanserv.chanusers WHERE userid = ? AND channelid = ?"
            )) {
                statement.setLong(1, userId);
                statement.setLong(2, chanId);
                int deleted = statement.executeUpdate();
                return deleted > 0;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Updates flags for a user on a channel
     *
     * @param userId The user ID
     * @param chanId The channel ID
     * @param flags The new flags value
     * @return true if successful
     */
    public boolean updateChanUserFlags(long userId, long chanId, int flags) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "UPDATE chanserv.chanusers SET flags = ?, changetime = ? WHERE userid = ? AND channelid = ?"
            )) {
                long now = System.currentTimeMillis() / 1000;
                statement.setInt(1, flags);
                statement.setLong(2, now);
                statement.setLong(3, userId);
                statement.setLong(4, chanId);
                int updated = statement.executeUpdate();
                return updated > 0;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Gets all users on a channel's access list
     *
     * @param chanId The channel ID
     * @return List of [username, flags] arrays
     */
    public ArrayList<String[]> getChanUsers(long chanId) {
        ArrayList<String[]> result = new ArrayList<>();
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "SELECT u.username, cu.flags FROM chanserv.chanusers cu " +
                "JOIN chanserv.users u ON cu.userid = u.id " +
                "WHERE cu.channelid = ? ORDER BY cu.flags DESC"
            )) {
                statement.setLong(1, chanId);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        String[] user = new String[2];
                        user[0] = resultset.getString("username");
                        user[1] = String.valueOf(resultset.getInt("flags"));
                        result.add(user);
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return result;
    }

    /**
     * Gets username by user ID
     *
     * @param userId The user ID
     * @return The username or null if not found
     */
    public String getUsernameById(long userId) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "SELECT username FROM " + USER_TABLE + " WHERE id = ?"
            )) {
                statement.setLong(1, userId);
                try (var resultset = statement.executeQuery()) {
                    if (resultset.next()) {
                        return resultset.getString("username");
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return null;
    }

    /**
     * Registers a new channel in the database
     * 
     * @param channelName The channel name (e.g., #test)
     * @param ownerId The user ID of the channel owner
     * @param timestamp The registration timestamp
     * @return true if successful, false otherwise
     */
    public boolean registerChannel(String channelName, long ownerId, long timestamp) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "INSERT INTO chanserv.channels " +
                "(id, name, flags, forcemodes, denymodes, chanlimit, autolimit, banstyle, created, lastactive, statsreset, banduration, founder, addedby, suspendby, suspendtime, chantype, totaljoins, tripjoins, maxusers, tripusers) " +
                "VALUES (?, ?, 0, 8707, 0, 0, 0, 0, ?, ?, ?, 0, ?, ?, 0, 0, 0, 0, 0, 0, 0)"
            )) {
                long newId;
                            try (var seqStmt = conn.prepareStatement("SELECT COALESCE(MAX(id), 0) + 1 FROM chanserv.channels")) {
                    try (var rs = seqStmt.executeQuery()) {
                        rs.next();
                        newId = rs.getLong(1);
                    }
                }

                statement.setLong(1, newId);    // id
                statement.setString(2, channelName); // name
                statement.setLong(3, timestamp); // created
                statement.setLong(4, timestamp); // lastactive
                statement.setLong(5, timestamp); // statsreset
                statement.setLong(6, ownerId);   // founder
                statement.setLong(7, ownerId);   // addedby
                statement.executeUpdate();
                
                // Add owner with full permissions
                long chanId = Long.parseLong(getChannel("id", channelName));
                int ownerFlags = Userflags.QCUFlag.OWNER.value | Userflags.QCUFlag.MASTER.value | 
                                 Userflags.QCUFlag.OP.value | Userflags.QCUFlag.AUTOOP.value;
                addChanUser(ownerId, chanId, ownerFlags);
                return true;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Drops (deletes) a channel from the database
     * 
     * @param chanId The channel ID
     * @return true if successful, false otherwise
     */
    public boolean dropChannel(long chanId) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try {
                // Delete all channel users first
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                    "DELETE FROM chanserv.chanusers WHERE channelid = ?"
                )) {
                    statement.setLong(1, chanId);
                    statement.executeUpdate();
                }
                
                // Delete the channel
                try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                    "DELETE FROM chanserv.channels WHERE id = ?"
                )) {
                    statement.setLong(1, chanId);
                    statement.executeUpdate();
                }
                return true;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Checks if any admin user exists (user with +d flag = 512)
     * @return true if at least one admin exists
     */
    public boolean hasAdminUser() {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "SELECT COUNT(*) FROM chanserv.users WHERE (flags & 512) = 512"
            )) {
                try (var resultset = statement.executeQuery()) {
                    if (resultset.next()) {
                        return resultset.getInt(1) > 0;
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Suspends a channel
     * 
     * @param chanId The channel ID
     * @param reason The suspension reason
     * @return true if successful, false otherwise
     */
    public boolean suspendChannel(long chanId, String reason) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "UPDATE chanserv.channels SET suspendby = ?, suspendreason = ?, suspendtime = ? WHERE id = ?"
            )) {
                statement.setLong(1, 1); // System suspension
                statement.setString(2, reason);
                statement.setLong(3, System.currentTimeMillis() / 1000);
                statement.setLong(4, chanId);
                statement.executeUpdate();
                return true;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Unsuspends a channel
     * 
     * @param chanId The channel ID
     * @return true if successful, false otherwise
     */
    public boolean unsuspendChannel(long chanId) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "UPDATE chanserv.channels SET suspendby = 0, suspendreason = NULL, suspendtime = 0 WHERE id = ?"
            )) {
                statement.setLong(1, chanId);
                statement.executeUpdate();
                return true;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Registers a new user account
     * @param username Username (account name)
     * @param password Plain text password (will be hashed)
     * @param email Email address
     * @return true if successful, false otherwise
     */
    public boolean registerUser(String username, String password, String email) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try {
                int index = getIndex() + 1;
                long now = getCurrentTime();
                String hashedPassword = hashPassword(password, now);
                
                try (Connection conn = getConnection();
                     var statement = conn.prepareStatement(
                        "INSERT INTO chanserv.users (username, created, lastauth, lastemailchng, flags, pwd, generated_pwd, email, lastemail, lastpasschng, id, language, suspendby, suspendexp, suspendtime, lockuntil, lastuserhost, suspendreason, comment, info) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {

                    statement.setString(1, username);
                    statement.setLong(2, now);
                    statement.setLong(3, 0); // lastauth
                    statement.setLong(4, 0); // lastemailchng
                    statement.setInt(5, 4);  // default flags
                    statement.setString(6, hashedPassword);
                    statement.setString(7, password); // generated_pwd - plain password
                    statement.setString(8, email);
                    statement.setString(9, ""); // lastemail
                    statement.setLong(10, 0);  // lastpasschng
                    statement.setInt(11, index); // id
                    statement.setInt(12, 0); // language
                    statement.setInt(13, 0); // suspendby
                    statement.setInt(14, 0); // suspendexp
                    statement.setInt(15, 0); // suspendtime
                    statement.setInt(16, 0); // lockuntil
                    statement.setString(17, ""); // lastuserhost
                    statement.setString(18, ""); // suspendreason
                    statement.setString(19, ""); // comment
                    statement.setString(20, ""); // info

                    int inserted = statement.executeUpdate();
                    return inserted > 0;
                }
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Hash password using SHA256 with salt derived from created timestamp
     * @param password Plain text password
     * @param created Unix timestamp used as salt
     * @return SHA256 hash as hex string
     */
    private String hashPassword(String password, long created) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String saltedPassword = password + created;
            byte[] hash = md.digest(saltedPassword.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            LOG.severe("SHA-256 algorithm not available: " + e.getMessage());
            return null;
        }
    }

    /**
     * Migrate old plaintext passwords to secure hashed format
     * This method can be called manually to migrate all remaining plaintext passwords
     */
    public void migratePasswordsToSecure() {
        int tries = 0;
        int migrated = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var selectStmt = conn.prepareStatement(
                    "SELECT id, username, password, created FROM chanserv.users WHERE password IS NOT NULL AND pwd IS NULL")) {
                try (var resultSet = selectStmt.executeQuery()) {
                    while (resultSet.next()) {
                        long id = resultSet.getLong("id");
                        String oldPassword = resultSet.getString("password");
                        long created = resultSet.getLong("created");

                        if (oldPassword != null && !oldPassword.isEmpty()) {
                            String hashedPassword = hashPassword(oldPassword, created);
                            if (hashedPassword != null) {
                                try (var updateStmt = conn.prepareStatement(
                                        "UPDATE chanserv.users SET pwd = ?, password = NULL WHERE id = ?")) {
                                    updateStmt.setString(1, hashedPassword);
                                    updateStmt.setLong(2, id);
                                    updateStmt.executeUpdate();
                                    migrated++;
                                }
                            }
                        }
                    }
                }
                if (migrated > 0) {
                    LOG.info("Migrated " + migrated + " passwords to secure format");
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    LOG.warning("Failed to migrate passwords: " + ex.getMessage());
                }
            }
            tries++;
        }
    }

    /**
     * Authenticates a user with username and password
     * @param username Username
     * @param password Password to verify
     * @return true if password matches, false otherwise
     */
    public boolean authenticateUser(String username, String password) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "SELECT pwd, new_pwd, password, created FROM chanserv.users WHERE LOWER(username) = LOWER(?) LIMIT 1"
            )) {
                statement.setString(1, username);
                try (var resultset = statement.executeQuery()) {
                    if (resultset.next()) {
                        String storedHash = resultset.getString("pwd");
                        String newHash = resultset.getString("new_pwd");
                        String oldPassword = resultset.getString("password");
                        long created = resultset.getLong("created");

                        // Check if user is trying to use new password
                        if (newHash != null && !newHash.isEmpty()) {
                            String inputHash = hashPassword(password, created);
                            if (inputHash != null && inputHash.equals(newHash)) {
                                // Activate new password: new_pwd -> pwd, clear new_pwd, reset_token and generated_pwd
                                try (var updateStmt = conn.prepareStatement(
                                        "UPDATE chanserv.users SET pwd = new_pwd, new_pwd = NULL, reset_token = NULL, generated_pwd = NULL WHERE LOWER(username) = LOWER(?)")) {
                                    updateStmt.setString(1, username);
                                    updateStmt.executeUpdate();
                                    LOG.info("Activated new password for user: " + username);
                                }
                                return true;
                            }
                        }

                        // Check old (current) password
                        if (storedHash != null && !storedHash.isEmpty()) {
                            String inputHash = hashPassword(password, created);
                            return inputHash != null && inputHash.equals(storedHash);
                        }
                        
                        // Fallback to old password (for migration)
                        if (oldPassword != null && password.equals(oldPassword)) {
                            // Auto-migrate this password on successful auth and delete plaintext
                            String hashedPassword = hashPassword(password, created);
                            if (hashedPassword != null) {
                                try (var updateStmt = conn.prepareStatement(
                                        "UPDATE chanserv.users SET pwd = ?, password = NULL WHERE LOWER(username) = LOWER(?)")) {
                                    updateStmt.setString(1, hashedPassword);
                                    updateStmt.setString(2, username);
                                    updateStmt.executeUpdate();
                                    LOG.info("Auto-migrated password for user: " + username + " and deleted plaintext password");
                                }
                            }
                            return true;
                        }
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Delete all remaining plaintext passwords from the database
     * This should be called after all users have been migrated to ensure no plaintext passwords remain
     * WARNING: Only call this after ensuring all passwords have been properly migrated to hashed format
     * @return Number of plaintext passwords deleted
     */
    public int deleteAllPlaintextPasswords() {
        int tries = 0;
        int deleted = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                    "UPDATE chanserv.users SET password = NULL WHERE password IS NOT NULL AND pwd IS NOT NULL")) {
                deleted = statement.executeUpdate();
                if (deleted > 0) {
                    LOG.warning("Deleted " + deleted + " plaintext passwords from database (users already have hashed passwords)");
                } else {
                    LOG.info("No plaintext passwords found - database is clean");
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    LOG.severe("Failed to delete plaintext passwords: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return deleted;
    }

    /**
     * Updates a user's password
     * @param username Username
     * @param newPassword New password
     * @return true if successful, false otherwise
     */
    public boolean updateUserPassword(String username, String newPassword) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try {
                // Get user's created timestamp for salt
                long created = 0;
                try (Connection conn = getConnection();
                     var selectStmt = conn.prepareStatement(
                    "SELECT created FROM chanserv.users WHERE username = ?"
                )) {
                    selectStmt.setString(1, username);
                    try (var rs = selectStmt.executeQuery()) {
                        if (rs.next()) {
                            created = rs.getLong("created");
                        }
                    }
                }
                
                if (created == 0) {
                    LOG.warning("Could not find user for password update: " + username);
                    return false;
                }
                
                // Hash the new password
                String hashedPassword = hashPassword(newPassword, created);
                
                // Generate reset token
                String resetToken = java.util.UUID.randomUUID().toString().replace("-", "");
                
                // Store new password in new_pwd (keeps old pwd active until new one is used)
                try (Connection conn = getConnection();
                     var updateStmt = conn.prepareStatement(
                    "UPDATE chanserv.users SET new_pwd = ?, generated_pwd = ?, reset_token = ?, password = NULL WHERE username = ?"
                )) {
                    updateStmt.setString(1, hashedPassword);
                    updateStmt.setString(2, newPassword); // Store plain password in generated_pwd
                    updateStmt.setString(3, resetToken);
                    updateStmt.setString(4, username);
                    updateStmt.executeUpdate();
                    LOG.info("Password update pending for user: " + username + " (reset token: " + resetToken + ")");
                    return true;
                }
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    LOG.severe("Failed to update password: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Get the reset token for a user
     * @param username Username
     * @return Reset token or null if not found
     */
    public String getResetToken(String username) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var stmt = conn.prepareStatement(
                    "SELECT reset_token FROM chanserv.users WHERE username = ?"
            )) {
                stmt.setString(1, username);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("reset_token");
                    }
                }
                return null;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    LOG.severe("Failed to get reset token: " + ex.getMessage());
                }
            }
            tries++;
        }
        return null;
    }

    /**
     * Reset password change using reset token (discards new_pwd, keeps old pwd)
     * @param username Username
     * @param token Reset token
     * @return true if successful, false otherwise
     */
    public boolean resetPasswordWithToken(String username, String token) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var stmt = conn.prepareStatement(
                    "UPDATE chanserv.users SET new_pwd = NULL, reset_token = NULL, generated_pwd = NULL " +
                    "WHERE username = ? AND reset_token = ?"
            )) {
                stmt.setString(1, username);
                stmt.setString(2, token);
                int updated = stmt.executeUpdate();
                if (updated > 0) {
                    LOG.info("Password change cancelled for user: " + username);
                    return true;
                } else {
                    LOG.warning("Invalid reset token for user: " + username);
                    return false;
                }
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    LOG.severe("Failed to reset password: " + ex.getMessage());
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Clear pending password change (removes new_pwd, reset_token, generated_pwd)
     * Used when user cancels a password change via email verification
     * @param username Username
     * @return true if successful, false otherwise
     */
    public boolean clearPendingPasswordChange(String username) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var stmt = conn.prepareStatement(
                    "UPDATE chanserv.users SET new_pwd = NULL, reset_token = NULL, generated_pwd = NULL " +
                    "WHERE LOWER(username) = LOWER(?)"
            )) {
                stmt.setString(1, username);
                int updated = stmt.executeUpdate();
                if (updated > 0) {
                    LOG.info("Pending password change cleared for user: " + username);
                    return true;
                } else {
                    LOG.warning("User not found for clearing password change: " + username);
                    return false;
                }
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    LOG.severe("Failed to clear pending password change: " + ex.getMessage());
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Deletes a user account
     * @param username Username
     * @return true if successful, false otherwise
     */
    public boolean deleteUser(String username) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "DELETE FROM chanserv.users WHERE username = ?"
            )) {
                statement.setString(1, username);
                int deleted = statement.executeUpdate();
                return deleted > 0;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Adds a GLine (global ban)
     * @param mask ident@host mask
     * @param reason Ban reason
     * @param setby Oper account who set it
     * @param expiresIn Expiry time in seconds (0 = permanent)
     * @return true if successful
     */
    public boolean addGline(String mask, String reason, String setby, long expiresIn) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "INSERT INTO operserv.glines (mask, reason, setby, created, expires) VALUES (?, ?, ?, ?, ?)"
            )) {
                long now = System.currentTimeMillis() / 1000;
                statement.setString(1, mask);
                statement.setString(2, reason);
                statement.setString(3, setby);
                statement.setLong(4, now);
                statement.setLong(5, expiresIn > 0 ? now + expiresIn : 0);
                int inserted = statement.executeUpdate();
                return inserted > 0;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Removes a GLine
     * @param mask ident@host mask
     * @return true if successful
     */
    public boolean removeGline(String mask) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "DELETE FROM operserv.glines WHERE mask = ?"
            )) {
                statement.setString(1, mask);
                int deleted = statement.executeUpdate();
                return deleted > 0;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Removes expired GLines from database
     * @return Number of GLines deleted
     */
    public int removeExpiredGlines() {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "DELETE FROM operserv.glines WHERE expires > 0 AND expires < ?"
            )) {
                long now = System.currentTimeMillis() / 1000;
                statement.setLong(1, now);
                int deleted = statement.executeUpdate();
                return deleted;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return 0;
    }

    /**
     * Gets active GLines
     * @param limit Maximum number to return
     * @return List of GLine data [mask, reason, setby, created, expires]
     */
    public ArrayList<String[]> getGlines(int limit) {
        ArrayList<String[]> glines = new ArrayList<>();
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "SELECT mask, reason, setby, created, expires FROM operserv.glines ORDER BY created DESC LIMIT ?"
            )) {
                statement.setInt(1, limit);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        String[] data = new String[5];
                        data[0] = resultset.getString("mask");
                        data[1] = resultset.getString("reason");
                        data[2] = resultset.getString("setby");
                        data[3] = String.valueOf(resultset.getLong("created"));
                        data[4] = String.valueOf(resultset.getLong("expires"));
                        glines.add(data);
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return glines;
    }

    /**
     * Gets all active GLines (no limit)
     * @return List of GLine data [mask, reason, setby, created, expires]
     */
    public ArrayList<String[]> getAllGlines() {
        ArrayList<String[]> glines = new ArrayList<>();
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "SELECT mask, reason, setby, created, expires FROM operserv.glines ORDER BY created DESC"
            )) {
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        String[] data = new String[5];
                        data[0] = resultset.getString("mask");
                        data[1] = resultset.getString("reason");
                        data[2] = resultset.getString("setby");
                        data[3] = String.valueOf(resultset.getLong("created"));
                        data[4] = String.valueOf(resultset.getLong("expires"));
                        glines.add(data);
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return glines;
    }

    /**
     * Adds a TrustCheck allow rule
     * @param mask Rule mask, supports wildcards. Typical format: ident@ip (e.g. *admin*@192.0.2.*)
     * @param setby Oper account who set it
     * @return true if inserted, false otherwise (e.g. already exists)
     */
    public boolean addTrust(String mask, String setby) {
        return addTrust(mask, setby, 0, false);
    }

    /**
     * Adds a TrustCheck allow rule
     * @param mask Rule mask
     * @param setby Oper account who set it
     * @param maxConn Maximum simultaneous connections per IP (0 = unlimited)
     * @param requireIdent Whether ident must be present/non-empty
     * @return true if inserted, false otherwise
     */
    public boolean addTrust(String mask, String setby, int maxConn, boolean requireIdent) {
        return addTrust(mask, setby, maxConn, requireIdent, 0);
    }

    /**
     * Adds a TrustCheck allow rule
     * @param mask Rule mask
     * @param setby Oper account who set it
     * @param maxConn Maximum simultaneous connections per IP (0 = unlimited)
     * @param requireIdent Whether ident must be present/non-empty
     * @param maxIdentsPerHost Maximum identical idents per host (0 = unlimited)
     * @return true if inserted, false otherwise
     */
    public boolean addTrust(String mask, String setby, int maxConn, boolean requireIdent, int maxIdentsPerHost) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                    "INSERT INTO operserv.trusts (mask, setby, created, maxconn, require_ident, max_idents_per_host) VALUES (?, ?, ?, ?, ?, ?)")) {
                long now = System.currentTimeMillis() / 1000;
                statement.setString(1, mask);
                statement.setString(2, setby);
                statement.setLong(3, now);
                statement.setInt(4, Math.max(0, maxConn));
                statement.setBoolean(5, requireIdent);
                statement.setInt(6, Math.max(0, maxIdentsPerHost));
                int inserted = statement.executeUpdate();
                return inserted > 0;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Removes a TrustCheck allow rule
     * @param mask Rule mask
     * @return true if deleted, false if not found
     */
    public boolean removeTrust(String mask) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                    "DELETE FROM operserv.trusts WHERE mask = ?")) {
                statement.setString(1, mask);
                int deleted = statement.executeUpdate();
                return deleted > 0;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Fetches a TrustCheck allow rule by exact mask.
     * @param mask Rule mask
     * @return Rule data [mask, maxconn, require_ident, max_idents_per_host] or null if not found
     */
    public String[] getTrustRule(String mask) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                    "SELECT mask, maxconn, require_ident, max_idents_per_host FROM operserv.trusts WHERE mask = ?")) {
                statement.setString(1, mask);
                try (var resultset = statement.executeQuery()) {
                    if (resultset.next()) {
                        String[] row = new String[4];
                        row[0] = resultset.getString("mask");
                        row[1] = String.valueOf(resultset.getInt("maxconn"));
                        row[2] = String.valueOf(resultset.getBoolean("require_ident"));
                        row[3] = String.valueOf(resultset.getInt("max_idents_per_host"));
                        return row;
                    }
                }
                return null;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return null;
    }

    /**
     * Updates an existing TrustCheck allow rule.
     * @param mask Rule mask
     * @param maxConn Maximum simultaneous connections per IP (0 = unlimited)
     * @param requireIdent Whether ident must be present/non-empty
     * @return true if updated, false if not found
     */
    public boolean updateTrust(String mask, int maxConn, boolean requireIdent) {
        return updateTrust(mask, maxConn, requireIdent, 0);
    }

    /**
     * Updates an existing TrustCheck allow rule.
     * @param mask Rule mask
     * @param maxConn Maximum simultaneous connections per IP (0 = unlimited)
     * @param requireIdent Whether ident must be present/non-empty
     * @param maxIdentsPerHost Maximum identical idents per host (0 = unlimited)
     * @return true if updated, false if not found
     */
    public boolean updateTrust(String mask, int maxConn, boolean requireIdent, int maxIdentsPerHost) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                    "UPDATE operserv.trusts SET maxconn = ?, require_ident = ?, max_idents_per_host = ? WHERE mask = ?")) {
                statement.setInt(1, Math.max(0, maxConn));
                statement.setBoolean(2, requireIdent);
                statement.setInt(3, Math.max(0, maxIdentsPerHost));
                statement.setString(4, mask);
                int updated = statement.executeUpdate();
                return updated > 0;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Lists TrustCheck allow rules
     * @param limit Maximum number to return
     * @return List of rule data [mask, setby, created, maxconn, require_ident, max_idents_per_host]
     */
    public ArrayList<String[]> getTrusts(int limit) {
        ArrayList<String[]> trusts = new ArrayList<>();
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                    "SELECT mask, setby, created, maxconn, require_ident, max_idents_per_host FROM operserv.trusts ORDER BY created DESC LIMIT ?")) {
                statement.setInt(1, limit);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        String[] data = new String[6];
                        data[0] = resultset.getString("mask");
                        data[1] = resultset.getString("setby");
                        data[2] = String.valueOf(resultset.getLong("created"));
                        data[3] = String.valueOf(resultset.getInt("maxconn"));
                        data[4] = String.valueOf(resultset.getBoolean("require_ident"));
                        data[5] = String.valueOf(resultset.getInt("max_idents_per_host"));
                        trusts.add(data);
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return trusts;
    }

    /**
     * Gets all TrustCheck rules needed for evaluation
     * @return List of rule data [mask, maxconn, require_ident, max_idents_per_host]
     */
    public ArrayList<String[]> getAllTrustRules() {
        ArrayList<String[]> rules = new ArrayList<>();
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                    "SELECT mask, maxconn, require_ident, max_idents_per_host FROM operserv.trusts ORDER BY created DESC")) {
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        String[] row = new String[4];
                        row[0] = resultset.getString("mask");
                        row[1] = String.valueOf(resultset.getInt("maxconn"));
                        row[2] = String.valueOf(resultset.getBoolean("require_ident"));
                        row[3] = String.valueOf(resultset.getInt("max_idents_per_host"));
                        rules.add(row);
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return rules;
    }

    /**
     * Gets all TrustCheck allow rule masks (no limit)
     * @return List of masks
     */
    public ArrayList<String> getAllTrustMasks() {
        ArrayList<String> masks = new ArrayList<>();
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                    "SELECT mask FROM operserv.trusts ORDER BY created DESC")) {
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        masks.add(resultset.getString("mask"));
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return masks;
    }

    /**
     * Counts TrustCheck allow rules
     * @return number of stored trust rules
     */
    public int getTrustCount() {
        int count = 0;
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                    "SELECT COUNT(*) FROM operserv.trusts")) {
                try (var resultset = statement.executeQuery()) {
                    if (resultset.next()) {
                        count = resultset.getInt(1);
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return count;
    }

    /**
     * Adds a channel ban
     * @param channelId Channel ID
     * @param userId User ID who set the ban
     * @param hostmask Ban mask (e.g., *!*@*.example.com)
     * @param expiry Unix timestamp when ban expires (0 = permanent)
     * @param reason Ban reason
     * @return true if successful
     */
    public boolean addChannelBan(long channelId, long userId, String hostmask, long expiry, String reason) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "INSERT INTO chanserv.bans (channelid, userid, hostmask, expiry, reason) VALUES (?, ?, ?, ?, ?)"
            )) {
                statement.setLong(1, channelId);
                statement.setLong(2, userId);
                statement.setString(3, hostmask);
                statement.setLong(4, expiry);
                statement.setString(5, reason);
                int inserted = statement.executeUpdate();
                return inserted > 0;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Removes a channel ban by ban ID
     * @param banId Ban ID to remove
     * @return true if successful
     */
    public boolean removeChannelBan(long banId) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "DELETE FROM chanserv.bans WHERE banid = ?"
            )) {
                statement.setLong(1, banId);
                int deleted = statement.executeUpdate();
                return deleted > 0;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Removes a channel ban by hostmask
     * @param channelId Channel ID
     * @param hostmask Ban mask to remove
     * @return true if successful
     */
    public boolean removeChannelBanByMask(long channelId, String hostmask) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "DELETE FROM chanserv.bans WHERE channelid = ? AND hostmask = ?"
            )) {
                statement.setLong(1, channelId);
                statement.setString(2, hostmask);
                int deleted = statement.executeUpdate();
                return deleted > 0;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return false;
    }

    /**
     * Gets active channel bans (not expired)
     * @param channelId Channel ID
     * @return List of ban data [banid, hostmask, expiry, reason, setby_username]
     */
    public ArrayList<String[]> getChannelBans(long channelId) {
        ArrayList<String[]> bans = new ArrayList<>();
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "SELECT b.banid, b.hostmask, b.expiry, b.reason, u.username " +
                "FROM chanserv.bans b " +
                "LEFT JOIN chanserv.users u ON b.userid = u.id " +
                "WHERE b.channelid = ? AND (b.expiry = 0 OR b.expiry > ?) " +
                "ORDER BY b.banid DESC"
            )) {
                long now = System.currentTimeMillis() / 1000;
                statement.setLong(1, channelId);
                statement.setLong(2, now);
                try (var resultset = statement.executeQuery()) {
                    while (resultset.next()) {
                        String[] data = new String[5];
                        data[0] = String.valueOf(resultset.getLong("banid"));
                        data[1] = resultset.getString("hostmask");
                        data[2] = String.valueOf(resultset.getLong("expiry"));
                        data[3] = resultset.getString("reason");
                        data[4] = resultset.getString("username");
                        bans.add(data);
                    }
                }
                break;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return bans;
    }

    /**
     * Removes expired channel bans
     * @return Number of bans deleted
     */
    public int cleanupExpiredBans() {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection();
                 var statement = conn.prepareStatement(
                "DELETE FROM chanserv.bans WHERE expiry > 0 AND expiry < ?"
            )) {
                long now = System.currentTimeMillis() / 1000;
                statement.setLong(1, now);
                int deleted = statement.executeUpdate();
                return deleted;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return 0;
    }

    /**
     * Checks if a user host matches a ban mask
     * Supports * (any characters) and ? (single character) wildcards
     * @param userHost User's host/mask (e.g., *!*@user.suffix)
     * @param banMask Ban mask pattern (e.g., *!*@*.example.com)
     * @return true if the user host matches the ban mask
     */
    public boolean checkBanMatch(String userHost, String banMask) {
        if (userHost == null || banMask == null) {
            return false;
        }
        // Convert wildcard pattern to regex
        String regex = banMask
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".");
        return userHost.matches(regex);
    }

    /**
     * Deletes inactive ChanServ users who haven't authenticated in the specified number of days
     * Excludes privileged accounts (OPER, STAFF, ADMIN, DEV) from deletion
     * @param inactiveDays Number of days of inactivity threshold
     * @param currentLoggedInUsers Set of currently logged in usernames (lowercase)
     * @return Number of users deleted
     */
    public int deleteInactiveChanServUsers(int inactiveDays, java.util.Set<String> currentLoggedInUsers) {
        int tries = 0;
        int deletedCount = 0;

        while (tries < 2) {
            ensureConnection();
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);

                try {
                    // Calculate the threshold timestamp (current time - inactiveDays)
                    long thresholdTime = (System.currentTimeMillis() / 1000) - (inactiveDays * 24L * 60L * 60L);

                    // Find inactive users (exclude privileged accounts: OPER, STAFF, ADMIN, DEV)
                    java.util.List<String> inactiveUsers = new java.util.ArrayList<>();
                    try (var selectStmt = conn.prepareStatement(
                        "SELECT username, flags FROM chanserv.users WHERE lastauth < ? AND lastauth > 0"
                    )) {
                        selectStmt.setLong(1, thresholdTime);
                        try (var rs = selectStmt.executeQuery()) {
                            while (rs.next()) {
                                String username = rs.getString("username");
                                int flags = rs.getInt("flags");
                                
                                // Skip if user is currently logged in
                                if (currentLoggedInUsers.contains(username.toLowerCase())) {
                                    continue;
                                }
                                
                                // Skip privileged accounts: OPER (0x0020), STAFF (0x0008), ADMIN (0x0200), DEV (0x0040)
                                boolean isPrivileged = (flags & 0x0020) != 0 || // OPER
                                                      (flags & 0x0008) != 0 || // STAFF
                                                      (flags & 0x0200) != 0 || // ADMIN
                                                      (flags & 0x0040) != 0;   // DEV
                                
                                if (!isPrivileged) {
                                    inactiveUsers.add(username);
                                }
                            }
                        }
                    }

                    // Delete inactive users and their channel access
                    if (!inactiveUsers.isEmpty()) {
                        // First, delete channel access entries
                        try (var deleteChanUsersStmt = conn.prepareStatement(
                            "DELETE FROM chanserv.chanusers WHERE userid IN " +
                            "(SELECT id FROM chanserv.users WHERE username = ?)"
                        )) {
                            for (String username : inactiveUsers) {
                                deleteChanUsersStmt.setString(1, username);
                                deleteChanUsersStmt.executeUpdate();
                            }
                        }

                        // Then, delete the users themselves
                        try (var deleteUsersStmt = conn.prepareStatement(
                            "DELETE FROM chanserv.users WHERE username = ?"
                        )) {
                            for (String username : inactiveUsers) {
                                deleteUsersStmt.setString(1, username);
                                deleteUsersStmt.executeUpdate();
                                deletedCount++;
                                LOG.info("Deleted inactive ChanServ user: " + username);
                            }
                        }
                    }

                    conn.commit();

                    if (deletedCount > 0) {
                        LOG.info("Deleted " + deletedCount + " inactive ChanServ users (inactive > " + inactiveDays + " days)");
                    }

                    return deletedCount;

                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }

            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    LOG.severe("Failed to delete inactive ChanServ users: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return deletedCount;
    }

    /**
     * Gets the creation timestamp for a channel from the database
     * @param channelName The name of the channel
     * @return The timestamp when the channel was created, or 0 if not found
     */
    public long getChannelTimestamp(String channelName) {
        int tries = 0;
        while (tries < 2) {
            ensureConnection();
            String sql = "SELECT created FROM chanserv.channels WHERE LOWER(name) = LOWER(?)";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, channelName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("created");
                    }
                }
                return 0;
            } catch (SQLException ex) {
                if (tries == 0) {
                    LOG.warning(RECONNECT_MSG + ex.getMessage());
                    initializeConnectionPool();
                } else {
                    LOG.severe("Failed to get channel timestamp: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
            tries++;
        }
        return 0;
    }
}

