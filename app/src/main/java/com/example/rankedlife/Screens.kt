package com.example.rankedlife

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val Gold = Color(0xFFE0B64A)
val BgColor = Color(0xFF0B1020)
val PanelColor = Color(0xFF151C33)
val ChipOff = Color(0xFF232C45)
val Good = Color(0xFF5BD68A)
val Bad = Color(0xFFFF6B6B)

@Composable
fun RankedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            onPrimary = Color.Black,
            background = BgColor,
            surface = PanelColor,
            onSurface = Color.White,
            onBackground = Color.White
        ),
        content = content
    )
}

fun signed(n: Int): String = if (n > 0) "+$n" else "$n"

fun fmtTime(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return String.format("%d:%02d:%02d", h, m, s)
}

// ============================================================ RAÍZ

@Composable
fun AppRoot(model: AppModel) {
    var tab by remember { mutableStateOf(0) }
    var closing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CloseResult?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            model.nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    val res = result
    Surface(modifier = Modifier.fillMaxSize(), color = BgColor) {
        if (res != null) {
            ResultScreen(res) {
                result = null
                closing = false
                tab = 0
            }
        } else if (closing) {
            CloseScreen(
                model = model,
                onCancel = { closing = false },
                onDone = { r -> result = r }
            )
        } else {
            Scaffold(
                containerColor = BgColor,
                bottomBar = {
                    NavigationBar(containerColor = PanelColor) {
                        val labels = listOf("Hoy", "Rangos", "Top", "Progreso", "Ajustes")
                        val icons = listOf("🏠", "🏆", "👑", "📈", "⚙️")
                        for (i in labels.indices) {
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Text(icons[i]) },
                                label = { Text(labels[i]) }
                            )
                        }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad)) {
                    when (tab) {
                        0 -> HomeScreen(model) { closing = true }
                        1 -> RanksScreen(model)
                        2 -> LeaderboardScreen(model)
                        3 -> ProgressScreen(model)
                        else -> SettingsScreen(model)
                    }
                }
            }
        }
    }
}

// ============================================================ COMPONENTES

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PanelColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun BarProgress(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0x33FFFFFF))
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color)
        )
    }
}

