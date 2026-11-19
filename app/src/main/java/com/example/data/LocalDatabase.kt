package com.example.data

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

@Entity(tableName = "local_projects")
data class LocalProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val bpm: Double,
    val musicalKey: String,
    val scaleName: String,
    val trackCount: Int,
    val versionNumber: Int,
    val payloadJson: String,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_versions")
data class LocalVersionEntity(
    @PrimaryKey(autoGenerate = true) val versionId: Long = 0,
    val projectId: String,
    val versionNumber: Int,
    val label: String,
    val payloadJson: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_patterns")
data class CustomPatternEntity(
    @PrimaryKey val id: String,
    val name: String,
    val style: String,
    val timeSignature: String,
    val bpm: Double,
    val patternJson: String,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "autosave_recovery")
data class AutosaveRecoveryEntity(
    @PrimaryKey val slotId: Int = 1,
    val projectId: String,
    val projectTitle: String,
    val payloadJson: String,
    val savedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "sound_assets")
data class SoundAssetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val uriString: String,
    val formatLabel: String,
    val sampleRate: Int,
    val durationSec: Float,
    val missing: Boolean = false
)

@Dao
interface StudioDao {
    @Query("SELECT * FROM local_projects ORDER BY updatedAtMs DESC")
    fun observeProjects(): Flow<List<LocalProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(entity: LocalProjectEntity)

    @Query("DELETE FROM local_projects WHERE id = :projectId")
    suspend fun deleteProject(projectId: String)

    @Query("SELECT * FROM local_versions WHERE projectId = :projectId ORDER BY versionNumber DESC")
    fun observeVersions(projectId: String): Flow<List<LocalVersionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(entity: LocalVersionEntity)

    @Query("SELECT * FROM custom_patterns ORDER BY updatedAtMs DESC")
    fun observeCustomPatterns(): Flow<List<CustomPatternEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPattern(entity: CustomPatternEntity)

    @Query("SELECT * FROM autosave_recovery WHERE slotId = 1 LIMIT 1")
    suspend fun getAutosave(): AutosaveRecoveryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAutosave(entity: AutosaveRecoveryEntity)

    @Query("DELETE FROM autosave_recovery WHERE slotId = 1")
    suspend fun clearAutosave()

    @Query("SELECT * FROM sound_assets ORDER BY name ASC")
    fun observeSoundAssets(): Flow<List<SoundAssetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSoundAsset(entity: SoundAssetEntity)
}

@Database(
    entities = [
        LocalProjectEntity::class,
        LocalVersionEntity::class,
        CustomPatternEntity::class,
        AutosaveRecoveryEntity::class,
        SoundAssetEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TrueworldDatabase : RoomDatabase() {
    abstract fun studioDao(): StudioDao

    companion object {
        @Volatile
        private var INSTANCE: TrueworldDatabase? = null

        fun getInstance(context: Context): TrueworldDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrueworldDatabase::class.java,
                    "trueworld_studio.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
