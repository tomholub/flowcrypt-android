/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.service.actionqueue.actions

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Must be run in non-UI thread. This class describes an action which will be run on a queue.
 *
 * @author Denis Bondarenko
 * Date: 29.01.2018
 * Time: 16:56
 * E-mail: DenBond7@gmail.com
 */
interface Action : Parcelable {
  var id: Long
  val email: String?
  val version: Int
  val type: Type

  suspend fun run(context: Context)

  /**
   * This class contains information about all action types.
   */
  @Parcelize
  enum class Type constructor(val value: String) : Parcelable {
    NONE("none"),
    BACKUP_PRIVATE_KEY_TO_INBOX("backup_private_key_to_inbox"),
    ENCRYPT_PRIVATE_KEYS("encrypt_private_keys"),
    LOAD_GMAIL_ALIASES("load_gmail_aliases");

    companion object {
      @JvmStatic
      fun generate(code: String): Type? {
        for (messageState in values()) {
          if (messageState.value == code) {
            return messageState
          }
        }

        return null
      }
    }
  }

  companion object {
    const val TAG_NAME_ACTION_TYPE = "actionType"
  }

  //@Parcelize
  data class None(override var id: Long = 0) : Action {
    override val email: String? = null
    override val version: Int = 0
    override val type: Type = Type.NONE

    constructor(parcel: Parcel) : this(parcel.readLong())

    override suspend fun run(context: Context) {}
    override fun writeToParcel(parcel: Parcel, flags: Int) {
      parcel.writeLong(id)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<None> {
      override fun createFromParcel(parcel: Parcel): None = None(parcel)
      override fun newArray(size: Int): Array<None?> = arrayOfNulls(size)
    }
  }
}
