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

import com.wultra.android.digitalonboarding.networking.model.Document

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
        val split = cacheData.split(":")
        if (split.count() != 2) {
            throw Exception("Cannot create scan process from cache - unknown cache format")
        }
        val version = CacheVersion.valueOf(split[0])

        if (version != CacheVersion.V1) {
            throw Exception("Cannot create scan process from cache - unknown cache version")
        }

        documents = split[1].split(",").map { ScannedDocument(DocumentType.valueOf(it)) }
    }

    internal fun feed(serverData: List<Document>) {
        serverData.groupBy { it.type }.forEach { group ->
            documents.firstOrNull { it.type.apiType() == group.key }?.serverResult = group.value
        }
    }

    internal fun dataForCache() = "${CacheVersion.V1.name}:${documents.joinToString(",") { it.type.name }}"

    internal enum class CacheVersion {
        V1
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

    internal var serverResult: List<Document>? = null

    /** Upload state of the document */
    fun uploadState(): UploadState {
        val serverResult = serverResult ?: return UploadState.NOT_UPLOADED

        return if (serverResult.any { it.errors?.isEmpty() == false }) {
            UploadState.REJECTED
        } else {
            UploadState.ACCEPTED
        }
    }
}
