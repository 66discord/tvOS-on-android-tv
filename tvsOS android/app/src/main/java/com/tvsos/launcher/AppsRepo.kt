package com.tvsos.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import java.text.Collator
import java.util.Locale
/** Acesso a lista de apps, icones, cores dominantes e intents de abertura. */
class AppsRepo(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    private val cache = object : LruCache<String, Bitmap>(10 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val tints = HashMap<String, Int>()

    @Suppress("DEPRECATION")
    private fun resolve(category: String): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return try {
            pm.queryIntentActivities(intent, 0) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Apps com icone que realmente abrem (launcher ou leanback). */
    fun load(): List<AppItem> {
        val map = LinkedHashMap<String, AppItem>()
        val categories = listOf(
            Intent.CATEGORY_LAUNCHER,
            "android.intent.category.LEANBACK_LAUNCHER"
        )
        for (cat in categories) {
            for (ri in resolve(cat)) {
                val info = ri.activityInfo ?: continue
                val pkg = info.packageName ?: continue
                if (pkg == context.packageName) continue
                if (map.containsKey(pkg)) continue
                var label = ""
                try {
                    label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                } catch (_: Exception) {
                }
                if (label.isEmpty()) label = pkg
                map[pkg] = AppItem(pkg, label)
            }
        }
        val collator = Collator.getInstance(Locale("pt", "BR"))
        val list = map.values.toMutableList()
        val cmp = object : Comparator<AppItem> {
            override fun compare(a: AppItem, b: AppItem): Int = collator.compare(a.label, b.label)
        }
        list.sortWith(cmp)
        return list
    }

    @Suppress("DEPRECATION")
    private fun appIcon(pkg: String): Drawable? = try {
        pm.getApplicationIcon(pkg)
    } catch (e: Exception) {
        null
    }

    /** Icone ja renderizado em bitmap quadrado (cache por tamanho). */
    fun iconBitmap(pkg: String, size: Int): Bitmap? {
        if (size <= 0) return null
        val key = "$pkg#$size"
        cache.get(key)?.let { return it }
        val d = appIcon(pkg) ?: return null
        val bmp: Bitmap? = try {
            val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(b)
            d.setBounds(0, 0, size, size)
            d.draw(canvas)
            b
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
        if (bmp == null) return null
        cache.put(key, bmp)
        return bmp
    }

    fun dominant(pkg: String): Int {
        tints[pkg]?.let { return it }
        val bmp = iconBitmap(pkg, 96)
        val color = if (bmp != null) dominantColor(bmp) else C.ACCENTS[0]
        tints[pkg] = color
        return color
    }

    /** Pre-carrega icones e cores fora da main thread. */
    fun preload(items: List<AppItem>, tileSize: Int) {
        for (item in items) {
            try {
                iconBitmap(item.pkg, tileSize)
                dominant(item.pkg)
            } catch (_: Exception) {
            }
        }
    }

    fun launchIntent(pkg: String): Intent? {
        val i = try {
            pm.getLaunchIntentForPackage(pkg) ?: pm.getLeanbackLaunchIntentForPackage(pkg)
        } catch (e: Exception) {
            null
        } ?: return null
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return i
    }

    fun openInfo(pkg: String) {
        try {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            i.data = Uri.parse("package:$pkg")
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } catch (_: Exception) {
        }
    }
}
