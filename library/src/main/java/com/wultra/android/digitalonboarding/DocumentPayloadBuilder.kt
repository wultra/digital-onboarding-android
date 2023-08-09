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
import com.wultra.android.digitalonboarding.networking.DocumentSubmitFile
import com.wultra.android.digitalonboarding.networking.DocumentSubmitRequestData
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class DocumentPayloadBuilder {

    companion object {

        @Throws
        fun build(processId: String, files: List<DocumentFile>): DocumentSubmitRequestData {
            val zipFile = File.createTempFile(UUID.randomUUID().toString(), ".zip")
            zipFile.deleteOnExit()

            try {
                ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                    files.forEach { file ->
                        zos.putNextEntry(ZipEntry(file.filename()))
                        file.data.inputStream().copyTo(zos)
                    }
                }

                val base64data = Base64.encodeToString(zipFile.readBytes(), Base64.DEFAULT)

                return DocumentSubmitRequestData(
                    processId,
                    base64data,
                    files.any { it.originalDocumentId != null },
                    files.map { it.metadata() }
                )
            } finally {
                zipFile.delete()
            }
        }
    }
}

private fun DocumentFile.filename() = "${type.name.lowercase()}_${side.name.lowercase()}.jpg"

private fun DocumentFile.metadata() = DocumentSubmitFile(filename(), type.apiType(), side.apiType(), originalDocumentId)
