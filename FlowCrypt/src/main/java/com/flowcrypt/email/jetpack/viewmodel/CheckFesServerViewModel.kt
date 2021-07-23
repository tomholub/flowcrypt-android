/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.jetpack.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.flowcrypt.email.R
import com.flowcrypt.email.api.email.EmailUtil
import com.flowcrypt.email.api.retrofit.FlowcryptApiRepository
import com.flowcrypt.email.api.retrofit.response.api.FesServerResponse
import com.flowcrypt.email.api.retrofit.response.base.Result
import kotlinx.coroutines.launch

/**
 * @author Denis Bondarenko
 *         Date: 7/23/21
 *         Time: 9:54 AM
 *         E-mail: DenBond7@gmail.com
 */
class CheckFesServerViewModel(application: Application) : BaseAndroidViewModel(application) {
  private val repository = FlowcryptApiRepository()
  val checkFesServerLiveData: MutableLiveData<Result<FesServerResponse>> =
    MutableLiveData(Result.none())

  fun checkFesServerAvailability(account: String) {
    viewModelScope.launch {
      val context: Context = getApplication()
      checkFesServerLiveData.value =
        Result.loading(progressMsg = context.getString(R.string.loading))

      try {
        checkFesServerLiveData.value = repository.checkFes(
          context = getApplication(),
          domain = EmailUtil.getDomain(account)
        )
      } catch (e: Exception) {
        checkFesServerLiveData.value = Result.exception(e)
      }
    }
  }
}
