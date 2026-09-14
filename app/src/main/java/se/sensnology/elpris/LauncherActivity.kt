package se.sensnology.elpris

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView

class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemeSettings.apply(this)
        super.onCreate(savedInstanceState)
        val pairingResult = handleHomeAssistantPairing(intent)
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, PriceWidgetProvider::class.java))
            .filter { WidgetSettings.isConfigured(this, it) }
            .toIntArray()
        when (ids.size) {
            0 -> open(STANDALONE_SETTINGS_ID, pairingResult)
            1 -> open(ids.first(), pairingResult)
            else -> showWidgetPicker(ids, pairingResult)
        }
    }

    private fun showWidgetPicker(ids: IntArray, pairingResult: Boolean? = null) {
        val darkTheme = AppThemeSettings.isDark(this)
        val background = if (darkTheme) 0xFF10151B.toInt() else 0xFFF6F7FB.toInt()
        val textColor = if (darkTheme) 0xFFF5F7FA.toInt() else 0xFF192029.toInt()
        val mutedColor = if (darkTheme) 0xFFA8B1BC.toInt() else 0xFF667180.toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            setBackgroundColor(background)
        }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10))
            addView(ImageView(this@LauncherActivity).apply { setImageResource(R.drawable.app_icon) },
                LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(10) })
            addView(TextView(this@LauncherActivity).apply {
                text = AppLanguageSettings.text(this@LauncherActivity, R.string.app_title); textSize = 27f; setTextColor(textColor)
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(TextView(this).apply {
            text = AppLanguageSettings.text(this@LauncherActivity, R.string.choose_widget)
            textSize = 16f
            setTextColor(mutedColor)
            setPadding(0, dp(12), 0, dp(12))
        })
        ids.forEachIndexed { index, id ->
            val settings = WidgetSettings.load(this, id)
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(Button(this@LauncherActivity).apply {
                    text = AppLanguageSettings.text(this@LauncherActivity, R.string.widget_choice, index + 1, settings.area, settings.intervalMinutes)
                    isAllCaps = false
                    textSize = 16f
                    gravity = Gravity.CENTER_VERTICAL
                    setOnClickListener { open(id, pairingResult) }
                }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(8) })
                addView(ImageButton(this@LauncherActivity).apply {
                    setImageResource(android.R.drawable.ic_menu_delete)
                    contentDescription = AppLanguageSettings.text(this@LauncherActivity, R.string.forget_widget)
                    setOnClickListener { confirmForget(id) }
                }, LinearLayout.LayoutParams(dp(54), dp(54)))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply {
                bottomMargin = dp(8)
            })
        }
        setContentView(root)
    }

    private fun confirmForget(id: Int) {
        AlertDialog.Builder(this)
            .setTitle(AppLanguageSettings.text(this, R.string.forget_widget_title))
            .setMessage(AppLanguageSettings.text(this, R.string.forget_widget_message))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(AppLanguageSettings.text(this, R.string.forget_widget)) { _, _ ->
                WidgetSettings.delete(this, id)
                recreate()
            }
            .show()
    }

    private fun open(id: Int, pairingResult: Boolean? = null) {
        startActivity(Intent(this, WidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(WidgetConfigActivity.EXTRA_EXISTING_WIDGET, true)
            pairingResult?.let { putExtra(WidgetConfigActivity.EXTRA_HOME_ASSISTANT_PAIRING, it) }
        })
        finish()
    }

    private fun handleHomeAssistantPairing(intent: Intent): Boolean? {
        val uri = intent.data ?: return null
        if (uri.scheme !in setOf("spotnav", "elpris") || uri.host != "home-assistant") return null
        val url = uri.getQueryParameter("url").orEmpty().trim()
        val webhook = uri.getQueryParameter("webhook").orEmpty().trim()
        val valid = HomeAssistantSettings.isAllowedBaseUrl(url) && webhook.isNotBlank()
        if (valid) HomeAssistantSettings.save(this, HomeAssistantSettings(url, webhook))
        return valid
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        // Zero is AppWidgetManager.INVALID_APPWIDGET_ID, so the standalone
        // profile must use a distinct ID that can never belong to a widget.
        private const val STANDALONE_SETTINGS_ID = -1
    }
}
