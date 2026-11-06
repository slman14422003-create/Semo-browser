package com.nova.browser

import android.content.Context
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import org.json.JSONTokener

class NovaWebView(context: Context) : WebView(context) {
    var onSelectionAction: ((Int, String) -> Unit)? = null

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        if (callback == null || !Prefs.smart || onSelectionAction == null) return super.startActionMode(callback, type)
        return super.startActionMode(SelectionCallback(callback), type)
    }

    private inner class SelectionCallback(private val base: ActionMode.Callback) : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val ok = base.onCreateActionMode(mode, menu)
            if (ok) {
                menu.add(Menu.NONE, SmartAssist.SEL_ASK, Menu.NONE, S(R.string.smart_ask_ai)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                menu.add(Menu.NONE, SmartAssist.SEL_TRANSLATE, Menu.NONE, S(R.string.smart_translate)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                menu.add(Menu.NONE, SmartAssist.SEL_SEARCH, Menu.NONE, S(R.string.smart_search)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            }
            return ok
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = base.onPrepareActionMode(mode, menu)

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val id = item.itemId
            if (id != SmartAssist.SEL_ASK && id != SmartAssist.SEL_TRANSLATE && id != SmartAssist.SEL_SEARCH) return base.onActionItemClicked(mode, item)
            evaluateJavascript("(function(){var s=window.getSelection();return s?s.toString():''})()") { raw ->
                val text = runCatching { JSONTokener(raw ?: "").nextValue() as? String }.getOrNull().orEmpty()
                mode.finish()
                onSelectionAction?.invoke(id, text)
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) = base.onDestroyActionMode(mode)

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            if (base is ActionMode.Callback2) base.onGetContentRect(mode, view, outRect) else super.onGetContentRect(mode, view, outRect)
        }
    }
}
