/*
 * Copyright 2023 Wultra s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 */

@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.wultra.android.digitalonboarding

import com.google.gson.Gson
import com.wultra.android.digitalonboarding.networking.model.Document
import com.wultra.android.digitalonboarding.networking.model.DocumentFileSide

/** Verification Scan Process that describes which documents needs to be scanned and uploaded */
class VerificationScanProcess {

    /** Documents that needs to be scanned */
    val documents: List<ScannedDocument>

    /** Which document should be scanned next. `nil` when all documents are uploaded and accepted */
    fun nextDocumentToScan() = documents.firstOrNull { it.uploadState() != ScannedDocument.UploadState.ACCEPTED }

    constructor(types: List<DocumentType>) {
        this.documents = types.map { ScannedDocument(it) }
    }

    // FOR CACHE PURPOSES

    @Throws
    internal constructor(cacheData: String) {
        this.documents = parseDocumentsFromCache(cacheData)
    }

    internal fun feed(serverData: List<Document>) {
        serverData.groupBy { it.type }.forEach { group ->
            documents.firstOrNull { it.type == group.key }?.processServerData(group.value)
        }
    }

    internal fun dataForCache() = Gson().toJson(
        CacheV2(
            v = 2,
            documents = documents.map { doc ->
                CacheV2.CachedDocument(
                    type = doc.type,
                    sides = doc.sides.map { side ->
                        CacheV2.CachedSide(
                            side = when (side.type) {
                                DocumentSide.FRONT -> CacheV2.CachedSide.Side.FRONT
                                DocumentSide.BACK -> CacheV2.CachedSide.Side.BACK
                            },
                            serverId = side.serverId,
                            uploadState = when (side.uploadState) {
                                ScannedDocument.UploadState.ACCEPTED -> CacheV2.CachedSide.UploadState.ACCEPTED
                                else -> CacheV2.CachedSide.UploadState.REJECTED
                            },
                        )
                    },
                )
            },
        )
    )

    internal enum class CacheVersion {
        V1
    }

    private data class CacheV2(
        val v: Int,
        val documents: List<CachedDocument>,
    ) {
        data class CachedDocument(
            val type: String,
            val sides: List<CachedSide>,
        )

        data class CachedSide(
            val side: Side,
            val serverId: String,
            val uploadState: UploadState,
        ) {
            enum class Side {
                FRONT,
                BACK,
            }

            enum class UploadState {
                ACCEPTED,
                REJECTED,
            }
        }
    }

    companion object {
        @Throws
        private fun parseDocumentsFromCache(cacheData: String): List<ScannedDocument> {

            // Try to parse new cache format first, if it fails, try to parse old cache format for backward compatibility
            runCatching {
                val cache = Gson().fromJson(cacheData, CacheV2::class.java)
                if (cache != null && cache.v == 2) {
                    return@parseDocumentsFromCache cache.documents.map { cachedDocument ->
                        ScannedDocument(
                            type = cachedDocument.type,
                            sides = cachedDocument.sides.map { cachedSide ->
                                ScannedDocument.Side(
                                    type = when (cachedSide.side) {
                                        CacheV2.CachedSide.Side.FRONT -> DocumentSide.FRONT
                                        CacheV2.CachedSide.Side.BACK -> DocumentSide.BACK
                                    },
                                    serverId = cachedSide.serverId,
                                    uploadState = when (cachedSide.uploadState) {
                                        CacheV2.CachedSide.UploadState.ACCEPTED -> ScannedDocument.UploadState.ACCEPTED
                                        CacheV2.CachedSide.UploadState.REJECTED -> ScannedDocument.UploadState.REJECTED
                                    },
                                )
                            },
                        )
                    }
                }
            }

            // Old cache format is simple string with version and comma separated document types, e.g. "V1:ID_CARD,DRIVER_LICENSE"
            val split = cacheData.split(":")
            if (split.count() != 2) {
                throw Exception("Cannot create scan process from cache - unknown cache format")
            }
            val version = CacheVersion.valueOf(split[0])
            if (version != CacheVersion.V1) {
                throw Exception("Cannot create scan process from cache - unknown cache version")
            }
            return split[1].split(",").map { ScannedDocument(it) }
        }
    }
}

/**
 * Document that needs to be scanned during process
 *
 * @property type Type of the document
 */
class ScannedDocument(val type: DocumentType) {

    enum class UploadState {
        /** Document was not uploaded yet */
        NOT_UPLOADED,
        /** Document was accepted */
        ACCEPTED,
        /** Document was rejected and needs to be re-uploaded */
        REJECTED
    }

    var sides: List<Side> = emptyList()
        private set

    internal constructor(type: DocumentType, sides: List<Side>) : this(type) {
        this.sides = sides
    }

    /** Upload state of the document */
    fun uploadState(): UploadState {
        if (sides.isEmpty()) {
            return UploadState.NOT_UPLOADED
        }

        if (sides.any { it.uploadState == UploadState.REJECTED }) {
            return UploadState.REJECTED
        }

        return UploadState.ACCEPTED
    }

    internal fun originalDocumentIdFor(side: DocumentSide): String? {
        return sides.firstOrNull { it.type == side }?.serverId
    }

    internal fun processServerData(documents: List<Document>) {
        sides = documents.map { document ->
            Side(
                type = when (document.side) {
                    DocumentFileSide.BACK -> DocumentSide.BACK
                    else -> DocumentSide.FRONT
                },
                serverId = document.id,
                uploadState = if (document.errors?.isNotEmpty() == true) {
                    UploadState.REJECTED
                } else {
                    UploadState.ACCEPTED
                },
            )
        }
    }

    data class Side(
        val type: DocumentSide,
        val serverId: String,
        val uploadState: UploadState,
    )
}
