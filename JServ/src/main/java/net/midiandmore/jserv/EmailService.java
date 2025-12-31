package net.midiandmore.jserv;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.digest.HmacUtils;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.codec.digest.HmacAlgorithms.HMAC_SHA_256;

/**
 * Email service for sending templated emails
 */
public class EmailService {
    private static final Logger LOG = Logger.getLogger(EmailService.class.getName());
    private final Map<Integer, EmailTemplate> templates;
    private final String defaultLanguage = "en";
    private final SmtpMailer smtpMailer;
    
    // Configuration parameters
    private final String botName;
    private final String serverName;
    private final String networkName;
    private final String siteUrl;
    private final String securityUrl;
    private final String activationUrl;
    private final int cleanupDays;
    private final String urlKey;
    private final String urlSecret;
    private final String url;
    private final String activationKey;
    private final java.util.Properties config;
    
    public EmailService(java.util.Properties config) {
        this.config = config;
        this.templates = new HashMap<>();
        this.smtpMailer = new SmtpMailer(config);
        
        // Load configuration parameters with defaults
        this.botName = config.getProperty("bot_name", "AuthServ");
        this.serverName = config.getProperty("servername", "irc.network.net");
        this.networkName = config.getProperty("network", "IRC Network");
        this.siteUrl = config.getProperty("site_url", "https://www.example.net");
        this.securityUrl = config.getProperty("security_url", "https://www.example.net/security");
        this.activationUrl = config.getProperty("activation_url", "https://www.example.net/activate");
        this.cleanupDays = Integer.parseInt(config.getProperty("account_cleanup_days", "90"));
        this.urlKey = config.getProperty("urlkey", "");
        this.urlSecret = config.getProperty("urlsecret", "");
        this.url = config.getProperty("url", "");
        this.activationKey = config.getProperty("activationkey", "");
        
        loadTemplates();
        LOG.info(smtpMailer.getConfigSummary());
        LOG.info(String.format("Email config: Bot=%s, Network=%s, Site=%s", botName, networkName, siteUrl));
    }
    
    /**
     * Get configuration wrapper
     */
    private ConfigWrapper getConfig() {
        return new ConfigWrapper();
    }
    
    /**
     * Configuration wrapper class
     */
    private class ConfigWrapper {
        public java.util.Properties getConfigFile() {
            return config;
        }
    }
    
    private void loadTemplates() {
        try (InputStream is = getClass().getResourceAsStream("/email-templates.json");
             JsonReader reader = Json.createReader(is)) {
            JsonArray templatesArray = reader.readArray();
            for (int i = 0; i < templatesArray.size(); i++) {
                JsonObject templateObj = templatesArray.getJsonObject(i);
                int id = Integer.parseInt(templateObj.getString("id"));
                EmailTemplate template = new EmailTemplate(
                    id,
                    templateObj.getJsonObject("subject"),
                    templateObj.getJsonObject("body")
                );
                templates.put(id, template);
            }
            LOG.info("Loaded " + templates.size() + " email templates");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load email templates", e);
        }
    }
    
    /**
     * Queue an email for sending
     * @param templateId Email template ID (1-6)
     * @param toEmail Recipient email address
     * @param variables Variables for template substitution
     * @param language Language code (en, de, es, etc.)
     */
    public void queueEmail(int templateId, String toEmail, Map<String, String> variables, String language) {
        EmailTemplate template = templates.get(templateId);
        if (template == null) {
            LOG.warning("Email template not found: " + templateId);
            return;
        }
        
        String lang = language != null ? language : defaultLanguage;
        String subject = template.getSubject(lang);
        String body = template.getBody(lang);
        
        // Replace variables
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "%(" + entry.getKey() + ")s";
            if (entry.getValue() != null) {
                subject = subject.replace(placeholder, entry.getValue());
                body = body.replace(placeholder, entry.getValue());
            }
        }
        
