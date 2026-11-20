package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "voucher_history")
data class VoucherHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val portalHost: String,
    val portalUrl: String,
    val source: String, // "CAMERA", "GALLERY", "MANUAL"
    val verifiedConnected: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "portal_profiles")
data class PortalProfileEntity(
    @PrimaryKey val gatewayHost: String,
    val loginUrl: String,
    val preferredMode: String,
    val mirrorPassword: Boolean,
    val lastUsedAt: Long = System.currentTimeMillis(),
)

@Dao
interface VoucherHistoryDao {
    @Query("SELECT * FROM voucher_history ORDER BY timestamp DESC LIMIT 100")
    fun observeHistory(): Flow<List<VoucherHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: VoucherHistoryEntity): Long

    @Query("UPDATE voucher_history SET verifiedConnected = 1 WHERE id = :id")
    suspend fun markConnected(id: Long)

    @Query("DELETE FROM voucher_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM voucher_history")
    suspend fun clearAll()
}

@Dao
interface PortalProfileDao {
    @Query("SELECT * FROM portal_profiles ORDER BY lastUsedAt DESC")
    fun observeProfiles(): Flow<List<PortalProfileEntity>>

    @Query("SELECT * FROM portal_profiles WHERE gatewayHost = :host LIMIT 1")
    suspend fun getByHost(host: String): PortalProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: PortalProfileEntity)

    @Query("DELETE FROM portal_profiles WHERE gatewayHost = :host")
    suspend fun deleteByHost(host: String)
}

@Database(
    entities = [VoucherHistoryEntity::class, PortalProfileEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun voucherHistoryDao(): VoucherHistoryDao
    abstract fun portalProfileDao(): PortalProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mikrotik_2027.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class MikrotikRepository(
    private val historyDao: VoucherHistoryDao,
    private val profileDao: PortalProfileDao,
) {
    val history: Flow<List<VoucherHistoryEntity>> = historyDao.observeHistory()
    val profiles: Flow<List<PortalProfileEntity>> = profileDao.observeProfiles()

    suspend fun recordVoucherUse(
        code: String,
        portalHost: String,
        portalUrl: String,
        source: String,
        verifiedConnected: Boolean = false,
    ): Long {
        val id = historyDao.insert(
            VoucherHistoryEntity(
                code = code,
                portalHost = portalHost,
                portalUrl = portalUrl,
                source = source,
                verifiedConnected = verifiedConnected,
            )
        )
        if (portalHost.isNotBlank()) {
            val existing = profileDao.getByHost(portalHost)
            profileDao.upsert(
                PortalProfileEntity(
                    gatewayHost = portalHost,
                    loginUrl = portalUrl.ifBlank { existing?.loginUrl ?: "" },
                    preferredMode = existing?.preferredMode ?: "AUTO",
                    mirrorPassword = existing?.mirrorPassword ?: true,
                    lastUsedAt = System.currentTimeMillis(),
                )
            )
        }
        return id
    }

    suspend fun markVoucherConnected(id: Long) = historyDao.markConnected(id)

    suspend fun deleteHistoryItem(id: Long) = historyDao.deleteById(id)

    suspend fun clearHistory() = historyDao.clearAll()

    suspend fun savePortalProfile(
        gatewayHost: String,
        loginUrl: String,
        preferredMode: String,
        mirrorPassword: Boolean,
    ) {
        profileDao.upsert(
            PortalProfileEntity(
                gatewayHost = gatewayHost,
                loginUrl = loginUrl,
                preferredMode = preferredMode,
                mirrorPassword = mirrorPassword,
                lastUsedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deletePortalProfile(host: String) = profileDao.deleteByHost(host)
}
