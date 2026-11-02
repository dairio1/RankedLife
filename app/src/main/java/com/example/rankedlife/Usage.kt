package com.example.rankedlife

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

class UsageResult(
    val distMin: Int,
    val igOpens: Int,
    val unlocks: Int,
    val perApp: Map<String, Int>
)

class UsageState(val perm: Boolean, val res: UsageResult?)

object Usage {
    private const val IG = "com.instagram.android"

    /** Apps que cuentan como distracción dentro del horario de trabajo/estudio. */
    val apps = mapOf(
        "com.google.android.youtube" to "YouTube",
        "com.instagram.android" to "Instagram",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok",
        "com.facebook.katana" to "Facebook",
        "com.twitter.android" to "X",
        "com.snapchat.android" to "Snapchat",
        "com.reddit.frontpage" to "Reddit"
    )

    @Suppress("DEPRECATION")
    fun hasPermission(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun windowsFor(date: LocalDate, c: Cfg): List<Pair<Long, Long>> {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone)
        return listOf(
            Pair(
                start.plusHours(c.workStart.toLong()).toInstant().toEpochMilli(),
                start.plusHours(c.workEnd.toLong()).toInstant().toEpochMilli()
            ),
            Pair(
                start.plusHours(c.studyStart.toLong()).toInstant().toEpochMilli(),
                start.plusHours(c.studyEnd.toLong()).toInstant().toEpochMilli()
            )
        )
    }

    fun read(ctx: Context, date: LocalDate, windows: List<Pair<Long, Long>>, nowMs: Long): UsageResult {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val zone = ZoneId.systemDefault()
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(nowMs, dayStart + 24L * 3600L * 1000L)

        val events = usm.queryEvents(dayStart, end)
        val e = UsageEvents.Event()
        val fgStart = HashMap<String, Long>()
        val lastPause = HashMap<String, Long>()
        val ms = HashMap<String, Long>()
        var opens = 0
        var unlocks = 0

        fun addOverlap(pkg: String, a: Long, b: Long) {
            for (w in windows) {
                val s = maxOf(a, w.first)
                val t = minOf(b, w.second)
                if (t > s) ms[pkg] = (ms[pkg] ?: 0L) + (t - s)
            }
        }

        fun inWindows(t: Long): Boolean = windows.any { t >= it.first && t <= it.second }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val ts = e.timeStamp
            if (e.eventType == 18 && Build.VERSION.SDK_INT >= 28) {
                unlocks++
                continue
            }
            val pkg = e.packageName ?: continue
            if (!apps.containsKey(pkg)) continue
            when (e.eventType) {
                1 -> {
                    if (!fgStart.containsKey(pkg)) {
                        fgStart[pkg] = ts
                        val lp = lastPause[pkg]
                        val reopen = lp != null && ts - lp < 5000L
                        if (pkg == IG && !reopen && inWindows(ts)) opens++
                    }
                }
                2 -> {
                    val s = fgStart.remove(pkg)
                    if (s != null) addOverlap(pkg, s, ts)
                    lastPause[pkg] = ts
                }
            }
        }
        for ((pkg, s) in fgStart) addOverlap(pkg, s, end)

        val perApp = HashMap<String, Int>()
        var totalMs = 0L
        for ((pkg, v) in ms) {
            totalMs += v
            val label = apps[pkg] ?: pkg
            perApp[label] = (perApp[label] ?: 0) + (v / 60000L).toInt()
        }
        return UsageResult((totalMs / 60000L).toInt(), opens, unlocks, perApp)
    }
}
