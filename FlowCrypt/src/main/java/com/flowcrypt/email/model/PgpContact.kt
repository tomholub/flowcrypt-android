/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.model

import android.os.Parcel
import android.os.Parcelable
import com.flowcrypt.email.database.entity.PublicKeyEntity
import com.flowcrypt.email.database.entity.RecipientEntity
import com.flowcrypt.email.security.model.PgpKeyDetails
import java.util.ArrayList
import javax.mail.internet.AddressException
import javax.mail.internet.InternetAddress

data class PgpContact constructor(
  var email: String,
  var name: String? = null,
  var pubkey: String? = null,
  var hasPgp: Boolean = false,
  var client: String? = null,
  var fingerprint: String? = null,
  var lastUse: Long = 0,
  var pgpKeyDetails: PgpKeyDetails? = null,
  var hasNotUsablePubKey: Boolean = false
) : Parcelable {

  constructor(source: Parcel) : this(
    source.readString()!!,
    source.readString(),
    source.readString(),
    source.readInt() == 1,
    source.readString(),
    source.readString(),
    source.readLong(),
    source.readParcelable(PgpKeyDetails::class.java.classLoader),
    source.readInt() == 1
  )

  constructor(email: String, name: String?) : this(email) {
    this.name = name
  }

  override fun describeContents(): Int {
    return 0
  }

  override fun writeToParcel(dest: Parcel, flags: Int) =
    with(dest) {
      writeString(email)
      writeString(name)
      writeString(pubkey)
      writeInt((if (hasPgp) 1 else 0))
      writeString(client)
      writeString(fingerprint)
      writeLong(lastUse)
      writeParcelable(pgpKeyDetails, flags)
      writeInt((if (hasNotUsablePubKey) 1 else 0))
    }

  fun toContactEntity(): RecipientEntity {
    return RecipientEntity(
      email = email.lowercase(),
      name = name,
      lastUse = lastUse
    )
  }

  fun toPubKey(): PublicKeyEntity {
    return PublicKeyEntity(
      recipient = email,
      fingerprint = fingerprint!!,
      publicKey = pubkey!!.toByteArray()
    )
  }

  companion object {
    @JvmField
    val CREATOR: Parcelable.Creator<PgpContact> = object : Parcelable.Creator<PgpContact> {
      override fun createFromParcel(source: Parcel): PgpContact = PgpContact(source)
      override fun newArray(size: Int): Array<PgpContact?> = arrayOfNulls(size)
    }

    fun determinePgpContacts(users: List<String>): ArrayList<PgpContact> {
      val pgpContacts = ArrayList<PgpContact>()
      for (user in users) {
        try {
          val internetAddresses = InternetAddress.parse(user)

          for (internetAddress in internetAddresses) {
            val email = internetAddress.address.lowercase()
            val name = internetAddress.personal

            pgpContacts.add(PgpContact(email, name))
          }
        } catch (e: AddressException) {
          e.printStackTrace()
        }
      }

      return pgpContacts
    }
  }
}
