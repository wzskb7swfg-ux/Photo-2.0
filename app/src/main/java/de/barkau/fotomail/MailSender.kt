package de.barkau.fotomail

import java.io.File
import java.util.Date
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/** Versendet E-Mails direkt über SMTP (z. B. Gmail), immer verschlüsselt (SSL/TLS). */
object MailSender {

    fun send(prefs: Prefs, subject: String, body: String, files: List<File>) {
        val host = prefs.host
        val port = prefs.port
        val user = prefs.user
        // Google zeigt App-Passwörter mit Leerzeichen an – die gehören nicht dazu.
        val password = if (host.contains("gmail", ignoreCase = true))
            prefs.password.replace(" ", "") else prefs.password

        val props = Properties().apply {
            put("mail.smtp.host", host)
            put("mail.smtp.port", port.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.connectiontimeout", "20000")
            put("mail.smtp.timeout", "60000")
            put("mail.smtp.writetimeout", "120000")
            if (port == 465) {
                put("mail.smtp.ssl.enable", "true")
            } else {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
            put("mail.smtp.ssl.checkserveridentity", "true")
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(user, password)
        })

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(user))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(prefs.recipient))
            setSubject(subject, "UTF-8")
            sentDate = Date()

            val multipart = MimeMultipart()
            multipart.addBodyPart(MimeBodyPart().apply { setText(body, "UTF-8") })
            for (f in files) {
                val part = MimeBodyPart()
                part.dataHandler = DataHandler(object : FileDataSource(f) {
                    override fun getContentType(): String = "image/jpeg"
                })
                part.fileName = f.name
                multipart.addBodyPart(part)
            }
            setContent(multipart)
        }

        Transport.send(message)
    }
}
