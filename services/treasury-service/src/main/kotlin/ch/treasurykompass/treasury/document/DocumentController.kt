package ch.treasurykompass.treasury.document

import ch.treasurykompass.treasury.common.ProblemException
import ch.treasurykompass.treasury.common.ProblemType
import ch.treasurykompass.treasury.common.ValidationException
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.util.UUID

@RestController
@RequestMapping("/api/clients/{clientId}/documents")
class DocumentController(
    private val documentService: DocumentService,
    private val uploadProperties: UploadProperties,
) {

    @GetMapping
    fun list(@PathVariable clientId: UUID): List<DocumentView> = documentService.list(clientId)

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@PathVariable clientId: UUID, @RequestPart("file") file: MultipartFile): ResponseEntity<DocumentView> {
        // Checked before reading the bytes; the multipart limit (21 MB) only protects the request as a whole.
        if (file.size > uploadProperties.maxBytes) {
            throw ProblemException(
                ProblemType.PAYLOAD_TOO_LARGE,
                "Die Datei ist grösser als ${uploadProperties.maxBytes / (1024 * 1024)} MB.",
            )
        }
        if (file.isEmpty) throw ValidationException("file", "Datei fehlt oder ist leer")
        val document = documentService.upload(clientId, file.originalFilename, file.bytes)
        val location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(document.id).toUri()
        return ResponseEntity.created(location).body(document)
    }

    @GetMapping("/{documentId}")
    fun get(@PathVariable clientId: UUID, @PathVariable documentId: UUID): DocumentView = documentService.get(clientId, documentId)

    @GetMapping("/{documentId}/content")
    fun content(@PathVariable clientId: UUID, @PathVariable documentId: UUID): ResponseEntity<ByteArray> =
        pdfResponse(documentService.content(clientId, documentId))

    @PostMapping("/{documentId}/reprocess")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun reprocess(@PathVariable clientId: UUID, @PathVariable documentId: UUID): DocumentView =
        documentService.reprocess(clientId, documentId)

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable clientId: UUID, @PathVariable documentId: UUID) = documentService.delete(clientId, documentId)

    companion object {
        /** Inline delivery as PDF only; `nosniff` keeps browsers from interpreting the bytes as anything else. */
        fun pdfResponse(file: DocumentFile, extraHeaders: HttpHeaders = HttpHeaders()): ResponseEntity<ByteArray> {
            val headers = HttpHeaders().apply {
                addAll(extraHeaders)
                contentType = MediaType.APPLICATION_PDF
                contentDisposition = ContentDisposition.inline().filename(file.document.filename, Charsets.UTF_8).build()
                set("X-Content-Type-Options", "nosniff")
                contentLength = file.bytes.size.toLong()
            }
            return ResponseEntity.ok().headers(headers).body(file.bytes)
        }
    }
}
