package dev.dwm.liftlog

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.dwm.liftlog.data.db.createDatabase
import java.time.LocalDate

class OverloadWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OverloadWidget()
}

class OverloadWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = createDatabase(context)
        val today = LocalDate.now().toEpochDay()
        val logs = db.foodLogDao().forRangeOnce(today, today)
        var kcal = 0.0
        for (log in logs) {
            val f = db.foodDao().byId(log.foodId) ?: continue
            kcal += log.grams * f.kcal / 100
        }
        val target = db.settingDao().get("lastTargetKcal")?.toDoubleOrNull()
        val line = if (target != null) "${(target - kcal).toInt()} kcal left" else "${kcal.toInt()} kcal today"

        provideContent {
            GlanceTheme {
                Column(
                    GlanceModifier.fillMaxSize()
                        .background(Color(0xFF0A0E14))
                        .cornerRadius(16.dp)
                        .padding(14.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                ) {
                    Text(
                        "OVERLOAD",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF00E676)),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        ),
                    )
                    Text(
                        line,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFE9EDF5)),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                    )
                }
            }
        }
    }
}
