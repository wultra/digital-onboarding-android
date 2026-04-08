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
import org.junit.Test

class DocumentActionTest {

    @Test
    fun acceptedStatusMapsToProceed() {
        val document = Document("f.jpg", "1", "ID_CARD", DocumentFileSide.FRONT, DocumentStatus.ACCEPTED, null)
        assertEquals(DocumentAction.PROCEED, document.action())
    }

    @Test
    fun rejectedAndFailedMapToError() {
        val rejected = Document("f.jpg", "1", "ID_CARD", DocumentFileSide.FRONT, DocumentStatus.REJECTED, listOf("blur"))
        val failed = Document("f.jpg", "2", "ID_CARD", DocumentFileSide.FRONT, DocumentStatus.FAILED, null)

        assertEquals(DocumentAction.ERROR, rejected.action())
        assertEquals(DocumentAction.ERROR, failed.action())
    }

    @Test
    fun inProgressStatusesMapToWait() {
        val waitingStatuses = listOf(
            DocumentStatus.UPLOAD_IN_PROGRESS,
            DocumentStatus.IN_PROGRESS,
            DocumentStatus.VERIFICATION_PENDING,
            DocumentStatus.VERIFICATION_IN_PROGRESS,
        )

        waitingStatuses.forEach { status ->
            val document = Document("f.jpg", "1", "ID_CARD", DocumentFileSide.FRONT, status, null)
            assertEquals(DocumentAction.WAIT, document.action())
        }
    }
}
