/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity.fragment

import android.content.Context
import android.graphics.ColorFilter
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.addTextChangedListener
import androidx.navigation.fragment.navArgs
import com.flowcrypt.email.R
import com.flowcrypt.email.databinding.FragmentCheckPassphraseStrengthBinding
import com.flowcrypt.email.extensions.navController
import com.flowcrypt.email.ui.activity.fragment.base.BasePassphraseStrengthFragment
import com.flowcrypt.email.ui.notifications.SystemNotificationManager
import com.flowcrypt.email.util.UIUtil

/**
 * This fragment does a reliability check of a provided passphrase.
 *
 * @author Denis Bondarenko
 * Date: 05.08.2018
 * Time: 20:15
 * E-mail: DenBond7@gmail.com
 */
class CheckPassphraseStrengthFragment : BasePassphraseStrengthFragment() {
  private val args by navArgs<CheckPassphraseStrengthFragmentArgs>()
  private var binding: FragmentCheckPassphraseStrengthBinding? = null

  override val contentResourceId: Int = R.layout.fragment_check_passphrase_strength
  override val isToolbarVisible: Boolean = false

  override fun onAttach(context: Context) {
    super.onAttach(context)
    SystemNotificationManager(context)
      .cancel(SystemNotificationManager.NOTIFICATION_ID_PASSPHRASE_TOO_WEAK)
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View? {
    binding = FragmentCheckPassphraseStrengthBinding.inflate(inflater, container, false)
    return binding?.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    supportActionBar?.title = getString(R.string.security)
    initViews()
    initPasswordStrengthViewModel()
  }

  override fun onButtonContinueColorChanged(colorFilter: ColorFilter) {
    binding?.btSetPassphrase?.background?.colorFilter = colorFilter
  }

  override fun onPassphraseQualityChanged(progress: Int) {
    binding?.pBarPassphraseQuality?.progress = progress
  }

  override fun onPassphraseQualityProgressDrawableColorChanged(colorFilter: ColorFilter) {
    binding?.pBarPassphraseQuality?.progressDrawable?.colorFilter = colorFilter
  }

  override fun onPassphraseQualityTextChanged(charSequence: CharSequence) {
    binding?.tVPassphraseQuality?.text = charSequence
  }

  override fun onContinue() {
    navController?.navigate(
      CheckPassphraseStrengthFragmentDirections
        .actionCheckPassphraseStrengthFragmentToRecheckProvidedPassphraseFragment(
          popBackStackIdIfSuccess = args.popBackStackIdIfSuccess,
          title = args.title,
          passphrase = binding?.eTPassphrase?.text.toString()
        )
    )
  }

  private fun initViews() {
    binding?.tVTitle?.text = args.title
    binding?.tVLostPassphraseWarning?.text = args.lostPassphraseTitle
    binding?.iBShowPasswordHint?.setOnClickListener {
      showPassphraseHint()
    }

    binding?.eTPassphrase?.addTextChangedListener { editable ->
      val passphrase = editable.toString()
      passwordStrengthViewModel.check(passphrase)
      if (TextUtils.isEmpty(editable)) {
        binding?.tVPassphraseQuality?.setText(R.string.passphrase_must_be_non_empty)
      }
    }

    binding?.eTPassphrase?.setOnEditorActionListener { v, actionId, _ ->
      return@setOnEditorActionListener when (actionId) {
        EditorInfo.IME_ACTION_DONE -> {
          checkAndMoveOn(binding?.eTPassphrase?.text, binding?.root)
          UIUtil.hideSoftInput(requireContext(), v)
          true
        }
        else -> false
      }
    }

    binding?.btSetPassphrase?.setOnClickListener {
      checkAndMoveOn(binding?.eTPassphrase?.text, binding?.root)
    }
  }
}
