package net.midiandmore.jserv;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SMTP Mailer - Sends emails via SMTP with TLS/SSL support
 * Compatible with Gmail, Outlook, and other SMTP servers
 */
public class SmtpMailer {
    private static final Logger LOG = Logger.getLogger(SmtpMailer.class.getName());
    
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String fromAddress;
    private final String fromName;
    private final boolean useTls;
    private final boolean useSsl;
    private final boolean enabled;
    
    /**
     * Create SMTP mailer from configuration
     * @param config Configuration properties
     */
    public SmtpMailer(java.util.Properties config) {
        this.host = config.getProperty("smtp_host", "smtp.gmail.com");
        this.port = Integer.parseInt(config.getProperty("smtp_port", "587"));
        this.username = config.getProperty("smtp_username", "");
        this.password = config.getProperty("smtp_password", "");
        this.fromAddress = config.getProperty("smtp_from", "noreply@localhost");
        this.fromName = config.getProperty("smtp_from_name", "Services");
        
        // Auto-detect SSL/TLS based on port if not explicitly set
        String tlsConfig = config.getProperty("smtp_use_tls", "");
        String sslConfig = config.getProperty("smtp_use_ssl", "");
        
        if (sslConfig.isEmpty() && tlsConfig.isEmpty()) {
            // Auto-detect: Port 465 = SSL, Port 587 = TLS
            this.useSsl = (port == 465);
            this.useTls = (port == 587);
        } else {
            this.useTls = Boolean.parseBoolean(tlsConfig);
            this.useSsl = Boolean.parseBoolean(sslConfig);
        }
        
        // Enable only if username and password are configured
        this.enabled = !username.isEmpty() && !password.isEmpty();
        
        if (enabled) {
            LOG.info("SMTP Mailer enabled: " + host + ":" + port + " (TLS: " + useTls + ", SSL: " + useSsl + ")");
        } else {
            LOG.warning("SMTP Mailer disabled: username or password not configured");
        }
    }
    
    /**
     * Send an email
     * @param to Recipient email address
     * @param subject Email subject
     * @param body Email body (plain text)
     * @return true if sent successfully
     */
    public boolean sendMail(String to, String subject, String body) {
        if (!enabled) {
            LOG.warning("SMTP not configured - email not sent to: " + to);
            LOG.fine("Subject: " + subject);
            LOG.fine("Body:\n" + body);
            return false;
        }
        
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", String.valueOf(port));
            props.put("mail.smtp.auth", "true");
            
            // TLS configuration (for port 587)
            if (useTls) {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
            
            // SSL configuration (for port 465)
            if (useSsl) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.socketFactory.port", String.valueOf(port));
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.socketFactory.fallback", "false");
            }
            
            // Timeout settings
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");
            props.put("mail.smtp.writetimeout", "10000");
            
            // Create session with authentication
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });
            
            // Enable debug mode
            session.setDebug(true);
            
            // Create message
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromAddress, fromName));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(body);
            
            // Send message
            Transport.send(message);
            
            LOG.info("Email sent successfully to: " + to);
            return true;
            
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to send email to: " + to, e);
            return false;
        }
    }
    
    /**
     * Check if SMTP is properly configured and enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Get SMTP configuration summary
     */
    public String getConfigSummary() {
        return String.format("SMTP: %s:%d (User: %s, TLS: %s, SSL: %s, Enabled: %s)",
            host, port, username, useTls, useSsl, enabled);
    }
}
