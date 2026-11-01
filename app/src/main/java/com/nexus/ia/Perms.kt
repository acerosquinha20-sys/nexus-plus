package com.nexus.ia

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import rikka.shizuku.Shizuku

object Perms {
    const val TERMUX = "com.termux.permission.RUN_COMMAND"

    fun shizukuRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun shizukuGranted(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun termuxInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo("com.termux", 0)
        true
    } catch (e: Exception) {
        false
    }

    fun termuxGranted(ctx: Context): Boolean =
        ctx.checkSelfPermission(TERMUX) == PackageManager.PERMISSION_GRANTED

    fun unlocked(ctx: Context): Boolean = shizukuGranted() || termuxGranted(ctx)

    fun overlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun accessibility(ctx: Context): Boolean {
        val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return s.split(':').any { it.startsWith(ctx.packageName + "/") }
    }
}
