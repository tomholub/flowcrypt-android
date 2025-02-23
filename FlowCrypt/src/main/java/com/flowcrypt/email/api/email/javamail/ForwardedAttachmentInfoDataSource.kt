/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.email.javamail

import android.content.Context
import com.flowcrypt.email.api.email.model.AttachmentInfo
import com.flowcrypt.email.security.pgp.PgpDecryptAndOrVerify
import com.flowcrypt.email.security.pgp.PgpEncryptAndOrSign
import org.apache.commons.io.FilenameUtils
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.pgpainless.key.protection.SecretKeyRingProtector
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * @author Denys Bondarenko
 */
class ForwardedAttachmentInfoDataSource(
  context: Context,
  att: AttachmentInfo,
  private val shouldBeEncrypted: Boolean,
  private val publicKeys: List<String>? = null,
  private val secretKeys: PGPSecretKeyRingCollection,
  private val protector: SecretKeyRingProtector
) : AttachmentInfoDataSource(context, att) {
  override fun getInputStream(): InputStream? {
    val inputStream = super.getInputStream() ?: return null
    val srcInputStream = if (att.decryptWhenForward) {
      //due to PGPainless(or maybe BC) we have to read a stream fully before using here
      //Because JavaMail tries to identify encoding and does some manipulation with the input stream
      val decryptedBytes = PgpDecryptAndOrVerify.genDecryptionStream(
        srcInputStream = inputStream,
        secretKeys = secretKeys,
        protector = protector
      ).readBytes()

      decryptedBytes.inputStream()
    } else inputStream

    return if (shouldBeEncrypted) {
      //The same situation here.
      //todo-denbond7 it should be improved in the future for better performance
      val tempByteArrayOutputStream = ByteArrayOutputStream()
      PgpEncryptAndOrSign.encryptAndOrSign(
        srcInputStream = srcInputStream,
        destOutputStream = tempByteArrayOutputStream,
        pubKeys = requireNotNull(publicKeys),
        //at this stage we will always have .PGP in the and of the attachment name. Need to drop it.
        fileName = FilenameUtils.removeExtension(name),
      )

      tempByteArrayOutputStream.toByteArray().inputStream()
    } else {
      srcInputStream
    }
  }
}
