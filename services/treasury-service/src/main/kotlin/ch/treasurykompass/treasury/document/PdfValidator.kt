package ch.treasurykompass.treasury.document

import ch.treasurykompass.treasury.common.ProblemException
import ch.treasurykompass.treasury.common.ProblemType
import jakarta.validation.constraints.Positive
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import java.io.IOException

@Validated
@ConfigurationProperties("treasury.upload")
data class UploadProperties(
    @field:Positive val maxBytes: Long,
    @field:Positive val maxPages: Int,
)

/** Result of a successful validation. */
data class ValidatedPdf(val bytes: ByteArray, val pageCount: Int)

/**
 * Checks an upload before anything is stored (architecture 12): PDF signature within the first 1024 bytes
 * (the browser's content type does not count), size and page limits, no encryption. PDFBox parses in memory only;
 * it never executes JavaScript or embedded files.
 */
@Component
class PdfValidator(private val properties: UploadProperties) {

    fun validate(bytes: ByteArray): ValidatedPdf {
        if (bytes.size > properties.maxBytes) {
            throw ProblemException(ProblemType.PAYLOAD_TOO_LARGE, "Die Datei ist grösser als ${properties.maxBytes / BYTES_PER_MB} MB.")
        }
        if (!hasPdfSignature(bytes)) {
            throw ProblemException(ProblemType.UNSUPPORTED_MEDIA_TYPE, "Die Datei ist keine PDF-Datei (Signatur %PDF- fehlt).")
        }
        val pageCount = pageCountOf(bytes)
        if (pageCount > properties.maxPages) {
            throw ProblemException(ProblemType.PDF_PAGE_LIMIT, "Die PDF-Datei hat $pageCount Seiten, erlaubt sind höchstens ${properties.maxPages}.")
        }
        return ValidatedPdf(bytes, pageCount)
    }

    private fun hasPdfSignature(bytes: ByteArray): Boolean {
        val window = minOf(bytes.size, SIGNATURE_WINDOW)
        return (0..window - SIGNATURE.size).any { offset -> SIGNATURE.indices.all { bytes[offset + it] == SIGNATURE[it] } }
    }

    private fun pageCountOf(bytes: ByteArray): Int = try {
        Loader.loadPDF(RandomAccessReadBuffer(bytes), "", null, null, IOUtils.createMemoryOnlyStreamCache()).use { document ->
            // Owner-password-only encryption opens without a password but is rejected as well.
            if (document.isEncrypted) throw encrypted()
            document.numberOfPages
        }
    } catch (_: InvalidPasswordException) {
        throw encrypted()
    } catch (ex: IOException) {
        throw ProblemException(ProblemType.PDF_UNREADABLE, "Die PDF-Datei kann nicht gelesen werden.").apply { initCause(ex) }
    }

    private fun encrypted() = ProblemException(ProblemType.PDF_ENCRYPTED, "Die PDF-Datei ist passwortgeschützt oder verschlüsselt.")

    private companion object {
        const val SIGNATURE_WINDOW = 1024
        const val BYTES_PER_MB = 1024 * 1024
        val SIGNATURE = "%PDF-".toByteArray(Charsets.US_ASCII)
    }
}
