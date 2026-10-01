package org.openiisu.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openiisu.core.ConsoleConfig
import org.openiisu.core.Rom

enum class LayoutMode(val label: String) { Grid("Grid"), Xmb("XMB"), Wii("Pages") }

/** Light/dark palette in the soft style of the iiSU presentation. */
class Palette(val dark: Boolean) {
    val bg = if (dark) Color(0xFF16161C) else Color(0xFFF1F1F5)
    val surface = if (dark) Color(0xFF24242E) else Color.White
    val text = if (dark) Color(0xFFF2F2F7) else Color(0xFF25252E)
    val muted = if (dark) Color(0xFF9A9AAA) else Color(0xFF8A8A99)
}

private val gradients = listOf(
    Color(0xFF22C1E8) to Color(0xFF3B82F6), Color(0xFFF59E0B) to Color(0xFFF97316),
    Color(0xFF34D399) to Color(0xFF14B8A6), Color(0xFFA78BFA) to Color(0xFF7C3AED),
    Color(0xFFF472B6) to Color(0xFFEF4444), Color(0xFFFACC15) to Color(0xFFF59E0B),
)

private fun gradientFor(key: String): Brush {
    val (a, b) = gradients[(key.hashCode() and 0x7fffffff) % gradients.size]
    return Brush.linearGradient(listOf(a, b))
}

