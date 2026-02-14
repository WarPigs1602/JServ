/*
 * AuthServ - Account Management Service
 * Handles user account registration, authentication, and management
 */
package net.midiandmore.jserv;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AuthServ Module - Manages user accounts and authentication
 * 
 * Features:
 * - REGISTER: Register new account
 * - IDENTIFY: Authenticate with username/password
 * - PASSWD: Change password
 * - INFO: Display account information
 * - DELETE: Delete account
 * 
 * @author Andreas Pschorn
 */
public final class AuthServ implements Software, Module {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PW_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz0123456789!@#$%^&*";
    private static final int PASSWORD_MAX_LENGTH = 11; // DB schema: chanserv.users.password varchar(11)
    private static final long CLEANUP_INTERVAL = 86400000; // 24 hours in milliseconds

    private static final Logger LOG = Logger.getLogger(AuthServ.class.getName());

    private boolean enabled = false;
    private JServ mi;
    private SocketThread st;
    private PrintWriter pw;
    private BufferedReader br;
    private String numeric;
    private String numericSuffix;
    private EmailService emailService;
    private java.util.Timer cleanupTimer;

    public AuthServ(JServ jserv, SocketThread socketThread, PrintWriter pw, BufferedReader br) {
        initialize(jserv, socketThread, pw, br);
    }

    @Override
    public void initialize(JServ jserv, SocketThread socketThread, PrintWriter pw, BufferedReader br) {
        this.mi = jserv;
        this.st = socketThread;
        this.pw = pw;
        this.br = br;
        this.enabled = false;
        this.emailService = new EmailService(jserv.getConfig().getConfigFile());
        LOG.log(Level.INFO, "AuthServ module initialized");
    }

    @Override
    public void handshake(String nick, String servername, String description, String numeric, String identd) {
        if (!enabled) {
            LOG.log(Level.WARNING, "AuthServ handshake called but module is disabled");
            return;
        }

        this.numeric = numeric;
        String resolvedNick = (nick != null && !nick.isEmpty()) ? nick : "AuthServ";
        String resolvedServername = (servername != null && !servername.isEmpty()) ? servername : "auth.services";
        String resolvedDescription = (description != null && !description.isEmpty()) ? description
                : "Account Management Service";
        String resolvedIdentd = (identd != null && !identd.isEmpty()) ? identd : resolvedNick.toLowerCase();

        LOG.log(Level.INFO, "Registering AuthServ nick: {0}", resolvedNick);
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

        enabled = true;
        
        // Start automatic cleanup timer
        startCleanupTimer();
    }

