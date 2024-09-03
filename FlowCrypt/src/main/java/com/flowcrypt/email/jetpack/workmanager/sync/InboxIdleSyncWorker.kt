/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.jetpack.workmanager.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import com.flowcrypt.email.BuildConfig
import com.flowcrypt.email.api.email.EmailUtil
import com.flowcrypt.email.api.email.FoldersManager
import com.flowcrypt.email.api.email.gmail.GmailApiHelper
import com.flowcrypt.email.api.email.gmail.model.GmailThreadInfo
import com.flowcrypt.email.api.email.model.LocalFolder
import com.flowcrypt.email.database.entity.AccountEntity
import com.flowcrypt.email.database.entity.MessageEntity
import com.flowcrypt.email.database.entity.MessageEntity.Companion.LABEL_IDS_SEPARATOR
import com.flowcrypt.email.extensions.com.google.api.services.gmail.model.hasPgp
import com.flowcrypt.email.extensions.isAppForegrounded
import com.flowcrypt.email.util.exception.GmailAPIException
import com.google.api.services.gmail.model.History
import com.google.api.services.gmail.model.Thread
import jakarta.mail.FetchProfile
import jakarta.mail.Folder
import jakarta.mail.Store
import jakarta.mail.UIDFolder
import jakarta.mail.internet.InternetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.angus.mail.imap.IMAPFolder
import java.math.BigInteger
import java.net.HttpURLConnection

/**
 * This task does a job of loading all new messages which not exist in the cache but exist on the server and updates
 * existing messages in the local database.
 *
 * @author Denys Bondarenko
 */
