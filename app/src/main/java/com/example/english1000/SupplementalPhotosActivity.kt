package com.example.english1000

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

/** Supplemental photo vocabulary: 17 real pictures whose compound English
 * names are not existing dictionary lemmas. Does not modify the 5126 entries.
 */
class SupplementalPhotosActivity : Activity() {
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 24, 43))
            setPadding(dp(16), dp(16), dp(16), dp(20))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val items = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(items)
        root.addView(scroll)
        setContentView(root)
        val header = TextView(this).apply {
            text = "Дополнительные фотографии"
            textSize = 24f
            setTextColor(Color.rgb(255, 204, 73))
            gravity = Gravity.CENTER
        }
        items.addView(header)
        val intro = TextView(this).apply {
            text = "17 составных выражений вне основного словаря из 5126 слов. Все изображения работают без интернета."
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(4), dp(14), dp(4), dp(14))
        }
        items.addView(intro)
        val goBack = Button(this).apply {
            text = "‹ Назад"
            isAllCaps = false
            setOnClickListener { finish() }
        }
        items.addView(goBack)
        val catalog = JSONObject(assets.open("supplemental_photo_catalog.json")
            .bufferedReader().use { it.readText() }).getJSONArray("images")
        for (i in 0 until catalog.length()) {
            val row = catalog.getJSONObject(i)
            val title = row.getString("titleEn")
            val path = row.getString("asset")
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(18, 43, 64))
                    cornerRadius = dp(18).toFloat()
                    setStroke(dp(2), Color.rgb(255, 193, 60))
                }
            }
            val label = TextView(this).apply {
                text = title
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }
            box.addView(label)
            val img = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                contentDescription = title
                val bitmap = runCatching {
                    assets.open(path).use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
                if (bitmap != null) {
                    setImageBitmap(bitmap)
                } else {
                    visibility = android.view.View.GONE
                    label.text = "$title — изображение недоступно"
                }
            }
            box.addView(img, LinearLayout.LayoutParams(-1, dp(190)))
            items.addView(box, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(10)
            })
        }
    }
}
