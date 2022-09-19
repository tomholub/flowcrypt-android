/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.jetpack.workmanager.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.flowcrypt.email.BuildConfig
import com.flowcrypt.email.api.email.gmail.GmailApiHelper
import com.flowcrypt.email.database.entity.AccountEntity
import com.flowcrypt.email.security.KeyStoreCryptoManager
import com.flowcrypt.email.util.CacheManager
import com.flowcrypt.email.util.FileAndDirectoryUtils
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.DirectoryFileFilter
import org.apache.commons.io.filefilter.TrueFileFilter
import java.io.File
import java.io.FileFilter
import java.util.Properties

class UploadDraftsWorker(context: Context, params: WorkerParameters) :
  BaseSyncWorker(context, params) {
  override suspend fun runIMAPAction(accountEntity: AccountEntity, store: Store) {
    uploadDrafts(accountEntity, store)
  }

  override suspend fun runAPIAction(accountEntity: AccountEntity) {
    uploadDrafts(accountEntity)
  }

  private suspend fun uploadDrafts(account: AccountEntity, store: Store) =
    withContext(Dispatchers.IO) {
      uploadDraftsInternal(account) { draftId, mimeMessage ->

        return@uploadDraftsInternal ""
        //to update IMAP draft we have to delete the old one and add a new one
        /*val foldersManager = FoldersManager.fromDatabaseSuspend(applicationContext, account)
        val folderDrafts = foldersManager.folderDrafts ?: return@uploadDraftsInternal

        store.getFolder(folderDrafts.fullName).use { folder ->
          val draftsFolder = (folder as IMAPFolder).apply { open(Folder.READ_WRITE) }
          draftsFolder.appendMessages(arrayOf<Message>(mimeMessage.apply {
            setFlag(Flags.Flag.DRAFT, true)
            setFlag(Flags.Flag.SEEN, true)
          }))
        }*/
      }
    }

  private suspend fun uploadDrafts(account: AccountEntity) = withContext(Dispatchers.IO) {
    uploadDraftsInternal(account) { draftId, mimeMessage ->
      executeGMailAPICall(applicationContext) {
        return@executeGMailAPICall GmailApiHelper.uploadDraft(
          context = applicationContext,
          account = account,
          mimeMessage = mimeMessage,
          draftId = draftId
        )
      }
    }
  }

  private suspend fun uploadDraftsInternal(
    account: AccountEntity,
    action: suspend (draftId: String?, mimeMessage: MimeMessage) -> String
  ) = withContext(Dispatchers.IO) {
    val draftsDir = CacheManager.getDraftDirectory(applicationContext)
    val directories = draftsDir.listFiles(FileFilter { it.isDirectory }) ?: emptyArray()
    var attemptsCount = 0
    while (attemptsCount < MAX_ATTEMPTS_COUNT && FileUtils.listFiles(
        draftsDir,
        TrueFileFilter.INSTANCE,
        DirectoryFileFilter.DIRECTORY
      ).isNotEmpty()
    ) {
      for (directory in directories) {
        val directoryName = directory.name
        val existingDraftEntity = roomDatabase.draftDao().getDraftEntityById(directoryName)
        if (existingDraftEntity == null) {
          FileAndDirectoryUtils.deleteDir(directory)
          continue
        }
        val originalDraftId = existingDraftEntity.draftId
        val drafts = directory.listFiles(FileFilter { it.isFile }) ?: emptyArray()
        try {
          val lastVersion = drafts.maxBy { it.lastModified() }
          val inputStream = KeyStoreCryptoManager.getCipherInputStream(lastVersion.inputStream())
          val mimeMessage = MimeMessage(Session.getInstance(Properties()), inputStream)
          val draftId = action.invoke(originalDraftId, mimeMessage)
          if (originalDraftId == null) {
            roomDatabase.draftDao().updateSuspend(existingDraftEntity.copy(draftId = draftId))
          }

          drafts.forEach { FileAndDirectoryUtils.deleteFile(it) }
          if ((directory.listFiles() ?: emptyArray<File>()).isEmpty()) {
            FileAndDirectoryUtils.deleteDir(directory)
          }
        } catch (e: Exception) {
          e.printStackTrace()

          if (e.cause is GoogleJsonResponseException) {
            val isNotDraft = (e.cause as GoogleJsonResponseException).details.errors.any {
              it.message == "Message not a draft"
            }

            val isNotActual = (e.cause as GoogleJsonResponseException).details.errors.any {
              it.message == "Requested entity was not found."
            }

            if (isNotDraft || isNotActual) {
              /*
              it means the draft was discarded
              or a message has been sent
              or updating the draft with local changes is not actual
              */
              roomDatabase.draftDao().deleteSuspend(existingDraftEntity)
              FileAndDirectoryUtils.deleteDir(directory)
            }
          }

          continue
        }
      }

      attemptsCount++
    }
  }

  companion object {
    const val GROUP_UNIQUE_TAG = BuildConfig.APPLICATION_ID + ".UPLOAD_DRAFTS"
    const val MAX_ATTEMPTS_COUNT = 10

    fun enqueue(context: Context) {
      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

      WorkManager
        .getInstance(context.applicationContext)
        .enqueueUniqueWork(
          GROUP_UNIQUE_TAG,
          ExistingWorkPolicy.APPEND,
          OneTimeWorkRequestBuilder<UploadDraftsWorker>()
            .addTag(TAG_SYNC)
            .setConstraints(constraints)
            .build()
        )
    }
  }
}
