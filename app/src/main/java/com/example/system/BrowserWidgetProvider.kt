package com.example.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

object BrowserLaunchContract {
    const val ACTION_OPEN_BROWSER = "com.example.action.OPEN_BROWSER"
    const val ACTION_NEW_INCOGNITO = "com.example.action.NEW_INCOGNITO"
    const val ACTION_AI_ANALYZE = "com.example.action.AI_ANALYZE"
    const val ACTION_DAILY_NEWS = "com.example.action.DAILY_NEWS"
    const val ACTION_OPEN_PWA = "com.example.action.OPEN_PWA"
    const val EXTRA_URL = "extra_open_url"
}

class BrowserWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_pc_viewport)
            views.setOnClickPendingIntent(
                R.id.widget_search_area,
                createActionPendingIntent(context, BrowserLaunchContract.ACTION_OPEN_BROWSER, 101)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_incognito,
                createActionPendingIntent(context, BrowserLaunchContract.ACTION_NEW_INCOGNITO, 102)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_ai,
                createActionPendingIntent(context, BrowserLaunchContract.ACTION_AI_ANALYZE, 103)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_news,
                createActionPendingIntent(context, BrowserLaunchContract.ACTION_DAILY_NEWS, 104)
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun createActionPendingIntent(
        context: Context,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
