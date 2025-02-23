/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.database.entity

import android.os.Parcelable
import android.provider.BaseColumns
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import jakarta.mail.internet.InternetAddress
import kotlinx.parcelize.Parcelize

/**
 * @author Denys Bondarenko
 */
@Entity(
  tableName = "recipients",
  indices = [
    Index(name = "name_in_recipients", value = ["name"]),
    Index(name = "last_use_in_recipients", value = ["last_use"]),
    Index(name = "email_in_recipients", value = ["email"], unique = true)
  ]
)
@Parcelize
data class RecipientEntity(
  @PrimaryKey(autoGenerate = true) @ColumnInfo(name = BaseColumns._ID) val id: Long? = null,
  val email: String,
  @ColumnInfo(defaultValue = "NULL") val name: String? = null,
  @ColumnInfo(name = "last_use", defaultValue = "0") val lastUse: Long = 0
) : Parcelable {
  fun toInternetAddress(): InternetAddress {
    return InternetAddress(email, name)
  }

  @Parcelize
  data class WithPgpMarker(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = BaseColumns._ID) val id: Long? = null,
    val email: String,
    @ColumnInfo(defaultValue = "NULL") val name: String? = null,
    @ColumnInfo(name = "last_use", defaultValue = "0") val lastUse: Long = 0,
    @ColumnInfo(name = "has_pgp", defaultValue = "0") val hasPgp: Boolean,
  ) : Parcelable {
    fun toRecipientEntity(): RecipientEntity {
      return RecipientEntity(id = id, email = email, name = name, lastUse = lastUse)
    }
  }
}
