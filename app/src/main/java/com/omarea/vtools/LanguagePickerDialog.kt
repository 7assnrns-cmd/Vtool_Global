package com.omarea.vtools

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class LanguagePickerDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val current = LocaleHelper.getSaved(requireContext())
        val labels = LocaleHelper.supported.map { it.second }.toTypedArray()
        val codes = LocaleHelper.supported.map { it.first }
        val checked = codes.indexOf(current).coerceAtLeast(0)

        return AlertDialog.Builder(requireContext())
            .setTitle("Language / 语言 / اللغة / 言語")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val newLang = codes[which]
                if (newLang != current) {
                    LocaleHelper.apply(requireActivity(), newLang)
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
