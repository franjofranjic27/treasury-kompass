package ch.treasurykompass.treasury.audit

import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AuditEventRepository : JpaRepository<AuditEvent, Long> {

    fun findByClientIdOrderByIdDesc(clientId: UUID, limit: Limit): List<AuditEvent>

    fun findByClientIdAndIdLessThanOrderByIdDesc(clientId: UUID, before: Long, limit: Limit): List<AuditEvent>
}
