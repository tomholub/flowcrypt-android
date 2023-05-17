/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.ui.activity.fragment.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.navArgs
import com.flowcrypt.email.R
import com.flowcrypt.email.databinding.FragmentDialogKeyDetailsBinding
import com.flowcrypt.email.extensions.visible
import com.flowcrypt.email.util.DateTimeUtil
import com.flowcrypt.email.util.GeneralUtil
import java.util.Date

/**
 * @author Denys Bondarenko
 */
class UpdatePrivateKeyDialogFragment : BaseDialogFragment() {
  private var binding: FragmentDialogKeyDetailsBinding? = null
  private val args by navArgs<UpdatePrivateKeyDialogFragmentArgs>()

  @SuppressLint("SetTextI18n")
  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    binding = FragmentDialogKeyDetailsBinding.inflate(
      LayoutInflater.from(context),
      if ((view != null) and (view is ViewGroup)) view as ViewGroup? else null,
      false
    )

    initViews()

    val builder = AlertDialog.Builder(requireContext()).apply {
      setTitle("New Private Key details")
      setView(binding?.root)

      setPositiveButton(R.string.use_this_key) { _, _ ->
        setFragmentResult(
          args.requestKey,
          bundleOf(KEY_NEW_PRIVATE_KEY to args.newPgpKeyDetails)
        )
      }

      setNegativeButton(R.string.cancel) { _, _ -> }
    }
    return builder.create()
  }

  @SuppressLint("SetTextI18n")
  private fun initViews() {
    var isExpectedEmailFound = false

    if (args.newPgpKeyDetails.mimeAddresses.isEmpty()) {
      args.newPgpKeyDetails.users.forEach { user ->
        val userLayout =
          layoutInflater.inflate(R.layout.item_user_with_email, binding?.lUsers, false)
        val tVUserName = userLayout.findViewById<TextView>(R.id.tVUserName)
        tVUserName.text = user
        binding?.lUsers?.addView(userLayout)
        isExpectedEmailFound = user.contains(
          args.existingPgpKeyDetails.getPrimaryInternetAddress()?.address ?: "",
          ignoreCase = true
        )
      }
    } else {
      args.newPgpKeyDetails.mimeAddresses.forEach { address ->
        val userLayout =
          layoutInflater.inflate(R.layout.item_user_with_email, binding?.lUsers, false)
        val tVUserName = userLayout.findViewById<TextView>(R.id.tVUserName)
        tVUserName.text = address.personal
        val tVEmail = userLayout.findViewById<TextView>(R.id.tVEmail)
        tVEmail.text = address.address
        binding?.lUsers?.addView(userLayout)
        isExpectedEmailFound = address.address.equals(
          args.existingPgpKeyDetails.getPrimaryInternetAddress()?.address,
          true
        )
      }
    }

    if (!isExpectedEmailFound) {
      binding?.tVWarning?.visible()
      binding?.tVWarning?.text =
        getString(
          R.string.warning_no_expected_email,
          args.newPgpKeyDetails.getPrimaryInternetAddress()?.address
        )
    }

    args.newPgpKeyDetails.ids.forEach { uid ->
      val tVFingerprint = TextView(context)
      tVFingerprint.setTextSize(
        TypedValue.COMPLEX_UNIT_PX,
        resources.getDimension(R.dimen.default_text_size_very_small)
      )
      tVFingerprint.typeface = Typeface.DEFAULT_BOLD
      tVFingerprint.setTextColor(ContextCompat.getColor(requireContext(), R.color.silver))
      tVFingerprint.setTextIsSelectable(true)
      tVFingerprint.text = "* ${GeneralUtil.doSectionsInText(" ", uid.fingerprint, 4)}"
      binding?.lFingerprints?.addView(tVFingerprint)
    }

    binding?.tVAlgorithm?.text =
      getString(R.string.template_algorithm, args.newPgpKeyDetails.algo.algorithm)
    binding?.tVAlgorithmBitsOrCurve?.text = if (args.newPgpKeyDetails.algo.bits == 0) {
      getString(R.string.template_curve, args.newPgpKeyDetails.algo.curve)
    } else {
      getString(R.string.template_algorithm_bits, args.newPgpKeyDetails.algo.bits.toString())
    }

    binding?.tVCreated?.text = getString(
      R.string.template_created,
      DateTimeUtil.getPgpDateFormat(context).format(Date(args.newPgpKeyDetails.created))
    )
    binding?.tVModified?.text = getString(
      R.string.template_modified,
      DateTimeUtil.getPgpDateFormat(context).format(Date(args.newPgpKeyDetails.lastModified ?: 0))
    )

    if (args.newPgpKeyDetails.isExpired) {
      binding?.tVWarning?.visible()
      val warningText = getString(
        R.string.warning_key_expired,
        DateTimeUtil.getPgpDateFormat(context).format(Date(args.newPgpKeyDetails.expiration ?: 0))
      )
      if (binding?.tVWarning?.text.isNullOrEmpty()) {
        binding?.tVWarning?.text = warningText
      } else binding?.tVWarning?.append("\n\n" + warningText)
    }
  }

  companion object {
    val KEY_NEW_PRIVATE_KEY = GeneralUtil.generateUniqueExtraKey(
      "KEY_NEW_PRIVATE_KEY", UpdatePrivateKeyDialogFragment::class.java
    )
  }
}
