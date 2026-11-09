package com.example.english1000

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.widget.CheckBox
import android.widget.LinearLayout

/** The same, user-selected examples option is shown before either learning mode. */
object ExampleModeSetup {
    const val PREF_SHOW_EXAMPLES = "show_examples_during_learning"

    fun selected(context: Context): Boolean =
        context.getSharedPreferences("progress_v1", Context.MODE_PRIVATE)
            .getBoolean(PREF_SHOW_EXAMPLES, false)

    fun show(activity: Activity, title: String, onStart: (Boolean) -> Unit) {
        val padding = (20f * activity.resources.displayMetrics.density + .5f).toInt()
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, padding / 2)
        }
        val english = activity.getSharedPreferences("progress_v1", Context.MODE_PRIVATE)
            .getString("ui_language", "ru") == "en"
        val checkbox = CheckBox(activity).apply {
            text = if (english) "Include usage examples" else "Добавлять примеры употребления"
            isChecked = selected(activity)
            textSize = 16f
        }
        layout.addView(checkbox)
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton(if (english) "Start" else "Начать") { _, _ ->
                val checked = checkbox.isChecked
                activity.getSharedPreferences("progress_v1", Context.MODE_PRIVATE)
                    .edit().putBoolean(PREF_SHOW_EXAMPLES, checked).apply()
                onStart(checked)
            }
            .setNegativeButton(if (english) "Cancel" else "Отмена", null)
            .show()
    }
}
