package org.openiisu.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.openiisu.core.ExtraValue
import org.openiisu.core.IntentSpec

object EmulatorLauncher {
    fun isInstalled(context: Context, pkg: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(pkg) != null

    fun toIntent(spec: IntentSpec): Intent = Intent().apply {
        spec.action?.let { action = it }
        spec.data?.let { data = Uri.parse(it) }
        if (spec.activity != null) component = ComponentName(spec.packageName, spec.activity) else setPackage(spec.packageName)
        for ((k, v) in spec.extras) when (v) {
            is ExtraValue.Str -> putExtra(k, v.v)
            is ExtraValue.Bool -> putExtra(k, v.v)
            is ExtraValue.Int -> putExtra(k, v.v)
        }
        if ("activity-clear-top" in spec.flags) addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if ("activity-new-task" in spec.flags) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Fires the first candidate whose package is installed; returns false if none is. */
    fun launch(context: Context, candidates: List<IntentSpec>): Boolean {
        val spec = candidates.firstOrNull { isInstalled(context, it.packageName) } ?: return false
        context.startActivity(toIntent(spec))
        return true
    }
}
