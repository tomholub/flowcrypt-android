/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: denbond7
 */

package com.flowcrypt.email.ui.activity.fragment.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowcrypt.email.R
import com.flowcrypt.email.databinding.FragmentChangeGmailLabelsForSingleMessageBinding
import com.flowcrypt.email.extensions.launchAndRepeatWithLifecycle
import com.flowcrypt.email.extensions.navController
import com.flowcrypt.email.jetpack.lifecycle.CustomAndroidViewModelFactory
import com.flowcrypt.email.jetpack.viewmodel.GmailLabelsViewModel
import com.flowcrypt.email.ui.activity.fragment.base.ListProgressBehaviour
import com.flowcrypt.email.ui.adapter.GmailApiLabelsWithChoiceListAdapter
import com.flowcrypt.email.ui.adapter.recyclerview.itemdecoration.MarginItemDecoration

/**
 * @author Denys Bondarenko
 */
class ChangeGmailLabelsForSingleMessageDialogFragment : BaseDialogFragment(),
  ListProgressBehaviour {
  private var binding: FragmentChangeGmailLabelsForSingleMessageBinding? = null
  private val args by navArgs<ChangeGmailLabelsForSingleMessageDialogFragmentArgs>()
  private val gmailLabelsViewModel: GmailLabelsViewModel by viewModels {
    object : CustomAndroidViewModelFactory(requireActivity().application) {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GmailLabelsViewModel(requireActivity().application, args.messageEntity) as T
      }
    }
  }
  private val gmailApiLabelsWithChoiceListAdapter = GmailApiLabelsWithChoiceListAdapter()

  override val emptyView: View?
    get() = binding?.textViewStatus
  override val progressView: View?
    get() = binding?.progressBar
  override val contentView: View?
    get() = binding?.recyclerViewLabels
  override val statusView: View?
    get() = binding?.textViewStatus

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setupGmailLabelsViewModel()
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    binding = FragmentChangeGmailLabelsForSingleMessageBinding.inflate(
      LayoutInflater.from(requireContext()),
      if ((view != null) and (view is ViewGroup)) view as ViewGroup? else null,
      false
    )

    binding?.recyclerViewLabels?.apply {
      layoutManager = LinearLayoutManager(context)
      addItemDecoration(
        MarginItemDecoration(
          marginBottom = resources.getDimensionPixelSize(R.dimen.default_margin_small)
        )
      )
      adapter = gmailApiLabelsWithChoiceListAdapter
    }

    val builder = AlertDialog.Builder(requireContext()).apply {
      setView(binding?.root)
      setTitle(getString(R.string.manage_labels))

      setNegativeButton(R.string.cancel) { _, _ ->
        navController?.navigateUp()
      }
    }

    return builder.create()
  }

  private fun setupGmailLabelsViewModel() {
    launchAndRepeatWithLifecycle {
      gmailLabelsViewModel.labelsInfoFlow.collect {
        gmailApiLabelsWithChoiceListAdapter.submitList(it)
        if (it.isEmpty()) {
          showEmptyView()
        } else {
          showContent()
        }
      }
    }
  }
}