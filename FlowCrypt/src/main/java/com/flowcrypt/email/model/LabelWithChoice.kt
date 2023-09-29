/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.model

/**
 * @author Denys Bondarenko
 */
data class LabelWithChoice(
  val name: String,
  val id: String,
  val backgroundColor: String? = null,
  val textColor: String? = null,
  val isChecked: Boolean
)