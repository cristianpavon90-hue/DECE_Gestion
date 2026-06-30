package com.example.decegestin

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase

class DashboardWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        updateAppWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_PAGE) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentPage = prefs.getInt(PREF_PAGE_KEY, 0)
            prefs.edit().putInt(PREF_PAGE_KEY, (currentPage + 1) % 2).apply()
            
            // Actualizar instantáneamente usando caché
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, DashboardWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    companion object {
        private const val ACTION_TOGGLE_PAGE = "com.example.decegestin.ACTION_TOGGLE_PAGE"
        private const val PREFS_NAME = "DashboardWidgetPrefs"
        private const val PREF_PAGE_KEY = "current_page"
        private const val CACHE_INST_NAME = "cached_inst_name"
        private const val CACHE_PREFIX = "cat_"

        fun notifyUpdate(context: Context) {
            val intent = Intent(context, DashboardWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, DashboardWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }

        internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.dashboard_widget)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // --- SETUP UI BÁSICA (INSTANTÁNEA) ---
            setupStaticActions(context, views)
            
            // Mostrar nombre de institución guardado en caché
            val cachedInst = prefs.getString(CACHE_INST_NAME, context.getString(R.string.widget_default_inst))
            views.setTextViewText(R.id.widget_institution_name, cachedInst)
            
            // Actualizar vistas con datos de caché inmediatamente
            updateViewsFromCache(context, views, prefs, cachedInst ?: "")
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // --- FETCH DATA (ASÍNCRONO EN SEGUNDO PLANO) ---
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            if (user != null) {
                val database = FirebaseDatabase.getInstance().reference
                database.child("users").child(user.uid).child("institution").get().addOnSuccessListener { snapshot ->
                    val inst = snapshot.value?.toString()
                    if (inst != null) {
                        prefs.edit().putString(CACHE_INST_NAME, inst).apply()
                        views.setTextViewText(R.id.widget_institution_name, inst)
                        
                        val safeKey = AppUtils.getSafeKey(inst)
                        database.child("institutions").child(safeKey).child("categories").get().addOnSuccessListener { catSnapshot ->
                            // Guardar nuevos datos en caché y actualizar UI
                            saveToCacheAndRefresh(context, views, catSnapshot, inst, prefs, appWidgetManager, appWidgetId)
                        }
                    }
                }
            } else {
                views.setTextViewText(R.id.widget_institution_name, context.getString(R.string.widget_login))
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun setupStaticActions(context: Context, views: RemoteViews) {
            val toggleIntent = Intent(context, DashboardWidget::class.java).apply { action = ACTION_TOGGLE_PAGE }
            val togglePI = PendingIntent.getBroadcast(context, 0, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_dots_menu, togglePI)
            views.setInt(R.id.widget_dots_menu, "setColorFilter", android.graphics.Color.WHITE)

            val bitacoraIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("navigate_to", "BitacoraFragment")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val bitacoraPI = PendingIntent.getActivity(context, 1001, bitacoraIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_bitacora, bitacoraPI)
            views.setInt(R.id.widget_bitacora, "setColorFilter", android.graphics.Color.WHITE)

            val newFormIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("navigate_to", "NewFormFragment")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val newFormPI = PendingIntent.getActivity(context, 1002, newFormIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_new_form, newFormPI)
            views.setInt(R.id.widget_new_form, "setColorFilter", android.graphics.Color.WHITE)
        }

        private fun updateViewsFromCache(context: Context, views: RemoteViews, prefs: android.content.SharedPreferences, institution: String) {
            val page = prefs.getInt(PREF_PAGE_KEY, 0)
            if (page == 0) {
                fillColumn(context, views, R.id.column1, R.id.title1, R.id.value1, context.getString(R.string.widget_cat_oficios_short), context.getString(R.string.cat_oficios), prefs, institution)
                fillColumn(context, views, R.id.column2, R.id.title2, R.id.value2, context.getString(R.string.widget_cat_informes_short), context.getString(R.string.cat_informes), prefs, institution)
                fillColumn(context, views, R.id.column3, R.id.title3, R.id.value3, context.getString(R.string.widget_cat_reporte_short), context.getString(R.string.cat_reporte), prefs, institution)
            } else {
                fillColumn(context, views, R.id.column1, R.id.title1, R.id.value1, context.getString(R.string.widget_cat_msp_short), context.getString(R.string.cat_msp), prefs, institution)
                fillColumn(context, views, R.id.column2, R.id.title2, R.id.value2, context.getString(R.string.widget_cat_udai_short), context.getString(R.string.cat_udai), prefs, institution)
                views.setTextViewText(R.id.title3, ""); views.setTextViewText(R.id.value3, "")
            }
        }

        private fun fillColumn(context: Context, views: RemoteViews, colId: Int, tId: Int, vId: Int, short: String, full: String, prefs: android.content.SharedPreferences, inst: String) {
            views.setTextViewText(tId, short)
            val lastMarked = prefs.getInt(CACHE_PREFIX + full, 0)
            views.setTextViewText(vId, String.format("%03d", lastMarked))

            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("navigate_to", "DetailFragment")
                putExtra("title", full)
                putExtra("institution", inst)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(context, full.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(colId, pi)
        }

        private fun saveToCacheAndRefresh(context: Context, views: RemoteViews, snapshot: DataSnapshot, inst: String, prefs: android.content.SharedPreferences, manager: AppWidgetManager, id: Int) {
            val editor = prefs.edit()
            val categories = listOf(context.getString(R.string.cat_oficios), context.getString(R.string.cat_informes), context.getString(R.string.cat_reporte), context.getString(R.string.cat_msp), context.getString(R.string.cat_udai))
            
            categories.forEach { cat ->
                val catData = snapshot.child(cat)
                val maxNum = catData.children.mapNotNull { it.key?.toIntOrNull() }.maxOrNull() ?: 0
                editor.putInt(CACHE_PREFIX + cat, maxNum)
            }
            editor.apply()
            
            updateViewsFromCache(context, views, prefs, inst)
            manager.updateAppWidget(id, views)
        }
    }
}
