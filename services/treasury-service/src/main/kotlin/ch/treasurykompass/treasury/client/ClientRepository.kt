package ch.treasurykompass.treasury.client

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ClientRepository : JpaRepository<Client, UUID> {

    /** `SELECT … FOR UPDATE` on the client row: serialises all commands of one client (architecture 4.1). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Client c where c.id = :id")
    fun lockById(id: UUID): Client?

    @Query("select count(c) > 0 from Client c where lower(c.name) = lower(:name)")
    fun existsByNameIgnoringCase(name: String): Boolean

    @Query("select count(c) > 0 from Client c where lower(c.name) = lower(:name) and c.id <> :excludedId")
    fun existsByNameIgnoringCaseExcluding(name: String, excludedId: UUID): Boolean

    /** [escapedQuery] must have `!`, `%` and `_` escaped with `!`. */
    @Query("select c from Client c where lower(c.name) like lower(concat('%', :escapedQuery, '%')) escape '!' order by c.name")
    fun search(escapedQuery: String): List<Client>

    @Query("select c from Client c order by c.name")
    fun findAllOrderedByName(): List<Client>
}