open class InboxIdleSyncWorker(context: Context, params: WorkerParameters) :
  BaseIdleWorker(context, params) {

  override suspend fun runIMAPAction(accountEntity: AccountEntity, store: Store) {
    syncMessages(accountEntity, store)
  }

  override suspend fun runAPIAction(accountEntity: AccountEntity) {
    syncMessages(accountEntity)
  }

  private suspend fun syncMessages(accountEntity: AccountEntity, store: Store) =
    withContext(Dispatchers.IO) {
      val foldersManager = FoldersManager.fromDatabaseSuspend(applicationContext, accountEntity)
      val inboxLocalFolder = foldersManager.findInboxFolder() ?: return@withContext
      val folderFullName = inboxLocalFolder.fullName

      store.getFolder(folderFullName).use { folder ->
        val remoteFolder = (folder as IMAPFolder).apply { open(Folder.READ_ONLY) }
        val mapOfUIDAndMsgFlags =
          roomDatabase.msgDao().getMapOfUIDAndMsgFlagsSuspend(accountEntity.email, folderFullName)
        val cachedUIDSet = mapOfUIDAndMsgFlags.keys.toSet()

        if (accountEntity.showOnlyEncrypted == true) {
          val foundMsgs = remoteFolder.search(EmailUtil.genPgpThingsSearchTerm(accountEntity))
          val fetchProfile = FetchProfile().apply {
            add(UIDFolder.FetchProfileItem.UID)
          }
          remoteFolder.fetch(foundMsgs, fetchProfile)
          val updatedMsgs = EmailUtil.getUpdatedMsgsByUIDs(remoteFolder, cachedUIDSet.toLongArray())
          processUpdatedMsgs(
            mapOfUIDAndMsgFlags,
            remoteFolder,
            updatedMsgs,
            accountEntity,
            folderFullName
          )
          processDeletedMsgs(cachedUIDSet, remoteFolder, updatedMsgs, accountEntity, folderFullName)

          val newestCachedUID = roomDatabase.msgDao()
            .getLastUIDOfMsgForLabelSuspend(accountEntity.email, folderFullName) ?: return@use
          val newMsgs = EmailUtil.fetchMsgs(
            remoteFolder,
            foundMsgs.filter { message -> remoteFolder.getUID(message) > newestCachedUID }
              .toTypedArray()
          )
          val newCandidates = EmailUtil.genNewCandidates(cachedUIDSet, remoteFolder, newMsgs)
          processNewMsgs(newCandidates, accountEntity, inboxLocalFolder, remoteFolder)
        } else {
          val oldestCachedUID = roomDatabase.msgDao()
            .getOldestUIDOfMsgForLabelSuspend(accountEntity.email, folderFullName) ?: return@use
          val allMsgsFromOldestExisted =
            EmailUtil.getUpdatedMsgsByUID(remoteFolder, oldestCachedUID.toLong(), UIDFolder.LASTUID)
          processDeletedMsgs(
            cachedUIDSet,
            remoteFolder,
            allMsgsFromOldestExisted,
            accountEntity,
            folderFullName
          )
          processUpdatedMsgs(
            mapOfUIDAndMsgFlags,
            remoteFolder,
            allMsgsFromOldestExisted,
            accountEntity,
            folderFullName
          )

          val newCandidates =
            EmailUtil.genNewCandidates(cachedUIDSet, remoteFolder, allMsgsFromOldestExisted)
          processNewMsgs(newCandidates, accountEntity, inboxLocalFolder, remoteFolder)
        }
      }
    }

  private suspend fun syncMessages(accountEntity: AccountEntity) = withContext(Dispatchers.IO) {
    val foldersManager = FoldersManager.fromDatabaseSuspend(applicationContext, accountEntity)
    val inboxLocalFolder = foldersManager.findInboxFolder() ?: return@withContext
    val newestMsg =
      roomDatabase.msgDao().getNewestMsg(account = accountEntity.email, inboxLocalFolder.fullName)
    val labelEntity = roomDatabase.labelDao()
      .getLabelSuspend(accountEntity.email, accountEntity.accountType, inboxLocalFolder.fullName)
    val labelEntityHistoryId = BigInteger(labelEntity?.historyId ?: "0")
    val msgEntityHistoryId = BigInteger(newestMsg?.historyId ?: "0")
    val startHistoryId = labelEntityHistoryId.max(msgEntityHistoryId)

    executeGMailAPICall(applicationContext) {
      if (startHistoryId != BigInteger.ZERO) {
        try {
          val historyList = GmailApiHelper.loadHistoryInfo(
            context = applicationContext,
            accountEntity = accountEntity,
            localFolder = inboxLocalFolder,
            historyId = labelEntityHistoryId.max(msgEntityHistoryId)
          )

          handleMsgsFromHistory(accountEntity, inboxLocalFolder, historyList)
        } catch (e: Exception) {
          if (e is GmailAPIException && e.code == HttpURLConnection.HTTP_NOT_FOUND) {
            /*
           Based on https://developers.google.com/gmail/api/reference/rest/v1/users.history/list

           The supplied startHistoryId should be obtained from the historyId of a message, thread,
           or previous list response. History IDs increase chronologically but are not contiguous
           with random gaps in between valid IDs. Supplying an invalid or out of date
           startHistoryId typically returns an HTTP 404 error code. A historyId is typically valid
           for at least a week, but in some rare circumstances may be valid for only a few hours.
           If an HTTP 404 error response is received, the app should perform a full sync.
           */

            labelEntity?.let { roomDatabase.labelDao().updateSuspend(it.copy(historyId = null)) }
            roomDatabase.msgDao().delete(accountEntity.email, inboxLocalFolder.fullName)
          }
        }
      }
    }
  }

  private suspend fun handleMsgsFromHistory(
    accountEntity: AccountEntity, localFolder: LocalFolder,
    historyList: List<History>
  ) = withContext(Dispatchers.IO) {
    GmailApiHelper.processHistory(localFolder, historyList) { deleteCandidatesUIDs,
                                                              newCandidatesMap,
                                                              updateCandidatesMap,
                                                              labelsToBeUpdatedMap ->
      processDeletedMsgs(accountEntity, localFolder.fullName, deleteCandidatesUIDs)

      val newCandidates = newCandidatesMap.values.toList()
      if (newCandidates.isNotEmpty()) {
        val gmailThreadInfoList: List<GmailThreadInfo>
        val msgs: List<com.google.api.services.gmail.model.Message>

        if (accountEntity.useConversationMode) {
          val uniqueThreadIdList = newCandidates.map { it.threadId }.toSet()
          gmailThreadInfoList = GmailApiHelper.loadGmailThreadInfoInParallel(
            context = applicationContext,
            accountEntity = accountEntity,
            threads = uniqueThreadIdList.map { Thread().apply { id = it } },
            format = GmailApiHelper.RESPONSE_FORMAT_FULL,
            localFolder = localFolder
          )

          msgs = gmailThreadInfoList.map { it.lastMessage }
        } else {
          gmailThreadInfoList = emptyList()
          msgs = GmailApiHelper.loadMsgsInParallel(
            applicationContext, accountEntity,
            newCandidates.toList(), localFolder
          ).run {
            if (accountEntity.showOnlyEncrypted == true) {
              filter { it.hasPgp() }
            } else this
          }
        }

        val isOnlyPgpModeEnabled = accountEntity.showOnlyEncrypted ?: false
        val isNew = applicationContext.isAppForegrounded()

        val msgEntities = MessageEntity.genMessageEntities(
          context = applicationContext,
          account = accountEntity.email,
          accountType = accountEntity.accountType,
          label = localFolder.fullName,
          msgsList = msgs,
          isNew = isNew,
          onlyPgpModeEnabled = isOnlyPgpModeEnabled
        ) { message, messageEntity ->
          if (accountEntity.useConversationMode) {
            val thread = gmailThreadInfoList.firstOrNull { it.id == message.threadId }
            messageEntity.copy(
              subject = thread?.subject,
              threadMessagesCount = thread?.messagesCount,
              labelIds = thread?.labels?.joinToString(separator = LABEL_IDS_SEPARATOR),
              hasAttachments = thread?.hasAttachments,
              threadRecipientsAddresses = InternetAddress.toString(
                thread?.recipients?.toTypedArray()
              ),
              hasPgp = thread?.hasPgpThings
            )
          } else {
            messageEntity
          }
        }

        processNewMsgs(accountEntity, localFolder, msgEntities)
        GmailApiHelper.identifyAttachments(
          msgEntities,
          msgs,
          accountEntity,
          localFolder,
          roomDatabase
        )
      }

      processUpdatedMsgs(accountEntity, localFolder.fullName, updateCandidatesMap)

      roomDatabase.msgDao()
        .updateGmailLabels(accountEntity.email, localFolder.fullName, labelsToBeUpdatedMap)
    }
  }

  companion object {
    const val GROUP_UNIQUE_TAG = BuildConfig.APPLICATION_ID + ".IDLE_SYNC_INBOX"

    fun enqueue(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
      enqueueWithDefaultParameters<InboxIdleSyncWorker>(
        context = context,
        uniqueWorkName = GROUP_UNIQUE_TAG,
        existingWorkPolicy = policy
      )
    }
  }
}
