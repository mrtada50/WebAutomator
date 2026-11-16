package com.webautomator.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.webautomator.app.R
import java.text.DateFormat
import java.util.Date

// ---------- basics ----------

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.color(@ColorRes id: Int): Int = ContextCompat.getColor(this, id)

fun Context.themeColor(attr: Int): Int = MaterialColors.getColor(this, attr, Color.GRAY)

internal const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
internal const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

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

fun LinearLayout.add(
    v: View,
    w: Int = MATCH,
    h: Int = WRAP,
    weight: Float = 0f,
    top: Int = 0,
    start: Int = 0,
    end: Int = 0,
    bottom: Int = 0
): LinearLayout {
    val lp = LinearLayout.LayoutParams(w, h, weight)
    lp.topMargin = context.dp(top)
    lp.marginStart = context.dp(start)
    lp.marginEnd = context.dp(end)
    lp.bottomMargin = context.dp(bottom)
    addView(v, lp)
    return this
}

/** Adds a view at its natural size. */
fun LinearLayout.addWrap(v: View, start: Int = 0, top: Int = 0): LinearLayout = add(v, WRAP, WRAP, 0f, top, start)

// ---------- text ----------

fun Context.tv(text: CharSequence?, sp: Float = 14f, bold: Boolean = false): TextView {
    val t = TextView(this)
    t.text = text
    t.textSize = sp
    t.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
    if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
    return t
}

fun Context.caption(text: CharSequence?, sp: Float = 12f): TextView {
    val t = tv(text, sp)
    t.setTextColor(color(R.color.text_secondary))
    return t
}

fun Context.screenTitle(text: CharSequence): TextView = tv(text, 26f, true)

fun Context.heading(text: CharSequence): TextView = tv(text, 16f, true)

// ---------- icons and buttons ----------

fun Context.iconView(@DrawableRes icon: Int, @ColorRes tint: Int, sizeDp: Int = 20): ImageView {
    val iv = ImageView(this)
    iv.setImageResource(icon)
    iv.imageTintList = ColorStateList.valueOf(color(tint))
    iv.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    return iv
}

fun Context.iconBtn(
    @DrawableRes icon: Int,
    @StringRes desc: Int,
    @StringRes helpRes: Int = 0,
    onClick: () -> Unit
): ImageButton {
    val b = ImageButton(this)
    b.setImageResource(icon)
    b.contentDescription = getString(desc)
    b.imageTintList = ColorStateList.valueOf(themeColor(com.google.android.material.R.attr.colorOnSurface))
    val ta = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
    b.background = ta.getDrawable(0)
    ta.recycle()
    b.setPadding(dp(10), dp(10), dp(10), dp(10))
    b.setOnClickListener { onClick() }
    if (helpRes != 0) b.help(helpRes)
    return b
}

private fun MaterialButton.prepare(text: CharSequence, @DrawableRes icon: Int, @StringRes helpRes: Int, onClick: () -> Unit): MaterialButton {
    this.text = text
    isAllCaps = false
    if (icon != 0) {
        this.icon = AppCompatResources.getDrawable(context, icon)
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        iconPadding = context.dp(8)
    }
    setOnClickListener { onClick() }
    if (helpRes != 0) help(helpRes)
    return this
}

fun MaterialButton.filledStyle(): MaterialButton {
    val bg = context.themeColor(com.google.android.material.R.attr.colorPrimary)
    val fg = context.themeColor(com.google.android.material.R.attr.colorOnPrimary)
    backgroundTintList = ColorStateList.valueOf(bg)
    setTextColor(fg)
    iconTint = ColorStateList.valueOf(fg)
    return this
}

fun MaterialButton.tonalStyle(): MaterialButton {
    val bg = context.themeColor(com.google.android.material.R.attr.colorSecondaryContainer)
    val fg = context.themeColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
    backgroundTintList = ColorStateList.valueOf(bg)
    setTextColor(fg)
    iconTint = ColorStateList.valueOf(fg)
    return this
}

fun MaterialButton.textStyle(): MaterialButton {
    val fg = context.themeColor(com.google.android.material.R.attr.colorPrimary)
    backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
    setTextColor(fg)
    iconTint = ColorStateList.valueOf(fg)
    return this
}

fun MaterialButton.compact(): MaterialButton {
    textSize = 13f
    insetTop = 0
    insetBottom = 0
    setPadding(context.dp(8), 0, context.dp(8), 0)
    minimumHeight = context.dp(44)
    return this
}

fun Context.filledBtn(text: CharSequence, @DrawableRes icon: Int = 0, @StringRes helpRes: Int = 0, onClick: () -> Unit): MaterialButton =
    MaterialButton(this).prepare(text, icon, helpRes, onClick).filledStyle()

fun Context.tonalBtn(text: CharSequence, @DrawableRes icon: Int = 0, @StringRes helpRes: Int = 0, onClick: () -> Unit): MaterialButton =
    MaterialButton(this).prepare(text, icon, helpRes, onClick).tonalStyle()

fun Context.textBtn(text: CharSequence, @DrawableRes icon: Int = 0, @StringRes helpRes: Int = 0, onClick: () -> Unit): MaterialButton =
    MaterialButton(this).prepare(text, icon, helpRes, onClick).textStyle()

