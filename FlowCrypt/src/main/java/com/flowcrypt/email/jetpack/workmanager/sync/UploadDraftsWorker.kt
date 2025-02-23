/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.jetpack.workmanager.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.flowcrypt.email.BuildConfig
import com.flowcrypt.email.api.email.gmail.GmailApiHelper
import com.flowcrypt.email.database.MessageState
import com.flowcrypt.email.database.entity.AccountEntity
import com.flowcrypt.email.database.entity.MessageEntity
import com.flowcrypt.email.extensions.threadIdAsLong
import com.flowcrypt.email.extensions.uid
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

/**
 * @author Denys Bondarenko
 */
class UploadDraftsWorker(context: Context, params: WorkerParameters) :
  BaseSyncWorker(context, params) {
  override suspend fun runIMAPAction(accountEntity: AccountEntity, store: Store) {
    uploadDrafts()
  }

  override suspend fun runAPIAction(accountEntity: AccountEntity) {
    uploadDrafts(accountEntity)
  }

  private suspend fun uploadDrafts() =
    withContext(Dispatchers.IO) {
      uploadDraftsInternal { _, _ ->
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
    uploadDraftsInternal { messageEntity, mimeMessage ->
      executeGMailAPICall(applicationContext) {
        val draftId = messageEntity.draftId
        val draft = GmailApiHelper.uploadDraft(
          context = applicationContext,
          account = account,
          mimeMessage = mimeMessage,
          draftId = draftId,
          threadId = messageEntity.threadIdAsHEX
        )

        val message = GmailApiHelper.loadMsgInfoSuspend(
          context = applicationContext,
          accountEntity = account,
          msgId = draft.message.id,
          fields = listOf(
            "id",
            "threadId",
            "historyId"
          ),
          format = GmailApiHelper.RESPONSE_FORMAT_FULL
        )

        val freshestMessageEntity =
          roomDatabase.msgDao().getMsgById(messageEntity.id ?: Long.MIN_VALUE)

        val messageEntityWithoutStateChange = messageEntity.copy(
          uid = message.uid,
          threadId = message.threadIdAsLong,
          draftId = draft.id,
          historyId = message.historyId.toString()
        )

        roomDatabase.msgDao().updateSuspend(
          if (MessageState.PENDING_DELETING_DRAFT.value == freshestMessageEntity?.state) {
            messageEntityWithoutStateChange
          } else {
            messageEntityWithoutStateChange.copy(state = MessageState.NONE.value)
          }
        )
      }
    }
  }

  private suspend fun uploadDraftsInternal(
    action: suspend (messageEntity: MessageEntity, mimeMessage: MimeMessage) -> Unit
  ) = withContext(Dispatchers.IO) {
    val draftsDir = CacheManager.getDraftDirectory(applicationContext)
    val directories = draftsDir.listFiles(FileFilter { it.isDirectory }) ?: emptyArray()
    var attemptsCount = 0
    val setOfThreadToBeAffected = mutableSetOf<Long>()
    while (attemptsCount < MAX_ATTEMPTS_COUNT && FileUtils.listFiles(
        draftsDir,
        TrueFileFilter.INSTANCE,
        DirectoryFileFilter.DIRECTORY
      ).isNotEmpty()
    ) {
      for (directory in directories) {
        val directoryName = directory.name
        val existingDraftEntity = roomDatabase.msgDao().getMsgById(directoryName.toLong())

        if (existingDraftEntity == null) {
          FileAndDirectoryUtils.deleteDir(directory)
          continue
        }

        val activeAccount = roomDatabase.accountDao().getActiveAccountSuspend()
        if (activeAccount != null) {
          val outgoingMessages = roomDatabase.msgDao().getOutboxMsgsSuspend(activeAccount.email)
          if (outgoingMessages.any { it.uid == existingDraftEntity.uid }) {
            //it looks like a user sent a message for this draft
            FileAndDirectoryUtils.deleteDir(directory)
            continue
          }
        }

        val drafts = directory.listFiles(FileFilter { it.isFile }) ?: emptyArray()
        try {
          setProgress(
            workDataOf(
              EXTRA_KEY_MESSAGE_UID to existingDraftEntity.uid,
              EXTRA_KEY_THREAD_ID to existingDraftEntity.threadId,
              EXTRA_KEY_STATE to STATE_UPLOADING
            )
          )
          val lastVersion = drafts.maxBy { it.lastModified() }
          val inputStream = KeyStoreCryptoManager.getCipherInputStream(lastVersion.inputStream())
          val mimeMessage = MimeMessage(Session.getInstance(Properties()), inputStream)
          action.invoke(existingDraftEntity, mimeMessage)
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
              roomDatabase.msgDao().deleteSuspend(existingDraftEntity)
              FileAndDirectoryUtils.deleteDir(directory)
            }
          }

          continue
        } finally {
          setProgress(
            workDataOf(
              EXTRA_KEY_MESSAGE_UID to existingDraftEntity.uid,
              EXTRA_KEY_THREAD_ID to existingDraftEntity.threadId,
              EXTRA_KEY_STATE to STATE_UPLOAD_COMPLETED
            )
          )

          existingDraftEntity.threadId?.let { setOfThreadToBeAffected.add(it) }
        }
      }

      attemptsCount++
    }

    setProgress(
      workDataOf(
        EXTRA_KEY_STATE to STATE_COMMON_UPLOAD_COMPLETED,
        EXTRA_KEY_THREAD_ID_LIST to setOfThreadToBeAffected.toTypedArray(),
      )
    )
  }

  companion object {
    const val EXTRA_KEY_MESSAGE_UID = "MESSAGE_UID"
    const val EXTRA_KEY_THREAD_ID = "THREAD_ID"
    const val EXTRA_KEY_THREAD_ID_LIST = "THREAD_ID_LIST"
    const val EXTRA_KEY_STATE = "STATE"

    const val STATE_UPLOADING = 0
    const val STATE_UPLOAD_COMPLETED = 1
    const val STATE_COMMON_UPLOAD_COMPLETED = 2

    const val GROUP_UNIQUE_TAG = BuildConfig.APPLICATION_ID + ".UPLOAD_DRAFTS"
    const val MAX_ATTEMPTS_COUNT = 10

    fun enqueue(context: Context) {
      enqueueWithDefaultParameters<UploadDraftsWorker>(
        context = context,
        uniqueWorkName = GROUP_UNIQUE_TAG,
        existingWorkPolicy = ExistingWorkPolicy.KEEP
      )
    }
  }
}
