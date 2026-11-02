package com.example.rankedlife

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

fun isWeekday(d: LocalDate): Boolean =
    d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY

data class DayRecord(
    val date: String,
    val score: Int,
    val delta: Int,
    val lpAfter: Int,
    val missed: Boolean,
    val morningMin: Int = 0,
    val studyMin: Int = 0,
    val distMin: Int = 0,
    val igOpens: Int = 0,
    val nails: Int = 0,
    val loops: Int = 0,
    val showered: Boolean = true,
    val overate: Boolean = false
)

data class Work(val morningSec: Long = 0L, val studySec: Long = 0L)

data class AppData(
    val lp: Int = 0,
    val shield: Boolean = true,
    val startDate: String = "",
    val records: List<DayRecord> = emptyList(),
    val work: Map<String, Work> = emptyMap(),
    val activeTimer: String = "",
    val timerStart: Long = 0L,
    val timerDate: String = "",
    val cfg: Cfg = Cfg()
)

class CloseResult(
    val oldLp: Int,
    val newLp: Int,
    val score: Int,
    val delta: Int,
    val items: List<Pair<String, Double>>,
    val shieldUsed: Boolean
)

object Codec {
    fun encode(d: AppData): String {
        val o = JSONObject()
        o.put("lp", d.lp)
        o.put("shield", d.shield)
        o.put("startDate", d.startDate)
        o.put("activeTimer", d.activeTimer)
        o.put("timerStart", d.timerStart)
        o.put("timerDate", d.timerDate)

        val recs = JSONArray()
        for (r in d.records) {
            val j = JSONObject()
            j.put("date", r.date)
            j.put("score", r.score)
            j.put("delta", r.delta)
            j.put("lpAfter", r.lpAfter)
            j.put("missed", r.missed)
            j.put("morningMin", r.morningMin)
            j.put("studyMin", r.studyMin)
            j.put("distMin", r.distMin)
            j.put("igOpens", r.igOpens)
            j.put("nails", r.nails)
            j.put("loops", r.loops)
            j.put("showered", r.showered)
            j.put("overate", r.overate)
            recs.put(j)
        }
        o.put("records", recs)

        val w = JSONObject()
        for ((k, v) in d.work) {
            val j = JSONObject()
            j.put("m", v.morningSec)
            j.put("s", v.studySec)
            w.put(k, j)
        }
        o.put("work", w)

        val c = JSONObject()
        c.put("mg", d.cfg.morningGoal)
        c.put("sg", d.cfg.studyGoal)
        c.put("ws", d.cfg.workStart)
        c.put("we", d.cfg.workEnd)
        c.put("ss", d.cfg.studyStart)
        c.put("se", d.cfg.studyEnd)
        o.put("cfg", c)
        return o.toString()
    }

    fun decode(text: String): AppData {
        val o = JSONObject(text)
        val recs = ArrayList<DayRecord>()
        val arr = o.optJSONArray("records") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val j = arr.getJSONObject(i)
            recs.add(
                DayRecord(
                    date = j.optString("date"),
                    score = j.optInt("score"),
                    delta = j.optInt("delta"),
                    lpAfter = j.optInt("lpAfter"),
                    missed = j.optBoolean("missed"),
                    morningMin = j.optInt("morningMin"),
                    studyMin = j.optInt("studyMin"),
                    distMin = j.optInt("distMin"),
                    igOpens = j.optInt("igOpens"),
                    nails = j.optInt("nails"),
                    loops = j.optInt("loops"),
                    showered = j.optBoolean("showered", true),
                    overate = j.optBoolean("overate")
                )
            )
        }
        val work = HashMap<String, Work>()
        val wj = o.optJSONObject("work")
        if (wj != null) {
            val keys = wj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val j = wj.getJSONObject(k)
                work[k] = Work(j.optLong("m"), j.optLong("s"))
            }
        }
        val cj = o.optJSONObject("cfg")
        val def = Cfg()
        val cfg = if (cj == null) def else Cfg(
            morningGoal = cj.optInt("mg", def.morningGoal),
            studyGoal = cj.optInt("sg", def.studyGoal),
            workStart = cj.optInt("ws", def.workStart),
            workEnd = cj.optInt("we", def.workEnd),
            studyStart = cj.optInt("ss", def.studyStart),
            studyEnd = cj.optInt("se", def.studyEnd)
        )
        return AppData(
            lp = o.optInt("lp"),
            shield = o.optBoolean("shield", true),
            startDate = o.optString("startDate", LocalDate.now().toString()),
            records = recs,
            work = work,
            activeTimer = o.optString("activeTimer", ""),
            timerStart = o.optLong("timerStart"),
            timerDate = o.optString("timerDate", ""),
            cfg = cfg
        )
    }
}

