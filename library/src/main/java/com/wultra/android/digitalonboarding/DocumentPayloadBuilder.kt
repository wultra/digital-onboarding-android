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

package com.wultra.android.digitalonboarding

import android.util.Base64
import com.wultra.android.digitalonboarding.networking.model.DocumentSubmitFile
import com.wultra.android.digitalonboarding.networking.model.DocumentSubmitRequestData

internal class DocumentPayloadBuilder {

    companion object {
        fun build(processId: String, files: List<DocumentFile>) = DocumentSubmitRequestData(
            processId = processId,
            resubmit = files.any { it.originalDocumentId != null },
            documents = files.map { it.toSubmitFile() }
        )
    }
}

private fun DocumentFile.toSubmitFile() = DocumentSubmitFile(
    filename = filename(),
    type = type,
    side = side.apiType(),
    data = dataUrlSafe(),
    originalDocumentId = originalDocumentId
)
private fun DocumentFile.filename() = "${type.lowercase()}_${side.name.lowercase()}.jpg"
private fun DocumentFile.dataUrlSafe() =
    Base64.encodeToString(data, Base64.NO_PADDING or Base64.NO_WRAP)