    @Override
    public void parseLine(String text) {
        if (!enabled) {
            return;
        }

        try {
            // Capture message part (after ':') as a single token
            String[] elem = text.split(" ", 4);
            if (elem.length < 2) {
                return;
            }

            // Handle private messages to AuthServ
            if (elem[1].equals("P")) {
                handlePrivateMessage(text, elem);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error parsing AuthServ line: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handlePrivateMessage(String line, String[] elem) {
        // elem[0] = sender numeric
        // elem[1] = "P"
        // elem[2] = target numeric (AuthServ)
        // elem[3] = ":<message>"
        if (elem.length < 4) {
            return;
        }

        String senderNumeric = elem[0];
        String targetNumeric = elem[2];
        String myNumeric = this.numeric + this.getNumericSuffix();
        if (!targetNumeric.equals(myNumeric)) {
            return; // Not for AuthServ
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 3; i < elem.length; i++) {
            sb.append(elem[i]).append(" ");
        }
        String message = sb.toString().trim();
        if (message.startsWith(":")) {
            message = message.substring(1);
        }
        String[] parts = message.split(" ");
        if (parts.length == 0) {
            return;
        }

        String command = parts[0].toUpperCase();

        // Get sender info
        Users sender = st.getUsers().get(senderNumeric);
        if (sender == null) {
            return;
        }
        String senderAccount = sender.getAccount();

        switch (command) {
            case "REGISTER":
                handleRegister(senderNumeric, senderAccount, parts);
                break;
            case "IDENTIFY":
                handleIdentify(senderNumeric, senderAccount, parts);
                break;
            case "PASSWD":
                handlePasswd(senderNumeric, senderAccount, parts);
                break;
            case "RESETPASSWORD":
                handleResetPassword(senderNumeric, senderAccount, parts);
                break;
            case "REQUESTPASSWORD":
                handleRequestPassword(senderNumeric, senderAccount, parts);
                break;
            case "INFO":
                handleInfo(senderNumeric, senderAccount, parts);
                break;
            case "DELETE":
                handleDelete(senderNumeric, senderAccount, parts);
                break;
            case "STATUS":
                handleStatus(senderNumeric, senderAccount);
                break;
            case "HELP":
                handleHelp(senderNumeric, parts);
                break;
            case "SHOWCOMMANDS":
                handleShowCommands(senderNumeric);
                break;
            case "VERSION":
                handleVersion(senderNumeric);
                break;
            case "USERFLAGS":
                handleUserFlags(senderNumeric, senderAccount, parts);
                break;
            default:
                sendNotice(senderNumeric, "Unknown command: " + command + ". Use HELP for a list of commands.");
                break;
        }
    }

    private String generateRandomPassword(int length) {
        int safeLength = Math.min(length, PASSWORD_MAX_LENGTH);
        StringBuilder sb = new StringBuilder(safeLength);
        for (int i = 0; i < safeLength; i++) {
            int idx = RANDOM.nextInt(PW_CHARS.length());
            sb.append(PW_CHARS.charAt(idx));
        }
        return sb.toString();
    }

    private boolean isValidEmail(String email) {
        if (email == null) {
            return false;
        }
        return email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    private void handleRegister(String senderNumeric, String senderAccount, String[] parts) {
        if (senderAccount != null && !senderAccount.isEmpty()) {
            sendNotice(senderNumeric, "You are already identified. Logout before registering a new account.");
            return;
        }

        Users sender = st.getUsers().get(senderNumeric);
        if (sender != null && sender.hasAttemptedRegistration()) {
            sendNotice(senderNumeric, "You have already attempted registration in this session.");
            sendNotice(senderNumeric, "Reconnect to register another account.");
            return;
        }

        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: REGISTER <email> <email-confirm>");
            return;
        }

        String email = parts[1].trim();
        String emailConfirm = parts[2].trim();
        if (!isValidEmail(email) || !isValidEmail(emailConfirm)) {
            sendNotice(senderNumeric, "Invalid email. Provide a valid email address.");
            return;
        }

        if (!email.equalsIgnoreCase(emailConfirm)) {
            sendNotice(senderNumeric, "Email addresses do not match.");
            return;
        }

        // Check if email is already registered
        if (mi.getDb().isEmailRegistered(email)) {
            sendNotice(senderNumeric, "This email address is already registered.");
            sendNotice(senderNumeric, "Each email address can only be used once.");
            return;
        }

        if (sender == null || sender.getNick() == null || sender.getNick().isBlank()) {
            sendNotice(senderNumeric, "Cannot determine your current nick.");
            return;
        }

        String username = sender.getNick();

        // Check if account already exists
        String accountId = mi.getDb().getData("id", username);
        if (accountId != null) {
            sendNotice(senderNumeric, "Account " + username + " is already registered.");
            return;
        }

        // Validate username (alphanumeric, 2-16 chars, case-insensitive)
        if (!username.matches("^[a-zA-Z0-9]{2,16}$")) {
            sendNotice(senderNumeric, "Your nick is not a valid account name (2-16 alphanumeric).");
            return;
        }

        String password = generateRandomPassword(PASSWORD_MAX_LENGTH);

        // Register account
        boolean success = mi.getDb().registerUser(username, password, email);
        if (success) {
            // Mark registration attempt only on successful registration
            sender = st.getUsers().get(senderNumeric);
            if (sender != null) {
                sender.setRegistrationAttempted(true);
            }

            sendNotice(senderNumeric, "Account " + username + " registered successfully.");
            sendNotice(senderNumeric, "A temporary password has been sent to your email address.");
            sendNotice(senderNumeric, "You cannot register additional accounts in this session.");
            LOG.log(Level.INFO, "AuthServ: {0} registered account {1} with email {2}",
                    new Object[] { senderNumeric, username, email });
            // Get user ID and send registration email with password
            String userIdStr = mi.getDb().getData("id", username);
            int userId = userIdStr != null ? Integer.parseInt(userIdStr) : 0;
            emailService.sendRegistration(userId, username, email, password, mi.getDb());

        } else {
            sendNotice(senderNumeric, "Failed to register account " + username);
        }
    }

    private void handleIdentify(String senderNumeric, String senderAccount, String[] parts) {
        // IDENTIFY can only be used via private message (not in channels)
        // This is a security measure to prevent password interception
        if (senderAccount != null && !senderAccount.isEmpty()) {
            sendNotice(senderNumeric, "You are already identified as " + senderAccount + ".");
            return;
        }
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: IDENTIFY <username> <password>");
            return;
        }

        String username = parts[1];
        String password = parts[2];

        // Check if account exists and password is correct
        if (mi.getDb().authenticateUser(username, password)) {
            // Mark user as authenticated
            Users user = st.getUsers().get(senderNumeric);
            if (user != null) {
                user.setAccount(username);
                // Propagate authentication to the network (AC command)
                long now = System.currentTimeMillis() / 1000;
                String idStr = mi.getDb().getData("id", username);
                long accountId = 0;
                try {
                    if (idStr != null && !idStr.isBlank()) {
                        accountId = Long.parseLong(idStr);
                    }
                } catch (NumberFormatException ignored) {
                    // keep default 0 if parsing fails
                }
                sendText("%s AC %s %s %d %d", numeric + getNumericSuffix(), senderNumeric, username, now, accountId);

                // Update lastauth timestamp
                mi.getDb().updateData("lastauth", username, now);
                LOG.log(Level.FINE, "AuthServ: Updated lastauth for user {0}", username);

                sendNotice(senderNumeric, "You are now identified as " + username);
                LOG.log(Level.INFO, "AuthServ: {0} identified as {1}",
                        new Object[] { senderNumeric, username });

                // Apply channel modes based on userflags after identification
                applyChannelModesAfterIdentify(senderNumeric, user, username);
            }
        } else {
            sendNotice(senderNumeric, "Authentication failed. Invalid username or password.");
        }
    }

    /**
     * Apply channel modes automatically after user identifies.
     * Sets OP/VOICE modes based on user's flags in each channel they're in.
     */
    private void applyChannelModesAfterIdentify(String userNumeric, Users user, String username) {
        // Delegate to ChanServ to apply bans and rights post-auth
        if (st != null && st.getModuleManager() != null) {
            Module module = st.getModuleManager().getModule("ChanServ");
            if (module instanceof ChanServ cs && module.isEnabled()) {
                cs.applyFlagsAfterAuth(userNumeric, username);
            }
        }
    }

    private void handlePasswd(String senderNumeric, String senderAccount, String[] parts) {
        // PASSWD can only be used via private message (not in channels)
        // This is a security measure to prevent password interception
        if (senderAccount == null || senderAccount.isEmpty()) {
            sendNotice(senderNumeric, "You must be identified to use this command.");
            return;
        }

        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: PASSWD <oldpassword> <newpassword>");
            return;
        }

        String oldPassword = parts[1];
        String newPassword = parts[2];

        // Verify old password
        if (!mi.getDb().authenticateUser(senderAccount, oldPassword)) {
            sendNotice(senderNumeric, "Old password is incorrect.");
            return;
        }

        // Validate new password
        if (newPassword.length() < 6) {
            sendNotice(senderNumeric, "New password must be at least 6 characters long.");
            return;
        }
        if (newPassword.length() > PASSWORD_MAX_LENGTH) {
            sendNotice(senderNumeric,
                    "New password must be at most " + PASSWORD_MAX_LENGTH + " characters (DB limit).");
            return;
        }

        // Update password
        boolean success = mi.getDb().updateUserPassword(senderAccount, newPassword);
        if (success) {

            sendNotice(senderNumeric, "Your password change is pending.");
            sendNotice(senderNumeric, "You will receive an email with instructions to cancel the change.");
            sendNotice(senderNumeric, "The new password will be active once you use it to authenticate.");
            LOG.log(Level.INFO, "AuthServ: {0} initiated password change",
                    senderAccount);
            // Get reset token and send notification email
            String email = mi.getDb().getUserField("email", senderAccount);
            if (email != null && !email.isEmpty()) {
                String resetToken = mi.getDb().getResetToken(senderAccount);
                if (resetToken != null) {
                    emailService.sendPasswordChange(senderAccount, email, resetToken);
                }
            }
        } else {
            sendNotice(senderNumeric, "Failed to change password.");
        }
    }

    private void handleResetPassword(String senderNumeric, String senderAccount, String[] parts) {
        if (senderAccount == null || senderAccount.isEmpty()) {
            sendNotice(senderNumeric, "You must be identified to use this command.");
            return;
        }

        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: RESETPASSWORD <email>");
            sendNotice(senderNumeric, "Cancel a pending password change using your registered email address.");
            return;
        }

        String providedEmail = parts[1].toLowerCase();

        // Get registered email address
        String registeredEmail = mi.getDb().getUserField("email", senderAccount);
        if (registeredEmail == null || registeredEmail.isEmpty()) {
            sendNotice(senderNumeric, "No email address registered for your account.");
            return;
        }

        // Verify provided email matches registered email
        if (!providedEmail.equalsIgnoreCase(registeredEmail)) {
            sendNotice(senderNumeric, "The provided email address does not match your registered email.");
            return;
        }

        // Clear pending password change (new_pwd, reset_token, generated_pwd)
        boolean success = mi.getDb().clearPendingPasswordChange(senderAccount);

        if (success) {
            sendNotice(senderNumeric, "Your pending password change has been cancelled.");
            sendNotice(senderNumeric, "Your old password is still active.");
            LOG.log(Level.INFO, "AuthServ: {0} cancelled password change via email verification", senderAccount);
        } else {
            sendNotice(senderNumeric, "No pending password change found or failed to cancel.");
        }
    }