@Composable
fun RankHeader(lp: Int, shield: Boolean) {
    val info = Ladder.info(lp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Emblem(info.tier, Modifier.size(150.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            info.title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = tierColor(info.tier)
        )
        Spacer(Modifier.height(8.dp))
        BarProgress(info.progress, tierColor(info.tier))
        Spacer(Modifier.height(4.dp))
        val txt = if (info.lpNeeded > 0) "${info.lpIn} / ${info.lpNeeded} LP" else "${info.lpIn} LP · rango máximo"
        Text(txt, fontSize = 13.sp)
        Text(
            if (shield) "🛡 Escudo de descenso: activo" else "🛡 Escudo gastado: gana LP para recuperarlo",
            fontSize = 12.sp,
            color = Color(0xFFAAB3CC)
        )
    }
}

@Composable
fun ChoiceRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, label ->
                val sel = i == selected
                Button(
                    onClick = { onSelect(i) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (sel) Gold else ChipOff,
                        contentColor = if (sel) Color.Black else Color.White
                    )
                ) {
                    Text(label, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun Stepper(
    label: String,
    value: Int,
    unit: String,
    step: Int,
    minV: Int,
    maxV: Int,
    onChange: (Int) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        OutlinedButton(
            onClick = { onChange((value - step).coerceAtLeast(minV)) },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(40.dp)
        ) { Text("−") }
        Text("$value$unit", Modifier.width(84.dp), textAlign = TextAlign.Center)
        OutlinedButton(
            onClick = { onChange((value + step).coerceAtMost(maxV)) },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(40.dp)
        ) { Text("+") }
    }
}

@Composable
fun rememberUsageState(model: AppModel, refresh: Int): UsageState {
    val ctx = LocalContext.current
    var st by remember { mutableStateOf(UsageState(false, null)) }
    LaunchedEffect(model.resumeTick, refresh) {
        var perm = Usage.hasPermission(ctx)
        var res: UsageResult? = null
        if (perm) {
            try {
                val today = LocalDate.now()
                val cfg = model.data.cfg
                res = withContext(Dispatchers.IO) {
                    Usage.read(ctx, today, Usage.windowsFor(today, cfg), System.currentTimeMillis())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                perm = false
            }
        }
        st = UsageState(perm, res)
    }
    return st
}

// ============================================================ HOY

@Composable
fun HomeScreen(model: AppModel, onClose: () -> Unit) {
    val d = model.data
    val today = LocalDate.now()
    val weekday = isWeekday(today)
    val closedRec = d.records.firstOrNull { it.date == today.toString() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RankHeader(d.lp, d.shield)
        Spacer(Modifier.height(20.dp))

        if (!weekday) {
            Panel {
                Text("Fin de semana", fontWeight = FontWeight.Bold)
                Text("Hoy no cuenta. Descansa; el lunes se retoma.")
            }
        } else if (closedRec != null) {
            Panel {
                Text("Día cerrado ✔", fontWeight = FontWeight.Bold)
                Text("Puntuación: ${closedRec.score}  ·  LP: ${signed(closedRec.delta)}")
                Text("Mañana puedes volver a sumar.", fontSize = 13.sp)
            }
        } else {
            TimerCard("🌅 Mañana · videos útiles", "morning", d.cfg.morningGoal, model)
            Spacer(Modifier.height(12.dp))
            TimerCard("📚 Estudio para el examen", "study", d.cfg.studyGoal, model)
            Spacer(Modifier.height(12.dp))
            UsageCard(model)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) { Text("Cerrar el día", fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun TimerCard(title: String, kind: String, goalMin: Int, model: AppModel) {
    val sec = model.elapsedSec(kind)
    val running = model.data.activeTimer == kind
    Panel {
        Text(title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(fmtTime(sec) + "  /  meta $goalMin min", fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        BarProgress(sec / 60f / goalMin.coerceAtLeast(1), if (running) Good else Gold)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { if (running) model.stopTimer() else model.startTimer(kind) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (running) Bad else Gold,
                contentColor = Color.Black
            )
        ) { Text(if (running) "Detener" else "Iniciar") }
    }
}

@Composable
fun UsageCard(model: AppModel) {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val st = rememberUsageState(model, refresh)
    Panel {
        Text("📱 Distracciones de hoy", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        if (!st.perm) {
            Text(
                "Para medirlas automáticamente necesito el permiso «Acceso a uso de apps».",
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }) { Text("Dar permiso") }
        } else {
            val r = st.res
            if (r == null) {
                Text("Midiendo…", fontSize = 13.sp)
            } else {
                Text("${r.distMin} min en apps distractoras (horarios que cuentan)")
                Text("Instagram abierto ${r.igOpens} veces en ese horario")
                Text("Desbloqueos del teléfono hoy: ${r.unlocks}", fontSize = 12.sp, color = Color(0xFFAAB3CC))
                if (r.perApp.isNotEmpty()) {
                    val detail = r.perApp.entries.joinToString("  ·  ") { "${it.key} ${it.value} min" }
                    Text(detail, fontSize = 12.sp, color = Color(0xFFAAB3CC))
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { refresh += 1 }) { Text("Actualizar") }
        }
    }
}

// ============================================================ CERRAR DÍA

@Composable
fun CloseScreen(model: AppModel, onCancel: () -> Unit, onDone: (CloseResult) -> Unit) {
    var focus by remember { mutableStateOf(-1) }
    var msgs by remember { mutableStateOf(-1) }
    var stories by remember { mutableStateOf(-1) }
    var wrote by remember { mutableStateOf(-1) }
    var showered by remember { mutableStateOf(-1) }
    var overate by remember { mutableStateOf(-1) }
    var loops by remember { mutableStateOf(-1) }
    var nails by remember { mutableStateOf(-1) }
    var manualDist by remember { mutableStateOf(0) }
    var manualIg by remember { mutableStateOf(0) }
    var confirm by remember { mutableStateOf(false) }

    val st = rememberUsageState(model, 0)
    val auto = st.perm && st.res != null
    val dist = if (auto) st.res!!.distMin else manualDist
    val ig = if (auto) st.res!!.igOpens else manualIg

    val morningMin = (model.elapsedSec("morning") / 60).toInt()
    val studyMin = (model.elapsedSec("study") / 60).toInt()

    val complete = focus >= 0 && msgs >= 0 && stories >= 0 && wrote >= 0 &&
        showered >= 0 && overate >= 0 && loops >= 0 && nails >= 0 &&
        (st.perm == false || st.res != null)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Cierre del día", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Responde con sinceridad. Una vez cerrado no se puede editar.", fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))

        Panel {
            Text("Medido automáticamente", fontWeight = FontWeight.Bold)
            Text("🌅 Videos útiles: $morningMin min")
            Text("📚 Estudio: $studyMin min")
            if (auto) {
                Text("📱 Distracciones: $dist min")
                Text("📸 Instagram abierto: $ig veces")
            } else {
                Text(
                    "No tengo permiso de uso de apps; captura esto a mano:",
                    fontSize = 13.sp,
                    color = Color(0xFFAAB3CC)
                )
                Stepper("Min en apps distractoras", manualDist, " min", 5, 0, 600) { manualDist = it }
                Stepper("Veces que abriste Instagram", manualIg, "", 1, 0, 100) { manualIg = it }
            }
        }

        Spacer(Modifier.height(12.dp))
        Panel {
            Text("Checklist", fontWeight = FontWeight.Bold)
            ChoiceRow("Mi jornada de trabajo fue con atención", listOf("Nada", "Poco", "Bastante", "Total"), focus) { focus = it }
            ChoiceRow("Respondí los mensajes pendientes", listOf("Ninguno", "Algunos", "Todos / no había"), msgs) { msgs = it }
            ChoiceRow("Respondí historias o notas que quería", listOf("Ninguna", "Algunas", "Todas / no había"), stories) { stories = it }
            ChoiceRow("Le escribí a alguien que debía o quería escribirle", listOf("No", "Sí"), wrote) { wrote = it }
            ChoiceRow("Me bañé antes de las 9 pm", listOf("No", "Sí"), showered) { showered = it }
            ChoiceRow("Comí de más en la noche", listOf("No", "Sí"), overate) { overate = it }
            ChoiceRow("Loops (canción o clip repetido) que me quitaron foco", listOf("Nada", "Poco", "Mucho"), loops) { loops = it }
            ChoiceRow("Me mordí las uñas", listOf("Nada", "Poco", "Mucho"), nails) { nails = it }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { confirm = true },
            enabled = complete,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) { Text(if (complete) "Calcular y cerrar día" else "Responde todo para continuar") }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
        Spacer(Modifier.height(24.dp))
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Cerrar el día?") },
            text = { Text("Se calculará tu puntuación y ya no podrás cambiar estas respuestas.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    val inp = DayInput(
                        workFocus = focus,
                        msgs = msgs,
                        stories = stories,
                        wroteSomeone = wrote == 1,
                        showered = showered == 1,
                        overate = overate == 1,
                        loops = loops,
                        nails = nails,
                        distMin = dist,
                        igOpens = ig
                    )
                    onDone(model.closeDay(inp))
                }) { Text("Cerrar") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Revisar") }
            }
        )
    }
}

@Composable
fun ResultScreen(r: CloseResult, onDone: () -> Unit) {
    val before = Ladder.info(r.oldLp)
    val after = Ladder.info(r.newLp)
    val up = Ladder.divisionFloor(r.newLp) > Ladder.divisionFloor(r.oldLp)
    val down = Ladder.divisionFloor(r.newLp) < Ladder.divisionFloor(r.oldLp)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Resultado del día", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("${r.score}", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Gold)
        Text("puntos del día", fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Emblem(before.tier, Modifier.size(90.dp))
            Text("   →   ", fontSize = 24.sp)
            Emblem(after.tier, Modifier.size(90.dp))
        }
        Text("${before.title}  →  ${after.title}", fontWeight = FontWeight.Bold, color = tierColor(after.tier))
        Text(
            "LP: ${signed(r.delta)}",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = if (r.delta > 0) Good else if (r.delta < 0) Bad else Color.White
        )
        if (up) Text("🎉 ¡Subiste de división o rango!", color = Good)
        if (down) Text("📉 Bajaste de división o rango.", color = Bad)
        if (r.shieldUsed) Text("🛡 El escudo evitó que bajaras. Gana LP para recuperarlo.", color = Gold, textAlign = TextAlign.Center)

        Spacer(Modifier.height(16.dp))
        Panel {
            Text("Desglose", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            for (item in r.items) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.first, Modifier.weight(1f), fontSize = 13.sp)
                    Text(
                        String.format("%+.1f", item.second),
                        fontSize = 13.sp,
                        color = if (item.second > 0.05) Good else if (item.second < -0.05) Bad else Color(0xFFAAB3CC)
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Listo") }
        Spacer(Modifier.height(24.dp))
    }
}

// ============================================================ RANGOS

@Composable
fun RanksScreen(model: AppModel) {
    val cur = Ladder.tierIndex(model.data.lp)
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        items(Ladder.tiers.size) { i ->
            val t = Ladder.tiers[i]
            val next = if (i < Ladder.tiers.size - 1) Ladder.starts[i + 1] else -1
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (i == cur) Color(0xFF26304F) else PanelColor
                )
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Emblem(i, Modifier.size(76.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            t.name + if (i == cur) "  ← estás aquí" else "",
                            fontWeight = FontWeight.Bold,
                            color = tierColor(i)
                        )
                        val range = if (next > 0) "${Ladder.starts[i]} – ${next - 1} LP" else "${Ladder.starts[i]}+ LP"
                        Text(range, fontSize = 12.sp)
                        Text("Día mínimo para no perder: ${t.eq}", fontSize = 12.sp)
                        Text("Máx. +${t.maxGain} / −${t.maxLoss} LP por día", fontSize = 12.sp, color = Color(0xFFAAB3CC))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ============================================================ PROGRESO

@Composable
fun LpChart(points: List<Int>, modifier: Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val mn = points.min()
        val mx = points.max()
        val range = maxOf(1, mx - mn).toFloat()
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = size.width * i / (points.size - 1)
            val y = size.height - (v - mn) / range * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Gold, style = Stroke(width = 5f))
    }
}

@Composable
fun ProgressScreen(model: AppModel) {
    val d = model.data
    val recs = d.records
    val fmt = DateTimeFormatter.ofPattern("dd/MM")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Progreso", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (recs.isEmpty()) {
            Panel { Text("Aún no hay días cerrados. Cierra tu primer día para ver tu progreso.") }
        } else {
            val last7 = recs.takeLast(7)
            val avg7 = last7.map { it.score }.average()
            var streak = 0
            for (r in recs.reversed()) {
                if (r.delta > 0) streak++ else break
            }
            Panel {
                Text("Resumen", fontWeight = FontWeight.Bold)
                Text("Días registrados: ${recs.size}")
                Text("Promedio últimos 7 días: ${String.format("%.1f", avg7)}")
                Text("Racha de días subiendo: $streak")
            }

            Spacer(Modifier.height(12.dp))
            Panel {
                Text("LP de los últimos días", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val pts = listOf(0) + recs.map { it.lpAfter }
                LpChart(pts.takeLast(30), Modifier.fillMaxWidth().height(140.dp))
            }

            Spacer(Modifier.height(12.dp))
            val last14 = recs.takeLast(14).filter { !it.missed }
            if (last14.isNotEmpty()) {
                val n = last14.size
                Panel {
                    Text("Obstáculos (últimos $n días evaluados)", fontWeight = FontWeight.Bold)
                    Text("Me mordí las uñas: ${last14.count { it.nails > 0 }} de $n días")
                    Text("Loops que quitaron foco: ${last14.count { it.loops > 0 }} de $n días")
                    Text("Comí de más en la noche: ${last14.count { it.overate }} de $n días")
                    Text("No me bañé antes de las 9 pm: ${last14.count { !it.showered }} de $n días")
                    Text("Distracciones: ${last14.map { it.distMin }.average().toInt()} min por día en promedio")
                    Text("Instagram de reflejo: ${String.format("%.1f", last14.map { it.igOpens }.average())} aperturas por día")
                }
                Spacer(Modifier.height(12.dp))
            }

            Panel {
                Text("Historial", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                for (r in recs.takeLast(14).reversed()) {
                    val label = try {
                        LocalDate.parse(r.date).format(fmt)
                    } catch (e: Exception) {
                        r.date
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label + if (r.missed) "  (sin evaluar)" else "", fontSize = 13.sp)
                        Text(
                            "${r.score} pts  ·  ${signed(r.delta)} LP",
                            fontSize = 13.sp,
                            color = if (r.delta > 0) Good else if (r.delta < 0) Bad else Color.White
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ============================================================ AJUSTES

@Composable
fun SettingsScreen(model: AppModel) {
    val ctx = LocalContext.current
    val c = model.data.cfg
    val st = rememberUsageState(model, 0)
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Ajustes", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Panel {
            Text("Metas diarias", fontWeight = FontWeight.Bold)
            Stepper("Videos útiles (mañana)", c.morningGoal, " min", 10, 10, 240) {
                model.updateCfg(c.copy(morningGoal = it))
            }
            Stepper("Estudio para el examen", c.studyGoal, " min", 15, 15, 480) {
                model.updateCfg(c.copy(studyGoal = it))
            }
        }

        Spacer(Modifier.height(12.dp))
        Panel {
            Text("Horarios donde cuentan las distracciones", fontWeight = FontWeight.Bold)
            Stepper("Bloque 1 desde", c.workStart, ":00", 1, 0, 23) { model.updateCfg(c.copy(workStart = it)) }
            Stepper("Bloque 1 hasta", c.workEnd, ":00", 1, 1, 24) { model.updateCfg(c.copy(workEnd = it)) }
            Stepper("Bloque 2 desde", c.studyStart, ":00", 1, 0, 23) { model.updateCfg(c.copy(studyStart = it)) }
            Stepper("Bloque 2 hasta", c.studyEnd, ":00", 1, 1, 24) { model.updateCfg(c.copy(studyEnd = it)) }
            Text(
                "YouTube, Instagram, TikTok y otras apps solo penalizan dentro de estos dos bloques (por defecto 9 am a 2 pm y 5 pm a 10 pm). Fuera de ellos no cuentan.",
                fontSize = 12.sp,
                color = Color(0xFFAAB3CC)
            )
        }

        Spacer(Modifier.height(12.dp))
        Panel {
            Text("Permiso de uso de apps", fontWeight = FontWeight.Bold)
            Text(if (st.perm) "Concedido ✔" else "No concedido ✘")
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }) { Text("Abrir ajustes de acceso a uso") }
        }

        Spacer(Modifier.height(12.dp))
        Panel {
            Text("Reglas", fontWeight = FontWeight.Bold)
            Text(
                "• Solo cuentan los días de lunes a viernes.\n" +
                    "• Un día laboral que no cierres cuenta como 0 puntos.\n" +
                    "• Mientras más alto el rango, más alto debe ser tu día para no perder.",
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { confirmReset = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Reiniciar todo el progreso", color = Bad) }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("¿Reiniciar todo?") },
            text = { Text("Perderás tu rango, LP e historial. Esto no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    model.resetAll()
                }) { Text("Reiniciar", color = Bad) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancelar") }
            }
        )
    }
}
