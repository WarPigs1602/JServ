package net.midiandmore.jserv;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * HelpServ - Help and information service inspired by NewServ helpmod2.
 */
public final class HelpServ extends AbstractModule implements Software {

    private static final int MAX_NOTICE_LINES = 8;
    private static final int MAX_TOPICS = 24;
    private static final int MAX_COMMAND_HELP_LINES = 24;
    private static final int MAX_LIST_USERS = 40;
    private static final int H_PASSIVE = 1 << 0;
    private static final int H_WELCOME = 1 << 1;
    private static final int H_JOINFLOOD_PROTECTION = 1 << 2;
    private static final int H_QUEUE = 1 << 3;
    private static final int H_QUEUE_SPAMMY = 1 << 4;
    private static final int H_QUEUE_MAINTAIN = 1 << 5;
    private static final int H_REPORT = 1 << 6;
    private static final int H_ANTI_IDLE = 1 << 9;
    private static final int H_MAINTAIN_M = 1 << 10;
    private static final int H_MAINTAIN_I = 1 << 11;
    private static final int H_HANDLE_TOPIC = 1 << 12;
    private static final int H_CHANNEL_COMMANDS = 1 << 15;
    private static final int H_REQUIRE_TICKET = 1 << 19;
    private static final int HCHANNEL_CONF_COUNT = 21;
    private static final int H_ACC_ALL_PRIVMSG = 1 << 0;
    private static final int H_ACC_ALL_NOTICE = 1 << 1;
    private static final int H_ACC_NOSPAM = 1 << 2;
    private static final int H_ACC_AUTO_OP = 1 << 3;
    private static final int H_ACC_AUTO_VOICE = 1 << 4;
    private static final int H_ACC_NO_CMD_ERROR = 1 << 5;
    private static final int HACCOUNT_CONF_COUNT = 5;
    private static final int MAX_ACCONF_ARGS = 6;
    private static final String[] ACCOUNT_CONF_NAMES = {
            "Send replies as private messages",
            "Send replies as notices",
            "Do not send verbose messages",
            "Auto-op on join",
            "Auto-voice on join",
            "Suppress unknown command error"
    };

    private volatile TopicNode root = new TopicNode("ROOT", null);
    private final Map<String, TopicNode> userState = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> supportQueue = new ConcurrentHashMap<>();
    private final Map<String, Boolean> queueEnabled = new ConcurrentHashMap<>();
    private final Map<String, Boolean> queueMaintain = new ConcurrentHashMap<>();
    private final Map<String, Integer> queueMaintainTarget = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> channelTickets = new ConcurrentHashMap<>();
    private final Map<String, String> ticketMessages = new ConcurrentHashMap<>();
    private final Map<String, String> globalTerms = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> channelTerms = new ConcurrentHashMap<>();
    private final Map<String, Integer> accountConfig = new ConcurrentHashMap<>();
    private final Map<String, Integer> accountLevels = new ConcurrentHashMap<>();
    private final List<String> accountWriteOrder = new CopyOnWriteArrayList<>();
    private final Map<String, Set<String>> pendingAutoModes = new ConcurrentHashMap<>();
    private final Map<String, String> reportRoutes = new ConcurrentHashMap<>();
    private final Set<String> managedChannels = ConcurrentHashMap.newKeySet();
    private final Map<String, String> channelWelcome = new ConcurrentHashMap<>();
    private final Map<String, Integer> channelConfigFlags = new ConcurrentHashMap<>();
    private final Map<String, List<String>> channelTopicParts = new ConcurrentHashMap<>();
    private final Map<String, Integer> dbChannelFlags = new ConcurrentHashMap<>();
    private final Map<String, BanRecord> globalBans = new ConcurrentHashMap<>();
    private final Map<String, Map<String, BanRecord>> channelBans = new ConcurrentHashMap<>();
    private final Map<String, List<CensorEntry>> channelCensors = new ConcurrentHashMap<>();
    private final Map<String, String> lamerControlProfiles = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> lamerControlProfileValues = new ConcurrentHashMap<>();
    private final Map<String, Integer> idleKickTimeoutSeconds = new ConcurrentHashMap<>();
    private final Map<String, Long> joinFloodControl = new ConcurrentHashMap<>();
    private final Map<String, Long> joinFloodRegOnlyUntil = new ConcurrentHashMap<>();
    private final Map<String, Long> termUsageStats = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> channelTermUsageStats = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> accountChannelStats = new ConcurrentHashMap<>();
    private volatile String serviceNick = "HelpServ";
    private volatile Path sourceFile;
    private volatile Path helpmodDbFile;
    private volatile String dbDefaultChannel;
    private volatile int hstatCycle = 0;

    public HelpServ(JServ jserv, SocketThread socketThread, PrintWriter pw, BufferedReader br) {
        initialize(jserv, socketThread, pw, br);
    }

    @Override
    public String getModuleName() {
        return "HelpServ";
    }

    @Override
    public void handshake(String nick, String servername, String description, String numeric, String identd) {
        if (!enabled) {
            getLogger().log(Level.WARNING, "HelpServ handshake called but module is disabled");
            return;
        }

        String resolvedNick = resolveFromHelpConfig(nick, "nick", "HelpServ");
        String resolvedServername = resolveFromHelpConfig(servername, "servername", "services.example.com");
        String resolvedDescription = resolveFromHelpConfig(description, "description", "Help and Information Service");
        String resolvedIdentd = resolveFromHelpConfig(identd, "identd", "helpserv");

        this.numeric = numeric;
        this.serviceNick = resolvedNick;

        sendText("%s N %s 2 %d %s %s +oikr - %s:%d U]AEB %s%s :%s",
                this.numeric,
                resolvedNick,
                time(),
                resolvedIdentd,
                resolvedServername,
                resolvedNick,
                time(),
                this.numeric,
                getNumericSuffix(),
                resolvedDescription);

            String defaultChannel = resolveDefaultHelpChannel().toLowerCase(Locale.ROOT);
            queueEnabled.put(defaultChannel, false);
            managedChannels.add(defaultChannel);

        reloadTopics();
        reloadHelpmodDb();
        getLogger().log(Level.INFO, "HelpServ registered as {0}", resolvedNick);
    }

    @Override
    public void registerBurstChannels(java.util.HashMap<String, Burst> bursts, String serverNumeric) {
        if (!enabled) {
            return;
        }
        Set<String> channels = new HashSet<>(managedChannels);
        if (channels.isEmpty()) {
            channels.add(resolveDefaultHelpChannel().toLowerCase(Locale.ROOT));
        }
        for (String channel : channels) {
            if (channel == null || channel.isBlank() || !channel.startsWith("#")) {
                continue;
            }
            String key = channel.toLowerCase(Locale.ROOT);
            if (!bursts.containsKey(key)) {
                bursts.put(key, new Burst(channel));
            }
            bursts.get(key).getUsers().add(serverNumeric + getNumericSuffix());
            applyConfiguredModesForChannelOnBurst(key);
            getLogger().log(Level.INFO, "HelpServ registered burst channel: {0}", channel);
        }
    }

    @Override
    public void parseLine(String text) {
        if (!enabled) {
            return;
        }
        processJoinFloodTimeouts();
        processPendingAutoModes();
        String line = text == null ? "" : text.trim();
        if (line.isEmpty()) {
            return;
        }
        String[] elem = line.split(" ");
        if (elem.length < 2) {
            return;
        }

        if (("J".equals(elem[1]) || "C".equals(elem[1])) && elem.length >= 3) {
            handleJoinEvent(elem[0], elem[2]);
            processPendingAutoModes();
            return;
        }

        if ("N".equals(elem[1])) {
            processPendingAutoModes();
            return;
        }

        if ("T".equals(elem[1])) {
            handleTopicEvent(elem, line);
            processPendingAutoModes();
            return;
        }

        if ("M".equals(elem[1])) {
            handleModeEvent(elem);
            processPendingAutoModes();
            return;
        }

        if (elem.length < 4) {
            return;
        }

        if (!"P".equals(elem[1])) {
            return;
        }

        String senderNumeric = elem[0];
        String target = elem[2];
        String myNumeric = numeric + getNumericSuffix();
        if (!elem[3].startsWith(":")) {
            return;
        }

        String message = line.substring(line.indexOf(elem[3]) + 1).trim();
        if (message.isEmpty()) {
            return;
        }

        if (myNumeric.equals(target)) {
            handleHelpCommand(senderNumeric, message, false, null);
            processPendingAutoModes();
            return;
        }

        if (target.startsWith("#") && isManagedChannel(target)) {
            if (!isChannelCommandsEnabled(target)) {
                return;
            }
            String triggered = extractChannelTriggeredCommand(message);
            if (triggered != null && !triggered.isBlank()) {
                handleHelpCommand(senderNumeric, triggered, true, target);
            }
        }
        processPendingAutoModes();
    }

    private void handleJoinEvent(String joiningNumeric, String channelRaw) {
        if (joiningNumeric == null || channelRaw == null || channelRaw.isBlank()) {
            return;
        }
        String myNumeric = numeric + getNumericSuffix();
        if (myNumeric.equals(joiningNumeric)) {
            return;
        }
        String channel = channelRaw.toLowerCase(Locale.ROOT);
        if (!isManagedChannel(channel) || isChannelFlagEnabled(channel, H_PASSIVE)) {
            return;
        }

        Users joiningUser = st != null && st.getUsers() != null ? st.getUsers().get(joiningNumeric) : null;

        int flags = channelConfigFlags.getOrDefault(channel, dbChannelFlags.getOrDefault(channel, 0));
        if ((flags & (H_MAINTAIN_M | H_MAINTAIN_I)) != 0 && hasMaintainModeMismatch(channel, flags)) {
            applyChannelModeCheck(channel, flags);
        }

        if (!applyAcconfJoinModes(channel, joiningNumeric, joiningUser)) {
            queuePendingAutoMode(channel, joiningNumeric);
        }

        if (isChannelFlagEnabled(channel, H_JOINFLOOD_PROTECTION)) {
            handleJoinFloodProtection(channel);
        }

        if (isChannelFlagEnabled(channel, H_QUEUE)) {
            enqueueUser(channel, joiningNumeric);
            if (isChannelFlagEnabled(channel, H_QUEUE_SPAMMY) && !isStaffLike(joiningUser)
                    && !hasAccountConfigFlag(joiningNumeric, H_ACC_NOSPAM)) {
                int pos = queuePosition(channel, joiningNumeric);
                sendNotice(joiningNumeric, "Channel " + channel + " is using a queue system. Your queue position is #" + pos + ". Please wait for your turn.");
            }
        }

        if (isChannelFlagEnabled(channel, H_ANTI_IDLE) && !isChannelFlagEnabled(channel, H_QUEUE)
                && isChannelModeEnabled(channel, 'm') && !isStaffLike(joiningUser)) {
            sendNotice(joiningNumeric, "Channel " + channel + " is currently moderated and anti-idle is active. Please try again later.");
        }

        if (!isWelcomeEnabled(channel)) {
            return;
        }
        String welcome = channelWelcome.get(channel);
        if (welcome == null || welcome.isBlank()) {
            return;
        }
        sendNotice(joiningNumeric, "[" + channel + "] " + welcome);
    }

    private void applyConfiguredModesForChannelOnBurst(String channel) {
        if (channel == null || channel.isBlank() || !channel.startsWith("#")) {
            return;
        }
        if (isChannelFlagEnabled(channel, H_PASSIVE)) {
            return;
        }

        int flags = channelConfigFlags.getOrDefault(channel, dbChannelFlags.getOrDefault(channel, 0));
        if ((flags & (H_MAINTAIN_M | H_MAINTAIN_I)) != 0) {
            applyChannelModeCheck(channel, flags);
        }

        if ((flags & H_QUEUE) != 0) {
            enqueueCurrentChannelUsers(channel);
        }

        if (st == null || st.getChannel() == null || st.getUsers() == null) {
            return;
        }
        Channel channelObj = st.getChannel().get(channel);
        if (channelObj == null || channelObj.getUsers() == null || channelObj.getUsers().isEmpty()) {
            return;
        }

        String myNumeric = numeric + getNumericSuffix();
        for (String userNumeric : channelObj.getUsers()) {
            if (userNumeric == null || userNumeric.isBlank() || myNumeric.equals(userNumeric)) {
                continue;
            }
            Users user = st.getUsers().get(userNumeric);
            if (!applyAcconfJoinModes(channel, userNumeric, user)) {
                queuePendingAutoMode(channel, userNumeric);
            }
        }
    }

    private void handleTopicEvent(String[] elem, String line) {
        if (elem == null || elem.length < 3 || line == null) {
            return;
        }
        String channel = elem[2] == null ? "" : elem[2].toLowerCase(Locale.ROOT);
        if (!channel.startsWith("#") || !isManagedChannel(channel)) {
            return;
        }
        if (!isChannelFlagEnabled(channel, H_HANDLE_TOPIC) || isChannelFlagEnabled(channel, H_PASSIVE)) {
            return;
        }

        String myNumeric = numeric + getNumericSuffix();
        String setterNumeric = elem[0];
        if (myNumeric.equals(setterNumeric)) {
            return;
        }

        String topic = extractTrailingText(line);
        if (topic == null) {
            topic = "";
        }

        Users setter = st != null && st.getUsers() != null ? st.getUsers().get(setterNumeric) : null;
        if (getUserCommandLevel(setter) >= 5) {
            List<String> parts = channelTopicParts.computeIfAbsent(channel, c -> new ArrayList<>());
            parts.clear();
            if (!topic.isBlank()) {
                parts.add(topic);
            }
            return;
        }

        List<String> stored = channelTopicParts.get(channel);
        if (stored == null || stored.isEmpty()) {
            return;
        }
        String restoredTopic = String.join(" | ", stored);
        if (restoredTopic.isBlank()) {
            return;
        }
        sendText("%s%s T %s %d :%s", numeric, getNumericSuffix(), channel, time(), restoredTopic);
    }

    private void handleModeEvent(String[] elem) {
        if (elem == null || elem.length < 4) {
            return;
        }
        String channel = elem[2] == null ? "" : elem[2].toLowerCase(Locale.ROOT);
        if (!channel.startsWith("#") || !isManagedChannel(channel)) {
            return;
        }

        int modeIndex = -1;
        for (int i = 3; i < elem.length; i++) {
            String token = elem[i];
            if (token != null && !token.isBlank() && (token.startsWith("+") || token.startsWith("-"))) {
                modeIndex = i;
                break;
            }
        }
        if (modeIndex < 0) {
            return;
        }

        String modeSpec = elem[modeIndex] == null ? "" : elem[modeIndex];
        if (modeSpec.isBlank()) {
            return;
        }

        int devoiceCount = applyModeEventToLocalState(channel, modeSpec, elem, modeIndex + 1);

        int flags = channelConfigFlags.getOrDefault(channel, dbChannelFlags.getOrDefault(channel, 0));
        if ((flags & (H_MAINTAIN_M | H_MAINTAIN_I)) != 0) {
            applyChannelModeCheck(channel, flags);
        }

        if (devoiceCount <= 0) {
            return;
        }

        if (!isChannelFlagEnabled(channel, H_QUEUE) || !isChannelFlagEnabled(channel, H_QUEUE_MAINTAIN)
                || isChannelFlagEnabled(channel, H_PASSIVE)) {
            return;
        }

        int autoTarget = queueMaintainTarget.getOrDefault(channel, queueMaintain.getOrDefault(channel, false) ? 1 : 0);
        if (autoTarget <= 0) {
            return;
        }

        int currentlyHelped = countQueuedUsersOffQueue(channel);
        int amount = Math.min(25, Math.max(0, autoTarget - currentlyHelped));
        if (amount <= 0) {
            return;
        }

        String responsible = nickOrNumeric(elem[0]);
        int invited = 0;
        for (int i = 0; i < amount; i++) {
            String next = pollNextQueueUser(channel);
            if (next == null) {
                break;
            }
            sendText("%s%s I %s %s", numeric, getNumericSuffix(), next, channel);
            sendNotice(next, "It is now your turn for support in " + channel + ".");
            invited++;
        }
        if (invited > 0) {
            sendChannelMessage(channel, "user" + (invited == 1 ? " " : "s ") + invited
                    + ": Please state your questions on this channel and direct them to " + responsible);
        }
    }

