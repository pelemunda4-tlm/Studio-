package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CloudProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val bpm: Double = 120.0,
    val timeSignatureNum: Long = 4L,
    val timeSignatureDen: Long = 4L,
    val musicalKey: String = "C",
    val scaleName: String = "Major (Ionian)",
    val trackCount: Long = 0L,
    val versionNumber: Long = 1L,
    val snapshotPayload: String = "{}",
    val notesSummary: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudProjectVersion(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val versionNumber: Long = 1L,
    val label: String = "",
    val snapshotPayload: String = "{}",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudSharedPattern(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val name: String = "",
    val style: String = "",
    val timeSignatureNum: Long = 4L,
    val timeSignatureDen: Long = 4L,
    val bpm: Double = 116.0,
    val stepsJson: String = "{}",
    val shared: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudSharedProject(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val projectTitle: String = "",
    val bpm: Double = 120.0,
    val musicalKey: String = "C",
    val snapshotPayload: String = "{}",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
