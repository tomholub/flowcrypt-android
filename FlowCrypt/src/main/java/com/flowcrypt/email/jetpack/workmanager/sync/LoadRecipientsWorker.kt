/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.jetpack.workmanager.sync

import android.content.Context
import android.text.TextUtils
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import com.flowcrypt.email.BuildConfig
import com.flowcrypt.email.api.email.FoldersManager
import com.flowcrypt.email.api.email.gmail.GmailApiHelper
import com.flowcrypt.email.api.email.gmail.api.GmaiAPIMimeMessage
import com.flowcrypt.email.database.FlowCryptRoomDatabase
import com.flowcrypt.email.database.entity.AccountEntity
import com.flowcrypt.email.database.entity.RecipientEntity
import com.flowcrypt.email.extensions.kotlin.asInternetAddresses
import org.eclipse.angus.mail.imap.IMAPFolder
import jakarta.mail.FetchProfile
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.internet.InternetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Properties

/**
 * This [CoroutineWorker] loads information about recipients from the SENT folder.
 *
 * @author Denys Bondarenko
 */
class LoadRecipientsWorker(context: Context, params: WorkerParameters) :
  BaseSyncWorker(context, params) {
  override suspend fun runIMAPAction(accountEntity: AccountEntity, store: Store) {
    fetchContacts(accountEntity, store)
  }

  override suspend fun runAPIAction(accountEntity: AccountEntity) {
    fetchContacts(accountEntity)
  }

  private suspend fun fetchContacts(account: AccountEntity, store: Store) =
    withContext(Dispatchers.IO) {
      fetchContactsInternal(account) {
        val foldersManager = FoldersManager.fromDatabaseSuspend(applicationContext, account)
        val folderSent =
          foldersManager.findSentFolder() ?: return@fetchContactsInternal emptyArray()

        store.getFolder(folderSent.fullName).use { folder ->
          val imapFolder = (folder as IMAPFolder).apply { open(Folder.READ_ONLY) }
          val msgs = imapFolder.messages

          if (msgs.isNotEmpty()) {
            val fetchProfile = FetchProfile()
            fetchProfile.add(Message.RecipientType.TO.toString().uppercase())
            fetchProfile.add(Message.RecipientType.CC.toString().uppercase())
            fetchProfile.add(Message.RecipientType.BCC.toString().uppercase())
            imapFolder.fetch(msgs, fetchProfile)

            return@fetchContactsInternal msgs
          }
        }

        return@fetchContactsInternal emptyArray()
      }
    }

  private suspend fun fetchContacts(account: AccountEntity) = withContext(Dispatchers.IO) {
    fetchContactsInternal(account) {
      val foldersManager = FoldersManager.fromDatabaseSuspend(applicationContext, account)
      val folderSent = foldersManager.findSentFolder() ?: return@fetchContactsInternal emptyArray()

      executeGMailAPICall(applicationContext) {
        val gmailApiService = GmailApiHelper.generateGmailApiService(applicationContext, account)

        var response = gmailApiService
          .users()
          .messages()
          .list(GmailApiHelper.DEFAULT_USER_ID)
          .setLabelIds(listOf(GmailApiHelper.LABEL_SENT))
          .execute()

        val msgsBaseInfo = mutableListOf<com.google.api.services.gmail.model.Message>()

        //Try to load the last 200 messages. Only base info
        while (response.messages != null) {
          //to prevent limit exception we can do it once per 1 seconds
          delay(1000)
          msgsBaseInfo.addAll(response.messages)
          if (msgsBaseInfo.size < MAX_MSGS_COUNT && response.nextPageToken != null) {
            response = gmailApiService
              .users()
              .messages()
              .list(GmailApiHelper.DEFAULT_USER_ID)
              .setPageToken(response.nextPageToken)
              .execute()
          } else {
            break
          }
        }

        val list = mutableListOf<com.google.api.services.gmail.model.Message>()
        list.addAll(
          GmailApiHelper.doOperationViaStepsSuspend(
            stepValue = 50,
            list = msgsBaseInfo
          ) { subList ->
            //to prevent limit exception we can do it once per 1 seconds
            delay(1000)
            GmailApiHelper.loadMsgs(
              context = applicationContext,
              accountEntity = account,
              messages = subList,
              localFolder = folderSent,
              format = GmailApiHelper.MESSAGE_RESPONSE_FORMAT_METADATA,
              metadataHeaders = listOf("To", "Cc"),
              fields = listOf("payload")
            )
          })

        val session = Session.getInstance(Properties())
        list.map { GmaiAPIMimeMessage(session, it) }.toTypedArray()
      }
    }
  }

  private suspend fun fetchContactsInternal(
    account: AccountEntity,
    action: suspend () -> Array<Message>
  ) = withContext(Dispatchers.IO) {
    if (account.contactsLoaded == true) return@withContext
    val msgs = action.invoke()

    if (msgs.isNotEmpty()) {
      updateContacts(msgs)
      FlowCryptRoomDatabase.getDatabase(applicationContext).accountDao()
        .updateAccountSuspend(account.copy(contactsLoaded = true))
    }
  }

  private suspend fun updateContacts(msgs: Array<Message>) = withContext(Dispatchers.IO) {
    val emailAndNamePairs = mutableListOf<Pair<String, String>>()
    for (msg in msgs) {
      emailAndNamePairs.addAll(parseRecipients(msg, Message.RecipientType.TO))
      emailAndNamePairs.addAll(parseRecipients(msg, Message.RecipientType.CC))
      emailAndNamePairs.addAll(parseRecipients(msg, Message.RecipientType.BCC))
    }

    val recipientDao = FlowCryptRoomDatabase.getDatabase(applicationContext).recipientDao()
    val availableRecipients = recipientDao.getAllRecipients()

    val recipientsInDatabase = HashSet<String>()
    val recipientsToUpdate = HashSet<String>()
    val recipientsToCreate = HashSet<String>()
    val recipientsByEmailMap = HashMap<String, RecipientEntity?>()

    val newCandidates = mutableListOf<RecipientEntity>()
    val updateCandidates = mutableListOf<RecipientEntity>()

    for (recipientEntity in availableRecipients) {
      recipientsInDatabase.add(recipientEntity.email.lowercase())
      recipientsByEmailMap[recipientEntity.email.lowercase()] = recipientEntity
    }

    for (emailAndNamePair in emailAndNamePairs) {
      if (recipientsInDatabase.contains(emailAndNamePair.first)) {
        val recipientEntity = recipientsByEmailMap[emailAndNamePair.first]
        if (recipientEntity?.email.isNullOrEmpty()) {
          if (!recipientsToUpdate.contains(emailAndNamePair.first)) {
            recipientsToUpdate.add(emailAndNamePair.first)
            recipientEntity?.copy(name = emailAndNamePair.second)?.let { updateCandidates.add(it) }
          }
        }
      } else {
        if (!recipientsToCreate.contains(emailAndNamePair.first)) {
          recipientsToCreate.add(emailAndNamePair.first)
          newCandidates.add(
            RecipientEntity(
              email = emailAndNamePair.first,
              name = emailAndNamePair.second
            )
          )
        }
      }
    }

    recipientDao.updateSuspend(updateCandidates)
    recipientDao.insertSuspend(newCandidates)
  }

  /**
   * Generate an array of [Pair] objects from the input message.
   * This information will be retrieved from "to", "cc" or "bcc" headers.
   *
   * @param msg           The input [Message].
   * @param recipientType The input [Message.RecipientType].
   * @return An array of [Pair] objects, which contains information about emails and names.
   */
  private suspend fun parseRecipients(
    msg: Message?,
    recipientType: Message.RecipientType?
  ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
    if (msg != null && recipientType != null) {
      try {
        val header = msg.getHeader(recipientType.toString()) ?: return@withContext emptyList()
        if (header.isNotEmpty()) {
          if (!TextUtils.isEmpty(header[0])) {
            val addresses = header[0].asInternetAddresses()
            val emailAndNamePairs = mutableListOf<Pair<String, String>>()
            for (address in addresses) {
              emailAndNamePairs.add(Pair(address.address.lowercase(), address.personal))
            }

            return@withContext emailAndNamePairs
          }
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }

      return@withContext emptyList()
    } else {
      return@withContext emptyList()
    }
  }

  companion object {
    const val GROUP_UNIQUE_TAG = BuildConfig.APPLICATION_ID + ".FETCH_CONTACTS"
    private const val MAX_MSGS_COUNT = 200

    fun enqueue(context: Context) {
      enqueueWithDefaultParameters<LoadRecipientsWorker>(
        context = context,
        uniqueWorkName = GROUP_UNIQUE_TAG,
        existingWorkPolicy = ExistingWorkPolicy.KEEP
      )
    }
  }
}
