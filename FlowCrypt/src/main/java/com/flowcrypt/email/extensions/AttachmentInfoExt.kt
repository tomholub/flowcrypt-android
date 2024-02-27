/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.extensions

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.flowcrypt.email.Constants
import com.flowcrypt.email.api.email.model.AttachmentInfo
import org.apache.commons.io.FileUtils
import java.io.File

/**
 * @author Denys Bondarenko
 */
fun AttachmentInfo.useFileProviderToGenerateUri(
  context: Context,
  directory: File
): Pair<File, Uri> {
  val fileName = getSafeName()
  val file = File(directory, fileName)
  if (!file.exists()) {
    FileUtils.writeByteArrayToFile(file, rawData)
  }
  val uri = FileProvider.getUriForFile(context, Constants.FILE_PROVIDER_AUTHORITY, file)
  return Pair(file, uri)
}
