# FotoMail

Schickt neu gemachte Kamerafotos automatisch per E-Mail. Die App sammelt alle Fotos, die kurz hintereinander entstehen, und verschickt sie gebündelt, sobald einige Minuten (Standard: 3) kein neues Foto mehr dazugekommen ist.

**Datenschutz eingebaut:** Vor dem Versand wird jedes Bild neu gespeichert. Dabei gehen alle versteckten Bilddaten verloren, vor allem der GPS-Standort (bei Hausbesuchen = Adresse des Klienten), Gerätedaten und Seriennummern. Gesendet werden nur Fotos aus der Kamera, keine Screenshots oder Downloads. Die App enthält keine Werbung, kein Tracking und spricht mit keinem Server außer deinem Mailserver.

> Bitte trotzdem nur Bilder ohne erkennbare Personen aufnehmen: kein Gesicht, keine Namen, Tattoos, Schmuck, Briefe oder Fotos im Hintergrund.

---

## Einmalige Einrichtung (ca. 20 Minuten)

### Schritt 1 – Gmail vorbereiten

Die App meldet sich bei Gmail mit einem **App-Passwort** an, nicht mit deinem normalen Passwort.

1. Mit dem Gmail-Konto anmelden, das die Fotos verschicken soll.
2. Unter **myaccount.google.com → Sicherheit** die **Bestätigung in zwei Schritten** einschalten (falls noch nicht aktiv).
3. **myaccount.google.com/apppasswords** öffnen, als Namen „FotoMail“ eingeben, auf **Erstellen** tippen.
4. Das 16-stellige Passwort notieren. Google zeigt es nur einmal an.

### Schritt 2 – App bei GitHub bauen lassen

1. Auf **github.com** ein kostenloses Konto anlegen.
2. Oben rechts **+ → New repository**. Name: `FotoMail`, **Private** wählen, auf **Create repository** klicken.
3. Auf der neuen Seite den Link **„uploading an existing file“** anklicken.
4. Die ZIP-Datei von Claude am PC entpacken und **den gesamten Inhalt des Ordners `FotoMail`** (alle Dateien *und* Ordner, auch `.github`) in das Browserfenster ziehen.
5. Unten auf **Commit changes** klicken.
6. Oben auf **Actions** klicken. Der Bau startet von selbst und dauert ca. 3–5 Minuten. Ein grüner Haken heißt: fertig.

### Schritt 3 – App aufs Handy

1. Auf dem Diensthandy im Browser **github.com** öffnen und anmelden.
2. Dein Repository `FotoMail` öffnen → rechts **Releases** → **FotoMail.apk** antippen.
3. Datei öffnen. Android fragt, ob der Browser Apps installieren darf → **Zulassen**.
4. Falls „Play Protect“ warnt: **Weitere Details → Trotzdem installieren**. (Die Warnung kommt bei jeder App, die nicht aus dem Play Store stammt.)

### Schritt 4 – App einstellen

1. **Fotos senden an:** die Adresse, an der du die Bilder haben möchtest.
2. **Absender:** deine Gmail-Adresse.
3. **App-Passwort:** das 16-stellige Passwort aus Schritt 1 (Leerzeichen sind egal).
4. Mailserver `smtp.gmail.com`, Port `465` stehen schon drin.
5. **Test-Mail** antippen und prüfen, ob sie ankommt.
6. **Automatischer Versand** einschalten. Bei der Frage nach Fotos **„Alle zulassen“** wählen (nicht „Bestimmte Fotos auswählen“), Benachrichtigungen erlauben.
7. **Akku-Optimierung ausschalten** antippen und bestätigen. Sonst legt Android die App schlafen.

**Zusätzlich bei Samsung:** Einstellungen → Akku → Hintergrundnutzungslimits → „Nie in Standby versetzte Apps“ → FotoMail hinzufügen.

Fertig. Oben in der Statusleiste steht jetzt „FotoMail aktiv“.

---

## Bedienung im Alltag

- Fotos ganz normal mit der Kamera machen.
- Die Anzeige oben zeigt „3 neue Fotos – Versand nach 3 Min. Pause“.
- Nach der Pause kommt eine Mail, z. B. *„Fotos 18.10.2026, 14:32–14:37 (3 Bilder)“*.
- Kein Internet? Die App wartet und sendet, sobald wieder Netz da ist.
- **Jetzt senden** schickt sofort, ohne die Pause abzuwarten.
- Nach einem Neustart des Handys läuft die App von selbst weiter.

Fotos, die *vor* dem Einschalten gemacht wurden, werden nicht verschickt. Die Fotos bleiben in der Galerie; die App löscht nichts.

## Wenn etwas nicht klappt

| Meldung | Lösung |
|---|---|
| „Anmeldung abgelehnt“ | App-Passwort neu erstellen (Schritt 1) und eintragen. Nicht das normale Gmail-Passwort verwenden. |
| Es kommen keine Mails | Akku-Optimierung prüfen (Schritt 4.7), bei Samsung „Nie in Standby“ setzen. |
| „Zugriff auf Fotos fehlt“ | Einstellungen → Apps → FotoMail → Berechtigungen → Fotos → „Alle zulassen“. |
| Mails sehr groß | Schalter „Hohe Auflösung“ ausschalten. |