@Composable
fun RomCard(rom: Rom, focused: Boolean, size: Int, p: Palette, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (focused) 1.1f else 1f)
    Column(
        Modifier.size(size.dp).scale(scale)
            .shadow(if (focused) 18.dp else 4.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp)).background(gradientFor(rom.id)).clickable(onClick = onClick).padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(rom.title.take(1).uppercase(), fontSize = (size / 3).sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f))
        Text(rom.title, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ConsoleBar(consoles: List<ConsoleConfig>, selected: Int, p: Palette, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        itemsIndexed(consoles) { i, c ->
            val on = i == selected
            Box(
                Modifier.size(if (on) 56.dp else 44.dp).clip(CircleShape)
                    .background(if (on) gradientFor(c.shortName) else Brush.linearGradient(listOf(p.surface, p.surface)))
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(c.shortName.uppercase().take(3), color = if (on) Color.White else p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun GridLayout(roms: List<Rom>, sel: Int, p: Palette, onSel: (Int) -> Unit, onLaunch: (Rom) -> Unit) {
    val st = rememberLazyGridState()
    LaunchedEffect(sel) { if (roms.isNotEmpty()) st.animateScrollToItem((sel / 3 - 1).coerceAtLeast(0) * 3) }
    LazyHorizontalGrid(GridCells.Fixed(3), state = st, contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
        itemsIndexed(roms) { i, r -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { RomCard(r, i == sel, 140, p) { onSel(i); onLaunch(r) } } }
    }
}

@Composable
fun XmbLayout(roms: List<Rom>, sel: Int, p: Palette, onSel: (Int) -> Unit, onLaunch: (Rom) -> Unit) {
    val st = rememberLazyListState()
    LaunchedEffect(sel) { if (roms.isNotEmpty()) st.animateScrollToItem(sel, scrollOffset = -180) }
    LazyColumn(state = st, contentPadding = PaddingValues(vertical = 200.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(roms) { i, r ->
            val f = i == sel
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                RomCard(r, f, if (f) 150 else 90, p) { onSel(i); onLaunch(r) }
                if (f) Text(r.title, color = p.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun PagedLayout(roms: List<Rom>, sel: Int, p: Palette, onSel: (Int) -> Unit, onLaunch: (Rom) -> Unit) {
    val perPage = 15
    val page = sel / perPage
    val pages = ((roms.size + perPage - 1) / perPage).coerceAtLeast(1)
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text("◀", color = if (page > 0) p.text else p.muted, fontSize = 28.sp,
            modifier = Modifier.padding(12.dp).clickable(enabled = page > 0) { onSel((page - 1) * perPage) })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            for (row in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (col in 0 until 5) {
                    val i = page * perPage + row * 5 + col
                    if (i < roms.size) RomCard(roms[i], i == sel, 120, p) { onSel(i); onLaunch(roms[i]) } else Spacer(Modifier.size(120.dp))
                }
            }
            Text("${page + 1} / $pages", color = p.muted)
        }
        Text("▶", color = if (page < pages - 1) p.text else p.muted, fontSize = 28.sp,
            modifier = Modifier.padding(12.dp).clickable(enabled = page < pages - 1) { onSel((page + 1) * perPage) })
    }
}

/**
 * Whole launcher screen. Keys: arrows move, Enter launches, [ / ] switch console,
 * 1/2/3 switch layout, D toggles dark mode, R rescans.
 */
@Composable
fun LauncherScreen(state: AppState) {
    var dark by remember { mutableStateOf(false) }
    val p = Palette(dark)
    var library by remember { mutableStateOf(state.scanner.scan(state.romRoot)) }
    val consoles = library.keys.toList()
    var cIdx by remember { mutableStateOf(0) }
    var sel by remember { mutableStateOf(0) }
    var mode by remember { mutableStateOf(LayoutMode.Grid) }
    var status by remember { mutableStateOf("ROM folder: ${state.romRoot.path}") }
    val roms = consoles.getOrNull(cIdx)?.let { library[it] }.orEmpty()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun pick(i: Int) { cIdx = i.coerceIn(0, (consoles.size - 1).coerceAtLeast(0)); sel = 0 }
    fun launch(r: Rom) { status = state.launch(r) }
    fun move(delta: Int) { if (roms.isNotEmpty()) sel = (sel + delta).coerceIn(0, roms.size - 1) }

    Column(
        Modifier.fillMaxSize().background(p.bg).focusRequester(focus).focusable().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val v = when (mode) { LayoutMode.Grid -> 3; LayoutMode.Xmb -> 1; LayoutMode.Wii -> 5 }
            when (e.key) {
                Key.DirectionRight -> { if (mode == LayoutMode.Xmb) pick(cIdx + 1) else move(v); true }
                Key.DirectionLeft -> { if (mode == LayoutMode.Xmb) pick(cIdx - 1) else move(-v); true }
                Key.DirectionDown -> { move(if (mode == LayoutMode.Grid) 1 else if (mode == LayoutMode.Xmb) 1 else 1); true }
                Key.DirectionUp -> { move(-1); true }
                Key.Enter -> { roms.getOrNull(sel)?.let(::launch); true }
                Key.RightBracket -> { pick(cIdx + 1); true }
                Key.LeftBracket -> { pick(cIdx - 1); true }
                Key.One -> { mode = LayoutMode.Grid; true }
                Key.Two -> { mode = LayoutMode.Xmb; true }
                Key.Three -> { mode = LayoutMode.Wii; true }
                Key.D -> { dark = !dark; true }
                Key.R -> { library = state.scanner.scan(state.romRoot); pick(0); true }
                else -> false
            }
        }.padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("openiisu", color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(24.dp))
            Box(Modifier.weight(1f)) { ConsoleBar(consoles, cIdx, p) { pick(it) } }
            LayoutMode.entries.forEach { m ->
                Text(m.label, color = if (m == mode) p.text else p.muted, fontWeight = if (m == mode) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(8.dp).clickable { mode = m })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (roms.isEmpty()) Text("No ROMs found in ${state.romRoot}/<console>/ — add some and press R.", color = p.muted, modifier = Modifier.align(Alignment.Center))
            else when (mode) {
                LayoutMode.Grid -> GridLayout(roms, sel, p, { sel = it }, ::launch)
                LayoutMode.Xmb -> XmbLayout(roms, sel, p, { sel = it }, ::launch)
                LayoutMode.Wii -> PagedLayout(roms, sel, p, { sel = it }, ::launch)
            }
        }
        Text("$status    ·    ←↑↓→ move   Enter play   [ ] console   1/2/3 layout   D dark   R rescan", color = p.muted, fontSize = 12.sp)
    }
}
