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

@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.wultra.android.digitalonboarding

import com.wultra.android.digitalonboarding.networking.model.DocumentFileSide
import com.wultra.android.digitalonboarding.networking.model.DocumentSubmitFileType

class DocumentFile {
    /** Image to be uploaded. */
    var data: ByteArray
    /** Image signature. */
    var dataSignature: String?
    /**Type of the document */
    val type: DocumentType
    /** Side of the document (null if the document is one-sided or only one side is expected) */
    val side: DocumentSide
    /** In case of re-upload */
    val originalDocumentId: String?

    /**
     * Image that can be send to the backend for Identity Verification
     *
     * @param scannedDocument Document which we're uploading
     * @param data: Image raw data
     * @param dataSignature: Signature of the image data. Optional, `null` by default
     * @param side: Side of the document which the image captures
     */
    constructor(scannedDocument: ScannedDocument, data: ByteArray, dataSignature: String? = null, side: DocumentSide) {
        this.originalDocumentId = scannedDocument.serverResult?.first { it.side == side.apiType() }?.id
        this.data = data
        this.dataSignature = dataSignature
        this.type = scannedDocument.type
        this.side = side
    }

    /**
     * Image that can be send to the backend for Identity Verification
     *
     * @param data: Image data to be uploaded.
     * @param dataSignature: Image signature
     * @param type: Type of the document
     * @param side: Side of the document (nil if the document is one-sided or only one side is expected)
     * @param originalDocumentId: Original document ID In case of a re-upload
     */
    constructor(data: ByteArray, dataSignature: String? = null, type: DocumentType, side: DocumentSide, originalDocumentId: String? = null) {
        this.data = data
        this.dataSignature = dataSignature
        this.type = type
        this.side = side
        this.originalDocumentId = originalDocumentId
    }
}

/**
 * Creates image that can be send to the backend for Identity Verification
 *
 * @param side: Side of the document which the image captures
 * @param data: Image raw data
 * @param dataSignature: Signature of the image data. Optional, `null` by default
 * @return Document file for upload
 */
fun ScannedDocument.createFileForUpload(side: DocumentSide, data: ByteArray, dataSignature: String? = null): DocumentFile {
    return DocumentFile(this, data, dataSignature, side)
}

/** Type of the document */
enum class DocumentType {
    /** National ID card */
    ID_CARD,
    /** Passport */
    PASSPORT,
    /** Driving license */
    DRIVERS_LICENSE;

    /** Available sides of the document */
    fun sides(): List<DocumentSide> {
        return when (this) {
            ID_CARD -> listOf(DocumentSide.FRONT, DocumentSide.BACK)
            PASSPORT -> listOf(DocumentSide.FRONT)
            DRIVERS_LICENSE -> listOf(DocumentSide.FRONT)
        }
    }

    internal fun apiType(): DocumentSubmitFileType {
        return when (this) {
            ID_CARD -> DocumentSubmitFileType.ID_CARD
            PASSPORT -> DocumentSubmitFileType.PASSPORT
            DRIVERS_LICENSE -> DocumentSubmitFileType.DRIVING_LICENSE
        }
    }
}

/** Side of the document */
enum class DocumentSide {
    /** Front side of an document. Usually the one with the picture. */
    FRONT,
    /** Back side of an document */
    BACK;

    internal fun apiType(): DocumentFileSide {
        return when (this) {
            FRONT -> DocumentFileSide.FRONT
            BACK -> DocumentFileSide.BACK
        }
    }
}