    private void handleInfo(String senderNumeric, String senderAccount, String[] parts) {
        if (senderAccount == null || senderAccount.isEmpty()) {
            sendNotice(senderNumeric, "You must be identified to use this command.");
            return;
        }

        String userIdStr = mi.getDb().getData("id", senderAccount);
        if (userIdStr == null) {
            sendNotice(senderNumeric, "Account information not found.");
            return;
        }

        long userId = Long.parseLong(userIdStr);
        String created = mi.getDb().getData("created", senderAccount);
        String lastlogin = mi.getDb().getData("lastauth", senderAccount);

        sendNotice(senderNumeric, "Account information for " + senderAccount + ":");
        sendNotice(senderNumeric, "Username: " + senderAccount);

        if (created != null && !created.isEmpty()) {
            String createdDate = formatTimestamp(created);
            sendNotice(senderNumeric, "Created: " + createdDate);
        }

        if (lastlogin != null && !lastlogin.isEmpty() && !lastlogin.equals("0")) {
            String lastLoginDate = formatTimestamp(lastlogin);
            sendNotice(senderNumeric, "Last Login: " + lastLoginDate);
        } else {
            sendNotice(senderNumeric, "Last Login: Never");
        }
    }

    private void handleStatus(String senderNumeric, String senderAccount) {
        if (senderAccount == null || senderAccount.isEmpty()) {
            sendNotice(senderNumeric, "Status: not identified.");
            return;
        }
        String created = mi.getDb().getData("created", senderAccount);
        String lastauth = mi.getDb().getData("lastauth", senderAccount);
        sendNotice(senderNumeric, "Status: identified as " + senderAccount + ".");
        if (created != null && !created.isEmpty()) {
            sendNotice(senderNumeric, "Created: " + formatTimestamp(created));
        }
        if (lastauth != null && !lastauth.isEmpty() && !"0".equals(lastauth)) {
            sendNotice(senderNumeric, "Last Auth: " + formatTimestamp(lastauth));
        }
    }

