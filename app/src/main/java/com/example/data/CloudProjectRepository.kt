package com.example.data

import android.content.Context
import com.example.R
import com.example.model.CloudProject
import com.example.model.CloudProjectVersion
import com.example.model.CloudSharedPattern
import com.example.model.CloudSharedProject
import com.example.model.DawProject
import com.example.model.RhythmPattern
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CloudProjectRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Please sign in with Google before accessing cloud studio backups.")
    }

    private fun sanitizeDocId(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64).ifBlank {
            "id_${UUID.randomUUID().toString().take(8)}"
        }
    }

    fun observeCloudProjects(userId: String): Flow<List<CloudProject>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/projects"
        emitAll(
            db.collection("users").document(userId).collection("projects")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProjectVersions(userId: String, projectId: String): Flow<List<CloudProjectVersion>> = flow {
        if (userId.isBlank() || projectId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val safeProjId = sanitizeDocId(projectId)
        val path = "users/$userId/projects/$safeProjId/versions"
        emitAll(
            db.collection("users").document(userId)
                .collection("projects").document(safeProjId)
                .collection("versions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudProjectVersion::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.versionNumber }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeSharedPatterns(): Flow<List<CloudSharedPattern>> = flow {
        val uid = requireUserId()
        val path = "shared_patterns"
        emitAll(
            db.collection("shared_patterns")
                .whereEqualTo("shared", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudSharedPattern::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun backupProject(project: DawProject, versionLabel: String): Result<Unit> {
        val safeId = sanitizeDocId(project.id)
        val uidPreview = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$uidPreview/projects/$safeId"
        return try {
            val uid = requireUserId()
            val payload = ProjectSerializer.projectToJson(project)
            require(payload.length <= 600000) { "Project payload exceeds cloud document limit (600 KB)." }
            val title = project.title.trim().take(100).ifBlank { "Untitled Session" }
            val bpm = project.bpm.coerceIn(40.0, 240.0)
            val tsNum = project.timeSignature.numerator.coerceIn(1, 16)
            val tsDen = project.timeSignature.denominator.coerceIn(1, 16)
            val keyStr = project.musicalKey.trim().take(24).ifBlank { "C" }
            val scaleStr = project.scale.displayName.trim().take(40).ifBlank { "Major" }
            val trackCount = project.tracks.size.coerceIn(0, 128)
            val versionNumber = project.versionNumber.coerceAtLeast(1)
            val summary = project.notesSummary.trim().take(500).ifBlank { null }

            val docRef = db.collection("users").document(uid).collection("projects").document(safeId)
            val existingSnap = docRef.get().await()

            if (!existingSnap.exists()) {
                val createMap = mapOf(
                    "userId" to uid,
                    "title" to title,
                    "bpm" to bpm,
                    "timeSignatureNum" to tsNum,
                    "timeSignatureDen" to tsDen,
                    "musicalKey" to keyStr,
                    "scaleName" to scaleStr,
                    "trackCount" to trackCount,
                    "versionNumber" to versionNumber,
                    "snapshotPayload" to payload,
                    "notesSummary" to summary,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(createMap).await()
            } else {
                val updateMap = mapOf(
                    "title" to title,
                    "bpm" to bpm,
                    "timeSignatureNum" to tsNum,
                    "timeSignatureDen" to tsDen,
                    "musicalKey" to keyStr,
                    "scaleName" to scaleStr,
                    "trackCount" to trackCount,
                    "versionNumber" to versionNumber,
                    "snapshotPayload" to payload,
                    "notesSummary" to summary,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.update(updateMap).await()
            }

            // Record immutable version snapshot
            val verId = "v${versionNumber}_${System.currentTimeMillis()}"
            val verMap = mapOf(
                "userId" to uid,
                "projectId" to safeId,
                "versionNumber" to versionNumber,
                "label" to versionLabel.trim().take(80).ifBlank { "Studio Backup v$versionNumber" },
                "snapshotPayload" to payload,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.collection("versions").document(verId).set(verMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun shareProject(project: DawProject): Result<String> {
        val shareCode = "TW-${sanitizeDocId(project.id).take(8).uppercase()}-${(100..999).random()}"
        val path = "shared_projects/$shareCode"
        return try {
            val uid = requireUserId()
            val ownerName = (auth.currentUser?.displayName ?: "Trueworld Producer").trim().take(60).ifBlank { "Producer" }
            val payload = ProjectSerializer.projectToJson(project)
            require(payload.length <= 600000) { "Project too large for share snapshot." }
            val map = mapOf(
                "ownerId" to uid,
                "ownerName" to ownerName,
                "projectTitle" to project.title.trim().take(100).ifBlank { "Shared Session" },
                "bpm" to project.bpm.coerceIn(40.0, 240.0),
                "musicalKey" to project.musicalKey.trim().take(24).ifBlank { "C" },
                "snapshotPayload" to payload,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("shared_projects").document(shareCode).set(map).await()
            Result.success(shareCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun fetchSharedProject(shareCode: String): Result<DawProject> {
        val safeCode = sanitizeDocId(shareCode.trim())
        val path = "shared_projects/$safeCode"
        return try {
            requireUserId()
            val snap = db.collection("shared_projects").document(safeCode).get().await()
            val model = snap.toObject(
                CloudSharedProject::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: throw IllegalStateException("No shared project found for code $safeCode")
            val parsed = ProjectSerializer.projectFromJson(model.snapshotPayload)
                ?: throw IllegalStateException("Shared project snapshot was malformed.")
            Result.success(parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun saveAndSharePattern(pattern: RhythmPattern, sharePublicly: Boolean): Result<Unit> {
        val safeId = sanitizeDocId(pattern.id)
        val path = "shared_patterns/$safeId"
        return try {
            val uid = requireUserId()
            val author = (auth.currentUser?.displayName ?: "Trueworld Producer").trim().take(60).ifBlank { "Producer" }
            val stepsJson = ProjectSerializer.patternToJson(pattern)
            require(stepsJson.length <= 50000) { "Pattern too large." }
            val docRef = db.collection("shared_patterns").document(safeId)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val map = mapOf(
                    "userId" to uid,
                    "authorName" to author,
                    "name" to pattern.name.trim().take(80).ifBlank { "Custom Rhythm" },
                    "style" to pattern.style.displayName.take(40),
                    "timeSignatureNum" to pattern.timeSignature.numerator.coerceIn(1, 16),
                    "timeSignatureDen" to pattern.timeSignature.denominator.coerceIn(1, 16),
                    "bpm" to pattern.bpm.coerceIn(40.0, 240.0),
                    "stepsJson" to stepsJson,
                    "shared" to sharePublicly,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(map).await()
            } else {
                val map = mapOf(
                    "authorName" to author,
                    "name" to pattern.name.trim().take(80).ifBlank { "Custom Rhythm" },
                    "style" to pattern.style.displayName.take(40),
                    "timeSignatureNum" to pattern.timeSignature.numerator.coerceIn(1, 16),
                    "timeSignatureDen" to pattern.timeSignature.denominator.coerceIn(1, 16),
                    "bpm" to pattern.bpm.coerceIn(40.0, 240.0),
                    "stepsJson" to stepsJson,
                    "shared" to sharePublicly,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(map).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudProject(userId: String, projectId: String): Result<Unit> {
        val safeId = sanitizeDocId(projectId)
        val path = "users/$userId/projects/$safeId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user delete." }
            db.collection("users").document(uid).collection("projects").document(safeId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