    private int applyModeEventToLocalState(String channel, String modeSpec, String[] elem, int firstParamIndex) {
        Channel channelObj = st != null && st.getChannel() != null ? st.getChannel().get(channel) : null;
        boolean add = true;
        int paramIndex = firstParamIndex;
        int devoiceCount = 0;

        for (int i = 0; i < modeSpec.length(); i++) {
            char mode = modeSpec.charAt(i);
            if (mode == '+') {
                add = true;
                continue;
            }
            if (mode == '-') {
                add = false;
                continue;
            }

            if (!modeTakesParameter(mode, add)) {
                updateLocalChannelMode(channel, mode, add);
                continue;
            }

            if (paramIndex >= elem.length) {
                continue;
            }

            String targetNumeric = elem[paramIndex++];
            if (targetNumeric == null || targetNumeric.isBlank()) {
                continue;
            }

            switch (mode) {
                case 'v':
                    if (add) {
                        if (channelObj != null && !channelObj.getVoice().contains(targetNumeric)) {
                            channelObj.addVoice(targetNumeric);
                        }
                    } else {
                        Users targetUser = st != null && st.getUsers() != null ? st.getUsers().get(targetNumeric) : null;
                        if (!isStaffLike(targetUser) && !(numeric + getNumericSuffix()).equals(targetNumeric)) {
                            devoiceCount++;
                        }
                        if (channelObj != null) {
                            channelObj.removeVoice(targetNumeric);
                        }
                    }
                    break;
                case 'j':
                    updateLocalChannelMode(channel, mode, add);
                    break;
                case 'o':
                    if (channelObj != null) {
                        if (add) {
                            if (!channelObj.getOp().contains(targetNumeric)) {
                                channelObj.addOp(targetNumeric);
                            }
                        } else {
                            channelObj.removeOp(targetNumeric);
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        return devoiceCount;
    }

    private boolean modeTakesParameter(char mode, boolean adding) {
        switch (mode) {
            case 'o':
            case 'v':
            case 'b':
            case 'k':
            case 'q':
            case 'a':
            case 'h':
            case 'I':
            case 'e':
            case 'j':
                return true;
            case 'l':
                return adding;
            default:
                return false;
        }
    }

    private String extractTrailingText(String line) {
        if (line == null) {
            return null;
        }
        int idx = line.indexOf(" :");
        if (idx < 0 || idx + 2 >= line.length()) {
            return null;
        }
        return line.substring(idx + 2);
    }

    private void handleHelpCommand(String senderNumeric, String rawMessage, boolean fromChannel, String triggerChannel) {
        String message = sanitizeInput(rawMessage);
        if (message.isBlank()) {
            return;
        }

        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        int actorLevel = getUserCommandLevel(actor);

        String termReplyChannel = fromChannel && triggerChannel != null && triggerChannel.startsWith("#")
                ? triggerChannel.toLowerCase(Locale.ROOT)
                : null;

        if (isNumericSelection(message)) {
            handleHelpSelection(senderNumeric, Integer.parseInt(message));
            return;
        }

        String[] parts = message.split("\\s+");
        String command = parts[0].toUpperCase(Locale.ROOT);
        String args = message.length() > parts[0].length() ? message.substring(parts[0].length()).trim() : "";

        String aclCommand = normalizeCommandForAcl(command);
        CommandDef aclDef = commandDef(aclCommand);
        if (aclDef != null && actorLevel < aclDef.minLevel) {
            sendNotice(senderNumeric, "Access denied: " + commandLevelName(aclDef.minLevel) + " level required for " + aclCommand + ".");
            return;
        }

        switch (command) {
            case "HELP":
                handleHelp(senderNumeric, args);
                break;
            case "TOPICS":
            case "MENU":
                handleTopics(senderNumeric, args);
                break;
            case "SEARCH":
            case "FIND":
                handleSearch(senderNumeric, args);
                break;
            case "TERM":
                handleTerm(senderNumeric, args, termReplyChannel, true);
                break;
            case "?":
                handleTerm(senderNumeric, buildTermFindArgs(args), termReplyChannel, false);
                break;
            case "?+":
                if (args == null || args.isBlank()) {
                    handleQueue(senderNumeric, queueShortcutArgs("NEXT", "", termReplyChannel));
                } else {
                    handleTerm(senderNumeric, buildTermFindArgs(args), termReplyChannel, false);
                    handleQueue(senderNumeric, queueShortcutArgs("NEXT", "", termReplyChannel));
                }
                break;
            case "?-":
                if (args == null || args.isBlank()) {
                    handleQueue(senderNumeric, queueShortcutArgs("DONE", "", termReplyChannel));
                } else {
                    handleTerm(senderNumeric, buildTermFindArgs(args), termReplyChannel, false);
                    handleQueue(senderNumeric, queueShortcutArgs("DONE", args, termReplyChannel));
                }
                break;
            case "QUESTIONMARK":
                handleTerm(senderNumeric, buildTermFindArgs(args), termReplyChannel, false);
                break;
            case "QUESTIONMARKPLUS":
                if (args == null || args.isBlank()) {
                    handleQueue(senderNumeric, queueShortcutArgs("NEXT", "", termReplyChannel));
                } else {
                    handleTerm(senderNumeric, buildTermFindArgs(args), termReplyChannel, false);
                    handleQueue(senderNumeric, queueShortcutArgs("NEXT", "", termReplyChannel));
                }
                break;
            case "QUESTIONMARKMINUS":
                if (args == null || args.isBlank()) {
                    handleQueue(senderNumeric, queueShortcutArgs("DONE", "", termReplyChannel));
                } else {
                    handleTerm(senderNumeric, buildTermFindArgs(args), termReplyChannel, false);
                    handleQueue(senderNumeric, queueShortcutArgs("DONE", args, termReplyChannel));
                }
                break;
            case "SHOWCOMMANDS":
                handleShowCommands(senderNumeric, args);
                break;
            case "COMMAND":
                handleCommandHelp(senderNumeric, args);
                break;
            case "STATUS":
                handleStatus(senderNumeric);
                break;
            case "VERSION":
                handleVersion(senderNumeric);
                break;
            case "INVITE":
                handleInvite(senderNumeric, args);
                break;
            case "ADDCHAN":
                handleAddChan(senderNumeric, args);
                break;
            case "DELCHAN":
                handleDelChan(senderNumeric, args);
                break;
            case "CHANCONF":
                handleChanConf(senderNumeric, args);
                break;
            case "WELCOME":
                handleWelcome(senderNumeric, args);
                break;
            case "TOPIC":
                handleTopic(senderNumeric, args);
                break;
            case "QUEUE":
                handleQueue(senderNumeric, args);
                break;
            case "NEXT":
                handleQueue(senderNumeric, queueShortcutArgs("NEXT", args, termReplyChannel));
                break;
            case "DONE":
                handleQueue(senderNumeric, queueShortcutArgs("DONE", args, termReplyChannel));
                break;
            case "ENQUEUE":
                handleQueue(senderNumeric, queueShortcutArgs("ON", args, termReplyChannel));
                break;
            case "DEQUEUE":
                handleQueue(senderNumeric, queueShortcutArgs("OFF", args, termReplyChannel));
                break;
            case "AUTOQUEUE":
                handleQueue(senderNumeric, queueShortcutArgs("MAINTAIN", args, termReplyChannel));
                break;
            case "TICKET":
                handleTicket(senderNumeric, args);
                break;
            case "RESOLVE":
                handleResolveTicket(senderNumeric, args);
                break;
            case "TICKETS":
                handleTickets(senderNumeric, args);
                break;
            case "SHOWTICKET":
                handleShowTicket(senderNumeric, args);
                break;
            case "TICKETMSG":
                handleTicketMessage(senderNumeric, args);
                break;
            case "WHOAMI":
                handleWhoAmI(senderNumeric);
                break;
            case "WHOIS":
                handleWhoIs(senderNumeric, args);
                break;
            case "ADDUSER":
                handleAddOrModUser(senderNumeric, args, false);
                break;
            case "MODUSER":
                handleAddOrModUser(senderNumeric, args, true);
                break;
            case "ACCONF":
                handleAcconf(senderNumeric, args);
                break;
            case "REPORT":
                handleReport(senderNumeric, args);
                break;
            case "WRITEDB":
                handleWriteDb(senderNumeric);
                break;
            case "SEEN":
                handleSeen(senderNumeric, args);
                break;
            case "LISTUSER":
                handleListUser(senderNumeric, args);
                break;
            case "DELUSER":
                handleDelUser(senderNumeric, args);
                break;
            case "LEVEL":
                handleLevel(senderNumeric, args);
                break;
            case "OP":
                handleSimpleMode(senderNumeric, args, "+o", "Usage: OP <#channel> [nick1 .. nick6]");
                break;
            case "DEOP":
                handleSimpleMode(senderNumeric, args, "-o", "Usage: DEOP <#channel> [nick1 .. nick6]");
                break;
            case "VOICE":
                handleSimpleMode(senderNumeric, args, "+v", "Usage: VOICE <#channel> [nick1 .. nick6]");
                break;
            case "DEVOICE":
                handleSimpleMode(senderNumeric, args, "-v", "Usage: DEVOICE <#channel> [nick1 .. nick6]");
                break;
            case "KICK":
                handleKick(senderNumeric, args);
                break;
            case "OUT":
                handleOut(senderNumeric, args);
                break;
            case "BAN":
                handleGlobalBan(senderNumeric, args);
                break;
            case "CHANBAN":
                handleChanBan(senderNumeric, args);
                break;
            case "CENSOR":
                handleCensor(senderNumeric, args);
                break;
            case "DNMO":
                handleDnmo(senderNumeric, args);
                break;
            case "EVERYONEOUT":
                handleEveryoneOut(senderNumeric, args);
                break;
            case "MESSAGE":
                handleMessage(senderNumeric, args);
                break;
            case "LAMERCONTROL":
                handleLamerControl(senderNumeric, args);
                break;
            case "LCEDIT":
                handleLcEdit(senderNumeric, args);
                break;
            case "IDLEKICK":
                handleIdleKick(senderNumeric, args);
                break;
            case "STATS":
                handleStats(senderNumeric, args);
                break;
            case "WEEKSTATS":
                handleWeekStats(senderNumeric, args);
                break;
            case "TOP10":
                handleTop10(senderNumeric, args);
                break;
            case "RATING":
                handleRating(senderNumeric, args);
                break;
            case "TERMSTATS":
                handleTermStats(senderNumeric, args);
                break;
            case "CHANSTATS":
                handleChanStats(senderNumeric, args);
                break;
            case "ACTIVESTAFF":
                handleActiveStaff(senderNumeric, args);
                break;
            case "CHECKCHANNEL":
                handleCheckChannel(senderNumeric, args);
                break;
            case "CHANNEL":
                handleChannel(senderNumeric, args);
                break;
            case "TRIAL":
                handleLevelShortcut(senderNumeric, args, "STAFF");
                break;
            case "STAFF":
                handleLevelShortcut(senderNumeric, args, "STAFF");
                break;
            case "OPER":
                handleLevelShortcut(senderNumeric, args, "OPER");
                break;
            case "ADMIN":
                handleLevelShortcut(senderNumeric, args, "ADMIN");
                break;
            case "FRIEND":
                handleLevelShortcut(senderNumeric, args, "HELPER");
                break;
            case "PEON":
                handleLevelShortcut(senderNumeric, args, "USER");
                break;
            case "IMPROPER":
                handleImproper(senderNumeric, args);
                break;
            case "RELOAD":
                handleReload(senderNumeric);
                break;
            case "STATSDUMP":
                handleStatsDump(senderNumeric);
                break;
            case "STATSREPAIR":
                handleStatsRepair(senderNumeric);
                break;
            case "STATSRESET":
                handleStatsReset(senderNumeric);
                break;
            default:
                if (!hasAccountConfigFlag(senderNumeric, H_ACC_NO_CMD_ERROR)) {
                    sendNotice(senderNumeric, "Unknown command: " + command + ". Use SHOWCOMMANDS for a list of commands.");
                }
                break;
        }
    }

    @Override
    public void shutdown() {
        getLogger().log(Level.INFO, "HelpServ shutting down");
    }

    private void handleHelp(String senderNumeric, String topicQuery) {
        if (topicQuery == null || topicQuery.isBlank()) {
            userState.put(senderNumeric, root);
            sendNode(senderNumeric, root);
            return;
        }

        if (isNumericSelection(topicQuery.trim())) {
            handleHelpSelection(senderNumeric, Integer.parseInt(topicQuery.trim()));
            return;
        }

        TopicNode node = resolveNode(topicQuery);
        if (node == null) {
            sendNotice(senderNumeric, "Invalid value. Use HELP to restart or provide an integer selection.");
            return;
        }

        userState.put(senderNumeric, node);
        sendNode(senderNumeric, node);
    }

    private void handleHelpSelection(String senderNumeric, int selection) {
        TopicNode state = userState.getOrDefault(senderNumeric, root);
        if (state == null) {
            state = root;
        }

        if (selection == 0) {
            if (state.parent != null) {
                state = state.parent;
            }
            userState.put(senderNumeric, state);
            sendNode(senderNumeric, state);
            return;
        }

        if (selection < 0) {
            sendNotice(senderNumeric, "Invalid selection. Use 0..N.");
            return;
        }

        List<TopicNode> children = new ArrayList<>(state.children.values());
        if (children.isEmpty()) {
            sendNotice(senderNumeric, "There are no more options to choose from. Use HELP to restart or 0 to return to the previous entry.");
            return;
        }

        if (selection > children.size()) {
            if (state.parent == null) {
                sendNotice(senderNumeric, "Bad selection. Enter an integer from 1 to " + children.size() + ".");
            } else {
                sendNotice(senderNumeric, "Bad selection. Enter an integer from 1 to " + children.size() + " (0 = previous). ");
            }
            return;
        }

        TopicNode next = children.get(selection - 1);
        userState.put(senderNumeric, next);
        sendNode(senderNumeric, next);
    }

    private void sendNode(String senderNumeric, TopicNode node) {
        String displayPath = node.path();
        if (!"ROOT".equals(displayPath)) {
            sendNotice(senderNumeric, "=== " + displayPath + " ===");
        }

        for (String line : node.lines) {
            sendNotice(senderNumeric, line);
        }

        if (node.children.isEmpty()) {
            sendNotice(senderNumeric, "This concludes the help for this topic, if you want to, you can restart the service with the 'help' command, or return to the previous entry by selecting 0");
            return;
        }

        List<TopicNode> children = new ArrayList<>(node.children.values());
        for (int i = 0; i < children.size(); i++) {
            sendNotice(senderNumeric, (i + 1) + ") " + children.get(i).title);
        }
        if (node.parent != null) {
            sendNotice(senderNumeric, "0) Previous menu");
        }
    }

    private void handleTopics(String senderNumeric, String topicQuery) {
        TopicNode node = resolveNode(topicQuery);
        if (node == null) {
            sendNotice(senderNumeric, "Topic not found.");
            return;
        }
        if (node.children.isEmpty()) {
            sendNotice(senderNumeric, "No subtopics under this topic.");
            return;
        }
        sendNotice(senderNumeric, "Topics in " + node.path() + ": " + joinTopicNames(node.children.values(), MAX_TOPICS));
    }

    private void handleSearch(String senderNumeric, String text) {
        if (text == null || text.isBlank()) {
            sendNotice(senderNumeric, "Usage: SEARCH <text>");
            return;
        }
        String needle = normalizeKey(text);
        List<TopicNode> matches = new ArrayList<>();
        Deque<TopicNode> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            TopicNode node = queue.removeFirst();
            for (TopicNode child : node.children.values()) {
                queue.addLast(child);
            }
            if (node == root) {
                continue;
            }
            boolean titleMatch = normalizeKey(node.title).contains(needle);
            boolean lineMatch = node.lines.stream().map(this::normalizeKey).anyMatch(v -> v.contains(needle));
            if (titleMatch || lineMatch) {
                matches.add(node);
            }
        }

        if (matches.isEmpty()) {
            sendNotice(senderNumeric, "No topics found for: " + text);
            return;
        }

        matches.sort(Comparator.comparing(TopicNode::path));
        StringBuilder sb = new StringBuilder();
        int max = Math.min(matches.size(), MAX_TOPICS);
        for (int i = 0; i < max; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(matches.get(i).path());
        }
        sendNotice(senderNumeric, "Found " + matches.size() + " topic(s): " + sb);
    }

    private void handleReload(String senderNumeric) {
        Users user = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (getUserCommandLevel(user) < 5) {
            sendNotice(senderNumeric, "Access denied: OPER level required.");
            return;
        }
        boolean topicsOk = reloadTopics();
        boolean dbOk = reloadHelpmodDb();
        sendNotice(senderNumeric, topicsOk ? "Help topics reloaded." : "Help topics reload failed, using previous data.");
        sendNotice(senderNumeric, dbOk ? "helpmod.db configuration reloaded." : "helpmod.db not loaded (using JSON defaults).");
    }

    private void handleShowCommands(String senderNumeric, String args) {
        Users user = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        int requesterLevel = getUserCommandLevel(user);
        if (requesterLevel <= 1 && user != null && user.isOper()) {
            requesterLevel = 5;
        }
        int requestedLevel = requesterLevel;
        String[] tokens = splitArgs(args);
        if (tokens.length > 0) {
            int parsed = parseIntOrDefault(tokens[0], -1);
            if (parsed >= 0 && parsed <= requesterLevel) {
                requestedLevel = parsed;
            }
        }

        Software.BuildInfo bi = Software.getBuildInfo();
        sendNotice(senderNumeric, "HelpMod " + bi.version() + " commands for userlevel " + commandLevelName(requestedLevel) + ":");

        List<CommandDef> visible = new ArrayList<>();
        for (CommandDef cmd : commandCatalog()) {
            if (cmd.minLevel <= requestedLevel) {
                visible.add(cmd);
            }
        }
        visible.sort(Comparator
                .comparingInt((CommandDef c) -> c.minLevel)
                .thenComparing(c -> c.name, String.CASE_INSENSITIVE_ORDER));

        int previousLevel = 1;
        for (CommandDef cmd : visible) {
            if (cmd.minLevel > previousLevel) {
                sendNotice(senderNumeric, "--- Additional commands for userlevel " + commandLevelName(cmd.minLevel) + " ---");
                previousLevel = cmd.minLevel;
            }
            sendNotice(senderNumeric, String.format(Locale.ROOT, "%-16s %s", cmd.name, cmd.help));
        }
    }

    private void handleCommandHelp(String senderNumeric, String cmd) {
        if (cmd == null || cmd.isBlank()) {
            sendNotice(senderNumeric, "Usage: COMMAND <name>");
            return;
        }
        String commandName = cmd.trim().toLowerCase(Locale.ROOT);
        String mapped;
        if ("?".equals(commandName) || "questionmark".equals(commandName)) {
            mapped = "?";
        } else if ("?+".equals(commandName) || "questionmarkplus".equals(commandName)) {
            mapped = "?+";
        } else if ("?-".equals(commandName) || "questionmarkminus".equals(commandName)) {
            mapped = "?-";
        } else {
            mapped = commandName.toUpperCase(Locale.ROOT);
        }

        CommandDef def = commandDef(mapped);
        if (def == null) {
            sendNotice(senderNumeric, "No command help available for: " + cmd);
            return;
        }
        sendNotice(senderNumeric, String.format(Locale.ROOT, "%s: %s", def.name, def.help));
    }

    private void handleStatus(String senderNumeric) {
        Users user = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(user)) {
            sendNotice(senderNumeric, "Access denied: STAFF level required.");
            return;
        }
        int topicCount = countTopics(root);
        sendNotice(senderNumeric, "HelpServ status: topics=" + topicCount + ", queues=" + supportQueue.size() + ", tickets=" + totalTicketCount()
            + ", source=" + (sourceFile != null ? sourceFile.toString() : "fallback")
            + ", hstat_cycle=" + hstatCycle
            + ", channels=" + managedChannels.size());
    }

    private void handleVersion(String senderNumeric) {
        Software.BuildInfo bi = Software.getBuildInfo();
        sendNotice(senderNumeric, "HelpServ version " + bi.getFullVersion());
    }

    private void handleInvite(String senderNumeric, String args) {
        String channel = parseChannelArg(args, resolveDefaultHelpChannel());
        String channelKey = channel.toLowerCase(Locale.ROOT);
        Users requester = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;

        if (requester == null) {
            return;
        }

        boolean ticketRequired = isTicketRequired(channelKey);
        if (ticketRequired && !isStaffLike(requester) && !hasValidTicket(channelKey, requester)) {
            sendNotice(senderNumeric, "No valid ticket for " + channel + ". Ask staff with TICKET command.");
            return;
        }

        if (Boolean.TRUE.equals(queueEnabled.get(channelKey)) && !isStaffLike(requester)) {
            enqueueUser(channelKey, senderNumeric);
            int pos = queuePosition(channelKey, senderNumeric);
            if (!hasAccountConfigFlag(senderNumeric, H_ACC_NOSPAM)) {
                sendNotice(senderNumeric, "Queue is active for " + channel + ". Added to queue at position #" + pos + ".");
            }
            return;
        }

        sendText("%s%s I %s %s", numeric, getNumericSuffix(), senderNumeric, channel);
        sendNotice(senderNumeric, "Invited you to " + channel);
    }

    private void sendUsage(String senderNumeric) {
        sendNotice(senderNumeric, "HelpServ commands: HELP [topic], TOPICS [topic], SEARCH <text>, TERM [#channel] [op], ? [term], SHOWCOMMANDS, COMMAND <name>, VERSION, INVITE [#channel], WELCOME [#channel] [text]");
        sendNotice(senderNumeric, "Account: WHOAMI, WHOIS <nick|#account>, SEEN <nick|#account>, LISTUSER [level] [pattern], ACCONF [[+/-]value1] ... [[+/-]valuen]");
        sendNotice(senderNumeric, "Queue: QUEUE [#channel] [LIST|SUMMARY|NEXT [n]|DONE [nick..]|ON|OFF|MAINTAIN [n]|RESET], NEXT, DONE, ENQUEUE, DEQUEUE");
        sendNotice(senderNumeric, "Moderation: OP/DEOP/VOICE/DEVOICE <#chan> [nick..], KICK <#chan> <nick..> [:reason], OUT <nick..> [:reason], MESSAGE <#chan> <text>, CHECKCHANNEL <#chan> [summary], CHANNEL <#chan>, EVERYONEOUT [#chan] [all|unauthed] [:reason]");
        sendNotice(senderNumeric, "Policy: BAN [LIST|ADD|DEL], CHANBAN <#chan> [ADD|DEL|LIST], CENSOR <#chan> [ADD|DEL|LIST], LAMERCONTROL [#chan] [profile|list], LCEDIT [ADD|DEL|VIEW|LIST|EDIT], IDLEKICK [#chan] [time], DNMO <nick..>");
        sendNotice(senderNumeric, "Stats: STATS, WEEKSTATS, TOP10, RATING, TERMSTATS, CHANSTATS, ACTIVESTAFF, STATSDUMP, STATSREPAIR, STATSRESET");
        sendNotice(senderNumeric, "Tickets: TICKET <nick> [#channel] [minutes], RESOLVE <nick>, TICKETS [#channel], SHOWTICKET <nick>, TICKETMSG [#channel] [text]");
        sendNotice(senderNumeric, "Staff: ADDUSER/MODUSER <account|nick> [level], DELUSER <account>, LEVEL <account> [USER|HELPER|STAFF|OPER|ADMIN|DEV], PEON/FRIEND/TRIAL/STAFF/OPER/ADMIN <account|nick>, IMPROPER <account|nick>, ADDCHAN/DELCHAN, CHANCONF, TOPIC, REPORT <#from> <#to>, WRITEDB");
    }

    private void handleAddChan(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required.");
            return;
        }
        String[] tokens = splitArgs(args);
        if (tokens.length < 1 || !tokens[0].startsWith("#")) {
            sendNotice(senderNumeric, "Usage: ADDCHAN <#channel>");
            return;
        }
        String channel = tokens[0].toLowerCase(Locale.ROOT);
        if (managedChannels.contains(channel)) {
            sendNotice(senderNumeric, "Channel already managed: " + channel);
            return;
        }
        managedChannels.add(channel);
        queueEnabled.putIfAbsent(channel, false);
        channelWelcome.putIfAbsent(channel, "Welcome to " + channel);
        channelConfigFlags.putIfAbsent(channel, 0);
        sendNotice(senderNumeric, "Channel added: " + channel);
    }

    private void handleDelChan(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required.");
            return;
        }
        String[] tokens = splitArgs(args);
        if (tokens.length < 1 || !tokens[0].startsWith("#")) {
            sendNotice(senderNumeric, "Usage: DELCHAN <#channel> [YesImSure]");
            return;
        }
        if (tokens.length < 2 || !"YesImSure".equals(tokens[1])) {
            sendNotice(senderNumeric, "Can not delete channel: add parameter YesImSure to confirm.");
            return;
        }
        String channel = tokens[0].toLowerCase(Locale.ROOT);
        if (channel.equals(resolveDefaultHelpChannel().toLowerCase(Locale.ROOT))) {
            sendNotice(senderNumeric, "Default help channel can not be deleted.");
            return;
        }
        managedChannels.remove(channel);
        queueEnabled.remove(channel);
        queueMaintain.remove(channel);
        queueMaintainTarget.remove(channel);
        channelTickets.remove(channel);
        ticketMessages.remove(channel);
        channelTerms.remove(channel);
        channelWelcome.remove(channel);
        channelConfigFlags.remove(channel);
        channelTopicParts.remove(channel);
        reportRoutes.remove(channel);
        sendNotice(senderNumeric, "Channel removed: " + channel);
    }

    private void handleChanConf(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required.");
            return;
        }
        String[] tokens = splitArgs(args);
        String channel = resolveDefaultHelpChannel().toLowerCase(Locale.ROOT);
        int index = 0;
        if (tokens.length > 0 && tokens[0].startsWith("#")) {
            channel = tokens[0].toLowerCase(Locale.ROOT);
            index = 1;
        }
        if (!isManagedChannel(channel)) {
            sendNotice(senderNumeric, "Cannot change or view channel configuration: Channel not specified or found");
            return;
        }

        int flags = channelConfigFlags.getOrDefault(channel, dbChannelFlags.getOrDefault(channel, 0));
        int oldFlags = flags;
        boolean changed = false;

        if (tokens.length <= index) {
            sendNotice(senderNumeric, "Channel configuration for " + channel + ":");
            for (int id = 0; id <= HCHANNEL_CONF_COUNT; id++) {
                sendNotice(senderNumeric, String.format(Locale.ROOT, "(%02d) %-32s : %s", id,
                        chanConfName(id), chanConfState(flags, 1 << id)));
            }
            return;
        }

        for (int i = index; i < tokens.length; i++) {
            String token = tokens[i];
            int type;
            String idToken;
            if (token.startsWith("+")) {
                type = 1;
                idToken = token.substring(1);
            } else if (token.startsWith("-")) {
                type = -1;
                idToken = token.substring(1);
            } else {
                type = 0;
                idToken = token;
            }

            int id = parseIntOrDefault(idToken, -1);
            if (id < 0 || id > HCHANNEL_CONF_COUNT) {
                sendNotice(senderNumeric, "Cannot change channel configuration: Expected integer between [0, " + HCHANNEL_CONF_COUNT + "]");
                continue;
            }

            int bit = 1 << id;
            switch (type) {
                case -1:
                    flags &= ~bit;
                    changed = true;
                    sendNotice(senderNumeric, "Channel configuration for " + channel + " changed: "
                            + chanConfName(id) + " set to " + chanConfState(flags, bit));
                    break;
                case 0:
                    sendNotice(senderNumeric, String.format(Locale.ROOT, "(%02d) %-32s : %s", id,
                            chanConfName(id), chanConfState(flags, bit)));
                    break;
                case 1:
                    flags |= bit;
                    changed = true;
                    sendNotice(senderNumeric, "Channel configuration for " + channel + " changed: "
                            + chanConfName(id) + " set to " + chanConfState(flags, bit));
                    break;
                default:
                    break;
            }
        }

