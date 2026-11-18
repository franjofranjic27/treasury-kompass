package ch.treasurykompass.treasury.common

import org.springframework.http.HttpStatus
import java.net.URI

/**
 * Problem types of the RFC 9457 error model (architecture 10.2).
 * The slug is part of the `type` URI and is repeated in the `code` extension.
 */
enum class ProblemType(val slug: String, val status: HttpStatus, val title: String) {
    VALIDATION_ERROR("validation-error", HttpStatus.BAD_REQUEST, "Ungültige Eingabe"),
    UNAUTHORIZED("unauthorized", HttpStatus.UNAUTHORIZED, "Nicht autorisiert"),
    NOT_FOUND("not-found", HttpStatus.NOT_FOUND, "Nicht gefunden"),
    METHOD_NOT_ALLOWED("method-not-allowed", HttpStatus.METHOD_NOT_ALLOWED, "Methode nicht erlaubt"),
    DUPLICATE_CLIENT("duplicate-client", HttpStatus.CONFLICT, "Kunde existiert bereits"),
    DUPLICATE_DOCUMENT("duplicate-document", HttpStatus.CONFLICT, "Dokument existiert bereits"),
    QUESTION_NOT_APPLICABLE("question-not-applicable", HttpStatus.CONFLICT, "Frage nicht anwendbar"),
    PROPOSAL_NOT_OPEN("proposal-not-open", HttpStatus.CONFLICT, "Vorschlag nicht mehr offen"),
    COLLAR_NOT_ALLOWED("collar-not-allowed", HttpStatus.CONFLICT, "Zero-Cost-Collar nicht erlaubt"),
    STALE_ASSESSMENT("stale-assessment", HttpStatus.CONFLICT, "Beurteilung veraltet"),
    ASSESSMENT_INCOMPLETE("assessment-incomplete", HttpStatus.CONFLICT, "Beurteilung unvollständig"),
    APPROVAL_BLOCKED("approval-blocked", HttpStatus.CONFLICT, "Freigabe nicht möglich"),
    NO_ACTIVE_APPROVAL("no-active-approval", HttpStatus.CONFLICT, "Keine aktive Freigabe"),
    STALE_INGEST("stale-ingest", HttpStatus.CONFLICT, "Veraltete Verarbeitung"),
    INVALID_STATUS_TRANSITION("invalid-status-transition", HttpStatus.CONFLICT, "Ungültiger Statuswechsel"),
    PAYLOAD_TOO_LARGE("payload-too-large", HttpStatus.CONTENT_TOO_LARGE, "Datei zu gross"),
    UNSUPPORTED_MEDIA_TYPE("unsupported-media-type", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Keine PDF-Datei"),
    PDF_PAGE_LIMIT("pdf-page-limit", HttpStatus.UNPROCESSABLE_CONTENT, "Zu viele Seiten"),
    PDF_ENCRYPTED("pdf-encrypted", HttpStatus.UNPROCESSABLE_CONTENT, "PDF ist passwortgeschützt"),
    PDF_UNREADABLE("pdf-unreadable", HttpStatus.UNPROCESSABLE_CONTENT, "PDF nicht lesbar"),
    UPSTREAM_UNAVAILABLE("upstream-unavailable", HttpStatus.SERVICE_UNAVAILABLE, "Dienst nicht erreichbar"),
    INTERNAL_ERROR("internal-error", HttpStatus.INTERNAL_SERVER_ERROR, "Interner Fehler");

    val type: URI = URI.create("urn:treasury-kompass:problem:$slug")

    companion object {
        /** Fallback mapping for framework exceptions that only carry an HTTP status. */
        fun forStatus(status: Int): ProblemType = when (status) {
            400 -> VALIDATION_ERROR
            401 -> UNAUTHORIZED
            404 -> NOT_FOUND
            405 -> METHOD_NOT_ALLOWED
            413 -> PAYLOAD_TOO_LARGE
            415 -> UNSUPPORTED_MEDIA_TYPE
            503 -> UPSTREAM_UNAVAILABLE
            else -> if (status in 400..499) VALIDATION_ERROR else INTERNAL_ERROR
        }
    }
}