    private void handleDelete(String senderNumeric, String senderAccount, String[] parts) {
        if (senderAccount == null || senderAccount.isEmpty()) {
            sendNotice(senderNumeric, "You must be identified to use this command.");
            return;
        }

        if (parts.length < 2) {
            sendNotice(senderNumeric, "Syntax: DELETE <password>");
            return;
        }

        String password = parts[1];

        // Verify password
        if (!mi.getDb().authenticateUser(senderAccount, password)) {
            sendNotice(senderNumeric, "Password is incorrect.");
            return;
        }

        // Delete account
        boolean success = mi.getDb().deleteUser(senderAccount);
        if (success) {
            sendNotice(senderNumeric, "Your account " + senderAccount + " has been deleted.");
            LOG.log(Level.WARNING, "AuthServ: {0} deleted account",
                    senderAccount);

            // Clear user's account info
            Users user = st.getUsers().get(senderNumeric);
            if (user != null) {
                user.setAccount("");
            }
        } else {
            sendNotice(senderNumeric, "Failed to delete account.");
        }
    }

    private void handleShowCommands(String senderNumeric) {
        sendNotice(senderNumeric, Messages.get("QM_COMMANDLIST"));
        sendNotice(senderNumeric, "   DELETE          Delete your account");
        sendNotice(senderNumeric, "   HELP            Show help for a command");
        sendNotice(senderNumeric, "   IDENTIFY        Authenticate with your account");
        sendNotice(senderNumeric, "   INFO            Display account information");
        sendNotice(senderNumeric, "   PASSWD          Change your password");
        sendNotice(senderNumeric, "   REGISTER        Register a new account");
        sendNotice(senderNumeric, "   REQUESTPASSWORD Request a new password via email");
        sendNotice(senderNumeric, "   RESETPASSWORD   Cancel a password change");
        sendNotice(senderNumeric, "   SHOWCOMMANDS    This message");
        sendNotice(senderNumeric, "   STATUS          Show authentication status");
        sendNotice(senderNumeric, "   USERFLAGS       Display user flags");
        sendNotice(senderNumeric, "   VERSION         Shows version information");
        sendNotice(senderNumeric, Messages.get("QM_ENDOFLIST"));
    }

    private void handleHelp(String senderNumeric, String[] parts) {
        if (parts.length == 1) {
            sendNotice(senderNumeric, "Use SHOWCOMMANDS to list all available commands.");
            sendNotice(senderNumeric, "Use HELP <command> for detailed help on a specific command.");
        } else {
            String command = parts[1].toUpperCase();
            switch (command) {
                case "RESETPASSWORD":
                    sendNotice(senderNumeric, "RESETPASSWORD <email>");
                    sendNotice(senderNumeric, "Cancels a pending password change using your registered email address.");
                    sendNotice(senderNumeric, "You must provide your registered email for verification.");
                    sendNotice(senderNumeric, "Your old password will remain active.");
                    break;
                case "DELETE":
                    sendNotice(senderNumeric, "DELETE <password>");
                    sendNotice(senderNumeric, "Deletes your account permanently.");
                    break;
                case "IDENTIFY":
                    sendNotice(senderNumeric, "IDENTIFY <username> <password>");
                    sendNotice(senderNumeric, "Authenticates you with your account.");
                    break;
                case "INFO":
                    sendNotice(senderNumeric, "INFO");
                    sendNotice(senderNumeric, "Displays information about your account.");
                    break;
                case "PASSWD":
                    sendNotice(senderNumeric, "PASSWD <oldpassword> <newpassword>");
                    sendNotice(senderNumeric, "Changes your account password.");
                    break;
                case "REGISTER":
                    sendNotice(senderNumeric, "REGISTER <email> <email-confirm>");
                    sendNotice(senderNumeric, "Registers a new account using your current nick as username.");
                    sendNotice(senderNumeric, "You will receive a generated temporary password.");
                    break;
                case "REQUESTPASSWORD":
                    sendNotice(senderNumeric, "REQUESTPASSWORD <username> <email>");
                    sendNotice(senderNumeric, "Requests a new password for your account.");
                    sendNotice(senderNumeric, "You must provide your registered email address for verification.");
                    sendNotice(senderNumeric,
                            "A new temporary password will be sent to your registered email address.");
                    break;
                case "STATUS":
                    sendNotice(senderNumeric, "STATUS");
                    sendNotice(senderNumeric, "Shows whether you are currently authenticated.");
                    break;
                case "USERFLAGS":
                    sendNotice(senderNumeric, "USERFLAGS [username] [+flag|-flag]");
                    sendNotice(senderNumeric, "Displays or modifies user flags.");
                    sendNotice(senderNumeric, "Without arguments: Shows your own flags");
                    sendNotice(senderNumeric, "With username only: Shows flags of that user");
                    sendNotice(senderNumeric, "With +flag/-flag: Adds or removes a flag");
                    sendNotice(senderNumeric, " ");
                    sendNotice(senderNumeric, "Permission levels:");
                    sendNotice(senderNumeric, "  Normal users: +n/-n (NOTICE only)");
                    sendNotice(senderNumeric, "  Helpers (+h): +n, +i, +c");
                    sendNotice(senderNumeric, "  Staff (+q): +n, +i, +c, +h, +p, +T");
                    sendNotice(senderNumeric, "  Opers (+o): Most flags except privileged");
                    sendNotice(senderNumeric, "  Admin/Dev (+a/+d): All flags");
                    sendNotice(senderNumeric, " ");
                    sendNotice(senderNumeric, "Examples: USERFLAGS, USERFLAGS username, USERFLAGS username +n");
                    break;
                default:
                    sendNotice(senderNumeric, "Unknown command: " + command);
                    break;
            }
        }
    }

