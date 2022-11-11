/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.retrofit.response.attester

import com.flowcrypt.email.api.retrofit.response.base.ApiError
import com.flowcrypt.email.api.retrofit.response.base.ApiResponse
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * Response from the API
 * "https://flowcrypt.com/attester/pub/{id or email}"
 *
 * @author Denis Bondarenko
 * Date: 05.05.2018
 * Time: 14:01
 * E-mail: DenBond7@gmail.com
 */
@Parcelize
data class PubResponse constructor(
  @SerializedName("error")
  @Expose override val apiError: ApiError? = null,
  @Expose val pubkey: String?
) : ApiResponse
