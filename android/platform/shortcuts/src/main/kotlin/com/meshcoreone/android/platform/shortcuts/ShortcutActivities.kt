// AndroidOnly: WP-404 No-UI trampolines that convert launcher shortcut and share intents into typed requests.
package com.meshcoreone.android.platform.shortcuts

import android.app.Activity
import android.content.Intent
import android.os.Bundle

private fun Activity.dispatch(): Boolean {
    val raw = RawShortcutIntent(
        action = intent.action,
        mimeType = intent.type,
        text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT),
        targetId = intent.getStringExtra(ShortcutContract.EXTRA_TARGET_ID),
        reach = intent.getStringExtra(ShortcutContract.EXTRA_REACH),
        shortcutId = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID),
    )
    val request = ShortcutRequestParser.parse(raw) ?: return false
    if (ShortcutHost.dispatcher.submit(request) == ShortcutDispatcher.Outcome.Held) {
        // Cold start: bring the app up; it installs its handler, which drains the held request once.
        packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it) }
    }
    return true
}

/** Exported system share target. Accepts only plain text; sending still requires in-app confirmation. */
class ShareTargetActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatch()
        finish()
    }
}

/** Not exported: reached only by the app's own launcher shortcuts. */
class ShortcutActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatch()
        finish()
    }
}