    private void handleRequestPassword(String senderNumeric, String senderAccount, String[] parts) {
        if (parts.length < 3) {
            sendNotice(senderNumeric, "Syntax: REQUESTPASSWORD <username> <email>");
            sendNotice(senderNumeric, "You must provide your registered email address for verification.");
            return;
        }

        String username = parts[1];
        String providedEmail = parts[2].toLowerCase();

        // Check if account exists
        if (!mi.getDb().isRegistered(username)) {
            sendNotice(senderNumeric, "Account not found: " + username);
            return;
        }

        // Get user ID
        int userId = mi.getDb().getUserId(username);
        if (userId == 0) {
            sendNotice(senderNumeric, "Error retrieving account information.");
            return;
        }

        // Get registered email address
        String registeredEmail = mi.getDb().getUserField("email", username);
        if (registeredEmail == null || registeredEmail.isEmpty()) {
            sendNotice(senderNumeric, "No email address registered for this account.");
            sendNotice(senderNumeric, "Please contact network staff for assistance.");
            return;
        }

        // Verify provided email matches registered email
        if (!providedEmail.equalsIgnoreCase(registeredEmail)) {
            sendNotice(senderNumeric, "Email address does not match the registered email for this account.");
            sendNotice(senderNumeric, "Please contact network staff if you no longer have access to your email.");
            LOG.warning(String.format("Failed password reset attempt for %s: email mismatch from %s", username,
                    senderNumeric));
            return;
        }

        // Generate new temporary password
        String newPassword = generateRandomPassword(10);

        // Update password in database using secure pwd field
        boolean success = mi.getDb().updateUserPassword(username, newPassword);
        if (!success) {
            sendNotice(senderNumeric, "Failed to update password. Please try again later.");
            LOG.warning("Failed to update password for user: " + username);
            return;
        }


        sendNotice(senderNumeric, "A new temporary password has been generated.");
        sendNotice(senderNumeric, "An email has been sent to: " + maskEmail(registeredEmail));
        sendNotice(senderNumeric, "Please check your inbox and use the new password to authenticate.");
        sendNotice(senderNumeric, "The old password will remain active until you use the new one.");

        // Get reset token and send email
        String resetToken = mi.getDb().getResetToken(username);
        if (resetToken != null) {
            emailService.sendPasswordChange(username, registeredEmail, resetToken);
        }

        // Also send new password
        emailService.sendPasswordRequest(username, registeredEmail, newPassword);
        LOG.info(String.format("Password reset requested for user %s by %s", username, senderNumeric));
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@");
        String local = parts[0];
        String domain = parts[1];
        if (local.length() <= 2) {
            return "*@" + domain;
        }
        return local.substring(0, 1) + "***" + local.substring(local.length() - 1) + "@" + domain;
    }

    private void handleUserFlags(String senderNumeric, String senderAccount, String[] parts) {
        // Check if user is authenticated
        if (senderAccount == null || senderAccount.isEmpty()) {
            sendNotice(senderNumeric, "You must be identified to use this command.");
            return;
        }

        // USERFLAGS [username] [+flag|-flag] - if no username, show own flags
        String targetUsername = (parts.length >= 2) ? parts[1] : senderAccount;

        // Get user flags from database
        if (!mi.getDb().isRegistered(targetUsername)) {
            sendNotice(senderNumeric, "Account not found: " + targetUsername);
            return;
        }

        int flags = mi.getDb().getFlags(targetUsername);

        // Check if user wants to modify flags
        if (parts.length >= 3) {
            String flagChange = parts[2];
            handleFlagModification(senderNumeric, senderAccount, targetUsername, flags, flagChange);
            return;
        }

        // Show flags (no modification)
        displayUserFlags(senderNumeric, targetUsername, flags);
    }

