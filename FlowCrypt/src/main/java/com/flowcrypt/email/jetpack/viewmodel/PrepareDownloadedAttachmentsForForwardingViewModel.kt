/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.jetpack.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.flowcrypt.email.R
import com.flowcrypt.email.api.email.model.AttachmentInfo
import com.flowcrypt.email.api.retrofit.response.base.Result
import com.flowcrypt.email.extensions.java.lang.printStackTraceIfDebugOnly
import com.flowcrypt.email.providers.EmbeddedAttachmentsProvider
import com.flowcrypt.email.util.coroutines.runners.ControlledRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FilenameUtils

/**
 * @author Denys Bondarenko
 */
class PrepareDownloadedAttachmentsForForwardingViewModel(
  private val attachments: Array<AttachmentInfo>,
  application: Application
) : DecryptDataViewModel(application) {
  private val controlledRunnerForPreparingAttachmentsForForwarding =
    ControlledRunner<Result<List<AttachmentInfo>>>()
  private val preparingAttachmentsForForwardingMutableStateFlow: MutableStateFlow<Result<List<AttachmentInfo>>> =
    MutableStateFlow(Result.none())
  val preparingAttachmentsForForwardingStateFlow: StateFlow<Result<List<AttachmentInfo>>> =
    preparingAttachmentsForForwardingMutableStateFlow.asStateFlow()

  fun prepare() {
    viewModelScope.launch {
      val context: Context = getApplication()
      preparingAttachmentsForForwardingMutableStateFlow.value =
        Result.loading(progressMsg = context.getString(R.string.processing_please_wait))
      preparingAttachmentsForForwardingMutableStateFlow.value =
        controlledRunnerForPreparingAttachmentsForForwarding.cancelPreviousThenRun {
          return@cancelPreviousThenRun prepareInternal(context)
        }
    }
  }

  private suspend fun prepareInternal(context: Context): Result<List<AttachmentInfo>> =
    withContext(Dispatchers.IO) {
      val list = mutableListOf<AttachmentInfo>()
      val embeddedAttachmentsCache = EmbeddedAttachmentsProvider.Cache.getInstance()

      for (attachmentInfo in attachments) {
        try {
          if (attachmentInfo.uri == null) {
            throw NullPointerException("attachmentInfo.uri == null")
          }

          val existingDocumentIdForDecryptedVersion = embeddedAttachmentsCache
            .getDocumentId(attachmentInfo.copy(name = FilenameUtils.getBaseName(attachmentInfo.name)))

          if (existingDocumentIdForDecryptedVersion != null) {
            val existingDecryptedVersion =
              embeddedAttachmentsCache.getUriVersion(existingDocumentIdForDecryptedVersion)

            if (existingDecryptedVersion != null) {
              list.add(existingDecryptedVersion)
              continue
            }
          }

          val originalAttName = attachmentInfo.getSafeName()

          val inputStream =
            context.contentResolver.openInputStream(attachmentInfo.uri)
              ?: throw IllegalStateException("Uri is not defined")
          val decryptedData = decryptDataIfNeeded(context, inputStream)

          /*list.add(
            attachmentInfo.copy(
              rawData = null,
              type = Constants.MIME_TYPE_BINARY_DATA,
              uri = uri,
              name = tempFile.name
            )
          )*/
        } catch (e: Exception) {
          e.printStackTraceIfDebugOnly()
        }
      }

      return@withContext Result.success(list)
    }
}