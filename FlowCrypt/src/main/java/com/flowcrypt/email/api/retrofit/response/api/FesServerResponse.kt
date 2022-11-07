/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.retrofit.response.api

import com.flowcrypt.email.api.retrofit.response.base.ApiError
import com.flowcrypt.email.api.retrofit.response.base.ApiResponse
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * @author Denis Bondarenko
 *         Date: 7/23/21
 *         Time: 8:52 AM
 *         E-mail: DenBond7@gmail.com
 */
@Parcelize
data class FesServerResponse constructor(
  @SerializedName("error")
  @Expose override val apiError: ApiError? = null,
  @Expose val vendor: String?,
  @Expose val service: String?,
  @Expose val orgId: String?,
  @Expose val version: String?,
  @Expose val endUserApiVersion: String?,
  @Expose val adminApiVersion: String?,
) : ApiResponse