    private void handleFlagModification(String senderNumeric, String senderAccount,
            String targetUsername, int currentFlags, String flagChange) {
        // Get sender's flags
        int senderFlags = mi.getDb().getFlags(senderAccount);

        boolean isStaff = Userflags.hasFlag(senderFlags, Userflags.Flag.STAFF);
        boolean isOper = Userflags.hasFlag(senderFlags, Userflags.Flag.OPER);
        boolean isAdmin = Userflags.hasFlag(senderFlags, Userflags.Flag.ADMIN);
        boolean isDev = Userflags.hasFlag(senderFlags, Userflags.Flag.DEV);
        boolean isHelper = Userflags.hasFlag(senderFlags, Userflags.Flag.HELPER);

        // Parse flag change (+n or -n)
        if (flagChange.length() < 2) {
            sendNotice(senderNumeric, "Invalid flag format. Use +flag or -flag (e.g., +n or -n)");
            return;
        }

        boolean addFlag = flagChange.charAt(0) == '+';
        boolean removeFlag = flagChange.charAt(0) == '-';

        if (!addFlag && !removeFlag) {
            sendNotice(senderNumeric, "Flag must start with + or - (e.g., +n or -n)");
            return;
        }

        char flagChar = flagChange.charAt(1);
        Userflags.Flag flag = Userflags.fromChar(flagChar);

        if (flag == null) {
            sendNotice(senderNumeric, "Unknown flag: " + flagChar);
            return;
        }

        // Check permissions for this flag
        if (!canModifyFlag(flag, isHelper, isStaff, isOper, isAdmin, isDev)) {
            sendNotice(senderNumeric, "You don't have permission to modify flag +" + flagChar);
            sendNotice(senderNumeric, "Normal users can only modify: +n/-n (NOTICE)");
            if (isHelper) {
                sendNotice(senderNumeric, "Helpers can modify: +n, +i, +c");
            }
            if (isStaff) {
                sendNotice(senderNumeric, "Staff can modify: +n, +i, +c, +h, +p, +T");
            }
            if (isOper) {
                sendNotice(senderNumeric, "Opers can modify: all except +o, +d, +a, +q, +g, +z, +I, +D");
            }
            if (isAdmin || isDev) {
                sendNotice(senderNumeric, "Admins/Devs can modify: all flags");
            }
            return;
        }

        // Prevent users from modifying their own privileged flags
        if (targetUsername.equalsIgnoreCase(senderAccount)) {
            if (flag == Userflags.Flag.OPER || flag == Userflags.Flag.ADMIN ||
                    flag == Userflags.Flag.DEV || flag == Userflags.Flag.STAFF) {
                sendNotice(senderNumeric, "You cannot modify your own privileged flags (+o, +a, +d, +q)");
                return;
            }
        }

        // Modify the flag
        int newFlags;
        if (addFlag) {
            newFlags = Userflags.setFlag(currentFlags, flag);
        } else {
            newFlags = Userflags.clearFlag(currentFlags, flag);
        }

        // Update in database
        mi.getDb().updateData("flags", targetUsername, newFlags);

        // Send confirmation
        String action = addFlag ? "added" : "removed";
        String flagName = flag.name();
        sendNotice(senderNumeric,
                "Flag +" + flagChar + " (" + flagName + ") " + action + " for user " + targetUsername);

        LOG.log(Level.INFO, "AuthServ: {0} modified flags for {1}: {2}",
                new Object[] { senderAccount, targetUsername, flagChange });

        // Show updated flags
        displayUserFlags(senderNumeric, targetUsername, newFlags);
    }

    private boolean canModifyFlag(Userflags.Flag flag, boolean isHelper, boolean isStaff,
            boolean isOper, boolean isAdmin, boolean isDev) {
        // Admin and Dev can modify all flags
        if (isAdmin || isDev) {
            return true;
        }

        // Oper can modify most flags except privileged ones
        if (isOper) {
            return flag != Userflags.Flag.OPER &&
                    flag != Userflags.Flag.DEV &&
                    flag != Userflags.Flag.ADMIN &&
                    flag != Userflags.Flag.STAFF &&
                    flag != Userflags.Flag.GLINE &&
                    flag != Userflags.Flag.SUSPENDED &&
                    flag != Userflags.Flag.INACTIVE &&
                    flag != Userflags.Flag.CLEANUPEXEMPT;
        }

        // Staff can modify some flags
        if (isStaff) {
            return flag == Userflags.Flag.NOTICE ||
                    flag == Userflags.Flag.INFO ||
                    flag == Userflags.Flag.ACHIEVEMENTS ||
                    flag == Userflags.Flag.HELPER ||
                    flag == Userflags.Flag.PROTECT ||
                    flag == Userflags.Flag.TRUST;
        }

        // Helper can modify limited flags
        if (isHelper) {
            return flag == Userflags.Flag.NOTICE ||
                    flag == Userflags.Flag.INFO ||
                    flag == Userflags.Flag.ACHIEVEMENTS;
        }

        // Normal users can only modify NOTICE flag
        return flag == Userflags.Flag.NOTICE;
    }

