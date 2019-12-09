/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.database.entity

import android.provider.BaseColumns
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * @author Denis Bondarenko
 *         Date: 12/5/19
 *         Time: 4:22 PM
 *         E-mail: DenBond7@gmail.com
 */
@Entity(tableName = "contacts",
    indices = [
      Index(name = "has_pgp_in_contacts", value = ["has_pgp"]),
      Index(name = "name_in_contacts", value = ["name"]),
      Index(name = "long_id_in_contacts", value = ["long_id"]),
      Index(name = "last_use_in_contacts", value = ["last_use"]),
      Index(name = "email_in_contacts", value = ["email"], unique = true)
    ]
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = BaseColumns._ID) val id: Long,
    val email: String,
    @ColumnInfo(defaultValue = "NULL") val name: String?,
    @ColumnInfo(name = "public_key", defaultValue = "NULL") val publicKey: ByteArray?,
    @ColumnInfo(name = "has_pgp") val hasPgp: Boolean,
    @ColumnInfo(defaultValue = "NULL") val client: String?,
    @ColumnInfo(defaultValue = "NULL") val attested: Boolean?,
    @ColumnInfo(defaultValue = "NULL") val fingerprint: String?,
    @ColumnInfo(name = "long_id", defaultValue = "NULL") val longId: String?,
    @ColumnInfo(defaultValue = "NULL") val keywords: String?,
    @ColumnInfo(name = "last_use", defaultValue = "0") val lastUse: Long
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ContactEntity

    if (id != other.id) return false
    if (email != other.email) return false
    if (name != other.name) return false
    if (publicKey != null) {
      if (other.publicKey == null) return false
      if (!publicKey.contentEquals(other.publicKey)) return false
    } else if (other.publicKey != null) return false
    if (hasPgp != other.hasPgp) return false
    if (client != other.client) return false
    if (attested != other.attested) return false
    if (fingerprint != other.fingerprint) return false
    if (longId != other.longId) return false
    if (keywords != other.keywords) return false
    if (lastUse != other.lastUse) return false

    return true
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + email.hashCode()
    result = 31 * result + (name?.hashCode() ?: 0)
    result = 31 * result + (publicKey?.contentHashCode() ?: 0)
    result = 31 * result + hasPgp.hashCode()
    result = 31 * result + (client?.hashCode() ?: 0)
    result = 31 * result + (attested?.hashCode() ?: 0)
    result = 31 * result + (fingerprint?.hashCode() ?: 0)
    result = 31 * result + (longId?.hashCode() ?: 0)
    result = 31 * result + (keywords?.hashCode() ?: 0)
    result = 31 * result + lastUse.hashCode()
    return result
  }
}