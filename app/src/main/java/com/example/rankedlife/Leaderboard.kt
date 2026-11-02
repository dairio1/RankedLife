package com.example.rankedlife

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

class Entry(val rank: Int, val name: String, val lp: Int, val isYou: Boolean)

class Board(
    val entries: List<Entry>,
    val inSovereign: Boolean,
    val yourRank: Int,
    val yourLp: Int,
    val gapNext: Int,
    val gapFirst: Int,
    val needToEnter: Int
)

/** Jugadores inventados que siguen escalando cada día, calculados a partir de la fecha. */
object Leaderboard {
    const val N = 100

    private val epoch: LocalDate = LocalDate.of(2026, 11, 1)

    private val top = listOf(
        "Zerathos", "Nyxara", "Kaiverge", "Solvane", "Vexillar",
        "Auroxis", "Thalorin", "Ignivex", "Quillon", "Draxomir"
    )
    private val pre = listOf(
        "Shadow", "Lunar", "Crimson", "Apex", "Nova", "Iron", "Silent", "Ghost", "Zenith", "Rogue",
        "Frost", "Ember", "Void", "Titan", "Echo", "Pixel", "Vortex", "Solar", "Mystic", "Cobalt"
    )
    private val suf = listOf(
        "Wolf", "Fox", "Reaper", "Knight", "Hawk", "Viper", "Monk", "Raven", "Storm", "Blade"
    )

    fun name(i: Int): String {
        if (i < top.size) return top[i]
        val p = pre[i % 20]
        val s = suf[((i % 20) * 3 + (i / 20) * 2) % 10]
        return p + s
    }

    private fun hash(i: Int, k: Int): Double {
        val x = Math.sin(i * 12.9898 + k * 78.233) * 43758.5453
        return x - Math.floor(x)
    }

    /** LP absolutos del jugador i (0 = el mejor) después de t días desde la fecha base. */
    private fun botLp(i: Int, t: Int): Int {
        val u = (N - 1 - i).toDouble() / (N - 1)
        val off = 40.0 + 3560.0 * Math.pow(u, 2.2)
        val amp = 200.0 + 0.9 * off
        val tau = 260.0 + 160.0 * hash(i, 1)
        val growth = amp * (1.0 - Math.exp(-t / tau))
        val wob = 14.0 * Math.sin(t * (0.35 + 0.5 * hash(i, 2)) + 6.28 * hash(i, 3))
        return (Ladder.starts[10] + off + growth + wob).roundToInt()
    }

    fun build(userLp: Int, today: LocalDate): Board {
        val t = max(0, ChronoUnit.DAYS.between(epoch, today).toInt())
        val sov = Ladder.starts[10]
        val bots = (0 until N)
            .map { Triple(it, name(it), botLp(it, t)) }
            .sortedByDescending { it.third }

        val inSov = userLp >= sov
        val entries = ArrayList<Entry>()
        var rank = 1
        var yourRank = 0
        var placed = false
        for (b in bots) {
            if (inSov && !placed && userLp > b.third) {
                entries.add(Entry(rank, "Tú", userLp - sov, true))
                yourRank = rank
                rank++
                placed = true
            }
            entries.add(Entry(rank, b.second, b.third - sov, false))
            rank++
        }
        if (inSov && !placed) {
            entries.add(Entry(rank, "Tú", userLp - sov, true))
            yourRank = rank
        }

        var gapNext = 0
        var gapFirst = 0
        if (inSov && yourRank > 1) {
            gapNext = entries[yourRank - 2].lp - (userLp - sov) + 1
            gapFirst = entries[0].lp - (userLp - sov) + 1
        }
        return Board(
            entries = entries,
            inSovereign = inSov,
            yourRank = yourRank,
            yourLp = userLp - sov,
            gapNext = gapNext,
            gapFirst = gapFirst,
            needToEnter = max(0, sov - userLp)
        )
    }
}

@Composable
fun LeaderboardScreen(model: AppModel) {
    val d = model.data
    val today = LocalDate.now()
    val board = remember(d.lp, today.toString()) { Leaderboard.build(d.lp, today) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column {
                Spacer(Modifier.height(8.dp))
                Panel {
                    Text("👑 Sovereign Leaderboard", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    if (board.inSovereign) {
                        Text(
                            "Tu puesto: #${board.yourRank} de ${board.entries.size}",
                            fontWeight = FontWeight.Bold,
                            color = Gold
                        )
                        Text("Tus LP en Sovereign: ${board.yourLp}")
                        if (board.yourRank > 1) {
                            Text("Para subir un puesto: ${board.gapNext} LP")
                            Text("Para el #1: ${board.gapFirst} LP")
                            Text(
                                "≈ ${(board.gapFirst + 12) / 13} días perfectos, si nadie más avanzara.",
                                fontSize = 12.sp,
                                color = Color(0xFFAAB3CC)
                            )
                        } else {
                            Text("¡Eres el #1! Defiéndelo todos los días.", color = Good)
                        }
                    } else {
                        val info = Ladder.info(d.lp)
                        Text("Tu rango actual: ${info.title}")
                        Text("Te faltan ${board.needToEnter} LP para entrar a Sovereign.", fontWeight = FontWeight.Bold, color = Gold)
                        Text("Entrarás en el puesto #${Leaderboard.N + 1}: el último.")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Los demás jugadores siguen escalando cada día.",
                        fontSize = 12.sp,
                        color = Color(0xFFAAB3CC)
                    )
                }
            }
        }
        items(board.entries.size) { idx ->
            val e = board.entries[idx]
            val bg = if (e.isYou) Color(0xFF3A3220) else PanelColor
            val fg = if (e.isYou) Gold else Color.White
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = bg)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val medal = when (e.rank) {
                        1 -> "🥇"
                        2 -> "🥈"
                        3 -> "🥉"
                        else -> "#${e.rank}"
                    }
                    Text(medal, Modifier.width(56.dp), fontWeight = FontWeight.Bold, color = fg)
                    Text(
                        if (e.isYou) "Tú" else e.name,
                        Modifier.weight(1f),
                        color = fg,
                        fontWeight = if (e.isYou) FontWeight.Bold else FontWeight.Normal
                    )
                    Text("${e.lp} LP", color = fg)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
