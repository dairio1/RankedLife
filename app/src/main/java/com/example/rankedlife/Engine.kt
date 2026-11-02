package com.example.rankedlife

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Un rango: [eq] = puntuación mínima del día para no perder LP. */
class Tier(val name: String, val eq: Int, val maxGain: Int, val maxLoss: Int)

class RankInfo(
    val tier: Int,
    val name: String,
    val division: String,
    val lpIn: Int,
    val lpNeeded: Int,
    val progress: Float
) {
    val title: String
        get() = if (division.isEmpty()) name else "$name $division"
}

/** Metas y horarios editables. */
data class Cfg(
    val morningGoal: Int = 120,
    val studyGoal: Int = 120,
    val workStart: Int = 9,
    val workEnd: Int = 14,
    val studyStart: Int = 17,
    val studyEnd: Int = 22
)

data class DayInput(
    val morningMin: Int = 0,
    val studyMin: Int = 0,
    val workFocus: Int = 0,      // 0..3
    val msgs: Int = 0,           // 0 ninguno, 1 algunos, 2 todos / no había
    val stories: Int = 0,        // 0 ninguna, 1 algunas, 2 todas / no había
    val wroteSomeone: Boolean = false,
    val showered: Boolean = false,
    val overate: Boolean = false,
    val loops: Int = 0,          // 0 nada, 1 poco, 2 mucho
    val nails: Int = 0,          // 0 nada, 1 poco, 2 mucho
    val distMin: Int = 0,
    val igOpens: Int = 0
)

class Breakdown(val items: List<Pair<String, Double>>, val total: Int)

object Ladder {
    val tiers = listOf(
        Tier("Iron", 35, 104, 20),
        Tier("Bronze", 42, 80, 24),
        Tier("Silver", 50, 64, 30),
        Tier("Gold", 58, 53, 36),
        Tier("Platinum", 66, 45, 40),
        Tier("Emerald", 73, 38, 44),
        Tier("Diamond", 80, 34, 50),
        Tier("Master", 86, 29, 56),
        Tier("Grandmaster", 91, 25, 60),
        Tier("Challenger", 95, 22, 70),
        Tier("Sovereign", 97, 10, 80)
    )

    /** LP absolutos donde empieza cada rango. Iron a Diamond: 4 divisiones de 100 LP. */
    val starts = intArrayOf(0, 400, 800, 1200, 1600, 2000, 2400, 2800, 3020, 3210, 3370)

    const val DIV_TIERS = 7

    fun tierIndex(lp: Int): Int {
        var t = 0
        for (k in starts.indices) {
            if (lp >= starts[k]) t = k
        }
        return t
    }

    fun divisionFloor(lp: Int): Int {
        val t = tierIndex(lp)
        return if (t < DIV_TIERS) starts[t] + ((lp - starts[t]) / 100) * 100 else starts[t]
    }

    fun info(lp: Int): RankInfo {
        val t = tierIndex(lp)
        val name = tiers[t].name
        if (t < DIV_TIERS) {
            val within = lp - starts[t]
            val idx = (within / 100).coerceIn(0, 3)
            val roman = listOf("IV", "III", "II", "I")[idx]
            val lpIn = within - idx * 100
            return RankInfo(t, name, roman, lpIn, 100, lpIn / 100f)
        }
        val lpIn = lp - starts[t]
        val needed = if (t < tiers.size - 1) starts[t + 1] - starts[t] else 0
        val prog = if (needed > 0) lpIn.toFloat() / needed else 1f
        return RankInfo(t, name, "", lpIn, needed, prog.coerceIn(0f, 1f))
    }

    /** Cuántos LP ganas o pierdes con [score] (0..100). [avg7] = promedio de los últimos 7 días. */
    fun lpDelta(lp: Int, score: Int, avg7: Double): Int {
        val tier = tiers[tierIndex(lp)]
        if (score >= tier.eq) {
            val base = tier.maxGain * (score - tier.eq).toDouble() / (100 - tier.eq)
            val form = if (avg7 >= tier.eq) {
                1.0 + 0.25 * (avg7 - tier.eq) / (100 - tier.eq)
            } else {
                max(0.5, 1.0 - (tier.eq - avg7) / 20.0)
            }
            return (base * form).roundToInt()
        }
        return -(tier.maxLoss * (tier.eq - score).toDouble() / tier.eq).roundToInt()
    }

    /** Aplica el cambio con escudo de descenso. Devuelve (nuevos LP, escudo). */
    fun applyDelta(lp: Int, shield: Boolean, delta: Int): Pair<Int, Boolean> {
        if (delta >= 0) {
            return Pair(lp + delta, if (delta > 0) true else shield)
        }
        val floor = divisionFloor(lp)
        val raw = lp + delta
        if (raw >= floor) return Pair(raw, shield)
        if (shield) return Pair(floor, false)
        return Pair(max(0, raw), false)
    }
}

object Scoring {
    fun score(i: DayInput, c: Cfg): Breakdown {
        val items = ArrayList<Pair<String, Double>>()
        items.add(Pair("Videos útiles de la mañana", 15.0 * min(1.0, i.morningMin.toDouble() / max(1, c.morningGoal))))
        items.add(Pair("Trabajo con atención", 20.0 * i.workFocus / 3.0))
        items.add(Pair("Estudio para el examen", 30.0 * min(1.0, i.studyMin.toDouble() / max(1, c.studyGoal))))
        items.add(Pair("Mensajes pendientes", when (i.msgs) { 2 -> 5.0; 1 -> 2.5; else -> 0.0 }))
        items.add(Pair("Historias y notas", when (i.stories) { 2 -> 5.0; 1 -> 2.5; else -> 0.0 }))
        items.add(Pair("Escribir a quien debías", if (i.wroteSomeone) 5.0 else 0.0))
        items.add(Pair("Bañarte antes de las 9 pm", if (i.showered) 5.0 else 0.0))
        items.add(Pair("Noche tranquila", if (i.overate) 0.0 else 10.0))
        items.add(Pair("Evaluación honesta", 5.0))

        val dist = max(0, i.distMin - 10) / 4.0
        items.add(Pair("Distracciones (en horario que cuenta)", -min(15.0, dist)))
        items.add(Pair("Instagram de reflejo", -min(10.0, max(0, i.igOpens - 3).toDouble())))
        items.add(Pair("Loops que quitaron foco", when (i.loops) { 2 -> -5.0; 1 -> -2.0; else -> 0.0 }))
        items.add(Pair("Morderte las uñas", when (i.nails) { 2 -> -4.0; 1 -> -2.0; else -> 0.0 }))

        val total = items.sumOf { it.second }
        return Breakdown(items, total.roundToInt().coerceIn(0, 100))
    }
}
