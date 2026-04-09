/*
 * Copyright 2026 Wultra s.r.o.
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

package com.wultra.android.digitalonboarding

import com.wultra.android.digitalonboarding.networking.model.Document
import com.wultra.android.digitalonboarding.networking.model.DocumentFileSide
import com.wultra.android.digitalonboarding.networking.model.DocumentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationScanProcessTest {

    @Test
    fun scanProcessPreservesServerDataThroughCacheSerialization() {
        val process = VerificationScanProcess(types = listOf("ID_CARD", "PASSPORT"))

        process.feed(
            listOf(
                Document("id_front.jpg", "srv-1", "ID_CARD", DocumentFileSide.FRONT, DocumentStatus.ACCEPTED, null),
                Document("id_back.jpg", "srv-2", "ID_CARD", DocumentFileSide.BACK, DocumentStatus.REJECTED, listOf("blur")),
                Document("pp_front.jpg", "srv-3", "PASSPORT", DocumentFileSide.FRONT, DocumentStatus.ACCEPTED, null),
            )
        )

        val restored = VerificationScanProcess(process.dataForCache())

        val idCard = restored.documents.first { it.type == "ID_CARD" }
        assertEquals(2, idCard.sides.size)
        val idFront = idCard.sides.first { it.type == DocumentSide.FRONT }
        assertEquals("srv-1", idFront.serverId)
        assertEquals(ScannedDocument.UploadState.ACCEPTED, idFront.uploadState)
        val idBack = idCard.sides.first { it.type == DocumentSide.BACK }
        assertEquals("srv-2", idBack.serverId)
        assertEquals(ScannedDocument.UploadState.REJECTED, idBack.uploadState)

        val passport = restored.documents.first { it.type == "PASSPORT" }
        assertEquals(1, passport.sides.size)
        assertEquals("srv-3", passport.sides.first().serverId)
        assertEquals(ScannedDocument.UploadState.ACCEPTED, passport.sides.first().uploadState)

        assertEquals("ID_CARD", restored.nextDocumentToScan()?.type)
    }

    @Test
    fun v1CacheMigratesToV2() {
        val process = VerificationScanProcess("V1:ID_CARD,PASSPORT")
        assertEquals(2, process.documents.size)
        assertEquals("ID_CARD", process.documents[0].type)
        assertEquals("PASSPORT", process.documents[1].type)
        assertTrue(process.documents.all { it.sides.isEmpty() })

        val v2Cache = process.dataForCache()
        assertTrue(v2Cache.contains("\"v\":2"))
        assertTrue(v2Cache.contains("ID_CARD"))
        assertTrue(v2Cache.contains("PASSPORT"))

        val restored = VerificationScanProcess(v2Cache)
        assertEquals(2, restored.documents.size)
        assertTrue(restored.documents.all { it.sides.isEmpty() })
    }

    @Test(expected = Exception::class)
    fun invalidCacheDataThrows() {
        VerificationScanProcess("garbage")
    }

    @Test
    fun nextDocumentToScanReturnsNullWhenAllAccepted() {
        val process = VerificationScanProcess(types = listOf("PASSPORT"))
        process.feed(
            listOf(
                Document("pp_front.jpg", "srv-1", "PASSPORT", DocumentFileSide.FRONT, DocumentStatus.ACCEPTED, null)
            )
        )

        assertNull(process.nextDocumentToScan())
    }
}
