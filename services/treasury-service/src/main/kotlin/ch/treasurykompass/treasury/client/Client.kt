package ch.treasurykompass.treasury.client

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "client")
class Client(
    @Id
    val id: UUID,
    @Column(nullable = false, length = 200)
    var name: String,
    var industry: String?,
    var employeeCount: Int?,
    var fiscalYear: Int?,
    var shortLabel: String?,
    @Column(nullable = false, updatable = false)
    val createdAt: Instant,
    @Column(nullable = false)
    var updatedAt: Instant,
)