---

## Wundfoto mit Messung

Auf der Startseite oben: **„Wundfoto mit Messung aufnehmen“**.

**Ablauf**
1. **Pseudonym** wählen oder mit **+ Neu** hinzufügen. Ihr könnt eure **eigene Pseudonym-Liste** verwenden: im Feld einfach mehrere Pseudonyme einfügen, eins pro Zeile (Komma oder Semikolon gehen auch). Erlaubt sind bis 20 Zeichen aus Buchstaben, Ziffern, Leerzeichen, `-`, `_` und `.`. Bitte keine echten Namen, Initialen oder Geburtsdaten – Einträge ohne Ziffer fragt die App sicherheitshalber nach. Ähnliche Doppel (z. B. „w-017“ und „W 017“) werden abgelehnt. Die Liste „Pseudonym → Klient“ führt ihr nur im Büro, nicht in der App.
2. **Lokalisation** und **Wunde Nr.** wählen. Die App merkt sich die letzte Lokalisation je Pseudonym.
3. **Kamera öffnen.** Einen Papier-Messstreifen neben die Wunde legen. Hilfen beim Fotografieren:
   - Hilfsrahmen und Fadenkreuz
   - Neigungsanzeige
   - Licht
   - **Vorlage**: das letzte Foto derselben Wunde halbtransparent eingeblendet, damit Winkel und Abstand gleich bleiben
4. **Maßstab:** die zwei weißen Punkte auf zwei Striche des Messstreifens ziehen und den Abstand wählen (1, 2, 5 oder 10 cm). Mit zwei Fingern zoomen. Beim Ziehen zeigt eine Lupe die Stelle vergrößert.
   - **Kein Streifen dabei?** „Grob schätzen“ rechnet den Maßstab aus dem Kameraabstand (Autofokus), der Brennweite und der Sensorgröße. Das ist nur grob, Abweichungen von ±20–50 % sind möglich, bei der Fläche entsprechend mehr. Die Werte stehen deshalb mit „ca.“ im Bild, und im Betreff steht „grobe Schätzung“ – nicht als Messwert in die Doku übernehmen. Wunde in die Bildmitte, damit der Autofokus darauf sitzt.
   - Die Schätzung erscheint nur, wenn die Kamera den Fokusabstand verlässlich meldet. Liefert sie ihn, zeigt die Kamera auch „Abstand ca. … cm“ an. Das hilft, Folgefotos aus gleichem Abstand zu machen.
5. **Länge** und **Breite** nach der Uhrmethode setzen:
   - Länge: Kopf–Fuß-Richtung (12–6 Uhr), an der längsten Stelle
   - Breite: rechtwinklig dazu (3–9 Uhr), an der breitesten Stelle
   - Die Punkte müssen bewegt werden. Ungeprüfte Vorgabelinien lässt die App nicht durch.
   - Die Fläche wird nach der Rechteckmethode (L × B) berechnet. Diese überschätzt die echte Fläche; bei ovalen Wunden liegt sie bei etwa 0,785 × L × B. Für den Verlauf ist das egal, solange immer gleich gemessen wird.
6. **Tiefe** von Hand eintragen: geschätzt oder mit Watteträger gemessen. Schnellauswahl: 0,1 (oberflächlich), 0,3, 0,5, 1 und 2 cm. Aus dem Foto allein lässt sich die Tiefe nicht bestimmen (siehe unten). Optional eine kurze Bemerkung ohne Namen.
7. **Senden.** Es kommt eine eigene E-Mail mit:
   - **Messbild:** Länge und Breite eingezeichnet, Maßband mit cm/mm-Strichen, Infoleiste mit Pseudonym, Lokalisation, Datum und Messwerten
   - **Originalfoto** ohne Einzeichnungen (abschaltbar)
   - **Betreff:** `Wundfoto W-017 – Ferse links (W1) – 19.10.2026 14:32`
   - **Dateien:** `W-017_2026-10-19_14-32_Ferse-links_W1_1_Messung.jpg`

**Datenschutz**
- **Speicherort:** Die Fotos landen **nicht** in der Galerie, sondern nur im geschützten App-Speicher. Nach erfolgreichem Versand werden sie gelöscht.
- **Kein Netz:** Fehlt die Verbindung, bleiben die Fotos im Postausgang und gehen später raus. Auf dem Wundfoto-Bildschirm steht dann „… warten auf Versand“.
- **Metadaten:** Standort- und Gerätedaten werden entfernt.
- **Vergleichsbild:** Auf dem Handy bleibt nur eine verkleinerte Kopie des letzten Fotos je Wunde für die Vorlage. Sie wird nach 90 Tagen automatisch gelöscht, ist abschaltbar und mit „Gespeicherte Vergleichsbilder löschen“ entfernbar. Wird ein Pseudonym entfernt, verschwindet auch sein Vergleichsbild.
- **Bildschirmschutz:** Kamera- und Messbildschirm erscheinen nicht in Screenshots und nicht in der Übersicht der zuletzt genutzten Apps.
- **Was nicht ins Bild gehört:** kein Gesicht, keine Tattoos, kein Schmuck, keine Papiere mit Namen.

