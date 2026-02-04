package com.example.udpservice.registration

import com.example.udpservice.persistence.AppRegistrationDao
import com.example.udpservice.persistence.AppRegistrationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation of RegistrationRepository.
 *
 * Converts between domain model (AppRegistration) and Room entity (AppRegistrationEntity).
 * All operations are thread-safe via Room's internal concurrency handling.
 */
class RegistrationRepositoryImpl(
    private val dao: AppRegistrationDao
) : RegistrationRepository {

    override suspend fun register(registration: AppRegistration): Boolean {
        val existed = dao.isRegistered(registration.prefix)
        dao.insertRegistration(registration.toEntity())
        return !existed
    }

    override suspend fun unregister(prefix: String): Boolean {
        return dao.deleteRegistration(prefix) > 0
    }

    override suspend fun getRegistration(prefix: String): AppRegistration? {
        return dao.getRegistration(prefix)?.toDomain()
    }

    override fun observeEnabledRegistrations(): Flow<List<AppRegistration>> {
        return dao.observeEnabledRegistrations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeAllRegistrations(): Flow<List<AppRegistration>> {
        return dao.observeAllRegistrations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun isRegistered(prefix: String): Boolean {
        return dao.isRegistered(prefix)
    }

    override suspend fun getRegisteredPrefixes(): Set<String> {
        return dao.getAllPrefixes().toSet()
    }

    // ========== Mapping functions ==========

    private fun AppRegistration.toEntity(): AppRegistrationEntity {
        return AppRegistrationEntity(
            prefix = prefix,
            packageName = packageName,
            notificationsEnabled = notificationsEnabled,
            deepLinkUri = deepLinkUri,
            createdAt = createdAt
        )
    }

    private fun AppRegistrationEntity.toDomain(): AppRegistration {
        return AppRegistration(
            prefix = prefix,
            packageName = packageName,
            notificationsEnabled = notificationsEnabled,
            deepLinkUri = deepLinkUri,
            createdAt = createdAt
        )
    }
}