        // Replace %d placeholders with empty string if not provided
        subject = subject.replaceAll("%\\([^)]+\\)d", "");
        body = body.replaceAll("%\\([^)]+\\)d", "");
        
        // Send email via SMTP
        boolean sent = smtpMailer.sendMail(toEmail, subject, body);
        if (!sent) {
            LOG.warning(String.format("Failed to send email [Template %d] to %s", templateId, toEmail));
        }
        
        // TODO: Implement actual email sending via SMTP
        // For now just log it
    }
    
    /**
     * Send password request email (Template 2)
     */
    public void sendPasswordRequest(String username, String email, String password) {
        Map<String, String> vars = new HashMap<>();
        vars.put("user.username", username);
        vars.put("user.password", password);
        vars.put("user.email", email);
        vars.put("config.bot", botName);
        vars.put("config.server", serverName);
        queueEmail(2, email, vars, defaultLanguage);
    }
    
    /**
     * Send password change notification (Template 3)
     */
    public void sendPasswordChange(String username, String email, String resetToken) {
        Map<String, String> vars = new HashMap<>();
        vars.put("user.username", username);
        vars.put("user.email", email);
        vars.put("config.bot", botName);
        String resetLine = "/msg " + botName + " RESETPASSWORD " + resetToken;
        vars.put("resetline", resetLine);
        long lockUntilTs = System.currentTimeMillis() / 1000 + (24 * 3600); // 24 hours
        String lockUntil = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            .format(new java.util.Date(lockUntilTs * 1000));
        vars.put("lockuntil", lockUntil);
        queueEmail(3, email, vars, defaultLanguage);
    }
    
    /**
     * Send account registration email (Template 1)
     */
    public void sendRegistration(int userId, String username, String email, String password, Database db) {
        Map<String, String> vars = new HashMap<>();
        vars.put("user.username", username);
        vars.put("user.password", password);
        vars.put("user.email", email);
        vars.put("config.bot", botName);
        
        // Generate activation URL if keys are configured
        if (!activationKey.isEmpty() && !urlKey.isEmpty()) {
            // Get generated_pwd from database for URL generation
            String generatedPwd = db.getData("generated_pwd", username);
            if (generatedPwd != null && !generatedPwd.isEmpty()) {
                String activationUrl = generateUrl(username, email, generatedPwd);
                vars.put("url", activationUrl);
            } else {
                LOG.warning("Generated password not found for user: " + username);
                return;
            }
        } else {
            LOG.warning("Activation URL keys not configured, cannot send registration email for user: " + username);
            return;
        }
        
        vars.put("config.siteurl", siteUrl);
        vars.put("config.securityurl", securityUrl);
        vars.put("network.name", networkName);
        vars.put("config.cleanup", String.valueOf(cleanupDays));
        queueEmail(1, email, vars, defaultLanguage);
    }
    
    /**
     * Send account reset notification (Template 4)
     */
    public void sendAccountReset(String username, String email, String password) {
        Map<String, String> vars = new HashMap<>();
        vars.put("user.username", username);
        vars.put("user.email", email);
        vars.put("user.password", password);
        vars.put("config.bot", botName);
        vars.put("config.securityurl", securityUrl);
        queueEmail(4, email, vars, defaultLanguage);
    }
    
    /**
     * Send email change notification (Template 5)
     */
    public void sendEmailChange(String username, String newEmail, String prevEmail) {
        Map<String, String> vars = new HashMap<>();
        vars.put("user.username", username);
        vars.put("user.email", newEmail);
        vars.put("prevemail", prevEmail);
        vars.put("config.bot", botName);
        String resetLine = "/msg " + botName + " REQUESTPASSWORD " + username + " " + newEmail;
        vars.put("resetline", resetLine);
        long lockUntilTs = System.currentTimeMillis() / 1000 + (24 * 3600); // 24 hours
        String lockUntil = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            .format(new java.util.Date(lockUntilTs * 1000));
        vars.put("lockuntil", lockUntil);
        queueEmail(5, newEmail, vars, defaultLanguage);
    }
    
    /**
     * Generate URL for user
     */
    private String generateUrl(String[] obj) {
        logDebug("Generating URL for user: %s", obj[1]);
        var r = Hex.encodeHexString(obj[2].getBytes(UTF_8));
        var uname = obj[1];
        var password = obj[11];
        var key = urlKey;
        LOG.info("urlKey from config: " + key);
        LOG.info("urlSecret from config: " + urlSecret);
        var a = MD5("%s %s".formatted(r, key));
        try {
            a = Hex.encodeHexString(RC4(a, password));
            logDebug("URL encryption successful for user: %s", uname);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException ex) {
            logError("Error generating URL for user: " + uname, ex);
        }
        var b = new HmacUtils(HMAC_SHA_256, MD5(MD5("%s %s %s".formatted(urlSecret, uname, a)))).hmacHex(r);
        var url = "%s?m=%s".formatted(getConfig().getConfigFile().getProperty("url"), b);
        logDebug("Generated URL: %s", url);
        return url;
    }

    /**
     * Generate URL for user (convenience overload for backward compatibility)
     */
    private String generateUrl(String username, String email, String password) {
        String[] obj = new String[12];
        obj[1] = username;
        obj[2] = email;
        obj[11] = password;
        return generateUrl(obj);
    }

    /**
     * Generate activation URL for user
     */
    private String generateActivationUrl(int userId, String username, String email, String password) {
        logDebug("Generating activation URL for user: %s", username);
        var r = Hex.encodeHexString(email.getBytes(UTF_8));
        var key = activationKey;
        String a = null;
        var hex = DigestUtils.sha256Hex("%s %s %s".formatted(r, key, password));
        var rc4 = Hex.encodeHexString(password.getBytes());
        try {
            a = Hex.encodeHexString(RC4(hex, rc4));
            logDebug("Activation URL encryption successful for user: %s", username);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException ex) {
            logError("Error generating activation URL for user: " + username, ex);
        }
        var hd = new HmacUtils(HMAC_SHA_256, "%s %s".formatted(r, key))
                .hmacHex("%d %s %s".formatted(userId, username, a));
        var generatedUrl = "%s?h=%s".formatted(activationUrl, hd);
        logDebug("Generated activation URL: %s", generatedUrl);
        return generatedUrl;
    }

    /**
     * MD5 hash helper
     */
    private static String MD5(String text) {
        return DigestUtils.md5Hex(text);
    }

    /**
     * Log debug message
     */
    private void logDebug(String format, Object... args) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine(String.format(format, args));
        }
    }

    /**
     * Log error message with exception
     */
    private void logError(String message, Exception ex) {
        LOG.log(Level.SEVERE, message, ex);
    }

    /**
     * RC4 encryption helper
     */
    private static byte[] RC4(String text, String part2) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        var part = text.getBytes();
        SecretKey key = new SecretKeySpec(part, "RC4");
        // Create Cipher instance and initialize it to encrytion mode
        var cipher = Cipher.getInstance("RC4");  // Transformation of the algorithm
        cipher.init(Cipher.ENCRYPT_MODE, key);
        var cipherBytes = cipher.doFinal(part2.getBytes());
        return cipherBytes;
    }

    /**
     * Email template holder
     */
    private static class EmailTemplate {
        private final int id;
        private final JsonObject subjects;
        private final JsonObject bodies;
        
        EmailTemplate(int id, JsonObject subjects, JsonObject bodies) {
            this.id = id;
            this.subjects = subjects;
            this.bodies = bodies;
        }
        
        String getSubject(String language) {
            return subjects.getString(language, subjects.getString("en"));
        }
        
        String getBody(String language) {
            return bodies.getString(language, bodies.getString("en"));
        }
    }
}
