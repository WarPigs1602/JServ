# SMTP Configuration for JServ

## Overview
JServ now includes SMTP email support for sending templated emails (password resets, account notifications, etc.).

## Configuration

Add these settings to your `config.json`:

```json
{"name":"smtp_host","value":"smtp.gmail.com"},
{"name":"smtp_port","value":"587"},
{"name":"smtp_username","value":"your-email@gmail.com"},
{"name":"smtp_password","value":"your-app-password"},
{"name":"smtp_from","value":"noreply@midiandmore.net"},
{"name":"smtp_from_name","value":"MidiAndMore.Net Services"},
{"name":"smtp_use_tls","value":"true"},
{"name":"smtp_use_ssl","value":"false"}
```

## Gmail Setup

### 1. Enable 2-Factor Authentication
Go to your Google Account settings and enable 2FA if not already enabled.

### 2. Create App Password
1. Go to: https://myaccount.google.com/apppasswords
2. Select "Mail" and your device
3. Click "Generate"
4. Copy the 16-character password
5. Use this password in `smtp_password` (remove spaces)

### 3. Configuration Example for Gmail
```json
{"name":"smtp_host","value":"smtp.gmail.com"},
{"name":"smtp_port","value":"587"},
{"name":"smtp_username","value":"your-email@gmail.com"},
{"name":"smtp_password","value":"abcdabcdabcdabcd"},
{"name":"smtp_use_tls","value":"true"},
{"name":"smtp_use_ssl","value":"false"}
```

## Other SMTP Providers

### Outlook/Hotmail
```json
{"name":"smtp_host","value":"smtp-mail.outlook.com"},
{"name":"smtp_port","value":"587"},
{"name":"smtp_use_tls","value":"true"}
```

### Yahoo Mail
```json
{"name":"smtp_host","value":"smtp.mail.yahoo.com"},
{"name":"smtp_port","value":"587"},
{"name":"smtp_use_tls","value":"true"}
```

### Custom SMTP Server
```json
{"name":"smtp_host","value":"mail.yourdomain.com"},
{"name":"smtp_port","value":"587"},
{"name":"smtp_username","value":"username"},
{"name":"smtp_password","value":"password"},
{"name":"smtp_use_tls","value":"true"}
```

## Ports

- **587**: STARTTLS (recommended) - Use with `smtp_use_tls=true`
- **465**: SSL/TLS - Use with `smtp_use_ssl=true` and `smtp_port=465`
- **25**: Plain (not recommended)

## Testing

After configuration, emails will be sent automatically when:
- User requests password reset (REQUESTPASSWORD)
- User changes password (PASSWD)
- User registers (if registration emails are enabled)

Check the logs for:
```
INFO: SMTP Mailer enabled: smtp.gmail.com:587 (TLS: true, SSL: false, Enabled: true)
INFO: Email sent successfully to: user@example.com
```

If SMTP is not configured:
```
WARNING: SMTP Mailer disabled: username or password not configured
WARNING: SMTP not configured - email not sent to: user@example.com
```

## Troubleshooting

### "Authentication failed"
- Verify username/password are correct
- For Gmail: Use App Password, not regular password
- Check if 2FA is enabled for Gmail

### "Connection timeout"
- Verify SMTP host and port are correct
- Check firewall settings
- Ensure TLS/SSL settings match server requirements

### "Invalid sender"
- Some providers require `smtp_from` to match `smtp_username`
- Update `smtp_from` to match your email address

## Security Notes

- **Never commit `config.json` with real passwords to version control**
- Use App Passwords for Gmail (never your main password)
- Keep SMTP credentials secure
- Use TLS whenever possible
- Consider using environment variables for sensitive data

## Email Templates

Templates are defined in `src/main/resources/email-templates.json`:
- Template 1: Account registration
- Template 2: Password request ✓
- Template 3: Password change ✓
- Template 4: Account reset
- Template 5: Email change
- Template 6: Registration completion

Templates support multiple languages (DE, EN, ES, FR, SV, IT, TR, PT).
