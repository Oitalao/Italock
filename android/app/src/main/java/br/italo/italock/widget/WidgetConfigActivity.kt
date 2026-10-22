package br.italo.italock.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.italo.italock.data.PairingStore
import br.italo.italock.R
import androidx.compose.ui.res.stringResource
import br.italo.italock.ui.components.Panel
import br.italo.italock.ui.components.ThinDivider
import br.italo.italock.ui.theme.ItalockTheme

/** Escolhe qual computador o widget controla. */
class WidgetConfigActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(br.italo.italock.i18n.L10n.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val pcs = PairingStore(this).all()
        setContent {
            ItalockTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.safeDrawingPadding().padding(vertical = 16.dp)) {
                        Text(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
                        if (pcs.isEmpty()) Text(stringResource(R.string.widget_config_empty),
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
                        else Panel {
                            pcs.forEachIndexed { i, pc ->
                                if (i > 0) ThinDivider()
                                Text(
                                    pc.displayName, style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        getSharedPreferences(PcWidgetProvider.PREFS, MODE_PRIVATE).edit().putString("w$id", pc.deviceId).commit()
                                        PcWidgetProvider.update(this@WidgetConfigActivity, AppWidgetManager.getInstance(this@WidgetConfigActivity), id)
                                        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                        finish()
                                    }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
