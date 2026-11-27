package com.nova.browser

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class GeckoUi(
    private val h: Handlers,
    private val tabUrl: () -> String = { "" },
    private val isPrivate: () -> Boolean = { false }
) : GeckoSession.PromptDelegate, GeckoSession.PermissionDelegate {
    private val act get() = h.activity

    private fun has(p: String) = ContextCompat.checkSelfPermission(act, p) == PackageManager.PERMISSION_GRANTED

    private fun gateKey(): String = hostOf(tabUrl()).ifBlank { "-" }

    private fun siteName(): String = hostOf(tabUrl()).ifBlank { S(R.string.gk_this_site) }

    private fun dp(v: Int): Int = (v * act.resources.displayMetrics.density).toInt()

    private fun jsDialog(message: String?, field: View?, cancellable: Boolean, done: (Boolean) -> Unit) {
        if (act.isFinishing || act.isDestroyed) {
            done(false)
            return
        }
        val key = gateKey()
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        if (field != null) box.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        var check: CheckBox? = null
        if (Shield.dialogOffersSuppress(key)) {
            val c = CheckBox(act)
            c.text = S(R.string.gk_dialog_suppress)
            box.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            check = c
        }
        var finished = false
        fun end(ok: Boolean) {
            if (finished) return
            finished = true
            if (check?.isChecked == true) Shield.dialogSuppress(key, isPrivate())
            done(ok)
        }
        val b = novaDialog(act)
            .setTitle(siteName())
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> end(true) }
            .setOnCancelListener { end(false) }
        if (box.childCount > 0) b.setView(box)
        if (cancellable) b.setNegativeButton(android.R.string.cancel) { _, _ -> end(false) }
        runCatching { b.show() }.onFailure { end(false) }
    }

    override fun onAlertPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.AlertPrompt): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        if (!Shield.dialogAllowed(gateKey(), isPrivate())) {
            res.complete(prompt.dismiss())
            return res
        }
        jsDialog(prompt.message, null, false) { res.complete(prompt.dismiss()) }
        return res
    }

    override fun onButtonPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.ButtonPrompt): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        if (!Shield.dialogAllowed(gateKey(), isPrivate())) {
            res.complete(prompt.dismiss())
            return res
        }
        jsDialog(prompt.message, null, true) { ok ->
            res.complete(if (ok) prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE) else prompt.dismiss())
        }
        return res
    }

    override fun onTextPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.TextPrompt): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        if (!Shield.dialogAllowed(gateKey(), isPrivate())) {
            res.complete(prompt.dismiss())
            return res
        }
        val input = EditText(act)
        input.setText(prompt.defaultValue ?: "")
        jsDialog(prompt.message, input, true) { ok ->
            res.complete(if (ok) prompt.confirm(input.text.toString()) else prompt.dismiss())
        }
        return res
    }

    override fun onAuthPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.AuthPrompt): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(8), dp(16), 0)
        val user = EditText(act)
        user.hint = S(R.string.gk_auth_user)
        val pass = EditText(act)
        pass.hint = S(R.string.gk_auth_pass)
        pass.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        box.addView(user)
        box.addView(pass)
        AlertDialog.Builder(act)
            .setTitle(prompt.title)
            .setMessage(prompt.message)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ -> res.complete(prompt.confirm(user.text.toString(), pass.text.toString())) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> res.complete(prompt.dismiss()) }
            .setOnCancelListener { res.complete(prompt.dismiss()) }
            .show()
        return res
    }

    override fun onChoicePrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.ChoicePrompt): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        val flat = ArrayList<GeckoSession.PromptDelegate.ChoicePrompt.Choice>()
        fun add(cs: Array<GeckoSession.PromptDelegate.ChoicePrompt.Choice>?) {
            cs?.forEach { c ->
                if (c.items != null) add(c.items)
                else if (!c.separator && !c.disabled) flat.add(c)
            }
        }
        add(prompt.choices)
        val labels = flat.map { it.label }.toTypedArray()
        val b = AlertDialog.Builder(act)
        if (!prompt.title.isNullOrBlank()) b.setTitle(prompt.title)
        if (prompt.type == GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE) {
            val sel = BooleanArray(flat.size) { flat[it].selected }
            b.setMultiChoiceItems(labels, sel) { _, i, c -> sel[i] = c }
            b.setPositiveButton(android.R.string.ok) { _, _ ->
                val ids = flat.filterIndexed { i, _ -> sel[i] }.map { it.id }.toTypedArray()
                res.complete(prompt.confirm(ids))
            }
            b.setNegativeButton(android.R.string.cancel) { _, _ -> res.complete(prompt.dismiss()) }
        } else {
            b.setItems(labels) { _, i -> res.complete(prompt.confirm(flat[i])) }
        }
        b.setOnCancelListener { res.complete(prompt.dismiss()) }
        b.show()
        return res
    }

    private fun kindOf(type: Int): DateTimeSpec.Kind? = when (type) {
        GeckoSession.PromptDelegate.DateTimePrompt.Type.DATE -> DateTimeSpec.Kind.DATE
        GeckoSession.PromptDelegate.DateTimePrompt.Type.TIME -> DateTimeSpec.Kind.TIME
        GeckoSession.PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL -> DateTimeSpec.Kind.DATETIME_LOCAL
        GeckoSession.PromptDelegate.DateTimePrompt.Type.MONTH -> DateTimeSpec.Kind.MONTH
        GeckoSession.PromptDelegate.DateTimePrompt.Type.WEEK -> DateTimeSpec.Kind.WEEK
        else -> null
    }

    private fun pickDate(
        kind: DateTimeSpec.Kind, init: LocalDateTime, min: String?, max: String?,
        onDate: (LocalDate) -> Unit, onCancel: () -> Unit
    ) {
        val dialog = DatePickerDialog(
            act,
            { _, y, m, day -> onDate(LocalDate.of(y, m + 1, day)) },
            init.year, init.monthValue - 1, init.dayOfMonth
        )
        val zone = ZoneId.systemDefault()
        val lo = DateTimeSpec.minStart(kind, min)
        val hi = DateTimeSpec.maxEnd(kind, max)
        if (lo == null || hi == null || !lo.isAfter(hi)) {
            if (lo != null) dialog.datePicker.minDate = DateTimeSpec.epochMillis(lo, zone)
            if (hi != null) dialog.datePicker.maxDate = DateTimeSpec.epochMillis(hi, zone)
        }
        if (kind == DateTimeSpec.Kind.MONTH) {
            runCatching {
                val id = act.resources.getIdentifier("day", "id", "android")
                if (id != 0) dialog.datePicker.findViewById<View>(id)?.visibility = View.GONE
            }
        }
        dialog.setOnCancelListener { onCancel() }
        dialog.show()
    }

    private fun pickTime(base: LocalDateTime, onTime: (LocalDateTime) -> Unit, onCancel: () -> Unit) {
        val dialog = TimePickerDialog(
            act,
            { _, hh, mm -> onTime(base.withHour(hh).withMinute(mm)) },
            base.hour, base.minute, DateFormat.is24HourFormat(act)
        )
        dialog.setOnCancelListener { onCancel() }
        dialog.show()
    }

    override fun onDateTimePrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.DateTimePrompt): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        val kind = kindOf(prompt.type)
        if (kind == null || act.isFinishing || act.isDestroyed) {
            res.complete(prompt.dismiss())
            return res
        }
        val min: String? = prompt.minValue
        val max: String? = prompt.maxValue
        val init = DateTimeSpec.initial(kind, prompt.defaultValue, min, max, LocalDateTime.now())
        val finish = { v: LocalDateTime ->
            res.complete(prompt.confirm(DateTimeSpec.format(kind, DateTimeSpec.clamp(kind, v, min, max))))
        }
        val cancel = { res.complete(prompt.dismiss()) }
        when (kind) {
            DateTimeSpec.Kind.TIME -> pickTime(init, { finish(it) }, cancel)
            DateTimeSpec.Kind.DATETIME_LOCAL ->
                pickDate(kind, init, min, max, { d -> pickTime(d.atTime(init.toLocalTime()), { finish(it) }, cancel) }, cancel)
            else -> pickDate(kind, init, min, max, { d -> finish(d.atStartOfDay()) }, cancel)
        }
        return res
    }

    override fun onColorPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.ColorPrompt): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        if (act.isFinishing || act.isDestroyed) {
            res.complete(prompt.dismiss())
            return res
        }
        val parsed = ColorPick.parse(prompt.defaultValue)
        val start = if (parsed < 0) 0 else parsed
        val picker = ColorPickerView(act)
        picker.setColor(start)
        val swatch = View(act)
        fun paint(c: Int) {
            val g = GradientDrawable()
            g.setColor(c or 0xFF000000.toInt())
            g.cornerRadius = dp(8).toFloat()
            g.setStroke(dp(1).coerceAtLeast(1), 0x33888888)
            swatch.background = g
        }
        paint(start)
        val hex = EditText(act)
        hex.setText(ColorPick.hex(start))
        hex.hint = S(R.string.gk_color_hex)
        hex.setSingleLine()
        hex.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        hex.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(7))
        var sync = false
        picker.onChange = { c ->
            sync = true
            hex.setText(ColorPick.hex(c))
            sync = false
            paint(c)
        }
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (sync) return
                val c = ColorPick.parse(s?.toString())
                if (c >= 0) {
                    sync = true
                    picker.setColor(c)
                    sync = false
                    paint(c)
                }
            }
        })
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = android.view.Gravity.CENTER_VERTICAL
        row.addView(swatch, LinearLayout.LayoutParams(dp(44), dp(44)))
        val hexLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        hexLp.marginStart = dp(12)
        row.addView(hex, hexLp)
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(12), dp(20), 0)
        box.addView(picker, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val rowLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rowLp.topMargin = dp(16)
        box.addView(row, rowLp)
        novaDialog(act)
            .setTitle(S(R.string.gk_color_title))
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val typed = ColorPick.parse(hex.text.toString())
                val c = if (typed < 0) picker.color and 0xFFFFFF else typed
                res.complete(prompt.confirm(ColorPick.hex(c)))
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> res.complete(prompt.dismiss()) }
            .setOnCancelListener { res.complete(prompt.dismiss()) }
            .show()
        return res
    }

    override fun onPopupPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.PopupPrompt): GeckoResult<PromptResponse>? {
        val host = runCatching { Uri.parse(prompt.targetUri ?: "").host }.getOrNull().orEmpty()
        if (GoogleAccounts.isAuthHost(host) || PopupGate.SHARED.consume()) {
            return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
        }
        if (act.isFinishing || act.isDestroyed || !PopupGate.SHARED.beginPending()) {
            return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))
        }
        val res = GeckoResult<PromptResponse>()
        var settled = false
        fun settle(answer: AllowOrDeny) {
            if (settled) return
            settled = true
            PopupGate.SHARED.endPending()
            res.complete(prompt.confirm(answer))
        }
        PopupBar.show(
            act, S(R.string.gk_popup_blocked), S(R.string.gk_popup_allow), PopupGate.PENDING_MS,
            { settle(AllowOrDeny.ALLOW) }, { settle(AllowOrDeny.DENY) }
        )
        return res
    }

    private fun confirmAllow(
        title: String, msg: String, yes: String, no: String, onFail: AllowOrDeny,
        done: (AllowOrDeny) -> Unit
    ) {
        if (act.isFinishing || act.isDestroyed) { done(onFail); return }
        runCatching {
            novaDialog(act).setTitle(title).setMessage(msg)
                .setPositiveButton(yes) { _, _ -> done(AllowOrDeny.ALLOW) }
                .setNegativeButton(no) { _, _ -> done(AllowOrDeny.DENY) }
                .setOnCancelListener { done(AllowOrDeny.DENY) }
                .show()
        }.onFailure { done(onFail) }
    }

    fun showRepostDialog(done: (AllowOrDeny) -> Unit) {
        confirmAllow(
            S(R.string.gk_repost_title),
            siteName() + "\n\n" + S(R.string.gk_repost_msg),
            S(R.string.gk_repost_yes), S(R.string.gk_cancel), AllowOrDeny.DENY, done
        )
    }

    fun showLeaveDialog(done: (AllowOrDeny) -> Unit) {
        confirmAllow(
            S(R.string.gk_leave_title),
            siteName() + "\n\n" + S(R.string.gk_leave_msg),
            S(R.string.gk_leave_yes), S(R.string.gk_leave_no), AllowOrDeny.ALLOW, done
        )
    }

    override fun onRepostConfirmPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.RepostConfirmPrompt): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        showRepostDialog { res.complete(prompt.confirm(it)) }
        return res
    }

    override fun onBeforeUnloadPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        showLeaveDialog { res.complete(prompt.confirm(it)) }
        return res
    }

    override fun onFilePrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.FilePrompt): GeckoResult<PromptResponse>? {
        val res = GeckoResult<PromptResponse>()
        val multiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
        val types = prompt.mimeTypes?.filter { it.isNotBlank() && !it.startsWith(".") }.orEmpty()
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        if (types.size == 1) i.type = types[0]
        else {
            i.type = "*/*"
            if (types.isNotEmpty()) i.putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
        }
        if (multiple) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        val cb: (Array<Uri>?) -> Unit = { uris ->
            if (uris == null || uris.isEmpty()) res.complete(prompt.dismiss())
            else if (multiple) res.complete(prompt.confirm(act, uris))
            else res.complete(prompt.confirm(act, uris[0]))
        }
        h.filePick(Intent.createChooser(i, null), cb)
        return res
    }

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<out String>?,
        callback: GeckoSession.PermissionDelegate.Callback
    ) {
        val list = permissions?.toList().orEmpty()
        h.androidPerm(list) {
            if (list.all { has(it) }) callback.grant() else callback.reject()
        }
    }

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission
    ): GeckoResult<Int>? {
        val allow = GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
        val deny = GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
        return when (perm.permission) {
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> {
                val res = GeckoResult<Int>()
                h.sitePerm(
                    perm.uri,
                    S(R.string.gk_perm_location),
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                ) { ok -> res.complete(if (ok) allow else deny) }
                res
            }
            GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS -> {
                val res = GeckoResult<Int>()
                h.sitePerm(perm.uri, S(R.string.gk_perm_storage), emptyList()) { ok -> res.complete(if (ok) allow else deny) }
                res
            }
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> GeckoResult.fromValue(deny)   // لا نمنح تخزيناً دائماً للمواقع بصمت
            GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS,
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> GeckoResult.fromValue(allow)
            else -> GeckoResult.fromValue(deny)
        }
    }

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback
    ) {
        val need = ArrayList<String>()
        val labels = ArrayList<String>()
        if (!video.isNullOrEmpty()) { need.add(Manifest.permission.CAMERA); labels.add(S(R.string.gk_perm_camera)) }
        if (!audio.isNullOrEmpty()) { need.add(Manifest.permission.RECORD_AUDIO); labels.add(S(R.string.gk_perm_mic)) }
        if (need.isEmpty()) { callback.reject(); return }
        h.sitePerm(uri, labels.joinToString(S(R.string.gk_perm_join)), need) { ok ->
            val v: GeckoSession.PermissionDelegate.MediaSource? =
                if (ok && has(Manifest.permission.CAMERA)) video?.firstOrNull() else null
            val a: GeckoSession.PermissionDelegate.MediaSource? =
                if (ok && has(Manifest.permission.RECORD_AUDIO)) audio?.firstOrNull() else null
            if (v == null && a == null) callback.reject() else callback.grant(v, a)
        }
    }
}