fun Context.pill(text: CharSequence, @ColorRes fg: Int, @ColorRes bg: Int): TextView {
    val t = tv(text, 11f, true)
    t.setTextColor(color(fg))
    val d = GradientDrawable()
    d.cornerRadius = dp(20).toFloat()
    d.setColor(color(bg))
    t.background = d
    t.setPadding(dp(10), dp(3), dp(10), dp(3))
    t.gravity = Gravity.CENTER
    return t
}

fun Context.chip(text: CharSequence, @DrawableRes icon: Int = 0, checkable: Boolean = false): Chip {
    val c = Chip(this)
    c.text = text
    c.isCheckable = checkable
    c.isCheckedIconVisible = false
    if (icon != 0) {
        c.chipIcon = AppCompatResources.getDrawable(this, icon)
        c.isChipIconVisible = true
    }
    c.id = View.generateViewId()
    return c
}

// ---------- inputs, cards, rows ----------

fun Context.input(@StringRes hint: Int, value: String = "", type: Int = InputType.TYPE_CLASS_TEXT): TextInputLayout {
    val l = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle)
    l.hint = getString(hint)
    val e = TextInputEditText(l.context)
    e.inputType = type
    e.setText(value)
    l.addView(e, LinearLayout.LayoutParams(MATCH, WRAP))
    return l
}

fun TextInputLayout.str(): String = editText?.text?.toString()?.trim() ?: ""

fun Context.cardWith(
    pad: Int = 14,
    @ColorRes fill: Int = R.color.card,
    strokeOn: Boolean = true,
    build: LinearLayout.() -> Unit
): MaterialCardView {
    val card = MaterialCardView(this)
    card.radius = dp(16).toFloat()
    card.cardElevation = 0f
    card.setCardBackgroundColor(color(fill))
    card.strokeWidth = if (strokeOn) dp(1) else 0
    card.strokeColor = color(R.color.stroke)
    val box = vbox(pad)
    box.build()
    card.addView(box, ViewGroup.LayoutParams(MATCH, WRAP))
    return card
}

class SwitchRow(val view: LinearLayout, val sw: MaterialSwitch)

fun Context.switchRow(@StringRes title: Int, @StringRes sub: Int = 0, @StringRes helpRes: Int = 0): SwitchRow {
    val row = hbox()
    val col = vbox(0)
    col.add(tv(getString(title), 15f, true))
    if (sub != 0) col.add(caption(getString(sub)), top = 2)
    row.add(col, 0, WRAP, 1f)
    val sw = MaterialSwitch(this)
    row.addWrap(sw, start = 12)
    if (helpRes != 0) {
        row.help(helpRes)
        sw.help(helpRes)
    }
    return SwitchRow(row, sw)
}

fun Context.emptyState(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes cta: Int = 0,
    onCta: () -> Unit = {}
): LinearLayout {
    val box = vbox(24)
    box.gravity = Gravity.CENTER
    val iv = ImageView(this)
    iv.setImageResource(icon)
    iv.imageTintList = ColorStateList.valueOf(color(R.color.brand))
    val bg = GradientDrawable()
    bg.shape = GradientDrawable.OVAL
    bg.setColor(color(R.color.brand_container))
    iv.background = bg
    iv.setPadding(dp(20), dp(20), dp(20), dp(20))
    box.add(iv, dp(72), dp(72))
    val t = tv(getString(title), 18f, true)
    t.gravity = Gravity.CENTER
    box.add(t, top = 16)
    val b = caption(getString(body), 14f)
    b.gravity = Gravity.CENTER
    box.add(b, top = 6)
    if (cta != 0) box.add(filledBtn(getString(cta), 0, 0, onCta), WRAP, WRAP, top = 16)
    return box
}

// ---------- time, help ----------

fun fmtTime(ts: Long): String =
    if (ts <= 0) "—" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ts))

fun relTime(ts: Long): String =
    if (ts <= 0) "—"
    else DateUtils.getRelativeTimeSpanString(ts, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** Long-press on any control shows a short, simple explanation of what it does. */
fun <T : View> T.help(@StringRes res: Int): T {
    setOnLongClickListener {
        Toast.makeText(context, res, Toast.LENGTH_LONG).show()
        true
    }
    return this
}

/** Simple list adapter: every row is rebuilt programmatically by [bind]. */
class RowAdapter<T>(private val vpad: Int = 6, private val bind: (Context, LinearLayout, T, Int) -> Unit) :
    RecyclerView.Adapter<RowAdapter.VH>() {

    class VH(val box: LinearLayout) : RecyclerView.ViewHolder(box)

    private var data: List<T> = emptyList()

    var items: List<T>
        get() = data
        set(value) {
            data = value
            notifyDataSetChanged()
        }

    /** Used while dragging: reorders without a full reload so the drag animation stays smooth. */
    fun moveItem(from: Int, to: Int) {
        val m = data.toMutableList()
        m.add(to, m.removeAt(from))
        data = m
        notifyItemMoved(from, to)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = parent.context.vbox(0)
        b.setPadding(0, parent.context.dp(vpad), 0, parent.context.dp(vpad))
        b.layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
        return VH(b)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.box.removeAllViews()
        bind(holder.box.context, holder.box, data[position], position)
    }
}
