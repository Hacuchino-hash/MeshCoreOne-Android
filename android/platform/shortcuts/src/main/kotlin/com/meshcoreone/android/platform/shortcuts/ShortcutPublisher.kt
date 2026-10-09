// AndroidOnly: WP-404 Publishes dynamic launcher and direct-share shortcuts through the framework ShortcutManager.
package com.meshcoreone.android.platform.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.PersistableBundle

class ShortcutPublisher(private val context: Context, private val text: ShortcutText) {
    /**
     * Replaces the dynamic set with the shortcuts for the current radio's [targets]. Called when the
     * addressable contact/channel set or the selected radio changes, so recipients from another radio
     * never linger; stale long-lived ids are removed explicitly.
     */
    fun publish(targets: List<ShortcutTarget>): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        val specs = ShortcutPlan.build(targets, text, manager.maxShortcutCountPerActivity)
        val stale = manager.dynamicShortcuts.map { it.id }.toSet() - specs.map { it.id }.toSet()
        if (stale.isNotEmpty()) manager.removeLongLivedShortcuts(stale.toList())
        return manager.setDynamicShortcuts(specs.mapIndexed { rank, spec -> toInfo(spec, rank) })
    }

    fun clear() {
        context.getSystemService(ShortcutManager::class.java)?.removeAllDynamicShortcuts()
    }

    private fun toInfo(spec: ShortcutSpec, rank: Int): ShortcutInfo {
        val intent = Intent(spec.action)
            .setClassName(context.packageName, ShortcutActionActivity::class.java.name)
            .setPackage(context.packageName)
        spec.targetId?.let { intent.putExtra(ShortcutContract.EXTRA_TARGET_ID, it) }
        spec.reach?.let { intent.putExtra(ShortcutContract.EXTRA_REACH, it.rawValue) }
        val builder = ShortcutInfo.Builder(context, spec.id)
            .setShortLabel(spec.shortLabel)
            .setLongLabel(spec.longLabel)
            .setIntent(intent)
            .setRank(rank)
        if (spec.sharesToTarget) {
            builder.setCategories(setOf(ShortcutContract.SHARE_CATEGORY))
            builder.setLongLived(true)
            builder.setExtras(PersistableBundle().apply { putString(ShortcutContract.EXTRA_TARGET_ID, spec.targetId) })
        }
        return builder.build()
    }
}
