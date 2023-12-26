/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.email.model

import android.os.Parcelable
import com.flowcrypt.email.database.MessageState
import com.flowcrypt.email.database.entity.MessageEntity
import com.flowcrypt.email.model.MessageEncryptionType
import com.flowcrypt.email.model.MessageType
import com.google.gson.annotations.Expose
import jakarta.mail.Flags
import jakarta.mail.internet.InternetAddress
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * Simple POJO class which describe an outgoing message model.
 *
 * @author Denys Bondarenko
 */
@Parcelize
data class OutgoingMessageInfo(
  @Expose val account: String? = null,
  @Expose val subject: String? = null,
  @Expose val msg: String? = null,
  @Expose val toRecipients: List<InternetAddress>? = null,
  @Expose val ccRecipients: List<InternetAddress>? = null,
  @Expose val bccRecipients: List<InternetAddress>? = null,
  @Expose val from: InternetAddress? = null,
  @Expose val atts: List<AttachmentInfo>? = null,
  @Expose val forwardedAtts: List<AttachmentInfo>? = null,
  @Expose val encryptionType: MessageEncryptionType? = null,
  @Expose @MessageType val messageType: Int = MessageType.NEW,
  @Expose val replyToMsgEntity: MessageEntity? = null,
  @Expose val uid: Long = 0,
  @Expose val password: CharArray? = null,
  @Expose val timestamp: Long = System.currentTimeMillis(),
) : Parcelable {

  @IgnoredOnParcel
  val isPasswordProtected = password?.isNotEmpty()

  /**
   * Generate a list of the all recipients.
   *
   * @return A list of the all recipients
   */
  fun getAllRecipients(): List<String> {
    return getPublicRecipients() + getProtectedRecipients()
  }

  fun getPublicRecipients(): List<String> {
    val toAddresses = toRecipients?.map { it.address } ?: emptyList()
    val ccAddresses = ccRecipients?.map { it.address } ?: emptyList()
    return toAddresses + ccAddresses
  }

  fun getProtectedRecipients() = bccRecipients?.map { it.address } ?: emptyList()
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as OutgoingMessageInfo

    if (account != other.account) return false
    if (subject != other.subject) return false
    if (msg != other.msg) return false
    if (toRecipients != other.toRecipients) return false
    if (ccRecipients != other.ccRecipients) return false
    if (bccRecipients != other.bccRecipients) return false
    if (from != other.from) return false
    if (atts != other.atts) return false
    if (forwardedAtts != other.forwardedAtts) return false
    if (encryptionType != other.encryptionType) return false
    if (messageType != other.messageType) return false
    if (replyToMsgEntity != other.replyToMsgEntity) return false
    if (uid != other.uid) return false
    if (password != null) {
      if (other.password == null) return false
      if (!password.contentEquals(other.password)) return false
    } else if (other.password != null) return false
    if (timestamp != other.timestamp) return false
    return isPasswordProtected == other.isPasswordProtected
  }

  override fun hashCode(): Int {
    var result = account?.hashCode() ?: 0
    result = 31 * result + (subject?.hashCode() ?: 0)
    result = 31 * result + (msg?.hashCode() ?: 0)
    result = 31 * result + (toRecipients?.hashCode() ?: 0)
    result = 31 * result + (ccRecipients?.hashCode() ?: 0)
    result = 31 * result + (bccRecipients?.hashCode() ?: 0)
    result = 31 * result + (from?.hashCode() ?: 0)
    result = 31 * result + (atts?.hashCode() ?: 0)
    result = 31 * result + (forwardedAtts?.hashCode() ?: 0)
    result = 31 * result + (encryptionType?.hashCode() ?: 0)
    result = 31 * result + messageType
    result = 31 * result + (replyToMsgEntity?.hashCode() ?: 0)
    result = 31 * result + uid.hashCode()
    result = 31 * result + (password?.contentHashCode() ?: 0)
    result = 31 * result + timestamp.hashCode()
    result = 31 * result + (isPasswordProtected?.hashCode() ?: 0)
    return result
  }

  fun toMessageEntity(
    folder: String,
    flags: Flags,
    password: ByteArray? = null,
  ): MessageEntity {
    return MessageEntity(
      email = requireNotNull(account),
      folder = folder,
      uid = uid,
      fromAddress = from.toString(),
      replyTo = replyToMsgEntity?.replyTo,
      toAddress = toRecipients?.toString(),
      ccAddress = ccRecipients?.toString(),
      subject = subject,
      flags = flags.toString().uppercase(),
      hasAttachments = atts?.isNotEmpty() == true || forwardedAtts?.isNotEmpty() == true,
      isNew = false,
      isEncrypted = encryptionType == MessageEncryptionType.ENCRYPTED,
      state = if (messageType == MessageType.FORWARD) {
        MessageState.NEW_FORWARDED.value
      } else {
        MessageState.NEW.value
      },
      password = password
    )
  }
}
