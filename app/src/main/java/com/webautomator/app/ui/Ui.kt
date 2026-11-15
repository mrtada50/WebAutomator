package com.webautomator.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import java.text.DateFormat
import java.util.Date

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.themeColor(attr: Int): Int = MaterialColors.getColor(this, attr, Color.GRAY)

fun Context.vbox(pad: Int = 0): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = LinearLayout.VERTICAL
    l.setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
    return l
}

fun Context.hbox(): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = LinearLayout.HORIZONTAL
    l.gravity = Gravity.CENTER_VERTICAL
    return l
}

fun Context.tv(text: CharSequence?, sp: Float = 14f, bold: Boolean = false): TextView {
    val t = TextView(this)
    t.text = text
    t.textSize = sp
    t.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
    if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
    return t
}

fun Context.btn(@StringRes res: Int, onClick: () -> Unit): MaterialButton = btn(getString(res), onClick)

fun Context.btn(text: CharSequence, onClick: () -> Unit): MaterialButton {
    val b = MaterialButton(this)
    b.text = text
    b.isAllCaps = false
    b.setOnClickListener { onClick() }
    return b
}

/** Compact text button used inside list rows. */
fun Context.smallBtn(text: CharSequence, onClick: () -> Unit): MaterialButton {
    val b = MaterialButton(this, null, android.R.attr.borderlessButtonStyle)
    b.text = text
    b.isAllCaps = false
    b.minWidth = 0
    b.minimumWidth = 0
    b.insetTop = 0
    b.insetBottom = 0
    b.setPadding(dp(10), 0, dp(10), 0)
    b.setOnClickListener { onClick() }
    return b
}

fun Context.field(@StringRes hint: Int, value: String = "", type: Int = InputType.TYPE_CLASS_TEXT): EditText {
    val e = EditText(this)
    e.setHint(hint)
    e.setText(value)
    e.inputType = type
    e.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
    return e
}

fun LinearLayout.add(
    v: View,
    w: Int = ViewGroup.LayoutParams.MATCH_PARENT,
    h: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    weight: Float = 0f,
    top: Int = 0,
    start: Int = 0
): LinearLayout {
    val lp = LinearLayout.LayoutParams(w, h, weight)
    lp.topMargin = context.dp(top)
    lp.marginStart = context.dp(start)
    addView(v, lp)
    return this
}

fun fmtTime(ts: Long): String =
    if (ts <= 0) "—" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ts))

/** Simple list adapter: every row is rebuilt programmatically by [bind]. */
class RowAdapter<T>(private val bind: (Context, LinearLayout, T, Int) -> Unit) :
    RecyclerView.Adapter<RowAdapter.VH>() {

    class VH(val box: LinearLayout) : RecyclerView.ViewHolder(box)

    var items: List<T> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = parent.context.vbox(10)
        b.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.box.removeAllViews()
        holder.box.setOnClickListener(null)
        holder.box.isClickable = false
        bind(holder.box.context, holder.box, items[position], position)
    }
}