        if (changed) {
            channelConfigFlags.put(channel, flags);
            dbChannelFlags.put(channel, flags);
            applyChannelConfChange(channel, oldFlags, flags);
        }
    }

    private void applyChannelConfChange(String channel, int oldFlags, int newFlags) {
        int changedBits = oldFlags ^ newFlags;

        if ((changedBits & H_QUEUE) != 0) {
            boolean enabled = (newFlags & H_QUEUE) != 0;
            queueEnabled.put(channel, enabled);
            if (enabled) {
                enqueueCurrentChannelUsers(channel);
                sendChannelMessage(channel, "Channel queue has been activated, all users enqueued");
            } else {
                devoicePeons(channel);
                sendChannelMessage(channel, "Channel queue has been deactivated");
            }
        }

        if ((changedBits & H_QUEUE_MAINTAIN) != 0) {
            boolean enabled = (newFlags & H_QUEUE_MAINTAIN) != 0;
            queueMaintain.put(channel, enabled);
            if (enabled) {
                queueMaintainTarget.putIfAbsent(channel, 1);
            } else {
                queueMaintainTarget.put(channel, 0);
            }
        }

        if ((changedBits & (H_MAINTAIN_M | H_MAINTAIN_I)) != 0) {
            applyChannelModeCheck(channel, newFlags);
        }

        if ((changedBits & H_JOINFLOOD_PROTECTION) != 0 && (newFlags & H_JOINFLOOD_PROTECTION) == 0) {
            deactivateJoinFlood(channel);
        }
    }

    private void devoicePeons(String channel) {
        if (st == null || st.getChannel() == null) {
            return;
        }
        Channel channelObj = st.getChannel().get(channel);
        if (channelObj == null) {
            return;
        }
        List<String> voiced = new ArrayList<>(channelObj.getVoice());
        for (String userNumeric : voiced) {
            if (userNumeric == null || userNumeric.isBlank()) {
                continue;
            }
            Users user = st.getUsers() != null ? st.getUsers().get(userNumeric) : null;
            if (isStaffLike(user)) {
                continue;
            }
            sendText("%s%s M %s -v %s", numeric, getNumericSuffix(), channel, userNumeric);
            channelObj.removeVoice(userNumeric);
        }
    }

    private void handleJoinFloodProtection(String channel) {
        long now = System.currentTimeMillis() / 1000;
        long control = joinFloodControl.getOrDefault(channel, 0L);
        if (control < now) {
            control = now;
        } else {
            control++;
        }
        joinFloodControl.put(channel, control);

        if ((control - now) > 12 && !isChannelModeEnabled(channel, 'r')) {
            if (isChannelFlagEnabled(channel, H_REPORT)) {
                String reportTarget = reportRoutes.get(channel);
                if (reportTarget != null && reportTarget.startsWith("#")) {
                    sendChannelMessage(reportTarget.toLowerCase(Locale.ROOT),
                            "Warning: Possible join flood on " + channel + ", setting +r");
                }
            }
            activateJoinFlood(channel);
        }
    }

    private void activateJoinFlood(String channel) {
        sendText("%s%s M %s +r", numeric, getNumericSuffix(), channel);
        updateLocalChannelMode(channel, 'r', true);
        joinFloodRegOnlyUntil.put(channel, (System.currentTimeMillis() / 1000) + 60L);
    }

    private void deactivateJoinFlood(String channel) {
        if (!isChannelModeEnabled(channel, 'r')) {
            joinFloodRegOnlyUntil.remove(channel);
            return;
        }
        sendText("%s%s M %s -r", numeric, getNumericSuffix(), channel);
        updateLocalChannelMode(channel, 'r', false);
        joinFloodRegOnlyUntil.remove(channel);
    }

    private void processJoinFloodTimeouts() {
        if (joinFloodRegOnlyUntil.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        for (Map.Entry<String, Long> entry : new ArrayList<>(joinFloodRegOnlyUntil.entrySet())) {
            if (entry.getValue() == null || entry.getValue() > now) {
                continue;
            }
            deactivateJoinFlood(entry.getKey());
        }
    }

    private void enqueueCurrentChannelUsers(String channel) {
        if (st == null || st.getChannel() == null) {
            return;
        }
        Channel channelObj = st.getChannel().get(channel);
        if (channelObj == null) {
            return;
        }
        for (String userNumeric : channelObj.getUsers()) {
            if (userNumeric == null || userNumeric.isBlank()) {
                continue;
            }
            if ((numeric + getNumericSuffix()).equals(userNumeric)) {
                continue;
            }
            enqueueUser(channel, userNumeric);
        }
    }

    private void applyChannelModeCheck(String channel, int flags) {
        boolean shouldModerate = (flags & H_MAINTAIN_M) != 0;
        boolean isModerated = isChannelModeEnabled(channel, 'm');
        if (shouldModerate && !isModerated) {
            sendText("%s%s M %s +m", numeric, getNumericSuffix(), channel);
            updateLocalChannelMode(channel, 'm', true);
        } else if (!shouldModerate && isModerated) {
            sendText("%s%s M %s -m", numeric, getNumericSuffix(), channel);
            updateLocalChannelMode(channel, 'm', false);
        }

        boolean shouldInviteOnly = (flags & H_MAINTAIN_I) != 0;
        boolean isInviteOnly = isChannelModeEnabled(channel, 'i');
        if (shouldInviteOnly && !isInviteOnly) {
            sendText("%s%s M %s +i", numeric, getNumericSuffix(), channel);
            updateLocalChannelMode(channel, 'i', true);
        } else if (!shouldInviteOnly && isInviteOnly) {
            sendText("%s%s M %s -i", numeric, getNumericSuffix(), channel);
            updateLocalChannelMode(channel, 'i', false);
        }
    }

    private boolean hasMaintainModeMismatch(String channel, int flags) {
        if (st == null || st.getChannel() == null || channel == null) {
            return false;
        }
        Channel channelObj = st.getChannel().get(channel.toLowerCase(Locale.ROOT));
        if (channelObj == null || channelObj.getModes() == null) {
            return false;
        }

        boolean shouldModerate = (flags & H_MAINTAIN_M) != 0;
        boolean shouldInviteOnly = (flags & H_MAINTAIN_I) != 0;

        boolean isModerated = isChannelModeEnabled(channel, 'm');
        boolean isInviteOnly = isChannelModeEnabled(channel, 'i');

        return shouldModerate != isModerated || shouldInviteOnly != isInviteOnly;
    }

    private boolean isChannelModeEnabled(String channel, char mode) {
        if (st == null || st.getChannel() == null || channel == null) {
            return false;
        }
        Channel channelObj = st.getChannel().get(channel.toLowerCase(Locale.ROOT));
        if (channelObj == null || channelObj.getModes() == null) {
            return false;
        }
        return channelObj.getModes().indexOf(mode) >= 0;
    }

    private boolean isChannelUserModeEnabled(String channel, String userNumeric, char mode) {
        if (st == null || st.getChannel() == null || channel == null || userNumeric == null || userNumeric.isBlank()) {
            return false;
        }
        Channel channelObj = st.getChannel().get(channel.toLowerCase(Locale.ROOT));
        if (channelObj == null) {
            return false;
        }
        switch (mode) {
            case 'o':
                return channelObj.getOp() != null && channelObj.getOp().contains(userNumeric);
            case 'v':
                return channelObj.getVoice() != null && channelObj.getVoice().contains(userNumeric);
            default:
                return false;
        }
    }

    private void updateLocalChannelUserMode(String channel, String userNumeric, char mode, boolean set) {
        if (st == null || st.getChannel() == null || channel == null || userNumeric == null || userNumeric.isBlank()) {
            return;
        }
        Channel channelObj = st.getChannel().get(channel.toLowerCase(Locale.ROOT));
        if (channelObj == null) {
            return;
        }
        switch (mode) {
            case 'o':
                if (set) {
                    if (!channelObj.getOp().contains(userNumeric)) {
                        channelObj.addOp(userNumeric);
                    }
                } else {
                    channelObj.removeOp(userNumeric);
                }
                break;
            case 'v':
                if (set) {
                    if (!channelObj.getVoice().contains(userNumeric)) {
                        channelObj.addVoice(userNumeric);
                    }
                } else {
                    channelObj.removeVoice(userNumeric);
                }
                break;
            default:
                break;
        }
    }

    private void updateLocalChannelMode(String channel, char mode, boolean set) {
        if (st == null || st.getChannel() == null || channel == null) {
            return;
        }
        Channel channelObj = st.getChannel().get(channel.toLowerCase(Locale.ROOT));
        if (channelObj == null) {
            return;
        }
        String modes = channelObj.getModes();
        String normalized = modes == null ? "" : modes.replace("+", "").replace("-", "");
        boolean has = normalized.indexOf(mode) >= 0;
        if (set && !has) {
            normalized = normalized + mode;
        } else if (!set && has) {
            normalized = normalized.replace(String.valueOf(mode), "");
        }
        channelObj.setModes("+" + normalized);
    }

    private String chanConfState(int flags, int mask) {
        return (flags & mask) != 0 ? "Yes" : "No";
    }

    private String chanConfName(int flag) {
        switch (flag) {
            case 0:
                return "Passive state";
            case 1:
                return "Welcome message";
            case 2:
                return "JoinFlood protection";
            case 3:
                return "Queue";
            case 4:
                return "Verbose queue (requires queue)";
            case 5:
                return "Auto queue (requires queue)";
            case 6:
                return "Channel status reporting";
            case 7:
                return "Pattern censor";
            case 8:
                return "Lamer control";
            case 9:
                return "Idle user removal";
            case 10:
                return "Keep channel moderated";
            case 11:
                return "Keep channel invite only";
            case 12:
                return "Handle channel topic";
            case 13:
                return "Calculate statistic";
            case 14:
                return "Remove joining trojans";
            case 15:
                return "Channel commands";
            case 16:
                return "Oper only channel";
            case 17:
                return "Disallow bold, underline, etc.";
            case 18:
                return "Queue inactivity deactivation";
            case 19:
                return "Require a ticket to join";
            case 20:
                return "Send a message on ticket issue";
            case 21:
                return "Excessive highlight prevention";
            default:
                return "Error, please contact strutsi";
        }
    }

    private void handleWelcome(String senderNumeric, String args) {
        String[] tokens = splitArgs(args);
        String channel = resolveDefaultHelpChannel().toLowerCase(Locale.ROOT);
        int index = 0;
        if (tokens.length > 0 && tokens[0].startsWith("#")) {
            channel = tokens[0].toLowerCase(Locale.ROOT);
            index = 1;
        }
        managedChannels.add(channel);

        if (tokens.length <= index) {
            sendNotice(senderNumeric, "Welcome " + channel + ": " + channelWelcome.getOrDefault(channel, "(none)"));
            return;
        }

        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required to set welcome.");
            return;
        }

        String text = joinTokens(tokens, index);
        channelWelcome.put(channel, text);
        sendNotice(senderNumeric, "Welcome updated for " + channel);
    }

    private void handleTopic(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required.");
            return;
        }

        String[] tokens = splitArgs(args);
        String channel = resolveDefaultHelpChannel().toLowerCase(Locale.ROOT);
        int index = 0;
        if (tokens.length > 0 && tokens[0].startsWith("#")) {
            channel = tokens[0].toLowerCase(Locale.ROOT);
            index = 1;
        }
        managedChannels.add(channel);
        List<String> parts = channelTopicParts.computeIfAbsent(channel, c -> new ArrayList<>());

        if (tokens.length <= index) {
            sendNotice(senderNumeric, "Topic " + channel + ": " + buildTopic(parts));
            return;
        }

        String op = tokens[index].toUpperCase(Locale.ROOT);
        String rest = tokens.length > index + 1 ? joinTokens(tokens, index + 1) : "";
        switch (op) {
            case "ADD":
                if (!rest.isBlank()) {
                    parts.add(rest);
                }
                sendNotice(senderNumeric, "Topic updated " + channel + ": " + buildTopic(parts));
                break;
            case "DEL": {
                int pos = parseIntOrDefault(rest, -1);
                if (pos < 1 || pos > parts.size()) {
                    sendNotice(senderNumeric, "Usage: TOPIC [#channel] DEL <position>");
                    return;
                }
                parts.remove(pos - 1);
                sendNotice(senderNumeric, "Topic updated " + channel + ": " + buildTopic(parts));
                break;
            }
            case "ERASE":
                parts.clear();
                sendNotice(senderNumeric, "Topic cleared for " + channel);
                break;
            case "SET":
                parts.clear();
                if (!rest.isBlank()) {
                    parts.add(rest);
                }
                sendNotice(senderNumeric, "Topic set for " + channel + ": " + buildTopic(parts));
                break;
            case "REFRESH":
                sendNotice(senderNumeric, "Topic " + channel + ": " + buildTopic(parts));
                break;
            default:
                sendNotice(senderNumeric, "Usage: TOPIC [#channel] [ADD <text>|DEL <n>|ERASE|SET <text>|REFRESH]");
                break;
        }
    }

    private void handleAddOrModUser(String senderNumeric, String args, boolean mod) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required.");
            return;
        }

        String[] tokens = splitArgs(args);
        if (tokens.length < 1) {
            sendNotice(senderNumeric, "Usage: " + (mod ? "MODUSER" : "ADDUSER") + " <account|nick> [level]");
            return;
        }

        String account = resolveAccountFromArg(tokens[0]);
        if (account == null || account.isBlank()) {
            sendNotice(senderNumeric, "Unknown account/user: " + tokens[0]);
            return;
        }
        account = normalizeAccountArg(account);

        if (mi == null || mi.getDb() == null || !mi.getDb().isRegistered(account)) {
            sendNotice(senderNumeric, "No registered account: " + account + " (create via AuthServ first)");
            return;
        }

        int flags = mi.getDb().getFlags(account);
        if (tokens.length == 1) {
            if (mod) {
                int level = resolveHelpmodAccountLevel(account);
                sendNotice(senderNumeric, "MODUSER " + account + ": level=" + commandLevelName(level));
            } else {
                int newFlags = applyLevel(flags, "USER");
                mi.getDb().updateData("flags", account, newFlags);
                accountLevels.put(account.toLowerCase(Locale.ROOT), 1);
                sendNotice(senderNumeric, "ADDUSER " + account + ": level set to USER");
            }
            return;
        }

        int requestedCommandLevel = parseCommandLevelToken(tokens[1]);
        if (requestedCommandLevel < 0) {
            sendNotice(senderNumeric, "Invalid level. Allowed: LAMER, PEON, FRIEND, TRIAL, STAFF, OPER, ADMIN, DEV");
            return;
        }

        String requestedLevel = normalizeLevelToken(tokens[1]);
        if (requestedLevel == null) {
            sendNotice(senderNumeric, "Invalid level. Allowed: LAMER, PEON, FRIEND, TRIAL, STAFF, OPER, ADMIN, DEV");
            return;
        }
        if ("LAMER".equals(requestedLevel)) {
            handleDelUser(senderNumeric, account);
            accountLevels.put(account.toLowerCase(Locale.ROOT), 0);
            return;
        }

        int newFlags = applyLevel(flags, requestedLevel);
        if (newFlags < 0) {
            sendNotice(senderNumeric, "Level mapping failed for: " + requestedLevel);
            return;
        }
        mi.getDb().updateData("flags", account, newFlags);
        accountLevels.put(account.toLowerCase(Locale.ROOT), requestedCommandLevel);
        sendNotice(senderNumeric, (mod ? "MODUSER" : "ADDUSER") + " " + account + ": level=" + commandLevelName(requestedCommandLevel));
    }

    private void handleAcconf(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (actor == null) {
            return;
        }

        String[] tokens = splitArgs(args);
        String account = resolveAccountName(actor);
        int index = 0;

        if (tokens.length > 0 && tokens[0].startsWith("#") && (isStaffLike(actor) || actor.isOper())) {
            account = normalizeAccountArg(tokens[0]);
            index = 1;
            if (account == null || account.isBlank() || mi == null || mi.getDb() == null || !mi.getDb().isRegistered(account)) {
                sendNotice(senderNumeric, "Unknown account for ACCONF: " + tokens[0]);
                return;
            }
        }

        if (account == null || account.isBlank()) {
            sendNotice(senderNumeric, "Account configuration impossible: You do not have an account");
            return;
        }

        String accountKey = account.toLowerCase(Locale.ROOT);
        int conf = accountConfig.getOrDefault(accountKey, 0);
        int originalConf = conf;

        if (tokens.length <= index) {
            sendNotice(senderNumeric, "Account configuration for " + account + ":");
            for (int i = 0; i <= HACCOUNT_CONF_COUNT; i++) {
                sendNotice(senderNumeric, String.format(Locale.ROOT, "(%02d) %-35s : %s",
                        i,
                        accountConfName(i),
                        accountConfState(conf, 1 << i)));
            }
            return;
        }

        int processed = 0;
        boolean changed = false;
        for (int i = index; i < tokens.length && processed < MAX_ACCONF_ARGS; i++) {
            String rawToken = tokens[i] == null ? "" : tokens[i].trim();
            if (rawToken.isBlank()) {
                continue;
            }
            processed++;

            int mode = 0;
            String token = rawToken;
            if (token.startsWith("+")) {
                mode = 1;
                token = token.substring(1).trim();
            } else if (token.startsWith("-")) {
                mode = -1;
                token = token.substring(1).trim();
            }

            int id = parseIntOrDefault(token, -1);
            if (id < 0 || id > HACCOUNT_CONF_COUNT) {
                sendNotice(senderNumeric, "Cannot change account configuration: Expected integer between [0, " + HACCOUNT_CONF_COUNT + "]");
                continue;
            }

            int bit = 1 << id;
            if (mode < 0) {
                conf &= ~bit;
                changed = true;
                sendNotice(senderNumeric, "Account configuration for " + account + " changed: "
                        + accountConfName(id) + " set to " + accountConfState(conf, bit));
            } else if (mode > 0) {
                conf |= bit;
                changed = true;
                sendNotice(senderNumeric, "Account configuration for " + account + " changed: "
                        + accountConfName(id) + " set to " + accountConfState(conf, bit));
            } else {
                sendNotice(senderNumeric, String.format(Locale.ROOT, "(%02d) %-35s : %s",
                        id,
                        accountConfName(id),
                        accountConfState(conf, bit)));
            }
        }
        if (changed) {
            accountConfig.put(accountKey, conf);
            applyAcconfAutoModesNow(senderNumeric, accountKey, originalConf, conf);
        }
    }

    private void applyAcconfAutoModesNow(String senderNumeric, String accountKey, int oldConf, int newConf) {
        if (accountKey == null || accountKey.isBlank()) {
            return;
        }

        int activated = (~oldConf) & newConf & (H_ACC_AUTO_OP | H_ACC_AUTO_VOICE);
        if (activated == 0) {
            return;
        }

        if (st == null || st.getUsers() == null || st.getChannel() == null) {
            return;
        }

        int opGranted = 0;
        int voiceGranted = 0;

        for (Map.Entry<String, Users> entry : st.getUsers().entrySet()) {
            String userNumeric = entry.getKey();
            Users user = entry.getValue();
            if (userNumeric == null || userNumeric.isBlank() || user == null) {
                continue;
            }

            String userAccount = normalizeAccountArg(resolveAccountName(user));
            if (userAccount == null || userAccount.isBlank() || !userAccount.equalsIgnoreCase(accountKey)) {
                continue;
            }

            for (String channelName : user.getChannels()) {
                if (channelName == null || channelName.isBlank()) {
                    continue;
                }
                String channel = channelName.toLowerCase(Locale.ROOT);
                if (!isManagedChannel(channel) || isChannelFlagEnabled(channel, H_PASSIVE)) {
                    continue;
                }

                Channel channelObj = st.getChannel().get(channel);
                if (channelObj == null || channelObj.getUsers() == null || !channelObj.getUsers().contains(userNumeric)) {
                    continue;
                }

                if ((activated & H_ACC_AUTO_OP) != 0 && isStaffLike(user)
                        && !isChannelUserModeEnabled(channel, userNumeric, 'o')) {
                    sendText("%s%s M %s +o %s", numeric, getNumericSuffix(), channel, userNumeric);
                    updateLocalChannelUserMode(channel, userNumeric, 'o', true);
                    opGranted++;
                }

                if ((activated & H_ACC_AUTO_VOICE) != 0 && isTrialLike(user)
                        && !isChannelUserModeEnabled(channel, userNumeric, 'v')) {
                    sendText("%s%s M %s +v %s", numeric, getNumericSuffix(), channel, userNumeric);
                    updateLocalChannelUserMode(channel, userNumeric, 'v', true);
                    voiceGranted++;
                }
            }
        }

        if (senderNumeric != null && !senderNumeric.isBlank() && (opGranted > 0 || voiceGranted > 0)) {
            sendNotice(senderNumeric, "ACCONF immediate apply: +o=" + opGranted + ", +v=" + voiceGranted + " for #" + accountKey);
        }
    }

    private boolean applyAcconfJoinModes(String channel, String joiningNumeric, Users joiningUser) {
        if (channel == null || joiningNumeric == null || joiningUser == null) {
            return false;
        }
        if (isChannelFlagEnabled(channel, H_PASSIVE)) {
            return false;
        }

        int conf = resolveAccountConfigForNumeric(joiningNumeric);
        if ((conf & H_ACC_AUTO_OP) != 0 && isStaffLike(joiningUser)) {
            if (!isChannelUserModeEnabled(channel, joiningNumeric, 'o')) {
                sendText("%s%s M %s +o %s", numeric, getNumericSuffix(), channel, joiningNumeric);
                updateLocalChannelUserMode(channel, joiningNumeric, 'o', true);
            }
        }
        if ((conf & H_ACC_AUTO_VOICE) != 0 && isTrialLike(joiningUser)) {
            if (!isChannelUserModeEnabled(channel, joiningNumeric, 'v')) {
                sendText("%s%s M %s +v %s", numeric, getNumericSuffix(), channel, joiningNumeric);
                updateLocalChannelUserMode(channel, joiningNumeric, 'v', true);
            }
        }
        return true;
    }

    private void queuePendingAutoMode(String channel, String userNumeric) {
        if (channel == null || channel.isBlank() || userNumeric == null || userNumeric.isBlank()) {
            return;
        }
        pendingAutoModes
                .computeIfAbsent(channel.toLowerCase(Locale.ROOT), k -> ConcurrentHashMap.newKeySet())
                .add(userNumeric);
    }

    private void processPendingAutoModes() {
        if (pendingAutoModes.isEmpty() || st == null || st.getUsers() == null || st.getChannel() == null) {
            return;
        }

        for (Map.Entry<String, Set<String>> entry : new ArrayList<>(pendingAutoModes.entrySet())) {
            String channel = entry.getKey();
            if (channel == null || channel.isBlank()) {
                pendingAutoModes.remove(channel);
                continue;
            }

            Channel channelObj = st.getChannel().get(channel);
            Set<String> waiting = entry.getValue();
            if (waiting == null || waiting.isEmpty()) {
                pendingAutoModes.remove(channel);
                continue;
            }

            for (String userNumeric : new ArrayList<>(waiting)) {
                if (userNumeric == null || userNumeric.isBlank()) {
                    waiting.remove(userNumeric);
                    continue;
                }

                if (channelObj == null || channelObj.getUsers() == null || !channelObj.getUsers().contains(userNumeric)) {
                    waiting.remove(userNumeric);
                    continue;
                }

                Users user = st.getUsers().get(userNumeric);
                if (applyAcconfJoinModes(channel, userNumeric, user)) {
                    waiting.remove(userNumeric);
                }
            }

            if (waiting.isEmpty()) {
                pendingAutoModes.remove(channel);
            }
        }
    }

    private void handleReport(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required.");
            return;
        }
        String[] tokens = splitArgs(args);
        if (tokens.length == 0) {
            if (reportRoutes.isEmpty()) {
                sendNotice(senderNumeric, "No report routes configured.");
                return;
            }
            int shown = 0;
            for (Map.Entry<String, String> e : reportRoutes.entrySet()) {
                sendNotice(senderNumeric, "REPORT " + e.getKey() + " -> " + e.getValue());
                shown++;
                if (shown >= MAX_TOPICS) {
                    sendNotice(senderNumeric, "(Report routes truncated)");
                    break;
                }
            }
            return;
        }
        if (tokens.length < 2 || !tokens[0].startsWith("#")) {
            sendNotice(senderNumeric, "Usage: REPORT <#source> <#target|OFF>");
            return;
        }
        String source = tokens[0].toLowerCase(Locale.ROOT);
        String target = tokens[1].toLowerCase(Locale.ROOT);
        if ("off".equals(target) || "none".equals(target) || "-".equals(target)) {
            reportRoutes.remove(source);
            sendNotice(senderNumeric, "Report route removed for " + source);
            return;
        }
        if (!target.startsWith("#")) {
            sendNotice(senderNumeric, "Target must be a channel (#target) or OFF.");
            return;
        }
        reportRoutes.put(source, target);
        sendNotice(senderNumeric, "Report route set: " + source + " -> " + target);
    }

    private void handleWriteDb(String senderNumeric) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required.");
            return;
        }

        Path targetFile = resolveHelpmodDbWriteTarget();
        if (targetFile == null) {
            sendNotice(senderNumeric, "WRITEDB failed: no writable helpmod.db path configured.");
            return;
        }

        try {
            writeHelpmodDb(targetFile);
            this.helpmodDbFile = targetFile;
            sendNotice(senderNumeric, "WRITEDB: Saved runtime state to " + targetFile + " (terms=" + globalTerms.size()
                    + ", queue=" + supportQueue.size() + ", reports=" + reportRoutes.size() + ").");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "HelpServ failed to write helpmod.db", e);
            sendNotice(senderNumeric, "WRITEDB failed: " + e.getMessage());
        }
    }

    private Path resolveHelpmodDbWriteTarget() {
        if (helpmodDbFile != null) {
            return helpmodDbFile;
        }

        Path helpDir = resolveHelpBaseDir();
        if (helpDir != null) {
            return helpDir.resolve("helpmod.db").normalize();
        }
        return null;
    }

    private void writeHelpmodDb(Path targetFile) throws IOException {
        Path parent = targetFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<String> lines = new ArrayList<>();
        lines.add("% G2 version 2.17 database");
        lines.add("%");
        lines.add("% G internal version");
        lines.add("version");
        lines.add("\t17");
        lines.add("");
        lines.add("% global variables");
        lines.add("%  hstat_cycle");
        lines.add("globals");
        lines.add("\t" + hstatCycle);
        lines.add("");

        Set<String> profileNameSet = new HashSet<>();
        profileNameSet.addAll(lamerControlProfileValues.keySet());
        for (String profile : lamerControlProfiles.values()) {
            if (profile != null && !profile.isBlank()) {
                profileNameSet.add(normalizeKey(profile));
            }
        }
        List<String> profileNames = new ArrayList<>(profileNameSet);
        profileNames.sort(String::compareToIgnoreCase);
        if (!profileNames.isEmpty()) {
            lines.add("% lamercontrol profile structure:");
            lines.add("%  X (string):");
            lines.add("%  Y (string):");
            lines.add("%  Z (int):");
            for (String profileName : profileNames) {
                if (profileName == null || profileName.isBlank()) {
                    continue;
                }
                lines.add("lamercontrol profile");
                lines.add("\t" + profileName);

                Map<String, String> values = lamerControlProfileValues.getOrDefault(profileName, Map.of());
                if (!values.isEmpty()) {
                    List<Map.Entry<String, String>> rawLines = new ArrayList<>();
                    List<Map.Entry<String, String>> keyedValues = new ArrayList<>();
                    for (Map.Entry<String, String> entry : values.entrySet()) {
                        String valueKey = entry.getKey();
                        if (valueKey != null && valueKey.startsWith("__line")) {
                            rawLines.add(entry);
                        } else {
                            keyedValues.add(entry);
                        }
                    }

                    rawLines.sort(Comparator.comparingInt(e -> parseIntOrDefault(e.getKey().substring("__line".length()), Integer.MAX_VALUE)));
                    for (Map.Entry<String, String> entry : rawLines) {
                        if (entry.getValue() != null && !entry.getValue().isBlank()) {
                            lines.add("\t" + sanitizeDbScalar(entry.getValue()));
                        }
                    }

                    keyedValues.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));
                    for (Map.Entry<String, String> entry : keyedValues) {
                        String valueKey = entry.getKey();
                        String value = entry.getValue();
                        if (valueKey == null || valueKey.isBlank()) {
                            continue;
                        }
                        if (value == null || value.isBlank()) {
                            lines.add("\t" + sanitizeDbScalar(valueKey));
                        } else {
                            lines.add("\t" + sanitizeDbScalar(valueKey + " " + value));
                        }
                    }
                }
                lines.add("\tlamercontrol profile");
                lines.add("");
            }
        }

        List<String> channels = collectPersistedChannels();
        for (String channel : channels) {
            String key = channel.toLowerCase(Locale.ROOT);
            int flags = channelConfigFlags.getOrDefault(key, dbChannelFlags.getOrDefault(key, H_CHANNEL_COMMANDS));
            String welcome = channelWelcome.getOrDefault(key, "(null)");
            String ticketMessage = ticketMessages.getOrDefault(key, "(null)");
            String lamerProfile = lamerControlProfiles.getOrDefault(key, "default");
            Map<String, String> terms = channelTerms.getOrDefault(key, Map.of());
            List<String> termKeys = new ArrayList<>(terms.keySet());
            termKeys.sort(String::compareToIgnoreCase);

            lines.add("channel");
            lines.add("\t" + key);
            lines.add("\t" + Integer.toHexString(flags));
            lines.add("\t" + sanitizeDbScalar(welcome));
            lines.add("\t" + sanitizeDbScalar(lamerProfile));
            lines.add("\t300");
            lines.add("\t" + sanitizeDbScalar(ticketMessage));
            lines.add("\t0 % censor");
            lines.add("\t" + termKeys.size() + " % terms");
            for (String termKey : termKeys) {
                lines.add("\t\t" + termKey);
                lines.add("\t\t" + sanitizeDbScalar(terms.get(termKey)));
            }
            lines.add("");
        }

        List<String> accounts = collectPersistedAccounts();
        if (!accounts.isEmpty()) {
            lines.add("% account structure:");
            lines.add("%  name (string):");
            lines.add("%  level (integer) flags (integer) last_activity (integer):");
            long now = System.currentTimeMillis() / 1000;
            String nowHex = Long.toHexString(now);
            for (String account : accounts) {
                int conf = accountConfig.getOrDefault(account, 0);
                int level = resolveHelpmodAccountLevel(account);
                Map<String, Long> perChannel = accountChannelStats.getOrDefault(account, Map.of());
                List<Map.Entry<String, Long>> stats = new ArrayList<>();
                for (Map.Entry<String, Long> e : perChannel.entrySet()) {
                    if (e.getKey() == null || !e.getKey().startsWith("#")) {
                        continue;
                    }
                    if (e.getValue() == null || e.getValue() <= 0) {
                        continue;
                    }
                    stats.add(Map.entry(e.getKey().toLowerCase(Locale.ROOT), e.getValue()));
                }
                stats.sort((a, b) -> a.getKey().compareToIgnoreCase(b.getKey()));
                lines.add("account");
                lines.add("\t" + account);
                lines.add("\t" + level + "\t" + conf + "\t" + nowHex);
                lines.add("\t" + stats.size() + " % statistics for this channel");
                for (Map.Entry<String, Long> stat : stats) {
                    lines.add("\t" + stat.getKey() + "\t" + stat.getValue());
                }
            }
            lines.add("");
        }

        lines.add("% ban structure:");
        lines.add("%  banmask (string):");
        lines.add("%  reason (string):");
        lines.add("%  expiration (int):");
        lines.add("");

        List<String> globalTermKeys = new ArrayList<>(globalTerms.keySet());
        globalTermKeys.sort(String::compareToIgnoreCase);
        if (!globalTermKeys.isEmpty()) {
            lines.add("% term structure:");
            lines.add("%  name (string):");
            lines.add("%  description (string):");
            for (String termKey : globalTermKeys) {
                lines.add("term");
                lines.add("\t" + termKey);
                lines.add("\t" + sanitizeDbScalar(globalTerms.get(termKey)));
            }
            lines.add("");
        }

        lines.add("% ticket structure:");
        lines.add("%  channel (string)");
        lines.add("%  authname (string)");
        lines.add("%  expiration time (int)");

        List<String> ticketChannels = new ArrayList<>(channelTickets.keySet());
        ticketChannels.sort(String::compareToIgnoreCase);
        long now = System.currentTimeMillis() / 1000;
        for (String channel : ticketChannels) {
            Map<String, Long> tickets = channelTickets.get(channel);
            if (tickets == null || tickets.isEmpty()) {
                continue;
            }
            List<String> authKeys = new ArrayList<>(tickets.keySet());
            authKeys.sort(String::compareToIgnoreCase);
            for (String authKey : authKeys) {
                Long expiry = tickets.get(authKey);
                if (expiry == null || expiry <= now) {
                    continue;
                }
                String auth = authKey.startsWith("acc:") ? authKey.substring(4) : authKey;
                if (auth.isBlank()) {
                    continue;
                }
                lines.add("ticket");
                lines.add("\t" + channel.toLowerCase(Locale.ROOT));
                lines.add("\t" + normalizeKey(auth));
                lines.add("\t" + expiry);
                String message = ticketMessages.get(channel.toLowerCase(Locale.ROOT));
                if (message != null && !message.isBlank()) {
                    lines.add("\t" + sanitizeDbScalar(message));
                }
            }
        }
        lines.add("");

        lines.add("% report structure:");
        lines.add("%  channel reported");
        lines.add("%  channel reported to");

        List<String> reportSources = new ArrayList<>(reportRoutes.keySet());
        reportSources.sort(String::compareToIgnoreCase);
        for (String source : reportSources) {
            String target = reportRoutes.get(source);
            if (target == null || !source.startsWith("#") || !target.startsWith("#")) {
                continue;
            }
            lines.add("report");
            lines.add("\t" + source.toLowerCase(Locale.ROOT));
            lines.add("\t" + target.toLowerCase(Locale.ROOT));
        }
        lines.add("");

        Path tmp = targetFile.resolveSibling(targetFile.getFileName().toString() + ".tmp");
        Files.write(tmp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException moveEx) {
            Files.move(tmp, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private List<String> collectPersistedChannels() {
        Set<String> all = new HashSet<>();
        if (dbDefaultChannel != null && !dbDefaultChannel.isBlank()) {
            all.add(dbDefaultChannel.toLowerCase(Locale.ROOT));
        }
        all.addAll(managedChannels);
        all.addAll(dbChannelFlags.keySet());
        all.addAll(channelConfigFlags.keySet());
        all.addAll(channelWelcome.keySet());
        all.addAll(lamerControlProfiles.keySet());
        all.addAll(channelTerms.keySet());
        all.addAll(channelTickets.keySet());
        all.addAll(ticketMessages.keySet());
        List<String> channels = new ArrayList<>();
        for (String c : all) {
            if (c != null && !c.isBlank() && c.startsWith("#")) {
                channels.add(c.toLowerCase(Locale.ROOT));
            }
        }
        channels.sort(String::compareToIgnoreCase);
        return channels;
    }

    private List<String> collectPersistedAccounts() {
        List<String> accounts = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String account : accountWriteOrder) {
            if (account == null || account.isBlank()) {
                continue;
            }
            String key = account.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                accounts.add(key);
            }
        }

        Set<String> missing = new HashSet<>();
        missing.addAll(accountLevels.keySet());
        missing.addAll(accountConfig.keySet());

        List<String> tail = new ArrayList<>();
        for (String account : missing) {
            if (account == null || account.isBlank()) {
                continue;
            }
            String key = account.toLowerCase(Locale.ROOT);
            if (!seen.contains(key)) {
                tail.add(key);
            }
        }
        tail.sort(String::compareToIgnoreCase);
        accounts.addAll(tail);
        return accounts;
    }

    private String sanitizeDbScalar(String value) {
        if (value == null || value.isBlank()) {
            return "(null)";
        }
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private void handleTerm(String senderNumeric, String args, String replyChannel, boolean includeSenderPrefixInChannel) {
        java.util.function.Consumer<String> reply = msg -> sendTermReply(senderNumeric, replyChannel, msg, includeSenderPrefixInChannel);
        String[] tokens = splitArgs(args);
        int index = 0;
        String channel = null;
        if (tokens.length > 0 && tokens[0].startsWith("#")) {
            channel = tokens[0].toLowerCase(Locale.ROOT);
            index = 1;
        }
        String effectiveChannel = channel != null ? channel : (replyChannel != null && replyChannel.startsWith("#")
                ? replyChannel.toLowerCase(Locale.ROOT)
                : null);

        String op = tokens.length > index ? tokens[index].toUpperCase(Locale.ROOT) : "FIND";
        String param = tokens.length > index + 1 ? joinTokens(tokens, index + 1) : "";
        Map<String, String> terms = getTermMap(effectiveChannel);

        if (terms.isEmpty()) {
            reply.accept("No terms available.");
            return;
        }

        switch (op) {
            case "LIST": {
                String pattern = param.isBlank() ? "*" : param;
                List<String> names = new ArrayList<>();
                for (String name : terms.keySet()) {
                    if (wildcardMatch(name, pattern)) {
                        names.add(name);
                    }
                }
                names.sort(String::compareToIgnoreCase);
                if (names.isEmpty()) {
                    reply.accept("No terms match pattern: " + pattern);
                    return;
                }
                int max = Math.min(MAX_TOPICS, names.size());
                reply.accept("Terms (" + names.size() + "): " + String.join(", ", names.subList(0, max)));
                if (names.size() > max) {
                    reply.accept("... " + (names.size() - max) + " more terms");
                }
                break;
            }
            case "LISTFULL": {
                int sent = 0;
                for (Map.Entry<String, String> e : terms.entrySet()) {
                    reply.accept(e.getKey() + ": " + e.getValue());
                    sent++;
                    if (sent >= MAX_COMMAND_HELP_LINES) {
                        reply.accept("(Term list truncated)");
                        break;
                    }
                }
                break;
            }
            case "GET": {
                if (param.isBlank()) {
                    reply.accept("Usage: TERM [#channel] GET <term>");
                    return;
                }
                String key = normalizeKey(param);
                String value = terms.get(key);
                if (value == null) {
                    reply.accept("Term not found: " + param);
                    return;
                }
                reply.accept(key + ": " + value);
                termUsageStats.merge(key, 1L, Long::sum);
                if (effectiveChannel != null) {
                    channelTermUsageStats.computeIfAbsent(effectiveChannel, k -> new ConcurrentHashMap<>()).merge(key, 1L, Long::sum);
                    recordAccountChannelStat(senderNumeric, effectiveChannel);
                }
                break;
            }
            case "ADD": {
                Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
                if (!isStaffLike(actor)) {
                    reply.accept("Access denied: staff/oper required for TERM ADD.");
                    return;
                }
                String[] addTokens = splitArgs(param);
                if (addTokens.length < 1 || addTokens[0].isBlank()) {
                    reply.accept("Cannot add term: Term name not specified");
                    return;
                }
                if (addTokens.length < 2) {
                    reply.accept("Cannot add term: Term description not specified");
                    return;
                }
                String key = normalizeKey(addTokens[0]);
                String value = joinTokens(addTokens, 1).trim();
                if (value.startsWith(":")) {
                    value = value.substring(1).trim();
                }
                if (value.isBlank()) {
                    reply.accept("Cannot add term: Term description not specified");
                    return;
                }

                Map<String, String> targetTerms;
                if (effectiveChannel != null) {
                    targetTerms = channelTerms.computeIfAbsent(effectiveChannel, k -> new ConcurrentHashMap<>());
                } else {
                    targetTerms = globalTerms;
                }
                if (targetTerms.containsKey(key)) {
                    reply.accept("Cannot add term: Term " + key + " is already added");
                    return;
                }
                targetTerms.put(key, value);
                reply.accept("Term " + key + " added successfully");
                break;
            }
            case "DEL": {
                Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
                if (!isStaffLike(actor)) {
                    reply.accept("Access denied: staff/oper required for TERM DEL.");
                    return;
                }
                String[] delTokens = splitArgs(param);
                if (delTokens.length < 1 || delTokens[0].isBlank()) {
                    reply.accept("Cannot delete term: Term name not specified");
                    return;
                }
                Map<String, String> targetTerms = effectiveChannel == null
                        ? globalTerms
                    : channelTerms.getOrDefault(effectiveChannel, Map.of());
                for (String rawKey : delTokens) {
                    String key = normalizeKey(rawKey);
                    if (key.isBlank()) {
                        continue;
                    }
                    if (!targetTerms.containsKey(key)) {
                        reply.accept("Cannot delete term: Term " + key + " not found");
                        continue;
                    }
                    if (targetTerms == globalTerms) {
                        globalTerms.remove(key);
                    } else {
                        targetTerms.remove(key);
                    }
                    reply.accept("Term " + key + " deleted successfully");
                }
                break;
            }
            case "FIND":
            default: {
                String pattern = op.equals("FIND") ? param : joinTokens(tokens, index);
                if (pattern.isBlank()) {
                    reply.accept("Usage: TERM [#channel] FIND <pattern>");
                    return;
                }
                String foundKey = null;
                for (String key : terms.keySet()) {
                    if (wildcardMatch(key, pattern) || key.contains(normalizeKey(pattern))) {
                        foundKey = key;
                        break;
                    }
                }
                if (foundKey == null) {
                    reply.accept("No term match for: " + pattern);
                    return;
                }
                reply.accept(foundKey + ": " + terms.get(foundKey));
                termUsageStats.merge(foundKey, 1L, Long::sum);
                if (effectiveChannel != null) {
                    channelTermUsageStats.computeIfAbsent(effectiveChannel, k -> new ConcurrentHashMap<>()).merge(foundKey, 1L, Long::sum);
                    recordAccountChannelStat(senderNumeric, effectiveChannel);
                }
                break;
            }
        }
    }

    private void handleWhoAmI(String senderNumeric) {
        Users user = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (user == null) {
            return;
        }
        String account = resolveAccountName(user);
        if (account == null) {
            sendNotice(senderNumeric, "You are not authenticated to a services account.");
            return;
        }
        int level = resolveHelpmodAccountLevel(account);
        int acconf = accountConfig.getOrDefault(account.toLowerCase(Locale.ROOT), 0);
        sendNotice(senderNumeric, "WHOAMI: account=" + account + ", level=" + commandLevelName(level) + ", acconf=" + formatAcconf(acconf));
    }

    private void handleWhoIs(String senderNumeric, String args) {
        if (args == null || args.isBlank()) {
            sendNotice(senderNumeric, "Usage: WHOIS <nick|#account>");
            return;
        }
        String account = resolveAccountFromArg(args.trim());
        if (account == null || account.isBlank()) {
            sendNotice(senderNumeric, "Unknown account/user: " + args.trim());
            return;
        }
        if (!accountLevels.containsKey(account.toLowerCase(Locale.ROOT))
                && (mi == null || mi.getDb() == null || !mi.getDb().isRegistered(account))) {
            sendNotice(senderNumeric, "No registered account: " + account);
            return;
        }

        int level = resolveHelpmodAccountLevel(account);
        int acconf = accountConfig.getOrDefault(account.toLowerCase(Locale.ROOT), 0);
        sendNotice(senderNumeric, "WHOIS: account=" + account + ", level=" + commandLevelName(level) + ", acconf=" + formatAcconf(acconf));

        if (mi != null && mi.getDb() != null && mi.getDb().isRegistered(account)) {
            String email = safeValue(mi.getDb().getData("email", account));
            String info = safeValue(mi.getDb().getData("info", account));
            if (!"-".equals(email)) {
                sendNotice(senderNumeric, "Email: " + email);
            }
            if (!"-".equals(info)) {
                sendNotice(senderNumeric, "Info: " + info);
            }
        }
    }

    private void handleSeen(String senderNumeric, String args) {
        if (args == null || args.isBlank()) {
            sendNotice(senderNumeric, "Usage: SEEN <nick|#account>");
            return;
        }
        String account = resolveAccountFromArg(args.trim());
        String lookup = normalizeAccountArg(account);

        String onlineNumeric = resolveUserNumeric(args.trim());
        if (onlineNumeric != null) {
            sendNotice(senderNumeric, "SEEN: " + nickOrNumeric(onlineNumeric) + " is currently online.");
            return;
        }

        if (mi == null || mi.getDb() == null || lookup.isBlank() || !mi.getDb().isRegistered(lookup)) {
            sendNotice(senderNumeric, "No seen information for: " + args.trim());
            return;
        }

        String lastAuth = safeValue(mi.getDb().getData("lastauth", lookup));
        if ("-".equals(lastAuth)) {
            sendNotice(senderNumeric, "SEEN: no lastauth data for account " + lookup);
            return;
        }

        long ts;
        try {
            ts = Long.parseLong(lastAuth);
        } catch (NumberFormatException ex) {
            sendNotice(senderNumeric, "SEEN: invalid timestamp for account " + lookup + " (" + lastAuth + ")");
            return;
        }

        long age = Math.max(0, (System.currentTimeMillis() / 1000) - ts);
        sendNotice(senderNumeric, "SEEN: account " + lookup + " last authenticated " + formatDuration(age) + " ago.");
    }

    private void handleListUser(String senderNumeric, String args) {
        if (accountLevels.isEmpty()) {
            sendNotice(senderNumeric, "Database unavailable.");
            return;
        }

        String[] tokens = splitArgs(args);
        String levelFilter = null;
        String pattern = "*";

        if (tokens.length > 0 && isLevelToken(tokens[0])) {
            levelFilter = tokens[0];
            if (tokens.length > 1) {
                pattern = tokens[1];
            }
        } else if (tokens.length > 0) {
            pattern = tokens[0];
        }

        List<String> matched = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : accountLevels.entrySet()) {
            String user = entry.getKey();
            int level = entry.getValue() == null ? 1 : entry.getValue();

            if (levelFilter != null && !matchesLevelFilter(level, levelFilter)) {
                continue;
            }
            if (!wildcardMatch(user, pattern)) {
                continue;
            }
            matched.add(user + "(" + commandLevelName(level) + ")");
        }
        matched.sort(String::compareToIgnoreCase);
        if (matched.isEmpty()) {
            sendNotice(senderNumeric, "No users found.");
            return;
        }
        int max = Math.min(MAX_LIST_USERS, matched.size());
        sendNotice(senderNumeric, "Users (" + matched.size() + "): " + String.join(", ", matched.subList(0, max)));
        if (matched.size() > max) {
            sendNotice(senderNumeric, "... " + (matched.size() - max) + " more users");
        }
    }

    private void handleDelUser(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isAdminLike(actor)) {
            sendNotice(senderNumeric, "Access denied: admin/oper required.");
            return;
        }
        if (args == null || args.isBlank()) {
            sendNotice(senderNumeric, "Usage: DELUSER <account>");
            return;
        }
        String account = normalizeAccountArg(args.trim());
        if (mi == null || mi.getDb() == null || !mi.getDb().isRegistered(account)) {
            sendNotice(senderNumeric, "No registered account: " + account);
            return;
        }
        int flags = mi.getDb().getFlags(account);
        flags = Userflags.setFlag(flags, Userflags.Flag.INACTIVE);
        flags = Userflags.clearFlag(flags, Userflags.Flag.HELPER);
        flags = Userflags.clearFlag(flags, Userflags.Flag.STAFF);
        flags = Userflags.clearFlag(flags, Userflags.Flag.OPER);
        flags = Userflags.clearFlag(flags, Userflags.Flag.ADMIN);
        flags = Userflags.clearFlag(flags, Userflags.Flag.DEV);
        mi.getDb().updateData("flags", account, flags);
        sendNotice(senderNumeric, "Account marked inactive: " + account);
    }

    private void handleLevel(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isAdminLike(actor)) {
            sendNotice(senderNumeric, "Access denied: admin/oper required.");
            return;
        }
        String[] t = splitArgs(args);
        if (t.length < 1) {
            sendNotice(senderNumeric, "Usage: LEVEL <account> [USER|HELPER|TRIAL|STAFF|OPER|ADMIN|DEV]");
            return;
        }
        String account = normalizeAccountArg(t[0]);
        if (mi == null || mi.getDb() == null || !mi.getDb().isRegistered(account)) {
            sendNotice(senderNumeric, "No registered account: " + account);
            return;
        }

        int currentFlags = mi.getDb().getFlags(account);
        if (t.length == 1) {
            int level = resolveHelpmodAccountLevel(account);
            sendNotice(senderNumeric, "LEVEL " + account + ": " + commandLevelName(level));
            return;
        }

        int requestedLevel = parseCommandLevelToken(t[1]);
        if (requestedLevel < 0) {
            sendNotice(senderNumeric, "Invalid level. Allowed: USER, HELPER, TRIAL, STAFF, OPER, ADMIN, DEV");
            return;
        }

        String normalizedLevel = normalizeLevelToken(t[1]);
        if (normalizedLevel == null) {
            sendNotice(senderNumeric, "Invalid level. Allowed: USER, HELPER, TRIAL, STAFF, OPER, ADMIN, DEV");
            return;
        }

        int newFlags = applyLevel(currentFlags, normalizedLevel);
        if (newFlags < 0) {
            sendNotice(senderNumeric, "Invalid level. Allowed: USER, HELPER, TRIAL, STAFF, OPER, ADMIN, DEV");
            return;
        }
        mi.getDb().updateData("flags", account, newFlags);
        accountLevels.put(account.toLowerCase(Locale.ROOT), requestedLevel);
        sendNotice(senderNumeric, "LEVEL updated for " + account + ": " + commandLevelName(requestedLevel));
    }

    private void handleLevelShortcut(String senderNumeric, String args, String level) {
        if (args == null || args.isBlank()) {
            sendNotice(senderNumeric, "Usage: " + level + " <account|nick>");
            return;
        }
        String account = resolveAccountFromArg(args.trim());
        if (account == null || account.isBlank()) {
            sendNotice(senderNumeric, "Unknown account/user: " + args.trim());
            return;
        }
        handleLevel(senderNumeric, normalizeAccountArg(account) + " " + level);
    }

    private void handleImproper(String senderNumeric, String args) {
        if (args == null || args.isBlank()) {
            sendNotice(senderNumeric, "Usage: IMPROPER <account|nick>");
            return;
        }
        String account = resolveAccountFromArg(args.trim());
        if (account == null || account.isBlank()) {
            sendNotice(senderNumeric, "Unknown account/user: " + args.trim());
            return;
        }
        handleDelUser(senderNumeric, normalizeAccountArg(account));
    }

    private void handleQueue(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (actor == null) {
            return;
        }

        String[] tokens = splitArgs(args);
        int index = 0;
        String channel = resolveDefaultHelpChannel();
        if (tokens.length > 0 && tokens[0].startsWith("#")) {
            channel = tokens[0];
            index = 1;
        }
        String key = channel.toLowerCase(Locale.ROOT);

        String op = (tokens.length > index ? tokens[index] : "LIST").toUpperCase(Locale.ROOT);
        String extra = tokens.length > index + 1 ? tokens[index + 1] : "";

        if (!isTrialLike(actor) && !"LIST".equals(op) && !"STATUS".equals(op) && !"SUMMARY".equals(op)) {
            sendNotice(senderNumeric, "Access denied: trial/staff/oper required for queue control.");
            return;
        }

        switch (op) {
            case "LIST":
            case "SUMMARY":
            case "STATUS": {
                Deque<String> queue = supportQueue.computeIfAbsent(key, k -> new LinkedList<>());
                int maintainValue = queueMaintainTarget.getOrDefault(key, queueMaintain.getOrDefault(key, false) ? 1 : 0);
                sendNotice(senderNumeric, "Queue " + channel + ": enabled=" + queueEnabled.getOrDefault(key, false)
                        + ", maintain=" + maintainValue + ", size=" + queue.size());
                if (!queue.isEmpty()) {
                    sendNotice(senderNumeric, "Queue users: " + queuePreview(queue, 15));
                }
                break;
            }
            case "NEXT": {
                int amount = parseIntOrDefault(extra, 1);
                if (amount < 1 || amount > 25) {
                    sendNotice(senderNumeric, "Invalid count for NEXT. Allowed range: 1-25.");
                    break;
                }
                int invited = 0;
                for (int i = 0; i < amount; i++) {
                    String next = pollNextQueueUser(key);
                    if (next == null) {
                        break;
                    }
                    sendText("%s%s I %s %s", numeric, getNumericSuffix(), next, channel);
                    sendNotice(senderNumeric, "Invited next user: " + nickOrNumeric(next) + " to " + channel);
                    if (!hasAccountConfigFlag(next, H_ACC_NOSPAM)) {
                        sendNotice(next, "You are next in support queue. Invited to " + channel + ".");
                    }
                    invited++;
                }
                if (invited == 0) {
                    sendNotice(senderNumeric, "Queue for " + channel + " is empty.");
                }
                break;
            }
            case "DONE": {
                String[] doneTargets;
                if (tokens.length <= index + 1) {
                    doneTargets = new String[]{senderNumeric};
                } else {
                    doneTargets = java.util.Arrays.copyOfRange(tokens, index + 1, tokens.length);
                }
                int removedCount = 0;
                for (String target : doneTargets) {
                    String numericTarget = resolveUserNumeric(target);
                    if (numericTarget == null) {
                        sendNotice(senderNumeric, "Unknown user: " + target);
                        continue;
                    }
                    boolean removed = supportQueue.computeIfAbsent(key, k -> new LinkedList<>()).remove(numericTarget);
                    if (removed) {
                        removedCount++;
                    }
                }
                sendNotice(senderNumeric, "Queue done: removed " + removedCount + " user(s) on " + channel);
                break;
            }
            case "ON":
                queueEnabled.put(key, true);
                setChannelFlag(key, H_QUEUE, true);
                sendNotice(senderNumeric, "Queue enabled for " + channel);
                break;
            case "OFF":
                queueEnabled.put(key, false);
                setChannelFlag(key, H_QUEUE, false);
                sendNotice(senderNumeric, "Queue disabled for " + channel);
                break;
            case "MAINTAIN": {
                if (extra.equalsIgnoreCase("on") || extra.equalsIgnoreCase("true")) {
                    queueMaintain.put(key, true);
                    queueMaintainTarget.put(key, 1);
                    setChannelFlag(key, H_QUEUE_MAINTAIN, true);
                    sendNotice(senderNumeric, "Auto-queue maintenance enabled for " + channel);
                } else if (extra.equalsIgnoreCase("off") || extra.equalsIgnoreCase("false")) {
                    queueMaintain.put(key, false);
                    queueMaintainTarget.put(key, 0);
                    setChannelFlag(key, H_QUEUE_MAINTAIN, false);
                    sendNotice(senderNumeric, "Auto-queue maintenance disabled for " + channel);
                } else if (!extra.isBlank()) {
                    int parsed = parseIntOrDefault(extra, Integer.MIN_VALUE);
                    if (parsed < 0 || parsed > 25) {
                        sendNotice(senderNumeric, "Invalid MAINTAIN value. Allowed range: 0-25.");
                        break;
                    }
                    int n = parsed;
                    queueMaintainTarget.put(key, n);
                    queueMaintain.put(key, n > 0);
                    setChannelFlag(key, H_QUEUE_MAINTAIN, n > 0);
                    sendNotice(senderNumeric, "Auto-queue target for " + channel + " set to " + n);
                } else {
                    int n = queueMaintainTarget.getOrDefault(key, queueMaintain.getOrDefault(key, false) ? 1 : 0);
                    sendNotice(senderNumeric, "Auto-queue target for " + channel + ": " + n);
                }
                break;
            }
            case "RESET": {
                supportQueue.computeIfAbsent(key, k -> new LinkedList<>()).clear();
                sendNotice(senderNumeric, "Queue reset for " + channel);
                break;
            }
            default:
                sendNotice(senderNumeric, "Usage: QUEUE [#channel] [LIST|SUMMARY|NEXT [n]|DONE [nick..]|ON|OFF|MAINTAIN [n]|RESET]");
                break;
        }
    }

    private void handleTicket(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required.");
            return;
        }

        String[] t = splitArgs(args);
        if (t.length < 1 || t[0].isBlank()) {
            sendNotice(senderNumeric, "Usage: TICKET <nick> [#channel] [minutes]");
            return;
        }
        String targetNumeric = resolveUserNumeric(t[0]);
        if (targetNumeric == null) {
            sendNotice(senderNumeric, "Unknown user: " + t[0]);
            return;
        }
        Users targetUser = st != null && st.getUsers() != null ? st.getUsers().get(targetNumeric) : null;
        String targetAccount = normalizeAccountArg(resolveAccountName(targetUser));
        if (targetAccount == null || targetAccount.isBlank()) {
            sendNotice(senderNumeric, "Cannot create ticket: target user has no account.");
            return;
        }
        String channel = (t.length > 1 && t[1].startsWith("#")) ? t[1] : resolveDefaultHelpChannel();
        String key = channel.toLowerCase(Locale.ROOT);
        int minutes = 30;
        String minuteToken = t.length > 2 ? t[2] : null;
        if (minuteToken != null) {
            try {
                minutes = Math.max(1, Math.min(10080, Integer.parseInt(minuteToken)));
            } catch (NumberFormatException ignored) {
            }
        }
        long expiry = (System.currentTimeMillis() / 1000) + (minutes * 60L);
        String ticketKey = "acc:" + normalizeKey(targetAccount);
        channelTickets.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(ticketKey, expiry);
        sendNotice(senderNumeric, "Ticket created for " + nickOrNumeric(targetNumeric) + " (#" + targetAccount + ") on " + channel + " for " + minutes + " minute(s).");
        sendNotice(targetNumeric, "You received a support ticket for " + channel + " (" + minutes + " minute(s)). Use INVITE " + channel + ".");
    }

    private void handleResolveTicket(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required.");
            return;
        }
        String[] t = splitArgs(args);
        if (t.length < 1) {
            sendNotice(senderNumeric, "Usage: RESOLVE <nick> [#channel]");
            return;
        }
        String target = resolveTicketKey(t[0]);
        if (target == null) {
            sendNotice(senderNumeric, "Unknown user/account: " + t[0]);
            return;
        }
        String channel = (t.length > 1 && t[1].startsWith("#")) ? t[1] : resolveDefaultHelpChannel();
        String key = channel.toLowerCase(Locale.ROOT);
        boolean removed = channelTickets.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).remove(target) != null;
        sendNotice(senderNumeric, removed ? "Ticket resolved for " + renderTicketTarget(target) : "No ticket found.");
    }

    private void handleTickets(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required.");
            return;
        }
        String channel = parseChannelArg(args, resolveDefaultHelpChannel());
        String key = channel.toLowerCase(Locale.ROOT);
        Map<String, Long> tickets = channelTickets.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        cleanupExpiredTickets(key);
        if (tickets.isEmpty()) {
            sendNotice(senderNumeric, "No active tickets for " + channel);
            return;
        }
        List<String> rows = new ArrayList<>();
        long now = System.currentTimeMillis() / 1000;
        for (Map.Entry<String, Long> e : tickets.entrySet()) {
            long left = Math.max(0, e.getValue() - now);
            rows.add(renderTicketTarget(e.getKey()) + "(" + (left / 60) + "m)");
        }
        sendNotice(senderNumeric, "Tickets " + channel + ": " + String.join(", ", rows));
    }

    private void handleShowTicket(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required.");
            return;
        }
        String[] t = splitArgs(args);
        if (t.length < 1) {
            sendNotice(senderNumeric, "Usage: SHOWTICKET <nick> [#channel]");
            return;
        }
        String target = resolveTicketKey(t[0]);
        if (target == null) {
            sendNotice(senderNumeric, "Unknown user/account: " + t[0]);
            return;
        }
        String channel = (t.length > 1 && t[1].startsWith("#")) ? t[1] : resolveDefaultHelpChannel();
        String key = channel.toLowerCase(Locale.ROOT);
        cleanupExpiredTickets(key);
        Long expiry = channelTickets.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).get(target);
        if (expiry == null) {
            sendNotice(senderNumeric, "No ticket for " + renderTicketTarget(target) + " on " + channel);
            return;
        }
        long left = Math.max(0, expiry - (System.currentTimeMillis() / 1000));
        sendNotice(senderNumeric, "Ticket for " + renderTicketTarget(target) + " on " + channel + " expires in " + (left / 60) + " minute(s).");
    }

    private void handleTicketMessage(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required.");
            return;
        }
        String[] t = splitArgs(args);
        String channel = resolveDefaultHelpChannel();
        int idx = 0;
        if (t.length > 0 && t[0].startsWith("#")) {
            channel = t[0];
            idx = 1;
        }
        String key = channel.toLowerCase(Locale.ROOT);
        if (t.length <= idx) {
            String msg = ticketMessages.getOrDefault(key, "(none)");
            sendNotice(senderNumeric, "Ticket message " + channel + ": " + msg);
            return;
        }
        String newMsg = args.substring(args.indexOf(t[idx])).trim();
        ticketMessages.put(key, newMsg);
        sendNotice(senderNumeric, "Ticket message updated for " + channel);
    }

    private String buildTermFindArgs(String args) {
        String[] t = splitArgs(args);
        if (t.length == 0) {
            return "FIND";
        }
        if (t[0].startsWith("#")) {
            if (t.length == 1) {
                return t[0] + " FIND";
            }
            return t[0] + " FIND " + joinTokens(t, 1);
        }
        return "FIND " + joinTokens(t, 0);
    }

    private void handleSimpleMode(String senderNumeric, String args, String mode, String usage) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        int requiredLevel = mode != null && mode.contains("o") ? 4 : 3;
        if (getUserCommandLevel(actor) < requiredLevel) {
            sendNotice(senderNumeric, requiredLevel >= 4
                    ? "Access denied: staff/oper required."
                    : "Access denied: trial/staff/oper required.");
            return;
        }

        String[] tokens = splitArgs(args);
        if (tokens.length < 1 || !tokens[0].startsWith("#")) {
            sendNotice(senderNumeric, usage);
            return;
        }

        String channel = tokens[0].toLowerCase(Locale.ROOT);
        Channel chan = st != null && st.getChannel() != null ? st.getChannel().get(channel) : null;
        if (chan == null) {
            sendNotice(senderNumeric, "Unknown channel: " + channel);
            return;
        }

        List<String> targets = new ArrayList<>();
        if (tokens.length == 1) {
            targets.add(senderNumeric);
        } else {
            for (int i = 1; i < tokens.length && targets.size() < 6; i++) {
                String numericTarget = resolveUserNumeric(tokens[i]);
                if (numericTarget == null) {
                    sendNotice(senderNumeric, "Unknown user: " + tokens[i]);
                    continue;
                }
                if (!chan.getUsers().contains(numericTarget)) {
                    sendNotice(senderNumeric, nickOrNumeric(numericTarget) + " is not on " + channel);
                    continue;
                }
                targets.add(numericTarget);
            }
        }

        if (targets.isEmpty()) {
            sendNotice(senderNumeric, "No valid targets.");
            return;
        }

        for (String target : targets) {
            sendText("%s%s M %s %s %s", numeric, getNumericSuffix(), channel, mode, target);
        }
        sendNotice(senderNumeric, "Applied " + mode + " to " + targets.size() + " user(s) on " + channel);
    }

    private void handleKick(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isTrialLike(actor)) {
            sendNotice(senderNumeric, "Access denied: trial/staff/oper required.");
            return;
        }

        String[] tokens = splitArgs(args);
        if (tokens.length < 2 || !tokens[0].startsWith("#")) {
            sendNotice(senderNumeric, "Usage: KICK <#channel> <nick1..nick6> [:reason]");
            return;
        }

        String channel = tokens[0].toLowerCase(Locale.ROOT);
        Channel chan = st != null && st.getChannel() != null ? st.getChannel().get(channel) : null;
        if (chan == null) {
            sendNotice(senderNumeric, "Unknown channel: " + channel);
            return;
        }

        String reason = "Requested by " + nickOrNumeric(senderNumeric);
        int reasonStart = args == null ? -1 : args.indexOf(" :");
        String targetPart = args == null ? "" : args;
        if (reasonStart >= 0) {
            targetPart = args.substring(0, reasonStart).trim();
            reason = args.substring(reasonStart + 2).trim();
            if (reason.isBlank()) {
                reason = "Requested by " + nickOrNumeric(senderNumeric);
            }
        }

        String[] targetTokens = splitArgs(targetPart);
        int kicked = 0;
        for (int i = 1; i < targetTokens.length && kicked < 6; i++) {
            String targetNumeric = resolveUserNumeric(targetTokens[i]);
            if (targetNumeric == null) {
                sendNotice(senderNumeric, "Unknown user: " + targetTokens[i]);
                continue;
            }
            if (!chan.getUsers().contains(targetNumeric)) {
                sendNotice(senderNumeric, nickOrNumeric(targetNumeric) + " is not on " + channel);
                continue;
            }
            sendText("%s%s K %s %s :%s", numeric, getNumericSuffix(), channel, targetNumeric, reason);
            kicked++;
        }

        if (kicked == 0) {
            sendNotice(senderNumeric, "No valid kick targets.");
            return;
        }
        sendNotice(senderNumeric, "KICK completed on " + channel + ": " + kicked + " user(s).");
    }

    private void handleOut(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required.");
            return;
        }

        String channel = resolveDefaultHelpChannel().toLowerCase(Locale.ROOT);
        Channel chan = st != null && st.getChannel() != null ? st.getChannel().get(channel) : null;
        if (chan == null) {
            sendNotice(senderNumeric, "Default help channel is not active: " + channel);
            return;
        }

        String reason = "Enough complaining";
        int reasonStart = args == null ? -1 : args.indexOf(":");
        String targetPart = args == null ? "" : args;
        if (reasonStart >= 0) {
            targetPart = args.substring(0, reasonStart).trim();
            reason = args.substring(reasonStart + 1).trim();
        }

        String[] targets = splitArgs(targetPart);
        int handled = 0;
        for (int i = 0; i < targets.length && handled < 6; i++) {
            String targetNumeric = resolveUserNumeric(targets[i]);
            if (targetNumeric == null) {
                continue;
            }
            Users targetUser = st.getUsers().get(targetNumeric);
            if (targetUser == null || !chan.getUsers().contains(targetNumeric)) {
                continue;
            }
            String mask = "*!*@" + targetUser.getHost();
            long expiry = (System.currentTimeMillis() / 1000) + 900;
            channelBans.computeIfAbsent(channel, k -> new ConcurrentHashMap<>()).put(mask, new BanRecord(mask, expiry, reason, nickOrNumeric(senderNumeric)));
            sendText("%s%s M %s +b %s", numeric, getNumericSuffix(), channel, mask);
            sendText("%s%s K %s %s :%s", numeric, getNumericSuffix(), channel, targetNumeric, reason);
            handled++;
        }

        sendNotice(senderNumeric, handled == 0 ? "No valid OUT targets." : "OUT applied to " + handled + " user(s).");
    }

    private void handleGlobalBan(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isAdminLike(actor)) {
            sendNotice(senderNumeric, "Access denied: admin/oper required.");
            return;
        }

        String[] t = splitArgs(args);
        String op = t.length == 0 ? "LIST" : t[0].toUpperCase(Locale.ROOT);
        switch (op) {
            case "LIST": {
                String pattern = t.length > 1 ? t[1] : "*";
                int shown = 0;
                for (BanRecord b : globalBans.values()) {
                    if (!wildcardMatch(b.mask, pattern)) {
                        continue;
                    }
                    sendNotice(senderNumeric, "BAN " + b.mask + " exp=" + b.expiresAt + " reason=" + b.reason);
                    shown++;
                    if (shown >= MAX_TOPICS) {
                        sendNotice(senderNumeric, "(Ban list truncated)");
                        break;
                    }
                }
                if (shown == 0) {
                    sendNotice(senderNumeric, "No global bans found.");
                }
                break;
            }
            case "ADD": {
                if (t.length < 2) {
                    sendNotice(senderNumeric, "Usage: BAN ADD <mask> [duration] [reason]");
                    return;
                }
                String mask = t[1];
                long duration = t.length > 2 ? parseDurationSeconds(t[2], 0L) : 0L;
                long expiry = duration <= 0 ? Long.MAX_VALUE : (System.currentTimeMillis() / 1000) + duration;
                String reason = t.length > 3 ? joinTokens(t, 3) : "No reason";
                globalBans.put(mask, new BanRecord(mask, expiry, reason, nickOrNumeric(senderNumeric)));
                sendNotice(senderNumeric, "Global ban added: " + mask);
                break;
            }
            case "DEL": {
                if (t.length < 2) {
                    sendNotice(senderNumeric, "Usage: BAN DEL <mask1> .. <mask6>");
                    return;
                }
                int removed = 0;
                for (int i = 1; i < t.length && i <= 6; i++) {
                    if (globalBans.remove(t[i]) != null) {
                        removed++;
                    }
                }
                sendNotice(senderNumeric, "Global bans removed: " + removed);
                break;
            }
            default:
                sendNotice(senderNumeric, "Usage: BAN [LIST [pattern]|ADD <mask> [duration] [reason]|DEL <mask..>]");
                break;
        }
    }

    private void handleChanBan(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isTrialLike(actor)) {
            sendNotice(senderNumeric, "Access denied: trial/staff/oper required.");
            return;
        }
        String[] t = splitArgs(args);
        if (t.length < 2 || !t[0].startsWith("#")) {
            sendNotice(senderNumeric, "Usage: CHANBAN <#channel> [ADD|DEL|LIST] [mask..]");
            return;
        }
        String channel = t[0].toLowerCase(Locale.ROOT);
        String op = t[1].toUpperCase(Locale.ROOT);
        Map<String, BanRecord> bans = channelBans.computeIfAbsent(channel, k -> new ConcurrentHashMap<>());
        switch (op) {
            case "ADD": {
                int added = 0;
                for (int i = 2; i < t.length && added < 6; i++) {
                    String mask = t[i];
                    bans.put(mask, new BanRecord(mask, Long.MAX_VALUE, "chanban", nickOrNumeric(senderNumeric)));
                    sendText("%s%s M %s +b %s", numeric, getNumericSuffix(), channel, mask);
                    added++;
                }
                sendNotice(senderNumeric, "CHANBAN ADD: " + added + " mask(s)");
                break;
            }
            case "DEL": {
                int removed = 0;
                for (int i = 2; i < t.length && removed < 6; i++) {
                    String mask = t[i];
                    if (bans.remove(mask) != null) {
                        sendText("%s%s M %s -b %s", numeric, getNumericSuffix(), channel, mask);
                        removed++;
                    }
                }
                sendNotice(senderNumeric, "CHANBAN DEL: " + removed + " mask(s)");
                break;
            }
            case "LIST": {
                String pattern = t.length > 2 ? t[2] : "*";
                int shown = 0;
                for (BanRecord b : bans.values()) {
                    if (!wildcardMatch(b.mask, pattern)) {
                        continue;
                    }
                    sendNotice(senderNumeric, "CHANBAN " + channel + ": " + b.mask + " (" + b.reason + ")");
                    shown++;
                    if (shown >= MAX_TOPICS) {
                        sendNotice(senderNumeric, "(Channel ban list truncated)");
                        break;
                    }
                }
                if (shown == 0) {
                    sendNotice(senderNumeric, "No channel bans found.");
                }
                break;
            }
            default:
                sendNotice(senderNumeric, "Usage: CHANBAN <#channel> [ADD|DEL|LIST] [mask..]");
                break;
        }
    }

    private void handleCensor(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required.");
            return;
        }
        String[] t = splitArgs(args);
        if (t.length < 1 || !t[0].startsWith("#")) {
            sendNotice(senderNumeric, "Usage: CENSOR <#channel> [ADD|DEL|LIST] ...");
            return;
        }
        String channel = t[0].toLowerCase(Locale.ROOT);
        String op = t.length > 1 ? t[1].toUpperCase(Locale.ROOT) : "LIST";
        List<CensorEntry> list = channelCensors.computeIfAbsent(channel, k -> new LinkedList<>());

        switch (op) {
            case "ADD":
                if (t.length < 4) {
                    sendNotice(senderNumeric, "Usage: CENSOR <#channel> ADD <warn|kick|chanban|ban> <pattern> [reason]");
                    return;
                }
                String type = t[2].toLowerCase(Locale.ROOT);
                String pattern = t[3];
                String reason = t.length > 4 ? joinTokens(t, 4) : "censor";
                list.add(new CensorEntry(type, pattern, reason));
                sendNotice(senderNumeric, "CENSOR added for " + channel + ": " + type + " " + pattern);
                break;
            case "DEL":
                if (t.length < 3) {
                    sendNotice(senderNumeric, "Usage: CENSOR <#channel> DEL <index>");
                    return;
                }
                int idx = parseIntOrDefault(t[2].replace("#", ""), -1) - 1;
                if (idx < 0 || idx >= list.size()) {
                    sendNotice(senderNumeric, "Invalid censor index.");
                    return;
                }
                CensorEntry removed = list.remove(idx);
                sendNotice(senderNumeric, "CENSOR removed: " + removed.type + " " + removed.pattern);
                break;
            case "LIST":
            default:
                if (list.isEmpty()) {
                    sendNotice(senderNumeric, "No censor entries for " + channel);
                    return;
                }
                for (int i = 0; i < list.size() && i < MAX_COMMAND_HELP_LINES; i++) {
                    CensorEntry ce = list.get(i);
                    sendNotice(senderNumeric, "#" + (i + 1) + " " + ce.type + " " + ce.pattern + " :" + ce.reason);
                }
                break;
        }
    }

    private void handleDnmo(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required.");
            return;
        }
        String[] t = splitArgs(args);
        if (t.length == 0) {
            sendNotice(senderNumeric, "Usage: DNMO <nick1> .. <nickN>");
            return;
        }
        int moved = 0;
        for (int i = 0; i < t.length; i++) {
            String target = resolveUserNumeric(t[i]);
            if (target == null) {
                continue;
            }
            for (Map.Entry<String, Deque<String>> e : supportQueue.entrySet()) {
                Deque<String> queue = e.getValue();
                if (queue.remove(target)) {
                    queue.addLast(target);
                    moved++;
                }
            }
            sendNotice(target, "Please do not message opers directly; you were moved to the end of the support queue.");
        }
        sendNotice(senderNumeric, "DNMO processed. Queue positions moved: " + moved);
    }

    private void handleEveryoneOut(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required.");
            return;
        }

        String[] t = splitArgs(args);
        String channel = t.length > 0 && t[0].startsWith("#") ? t[0].toLowerCase(Locale.ROOT) : resolveDefaultHelpChannel().toLowerCase(Locale.ROOT);
        String scope = t.length > 1 ? t[1].toLowerCase(Locale.ROOT) : "all";
        String reason = "clearing channel";
        int colonPos = args == null ? -1 : args.indexOf(":");
        if (colonPos >= 0) {
            reason = args.substring(colonPos + 1).trim();
        }

        Channel chan = st != null && st.getChannel() != null ? st.getChannel().get(channel) : null;
        if (chan == null) {
            sendNotice(senderNumeric, "Unknown channel: " + channel);
            return;
        }

        String me = numeric + getNumericSuffix();
        int kicked = 0;
        for (String userNumeric : chan.getUsers()) {
            if (userNumeric == null || userNumeric.equals(me)) {
                continue;
            }
            Users u = st.getUsers().get(userNumeric);
            if (u == null) {
                continue;
            }
            if (isStaffLike(u)) {
                continue;
            }
            if ("unauthed".equals(scope)) {
                String acc = resolveAccountName(u);
                if (acc != null && !acc.isBlank()) {
                    continue;
                }
            }
            sendText("%s%s K %s %s :%s", numeric, getNumericSuffix(), channel, userNumeric, reason);
            kicked++;
        }
        boolean inviteOnlyWasEnabled = isChannelModeEnabled(channel, 'i');
        if (!inviteOnlyWasEnabled) {
            sendText("%s%s M %s +i", numeric, getNumericSuffix(), channel);
            updateLocalChannelMode(channel, 'i', true);
        }
        sendNotice(senderNumeric,
                "EVERYONEOUT done on " + channel + ": kicked " + kicked + " user(s)"
                        + (inviteOnlyWasEnabled ? ", +i already set." : ", set +i."));
    }

    private void handleMessage(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isTrialLike(actor)) {
            sendNotice(senderNumeric, "Access denied: trial/staff/oper required.");
            return;
        }
        String[] t = splitArgs(args);
        if (t.length < 2 || !t[0].startsWith("#")) {
            sendNotice(senderNumeric, "Usage: MESSAGE <#channel> <text>");
            return;
        }
        String channel = t[0].toLowerCase(Locale.ROOT);
        String message = joinTokens(t, 1);
        String nick = nickOrNumeric(senderNumeric);
        sendText("%s%s P %s :[%s] %s", numeric, getNumericSuffix(), channel, nick, message);
        sendNotice(senderNumeric, "Message sent to " + channel);
    }

    private void handleLamerControl(String senderNumeric, String args) {
        String[] t = splitArgs(args);
        String channel = t.length > 0 && t[0].startsWith("#") ? t[0].toLowerCase(Locale.ROOT) : resolveDefaultHelpChannel().toLowerCase(Locale.ROOT);
        if (t.length <= 1) {
            sendNotice(senderNumeric, "LAMERCONTROL " + channel + ": " + lamerControlProfiles.getOrDefault(channel, "default"));
            return;
        }
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required.");
            return;
        }
        String token = t[1].toLowerCase(Locale.ROOT);
        if ("list".equals(token)) {
            List<String> profiles = new ArrayList<>(lamerControlProfileValues.keySet());
            profiles.sort(String::compareToIgnoreCase);
            if (profiles.isEmpty()) {
                sendNotice(senderNumeric, "No lamercontrol profiles defined.");
            } else {
                sendNotice(senderNumeric, "Lamercontrol profiles: " + String.join(", ", profiles));
            }
            return;
        }
        lamerControlProfiles.put(channel, token);
        sendNotice(senderNumeric, "LAMERCONTROL " + channel + " set to " + token);
    }

    private void handleLcEdit(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isAdminLike(actor)) {
            sendNotice(senderNumeric, "Access denied: admin/oper required.");
            return;
        }

        String[] t = splitArgs(args);
        if (t.length == 0) {
            sendNotice(senderNumeric, "Usage: LCEDIT [ADD|DEL|VIEW|LIST|EDIT] ...");
            return;
        }
        String op = t[0].toUpperCase(Locale.ROOT);
        switch (op) {
            case "ADD":
                if (t.length < 2) {
                    sendNotice(senderNumeric, "Usage: LCEDIT ADD <name>");
                    return;
                }
                lamerControlProfileValues.putIfAbsent(normalizeKey(t[1]), new ConcurrentHashMap<>());
                sendNotice(senderNumeric, "LC profile added: " + normalizeKey(t[1]));
                break;
            case "DEL":
                if (t.length < 2) {
                    sendNotice(senderNumeric, "Usage: LCEDIT DEL <name>");
                    return;
                }
                lamerControlProfileValues.remove(normalizeKey(t[1]));
                sendNotice(senderNumeric, "LC profile deleted: " + normalizeKey(t[1]));
                break;
            case "VIEW":
                if (t.length < 2) {
                    sendNotice(senderNumeric, "Usage: LCEDIT VIEW <name>");
                    return;
                }
                Map<String, String> values = lamerControlProfileValues.get(normalizeKey(t[1]));
                if (values == null || values.isEmpty()) {
                    sendNotice(senderNumeric, "No values for profile " + normalizeKey(t[1]));
                    return;
                }
                sendNotice(senderNumeric, "LCEDIT VIEW " + normalizeKey(t[1]) + ": " + values);
                break;
            case "LIST": {
                if (lamerControlProfileValues.isEmpty()) {
                    sendNotice(senderNumeric, "No LC profiles.");
                    return;
                }
                sendNotice(senderNumeric, "LC profiles: " + String.join(", ", new ArrayList<>(lamerControlProfileValues.keySet())));
                break;
            }
            case "EDIT":
                if (t.length < 4) {
                    sendNotice(senderNumeric, "Usage: LCEDIT EDIT <name> <component> <value>");
                    return;
                }
                lamerControlProfileValues
                        .computeIfAbsent(normalizeKey(t[1]), k -> new ConcurrentHashMap<>())
                        .put(normalizeKey(t[2]), joinTokens(t, 3));
                sendNotice(senderNumeric, "LCEDIT updated " + normalizeKey(t[1]) + ": " + normalizeKey(t[2]));
                break;
            default:
                sendNotice(senderNumeric, "Usage: LCEDIT [ADD|DEL|VIEW|LIST|EDIT] ...");
                break;
        }
    }

    private void handleIdleKick(String senderNumeric, String args) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isStaffLike(actor)) {
            sendNotice(senderNumeric, "Access denied: staff/oper required.");
            return;
        }

        String[] t = splitArgs(args);
        String channel = t.length > 0 && t[0].startsWith("#") ? t[0].toLowerCase(Locale.ROOT) : resolveDefaultHelpChannel().toLowerCase(Locale.ROOT);
        if (t.length < 2) {
            sendNotice(senderNumeric, "IDLEKICK " + channel + ": " + idleKickTimeoutSeconds.getOrDefault(channel, 0) + "s");
            return;
        }
        int seconds = (int) Math.max(60, Math.min(86400, parseDurationSeconds(t[1], 60L)));
        idleKickTimeoutSeconds.put(channel, seconds);
        sendNotice(senderNumeric, "IDLEKICK " + channel + " set to " + seconds + " seconds");
    }

    private void handleStats(String senderNumeric, String args) {
        String[] t = splitArgs(args);
        String channel = t.length > 0 && t[0].startsWith("#") ? t[0].toLowerCase(Locale.ROOT) : resolveDefaultHelpChannel().toLowerCase(Locale.ROOT);
        Users requester = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;

        String targetAccount = null;
        for (String token : t) {
            if (token.startsWith("#") && token.length() > 1 && !token.equals(channel)) {
                targetAccount = token.substring(1);
                break;
            }
        }
        if (targetAccount != null && !isAdminLike(requester)) {
            sendNotice(senderNumeric, "Access denied: stats for other users require admin/oper.");
            return;
        }

        String account = targetAccount;
        if (account == null && requester != null) {
            account = resolveAccountName(requester);
        }
        int queuePos = queuePosition(channel, senderNumeric);
        sendNotice(senderNumeric, "STATS " + channel + ": account=" + (account == null ? "(none)" : account)
                + ", queue_position=" + queuePos
                + ", queue_size=" + supportQueue.computeIfAbsent(channel, k -> new LinkedList<>()).size()
                + ", tickets=" + channelTickets.computeIfAbsent(channel, k -> new ConcurrentHashMap<>()).size()
                + ", hstat_cycle=" + hstatCycle);
    }

    private void handleWeekStats(String senderNumeric, String args) {
        String[] t = splitArgs(args);
        String channel = t.length > 0 && t[0].startsWith("#") ? t[0].toLowerCase(Locale.ROOT) : resolveDefaultHelpChannel().toLowerCase(Locale.ROOT);
        String type = t.length > 1 ? t[1].toLowerCase(Locale.ROOT) : "any";
        List<String> rows = buildStaffLikeList(channel, type, true);
        sendNotice(senderNumeric, rows.isEmpty() ? "WEEKSTATS " + channel + ": none" : "WEEKSTATS " + channel + ": " + String.join(", ", rows));
    }

    private void handleTop10(String senderNumeric, String args) {
        String[] t = splitArgs(args);
        String channel = t.length > 0 && t[0].startsWith("#") ? t[0].toLowerCase(Locale.ROOT) : resolveDefaultHelpChannel().toLowerCase(Locale.ROOT);
        String type = t.length > 1 ? t[1].toLowerCase(Locale.ROOT) : "opers";
        int amount = t.length > 2 ? Math.max(10, Math.min(50, parseIntOrDefault(t[2], 10))) : 10;
        List<String> rows = buildStaffLikeList(channel, type, true);
        if (rows.size() > amount) {
            rows = rows.subList(0, amount);
        }
        sendNotice(senderNumeric, rows.isEmpty() ? "TOP10 " + channel + ": none" : "TOP" + rows.size() + " " + channel + ": " + String.join(", ", rows));
    }

    private void handleRating(String senderNumeric, String args) {
        String channel = parseChannelArg(args, resolveDefaultHelpChannel()).toLowerCase(Locale.ROOT);
        int queuePos = queuePosition(channel, senderNumeric);
        int score = 50;
        if (queuePos > 0) {
            score = Math.max(1, 100 - (queuePos * 5));
        }
        sendNotice(senderNumeric, "RATING " + channel + ": " + score + "/100 (queue position " + (queuePos < 0 ? "none" : queuePos) + ")");
    }

    private void handleTermStats(String senderNumeric, String args) {
        String channel = parseChannelArg(args, null);
        Map<String, Long> stats = channel == null
                ? termUsageStats
                : channelTermUsageStats.getOrDefault(channel.toLowerCase(Locale.ROOT), Map.of());
        if (stats.isEmpty()) {
            sendNotice(senderNumeric, "TERMSTATS: no usage data.");
            return;
        }
        List<Map.Entry<String, Long>> rows = new ArrayList<>(stats.entrySet());
        rows.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        int max = Math.min(rows.size(), 10);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            out.add(rows.get(i).getKey() + "=" + rows.get(i).getValue());
        }
        sendNotice(senderNumeric, "TERMSTATS " + (channel == null ? "global" : channel) + ": " + String.join(", ", out));
    }

    private void handleChanStats(String senderNumeric, String args) {
        String[] t = splitArgs(args);
        String channel = t.length > 0 && t[0].startsWith("#") ? t[0].toLowerCase(Locale.ROOT) : resolveDefaultHelpChannel().toLowerCase(Locale.ROOT);
        Channel chan = st != null && st.getChannel() != null ? st.getChannel().get(channel) : null;
        if (chan == null) {
            sendNotice(senderNumeric, "Unknown channel: " + channel);
            return;
        }
        sendNotice(senderNumeric, "CHANSTATS " + channel + ": users=" + chan.getUsers().size()
                + ", ops=" + chan.getOp().size()
                + ", voice=" + chan.getVoice().size()
                + ", queue=" + supportQueue.computeIfAbsent(channel, k -> new LinkedList<>()).size()
                + ", tickets=" + channelTickets.computeIfAbsent(channel, k -> new ConcurrentHashMap<>()).size());
    }

    private void handleActiveStaff(String senderNumeric, String args) {
        String[] t = splitArgs(args);
        String channel = t.length > 0 && t[0].startsWith("#") ? t[0].toLowerCase(Locale.ROOT) : resolveDefaultHelpChannel().toLowerCase(Locale.ROOT);
        String userClass = t.length > 1 ? t[1].toLowerCase(Locale.ROOT) : "opers";
        String state = t.length > 2 ? t[2].toLowerCase(Locale.ROOT) : "active";

        List<String> active = buildStaffLikeList(channel, userClass, true);
        if ("inactive".equals(state)) {
            List<String> inactive = buildStaffLikeList(channel, userClass, false);
            sendNotice(senderNumeric, "ACTIVESTAFF " + channel + " inactive: " + (inactive.isEmpty() ? "none" : String.join(", ", inactive)));
            return;
        }
        sendNotice(senderNumeric, "ACTIVESTAFF " + channel + " active: " + (active.isEmpty() ? "none" : String.join(", ", active)));
    }

    private void handleCheckChannel(String senderNumeric, String args) {
        String[] t = splitArgs(args);
        if (t.length < 1 || !t[0].startsWith("#")) {
            sendNotice(senderNumeric, "Usage: CHECKCHANNEL <#channel> [summary]");
            return;
        }
        String channel = t[0].toLowerCase(Locale.ROOT);
        boolean summary = t.length > 1 && "summary".equalsIgnoreCase(t[1]);
        Channel chan = st != null && st.getChannel() != null ? st.getChannel().get(channel) : null;
        if (chan == null) {
            sendNotice(senderNumeric, "Unknown channel: " + channel);
            return;
        }
        sendNotice(senderNumeric, "CHECKCHANNEL " + channel + ": created=" + chan.getCreatedTimestamp()
                + ", modes=" + chan.getModes()
                + ", users=" + chan.getUsers().size());
        if (!summary) {
            List<String> users = new ArrayList<>();
            for (String userNumeric : chan.getUsers()) {
                users.add(nickOrNumeric(userNumeric));
                if (users.size() >= MAX_LIST_USERS) {
                    break;
                }
            }
            sendNotice(senderNumeric, "Users: " + String.join(", ", users));
        }
    }

    private void handleChannel(String senderNumeric, String args) {
        String[] t = splitArgs(args);
        if (t.length < 1 || !t[0].startsWith("#")) {
            sendNotice(senderNumeric, "Usage: CHANNEL <#channel>");
            return;
        }
        String channel = t[0].toLowerCase(Locale.ROOT);
        Channel chan = st != null && st.getChannel() != null ? st.getChannel().get(channel) : null;
        if (chan == null) {
            sendNotice(senderNumeric, "Unknown channel: " + channel);
            return;
        }

        List<String> users = new ArrayList<>();
        for (String userNumeric : chan.getUsers()) {
            String nick = nickOrNumeric(userNumeric);
            if (chan.getOp().contains(userNumeric)) {
                users.add("@" + nick);
            } else if (chan.getVoice().contains(userNumeric)) {
                users.add("+" + nick);
            } else {
                users.add(nick);
            }
            if (users.size() >= MAX_LIST_USERS) {
                break;
            }
        }
        sendNotice(senderNumeric, "CHANNEL " + channel + " (" + chan.getUsers().size() + " users): "
                + (users.isEmpty() ? "none" : String.join(", ", users)));
    }

    private void handleStatsDump(String senderNumeric) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isAdminLike(actor)) {
            sendNotice(senderNumeric, "Access denied: admin/oper required.");
            return;
        }

        long totalTermHits = 0;
        for (Long v : termUsageStats.values()) {
            if (v != null && v > 0) {
                totalTermHits += v;
            }
        }

        int queues = 0;
        int queuedUsers = 0;
        for (Map.Entry<String, Deque<String>> entry : supportQueue.entrySet()) {
            Deque<String> queue = entry.getValue();
            if (queue == null) {
                continue;
            }
            queues++;
            queuedUsers += queue.size();
        }

        sendNotice(senderNumeric, "STATSDUMP: channels=" + managedChannels.size()
                + ", accounts=" + accountLevels.size()
                + ", queues=" + queues
                + ", queued_users=" + queuedUsers);
        sendNotice(senderNumeric, "STATSDUMP: term_keys=" + termUsageStats.size()
                + ", term_hits=" + totalTermHits
                + ", channel_term_buckets=" + channelTermUsageStats.size()
                + ", account_channel_buckets=" + accountChannelStats.size());
    }

    private void handleStatsRepair(String senderNumeric) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isAdminLike(actor)) {
            sendNotice(senderNumeric, "Access denied: admin/oper required.");
            return;
        }

        int removed = 0;
        removed += pruneLongMap(termUsageStats);
        removed += pruneNestedLongMap(channelTermUsageStats);
        removed += pruneNestedLongMap(accountChannelStats);

        sendNotice(senderNumeric, "STATSREPAIR completed: removed " + removed + " invalid stats entries.");
    }

    private void handleStatsReset(String senderNumeric) {
        Users actor = st != null && st.getUsers() != null ? st.getUsers().get(senderNumeric) : null;
        if (!isAdminLike(actor)) {
            sendNotice(senderNumeric, "Access denied: admin/oper required.");
            return;
        }

        termUsageStats.clear();
        channelTermUsageStats.clear();
        accountChannelStats.clear();
        hstatCycle = 0;

        sendNotice(senderNumeric, "STATSRESET completed: in-memory statistics have been cleared.");
    }

    private int pruneLongMap(Map<String, Long> map) {
        int removed = 0;
        if (map == null) {
            return 0;
        }
        List<String> removeKeys = new ArrayList<>();
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            String key = entry.getKey();
            Long value = entry.getValue();
            if (key == null || key.isBlank() || value == null || value <= 0) {
                removeKeys.add(key);
            }
        }
        for (String key : removeKeys) {
            map.remove(key);
            removed++;
        }
        return removed;
    }

    private int pruneNestedLongMap(Map<String, Map<String, Long>> map) {
        int removed = 0;
        if (map == null) {
            return 0;
        }

        List<String> outerKeysToRemove = new ArrayList<>();
        for (Map.Entry<String, Map<String, Long>> outer : map.entrySet()) {
            String outerKey = outer.getKey();
            Map<String, Long> inner = outer.getValue();
            if (outerKey == null || outerKey.isBlank() || inner == null || inner.isEmpty()) {
                outerKeysToRemove.add(outerKey);
                continue;
            }
            removed += pruneLongMap(inner);
            if (inner.isEmpty()) {
                outerKeysToRemove.add(outerKey);
            }
        }

        for (String key : outerKeysToRemove) {
            map.remove(key);
            removed++;
        }
        return removed;
    }

    private List<String> buildStaffLikeList(String channel, String type, boolean active) {
        List<String> rows = new ArrayList<>();
        Set<String> channelUsers = new HashSet<>();
        Channel chan = st != null && st.getChannel() != null ? st.getChannel().get(channel) : null;
        if (chan != null) {
            channelUsers.addAll(chan.getUsers());
        }

        if (st == null || st.getUsers() == null || st.getUsers().isEmpty()) {
            return rows;
        }

        for (Users u : st.getUsers().values()) {
            if (u == null) {
                continue;
            }
            boolean inChannel = channelUsers.contains(u.getId());
            if (active && !inChannel) {
                continue;
            }
            if (!active && inChannel) {
                continue;
            }
            String cls = getUserCommandLevel(u) >= 5 ? "opers" : (isStaffLike(u) ? "staff" : "user");
            if (!"any".equalsIgnoreCase(type) && !cls.equalsIgnoreCase(type)) {
                continue;
            }
            if (!isStaffLike(u) && !"any".equalsIgnoreCase(type)) {
                continue;
            }
            rows.add(u.getNick() + "(" + cls + ")");
        }
        rows.sort(String::compareToIgnoreCase);
        if (rows.size() > 20) {
            return rows.subList(0, 20);
        }
        return rows;
    }

    private long parseDurationSeconds(String token, long fallback) {
        if (token == null || token.isBlank()) {
            return fallback;
        }
        String t = token.trim().toLowerCase(Locale.ROOT);
        long mult = 60;
        if (t.endsWith("s")) {
            mult = 1;
            t = t.substring(0, t.length() - 1);
        } else if (t.endsWith("m")) {
            mult = 60;
            t = t.substring(0, t.length() - 1);
        } else if (t.endsWith("h")) {
            mult = 3600;
            t = t.substring(0, t.length() - 1);
        } else if (t.endsWith("d")) {
            mult = 86400;
            t = t.substring(0, t.length() - 1);
        } else if (t.endsWith("w")) {
            mult = 604800;
            t = t.substring(0, t.length() - 1);
        }
        long value = parseLongOrDefault(t, -1L);
        if (value < 0) {
            return fallback;
        }
        return value * mult;
    }

    private boolean reloadTopics() {
        List<Path> files = resolveHelpFiles();
        if (files.isEmpty()) {
            getLogger().log(Level.WARNING, "HelpServ: no help file configured or found, using fallback topics");
            this.root = buildFallbackTree();
            this.sourceFile = null;
            return false;
        }

        try {
            TopicNode loadedRoot = new TopicNode("ROOT", null);
            for (Path file : files) {
                TopicNode parsedRoot = parseHelpFile(file);
                mergeTopicTrees(loadedRoot, parsedRoot);
                getLogger().log(Level.INFO, "HelpServ loaded help topics from {0}", file.toString());
            }
            this.root = loadedRoot;
            this.sourceFile = files.get(0);
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "HelpServ failed to load help topics", e);
            this.root = buildFallbackTree();
            this.sourceFile = null;
            return false;
        }
    }

    private boolean reloadHelpmodDb() {
        Path file = resolveHelpmodDbFile();
        if (file == null) {
            this.helpmodDbFile = null;
            this.dbDefaultChannel = null;
            this.dbChannelFlags.clear();
            return false;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String firstChannel = null;
            String section = "";
            List<String> entry = new ArrayList<>();
            Map<String, String> parsedGlobalTerms = new LinkedHashMap<>();
            Map<String, Map<String, String>> parsedChannelTerms = new LinkedHashMap<>();
            Map<String, String> parsedChannelWelcome = new LinkedHashMap<>();
            Map<String, Integer> parsedDbChannelFlags = new LinkedHashMap<>();
            Map<String, String> parsedLamerControlProfiles = new LinkedHashMap<>();
            Map<String, Map<String, String>> parsedLamerControlProfileValues = new LinkedHashMap<>();
            Set<String> parsedChannels = new HashSet<>();
            Map<String, String> parsedReportRoutes = new LinkedHashMap<>();
            Map<String, String> parsedTicketMessages = new LinkedHashMap<>();
            Map<String, Map<String, Long>> parsedTickets = new LinkedHashMap<>();
            Map<String, Integer> parsedAccountConfig = new LinkedHashMap<>();
            Map<String, Integer> parsedAccountLevels = new LinkedHashMap<>();
            Map<String, Map<String, Long>> parsedAccountChannelStats = new LinkedHashMap<>();
            int parsedHstatCycle = 0;

            for (String raw : lines) {
                if (raw == null) {
                    continue;
                }
                String trimmed = raw.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("%")) {
                    continue;
                }
                boolean indented = Character.isWhitespace(raw.charAt(0));
                if (!indented) {
                    if (!section.isEmpty() && !entry.isEmpty()) {
                        String maybeFirst = processHelpmodDbSection(section, entry, parsedGlobalTerms, parsedChannelTerms,
                                parsedChannelWelcome, parsedDbChannelFlags, parsedLamerControlProfiles, parsedLamerControlProfileValues,
                                parsedChannels, parsedReportRoutes, parsedTickets, parsedTicketMessages,
                            parsedAccountConfig, parsedAccountLevels, parsedAccountChannelStats);
                        if (firstChannel == null && maybeFirst != null) {
                            firstChannel = maybeFirst;
                        }
                        if ("globals".equals(section) && !entry.isEmpty()) {
                            parsedHstatCycle = parseIntOrDefault(entry.get(0).split("\\s+")[0], 0);
                        }
                    }
                    section = normalizeKey(trimmed);
                    entry = new ArrayList<>();
                    continue;
                }

                if (section.isEmpty()) {
                    continue;
                }
                entry.add(trimmed);
            }

            if (!section.isEmpty() && !entry.isEmpty()) {
                String maybeFirst = processHelpmodDbSection(section, entry, parsedGlobalTerms, parsedChannelTerms,
                        parsedChannelWelcome, parsedDbChannelFlags, parsedLamerControlProfiles, parsedLamerControlProfileValues,
                        parsedChannels, parsedReportRoutes, parsedTickets, parsedTicketMessages,
                    parsedAccountConfig, parsedAccountLevels, parsedAccountChannelStats);
                if (firstChannel == null && maybeFirst != null) {
                    firstChannel = maybeFirst;
                }
                if ("globals".equals(section) && !entry.isEmpty()) {
                    parsedHstatCycle = parseIntOrDefault(entry.get(0).split("\\s+")[0], 0);
                }
            }

            if (firstChannel != null) {
                dbDefaultChannel = firstChannel;
                queueEnabled.putIfAbsent(firstChannel.toLowerCase(Locale.ROOT), false);
            }

            if (!parsedChannels.isEmpty()) {
                managedChannels.clear();
                managedChannels.addAll(parsedChannels);
            }

            dbChannelFlags.clear();
            dbChannelFlags.putAll(parsedDbChannelFlags);

            lamerControlProfiles.clear();
            lamerControlProfiles.putAll(parsedLamerControlProfiles);

            lamerControlProfileValues.clear();
            lamerControlProfileValues.putAll(parsedLamerControlProfileValues);

            reportRoutes.clear();
            reportRoutes.putAll(parsedReportRoutes);

            channelWelcome.clear();
            channelWelcome.putAll(parsedChannelWelcome);

            ticketMessages.clear();
            ticketMessages.putAll(parsedTicketMessages);

            channelTickets.clear();
            channelTickets.putAll(parsedTickets);

            accountConfig.clear();
            accountConfig.putAll(parsedAccountConfig);

            accountLevels.clear();
            accountLevels.putAll(parsedAccountLevels);

            accountWriteOrder.clear();
            accountWriteOrder.addAll(parsedAccountLevels.keySet());

            accountChannelStats.clear();
            accountChannelStats.putAll(parsedAccountChannelStats);

            globalTerms.clear();
            globalTerms.putAll(parsedGlobalTerms);
            channelTerms.clear();
            channelTerms.putAll(parsedChannelTerms);
            hstatCycle = parsedHstatCycle;
            this.helpmodDbFile = file;
            getLogger().log(Level.INFO, "HelpServ loaded helpmod.db from {0}, defaultChannel={1}", new Object[]{file.toString(), dbDefaultChannel});
            return true;
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "HelpServ failed to load helpmod.db", e);
            return false;
        }
    }

    private Path resolveHelpmodDbFile() {
        Path helpDir = resolveHelpBaseDir();
        if (helpDir != null) {
            Path fromHelpDir = helpDir.resolve("helpmod.db").normalize();
            if (Files.exists(fromHelpDir)) {
                return fromHelpDir;
            }
        }
        return null;
    }

    private List<Path> resolveHelpFiles() {
        List<Path> files = new ArrayList<>();

        Path helpDir = resolveHelpBaseDir();
        if (helpDir != null) {
            List<Path> discovered = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(helpDir, "help_*.txt")) {
                for (Path file : stream) {
                    if (file != null && Files.isRegularFile(file)) {
                        discovered.add(file.normalize());
                    }
                }
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "HelpServ could not scan help_dir for language files: {0}", helpDir.toString());
            }

            discovered.sort((a, b) -> {
                String an = a.getFileName() == null ? "" : a.getFileName().toString().toLowerCase(Locale.ROOT);
                String bn = b.getFileName() == null ? "" : b.getFileName().toString().toLowerCase(Locale.ROOT);
                int ar = "help_en.txt".equals(an) ? 0 : ("help_de.txt".equals(an) ? 1 : 2);
                int br = "help_en.txt".equals(bn) ? 0 : ("help_de.txt".equals(bn) ? 1 : 2);
                if (ar != br) {
                    return Integer.compare(ar, br);
                }
                return an.compareTo(bn);
            });

            for (Path file : discovered) {
                addPathIfMissing(files, file);
            }
        }

        return files;
    }

    private void addPathIfMissing(List<Path> files, Path path) {
        if (files == null || path == null) {
            return;
        }
        Path normalized = path.normalize();
        for (Path existing : files) {
            if (existing != null && existing.normalize().equals(normalized)) {
                return;
            }
        }
        files.add(normalized);
    }

    private Path resolveHelpBaseDir() {
        String configured = resolveFromHelpConfig(null, "help_dir", "help");
        if (configured == null || configured.isBlank()) {
            return null;
        }
        Path path = resolvePathForWrite(configured.trim());
        if (Files.exists(path) && Files.isDirectory(path)) {
            return path;
        }
        return null;
    }

    private Path resolvePathForWrite(String configuredPath) {
        Path path = Paths.get(configuredPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Paths.get(".").toAbsolutePath().normalize().resolve(configuredPath).normalize();
    }

    private void mergeTopicTrees(TopicNode targetRoot, TopicNode sourceRoot) {
        if (targetRoot == null || sourceRoot == null) {
            return;
        }
        for (TopicNode sourceChild : sourceRoot.children.values()) {
            mergeTopicNode(targetRoot, sourceChild);
        }
    }

    private void mergeTopicNode(TopicNode targetParent, TopicNode sourceNode) {
        String key = normalizeKey(sourceNode.title);
        TopicNode targetNode = targetParent.children.get(key);
        if (targetNode == null) {
            targetParent.children.put(key, cloneTopicNode(sourceNode, targetParent));
            return;
        }

        for (String line : sourceNode.lines) {
            if (!targetNode.lines.contains(line)) {
                targetNode.lines.add(line);
            }
        }

        for (TopicNode sourceChild : sourceNode.children.values()) {
            mergeTopicNode(targetNode, sourceChild);
        }
    }

    private TopicNode cloneTopicNode(TopicNode sourceNode, TopicNode parent) {
        TopicNode cloned = new TopicNode(sourceNode.title, parent);
        cloned.lines.addAll(sourceNode.lines);
        for (TopicNode child : sourceNode.children.values()) {
            String key = normalizeKey(child.title);
            cloned.children.put(key, cloneTopicNode(child, cloned));
        }
        return cloned;
    }

    private TopicNode parseHelpFile(Path file) throws IOException {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (MalformedInputException ex) {
            Charset cp1252 = Charset.forName("windows-1252");
            lines = Files.readAllLines(file, cp1252);
            getLogger().log(Level.WARNING, "HelpServ help file is not valid UTF-8, falling back to windows-1252: {0}", file.toString());
        }
        TopicNode loadedRoot = new TopicNode("ROOT", null);

        Deque<TopicNode> nodeStack = new ArrayDeque<>();
        Deque<Integer> indentStack = new ArrayDeque<>();
        nodeStack.push(loadedRoot);
        indentStack.push(-1);

        TopicNode current = loadedRoot;

        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.replace("\t", "    ");
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int indent = leadingSpaces(line);
            if (trimmed.startsWith("*")) {
                String content = trimmed.substring(1).trim();
                if (!content.isEmpty()) {
                    current.lines.add(content);
                }
                continue;
            }

            if (trimmed.startsWith("$")) {
                continue;
            }

            while (indent <= indentStack.peek() && nodeStack.size() > 1) {
                nodeStack.pop();
                indentStack.pop();
            }

            TopicNode parent = nodeStack.peek();
            TopicNode next = new TopicNode(trimmed, parent);
            parent.children.put(normalizeKey(trimmed), next);

            nodeStack.push(next);
            indentStack.push(indent);
            current = next;
        }

        return loadedRoot;
    }

    private TopicNode resolveNode(String query) {
        TopicNode localRoot = this.root;
        if (query == null || query.isBlank()) {
            return localRoot;
        }

        String q = normalizeKey(query);
        String[] pathSegments = query.split("/");
        TopicNode current = localRoot;
        for (String segmentRaw : pathSegments) {
            String segment = normalizeKey(segmentRaw);
            if (segment.isBlank()) {
                continue;
            }

            TopicNode child = current.children.get(segment);
            if (child == null) {
                child = current.children.values().stream()
                        .filter(n -> normalizeKey(n.title).contains(segment))
                        .findFirst()
                        .orElse(null);
            }
            if (child == null) {
                return findByTitleContains(localRoot, segment);
            }
            current = child;
        }
        return current;
    }

    private TopicNode findByTitleContains(TopicNode rootNode, String token) {
        Deque<TopicNode> queue = new ArrayDeque<>();
        queue.add(rootNode);
        while (!queue.isEmpty()) {
            TopicNode node = queue.removeFirst();
            for (TopicNode child : node.children.values()) {
                if (normalizeKey(child.title).contains(token)) {
                    return child;
                }
                queue.addLast(child);
            }
        }
        return null;
    }

    private TopicNode buildFallbackTree() {
        TopicNode r = new TopicNode("ROOT", null);

        TopicNode start = new TopicNode("Getting Started", r);
        start.lines.add("Welcome to HelpServ.");
        start.lines.add("Use TOPICS to list categories and HELP <topic> to view details.");
        r.children.put(normalizeKey(start.title), start);

        TopicNode auth = new TopicNode("Accounts", r);
        auth.lines.add("Account help is available via AuthServ: REGISTER, IDENTIFY, PASSWD, REQUESTPASSWORD.");
        r.children.put(normalizeKey(auth.title), auth);

        TopicNode channels = new TopicNode("Channels", r);
        channels.lines.add("Channel management is provided by ChanServ (REGISTER, ADDUSER, MODUSER, INFO).");
        r.children.put(normalizeKey(channels.title), channels);

        return r;
    }

    private String joinTopicNames(Iterable<TopicNode> nodes, int max) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (TopicNode n : nodes) {
            if (count >= max) {
                sb.append(" ...");
                break;
            }
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(n.title);
            count++;
        }
        return sb.toString();
    }

    private int countTopics(TopicNode node) {
        int count = 1;
        for (TopicNode child : node.children.values()) {
            count += countTopics(child);
        }
        return count;
    }

    private int totalTicketCount() {
        int count = 0;
        for (String key : new HashSet<>(channelTickets.keySet())) {
            cleanupExpiredTickets(key);
            count += channelTickets.getOrDefault(key, Map.of()).size();
        }
        return count;
    }

    private void cleanupExpiredTickets(String channelKey) {
        Map<String, Long> t = channelTickets.computeIfAbsent(channelKey, k -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis() / 1000;
        t.entrySet().removeIf(e -> e.getValue() == null || e.getValue() <= now);
    }

    private boolean hasValidTicket(String channelKey, Users user) {
        cleanupExpiredTickets(channelKey);
        Map<String, Long> t = channelTickets.computeIfAbsent(channelKey, k -> new ConcurrentHashMap<>());
        if (t.containsKey(user.getId())) {
            return true;
        }
        if (user.getAccount() != null && !user.getAccount().isBlank()) {
            String accountMarker = "acc:" + normalizeKey(user.getAccount());
            return t.containsKey(accountMarker);
        }
        return false;
    }

    private boolean isStaffLike(Users user) {
        return getUserCommandLevel(user) >= 4;
    }

    private boolean isTrialLike(Users user) {
        return getUserCommandLevel(user) >= 3;
    }

    private boolean isAdminLike(Users user) {
        return getUserCommandLevel(user) >= 5;
    }

    private String resolveAccountFromArg(String arg) {
        if (arg == null || arg.isBlank()) {
            return null;
        }
        String token = arg.trim();
        if (token.startsWith("#") && token.length() > 1) {
            token = token.substring(1);
            return token;
        }
        String numericTarget = resolveUserNumeric(token);
        if (numericTarget != null && st != null && st.getUsers() != null) {
            Users u = st.getUsers().get(numericTarget);
            String account = resolveAccountName(u);
            if (account != null) {
                return account;
            }
        }
        return token;
    }

    private String normalizeAccountArg(String raw) {
        String account = raw == null ? "" : raw.trim();
        if (account.startsWith("#") && account.length() > 1) {
            account = account.substring(1);
        }
        return account;
    }

    private String resolveAccountName(Users user) {
        if (user == null) {
            return null;
        }
        String account = user.getAccount();
        if (account == null || account.isBlank() || "*".equals(account)) {
            return null;
        }
        return account;
    }

    private String safeValue(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }

    private String formatDuration(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private String levelNameFromFlags(int flags) {
        if (Userflags.hasFlag(flags, Userflags.Flag.DEV)) {
            return "DEV";
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.ADMIN)) {
            return "ADMIN";
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.OPER)) {
            return "OPER";
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.STAFF)) {
            return "STAFF";
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.HELPER)) {
            return "HELPER";
        }
        return "USER";
    }

    private String formatUserFlags(int flags) {
        StringBuilder sb = new StringBuilder();
        for (Userflags.Flag flag : Userflags.Flag.values()) {
            if (Userflags.hasFlag(flags, flag)) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(flag.name());
            }
        }
        return sb.length() == 0 ? "NONE" : sb.toString();
    }

    private void recordAccountChannelStat(String senderNumeric, String channel) {
        if (channel == null || channel.isBlank() || !channel.startsWith("#") || st == null || st.getUsers() == null) {
            return;
        }
        Users user = st.getUsers().get(senderNumeric);
        String account = normalizeAccountArg(resolveAccountName(user));
        if (account == null || account.isBlank()) {
            return;
        }
        String accountKey = account.toLowerCase(Locale.ROOT);
        String channelKey = channel.toLowerCase(Locale.ROOT);
        accountChannelStats.computeIfAbsent(accountKey, k -> new ConcurrentHashMap<>()).merge(channelKey, 1L, Long::sum);
    }

    private int applyLevel(int currentFlags, String level) {
        int flags = currentFlags;
        flags = Userflags.clearFlag(flags, Userflags.Flag.HELPER);
        flags = Userflags.clearFlag(flags, Userflags.Flag.STAFF);
        flags = Userflags.clearFlag(flags, Userflags.Flag.OPER);
        flags = Userflags.clearFlag(flags, Userflags.Flag.ADMIN);
        flags = Userflags.clearFlag(flags, Userflags.Flag.DEV);
        flags = Userflags.clearFlag(flags, Userflags.Flag.INACTIVE);

        switch (level) {
            case "USER":
                return flags;
            case "HELPER":
                return Userflags.setFlag(flags, Userflags.Flag.HELPER);
            case "TRIAL":
            case "STAFF":
                return Userflags.setFlag(flags, Userflags.Flag.STAFF);
            case "OPER":
                return Userflags.setFlag(flags, Userflags.Flag.OPER);
            case "ADMIN":
                return Userflags.setFlag(flags, Userflags.Flag.ADMIN);
            case "DEV":
                return Userflags.setFlag(flags, Userflags.Flag.DEV);
            default:
                return -1;
        }
    }

    private String resolveDefaultHelpChannel() {
        if (dbDefaultChannel != null && !dbDefaultChannel.isBlank()) {
            return dbDefaultChannel;
        }
        return "#help";
    }

    private String extractChannelName(List<String> channelEntryLines) {
        if (channelEntryLines == null || channelEntryLines.isEmpty()) {
            return null;
        }
        String candidate = channelEntryLines.get(0);
        if (candidate != null && candidate.startsWith("#")) {
            return candidate.trim();
        }
        return null;
    }

    private boolean parseBooleanLike(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return "1".equals(v) || "on".equals(v) || "true".equals(v) || "yes".equals(v);
    }

    private String parseChannelArg(String args, String fallback) {
        String[] t = splitArgs(args);
        if (t.length > 0 && t[0].startsWith("#")) {
            return t[0];
        }
        return fallback;
    }

    private String queueShortcutArgs(String queueOp, String rawArgs, String contextChannel) {
        String trimmed = rawArgs == null ? "" : rawArgs.trim();
        String[] parts = splitArgs(trimmed);

        if (parts.length > 0 && parts[0].startsWith("#")) {
            String channel = parts[0].toLowerCase(Locale.ROOT);
            String rest = parts.length > 1 ? joinTokens(parts, 1) : "";
            return rest.isBlank() ? (channel + " " + queueOp) : (channel + " " + queueOp + " " + rest);
        }

        if (trimmed.isBlank() && contextChannel != null && contextChannel.startsWith("#")) {
            return contextChannel.toLowerCase(Locale.ROOT) + " " + queueOp;
        }

        return trimmed.isBlank() ? queueOp : (queueOp + " " + trimmed);
    }

    private String[] splitArgs(String args) {
        String a = args == null ? "" : args.trim();
        if (a.isBlank()) {
            return new String[0];
        }
        return a.split("\\s+");
    }

    private void enqueueUser(String channelKey, String userNumeric) {
        Deque<String> q = supportQueue.computeIfAbsent(channelKey, k -> new LinkedList<>());
        if (!q.contains(userNumeric)) {
            q.addLast(userNumeric);
        }
    }

    private boolean isChannelFlagEnabled(String channel, int mask) {
        if (channel == null) {
            return false;
        }
        int flags = channelConfigFlags.getOrDefault(channel, dbChannelFlags.getOrDefault(channel, 0));
        return (flags & mask) != 0;
    }

    private void setChannelFlag(String channel, int mask, boolean enabled) {
        if (channel == null || channel.isBlank()) {
            return;
        }
        int current = channelConfigFlags.getOrDefault(channel, dbChannelFlags.getOrDefault(channel, 0));
        int next = enabled ? (current | mask) : (current & ~mask);
        if (current == next) {
            return;
        }
        channelConfigFlags.put(channel, next);
        dbChannelFlags.put(channel, next);
        applyChannelConfChange(channel, current, next);
    }

    private int queuePosition(String channelKey, String userNumeric) {
        Deque<String> q = supportQueue.computeIfAbsent(channelKey, k -> new LinkedList<>());
        int pos = 1;
        for (String n : q) {
            if (n.equals(userNumeric)) {
                return pos;
            }
            pos++;
        }
        return -1;
    }

    private String pollNextQueueUser(String channelKey) {
        Deque<String> q = supportQueue.computeIfAbsent(channelKey, k -> new LinkedList<>());
        while (!q.isEmpty()) {
            String next = q.pollFirst();
            if (st != null && st.getUsers() != null && st.getUsers().containsKey(next)) {
                return next;
            }
        }
        return null;
    }

    private int countQueuedUsersOffQueue(String channelKey) {
        if (st == null || st.getChannel() == null || channelKey == null) {
            return 0;
        }
        Channel channelObj = st.getChannel().get(channelKey);
        if (channelObj == null || channelObj.getVoice() == null || channelObj.getVoice().isEmpty()) {
            return 0;
        }

        int count = 0;
        for (String userNumeric : channelObj.getVoice()) {
            if (userNumeric == null || userNumeric.isBlank()) {
                continue;
            }
            Users user = st.getUsers() != null ? st.getUsers().get(userNumeric) : null;
            if (isStaffLike(user)) {
                continue;
            }
            if ((numeric + getNumericSuffix()).equals(userNumeric)) {
                continue;
            }
            count++;
        }
        return count;
    }

    private String queuePreview(Deque<String> queue, int max) {
        List<String> names = new ArrayList<>();
        int i = 0;
        for (String n : queue) {
            if (i >= max) {
                names.add("...");
                break;
            }
            names.add(nickOrNumeric(n));
            i++;
        }
        return String.join(", ", names);
    }

    private String resolveUserNumeric(String nickOrNumeric) {
        if (nickOrNumeric == null || nickOrNumeric.isBlank() || st == null || st.getUsers() == null) {
            return null;
        }
        String token = nickOrNumeric.trim();
        if (st.getUsers().containsKey(token)) {
            return token;
        }
        for (Map.Entry<String, Users> e : st.getUsers().entrySet()) {
            Users u = e.getValue();
            if (u != null && u.getNick() != null && u.getNick().equalsIgnoreCase(token)) {
                return e.getKey();
            }
        }
        return null;
    }

    private void sendTermReply(String senderNumeric, String replyChannel, String message) {
        sendTermReply(senderNumeric, replyChannel, message, true);
    }

    private void sendTermReply(String senderNumeric, String replyChannel, String message, boolean includeSenderPrefixInChannel) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (replyChannel != null && !replyChannel.isBlank()) {
            if (includeSenderPrefixInChannel) {
                sendChannelMessage(replyChannel, nickOrNumeric(senderNumeric) + ": " + message);
            } else {
                sendChannelMessage(replyChannel, message);
            }
            return;
        }
        sendNotice(senderNumeric, message);
    }

    private String nickOrNumeric(String numericId) {
        if (numericId == null || st == null || st.getUsers() == null) {
            return numericId;
        }
        Users u = st.getUsers().get(numericId);
        return (u != null && u.getNick() != null) ? u.getNick() : numericId;
    }

    private int leadingSpaces(String text) {
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (char c : lower.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-') {
                sb.append(c);
            }
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    private String sanitizeInput(String input) {
        String s = input == null ? "" : input.trim();
        if (s.startsWith("-")) {
            if (s.length() > 1) {
                s = s.substring(1).trim();
            }
        }
        return s;
    }

    private boolean isNumericSelection(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (char c : value.trim().toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    private String resolveTicketKey(String nickOrAccount) {
        if (nickOrAccount == null || nickOrAccount.isBlank()) {
            return null;
        }
        String token = nickOrAccount.trim();
        if (token.startsWith("#") && token.length() > 1) {
            return "acc:" + normalizeKey(token.substring(1));
        }

        String numericTarget = resolveUserNumeric(token);
        if (numericTarget == null || st == null || st.getUsers() == null) {
            return null;
        }
        Users target = st.getUsers().get(numericTarget);
        String account = normalizeAccountArg(resolveAccountName(target));
        if (account != null && !account.isBlank()) {
            return "acc:" + normalizeKey(account);
        }
        return numericTarget;
    }

    private String renderTicketTarget(String ticketKey) {
        if (ticketKey == null || ticketKey.isBlank()) {
            return "(unknown)";
        }
        if (ticketKey.startsWith("acc:") && ticketKey.length() > 4) {
            return "#" + ticketKey.substring(4);
        }
        return nickOrNumeric(ticketKey);
    }

    private String extractAddressedCommand(String message) {
        String m = message == null ? "" : message.trim();
        if (m.isEmpty()) {
            return null;
        }
        String nick = serviceNick == null ? "HelpServ" : serviceNick;
        String lower = m.toLowerCase(Locale.ROOT);
        String n = nick.toLowerCase(Locale.ROOT);

        if (lower.startsWith(n + ":") || lower.startsWith(n + ",")) {
            return m.substring(nick.length() + 1).trim();
        }
        if (lower.startsWith(n + " ")) {
            return m.substring(nick.length()).trim();
        }
        return null;
    }

    private String extractChannelTriggeredCommand(String message) {
        String m = message == null ? "" : message.trim();
        if (m.isEmpty()) {
            return null;
        }

        if (!m.startsWith("?")) {
            return null;
        }

        if (m.length() == 1) {
            return "?";
        }

        if (m.startsWith("??+") || m.startsWith("??-")) {
            return m.substring(1).trim();
        }

        char second = m.charAt(1);
        if (second == '+' || second == '-') {
            return m;
        }

        if (Character.isWhitespace(second)) {
            return m;
        }

        return "? " + m.substring(1).trim();
    }

    private String processHelpmodDbSection(String section, List<String> entry, Map<String, String> parsedGlobalTerms,
                                           Map<String, Map<String, String>> parsedChannelTerms,
                                           Map<String, String> parsedChannelWelcome,
                                           Map<String, Integer> parsedDbChannelFlags,
                                           Map<String, String> parsedLamerControlProfiles,
                                           Map<String, Map<String, String>> parsedLamerControlProfileValues,
                                           Set<String> parsedChannels,
                                           Map<String, String> parsedReportRoutes,
                                           Map<String, Map<String, Long>> parsedTickets,
                                           Map<String, String> parsedTicketMessages,
                                           Map<String, Integer> parsedAccountConfig,
                                           Map<String, Integer> parsedAccountLevels,
                                           Map<String, Map<String, Long>> parsedAccountChannelStats) {
        if ("globals".equals(section) && !entry.isEmpty()) {
            String value = entry.get(0).split("\\s+")[0];
            queueEnabled.put(resolveDefaultHelpChannel().toLowerCase(Locale.ROOT), parseBooleanLike(value));
            return null;
        }

        if ("term".equals(section) && entry.size() >= 2) {
            String key = normalizeKey(entry.get(0));
            String value = entry.get(1);
            if (!key.isBlank() && value != null && !value.isBlank()) {
                parsedGlobalTerms.put(key, value);
            }
            return null;
        }

        if ("channel".equals(section) && !entry.isEmpty()) {
            String chanName = extractChannelName(entry);
            if (chanName == null) {
                return null;
            }
            String chanKey = chanName.toLowerCase(Locale.ROOT);
            managedChannels.add(chanKey);
            parsedChannels.add(chanKey);

            if (entry.size() > 1) {
                int flags = parseHexOrDefault(entry.get(1), 0);
                parsedDbChannelFlags.put(chanKey, flags);
                queueEnabled.put(chanKey, (flags & H_QUEUE) != 0);
                queueMaintain.put(chanKey, (flags & H_QUEUE_MAINTAIN) != 0);
                if ((flags & H_QUEUE_MAINTAIN) != 0) {
                    queueMaintainTarget.put(chanKey, Math.max(1, queueMaintainTarget.getOrDefault(chanKey, 1)));
                }
            }

            if (entry.size() > 2 && entry.get(2) != null && !entry.get(2).isBlank() && !"(null)".equalsIgnoreCase(entry.get(2))) {
                parsedChannelWelcome.put(chanKey, entry.get(2));
            }

            if (entry.size() > 3 && entry.get(3) != null && !entry.get(3).isBlank() && !"(null)".equalsIgnoreCase(entry.get(3))) {
                parsedLamerControlProfiles.put(chanKey, normalizeKey(entry.get(3)));
            }

            if (entry.size() > 5 && entry.get(5) != null && !entry.get(5).isBlank() && !"(null)".equalsIgnoreCase(entry.get(5))) {
                parsedTicketMessages.put(chanKey, entry.get(5));
            }
            int termCount = 0;
            int termStartIndex = -1;
            for (int i = 0; i < entry.size(); i++) {
                String line = entry.get(i);
                if (line.contains("% terms")) {
                    termCount = Math.max(0, parseIntOrDefault(line.split("\\s+")[0], 0));
                    termStartIndex = i + 1;
                    break;
                }
            }
            if (termCount > 0 && termStartIndex >= 0) {
                Map<String, String> cTerms = new LinkedHashMap<>();
                int idx = termStartIndex;
                for (int i = 0; i < termCount && idx + 1 < entry.size(); i++) {
                    String name = normalizeKey(entry.get(idx));
                    String desc = entry.get(idx + 1);
                    if (!name.isBlank() && desc != null && !desc.isBlank()) {
                        cTerms.put(name, desc);
                    }
                    idx += 2;
                }
                if (!cTerms.isEmpty()) {
                    parsedChannelTerms.put(chanKey, cTerms);
                }
            }
            return chanName;
        }

        if ("lamercontrol profile".equals(section) && !entry.isEmpty()) {
            String profileName = normalizeKey(entry.get(0));
            if (profileName.isBlank()) {
                return null;
            }
            Map<String, String> values = new LinkedHashMap<>();
            int rawLineIndex = 0;
            for (int i = 1; i < entry.size(); i++) {
                String valueLine = entry.get(i);
                if (valueLine == null || valueLine.isBlank()) {
                    continue;
                }
                if ("lamercontrol profile".equals(normalizeKey(valueLine))) {
                    continue;
                }
                values.put("__line" + rawLineIndex, valueLine.trim());
                rawLineIndex++;
            }
            parsedLamerControlProfileValues.put(profileName, values);
            return null;
        }

        if ("report".equals(section) && entry.size() >= 2) {
            String from = entry.get(0).toLowerCase(Locale.ROOT);
            String to = entry.get(1).toLowerCase(Locale.ROOT);
            if (from.startsWith("#") && to.startsWith("#")) {
                parsedReportRoutes.put(from, to);
            }
            return null;
        }

        if ("ticket".equals(section) && entry.size() >= 3) {
            String channel = entry.get(0).toLowerCase(Locale.ROOT);
            String auth = normalizeKey(entry.get(1));
            long expiry = parseLongOrDefault(entry.get(2), 0L);
            if (channel.startsWith("#") && !auth.isBlank() && expiry > (System.currentTimeMillis() / 1000)) {
                parsedTickets.computeIfAbsent(channel, k -> new ConcurrentHashMap<>()).put("acc:" + auth, expiry);
                if (entry.size() > 3 && entry.get(3) != null && !entry.get(3).isBlank() && !"(null)".equalsIgnoreCase(entry.get(3))) {
                    parsedTicketMessages.put(channel, entry.get(3));
                }
            }
            return null;
        }

        if ("account".equals(section) && entry.size() >= 2) {
            String account = normalizeAccountArg(entry.get(0));
            if (account != null && !account.isBlank()) {
                String accountKey = account.toLowerCase(Locale.ROOT);
                String[] fields = entry.get(1).trim().split("\\s+");
                int level = 1;
                int conf = 0;
                if (fields.length >= 1) {
                    level = Math.max(0, Math.min(6, parseIntOrDefault(fields[0], 1)));
                }
                if (fields.length >= 2) {
                    conf = parseIntOrDefault(fields[1], 0);
                }
                parsedAccountConfig.put(accountKey, conf);
                parsedAccountLevels.put(accountKey, level);

                if (entry.size() >= 3) {
                    String[] statMeta = entry.get(2).trim().split("\\s+");
                    int statCount = statMeta.length > 0 ? Math.max(0, parseIntOrDefault(statMeta[0], 0)) : 0;
                    int consumed = 0;
                    Map<String, Long> stats = new LinkedHashMap<>();
                    for (int i = 3; i < entry.size() && consumed < statCount; i++) {
                        String[] statLine = entry.get(i).trim().split("\\s+");
                        if (statLine.length < 2) {
                            continue;
                        }
                        String channel = statLine[0].toLowerCase(Locale.ROOT);
                        long value = parseLongOrDefault(statLine[1], 0L);
                        if (channel.startsWith("#") && value > 0) {
                            stats.put(channel, value);
                        }
                        consumed++;
                    }
                    if (!stats.isEmpty()) {
                        parsedAccountChannelStats.put(accountKey, stats);
                    }
                }
            }
            return null;
        }
        return null;
    }

    private int resolveHelpmodAccountLevel(String account) {
        if (account == null || account.isBlank()) {
            return 1;
        }
        return Math.max(0, Math.min(6, accountLevels.getOrDefault(account.toLowerCase(Locale.ROOT), 1)));
    }

    private Map<String, String> getTermMap(String channel) {
        if (channel != null) {
            Map<String, String> c = channelTerms.get(channel.toLowerCase(Locale.ROOT));
            if (c != null && !c.isEmpty()) {
                return c;
            }
        }
        return globalTerms;
    }

    private String joinTokens(String[] tokens, int fromIndex) {
        if (tokens == null || fromIndex >= tokens.length) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = fromIndex; i < tokens.length; i++) {
            if (i > fromIndex) {
                sb.append(' ');
            }
            sb.append(tokens[i]);
        }
        return sb.toString();
    }

    private int parseIntOrDefault(String token, int fallback) {
        if (token == null || token.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(token.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private long parseLongOrDefault(String token, long fallback) {
        if (token == null || token.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(token.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private int parseHexOrDefault(String token, int fallback) {
        if (token == null || token.isBlank()) {
            return fallback;
        }
        try {
            return (int) Long.parseLong(token.trim(), 16);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private boolean isManagedChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return false;
        }
        String key = channel.toLowerCase(Locale.ROOT);
        if (managedChannels.isEmpty()) {
            return key.equals(resolveDefaultHelpChannel().toLowerCase(Locale.ROOT));
        }
        return managedChannels.contains(key);
    }

    private boolean isChannelCommandsEnabled(String channel) {
        String key = channel == null ? "" : channel.toLowerCase(Locale.ROOT);
        int flags = dbChannelFlags.getOrDefault(key, H_CHANNEL_COMMANDS);
        return (flags & H_CHANNEL_COMMANDS) != 0;
    }

    private boolean isWelcomeEnabled(String channel) {
        String key = channel == null ? "" : channel.toLowerCase(Locale.ROOT);
        int flags = dbChannelFlags.getOrDefault(key, -1);
        if (flags >= 0) {
            return (flags & H_WELCOME) != 0;
        }
        return channelWelcome.containsKey(key);
    }

    private boolean isTicketRequired(String channelKey) {
        int flags = channelConfigFlags.getOrDefault(channelKey, dbChannelFlags.getOrDefault(channelKey, -1));
        if (flags >= 0) {
            return (flags & H_REQUIRE_TICKET) != 0;
        }
        return false;
    }

    private boolean isLevelToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String t = token.trim().toUpperCase(Locale.ROOT);
        if (t.chars().allMatch(Character::isDigit)) {
            return true;
        }
        return "LAMER".equals(t) || "PEON".equals(t) || "FRIEND".equals(t) || "TRIAL".equals(t)
                || "STAFF".equals(t) || "OPER".equals(t) || "ADMIN".equals(t) || "DEV".equals(t)
                || "HELPER".equals(t) || "USER".equals(t);
    }

    private boolean matchesLevelFilter(int level, String token) {
        String t = token == null ? "" : token.trim().toUpperCase(Locale.ROOT);
        if (t.chars().allMatch(Character::isDigit)) {
            int n = parseIntOrDefault(t, -1);
            return n == level;
        }

        if ("LAMER".equals(t)) {
            return level == 0;
        }
        if ("PEON".equals(t)) {
            return level == 1;
        }
        if ("FRIEND".equals(t)) {
            return level == 2;
        }
        if ("TRIAL".equals(t)) {
            return level == 3;
        }
        if ("STAFF".equals(t)) {
            return level == 4;
        }
        if ("OPER".equals(t)) {
            return level == 5;
        }
        if ("ADMIN".equals(t) || "DEV".equals(t)) {
            return level == 6;
        }
        if ("USER".equals(t)) {
            return level == 1;
        }
        if ("HELPER".equals(t)) {
            return level == 2;
        }
        return false;
    }

    private boolean wildcardMatch(String text, String pattern) {
        String value = text == null ? "" : text;
        String pat = pattern == null || pattern.isBlank() ? "*" : pattern;
        String regex = pat
                .replace("\\", "\\\\")
                .replace(".", "\\.")
                .replace("?", ".")
                .replace("*", ".*");
        return value.toLowerCase(Locale.ROOT).matches(regex.toLowerCase(Locale.ROOT));
    }

    private String normalizeLevelToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String t = token.trim().toUpperCase(Locale.ROOT);
        if (t.chars().allMatch(Character::isDigit)) {
            int n = parseIntOrDefault(t, -1);
            switch (n) {
                case 0:
                    return "LAMER";
                case 1:
                    return "USER";
                case 2:
                    return "HELPER";
                case 3:
                    return "TRIAL";
                case 4:
                    return "STAFF";
                case 5:
                    return "OPER";
                case 6:
                    return "ADMIN";
                default:
                    return null;
            }
        }
        if ("PEON".equals(t)) {
            return "USER";
        }
        if ("FRIEND".equals(t)) {
            return "HELPER";
        }
        if ("TRIAL".equals(t)) {
            return "TRIAL";
        }
        if ("LAMER".equals(t) || "USER".equals(t) || "HELPER".equals(t) || "STAFF".equals(t)
                || "OPER".equals(t) || "ADMIN".equals(t) || "DEV".equals(t)) {
            return t;
        }
        return null;
    }

    private int parseRequestedCommandLevel(String args, int requesterLevel) {
        String[] tokens = splitArgs(args);
        if (tokens.length == 0) {
            return requesterLevel;
        }
        int parsed = parseCommandLevelToken(tokens[0]);
        if (parsed < 0 || parsed > requesterLevel) {
            return requesterLevel;
        }
        return parsed;
    }

    private int parseCommandLevelToken(String token) {
        if (token == null || token.isBlank()) {
            return -1;
        }
        String t = token.trim().toUpperCase(Locale.ROOT);
        if (t.chars().allMatch(Character::isDigit)) {
            int n = parseIntOrDefault(t, -1);
            if (n >= 0 && n <= 6) {
                return mapLegacyLevelToCommandLevel(n);
            }
            return -1;
        }
        switch (t) {
            case "LAMER":
                return 0;
            case "USER":
            case "PEON":
                return 1;
            case "HELPER":
            case "FRIEND":
                return 2;
            case "TRIAL":
                return 3;
            case "STAFF":
                return 4;
            case "OPER":
                return 5;
            case "ADMIN":
            case "DEV":
                return 6;
            default:
                return -1;
        }
    }

    private int mapLegacyLevelToCommandLevel(int n) {
        switch (n) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            case 4:
                return 4;
            case 5:
                return 5;
            case 6:
                return 6;
            default:
                return 1;
        }
    }

    private int getUserCommandLevel(Users user) {
        if (user == null) {
            return 1;
        }
        String account = resolveAccountName(user);
        if (account == null || account.isBlank()) {
            return 1;
        }
        return Math.max(0, Math.min(6, accountLevels.getOrDefault(account.toLowerCase(Locale.ROOT), 1)));
    }

    private String commandLevelName(int level) {
        switch (level) {
            case 0:
                return "LAMER";
            case 1:
                return "PEON";
            case 2:
                return "FRIEND";
            case 3:
                return "TRIAL";
            case 4:
                return "STAFF";
            case 5:
                return "OPER";
            case 6:
                return "ADMIN";
            default:
                return "USER";
        }
    }

    private String normalizeCommandForAcl(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        String c = command.trim().toUpperCase(Locale.ROOT);
        switch (c) {
            case "QUESTIONMARK":
                return "?";
            case "QUESTIONMARKPLUS":
                return "?+";
            case "QUESTIONMARKMINUS":
                return "?-";
            default:
                return c;
        }
    }

    private CommandDef commandDef(String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return null;
        }
        for (CommandDef cmd : commandCatalog()) {
            if (cmd.name.equalsIgnoreCase(commandName)) {
                return cmd;
            }
        }
        return null;
    }

    private List<CommandDef> commandCatalog() {
        List<CommandDef> defs = new ArrayList<>();
        defs.add(new CommandDef("HELP", 1, "Show help topics"));
        defs.add(new CommandDef("TOPICS", 1, "List subtopics"));
        defs.add(new CommandDef("SEARCH", 1, "Search topic tree"));
        defs.add(new CommandDef("TERM", 4, "Find/list term definitions"));
        defs.add(new CommandDef("?", 1, "Shortcut for TERM FIND"));
        defs.add(new CommandDef("?+", 3, "TERM FIND + queue NEXT"));
        defs.add(new CommandDef("?-", 3, "TERM FIND + queue DONE"));
        defs.add(new CommandDef("COMMAND", 0, "Show detailed command help"));
        defs.add(new CommandDef("SHOWCOMMANDS", 0, "Show commands by level"));
        defs.add(new CommandDef("VERSION", 1, "Show service version"));
        defs.add(new CommandDef("INVITE", 1, "Invite yourself to support channel"));
        defs.add(new CommandDef("WHOAMI", 0, "Show your account details"));
        defs.add(new CommandDef("WHOIS", 4, "Show account details by nick/#account"));
        defs.add(new CommandDef("SEEN", 4, "Show last seen/last auth"));
        defs.add(new CommandDef("LISTUSER", 4, "List accounts with pattern/level"));
        defs.add(new CommandDef("QUEUE", 3, "Show queue status"));
        defs.add(new CommandDef("NEXT", 3, "Queue shortcut"));
        defs.add(new CommandDef("DONE", 3, "Queue shortcut"));
        defs.add(new CommandDef("ENQUEUE", 3, "Queue on shortcut"));
        defs.add(new CommandDef("DEQUEUE", 3, "Queue off shortcut"));
        defs.add(new CommandDef("AUTOQUEUE", 3, "Queue maintain shortcut"));
        defs.add(new CommandDef("STATS", 4, "Show user/support stats"));
        defs.add(new CommandDef("RATING", 3, "Show simplified weekly rating"));
        defs.add(new CommandDef("TERMSTATS", 5, "Show term usage statistics"));
        defs.add(new CommandDef("ACCONF", 3, "Personalise HelpServ behaviour"));
        defs.add(new CommandDef("WELCOME", 4, "Show channel welcome text"));

        defs.add(new CommandDef("OP", 4, "Give +o on channel"));
        defs.add(new CommandDef("DEOP", 4, "Remove +o on channel"));
        defs.add(new CommandDef("VOICE", 3, "Give +v on channel"));
        defs.add(new CommandDef("DEVOICE", 3, "Remove +v on channel"));
        defs.add(new CommandDef("KICK", 3, "Kick users from channel"));
        defs.add(new CommandDef("OUT", 4, "Temp ban+kick from default help channel"));
        defs.add(new CommandDef("MESSAGE", 3, "Send prefixed staff message to channel"));
        defs.add(new CommandDef("CHECKCHANNEL", 4, "Inspect channel metadata/users"));
        defs.add(new CommandDef("CHANNEL", 3, "List all users on a channel"));
        defs.add(new CommandDef("CHANSTATS", 4, "Show channel aggregate stats"));
        defs.add(new CommandDef("WEEKSTATS", 6, "Show weekly activity snapshot"));
        defs.add(new CommandDef("TOP10", 4, "Show top active staff/opers"));
        defs.add(new CommandDef("ACTIVESTAFF", 6, "List active/inactive staff/opers"));
        defs.add(new CommandDef("STATSDUMP", 6, "Dump aggregated in-memory statistics"));
        defs.add(new CommandDef("STATSREPAIR", 6, "Repair inconsistent in-memory statistics"));
        defs.add(new CommandDef("STATSRESET", 6, "Reset in-memory statistics"));

        defs.add(new CommandDef("STATUS", 5, "Show HelpServ status"));
        defs.add(new CommandDef("BAN", 4, "Manage global ban list"));
        defs.add(new CommandDef("CHANBAN", 3, "Manage channel ban masks"));
        defs.add(new CommandDef("CENSOR", 4, "Manage channel censor entries"));
        defs.add(new CommandDef("LAMERCONTROL", 5, "Set/list lamer profile per channel"));
        defs.add(new CommandDef("LCEDIT", 6, "Edit lamercontrol profiles"));
        defs.add(new CommandDef("IDLEKICK", 5, "View/set idle kick timeout"));
        defs.add(new CommandDef("DNMO", 4, "Move users to queue end"));
        defs.add(new CommandDef("EVERYONEOUT", 4, "Clear channel users and set +i"));
        defs.add(new CommandDef("TICKET", 3, "Create invite ticket"));
        defs.add(new CommandDef("RESOLVE", 4, "Resolve invite ticket"));
        defs.add(new CommandDef("TICKETS", 4, "List active tickets"));
        defs.add(new CommandDef("SHOWTICKET", 4, "Show ticket details"));
        defs.add(new CommandDef("TICKETMSG", 4, "Set/view ticket message"));
        defs.add(new CommandDef("LEVEL", 6, "View/change account level"));
        defs.add(new CommandDef("PEON", 4, "Set level PEON"));
        defs.add(new CommandDef("FRIEND", 4, "Set level FRIEND"));
        defs.add(new CommandDef("TRIAL", 5, "Set level TRIAL"));
        defs.add(new CommandDef("STAFF", 5, "Set level STAFF"));
        defs.add(new CommandDef("OPER", 5, "Set level OPER"));
        defs.add(new CommandDef("ADMIN", 6, "Set level ADMIN"));
        defs.add(new CommandDef("IMPROPER", 4, "Set target inactive/lamer"));
        defs.add(new CommandDef("ADDUSER", 6, "Add/set account level"));
        defs.add(new CommandDef("MODUSER", 6, "Modify account level"));
        defs.add(new CommandDef("ADDCHAN", 6, "Add managed support channel"));
        defs.add(new CommandDef("DELCHAN", 6, "Delete managed channel"));
        defs.add(new CommandDef("CHANCONF", 4, "Configure channel flags"));
        defs.add(new CommandDef("TOPIC", 4, "Manage stored topic parts"));
        defs.add(new CommandDef("REPORT", 5, "Configure report routes"));
        defs.add(new CommandDef("WRITEDB", 5, "Write/commit runtime state"));
        defs.add(new CommandDef("RELOAD", 5, "Reload help topics and helpmod.db"));
        return defs;
    }

    private static final class CommandDef {
        private final String name;
        private final int minLevel;
        private final String help;

        private CommandDef(String name, int minLevel, String help) {
            this.name = name;
            this.minLevel = minLevel;
            this.help = help;
        }
    }

    private static final class BanRecord {
        private final String mask;
        private final long expiresAt;
        private final String reason;
        private final String setBy;

        private BanRecord(String mask, long expiresAt, String reason, String setBy) {
            this.mask = mask;
            this.expiresAt = expiresAt;
            this.reason = reason;
            this.setBy = setBy;
        }
    }

    private static final class CensorEntry {
        private final String type;
        private final String pattern;
        private final String reason;

        private CensorEntry(String type, String pattern, String reason) {
            this.type = type;
            this.pattern = pattern;
            this.reason = reason;
        }
    }

    private String formatAcconf(int conf) {
        List<String> enabled = new ArrayList<>();
        for (int i = 0; i <= HACCOUNT_CONF_COUNT; i++) {
            int bit = 1 << i;
            if ((conf & bit) != 0) {
                enabled.add("+" + i);
            }
        }
        if (enabled.isEmpty()) {
            return "(none)";
        }
        return String.join(" ", enabled);
    }

    private String formatChanConf(int flags) {
        if (flags == 0) {
            return "(none)";
        }
        List<String> enabled = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            int bit = 1 << i;
            if ((flags & bit) != 0) {
                enabled.add("+" + i);
            }
        }
        return String.join(" ", enabled);
    }

    private String buildTopic(List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return "(empty)";
        }
        return String.join(" | ", parts);
    }

    private String resolveFromHelpConfig(String currentValue, String key, String fallback) {
        if (currentValue != null && !currentValue.isBlank()) {
            return currentValue;
        }
        if (mi != null && mi.getConfig() != null && mi.getConfig().getHelpServFile() != null) {
            String value = mi.getConfig().getHelpServFile().getProperty(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private void sendNotice(String targetNumeric, String message) {
        if (numeric == null || getNumericSuffix() == null || targetNumeric == null || message == null || message.isBlank()) {
            return;
        }

        int conf = resolveAccountConfigForNumeric(targetNumeric);
        if ((conf & H_ACC_ALL_PRIVMSG) != 0 && (conf & H_ACC_ALL_NOTICE) == 0) {
            sendPrivateMessage(targetNumeric, message);
            return;
        }

        sendText("%s%s O %s :%s", numeric, getNumericSuffix(), targetNumeric, message);
    }

    private void sendPrivateMessage(String targetNumeric, String message) {
        if (numeric == null || getNumericSuffix() == null || targetNumeric == null || message == null || message.isBlank()) {
            return;
        }
        sendText("%s%s P %s :%s", numeric, getNumericSuffix(), targetNumeric, message);
    }

    private int resolveAccountConfigForNumeric(String targetNumeric) {
        if (targetNumeric == null || st == null || st.getUsers() == null) {
            return 0;
        }
        Users target = st.getUsers().get(targetNumeric);
        if (target == null) {
            return 0;
        }
        String account = resolveAccountName(target);
        if (account == null || account.isBlank()) {
            return 0;
        }
        return accountConfig.getOrDefault(account.toLowerCase(Locale.ROOT), 0);
    }

    private boolean hasAccountConfigFlag(String userNumeric, int flag) {
        return (resolveAccountConfigForNumeric(userNumeric) & flag) != 0;
    }

    private String accountConfName(int id) {
        if (id < 0 || id >= ACCOUNT_CONF_NAMES.length) {
            return "Unknown";
        }
        return ACCOUNT_CONF_NAMES[id];
    }

    private String accountConfState(int conf, int mask) {
        return (conf & mask) != 0 ? "Yes" : "No";
    }

    private void sendChannelMessage(String channel, String message) {
        if (numeric == null || getNumericSuffix() == null || channel == null || !channel.startsWith("#") || message == null || message.isBlank()) {
            return;
        }
        sendText("%s%s P %s :%s", numeric, getNumericSuffix(), channel.toLowerCase(Locale.ROOT), message);
    }

    private static final class TopicNode {
        private final String title;
        private final TopicNode parent;
        private final List<String> lines = new ArrayList<>();
        private final Map<String, TopicNode> children = new LinkedHashMap<>();

        private TopicNode(String title, TopicNode parent) {
            this.title = Objects.requireNonNull(title);
            this.parent = parent;
        }

        private String path() {
            if (parent == null) {
                return title;
            }
            if (parent.parent == null) {
                return title;
            }
            return parent.path() + " / " + title;
        }
    }
}