    private void displayUserFlags(String senderNumeric, String targetUsername, int flags) {
        // Build flag string
        StringBuilder flagStr = new StringBuilder();
        boolean hasFlags = false;
        for (Userflags.Flag flag : Userflags.Flag.values()) {
            if (Userflags.hasFlag(flags, flag)) {
                if (!hasFlags) {
                    flagStr.append('+');
                    hasFlags = true;
                }
                flagStr.append(flag.code);
            }
        }

        if (!hasFlags) {
            flagStr.append("(none)");
        }

        // Send response
        sendNotice(senderNumeric, "User flags for " + targetUsername + ": " + flagStr.toString());
        sendNotice(senderNumeric, "Flag descriptions:");

        // Show active flags with descriptions
        if (Userflags.hasFlag(flags, Userflags.Flag.INACTIVE)) {
            sendNotice(senderNumeric, "  +I - INACTIVE: Account is marked as inactive");
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.GLINE)) {
            sendNotice(senderNumeric, "  +g - GLINE: User is G-Lined");
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.NOTICE)) {
            sendNotice(senderNumeric, "  +n - NOTICE: Notices enabled");
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.STAFF)) {
            sendNotice(senderNumeric, "  +q - STAFF: Network staff member");
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.SUSPENDED)) {
            sendNotice(senderNumeric, "  +z - SUSPENDED: Account is suspended");
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.OPER)) {
            sendNotice(senderNumeric, "  +o - OPER: IRC Operator");
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.DEV)) {
            sendNotice(senderNumeric, "  +d - DEV: Developer");
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.PROTECT)) {
            sendNotice(senderNumeric, "  +p - PROTECT: Account is protected");
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.HELPER)) {
            sendNotice(senderNumeric, "  +h - HELPER: Network helper");
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.ADMIN)) {
            sendNotice(senderNumeric, "  +a - ADMIN: Administrator");
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.INFO)) {
            sendNotice(senderNumeric, "  +i - INFO: Show info");
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.DELAYEDGLINE)) {
            sendNotice(senderNumeric, "  +G - DELAYEDGLINE: Delayed G-Line");
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.NOAUTHLIMIT)) {
            sendNotice(senderNumeric, "  +L - NOAUTHLIMIT: No auth limit");
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.ACHIEVEMENTS)) {
            sendNotice(senderNumeric, "  +c - ACHIEVEMENTS: Achievements enabled");
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.CLEANUPEXEMPT)) {
            sendNotice(senderNumeric, "  +D - CLEANUPEXEMPT: Exempt from cleanup");
        }
        if (Userflags.hasFlag(flags, Userflags.Flag.TRUST)) {
            sendNotice(senderNumeric, "  +T - TRUST: Trusted user");
        }

        if (flagStr.toString().equals("(none)")) {
            sendNotice(senderNumeric, "  (No flags set)");
        }

        LOG.log(Level.INFO, "AuthServ: {0} viewed flags for {1}",
                new Object[] { mi.getDb().getData("username", senderNumeric), targetUsername });
    }

    private void handleVersion(String senderNumeric) {
        Software.BuildInfo buildInfo = Software.getBuildInfo();
        sendNotice(senderNumeric,
                String.format("AuthServ v%s by Andreas Pschorn (WarPigs)", buildInfo.getFullVersion()));
        sendNotice(senderNumeric, "Account Management Service for MidiAndMore IRC Network");
    }

    protected void sendNotice(String targetNumeric, String text) {
        // Use SocketThread helper to format notice correctly: <origin> O <target> :text
        st.sendNotice(this.numeric, this.getNumericSuffix(), "O", targetNumeric, text);
    }

    protected void sendText(String format, Object... args) {
        // Do not prepend origin here; callers include origin (numeric) in format args
        String formatted = String.format(format, args);
        pw.println(formatted);
        pw.flush();
    }

    private String formatTimestamp(String timestamp) {
        try {
            long ts = Long.parseLong(timestamp);
            java.time.Instant instant = java.time.Instant.ofEpochSecond(ts);
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(java.time.ZoneId.systemDefault());
            return formatter.format(instant);
        } catch (NumberFormatException e) {
            return timestamp;
        }
    }

    private long time() {
        return System.currentTimeMillis() / 1000;
    }

    @Override
    public String getModuleName() {
        return "AuthServ";
    }

    @Override
    public String getNumeric() {
        return numeric;
    }

    @Override
    public String getNumericSuffix() {
        return numericSuffix != null ? numericSuffix : "AB";
    }

    public void setNumericSuffix(String suffix) {
        this.numericSuffix = suffix;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void enable() {
        this.enabled = true;
        LOG.log(Level.INFO, "AuthServ module enabled");
    }

    @Override
    public void disable() {
        this.enabled = false;
        LOG.log(Level.INFO, "AuthServ module disabled");
    }

    @Override
    public void shutdown() {
        if (cleanupTimer != null) {
            cleanupTimer.cancel();
            cleanupTimer = null;
        }
        if (enabled) {
            disable();
        }
        LOG.log(Level.INFO, "AuthServ module shutdown");
    }

    @Override
    public void registerBurstChannels(java.util.HashMap<String, Burst> bursts, String serverNumeric) {
        if (!enabled) {
            return;
        }
        String channel = "#twilightzone";
        String key = channel.toLowerCase();
        if (!bursts.containsKey(key)) {
            bursts.put(key, new Burst(channel));
        }
        bursts.get(key).getUsers().add(serverNumeric + getNumericSuffix());
        LOG.log(Level.INFO, "AuthServ registered burst channel: {0}", channel);
    }
    
    /**
     * Start automatic cleanup timer (runs every 24 hours)
     */
    private void startCleanupTimer() {
        if (cleanupTimer != null) {
            cleanupTimer.cancel();
        }
        
        // Perform initial cleanup immediately on startup
        new Thread(() -> {
            try {
                Thread.sleep(5000); // Wait 5 seconds for initialization
                performCleanup();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "AuthServ-InitialCleanup").start();
        
        cleanupTimer = new java.util.Timer("AuthServ-Cleanup", true);
        cleanupTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                performCleanup();
            }
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL); // Run every 24 hours
        
        LOG.log(Level.INFO, "AuthServ cleanup timer started (interval: {0}ms)", CLEANUP_INTERVAL);
    }
    
    /**
     * Perform automatic account cleanup
     * - Delete inactive non-privileged accounts that haven't authenticated in X days
     * - Excludes: OPER, STAFF, ADMIN, DEV, CLEANUPEXEMPT, and currently logged in users
     */
    private void performCleanup() {
        long startTime = System.currentTimeMillis();
        int deletedCount = 0;
        
        LOG.log(Level.INFO, "Starting AuthServ account cleanup (excluding privileged accounts)");
        
        try {
            // Get cleanup days configuration (default: 90)
            int cleanupDays = 90;
            try {
                String cleanupDaysStr = mi.getConfig().getConfigFile().getProperty("account_cleanup_days", "90");
                cleanupDays = Integer.parseInt(cleanupDaysStr);
            } catch (NumberFormatException e) {
                LOG.log(Level.WARNING, "Invalid account_cleanup_days value, using default: 90");
            }
            
            // Get currently logged in users
            java.util.Set<String> loggedInUsers = new java.util.HashSet<>();
            for (Users user : st.getUsers().values()) {
                if (user.getAccount() != null && !user.getAccount().isEmpty()) {
                    loggedInUsers.add(user.getAccount().toLowerCase());
                }
            }
            
            // Delete inactive users (excluding privileged accounts, CLEANUPEXEMPT users and currently logged in users)
            deletedCount = mi.getDb().deleteInactiveChanServUsers(cleanupDays, loggedInUsers);
            
            long duration = System.currentTimeMillis() - startTime;
            
            // Log and notify opers
            String message = String.format("AuthServ cleanup completed in %dms: Deleted %d inactive non-privileged account(s) (inactive > %d days)",
                duration, deletedCount, cleanupDays);
            LOG.log(Level.INFO, message);
            
            if (deletedCount > 0) {
                sendOperNotice("[AuthServ Cleanup] " + message);
            }
            
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "AuthServ cleanup failed", e);
            sendOperNotice("[AuthServ Cleanup] FAILED: " + e.getMessage());
        }
    }
    
    /**
     * Send notification to logged-in privileged users (opers/staff/admin/dev) with oper mode
     * @param message Message to send
     */
    private void sendOperNotice(String message) {
        if (numeric == null || numericSuffix == null) {
            LOG.log(Level.WARNING, "Cannot send oper notice: numeric not set");
            return;
        }
        
        if (st == null || st.getUsers() == null || st.getUsers().isEmpty()) {
            LOG.log(Level.WARNING, "Cannot send oper notice: no users available");
            return;
        }
        
        if (mi == null || mi.getDb() == null) {
            LOG.log(Level.WARNING, "Cannot send oper notice: database not available");
            return;
        }
        
        String myNumeric = numeric + numericSuffix;
        int noticesSent = 0;
        
        // Iterate through all connected users
        for (var entry : st.getUsers().entrySet()) {
            String userNumeric = entry.getKey();
            Users user = entry.getValue();
            
            // Skip users without account or oper mode
            if (user == null || user.getAccount() == null || user.getAccount().isEmpty()) {
                continue;
            }
            
            if (!user.isOper()) {
                continue;
            }
            
            // Check if user has privileged flags
            String account = user.getAccount();
            int flags = mi.getDb().getFlags(account);
            
            boolean isPrivileged = Userflags.hasFlag(flags, Userflags.Flag.OPER)
                    || Userflags.hasFlag(flags, Userflags.Flag.STAFF)
                    || Userflags.hasFlag(flags, Userflags.Flag.ADMIN)
                    || Userflags.hasFlag(flags, Userflags.Flag.DEV);
            
            if (isPrivileged) {
                // Send private NOTICE to this user
                sendText("%s O %s :%s", myNumeric, userNumeric, message);
                noticesSent++;
            }
        }
        
        if (noticesSent > 0) {
            LOG.log(Level.INFO, "Oper notice sent to {0} privileged user(s)", noticesSent);
        }
    }
}