**Messstreifen:** `druckvorlage/Messstreifen-Wundfoto.pdf` in **Originalgröße (100 %)** drucken. Den 10-cm-Kontrollbalken mit einem Lineal prüfen. Jeden Streifen nur einmal verwenden. Geprüfte Papier-Wundlineale aus Verbandsmittel-Packungen gehen genauso und sind genauer.

**Warum keine automatische Tiefe?**
- **Keine Tiefenkamera:** Handys wie das Samsung Galaxy A56 5G haben eine 50-MP-Hauptkamera, eine 12-MP-Ultraweitwinkel- und eine 5-MP-Makrokamera, aber keinen Tiefensensor (ToF/LiDAR).
- **Software-Tiefe zu grob:** Googles AR-Tiefe errechnet Abstände aus der Bewegung der Kamera. Sie ist für Möbel gedacht, auf Zentimeter genau und nicht auf Millimeter.
- **Grundsätzlich nicht sichtbar:** Unterminierungen und Taschen liegen unter dem Wundrand und sind für keine Kamera sichtbar.

Grobe Werte für Länge und Breite gehen also, die Tiefe bleibt Sache von Augenmaß, Watteträger und Wundexpertin.

**Genauigkeit:** Die Messung ist nur so gut wie das Foto. Am genauesten wird sie, wenn
- die Kamera parallel zur Wundfläche gehalten wird,
- der Messstreifen auf **gleicher Höhe** neben der Wunde liegt,
- die Punkte mit der Lupe genau gesetzt werden.

Bei gewölbten Stellen wie Ferse oder Knöchel sind Abweichungen von einigen Millimetern normal. Die Werte sind **Orientierungswerte**. Maßgeblich in der Pflegedokumentation bleibt die Messung am Klienten nach eurem Wundstandard.

## Rechtliches vor dem Einsatz klären

- **Gesundheitsdaten:** Wundfotos sind Gesundheitsdaten (Art. 9 DSGVO). Ein Pseudonym macht sie nicht anonym, sie bleiben personenbezogen. Rechtsgrundlage ist in der Regel Art. 9 Abs. 2 lit. h DSGVO i. V. m. § 22 BDSG (Versorgung); das bewertet euer Datenschutzbeauftragter.
- **Mailweg:** Der Versand sollte über den **Mailserver der Firma** laufen und nicht über ein privates Gmail-Konto. Bei Google besteht kein Auftragsverarbeitungsvertrag mit eurem Pflegedienst. Auch das **Empfängerpostfach** muss beim Pflegedienst bzw. bei einem Anbieter mit AVV in der EU liegen. TLS verschlüsselt nur den Transport, nicht die Mail im Postfach. Den Mailserver stellt man in der App ein (Mailserver, Port, Absender, Passwort).
- **Einwilligung/Aufklärung:** Fotodokumentation nach eurem Hausstandard; in vielen Diensten wird eine Einwilligung des Klienten bzw. der Betreuung eingeholt.
- **Freigabe im Betrieb:** Den Einsatz mit Geschäftsführung und Datenschutzbeauftragtem abstimmen und ins Verarbeitungsverzeichnis aufnehmen.
- **Medizinprodukte-Recht:** Software, die Wunden vermisst und als Grundlage für Behandlungsentscheidungen dient, kann unter das Medizinprodukterecht (MDR) fallen. Die App ist deshalb als **Hilfsmittel zur Fotodokumentation** gedacht. Die Messwerte sind als Orientierungswerte gekennzeichnet, Therapieentscheidungen beruhen weiter auf der fachlichen Wundbeurteilung und der Messung am Klienten. Soll die Messung offiziell als Messinstrument im Wundmanagement dienen, vorher mit der Geschäftsführung klären. **Weitergabe oder Verkauf** an andere Einrichtungen: Eine Software mit der Zweckbestimmung „Wundvermessung“ wäre dann sehr wahrscheinlich ein Medizinprodukt (MDR, Regel 11); der Hinweis „Orientierungswerte“ reicht dann nicht.

## Update einspielen

Neue Dateien bei GitHub hochladen (wie Schritt 2.3–2.5). Der Bau läuft automatisch, danach die neue `FotoMail.apk` unter **Releases** herunterladen und über die alte App installieren. Die Einstellungen bleiben erhalten.

## Technik (für Interessierte)

Kotlin, Android 8 bis 15. Ein Vordergrunddienst beobachtet die Galerie (MediaStore). Jede Änderung startet eine Wartezeit neu (WorkManager). Danach werden alle Fotos aus `DCIM` mit höherer ID als beim letzten Versand neu kodiert (JPEG, max. 2560 px bzw. 4000 px, ohne Metadaten) und über SMTP mit SSL/TLS verschickt; Mails werden bei mehr als 15 MB aufgeteilt. Das Passwort liegt verschlüsselt im Android-Keystore, App-Backups sind abgeschaltet.
