package net.midiandmore.jserv;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

/**
 * ChanServ - Channel registration and management service
 * 
 * Uses newserv database structure (chanserv.channels, chanserv.chanusers)
 * and Userflags.QCUFlag for channel user access control.
 */
public final class ChanServ extends AbstractModule implements Software {

    public ChanServ(JServ jserv, SocketThread socketThread, PrintWriter pw, BufferedReader br) {
        initialize(jserv, socketThread, pw, br);
    }

    @Override
    public String getModuleName() {
        return "ChanServ";
    }

    @Override
    public void handshake(String nick, String servername, String description, String numeric, String identd) {
        if (!enabled) {
            getLogger().log(Level.WARNING, "ChanServ handshake called but module is disabled");
            return;
        }

        // Ensure ChanServ database tables exist
        try {
            mi.getDb().ensureChanServTables();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to ensure ChanServ tables: " + e.getMessage(), e);
        }

        String resolvedNick = resolveFromConfig(nick, "nick");
        String resolvedServername = resolveFromConfig(servername, "servername");
        String resolvedDescription = resolveFromConfig(description, "description");
        String resolvedIdentd = resolveFromConfig(identd, "identd");

        if (resolvedNick == null || resolvedNick.isBlank()
                || resolvedServername == null || resolvedServername.isBlank()
                || resolvedDescription == null || resolvedDescription.isBlank()) {
            getLogger().log(Level.WARNING, "Cannot perform handshake for ChanServ: missing configuration");
            return;
        }
        if (resolvedIdentd == null || resolvedIdentd.isBlank()) {
            resolvedIdentd = resolvedNick.toLowerCase();
        }

        this.numeric = numeric;
        getLogger().log(Level.INFO, "Registering ChanServ nick: {0}", resolvedNick);
        sendText("%s N %s 2 %d %s %s +oikrd - %s U]AEB %s%s :%s",
                this.numeric,
                resolvedNick,
                time(),
                resolvedIdentd,
                resolvedServername,
                resolvedNick,
                this.numeric,
                getNumericSuffix(),
                resolvedDescription);
    }

    private String resolveFromConfig(String currentValue, String key) {
        if (currentValue != null && !currentValue.isBlank()) {
            return currentValue;
        }
        if (mi == null || mi.getConfig() == null || mi.getConfig().getChanServFile() == null) {
            return currentValue;
        }
        String v = mi.getConfig().getChanServFile().getProperty(key);
        return (v != null && !v.isBlank()) ? v : currentValue;
    }

    @Override
    public void parseLine(String text) {
        if (!enabled) {
            return;
        }

        String line = text == null ? "" : text.trim();
        if (line.isEmpty()) {
            return;
        }

        String[] elem = line.split(" ");
        if (elem.length < 2) {
            return;
        }

        // Handle private messages to ChanServ
        if ("P".equals(elem[1]) && elem.length >= 4) {
            handlePrivateMessage(line, elem);
        }
        
        // Handle mode changes to keep channel op/voice lists synchronized
        if ("M".equals(elem[1]) && elem.length >= 4) {
            handleModeChange(elem);
        }
        
        // Handle user joining channel - check for autoop/autovoice
        if (("J".equals(elem[1]) || "C".equals(elem[1])) && elem.length >= 3) {
            // Check if ChanServ is joining a channel
            String joiningNumeric = elem[0];
            String myNumeric = numeric + getNumericSuffix();
            if (joiningNumeric.equals(myNumeric)) {
                // ChanServ joined, grant rights to users already in channel
                String channel = elem[2].toLowerCase();
                grantRightsToChannelUsers(channel);
            } else {
                // Regular user joined, check for autoop/autovoice
                handleUserJoin(elem);
            }
        }
    }

    private void handleModeChange(String[] elem) {
        // Format: <numeric> M <channel> <timestamp> <modes> [<params>...]
        // Example: AAAAH M #test 1766952418 -o BBBBB
        //          AAAAH M #test 1766952418 +ov BBBBB CCCCC
        if (elem.length < 5) {
            return;
        }
        
        String channel = elem[2].toLowerCase();
        // elem[3] is timestamp, skip it
        String modes = elem[4];
        
        Channel chan = getSt().getChannel().get(channel);
        if (chan == null) {
            return;
        }
        
        getLogger().log(Level.INFO, "ChanServ: Processing mode change on {0}: {1}", 
                new Object[]{channel, String.join(" ", elem)});
        
        // Parse modes and their parameters
        boolean adding = true;
        int paramIndex = 5; // Parameters start at index 5 (after timestamp and modes)
        
        for (int i = 0; i < modes.length(); i++) {
            char mode = modes.charAt(i);
            
            switch (mode) {
                case '+':
                    adding = true;
                    break;
                case '-':
                    adding = false;
                    break;
                case 'o':
                    // Op mode - has a parameter (user numeric or nick)
                    if (paramIndex < elem.length) {
                        String target = elem[paramIndex];
                        String targetNumeric = resolveToNumeric(target);
                        if (targetNumeric != null) {
                            if (adding) {
                                if (!chan.getOp().contains(targetNumeric)) {
                                    chan.addOp(targetNumeric);
                                    getLogger().log(Level.INFO, "ChanServ: Added {0} to op list on {1}", 
                                            new Object[]{targetNumeric, channel});
                                }
                            } else {
                                chan.removeOp(targetNumeric);
                                getLogger().log(Level.INFO, "ChanServ: Removed {0} from op list on {1}", 
                                        new Object[]{targetNumeric, channel});
                            }
                        }
                        paramIndex++;
                    }
                    break;
                case 'v':
                    // Voice mode - has a parameter (user numeric or nick)
                    if (paramIndex < elem.length) {
                        String target = elem[paramIndex];
                        String targetNumeric = resolveToNumeric(target);
                        if (targetNumeric != null) {
                            if (adding) {
                                if (!chan.getVoice().contains(targetNumeric)) {
                                    chan.addVoice(targetNumeric);
                                    getLogger().log(Level.INFO, "ChanServ: Added {0} to voice list on {1}", 
                                            new Object[]{targetNumeric, channel});
                                }
                            } else {
                                chan.removeVoice(targetNumeric);
                                getLogger().log(Level.INFO, "ChanServ: Removed {0} from voice list on {1}", 
                                        new Object[]{targetNumeric, channel});
                            }
                        }
                        paramIndex++;
                    }
                    break;
                // Other modes that have parameters (not relevant for op/voice tracking)
                case 'l':
                case 'k':
                case 'b':
                    if (paramIndex < elem.length) {
                        paramIndex++; // Skip parameter
                    }
                    break;
                default:
                    // Modes without parameters, ignore
                    break;
            }
        }
    }

    /**
     * Resolves a user identifier (numeric or nick) to a numeric.
     * @param identifier User numeric or nick name
     * @return User numeric, or null if not found
     */
    private String resolveToNumeric(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return null;
        }
        
        // Check if it's already a numeric (5 characters, alphanumeric)
        if (identifier.length() == 5 && identifier.matches("[A-Za-z0-9]+")) {
            return identifier;
        }
        
        // It's likely a nick, search for the user
        for (Users user : getSt().getUsers().values()) {
            if (identifier.equalsIgnoreCase(user.getNick())) {
                return user.getId();
            }
        }
        