class AppModel(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("rankedlife", Context.MODE_PRIVATE)

    var data by mutableStateOf(load())
        private set

    var nowMs by mutableStateOf(System.currentTimeMillis())
    var resumeTick by mutableStateOf(0)

    private fun load(): AppData {
        val txt = prefs.getString("data", null)
        if (txt != null) {
            try {
                var d = Codec.decode(txt)
                if (prefs.getInt("cfgv", 1) < 2) {
                    // Nuevos horarios que cuentan: 9-14 y 17-22
                    d = d.copy(cfg = d.cfg.copy(workEnd = 14, studyStart = 17, studyEnd = 22))
                    prefs.edit().putString("data", Codec.encode(d)).putInt("cfgv", 2).apply()
                }
                return d
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        prefs.edit().putInt("cfgv", 2).apply()
        return AppData(startDate = LocalDate.now().toString())
    }

    private fun commit(d: AppData) {
        data = d
        prefs.edit().putString("data", Codec.encode(d)).apply()
    }

    // ---------- Cronómetros ----------
    fun elapsedSec(kind: String): Long {
        val today = LocalDate.now().toString()
        val w = data.work[today] ?: Work()
        val base = if (kind == "morning") w.morningSec else w.studySec
        val running = if (data.activeTimer == kind && data.timerDate == today) {
            max(0L, (nowMs - data.timerStart) / 1000)
        } else 0L
        return base + running
    }

    private fun addWork(d: AppData, date: String, kind: String, secs: Long): AppData {
        val w = d.work[date] ?: Work()
        val nw = if (kind == "morning") {
            w.copy(morningSec = w.morningSec + secs)
        } else {
            w.copy(studySec = w.studySec + secs)
        }
        return d.copy(work = d.work + Pair(date, nw))
    }

    fun startTimer(kind: String) {
        stopTimer()
        commit(
            data.copy(
                activeTimer = kind,
                timerStart = System.currentTimeMillis(),
                timerDate = LocalDate.now().toString()
            )
        )
    }

    fun stopTimer() {
        val d = data
        if (d.activeTimer.isEmpty()) return
        val secs = min(5 * 3600L, max(0L, (System.currentTimeMillis() - d.timerStart) / 1000))
        commit(
            addWork(d, d.timerDate, d.activeTimer, secs)
                .copy(activeTimer = "", timerStart = 0L, timerDate = "")
        )
    }

    // ---------- Días ----------
    private fun applyScore(d: AppData, date: String, score: Int, missed: Boolean, inp: DayInput?): AppData {
        val last = d.records.takeLast(6).map { it.score } + score
        val avg = last.average()
        val delta = Ladder.lpDelta(d.lp, score, avg)
        val res = Ladder.applyDelta(d.lp, d.shield, delta)
        val rec = DayRecord(
            date = date,
            score = score,
            delta = res.first - d.lp,
            lpAfter = res.first,
            missed = missed,
            morningMin = inp?.morningMin ?: 0,
            studyMin = inp?.studyMin ?: 0,
            distMin = inp?.distMin ?: 0,
            igOpens = inp?.igOpens ?: 0,
            nails = inp?.nails ?: 0,
            loops = inp?.loops ?: 0,
            showered = inp?.showered ?: true,
            overate = inp?.overate ?: false
        )
        return d.copy(lp = res.first, shield = res.second, records = d.records + rec)
    }

    /** Cierra timers viejos y castiga con 0 los días laborales que no evaluaste. */
    fun sync() {
        var d = data
        val today = LocalDate.now()
        if (d.activeTimer.isNotEmpty() && d.timerDate != today.toString()) {
            val secs = min(3 * 3600L, max(0L, (System.currentTimeMillis() - d.timerStart) / 1000))
            d = addWork(d, d.timerDate, d.activeTimer, secs)
                .copy(activeTimer = "", timerStart = 0L, timerDate = "")
        }
        var day = try {
            LocalDate.parse(d.startDate)
        } catch (e: Exception) {
            today
        }
        val have = d.records.map { it.date }.toHashSet()
        while (day.isBefore(today)) {
            if (isWeekday(day) && day.toString() !in have) {
                d = applyScore(d, day.toString(), 0, true, null)
            }
            day = day.plusDays(1)
        }
        if (d != data) commit(d)
        nowMs = System.currentTimeMillis()
    }

    fun hasClosedToday(): Boolean {
        val today = LocalDate.now().toString()
        return data.records.any { it.date == today }
    }

    fun closeDay(input: DayInput): CloseResult {
        stopTimer()
        val today = LocalDate.now().toString()
        val inp = input.copy(
            morningMin = (elapsedSec("morning") / 60).toInt(),
            studyMin = (elapsedSec("study") / 60).toInt()
        )
        val d = data
        val bd = Scoring.score(inp, d.cfg)
        val nd = applyScore(d, today, bd.total, false, inp)
        commit(nd)
        return CloseResult(d.lp, nd.lp, bd.total, nd.lp - d.lp, bd.items, d.shield && !nd.shield)
    }

    fun updateCfg(c: Cfg) {
        commit(data.copy(cfg = c))
    }

    fun resetAll() {
        commit(AppData(startDate = LocalDate.now().toString()))
    }
}