        getLogger().log(Level.WARNING, "ChanServ: Could not resolve ''{0}'' to numeric", identifier);
        return null;
    }

    private void handlePrivateMessage(String line, String[] elem) {
        String senderNumeric = elem[0];
        String targetNumeric = elem[2];
        
        // Check if message is for ChanServ
        String myNumeric = numeric + getNumericSuffix();
        if (!targetNumeric.equals(myNumeric)) {
            return;
        }

        if (!elem[3].startsWith(":")) {
            return;
        }

        String message = line.substring(line.indexOf(elem[3]) + 1);
        String[] parts = message.split(" ");
        
        if (parts.length == 0) {
            return;
        }

        String command = parts[0].toUpperCase();
        Users sender = getSt().getUsers().get(senderNumeric);
        
        if (sender == null) {
            return;
        }

        String senderNick = sender.getNick();
        String senderAccount = sender.getAccount();
        boolean isOper = isOperAccount(sender);

        switch (command) {
            case "HELP":
                handleHelp(senderNumeric, parts);
                break;
            case "SHOWCOMMANDS":
                handleShowCommands(senderNumeric);
                break;
            case "OP":
                handleOp(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "DEOP":
                handleDeop(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "VOICE":
                handleVoice(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "DEVOICE":
                handleDevoice(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "AUTOOP":
                handleAutoOp(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "AUTOVOICE":
                handleAutoVoice(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "INVITE":
                handleInvite(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "BAN":
                handleBan(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "UNBAN":
                handleUnban(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "KICK":
                handleKick(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "TEMPBAN":
                handleTempBan(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "PERMBAN":
                handlePermBan(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "LISTBANS":
                handleListBans(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "DELBAN":
                handleDelBan(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "TOPIC":
                handleTopic(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "SETTOPIC":
                handleSetTopic(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "ADDCHAN":
                handleAddChan(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "ADDUSER":
                handleAddUser(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "DELUSER":
                handleDelUser(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "CLEARCHAN":
                handleClearChan(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "CHANFLAGS":
                handleChanFlags(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "USERFLAGS":
                handleUserFlags(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "REQUESTOWNER":
                handleRequestOwner(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "REQUESTOP":
                handleRequestOp(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "CHANLEV":
                handleChanLev(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "INFO":
                handleInfo(senderNumeric, senderNick, parts);
                break;
            case "WHOIS":
                handleWhois(senderNumeric, senderNick, senderAccount, parts);
                break;
            case "VERSION":
                handleVersion(senderNumeric);
                break;
            // Administrative commands - require OPER/STAFF/ADMIN flags and authentication
            case "REGISTER":
                if (isOper && senderAccount != null && !senderAccount.isEmpty()) {
                    handleRegisterChannel(senderNumeric, senderNick, senderAccount, parts);
                } else {
                    sendNotice(senderNumeric, "Access denied: You need staff privileges and must be authenticated.");
                }
                break;
            case "DROP":
                if (isOper && senderAccount != null && !senderAccount.isEmpty()) {
                    handleDropChannel(senderNumeric, senderNick, parts);
                } else {
                    sendNotice(senderNumeric, "Access denied: You need staff privileges and must be authenticated.");
                }
                break;
            case "SUSPEND":
                if (isOper && senderAccount != null && !senderAccount.isEmpty()) {
                    handleSuspendChannel(senderNumeric, senderNick, parts);
                } else {
                    sendNotice(senderNumeric, "Access denied: You need staff privileges and must be authenticated.");
                }
                break;
            case "UNSUSPEND":
                if (isOper && senderAccount != null && !senderAccount.isEmpty()) {
                    handleUnsuspendChannel(senderNumeric, senderNick, parts);
                } else {
                    sendNotice(senderNumeric, "Access denied: You need staff privileges and must be authenticated.");
                }
                break;
            case "CLAIM":
                if (isOper && senderAccount != null && !senderAccount.isEmpty()) {
                    handleClaimChannel(senderNumeric, senderNick, senderAccount, parts);
                } else {
                    sendNotice(senderNumeric, "Access denied: You need staff privileges and must be authenticated.");
                }
                break;
            default:
                sendNotice(senderNumeric, "Unknown command: " + command + ". Use HELP for a list of commands.");
                break;
        }
    }

    private void handleVersion(String senderNumeric) {
        Software.BuildInfo buildInfo = Software.getBuildInfo();
        sendNotice(senderNumeric, String.format("ChanServ v%s by Andreas Pschorn (WarPigs)", buildInfo.getFullVersion()));
        sendNotice(senderNumeric, String.format("Based on JServ v%s", buildInfo.getFullVersion()));
        sendNotice(senderNumeric, "Channel service for MidiAndMore IRC Network");
    }

    private void handleHelp(String senderNumeric, String[] parts) {
        if (parts.length == 1) {
            sendNotice(senderNumeric, "Syntax: HELP <command>");
            sendNotice(senderNumeric, "For a list of commands, use SHOWCOMMANDS.");
        } else {
            String helpCommand = parts[1].toUpperCase();
            sendCommandHelp(senderNumeric, helpCommand);
        }
    }

    private void handleShowCommands(String senderNumeric) {
        Users sender = getSt().getUsers().get(senderNumeric);
        boolean isOper = isOperAccount(sender);
        
        sendNotice(senderNumeric, "***** ChanServ Commands *****");
        sendNotice(senderNumeric, "   ADDCHAN      Register a channel (requires OP)");
        sendNotice(senderNumeric, "   ADDUSER      Add user to channel access list");
        sendNotice(senderNumeric, "   AUTOOP       Set autoop for a user");
        sendNotice(senderNumeric, "   AUTOVOICE    Set autovoice for a user");
        sendNotice(senderNumeric, "   BAN          Set a ban on the channel");
        sendNotice(senderNumeric, "   CHANFLAGS    Display channel flags");
        sendNotice(senderNumeric, "   CHANLEV      List channel access list");
        sendNotice(senderNumeric, "   CLEARCHAN    Remove all users from channel");
        sendNotice(senderNumeric, "   DELBAN       Delete a ban from channel by mask or ID");
        sendNotice(senderNumeric, "   DELUSER      Remove user from access list");
        sendNotice(senderNumeric, "   DEOP         Remove operator status");
        sendNotice(senderNumeric, "   DEVOICE      Remove voice status");
        sendNotice(senderNumeric, "   HELP         Show help for a command");
        sendNotice(senderNumeric, "   INFO         Display channel information");
        sendNotice(senderNumeric, "   INVITE       Invite yourself to a channel");
        sendNotice(senderNumeric, "   KICK         Kick a user from the channel");
        sendNotice(senderNumeric, "   LISTBANS     List all bans on channel");
        sendNotice(senderNumeric, "   OP           Give operator status");
        sendNotice(senderNumeric, "   PERMBAN      Set permanent ban on channel");
        sendNotice(senderNumeric, "   REQUESTOP    Request op when channel has no ops");
        sendNotice(senderNumeric, "   SETTOPIC     Set channel topic");
        sendNotice(senderNumeric, "   SHOWCOMMANDS This message");
        sendNotice(senderNumeric, "   TEMPBAN      Set temporary ban on channel");
        sendNotice(senderNumeric, "   TOPIC        Display channel topic");
        sendNotice(senderNumeric, "   UNBAN        Unban yourself from a channel");
        sendNotice(senderNumeric, "   USERFLAGS    View/set user flags");
        sendNotice(senderNumeric, "   VERSION      Shows version information");
        sendNotice(senderNumeric, "   WHOIS        Display user info on channel");
        if (isOper) {
            sendNotice(senderNumeric, "   +s CLAIM       Claim ownership of a channel");
            sendNotice(senderNumeric, "   +s DROP        Drop/delete a channel");
            sendNotice(senderNumeric, "   +s REGISTER    Register a new channel");
            sendNotice(senderNumeric, "   +s SUSPEND     Suspend a channel");
            sendNotice(senderNumeric, "   +s UNSUSPEND   Unsuspend a channel");
        }
        sendNotice(senderNumeric, "***** End of List *****");
    }

    private void sendCommandHelp(String senderNumeric, String command) {
        switch (command) {
            case "OP":
                sendNotice(senderNumeric, "OP <#channel> [nick]");
                sendNotice(senderNumeric, "Gives operator (@) status to yourself or the specified user.");
                sendNotice(senderNumeric, "You must have +o flag or higher on the channel.");
                break;
            case "DEOP":
                sendNotice(senderNumeric, "DEOP <#channel> <nick>");
                sendNotice(senderNumeric, "Removes operator status from the specified user.");
                sendNotice(senderNumeric, "You must have +o flag or higher on the channel.");
                break;
            case "VOICE":
                sendNotice(senderNumeric, "VOICE <#channel> [nick]");
                sendNotice(senderNumeric, "Gives voice (+) status to yourself or the specified user.");
                sendNotice(senderNumeric, "You must have +v flag or higher on the channel.");
                break;
            case "DEVOICE":
                sendNotice(senderNumeric, "DEVOICE <#channel> <nick>");
                sendNotice(senderNumeric, "Removes voice status from the specified user.");
                sendNotice(senderNumeric, "You must have +v flag or higher on the channel.");
                break;
            case "INVITE":
                sendNotice(senderNumeric, "INVITE <#channel>");
                sendNotice(senderNumeric, "Invites you to the specified channel.");
                sendNotice(senderNumeric, "You must have access to the channel.");
                break;
            case "UNBAN":
                sendNotice(senderNumeric, "UNBAN <#channel>");
                sendNotice(senderNumeric, "Removes all bans matching your host from the channel.");
                sendNotice(senderNumeric, "You must have access to the channel.");
                break;
            case "REQUESTOP":
                sendNotice(senderNumeric, "REQUESTOP <#channel>");
                sendNotice(senderNumeric, "Requests operator status in a channel that has no ops.");
                sendNotice(senderNumeric, "This command only works if:");
                sendNotice(senderNumeric, "  - You are authenticated (authed)");
                sendNotice(senderNumeric, "  - The channel has no operators");
                sendNotice(senderNumeric, "  - ChanServ is not in the channel");
                sendNotice(senderNumeric, "You will receive server-side ops (@) if all conditions are met.");
                break;
            case "BAN":
                sendNotice(senderNumeric, "BAN <#channel> <mask> [reason]");
                sendNotice(senderNumeric, "Sets a ban on the specified channel with the given mask.");
                sendNotice(senderNumeric, "Mask format: nick!ident@host (wildcards * and ? allowed)");
                sendNotice(senderNumeric, "You must have +o (operator) flag or higher on the channel.");
                break;
            case "KICK":
                sendNotice(senderNumeric, "KICK <#channel> <nick> [reason]");
                sendNotice(senderNumeric, "Kicks the specified user from the channel.");
                sendNotice(senderNumeric, "You must have +o (operator) flag or higher on the channel.");
                break;
            case "TEMPBAN":
                sendNotice(senderNumeric, "TEMPBAN <#channel> <mask> <duration> [reason]");
                sendNotice(senderNumeric, "Sets a temporary ban on the channel that expires after the specified duration.");
                sendNotice(senderNumeric, "Duration format: 1w=week, 1d=day, 1h=hour, 1m=minute");
                sendNotice(senderNumeric, "Example: TEMPBAN #channel *!*@*.example.com 2d Spambot");
                sendNotice(senderNumeric, "Ban is stored in database and enforced on every JOIN.");
                sendNotice(senderNumeric, "You must have +o (operator) flag or higher on the channel.");
                break;
            case "PERMBAN":
                sendNotice(senderNumeric, "PERMBAN <#channel> <mask> [reason]");
                sendNotice(senderNumeric, "Sets a permanent ban on the channel that never expires.");
                sendNotice(senderNumeric, "Mask format: nick!ident@host (wildcards * and ? allowed)");
                sendNotice(senderNumeric, "Example: PERMBAN #channel *!*@*.evil.com Permanent ban");
                sendNotice(senderNumeric, "Ban is stored in database and enforced on every JOIN.");
                sendNotice(senderNumeric, "You must have +o (operator) flag or higher on the channel.");
                break;
            case "LISTBANS":
                sendNotice(senderNumeric, "LISTBANS <#channel>");
                sendNotice(senderNumeric, "Lists all active bans on the channel.");
                sendNotice(senderNumeric, "Shows ban mask, expiry time, reason, and who set it.");
                sendNotice(senderNumeric, "You must have +k (known) flag on the channel.");
                break;
            case "DELBAN":
                sendNotice(senderNumeric, "DELBAN <#channel> <banmask|banid>");
                sendNotice(senderNumeric, "Removes a ban from the channel by mask or ID.");
                sendNotice(senderNumeric, "Use LISTBANS to see active bans.");
                sendNotice(senderNumeric, "You must have +o flag on the channel.");
                break;
            case "TOPIC":
                sendNotice(senderNumeric, "TOPIC <#channel>");
                sendNotice(senderNumeric, "Displays the current topic of the channel.");
                break;
            case "SETTOPIC":
                sendNotice(senderNumeric, "SETTOPIC <#channel> [topic]");
                sendNotice(senderNumeric, "Sets the channel topic to the specified text.");
                sendNotice(senderNumeric, "If no topic is provided, uses the stored topic from database.");
                sendNotice(senderNumeric, "You must have +t (topic) flag or higher on the channel.");
                break;
            case "CLEARCHAN":
                sendNotice(senderNumeric, "CLEARCHAN <#channel>");
                sendNotice(senderNumeric, "Removes all users from the channel.");
                sendNotice(senderNumeric, "You must have +n (owner) flag on the channel.");
                break;
            case "ADDCHAN":
                sendNotice(senderNumeric, "ADDCHAN <#channel>");
                sendNotice(senderNumeric, " ");
                sendNotice(senderNumeric, "Registers a new channel. You must be operator (@) in the channel.");
                sendNotice(senderNumeric, "The channel must not be already registered.");
                sendNotice(senderNumeric, "You will become the channel owner with +master flag.");
                break;
            case "ADDUSER":
                sendNotice(senderNumeric, "ADDUSER <#channel> <user>");
                sendNotice(senderNumeric, "Adds a user to the channel's access list with default flags.");
                sendNotice(senderNumeric, "You must have +m (master) flag on the channel.");
                break;
            case "DELUSER":
                sendNotice(senderNumeric, "DELUSER <#channel> <user>");
                sendNotice(senderNumeric, "Removes a user from the channel's access list.");
                sendNotice(senderNumeric, "You must have +m (master) flag on the channel.");
                break;
            case "AUTOOP":
                sendNotice(senderNumeric, "AUTOOP <#channel> <user>");
                sendNotice(senderNumeric, "Sets autoop flag (+a) for the user on the channel.");
                sendNotice(senderNumeric, "User will automatically get ops when joining.");
                sendNotice(senderNumeric, "You must have +m (master) flag or higher on the channel.");
                break;
            case "AUTOVOICE":
                sendNotice(senderNumeric, "AUTOVOICE <#channel> <user>");
                sendNotice(senderNumeric, "Sets autovoice flag (+g) for the user on the channel.");
                sendNotice(senderNumeric, "User will automatically get voice when joining.");
                sendNotice(senderNumeric, "You must have +m (master) flag or higher on the channel.");
                break;
            case "USERFLAGS":
                sendNotice(senderNumeric, "USERFLAGS <#channel> <user> [+/-flags]");
                sendNotice(senderNumeric, "View or modify user flags on a channel.");
                sendNotice(senderNumeric, "Flags: n=owner m=master o=op v=voice a=autoop g=autovoice");
                sendNotice(senderNumeric, "       t=topic p=protect k=known j=autoinvite");
                sendNotice(senderNumeric, "Example: USERFLAGS #test user1 +ao-v (add autoop+op, remove voice)");
                break;
            case "CHANLEV":
                sendNotice(senderNumeric, "CHANLEV <#channel>");
                sendNotice(senderNumeric, "Lists all users on the channel access list with their flags.");
                sendNotice(senderNumeric, "Shows username, flags, and last seen time.");
                break;
            case "INFO":
                sendNotice(senderNumeric, "INFO <#channel>");
                sendNotice(senderNumeric, "Displays information about a registered channel.");
                sendNotice(senderNumeric, "Shows owner, creation time, last used, and user count.");
                break;
            case "WHOIS":
                sendNotice(senderNumeric, "WHOIS <#channel> <user>");
                sendNotice(senderNumeric, "Displays detailed information about a user's access on the channel.");
                sendNotice(senderNumeric, "Shows flags, added time, and last active time.");
                break;
            case "CHANFLAGS":
                sendNotice(senderNumeric, "CHANFLAGS <#channel>");
                sendNotice(senderNumeric, "Displays the flags and settings for the channel.");
                break;
            case "VERSION":
                sendNotice(senderNumeric, "VERSION");
                sendNotice(senderNumeric, "Shows ChanServ version information.");
                break;
            case "HELP":
                sendNotice(senderNumeric, "HELP [command]");
                sendNotice(senderNumeric, "Shows general help or detailed help for a specific command.");
                break;
            case "SHOWCOMMANDS":
                sendNotice(senderNumeric, "SHOWCOMMANDS");
                sendNotice(senderNumeric, "Lists all available ChanServ commands.");
                break;
            case "REGISTER":
                sendNotice(senderNumeric, "REGISTER <#channel> <owner>");
                sendNotice(senderNumeric, "Registers a new channel with the specified owner.");
                sendNotice(senderNumeric, "Requires: Staff privileges (+s)");
                break;
            case "DROP":
                sendNotice(senderNumeric, "DROP <#channel>");
                sendNotice(senderNumeric, "Drops (deletes) a registered channel.");
                sendNotice(senderNumeric, "Requires: Staff privileges (+s)");
                break;
            case "SUSPEND":
                sendNotice(senderNumeric, "SUSPEND <#channel> <reason>");
                sendNotice(senderNumeric, "Suspends a channel, disabling all channel services.");
                sendNotice(senderNumeric, "Requires: Staff privileges (+s)");
                break;
            case "UNSUSPEND":
                sendNotice(senderNumeric, "UNSUSPEND <#channel>");
                sendNotice(senderNumeric, "Removes suspension from a channel.");
                sendNotice(senderNumeric, "Requires: Staff privileges (+s)");
                break;
            case "CLAIM":
                sendNotice(senderNumeric, "CLAIM <#channel>");
                sendNotice(senderNumeric, "Claims ownership of a channel, making you the owner.");
                sendNotice(senderNumeric, "Requires: Staff privileges (+s)");
                break;
            default:
                sendNotice(senderNumeric, "No detailed help available for: " + command);
                sendNotice(senderNumeric, "Use SHOWCOMMANDS to see all available commands.");
                break;
        }
    }

    // Checks operator privileges using the user's account flags (no nick fallback)
    private boolean isOperAccount(Users user) {
        if (user == null) {
            return false;
        }
        // User muss als Oper eingeloggt sein
        if (!user.isOper()) {
            return false;
        }
        String acc = user.getAccount();
        if (acc == null || acc.isBlank()) {
            return false;
        }
        int flags = mi.getDb().getFlags(acc);
        return Userflags.hasFlag(flags, Userflags.Flag.OPER)
                || Userflags.hasFlag(flags, Userflags.Flag.HELPER)
                || Userflags.hasFlag(flags, Userflags.Flag.STAFF)
                || Userflags.hasFlag(flags, Userflags.Flag.DEV);
    }

    private void handleOp(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: OP <#channel> [nick]");
            return;
        }

        String channel = parts[1].toLowerCase();
        String targetNick = parts.length >= 3 ? parts[2] : senderNick;

        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.OP)) {
            return;
        }

        String targetNumeric = parts.length >= 3 ? getSt().getUserNumeric(targetNick) : senderNumeric;
        if (targetNumeric == null) {
            sendNotice(senderNumeric, "User " + targetNick + " is not online.");
            return;
        }

        // Check if user is in channel
        Channel chan = getSt().getChannel().get(channel);
        if (chan == null) {
            sendNotice(senderNumeric, "Channel " + channel + " does not exist.");
            return;
        }
        
        // Check if user is in channel - users list may contain numerics with status flags
        boolean userInChannel = false;
        for (String user : chan.getUsers()) {
            String userNumeric = user.contains(":") ? user.split(":", 2)[0] : user;
            if (userNumeric.equals(targetNumeric)) {
                userInChannel = true;
                break;
            }
        }
        
        if (!userInChannel) {
            sendNotice(senderNumeric, "User " + targetNick + " is not in " + channel);
            return;
        }

        sendText("%s%s M %s +o %s", numeric, getNumericSuffix(), channel, targetNumeric);
        
        // Update channel op list
        if (!chan.getOp().contains(targetNumeric)) {
            chan.addOp(targetNumeric);
            getLogger().log(Level.INFO, "ChanServ: Added {0} to op list on {1} (via OP command)", 
                    new Object[]{targetNumeric, channel});
        }
        
        getLogger().log(Level.INFO, "ChanServ: {0} opped {1} on {2}. Current ops: {3}", 
                new Object[]{senderNick, targetNick, channel, chan.getOp()});
    }

    private void handleDeop(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: DEOP <#channel> <nick>");
            return;
        }

        String channel = parts[1].toLowerCase();
        String targetNick = parts[2];

        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.OP)) {
            return;
        }

        String targetNumeric = getSt().getUserNumeric(targetNick);
        if (targetNumeric == null) {
            sendNotice(senderNumeric, "User " + targetNick + " is not online.");
            return;
        }

        Channel chan = getSt().getChannel().get(channel);
        if (chan == null) {
            sendNotice(senderNumeric, "Channel " + channel + " does not exist.");
            return;
        }
        
        // Check if user is in channel - users list may contain numerics with status flags
        boolean userInChannel = false;
        for (String user : chan.getUsers()) {
            String userNumeric = user.contains(":") ? user.split(":", 2)[0] : user;
            if (userNumeric.equals(targetNumeric)) {
                userInChannel = true;
                break;
            }
        }
        
        if (!userInChannel) {
            sendNotice(senderNumeric, "User " + targetNick + " is not in " + channel + ".");
            return;
        }

        sendText("%s%s M %s -o %s", numeric, getNumericSuffix(), channel, targetNumeric);
        
        // Update channel op list
        chan.removeOp(targetNumeric);
        getLogger().log(Level.INFO, "ChanServ: Removed {0} from op list on {1} (via DEOP command)", 
                new Object[]{targetNumeric, channel});
        
        getLogger().log(Level.INFO, "ChanServ: {0} deopped {1} on {2}. Current ops: {3}", 
                new Object[]{senderNick, targetNick, channel, chan.getOp()});
    }

    private void handleVoice(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: VOICE <#channel> [nick]");
            return;
        }

        String channel = parts[1].toLowerCase();
        String targetNick = parts.length >= 3 ? parts[2] : senderNick;

        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.VOICE)) {
            return;
        }

        String targetNumeric = parts.length >= 3 ? getSt().getUserNumeric(targetNick) : senderNumeric;
        if (targetNumeric == null) {
            sendNotice(senderNumeric, "User " + targetNick + " is not online.");
            return;
        }

        Channel chan = getSt().getChannel().get(channel);
        if (chan == null) {
            sendNotice(senderNumeric, "Channel " + channel + " does not exist.");
            return;
        }
        
        // Check if user is in channel - users list may contain numerics with status flags
        boolean userInChannel = false;
        for (String user : chan.getUsers()) {
            String userNumeric = user.contains(":") ? user.split(":", 2)[0] : user;
            if (userNumeric.equals(targetNumeric)) {
                userInChannel = true;
                break;
            }
        }
        
        if (!userInChannel) {
            sendNotice(senderNumeric, "User " + targetNick + " is not in " + channel);
            return;
        }

        sendText("%s%s M %s +v %s", numeric, getNumericSuffix(), channel, targetNumeric);
        
        // Update channel voice list
        if (!chan.getVoice().contains(targetNumeric)) {
            chan.addVoice(targetNumeric);
        }
        
        getLogger().log(Level.INFO, "ChanServ: {0} voiced {1} on {2}", 
                new Object[]{senderNick, targetNick, channel});
    }

    private void handleDevoice(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: DEVOICE <#channel> <nick>");
            return;
        }

        String channel = parts[1].toLowerCase();
        String targetNick = parts[2];

        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.VOICE)) {
            return;
        }

        String targetNumeric = getSt().getUserNumeric(targetNick);
        if (targetNumeric == null) {
            sendNotice(senderNumeric, "User " + targetNick + " is not online.");
            return;
        }

        Channel chan = getSt().getChannel().get(channel);
        if (chan == null) {
            sendNotice(senderNumeric, "Channel " + channel + " does not exist.");
            return;
        }
        
        // Check if user is in channel - users list may contain numerics with status flags
        boolean userInChannel = false;
        for (String user : chan.getUsers()) {
            String userNumeric = user.contains(":") ? user.split(":", 2)[0] : user;
            if (userNumeric.equals(targetNumeric)) {
                userInChannel = true;
                break;
            }
        }
        
        if (!userInChannel) {
            sendNotice(senderNumeric, "User " + targetNick + " is not in " + channel + ".");
            return;
        }

        sendText("%s%s M %s -v %s", numeric, getNumericSuffix(), channel, targetNumeric);
        
        // Update channel voice list
        chan.removeVoice(targetNumeric);
        
        getLogger().log(Level.INFO, "ChanServ: {0} devoiced {1} on {2}", 
                new Object[]{senderNick, targetNick, channel});
    }

    private void handleAutoOp(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: AUTOOP <#channel> <user>");
            return;
        }

        String channel = parts[1].toLowerCase();
        String targetUser = parts[2];
        
        // Support #account syntax
        String displayName = targetUser;
        if (targetUser.startsWith("#")) {
            displayName = targetUser.substring(1) + " (account)";
        }

        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.MASTER)) {
            return;
        }

        // Get channel and user IDs from database
        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return;
        }

        String userIdStr = lookupUserId(targetUser);
        if (userIdStr == null) {
            sendNotice(senderNumeric, "User " + displayName + " is not registered.");
            return;
        }

        long chanId = Long.parseLong(chanIdStr);
        long userId = Long.parseLong(userIdStr);

        // Get current flags and add autoop
        String[] userData = mi.getDb().getChanUser(userId, chanId);
        int flags = 0;
        if (userData != null && userData[0] != null) {
            try {
                flags = Integer.parseInt(userData[0]);
            } catch (NumberFormatException e) {
                flags = 0;
            }
        }

        flags = Userflags.setQCUFlag(flags, Userflags.QCUFlag.AUTOOP);
        
        if (mi.getDb().setChanUserFlags(userId, chanId, flags)) {
            sendNotice(senderNumeric, "Set AUTOOP (+a) for " + targetUser + " on " + channel);
            getLogger().log(Level.INFO, "ChanServ: {0} set autoop for {1} on {2}", 
                    new Object[]{senderNick, targetUser, channel});
        } else {
            sendNotice(senderNumeric, "Failed to set AUTOOP. User may need to be added first.");
        }
    }

    private void handleAutoVoice(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: AUTOVOICE <#channel> <user>");
            return;
        }

        String channel = parts[1].toLowerCase();
        String targetUser = parts[2];
        
        // Support #account syntax
        String displayName = targetUser;
        if (targetUser.startsWith("#")) {
            displayName = targetUser.substring(1) + " (account)";
        }

        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.OP)) {
            return;
        }

        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return;
        }

        String userIdStr = lookupUserId(targetUser);
        if (userIdStr == null) {
            sendNotice(senderNumeric, "User " + displayName + " is not registered.");
            return;
        }

        long chanId = Long.parseLong(chanIdStr);
        long userId = Long.parseLong(userIdStr);

        String[] userData = mi.getDb().getChanUser(userId, chanId);
        int flags = 0;
        if (userData != null && userData[0] != null) {
            try {
                flags = Integer.parseInt(userData[0]);
            } catch (NumberFormatException e) {
                flags = 0;
            }
        }

        flags = Userflags.setQCUFlag(flags, Userflags.QCUFlag.AUTOVOICE);
        
        if (mi.getDb().setChanUserFlags(userId, chanId, flags)) {
            sendNotice(senderNumeric, "Set AUTOVOICE (+g) for " + targetUser + " on " + channel);
            getLogger().log(Level.INFO, "ChanServ: {0} set autovoice for {1} on {2}", 
                    new Object[]{senderNick, targetUser, channel});
        } else {
            sendNotice(senderNumeric, "Failed to set AUTOVOICE. User may need to be added first.");
        }
    }

    private void handleInvite(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: INVITE <#channel>");
            return;
        }

        String channel = parts[1].toLowerCase();

        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.KNOWN)) {
            return;
        }

        sendText("%s%s I %s %s", numeric, getNumericSuffix(), senderNumeric, channel);
        sendNotice(senderNumeric, "Invited you to " + channel);
    }

    private void handleUnban(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: UNBAN <#channel>");
            return;
        }

        String channel = parts[1].toLowerCase();

        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.KNOWN)) {
            return;
        }

        Users user = getSt().getUsers().get(senderNumeric);
        if (user == null) {
            return;
        }

        // Remove all bans matching the user's host
        sendText("%s%s M %s -b *!*@%s", numeric, getNumericSuffix(), channel, user.getHost());
        sendNotice(senderNumeric, "Unbanned you from " + channel);
    }

    private void handleRequestOp(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: REQUESTOP <#channel>");
            return;
        }

        // Check if user is authenticated
        if (senderAccount == null || senderAccount.isEmpty()) {
            sendNotice(senderNumeric, "You must be authenticated to use this command.");
            return;
        }

        String channel = parts[1].toLowerCase();
        Channel chan = getSt().getChannel().get(channel);
        
        if (chan == null) {
            sendNotice(senderNumeric, "Channel " + channel + " does not exist.");
            return;
        }

        // Check if user is in channel - users list may contain numerics with status flags (e.g., "ABAAA:o")
        boolean userInChannel = false;
        for (String user : chan.getUsers()) {
            String userNumeric = user.contains(":") ? user.split(":", 2)[0] : user;
            if (userNumeric.equals(senderNumeric)) {
                userInChannel = true;
                break;
            }
        }
        
        if (!userInChannel) {
            sendNotice(senderNumeric, "You are not in " + channel);
            return;
        }

        // Check if ChanServ is in the channel
        String myNumeric = numeric + getNumericSuffix();
        boolean chanservInChannel = false;
        for (String user : chan.getUsers()) {
            String userNumeric = user.contains(":") ? user.split(":", 2)[0] : user;
            if (userNumeric.equals(myNumeric)) {
                chanservInChannel = true;
                break;
            }
        }
        
        if (chanservInChannel) {
            sendNotice(senderNumeric, "Cannot use REQUESTOP while ChanServ is in the channel.");
            return;
        }

        // Check if channel has any ops
        if (!chan.getOp().isEmpty()) {
            sendNotice(senderNumeric, "Channel " + channel + " already has operators.");
            getLogger().log(Level.INFO, "ChanServ: REQUESTOP denied on {0}. Current ops: {1}", 
                    new Object[]{channel, chan.getOp()});
            return;
        }

        // Grant server-side ops to the requesting user
        sendText("%s M %s +o %s", numeric, channel, senderNumeric);
        
        // Update channel op list
        if (!chan.getOp().contains(senderNumeric)) {
            chan.addOp(senderNumeric);
            getLogger().log(Level.INFO, "ChanServ: Added {0} to op list on {1} (via REQUESTOP)", 
                    new Object[]{senderNumeric, channel});
        }
        
        sendNotice(senderNumeric, "Granted operator status on " + channel);
        getLogger().log(Level.INFO, "ChanServ: {0} (auth: {1}) used REQUESTOP on {2}. Current ops: {3}", 
                new Object[]{senderNick, senderAccount, channel, chan.getOp()});
    }

    private void handleTopic(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: TOPIC <#channel>");
            return;
        }

        String channel = parts[1].toLowerCase();
        Channel chan = getSt().getChannel().get(channel);
        
        if (chan == null) {
            sendNotice(senderNumeric, "Channel " + channel + " does not exist.");
            return;
        }

        // Topic is not stored in Channel object, only in server
        sendNotice(senderNumeric, "Topic display not available. Use SETTOPIC to set a new topic.");
    }

    private void handleSetTopic(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: SETTOPIC <#channel> [topic]");
            sendNotice(senderNumeric, "If no topic is provided, the stored topic from the database will be set.");
            return;
        }

        String channel = parts[1].toLowerCase();
        String topic;
        
        if (parts.length < 3) {
            // No topic provided - fetch from database
            String storedTopic = mi.getDb().getChannel("topic", channel);
            if (storedTopic == null || storedTopic.isEmpty()) {
                sendNotice(senderNumeric, "No stored topic found for " + channel);
                sendNotice(senderNumeric, "Use: SETTOPIC <#channel> <topic> to set a new topic.");
                return;
            }
            topic = storedTopic;
            sendNotice(senderNumeric, "Setting stored topic for " + channel);
        } else {
            // Topic provided in command
            topic = String.join(" ", java.util.Arrays.copyOfRange(parts, 2, parts.length));
        }

        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.TOPIC)) {
            return;
        }

        sendText("%s%s T %s :%s", numeric, getNumericSuffix(), channel, topic);
        sendNotice(senderNumeric, "Topic for " + channel + " has been set.");
        getLogger().log(Level.INFO, "ChanServ: {0} set topic on {1}", 
                new Object[]{senderNick, channel});
    }

    private void handleAddChan(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: ADDCHAN <#channel>");
            return;
        }

        String channel = parts[1].toLowerCase();
        
        // Check if user is authenticated
        if (senderAccount == null || senderAccount.isBlank()) {
            sendNotice(senderNumeric, "You must be authenticated to use this command.");
            return;
        }

        // Check if channel is already registered
        String existingChanId = mi.getDb().getChannel("id", channel);
        if (existingChanId != null) {
            sendNotice(senderNumeric, "Channel " + channel + " is already registered.");
            return;
        }

        // Check if channel exists and user has OP
        Channel chan = getSt().getChannel().get(channel);
        if (chan == null) {
            sendNotice(senderNumeric, "Channel " + channel + " does not exist or is empty.");
            return;
        }

        // Check if user has OP status using Channel's op list
        System.out.println("[DEBUG] ADDCHAN: Checking OP status for " + senderNick + " (" + senderNumeric + ") in " + channel);
        System.out.println("[DEBUG] ADDCHAN: Channel has " + chan.getUsers().size() + " users");
        System.out.println("[DEBUG] ADDCHAN: Channel OPs: " + chan.getOp());
        
        boolean hasOp = chan.getOp().contains(senderNumeric);
        System.out.println("[DEBUG] ADDCHAN: User " + senderNumeric + " has OP: " + hasOp);

        if (!hasOp) {
            sendNotice(senderNumeric, "You must be operator (@) in " + channel + " to register it.");
            return;
        }

        // Get user ID
        long userId = mi.getDb().getUserId(senderAccount);
        if (userId == 0) {
            sendNotice(senderNumeric, "Error: Could not find your user account.");
            return;
        }

        // Register the channel
        long timestamp = System.currentTimeMillis() / 1000;
        if (!mi.getDb().registerChannel(channel, userId, timestamp)) {
            sendNotice(senderNumeric, "Error: Failed to register channel.");
            return;
        }

        // Get the new channel ID
        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Error: Channel was registered but could not retrieve ID.");
            return;
        }
        long chanId = Long.parseLong(chanIdStr);

        // Add user as master (owner)
        int masterFlags = Userflags.QCUFlag.MASTER.value | Userflags.QCUFlag.KNOWN.value 
                        | Userflags.QCUFlag.AUTOOP.value | Userflags.QCUFlag.OP.value;
        
        if (mi.getDb().addChanUser(userId, chanId, masterFlags)) {
            // Join the channel after successful registration
            String myNumeric = numeric + getNumericSuffix();
            getSt().joinChannel(channel, numeric, myNumeric);
            
            sendNotice(senderNumeric, "Successfully registered " + channel);
            sendNotice(senderNumeric, "You are now the channel owner with +master flag.");
            getLogger().log(Level.INFO, "ChanServ: {0} registered channel {1}", 
                    new Object[]{senderNick, channel});
        } else {
            sendNotice(senderNumeric, "Channel registered but failed to add you as owner. Contact an IRC operator.");
            getLogger().log(Level.WARNING, "ChanServ: {0} registered {1} but failed to add as owner", 
                    new Object[]{senderNick, channel});
        }
    }

    private void handleAddUser(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: ADDUSER <#channel> <user>");
            return;
        }

        String channel = parts[1].toLowerCase();
        String targetUser = parts[2];

        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.MASTER)) {
            return;
        }

        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return;
        }

        String userIdStr = mi.getDb().getData("id", targetUser);
        if (userIdStr == null) {
            sendNotice(senderNumeric, "User " + targetUser + " is not registered.");
            return;
        }

        long chanId = Long.parseLong(chanIdStr);
        long userId = Long.parseLong(userIdStr);

        // Add user with default KNOWN flag
        int defaultFlags = Userflags.QCUFlag.KNOWN.value;
        
        if (mi.getDb().addChanUser(userId, chanId, defaultFlags)) {
            sendNotice(senderNumeric, "Added " + targetUser + " to " + channel + " with default flags (+k)");
            getLogger().log(Level.INFO, "ChanServ: {0} added {1} to {2}", 
                    new Object[]{senderNick, targetUser, channel});
        } else {
            sendNotice(senderNumeric, "Failed to add user. They may already be on the access list.");
        }
    }

    private void handleDelUser(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: DELUSER <#channel> <user>");
            return;
        }

        String channel = parts[1].toLowerCase();
        String targetUser = parts[2];
        
        // Support #account syntax
        String displayName = targetUser;
        if (targetUser.startsWith("#")) {
            displayName = targetUser.substring(1) + " (account)";
        }

        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.MASTER)) {
            return;
        }

        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return;
        }

        String userIdStr = lookupUserId(targetUser);
        if (userIdStr == null) {
            sendNotice(senderNumeric, "User " + displayName + " is not registered.");
            return;
        }

        long chanId = Long.parseLong(chanIdStr);
        long userId = Long.parseLong(userIdStr);
        
        if (mi.getDb().deleteChanUser(userId, chanId)) {
            sendNotice(senderNumeric, "Removed " + displayName + " from " + channel);
            getLogger().log(Level.INFO, "ChanServ: {0} removed {1} from {2}", 
                    new Object[]{senderNick, displayName, channel});
        } else {
            sendNotice(senderNumeric, "Failed to remove user from channel.");
        }
    }

    private void handleClearChan(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: CLEARCHAN <#channel>");
            return;
        }

        String channel = parts[1].toLowerCase();

        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.MASTER)) {
            return;
        }

        Channel chan = getSt().getChannel().get(channel);
        if (chan == null) {
            sendNotice(senderNumeric, "Channel " + channel + " does not exist.");
            return;
        }

        int count = 0;
        for (String userNumeric : new java.util.ArrayList<>(chan.getUsers())) {
            // Don't kick ChanServ itself or opers
            if (!userNumeric.equals(numeric + getNumericSuffix())) {
                Users user = getSt().getUsers().get(userNumeric);
                if (user != null && !isOperAccount(user)) {
                    sendText("%s%s K %s %s :Channel cleared by %s", 
                            numeric, getNumericSuffix(), channel, userNumeric, senderNick);
                    count++;
                }
            }
        }

        sendNotice(senderNumeric, "Cleared " + count + " users from " + channel);
        getLogger().log(Level.INFO, "ChanServ: {0} cleared {1} ({2} users)", 
                new Object[]{senderNick, channel, count});
    }

    private void handleChanFlags(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: CHANFLAGS <#channel>");
            return;
        }

        String channel = parts[1].toLowerCase();
        
        // Check if user has access to view channel flags (KNOWN flag or higher)
        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.KNOWN)) {
            return;
        }
        
        String flagsStr = mi.getDb().getChannel("flags", channel);
        
        if (flagsStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return;
        }

        // Try to parse as integer and convert to readable format
        String readableFlags;
        try {
            int flags = Integer.parseInt(flagsStr);
            readableFlags = getChannelFlagString(flags);
        } catch (NumberFormatException e) {
            // If not a number, show as-is
            readableFlags = flagsStr;
        }

        sendNotice(senderNumeric, "Channel flags for " + channel + ": " + readableFlags + " (" + flagsStr + ")");
    }

    /**
     * Check if sender is Owner or Master on channel
     * @return OWNER flag if sender is owner, MASTER flag if sender is master, null otherwise
     */
    private Userflags.QCUFlag checkSenderAccessLevel(String senderNumeric, String senderAccount, String channel) {
        if (senderAccount == null || senderAccount.isEmpty()) {
            sendNotice(senderNumeric, "You must be authenticated to use this command.");
            return null;
        }

        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return null;
        }

        String userIdStr = mi.getDb().getData("id", senderAccount);
        if (userIdStr == null) {
            sendNotice(senderNumeric, "Your account is not registered.");
            return null;
        }

        long chanId = Long.parseLong(chanIdStr);
        long userId = Long.parseLong(userIdStr);

        String[] userData = mi.getDb().getChanUser(userId, chanId);
        if (userData == null) {
            sendNotice(senderNumeric, "You do not have access to " + channel);
            return null;
        }

        int flags = 0;
        try {
            flags = Integer.parseInt(userData[0]);
        } catch (NumberFormatException e) {
            flags = 0;
        }

        if (Userflags.hasQCUFlag(flags, Userflags.QCUFlag.OWNER)) {
            return Userflags.QCUFlag.OWNER;
        }
        if (Userflags.hasQCUFlag(flags, Userflags.QCUFlag.MASTER)) {
            return Userflags.QCUFlag.MASTER;
        }

        sendNotice(senderNumeric, "You need Owner or Master flag on " + channel + ".");
        return null;

    }
    private void handleUserFlags(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: USERFLAGS <#channel> <user> [+/-flags]");
            return;
        }

        String channel = parts[1].toLowerCase();
        String targetUser = parts[2];
        
        // Support #account syntax to explicitly lookup by account name
        String displayName = targetUser;
        if (targetUser.startsWith("#")) {
            displayName = targetUser.substring(1) + " (account)";
        }

        // Only Owner and Master can set flags
        Userflags.QCUFlag requiredFlag = checkSenderAccessLevel(senderNumeric, senderAccount, channel);
        if (requiredFlag == null) {
            sendNotice(senderNumeric, "You need Owner or Master flag to use this command.");
            return;
        }
        boolean isMaster = requiredFlag == Userflags.QCUFlag.MASTER;

        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return;
        }

        String userIdStr = lookupUserId(targetUser);
        if (userIdStr == null) {
            sendNotice(senderNumeric, "User " + displayName + " is not registered.");
            return;
        }

        long chanId = Long.parseLong(chanIdStr);
        long userId = Long.parseLong(userIdStr);

        String[] userData = mi.getDb().getChanUser(userId, chanId);
        if (userData == null) {
            sendNotice(senderNumeric, "User " + displayName + " is not on the access list for " + channel);
            return;
        }

        int currentFlags = 0;
        try {
            currentFlags = Integer.parseInt(userData[0]);
        } catch (NumberFormatException e) {
            currentFlags = 0;
        }

        // If no flags parameter, just display current flags
        if (parts.length < 4) {
            String flagString = getFlagString(currentFlags);
            sendNotice(senderNumeric, "Flags for " + displayName + " on " + channel + ": " + flagString);
            return;
        }

        // Parse and apply flag changes
        String flagChanges = parts[3];
        int newFlags = currentFlags;
        boolean adding = true;

        for (char c : flagChanges.toCharArray()) {
            if (c == '+') {
                adding = true;
            } else if (c == '-') {
                adding = false;
            } else {
                Userflags.QCUFlag flag = Userflags.qcuFlagFromChar(c);
                if (flag != null) {
                    // Master cannot set/unset owner flag
                    if (isMaster && flag == Userflags.QCUFlag.OWNER) {
                        sendNotice(senderNumeric, "You cannot set the owner flag. Only owners can do this.");
                        return;
                    }
                    if (adding) {
                        newFlags = Userflags.setQCUFlag(newFlags, flag);
                    } else {
                        newFlags = Userflags.clearQCUFlag(newFlags, flag);
                    }
                }
            }
        }

        if (mi.getDb().setChanUserFlags(userId, chanId, newFlags)) {
            String flagString = getFlagString(newFlags);
            sendNotice(senderNumeric, "Flags for " + targetUser + " on " + channel + " are now: " + flagString);
            getLogger().log(Level.INFO, "ChanServ: {0} changed flags for {1} on {2} to {3}", 
                    new Object[]{senderNick, targetUser, channel, flagString});
            
            // Find user numeric - prefer by nick first, then by account
            String targetNumeric = null;
            Users targetUserObj = null;
            
            // Create final copy for lambda expressions
            final String lookupName = targetUser;
            
            // First try to find by nick
            targetUserObj = st.getUsers().values().stream()
                .filter(u -> u.getNick().equalsIgnoreCase(lookupName))
                .findFirst()
                .orElse(null);
            
            // If not found by nick, try by account
            if (targetUserObj == null) {
                targetUserObj = st.getUsers().values().stream()
                    .filter(u -> u.getAccount() != null && u.getAccount().equalsIgnoreCase(lookupName))
                    .findFirst()
                    .orElse(null);
            }
            
            if (targetUserObj != null) {
                for (String num : st.getUsers().keySet()) {
                    if (st.getUsers().get(num) == targetUserObj) {
                        targetNumeric = num;
                        break;
                    }
                }
            }
            
            // Apply modes if user is online and in channel
            if (targetNumeric != null) {
                Channel chan = getSt().getChannel().get(channel);
                if (isUserPresentInChannel(targetNumeric, chan)) {
                    // +a or +o → give OP
                    if (Userflags.hasQCUFlag(newFlags, Userflags.QCUFlag.AUTOOP) || 
                        Userflags.hasQCUFlag(newFlags, Userflags.QCUFlag.OP)) {
                        if (!chan.getOp().contains(targetNumeric)) {
                            sendText("%s%s M %s +o %s", numeric, getNumericSuffix(), channel, targetNumeric);
                        }
                    }
                    
                    // +g or +v → give VOICE
                    if (Userflags.hasQCUFlag(newFlags, Userflags.QCUFlag.AUTOVOICE) || 
                        Userflags.hasQCUFlag(newFlags, Userflags.QCUFlag.VOICE)) {
                        if (!chan.getVoice().contains(targetNumeric)) {
                            sendText("%s%s M %s +v %s", numeric, getNumericSuffix(), channel, targetNumeric);
                        }
                    }
                    
                    // +b → ban and kick immediately (using hidden host only)
                    if (Userflags.hasQCUFlag(newFlags, Userflags.QCUFlag.BANNED)) {
                        String account = targetUserObj.getAccount();
                        if (account != null && !account.isEmpty()) {
                            String accountBan = "*!*@" + account + getBanDomain();
                            sendText("%s%s M %s +b %s", numeric, getNumericSuffix(), channel, accountBan);
                        }
                        
                        sendText("%s%s K %s %s :Banned (user flagged with +b)", numeric, getNumericSuffix(), channel, targetNumeric);
                        getLogger().log(Level.INFO, "ChanServ: Automatically banned and kicked {0} from {1}", 
                                new Object[]{targetUserObj.getNick(), channel});
                    }
                    
                    // -b → remove bans (remove hidden host)
                    if (!Userflags.hasQCUFlag(newFlags, Userflags.QCUFlag.BANNED) && 
                        Userflags.hasQCUFlag(currentFlags, Userflags.QCUFlag.BANNED)) {
                        String account = targetUserObj.getAccount();
                        if (account != null && !account.isEmpty()) {
                            String accountBan = "*!*@" + account + getBanDomain();
                            sendText("%s%s M %s -b %s", numeric, getNumericSuffix(), channel, accountBan);
                        }
                        
                        getLogger().log(Level.INFO, "ChanServ: Removed ban for {0} from {1}", 
                                new Object[]{targetUserObj.getNick(), channel});
                    }
                }
            }
        } else {
            sendNotice(senderNumeric, "Failed to update flags.");
        }
    }

    /**
     * Apply channel flags for a user after successful authentication (IDENTIFY/SASL).
     * - If user has +b on a channel: set ban mask and kick
     * - Else grant OP/VOICE according to flags when the user is present
     */
    public void applyFlagsAfterAuth(String userNumeric, String username) {
        if (userNumeric == null || username == null || username.isEmpty()) {
            return;
        }

        for (Channel chan : st.getChannel().values()) {
            if (!isUserPresentInChannel(userNumeric, chan)) {
                continue;
            }

            String channelName = chan.getName().toLowerCase();
            String chanIdStr = mi.getDb().getChannel("id", channelName);
            if (chanIdStr == null) {
                continue;
            }

            String userIdStr = mi.getDb().getData("id", username);
            if (userIdStr == null) {
                continue;
            }

            try {
                long chanId = Long.parseLong(chanIdStr);
                long userId = Long.parseLong(userIdStr);
                String[] userData = mi.getDb().getChanUser(userId, chanId);
                if (userData == null) {
                    continue;
                }

                int flags = Integer.parseInt(userData[0]);

                // Ban takes priority
                if (Userflags.hasQCUFlag(flags, Userflags.QCUFlag.BANNED)) {
                    String accountBan = "*!*@" + username + getBanDomain();
                    sendText("%s%s M %s +b %s", numeric, getNumericSuffix(), channelName, accountBan);
                    sendText("%s%s K %s %s :Banned (user flagged with +b)", numeric, getNumericSuffix(), channelName, userNumeric);
                    getLogger().log(Level.INFO, "ChanServ: Auth ban applied to {0} on {1}", new Object[]{username, channelName});
                    continue;
                }

                // Grant OP only if flag is present AND user is in channel
                if ((Userflags.hasQCUFlag(flags, Userflags.QCUFlag.AUTOOP) ||
                     Userflags.hasQCUFlag(flags, Userflags.QCUFlag.OP)) &&
                    !chan.getOp().contains(userNumeric)) {
                    sendText("%s%s M %s +o %s", numeric, getNumericSuffix(), channelName, userNumeric);
                    continue;
                }

                // Grant VOICE only if flag is present AND user is in channel
                if ((Userflags.hasQCUFlag(flags, Userflags.QCUFlag.AUTOVOICE) ||
                     Userflags.hasQCUFlag(flags, Userflags.QCUFlag.VOICE)) &&
                    !chan.getVoice().contains(userNumeric)) {
                    sendText("%s%s M %s +v %s", numeric, getNumericSuffix(), channelName, userNumeric);
                }

            } catch (NumberFormatException e) {
                getLogger().log(Level.WARNING, "ChanServ: Failed to parse IDs for {0} on {1}", new Object[]{username, channelName});
            }
        }
    }

    private void handleChanLev(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: CHANLEV <#channel>");
            return;
        }

        String channel = parts[1].toLowerCase();
        
        // Check if user has access to view channel list (KNOWN flag or higher)
        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.KNOWN)) {
            return;
        }
        
        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return;
        }

        long chanId = Long.parseLong(chanIdStr);
        java.util.List<String[]> users = mi.getDb().getChanUsers(chanId);
        
        if (users == null || users.isEmpty()) {
            sendNotice(senderNumeric, "No users on access list for " + channel);
            return;
        }

        sendNotice(senderNumeric, "Access list for " + channel + ":");
        sendNotice(senderNumeric, "Username                 Flags");
        sendNotice(senderNumeric, "----------------------------------------");
        
        for (String[] user : users) {
            String username = user[0];
            int flags = Integer.parseInt(user[1]);
            String flagString = getFlagString(flags);
            sendNotice(senderNumeric, String.format("%-24s %s", username, flagString));
        }
        
        sendNotice(senderNumeric, "End of access list.");
    }

    private void handleRequestOwner(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: REQUESTOWNER <#channel>");
            return;
        }

        String channel = parts[1].toLowerCase();

        if (senderAccount == null || senderAccount.isEmpty()) {
            sendNotice(senderNumeric, "You must be authenticated to use this command.");
            return;
        }

        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return;
        }

        String userIdStr = mi.getDb().getData("id", senderAccount);
        if (userIdStr == null) {
            sendNotice(senderNumeric, "Your account is not registered.");
            return;
        }

        long chanId = Long.parseLong(chanIdStr);
        long userId = Long.parseLong(userIdStr);

        String[] userData = mi.getDb().getChanUser(userId, chanId);
        if (userData == null) {
            sendNotice(senderNumeric, "You do not have access to " + channel);
            return;
        }

        int flags = 0;
        try {
            flags = Integer.parseInt(userData[0]);
        } catch (NumberFormatException e) {
            flags = 0;
        }

        // Only Masters can request owner
        if (!Userflags.hasQCUFlag(flags, Userflags.QCUFlag.MASTER)) {
            sendNotice(senderNumeric, "Only masters can request ownership of a channel.");
            return;
        }

        // Check if channel already has an owner
        java.util.List<String[]> users = mi.getDb().getChanUsers(chanId);
        boolean hasOwner = false;
        if (users != null) {
            for (String[] user : users) {
                try {
                    int userFlags = Integer.parseInt(user[1]);
                    if (Userflags.hasQCUFlag(userFlags, Userflags.QCUFlag.OWNER)) {
                        hasOwner = true;
                        break;
                    }
                } catch (NumberFormatException e) {
                    // skip
                }
            }
        }

        if (hasOwner) {
            sendNotice(senderNumeric, "Channel " + channel + " already has an owner. Cannot request ownership.");
            return;
        }

        // Grant owner to this master
        if (mi.getDb().setChanUserFlags(userId, chanId, Userflags.setQCUFlag(flags, Userflags.QCUFlag.OWNER))) {
            sendNotice(senderNumeric, "You are now owner of " + channel + ".");
            getLogger().log(Level.INFO, "ChanServ: {0} became owner of {1} (REQUESTOWNER)", 
                    new Object[]{senderNick, channel});
        } else {
            sendNotice(senderNumeric, "Failed to grant ownership. Please contact an administrator.");
        }
    }

    private void handleInfo(String senderNumeric, String senderNick, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: INFO <#channel>");
            return;
        }

        String channel = parts[1].toLowerCase();
        
        String founder = mi.getDb().getChannel("founder", channel);
        if (founder == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return;
        }

        sendNotice(senderNumeric, "Information for " + channel + ":");
        
        // Convert founder ID to username
        String founderName = getUsernameById(founder);
        sendNotice(senderNumeric, "Founder: " + (founderName != null ? founderName : founder));
        
        String addedby = mi.getDb().getChannel("addedby", channel);
        if (addedby != null && !addedby.isEmpty()) {
            String addedByName = getUsernameById(addedby);
            sendNotice(senderNumeric, "Added by: " + (addedByName != null ? addedByName : addedby));
        }
        
        String created = mi.getDb().getChannel("created", channel);
        if (created != null && !created.isEmpty()) {
            String createdDate = formatTimestamp(created);
            sendNotice(senderNumeric, "Created: " + createdDate);
        }
        
        String lastactive = mi.getDb().getChannel("lastactive", channel);
        if (lastactive != null && !lastactive.isEmpty()) {
            String lastactiveDate = formatTimestamp(lastactive);
            sendNotice(senderNumeric, "Last active: " + lastactiveDate);
        }
    }

    private void handleWhois(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: WHOIS <#channel> <user>");
            return;
        }

        String channel = parts[1].toLowerCase();
        String targetUser = parts[2];
        
        // Check if user has access to view user info (KNOWN flag or higher)
        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.KNOWN)) {
            return;
        }

        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return;
        }

        String userIdStr = mi.getDb().getData("id", targetUser);
        if (userIdStr == null) {
            sendNotice(senderNumeric, "User " + targetUser + " is not registered.");
            return;
        }

        long chanId = Long.parseLong(chanIdStr);
        long userId = Long.parseLong(userIdStr);

        String[] userData = mi.getDb().getChanUser(userId, chanId);
        if (userData == null) {
            sendNotice(senderNumeric, "User " + targetUser + " is not on the access list for " + channel);
            return;
        }

        int flags = 0;
        try {
            flags = Integer.parseInt(userData[0]);
        } catch (NumberFormatException e) {
            flags = 0;
        }

        String flagString = getFlagString(flags);
        sendNotice(senderNumeric, "User information for " + targetUser + " on " + channel + ":");
        sendNotice(senderNumeric, "Flags: " + flagString);
        
        if (userData[1] != null && !userData[1].isEmpty()) {
            sendNotice(senderNumeric, "Last changed: " + userData[1]);
        }
        if (userData[2] != null && !userData[2].isEmpty()) {
            sendNotice(senderNumeric, "Last used: " + userData[2]);
        }
        if (userData[3] != null && !userData[3].isEmpty()) {
            sendNotice(senderNumeric, "Info: " + userData[3]);
        }
    }

    private boolean checkChannelAccess(String senderNumeric, String senderAccount, String channel, Userflags.QCUFlag requiredFlag) {
        if (senderAccount == null || senderAccount.isEmpty()) {
            sendNotice(senderNumeric, "You must be authenticated to use this command.");
            return false;
        }

        // Check if user is currently OP in the channel - OPs can do everything except MASTER/OWNER commands
        Channel chan = getSt().getChannel().get(channel);
        if (chan != null && chan.getOp().contains(senderNumeric)) {
            // Channel OPs have OP, VOICE, TOPIC, KNOWN flags automatically
            if (requiredFlag == Userflags.QCUFlag.OP || 
                requiredFlag == Userflags.QCUFlag.VOICE || 
                requiredFlag == Userflags.QCUFlag.TOPIC ||
                requiredFlag == Userflags.QCUFlag.KNOWN) {
                return true;
            }
        }

        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return false;
        }
        
        // Check if channel is suspended
        String suspendBy = mi.getDb().getChannel("suspendby", channel);
        if (suspendBy != null && !suspendBy.isEmpty() && !suspendBy.equals("0")) {
            String reason = mi.getDb().getChannel("suspendreason", channel);
            sendNotice(senderNumeric, "Channel " + channel + " is suspended: " + (reason != null ? reason : "No reason given"));
            return false;
        }

        String userIdStr = mi.getDb().getData("id", senderAccount);
        if (userIdStr == null) {
            sendNotice(senderNumeric, "Your account is not registered.");
            return false;
        }

        long chanId = Long.parseLong(chanIdStr);
        long userId = Long.parseLong(userIdStr);

        String[] userData = mi.getDb().getChanUser(userId, chanId);
        if (userData == null) {
            sendNotice(senderNumeric, "Access denied. You are not on the access list for " + channel);
            return false;
        }

        int flags = 0;
        try {
            flags = Integer.parseInt(userData[0]);
        } catch (NumberFormatException e) {
            sendNotice(senderNumeric, "Access denied.");
            return false;
        }

        // Check if user has required flag
        if (!Userflags.hasQCUFlag(flags, requiredFlag)) {
            // Also check for higher flags (owner or master override everything)
            if (!Userflags.hasQCUFlag(flags, Userflags.QCUFlag.OWNER) && 
                !Userflags.hasQCUFlag(flags, Userflags.QCUFlag.MASTER)) {
                sendNotice(senderNumeric, "Access denied. You need +" + requiredFlag.code + " flag or higher.");
                return false;
            }
        }

        return true;
    }

    private String getFlagString(int flags) {
        StringBuilder sb = new StringBuilder("+");
        for (Userflags.QCUFlag flag : Userflags.QCUFlag.values()) {
            if (Userflags.hasQCUFlag(flags, flag)) {
                sb.append(flag.code);
            }
        }
        return sb.length() > 1 ? sb.toString() : "+";
    }

    private String getChannelFlagString(int flags) {
        StringBuilder sb = new StringBuilder("+");

        for (Userflags.ChannelFlag flag : Userflags.ChannelFlag.values()) {
            if (Userflags.hasChannelFlag(flags, flag)) {
                sb.append(flag.code);
            }
        }

        return sb.length() > 1 ? sb.toString() : "+";
    }

    private void sendNotice(String targetNumeric, String message) {
        sendText("%s%s O %s :%s", numeric, getNumericSuffix(), targetNumeric, message);
    }

    private void handleUserJoin(String[] elem) {
        String userNumeric = elem[0];
        String channel = elem[2].toLowerCase();
        
        Users user = getSt().getUsers().get(userNumeric);
        if (user == null || user.getAccount() == null || user.getAccount().isEmpty()) {
            return;
        }
        
        String account = user.getAccount();
        
        // Check if channel is registered
        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            return;
        }

        int channelFlags = getChannelFlags(channel);
        
        // Early enforcement: suspended or known-only
        if (Userflags.hasChannelFlag(channelFlags, Userflags.ChannelFlag.SUSPENDED)) {
            sendText("%s%s K %s %s :Channel suspended", numeric, getNumericSuffix(), channel, userNumeric);
            return;
        }
        if (Userflags.hasChannelFlag(channelFlags, Userflags.ChannelFlag.KNOWNONLY)
                && (user.getAccount() == null || user.getAccount().isBlank())) {
            sendText("%s%s K %s %s :Known-only channel", numeric, getNumericSuffix(), channel, userNumeric);
            return;
        }
        
        // Check for channel bans from database
        long chanId = Long.parseLong(chanIdStr);
        String userHost = user.getNick() + "!" + user.getIdent() + "@" + user.getHost();
        String realUserHost = user.getNick() + "!" + user.getIdent() + "@" + user.getRealHost();
        String hiddenHostMask = user.getHiddenHost() != null ? user.getNick() + "!" + user.getHiddenHost() : null;
        
        ArrayList<String[]> bans = mi.getDb().getChannelBans(chanId);
        long currentTime = System.currentTimeMillis() / 1000;
        
        getLogger().log(Level.INFO, "ChanServ: Checking bans for {0} joining {1} - userHost: {2}, realHost: {3}, hiddenHost: {4}, bans found: {5}",
                new Object[]{user.getNick(), channel, userHost, realUserHost, hiddenHostMask, bans.size()});
        for (String[] ban : bans) {
            String banId = ban[0]; // ban ID
            String banMask = ban[1]; // hostmask
            String expiryStr = ban[2]; // expiry
            String banReason = ban[3]; // reason
            
            // Check if ban has expired
            long expiry = Long.parseLong(expiryStr);
            if (expiry > 0 && expiry < currentTime) {
                // Ban has expired - remove it
                long banIdLong = Long.parseLong(banId);
                if (mi.getDb().removeChannelBan(banIdLong)) {
                    sendText("%s%s M %s -b %s", numeric, getNumericSuffix(), channel, banMask);
                    System.out.println("[INFO] Removed expired ban " + banId + " (" + banMask + ") from " + channel);
                }
                continue; // Skip expired ban
            }
            
            getLogger().log(Level.INFO, "ChanServ: Testing ban mask {0} against {1}, {2} and {3}",
                    new Object[]{banMask, userHost, realUserHost, hiddenHostMask});
            // Check visible host, real host, and hidden host
            if (mi.getDb().checkBanMatch(userHost, banMask) 
                    || mi.getDb().checkBanMatch(realUserHost, banMask)
                    || (hiddenHostMask != null && mi.getDb().checkBanMatch(hiddenHostMask, banMask))) {
                // Apply ban and kick user
                sendText("%s%s M %s +b %s", numeric, getNumericSuffix(), channel, banMask);
                String kickReason = (banReason != null && !banReason.isEmpty()) ? "Banned: " + banReason : "Banned";
                sendText("%s%s K %s %s :%s", numeric, getNumericSuffix(), channel, userNumeric, kickReason);
                getLogger().log(Level.INFO, "ChanServ: Banned and kicked {0} from {1} (matched ban: {2})",
                        new Object[]{user.getNick(), channel, banMask});
                return; // User is kicked, no need to continue
            }
        }

        // Check if user has access
        String userIdStr = mi.getDb().getData("id", account);
        if (userIdStr == null) {
            return;
        }
        
        long userId = Long.parseLong(userIdStr);
        
        String[] userData = mi.getDb().getChanUser(userId, chanId);
        if (userData == null) {
            return;
        }
        
        int flags = 0;
        try {
            flags = Integer.parseInt(userData[0]);
        } catch (NumberFormatException e) {
            return;
        }
        
        // Update last use time
        mi.getDb().setChanUserFlags(userId, chanId, flags);

        // Check for BANNED flag - ban and kick immediately (using hidden host only)
        if (Userflags.hasQCUFlag(flags, Userflags.QCUFlag.BANNED)) {
            if (account != null && !account.isEmpty()) {
                String accountBan = "*!*@" + account + getBanDomain();
                sendText("%s%s M %s +b %s", numeric, getNumericSuffix(), channel, accountBan);
            }
            
            sendText("%s%s K %s %s :Banned (user flagged with +b)", numeric, getNumericSuffix(), channel, userNumeric);
            getLogger().log(Level.INFO, "ChanServ: Banned and kicked {0} from {1} (BANNED flag on join)",
                    new Object[]{user.getNick(), channel});
            return; // User is kicked, no need to give modes
        }

        boolean modeGiven = false;

        // Channel-level autoop has priority
        if (Userflags.hasChannelFlag(channelFlags, Userflags.ChannelFlag.AUTOOP)) {
            sendText("%s%s M %s +o %s", numeric, getNumericSuffix(), channel, userNumeric);
            modeGiven = true;
            getLogger().log(Level.INFO, "ChanServ: Channel AUTOOP gave op to {0} on {1}",
                    new Object[]{user.getNick(), channel});
        } else if (Userflags.hasQCUFlag(flags, Userflags.QCUFlag.AUTOOP)) {
            sendText("%s%s M %s +o %s", numeric, getNumericSuffix(), channel, userNumeric);
            modeGiven = true;
            getLogger().log(Level.INFO, "ChanServ: Auto-opped {0} on {1}", 
                    new Object[]{user.getNick(), channel});
        }

        // Channel-level autovoice/voiceall, if no op given yet
        if (!modeGiven) {
            if (Userflags.hasChannelFlag(channelFlags, Userflags.ChannelFlag.AUTOVOICE)
                    || Userflags.hasChannelFlag(channelFlags, Userflags.ChannelFlag.VOICEALL)) {
                sendText("%s%s M %s +v %s", numeric, getNumericSuffix(), channel, userNumeric);
                modeGiven = true;
                getLogger().log(Level.INFO, "ChanServ: Channel AUTOVOICE/VOICEALL gave voice to {0} on {1}",
                        new Object[]{user.getNick(), channel});
            } else if (Userflags.hasQCUFlag(flags, Userflags.QCUFlag.AUTOVOICE)) {
                sendText("%s%s M %s +v %s", numeric, getNumericSuffix(), channel, userNumeric);
                modeGiven = true;
                getLogger().log(Level.INFO, "ChanServ: Auto-voiced {0} on {1}", 
                        new Object[]{user.getNick(), channel});
            }
        }

        enforceBitchMode(channel, chanId, channelFlags);
    }

    /**
     * Get the ban domain for account-based bans (e.g., .users.midiandmore.net)
     * Defaults to .users.midiandmore.net if not configured
     */
    private String getBanDomain() {
        String banDomain = mi.getConfig().getConfigFile().getProperty("chanserv_ban_domain");
        if (banDomain == null || banDomain.isEmpty()) {
            banDomain = ".users.midiandmore.net";
        }
        return banDomain;
    }

    /**
     * Lookup user ID by name. Supports two lookup modes:
     * 1. With #account prefix: explicit account lookup
     * 2. Without prefix: tries username first (if registered), then falls back to account
     * 
     * @param userIdentifier The user identifier (with optional #account prefix)
     * @return User ID string, or null if not found
     */
    private String lookupUserId(String userIdentifier) {
        if (userIdentifier.startsWith("#")) {
            // Explicit account lookup
            String accountName = userIdentifier.substring(1);
            return mi.getDb().getData("id", accountName);
        } else {
            // Try username first (registered users)
            String userIdStr = mi.getDb().getData("id", userIdentifier);
            if (userIdStr != null) {
                return userIdStr;
            }
            // Fall back to account name
            return mi.getDb().getData("id", userIdentifier);
        }
    }

    private int getChannelFlags(String channel) {
        String flagsStr = mi.getDb().getChannel("flags", channel);
        if (flagsStr == null) {
            return 0;
        }
        try {
            return Integer.parseInt(flagsStr);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void enforceBitchMode(String channel, long chanId, int channelFlags) {
        // Bitch mode: only users on the access list with op-level rights may keep +o
        if (!Userflags.hasChannelFlag(channelFlags, Userflags.ChannelFlag.BITCH)) {
            return;
        }
        Channel chan = getSt().getChannel().get(channel);
        if (chan == null) {
            return;
        }

        String myNumeric = numeric + getNumericSuffix();
        for (String opNumeric : new ArrayList<>(chan.getOp())) {
            // Never strip ChanServ itself
            if (opNumeric.equals(myNumeric)) {
                continue;
            }

            Users opUser = getSt().getUsers().get(opNumeric);
            if (opUser == null) {
                continue;
            }

            boolean authorized = false;
            String account = opUser.getAccount();
            if (account != null && !account.isBlank()) {
                String userIdStr = mi.getDb().getData("id", account);
                if (userIdStr != null) {
                    try {
                        long userId = Long.parseLong(userIdStr);
                        String[] userData = mi.getDb().getChanUser(userId, chanId);
                        if (userData != null) {
                            int userFlags = Integer.parseInt(userData[0]);
                            authorized = Userflags.hasQCUFlag(userFlags, Userflags.QCUFlag.OP)
                                    || Userflags.hasQCUFlag(userFlags, Userflags.QCUFlag.MASTER)
                                    || Userflags.hasQCUFlag(userFlags, Userflags.QCUFlag.OWNER)
                                    || Userflags.hasQCUFlag(userFlags, Userflags.QCUFlag.PROTECT);
                        }
                    } catch (NumberFormatException ignored) {
                        // Fall through to unauthorized
                    }
                }
            }

            if (!authorized) {
                sendText("%s%s M %s -o %s", numeric, getNumericSuffix(), channel, opNumeric);
                getLogger().log(Level.INFO, "ChanServ: Bitch mode removed op from {0} on {1}",
                        new Object[]{opUser.getNick(), channel});
            }
        }
    }
    
    private void grantRightsToChannelUsers(String channel) {
        // Get channel from database
        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            return;
        }
        
        long chanId = Long.parseLong(chanIdStr);
        
        // Get all users in the channel
        Channel chan = getSt().getChannel().get(channel);
        if (chan == null) {
            return;
        }
        
        for (String userNumeric : new ArrayList<>(chan.getUsers())) {
            // Verify user is still in channel before processing
            if (!chan.getUsers().contains(userNumeric)) {
                continue;
            }
            
            Users user = getSt().getUsers().get(userNumeric);
            if (user == null || user.getAccount() == null || user.getAccount().isEmpty()) {
                continue;
            }
            
            // Get user ID from database
            String userIdStr = mi.getDb().getData("id", user.getAccount());
            if (userIdStr == null) {
                continue;
            }
            
            long userId = Long.parseLong(userIdStr);
            
            // Get user's flags on this channel
            String[] userData = mi.getDb().getChanUser(userId, chanId);
            if (userData == null) {
                continue;
            }
            
            int flags = 0;
            try {
                flags = Integer.parseInt(userData[0]);
            } catch (NumberFormatException e) {
                continue;
            }
            
            // Final verification: user numeric must exist in users map and be in channel
            if (!isUserPresentInChannel(userNumeric, chan)) {
                continue;
            }
            
            // Check if user has autoop and doesn't have op yet
            if (Userflags.hasQCUFlag(flags, Userflags.QCUFlag.AUTOOP)) {
                if (chan.getOp().contains(userNumeric)) {
                    continue; // Already has op
                }
                sendText("%s%s M %s +o %s", numeric, getNumericSuffix(), channel, userNumeric);
                getLogger().log(Level.INFO, "ChanServ: Granted op to {0} on {1}", 
                        new Object[]{user.getNick(), channel});
            }
            // Check if user has autovoice and doesn't have voice yet
            else if (Userflags.hasQCUFlag(flags, Userflags.QCUFlag.AUTOVOICE)) {
                if (chan.getVoice().contains(userNumeric)) {
                    continue; // Already has voice
                }
                sendText("%s%s M %s +v %s", numeric, getNumericSuffix(), channel, userNumeric);
                getLogger().log(Level.INFO, "ChanServ: Granted voice to {0} on {1}", 
                        new Object[]{user.getNick(), channel});
            }
        }
    }


    // Helper: check if a numeric refers to an online user who is in the given channel
    private boolean isUserPresentInChannel(String userNumeric, Channel chan) {
        return userNumeric != null && chan != null && st.getUsers().containsKey(userNumeric) && chan.getUsers().contains(userNumeric);
    }

    @Override
    public void registerBurstChannels(HashMap<String, Burst> bursts, String serverNumeric) {
        if (!enabled) {
            return;
        }
        
        // Always join #twilightzone
        String channel = "#twilightzone";
        if (!bursts.containsKey(channel.toLowerCase())) {
            bursts.put(channel.toLowerCase(), new Burst(channel));
        }
        bursts.get(channel.toLowerCase()).getUsers().add(serverNumeric + getNumericSuffix());
        getLogger().log(Level.INFO, "ChanServ registered burst channel: {0}", channel);
        
        // Load and join all registered channels from database
        try {
            java.util.ArrayList<String[]> registeredChannels = mi.getDb().getChannels();
            if (registeredChannels != null) {
                int count = 0;
                for (String[] chanData : registeredChannels) {
                    if (chanData.length > 1) {
                        String chanName = chanData[1]; // name is second column
                        if (chanName != null && chanName.startsWith("#")) {
                            String chanLower = chanName.toLowerCase();
                            if (!bursts.containsKey(chanLower)) {
                                bursts.put(chanLower, new Burst(chanName));
                            }
                            
                            Burst burst = bursts.get(chanLower);
                            if (!burst.getUsers().contains(serverNumeric + getNumericSuffix())) {
                                burst.getUsers().add(serverNumeric + getNumericSuffix());
                                count++;
                            }
                            
                            // Set channel modes if forcemodes is set
                            if (chanData.length > 2 && chanData[2] != null && !chanData[2].isEmpty()) {
                                String forcedModes = chanData[2];
                                burst.setModes(forcedModes);
                                getLogger().log(Level.INFO, "Set forced modes for {0}: {1}", new Object[]{chanName, forcedModes});
                            }
                            
                            // Set stored topic if available (topic is at index 22 in channels table)
                            if (chanData.length > 22 && chanData[22] != null && !chanData[22].isEmpty()) {
                                String storedTopic = chanData[22];
                                burst.setTopic(storedTopic);
                                getLogger().log(Level.INFO, "Prepared stored topic for {0}", chanName);
                            }
                        }
                    }
                }
                if (count > 0) {
                    getLogger().log(Level.INFO, "ChanServ joined {0} registered channels from database", count);
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to load registered channels from database: " + e.getMessage(), e);
        }
    }

    @Override
    public void shutdown() {
        // Nothing to do
    }

    // Administrative commands for Staff/Oper
    
    private void handleRegisterChannel(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: REGISTER <#channel> <owner>");
            return;
        }
        
        String channel = parts[1].toLowerCase();
        String ownerName = parts[2];
        
        if (!channel.startsWith("#")) {
            sendNotice(senderNumeric, "Invalid channel name. Must start with #");
            return;
        }
        
        // Check if channel already exists
        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr != null) {
            sendNotice(senderNumeric, "Channel " + channel + " is already registered.");
            return;
        }
        
        // Get owner user ID
        String ownerIdStr = mi.getDb().getData("id", ownerName);
        if (ownerIdStr == null) {
            sendNotice(senderNumeric, "User " + ownerName + " is not registered.");
            return;
        }
        
        long ownerId = Long.parseLong(ownerIdStr);
        long now = System.currentTimeMillis() / 1000;
        
        // Register channel in database
        boolean success = mi.getDb().registerChannel(channel, ownerId, now);
        if (success) {
            sendNotice(senderNumeric, "Channel " + channel + " has been registered with " + ownerName + " as owner.");
            getLogger().log(Level.INFO, "ChanServ: {0} registered channel {1} for {2}", 
                    new Object[]{senderNick, channel, ownerName});
            
            // Join ChanServ to the newly registered channel
            String myNumeric = numeric + getNumericSuffix();
            long timestamp = System.currentTimeMillis() / 1000;
            sendText("%s C %s %d", myNumeric, channel, timestamp);
            sendText("%s M %s +o %s", numeric, channel, myNumeric);
            
            // Give op to owner if they're in the channel
            String ownerNumeric = getSt().getUserNumeric(ownerName);
            if (ownerNumeric != null) {
                Channel chan = getSt().getChannel().get(channel);
                if (chan != null && chan.getUsers().contains(ownerNumeric)) {
                    sendText("%s%s M %s +o %s", numeric, getNumericSuffix(), channel, ownerNumeric);
                }
            }
        } else {
            sendNotice(senderNumeric, "Failed to register channel " + channel);
        }
    }
    
    private void handleDropChannel(String senderNumeric, String senderNick, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: DROP <#channel>");
            return;
        }
        
        String channel = parts[1].toLowerCase();
        
        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return;
        }
        
        long chanId = Long.parseLong(chanIdStr);
        boolean success = mi.getDb().dropChannel(chanId);
        
        if (success) {
            sendNotice(senderNumeric, "Channel " + channel + " has been dropped.");
            getLogger().log(Level.WARNING, "ChanServ: {0} dropped channel {1}", 
                    new Object[]{senderNick, channel});
            
            // Part via SocketThread helper and update local state
            getSt().partChannel(channel, numeric, getNumericSuffix());
            getSt().removeUser(numeric + getNumericSuffix(), channel);
        } else {
            sendNotice(senderNumeric, "Failed to drop channel " + channel);
        }
    }
    
    private void handleSuspendChannel(String senderNumeric, String senderNick, String[] parts) {
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: SUSPEND <#channel> <reason>");
            return;
        }
        
        String channel = parts[1].toLowerCase();
        StringBuilder reasonBuilder = new StringBuilder();
        for (int i = 2; i < parts.length; i++) {
            if (i > 2) reasonBuilder.append(" ");
            reasonBuilder.append(parts[i]);
        }
        String reason = reasonBuilder.toString();
        
        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return;
        }
        
        long chanId = Long.parseLong(chanIdStr);
        boolean success = mi.getDb().suspendChannel(chanId, reason);
        
        if (success) {
            sendNotice(senderNumeric, "Channel " + channel + " has been suspended.");
            sendNotice(senderNumeric, "Reason: " + reason);
            
            // Kick all users from the channel
            Channel chan = getSt().getChannel().get(channel);
            if (chan != null && !chan.getUsers().isEmpty()) {
                // Get a copy of the user list to avoid concurrent modification
                String[] users = chan.getUsers().toArray(new String[0]);
                String kickReason = "Channel suspended: " + reason;
                
                for (String user : users) {
                    // Send KICK command for each user
                    sendText("%s K %s %s :%s", numeric + getNumericSuffix(), channel, user, kickReason);
                    // Remove user from local channel tracking
                    getSt().removeUser(user, channel);
                }
            }
            
            getLogger().log(Level.WARNING, "ChanServ: {0} suspended channel {1}: {2}", 
                    new Object[]{senderNick, channel, reason});
        } else {
            sendNotice(senderNumeric, "Failed to suspend channel " + channel);
        }
    }
    
    private void handleUnsuspendChannel(String senderNumeric, String senderNick, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: UNSUSPEND <#channel>");
            return;
        }
        
        String channel = parts[1].toLowerCase();
        
        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return;
        }
        
        long chanId = Long.parseLong(chanIdStr);
        boolean success = mi.getDb().unsuspendChannel(chanId);
        
        if (success) {
            sendNotice(senderNumeric, "Channel " + channel + " has been unsuspended.");
            getLogger().log(Level.INFO, "ChanServ: {0} unsuspended channel {1}", 
                    new Object[]{senderNick, channel});
        } else {
            sendNotice(senderNumeric, "Failed to unsuspend channel " + channel);
        }
    }
    
    private void handleClaimChannel(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: CLAIM <#channel>");
            return;
        }
        
        String channel = parts[1].toLowerCase();
        
        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return;
        }
        
        String userIdStr = mi.getDb().getData("id", senderAccount);
        if (userIdStr == null) {
            sendNotice(senderNumeric, "Your account is not registered.");
            return;
        }
        
        long chanId = Long.parseLong(chanIdStr);
        long userId = Long.parseLong(userIdStr);
        
        // Check if user already has access
        String[] existingAccess = mi.getDb().getChanUser(userId, chanId);
        if (existingAccess != null) {
            // Update flags to OWNER
            int ownerFlags = Userflags.QCUFlag.OWNER.value | Userflags.QCUFlag.MASTER.value | 
                             Userflags.QCUFlag.OP.value | Userflags.QCUFlag.AUTOOP.value;
            mi.getDb().updateChanUserFlags(userId, chanId, ownerFlags);
        }
        
        // Add user with OWNER flag
        int ownerFlags = Userflags.QCUFlag.OWNER.value | Userflags.QCUFlag.MASTER.value | 
                         Userflags.QCUFlag.OP.value | Userflags.QCUFlag.AUTOOP.value;
        
        boolean success = existingAccess != null ? true : mi.getDb().addChanUser(userId, chanId, ownerFlags);
        
        if (success) {
            sendNotice(senderNumeric, "You are now the owner of " + channel);
            getLogger().log(Level.WARNING, "ChanServ: {0} claimed ownership of channel {1}", 
                    new Object[]{senderNick, channel});
            
            // Give op if user is in channel
            Channel chan = getSt().getChannel().get(channel);
            if (isUserPresentInChannel(senderNumeric, chan)) {
                sendText("%s%s M %s +o %s", numeric, getNumericSuffix(), channel, senderNumeric);
            }
        } else {
            sendNotice(senderNumeric, "Failed to claim channel " + channel);
        }
    }
    
    /**
     * Convert user ID to username
     */
    private String getUsernameById(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        
        try {
            long id = Long.parseLong(userId);
            // Use a custom query to get username by ID
            String username = mi.getDb().getUsernameById(id);
            return username != null ? username : userId;
        } catch (NumberFormatException e) {
            // If not a number, might already be a username
            return userId;
        }
    }

    /**
     * Format Unix timestamp to readable date
     */
    private String formatTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return timestamp;
        }
        
        try {
            long unixTime = Long.parseLong(timestamp);
            java.time.Instant instant = java.time.Instant.ofEpochSecond(unixTime);
            java.time.format.DateTimeFormatter formatter = 
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(java.time.ZoneId.systemDefault());
            return formatter.format(instant);
        } catch (NumberFormatException e) {
            return timestamp;
        }
    }

    private void handleBan(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: BAN <#channel> <mask> [reason]");
            return;
        }
        String channel = parts[1].toLowerCase();
        String mask = parts[2];
        String reason = parts.length >= 4 ? String.join(" ", java.util.Arrays.copyOfRange(parts, 3, parts.length)) : "Ban set by ChanServ";
        
        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.OP)) {
            return;
        }
        
        sendText("%s%s M %s +b %s", numeric, getNumericSuffix(), channel, mask);
        sendNotice(senderNumeric, "Ban set on " + channel + " for " + mask);
        getLogger().log(Level.INFO, "ChanServ: {0} banned {1} on {2}: {3}", new Object[]{senderNick, mask, channel, reason});
    }
    
    private void handleKick(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: KICK <#channel> <nick> [reason]");
            return;
        }
        String channel = parts[1].toLowerCase();
        String targetNick = parts[2];
        String reason = parts.length >= 4 ? String.join(" ", java.util.Arrays.copyOfRange(parts, 3, parts.length)) : "Kicked by ChanServ";
        
        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.OP)) {
            return;
        }
        
        String targetNumeric = getSt().getUserNumeric(targetNick);
        if (targetNumeric == null) {
            sendNotice(senderNumeric, "User " + targetNick + " is not online.");
            return;
        }
        
        // Verify user is in channel
        Channel chan = getSt().getChannel().get(channel);
        if (chan == null || !chan.getUsers().contains(targetNumeric)) {
            sendNotice(senderNumeric, "User " + targetNick + " is not in " + channel);
            return;
        }
        
        sendText("%s%s K %s %s :%s", numeric, getNumericSuffix(), channel, targetNumeric, reason);
        getLogger().log(Level.INFO, "ChanServ: {0} kicked {1} from {2}: {3}", new Object[]{senderNick, targetNick, channel, reason});
    }
    /**
     * Parses duration string (e.g., "1d", "2h", "30m") to seconds
     * @param duration Duration string (supports: w=weeks, d=days, h=hours, m=minutes)
     * @return Duration in seconds, or -1 if invalid
     */
    private long parseDuration(String duration) {
        if (duration == null || duration.length() < 2) {
            return -1;
        }
        
        try {
            String numPart = duration.substring(0, duration.length() - 1);
            char unit = duration.charAt(duration.length() - 1);
            long value = Long.parseLong(numPart);
            
            switch (unit) {
                case 'w':
                    return value * 604800; // weeks
                case 'd':
                    return value * 86400; // days
                case 'h':
                    return value * 3600; // hours
                case 'm':
                    return value * 60; // minutes
                default:
                    return -1;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
    }
    
    private void handleTempBan(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 4) {
            sendNotice(senderNumeric, "Syntax: TEMPBAN <#channel> <mask> <duration> [reason]");
            sendNotice(senderNumeric, "Duration: 1w=week, 1d=day, 1h=hour, 1m=minute");
            return;
        }
        
        String channel = parts[1].toLowerCase();
        String mask = parts[2];
        String durationStr = parts[3];
        String reason = parts.length >= 5 ? String.join(" ", java.util.Arrays.copyOfRange(parts, 4, parts.length)) : "Temporary ban";
        
        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.OP)) {
            return;
        }
        
        long durationSeconds = parseDuration(durationStr);
        if (durationSeconds < 0) {
            sendNotice(senderNumeric, "Invalid duration. Use format: 1w, 1d, 2h, 30m");
            return;
        }
        
        // Get channel ID
        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return;
        }
        long channelId = Long.parseLong(chanIdStr);
        
        // Check if ban mask already exists
        ArrayList<String[]> existingBans = mi.getDb().getChannelBans(channelId);
        for (String[] ban : existingBans) {
            String existingMask = ban[1];
            if (existingMask.equalsIgnoreCase(mask)) {
                sendNotice(senderNumeric, "Ban mask " + mask + " already exists on " + channel);
                sendNotice(senderNumeric, "Use DELBAN to remove it first, or use a different mask.");
                return;
            }
        }
        
        // Get user ID
        long userId = mi.getDb().getUserId(senderAccount);
        if (userId == 0) {
            sendNotice(senderNumeric, "Error: Could not find your user account.");
            return;
        }
        
        // Calculate expiry
        long expiry = (System.currentTimeMillis() / 1000) + durationSeconds;
        
        // Add ban to database
        if (!mi.getDb().addChannelBan(channelId, userId, mask, expiry, reason)) {
            sendNotice(senderNumeric, "Error: Failed to add ban to database.");
            return;
        }
        
        // Set ban on channel
        sendText("%s%s M %s +b %s", numeric, getNumericSuffix(), channel, mask);
        
        // Check if any users in channel match the ban and kick them
        Channel chan = getSt().getChannel().get(channel);
        if (chan != null) {
            System.out.println("[DEBUG] TEMPBAN: Checking " + chan.getUsers().size() + " users in " + channel);
            for (String userEntry : chan.getUsers()) {
                System.out.println("[DEBUG] TEMPBAN: Checking user entry: " + userEntry);
                
                // Remove mode suffix (:o, :v, etc.)
                String userNumeric = userEntry;
                if (userEntry.contains(":")) {
                    userNumeric = userEntry.split(":")[0];
                }
                
                System.out.println("[DEBUG] TEMPBAN: Looking up numeric: " + userNumeric);
                Users user = getSt().getUsers().get(userNumeric);
                System.out.println("[DEBUG] TEMPBAN: User object: " + (user != null ? user.getNick() : "NULL"));
                if (user != null) {
                    String userHost = user.getNick() + "!" + user.getIdent() + "@" + user.getHost();
                    String realUserHost = user.getNick() + "!" + user.getIdent() + "@" + user.getRealHost();
                    String hiddenHostMask = user.getHiddenHost() != null ? user.getNick() + "!" + user.getHiddenHost() : null;
                    
                    System.out.println("[DEBUG] TEMPBAN: User " + user.getNick() + " - visible: " + userHost + ", real: " + realUserHost + ", hidden: " + hiddenHostMask);
                    System.out.println("[DEBUG] TEMPBAN: Testing against mask: " + mask);
                    
                    // Check if any of the host masks match the ban
                    boolean matchVisible = mi.getDb().checkBanMatch(userHost, mask);
                    boolean matchReal = mi.getDb().checkBanMatch(realUserHost, mask);
                    boolean matchHidden = hiddenHostMask != null && mi.getDb().checkBanMatch(hiddenHostMask, mask);
                    
                    System.out.println("[DEBUG] TEMPBAN: Match results - visible: " + matchVisible + ", real: " + matchReal + ", hidden: " + matchHidden);
                    
                    if (matchVisible || matchReal || matchHidden) {
                        System.out.println("[DEBUG] TEMPBAN: Kicking " + user.getNick() + " (" + userNumeric + ")");
                        String kickReason = (reason != null && !reason.isEmpty()) ? "Banned: " + reason : "Banned";
                        sendText("%s%s K %s %s :%s", numeric, getNumericSuffix(), channel, userNumeric, kickReason);
                    }
                }
            }
        }
        
        sendNotice(senderNumeric, "Temporary ban set on " + channel + " for " + mask + " (expires in " + durationStr + ")");
        getLogger().log(Level.INFO, "ChanServ: {0} set temporary ban {1} on {2} for {3}: {4}", 
                       new Object[]{senderNick, mask, channel, durationStr, reason});
    }
    
    private void handlePermBan(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: PERMBAN <#channel> <mask> [reason]");
            return;
        }
        
        String channel = parts[1].toLowerCase();
        String mask = parts[2];
        String reason = parts.length >= 4 ? String.join(" ", java.util.Arrays.copyOfRange(parts, 3, parts.length)) : "Permanent ban";
        
        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.OP)) {
            return;
        }
        
        // Get channel ID
        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return;
        }
        long channelId = Long.parseLong(chanIdStr);
        
        // Check if ban mask already exists
        ArrayList<String[]> existingBans = mi.getDb().getChannelBans(channelId);
        for (String[] ban : existingBans) {
            String existingMask = ban[1];
            if (existingMask.equalsIgnoreCase(mask)) {
                sendNotice(senderNumeric, "Ban mask " + mask + " already exists on " + channel);
                sendNotice(senderNumeric, "Use DELBAN to remove it first, or use a different mask.");
                return;
            }
        }
        
        // Get user ID
        long userId = mi.getDb().getUserId(senderAccount);
        if (userId == 0) {
            sendNotice(senderNumeric, "Error: Could not find your user account.");
            return;
        }
        
        // Add permanent ban to database (expiry = 0)
        if (!mi.getDb().addChannelBan(channelId, userId, mask, 0, reason)) {
            sendNotice(senderNumeric, "Error: Failed to add ban to database.");
            return;
        }
        
        // Set ban on channel
        sendText("%s%s M %s +b %s", numeric, getNumericSuffix(), channel, mask);
        
        // Check if any users in channel match the ban and kick them
        Channel chan = getSt().getChannel().get(channel);
        if (chan != null) {
            System.out.println("[DEBUG] PERMBAN: Checking " + chan.getUsers().size() + " users in " + channel);
            for (String userEntry : chan.getUsers()) {
                System.out.println("[DEBUG] PERMBAN: Checking user entry: " + userEntry);
                
                // Remove mode suffix (:o, :v, etc.)
                String userNumeric = userEntry;
                if (userEntry.contains(":")) {
                    userNumeric = userEntry.split(":")[0];
                }
                
                System.out.println("[DEBUG] PERMBAN: Looking up numeric: " + userNumeric);
                Users user = getSt().getUsers().get(userNumeric);
                System.out.println("[DEBUG] PERMBAN: User object: " + (user != null ? user.getNick() : "NULL"));
                if (user != null) {
                    String userHost = user.getNick() + "!" + user.getIdent() + "@" + user.getHost();
                    String realUserHost = user.getNick() + "!" + user.getIdent() + "@" + user.getRealHost();
                    String hiddenHostMask = user.getHiddenHost() != null ? user.getNick() + "!" + user.getHiddenHost() : null;
                    
                    System.out.println("[DEBUG] PERMBAN: User " + user.getNick() + " - visible: " + userHost + ", real: " + realUserHost + ", hidden: " + hiddenHostMask);
                    System.out.println("[DEBUG] PERMBAN: Testing against mask: " + mask);
                    
                    // Check if any of the host masks match the ban
                    boolean matchVisible = mi.getDb().checkBanMatch(userHost, mask);
                    boolean matchReal = mi.getDb().checkBanMatch(realUserHost, mask);
                    boolean matchHidden = hiddenHostMask != null && mi.getDb().checkBanMatch(hiddenHostMask, mask);
                    
                    System.out.println("[DEBUG] PERMBAN: Match results - visible: " + matchVisible + ", real: " + matchReal + ", hidden: " + matchHidden);
                    
                    if (matchVisible || matchReal || matchHidden) {
                        System.out.println("[DEBUG] PERMBAN: Kicking " + user.getNick() + " (" + userNumeric + ")");
                        String kickReason = (reason != null && !reason.isEmpty()) ? "Banned: " + reason : "Banned";
                        sendText("%s%s K %s %s :%s", numeric, getNumericSuffix(), channel, userNumeric, kickReason);
                    }
                }
            }
        }
        
        sendNotice(senderNumeric, "Permanent ban set on " + channel + " for " + mask);
        getLogger().log(Level.INFO, "ChanServ: {0} set permanent ban {1} on {2}: {3}", 
                       new Object[]{senderNick, mask, channel, reason});
    }
    
    private void handleListBans(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: LISTBANS <#channel>");
            return;
        }
        
        String channel = parts[1].toLowerCase();
        
        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.KNOWN)) {
            return;
        }
        
        // Get channel ID
        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return;
        }
        long channelId = Long.parseLong(chanIdStr);
        
        // Get all bans for this channel
        ArrayList<String[]> bans = mi.getDb().getChannelBans(channelId);
        
        if (bans.isEmpty()) {
            sendNotice(senderNumeric, "No bans found for " + channel);
            return;
        }
        
        sendNotice(senderNumeric, "***** Ban list for " + channel + " *****");
        long currentTime = System.currentTimeMillis() / 1000;
        
        int activeBans = 0;
        int expiredBans = 0;
        
        for (String[] ban : bans) {
            String banId = ban[0];
            String hostmask = ban[1];
            String expiryStr = ban[2];
            String reason = ban[3];
            String setBy = ban[4];
            
            long expiry = Long.parseLong(expiryStr);
            String expiryText;
            
            if (expiry == 0) {
                expiryText = "Never";
                activeBans++;
            } else {
                long remaining = expiry - currentTime;
                if (remaining < 0) {
                    // Ban is expired - remove it
                    expiredBans++;
                    long banIdLong = Long.parseLong(banId);
                    if (mi.getDb().removeChannelBan(banIdLong)) {
                        sendText("%s%s M %s -b %s", numeric, getNumericSuffix(), channel, hostmask);
                        System.out.println("[INFO] Removed expired ban " + banId + " (" + hostmask + ") from " + channel);
                    }
                    continue; // Skip displaying expired ban
                } else {
                    expiryText = formatDuration(remaining);
                    activeBans++;
                }
            }
            
            sendNotice(senderNumeric, String.format("%s [Expires: %s]", hostmask, expiryText));
            sendNotice(senderNumeric, String.format("     Reason: %s | Set by: %s", reason, setBy));
        }
        
        sendNotice(senderNumeric, "***** End of ban list *****");
        sendNotice(senderNumeric, "Total: " + activeBans + " active ban(s)" + (expiredBans > 0 ? " (" + expiredBans + " expired ban(s) removed)" : ""));
    }
    
    private void handleDelBan(String senderNumeric, String senderNick, String senderAccount, String[] parts) {
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: DELBAN <#channel> <banmask|banid>");
            return;
        }
        
        String channel = parts[1].toLowerCase();
        String banIdentifier = parts[2];
        
        if (!checkChannelAccess(senderNumeric, senderAccount, channel, Userflags.QCUFlag.OP)) {
            return;
        }
        
        // Get channel ID
        String chanIdStr = mi.getDb().getChannel("id", channel);
        if (chanIdStr == null) {
            sendNotice(senderNumeric, "Channel " + channel + " is not registered.");
            return;
        }
        long channelId = Long.parseLong(chanIdStr);
        
        // Get all bans for this channel
        ArrayList<String[]> bans = mi.getDb().getChannelBans(channelId);
        
        // Try to find ban by ID or mask
        long banId = -1;
        String banMask = null;
        
        // First try to parse as ID
        try {
            long testId = Long.parseLong(banIdentifier);
            for (String[] ban : bans) {
                if (ban[0].equals(banIdentifier)) {
                    banId = testId;
                    banMask = ban[1];
                    break;
                }
            }
        } catch (NumberFormatException e) {
            // Not a number, try to match as mask
        }
        
        // If not found by ID, try to match by mask
        if (banMask == null) {
            for (String[] ban : bans) {
                if (ban[1].equalsIgnoreCase(banIdentifier)) {
                    banId = Long.parseLong(ban[0]);
                    banMask = ban[1];
                    break;
                }
            }
        }
        
        if (banMask == null) {
            sendNotice(senderNumeric, "Ban " + banIdentifier + " not found on " + channel);
            return;
        }
        
        // Remove ban from database
        if (!mi.getDb().removeChannelBan(banId)) {
            sendNotice(senderNumeric, "Error: Failed to remove ban from database.");
            return;
        }
        
        // Remove ban from channel
        sendText("%s%s M %s -b %s", numeric, getNumericSuffix(), channel, banMask);
        
        sendNotice(senderNumeric, "Ban removed from " + channel + " (" + banMask + ")");
        getLogger().log(Level.INFO, "ChanServ: {0} removed ban ({1}) from {2}", 
                       new Object[]{senderNick, banMask, channel});
    }
    
    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + " second(s)";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + " minute(s)";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            return hours + " hour(s)";
        } else if (seconds < 604800) {
            long days = seconds / 86400;
            return days + " day(s)";
        } else {
            long weeks = seconds / 604800;
            return weeks + " week(s)";
        }
    }
}

