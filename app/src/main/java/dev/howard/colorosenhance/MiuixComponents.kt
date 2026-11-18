package dev.howard.colorosenhance

import android.animation.ValueAnimator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import dev.howard.colorosenhance.ui.FeatureDemoView
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun SectionHeading(title: String) {
    Text(title, Modifier.padding(start = 12.dp, top = 12.dp, bottom = 8.dp).semantics { heading() },
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

@Composable
internal fun FeatureGlyph(key: String, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Box(modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(colors.primary.copy(alpha = .11f)), contentAlignment = Alignment.Center) {
        Image(SettingsIcons.icon(key), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(colors.primary))
    }
}

@Composable
internal fun DemoCard(page: SettingsLayout.Page, values: Map<String, Any>) {
    val colors=MiuixTheme.colorScheme
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("模拟预览", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("点按重播", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        AndroidView(
            factory = { context -> FeatureDemoView(context, page.demo()).apply { presentation(page.route()); values(values) } },
            modifier = Modifier.fillMaxWidth().heightIn(min = 175.dp, max = 230.dp),
            update = {
                it.presentation(page.route()); it.values(values)
                it.palette(colors.onSurface.toArgb(), colors.onSurfaceVariantSummary.toArgb(), colors.primary.toArgb(), colors.surface.toArgb())
            },
            onRelease = { it.stop() }
        )
    }
}

@Composable
internal fun GlassNavigation(selected: Int, glass: Boolean, backdrop: Backdrop, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val liquid = glass && LocalView.current.isHardwareAccelerated
    val dark = colors.surface.luminance() < .5f
    val shape = remember { RoundedCornerShape(percent = 50) }
    val accent = if (dark) Color(0xff78baff) else Color(0xff006cdb)
    val surfaceTint = if (dark) Color(0xff111722).copy(alpha = .20f) else Color.White.copy(alpha = .18f)
    val surfaceLight = remember(dark) { Brush.verticalGradient(listOf(
        Color.White.copy(alpha = if (dark) .10f else .28f), Color.White.copy(alpha = .01f),
        Color.White.copy(alpha = if (dark) .035f else .07f))) }
    val material = rememberLayerBackdrop()
    val interactions = remember { List(3) { MutableInteractionSource() } }
    val firstPressed by interactions[0].collectIsPressedAsState()
    val secondPressed by interactions[1].collectIsPressedAsState()
    val thirdPressed by interactions[2].collectIsPressedAsState()
    val pressedTab = when { firstPressed -> 0f; secondPressed -> 1f; thirdPressed -> 2f; else -> null }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var touching by remember { mutableStateOf(false) }
    var touchPreview by remember { mutableStateOf<Float?>(null) }
    var contact by remember { mutableStateOf(Offset.Unspecified) }
    var pressSerial by remember { mutableIntStateOf(0) }
    val currentSelection by rememberUpdatedState(selected)
    val selectTab by rememberUpdatedState(onSelect)
    val pressure = remember { Animatable(0f) }
    val releasedPress = remember { intArrayOf(0) }
    val held = touching || dragging != null || pressedTab != null
    LaunchedEffect(held, pressSerial, liquid) {
        if (!liquid || !ValueAnimator.areAnimatorsEnabled()) {
            pressure.snapTo(if (liquid && held) 1f else 0f)
            releasedPress[0] = pressSerial
        } else if (held) {
            pressure.animateTo(1f, spring(dampingRatio = .72f, stiffness = 950f))
        } else if (pressSerial != releasedPress[0] || pressure.value != 0f) {
            releasedPress[0] = pressSerial
            // A short tap must still lift the lens before the release spring.
            if (pressure.value < .88f) pressure.animateTo(.88f, tween(45))
            pressure.animateTo(0f, spring(dampingRatio = .5f, stiffness = 500f))
        }
    }
    val target = dragging ?: touchPreview ?: pressedTab ?: selected.toFloat()
    val position = animateFloatAsState(target,
        if (dragging == null) spring(dampingRatio = .78f, stiffness = 520f) else snap(), label = "glassSelection")
    // Only the current backdrop and interaction changes redraw this small region; no idle ticker.
    val surface = if (liquid) Modifier.drawBackdrop(
        backdrop = backdrop, shape = { shape }, exportedBackdrop = material,
        effects = { val p = pressure.value.coerceIn(0f, 1f); vibrancy(); blur(5.dp.toPx()); lens((14f + 3f * p).dp.toPx(), (20f + 5f * p).dp.toPx(), depthEffect = true) },
        highlight = { Highlight(width = (.8f + .2f * pressure.value.coerceIn(0f, 1f)).dp, blurRadius = .4.dp, alpha = .85f,
            style = HighlightStyle.Default(color = Color.White.copy(alpha = .65f), angle = 35f, falloff = 1.6f)) },
        shadow = { Shadow(radius = 18.dp, offset = DpOffset(0.dp, 5.dp), color = Color.Black.copy(alpha = if (dark) .24f else .12f)) },
        layerBlock = {
            val p = pressure.value.coerceIn(-.18f, 1.15f)
            scaleX = 1f + .012f * p
            scaleY = 1f + .045f * p
            translationY = -1.dp.toPx() * p
        },
        onDrawSurface = { drawRect(surfaceTint); drawRect(surfaceLight) }
    ) else Modifier.background(colors.surfaceContainer, shape)

    BoxWithConstraints(modifier.widthIn(max = 420.dp).fillMaxWidth().height(70.dp).then(surface)) {
        val tabWidth = (maxWidth - 12.dp) / 3
        val density = LocalDensity.current
        val tabWidthPx = with(density) { tabWidth.toPx() }
        val insetPx = with(density) { 6.dp.toPx() }
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val contactGesture = Modifier.pointerInput(tabWidthPx, rtl) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touching = true; contact = down.position; pressSerial++
                val physical = ((down.position.x - insetPx) / tabWidthPx).toInt().coerceIn(0, 2)
                touchPreview = (if (rtl) 2 - physical else physical).toFloat()
                try {
                    do {
                        val point = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                        if (point != null) contact = point.position
                    } while (point != null && point.pressed)
                } finally { touching = false; touchPreview = null }
            }
        }
        val dragGesture = Modifier.pointerInput(tabWidthPx, rtl) {
            detectHorizontalDragGestures(
                onDragStart = { point ->
                    val physical = ((point.x - insetPx) / tabWidthPx - .5f).coerceIn(0f, 2f)
                    dragging = if (rtl) 2f - physical else physical
                },
                onDragEnd = { val target = dragging?.roundToInt()?.coerceIn(0, 2); dragging = null; if (target != null) selectTab(target) },
                onDragCancel = { dragging = null },
                onHorizontalDrag = { change, distance ->
                    change.consume()
                    dragging = ((dragging ?: currentSelection.toFloat()) + distance / tabWidthPx * if (rtl) -1f else 1f).coerceIn(0f, 2f)
                }
            )
        }
        val selectedSurface = if (liquid) Modifier.drawBackdrop(
            backdrop = material, shape = { shape },
            effects = { val p = pressure.value.coerceIn(0f, 1f); lens((11f + 7f * p).dp.toPx(), (14f + 8f * p).dp.toPx(), depthEffect = true, chromaticAberration = true) },
            highlight = { Highlight(width = (.7f + .35f * pressure.value.coerceIn(0f, 1f)).dp, alpha = .8f,
                style = HighlightStyle.Default(color = Color.White.copy(alpha = .65f), angle = 45f)) },
            shadow = null,
            layerBlock = {
                val travel = abs(target - position.value).coerceIn(0f, 1f)
                val p = pressure.value.coerceIn(-.18f, 1.15f)
                scaleX = 1f + .13f * travel + .055f * p
                scaleY = 1f - .065f * travel + .16f * p
                translationY = -1.5.dp.toPx() * p
                rotationZ = (target - position.value).coerceIn(-1f, 1f) * .8f
            },
            onDrawSurface = {
                val p = pressure.value.coerceIn(0f, 1f)
                val physical = if (rtl) 2f - position.value else position.value
                val contactX = if (contact.isSpecified) (contact.x - insetPx - physical * tabWidthPx).coerceIn(size.width * .08f, size.width * .92f) else size.width * .32f
                val contactY = if (contact.isSpecified) (contact.y - insetPx).coerceIn(0f, size.height) else 0f
                drawRect(if (dark) Color.White.copy(alpha = .10f) else Color.White.copy(alpha = .32f))
                drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = .18f + .18f * p), Color.Transparent),
                    center = Offset(size.width * .32f * (1f - p) + contactX * p, contactY * p), radius = size.width * (.9f - .15f * p)))
            }
        ) else Modifier.background(colors.primary.copy(alpha = .12f), shape)
        Box(Modifier.padding(6.dp).offset {
            val logical = position.value.coerceIn(-.035f, 2.035f)
            IntOffset((logical * tabWidthPx).roundToInt(), 0)
        }.width(tabWidth).height(58.dp).then(selectedSurface))
        Row(Modifier.fillMaxSize().then(contactGesture).then(dragGesture).padding(6.dp).selectableGroup()) {
            listOf("首页" to "home", "功能" to "features", "管理" to "manage").forEachIndexed { index, (label, key) ->
                val active = selected == index
                val pressed by interactions[index].collectIsPressedAsState()
                val scale = animateFloatAsState(if (pressed) .92f else 1f,
                    spring(dampingRatio = .72f, stiffness = 700f), label = "tabPress")
                val foreground by animateColorAsState(if (active) accent else colors.onSurface.copy(alpha = .72f), label = "tabColor")
                Column(Modifier.weight(1f).fillMaxHeight().clip(shape)
                    .selectable(active, interactionSource = interactions[index], indication = null, role = Role.Tab, onClick = { pressSerial++; selectTab(index) }),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Image(SettingsIcons.icon(key), null, Modifier.size(24.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
                        colorFilter = ColorFilter.tint(foreground))
                    Spacer(Modifier.height(3.dp))
                    Text(label, color = foreground, fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

internal object SettingsIcons {
    private val cache = mutableMapOf<String, ImageVector>()
    fun icon(name: String): ImageVector = cache.getOrPut(name) {
        ImageVector.Builder("enhance_$name", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                when (name) {
                    "home" -> { moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f); moveTo(5f, 9f); lineTo(5f, 21f); lineTo(10f, 21f); lineTo(10f, 14f); lineTo(14f, 14f); lineTo(14f, 21f); lineTo(19f, 21f); lineTo(19f, 9f) }
                    "cloud" -> { moveTo(7f, 6f); lineTo(17f, 6f); curveTo(24f, 6f, 24f, 18f, 17f, 18f); lineTo(7f, 18f); curveTo(0f, 18f, 0f, 6f, 7f, 6f); moveTo(14f, 10f); lineTo(14f, 14f); moveTo(17f, 9f); lineTo(17f, 15f); moveTo(7f, 11f); lineTo(8f, 11f) }
                    "battery" -> { moveTo(4f, 7f); lineTo(18f, 7f); lineTo(18f, 17f); lineTo(4f, 17f); close(); moveTo(21f, 10f); lineTo(21f, 14f); moveTo(8f, 10f); lineTo(8f, 14f); moveTo(12f, 10f); lineTo(12f, 14f) }
                    "icons" -> { moveTo(4f, 19f); lineTo(4f, 13f); moveTo(9f, 19f); lineTo(9f, 9f); moveTo(14f, 19f); lineTo(14f, 5f); moveTo(20f, 19f); lineTo(20f, 2f) }
                    "aod" -> { ring(12f, 12f, 9f); moveTo(12f, 6f); lineTo(12f, 12f); lineTo(16f, 14f) }
                    "sim" -> { moveTo(8f, 3f); lineTo(19f, 3f); lineTo(19f, 21f); lineTo(5f, 21f); lineTo(5f, 6f); close(); moveTo(9f, 10f); lineTo(15f, 10f); lineTo(15f, 17f); lineTo(9f, 17f); close(); moveTo(12f, 10f); lineTo(12f, 17f) }
                    "installer" -> { moveTo(12f, 3f); lineTo(12f, 14f); moveTo(8f, 10f); lineTo(12f, 14f); lineTo(16f, 10f); moveTo(4f, 15f); lineTo(4f, 21f); lineTo(20f, 21f); lineTo(20f, 15f) }
                    "back" -> { moveTo(15f, 4f); lineTo(7f, 12f); lineTo(15f, 20f) }
                    "manage" -> { ring(12f, 12f, 3f); ring(12f, 12f, 8f); moveTo(12f, 1f); lineTo(12f, 4f); moveTo(12f, 20f); lineTo(12f, 23f); moveTo(1f, 12f); lineTo(4f, 12f); moveTo(20f, 12f); lineTo(23f, 12f) }
                    else -> { moveTo(3f, 3f); lineTo(9f, 3f); lineTo(9f, 9f); lineTo(3f, 9f); close(); moveTo(15f, 3f); lineTo(21f, 3f); lineTo(21f, 9f); lineTo(15f, 9f); close(); moveTo(3f, 15f); lineTo(9f, 15f); lineTo(9f, 21f); lineTo(3f, 21f); close(); moveTo(15f, 15f); lineTo(21f, 15f); lineTo(21f, 21f); lineTo(15f, 21f); close() }
                }
            }
        }.build()
    }
    private fun PathBuilder.ring(x: Float, y: Float, r: Float) {
        val k = r * .5523f
        moveTo(x + r, y); curveTo(x + r, y + k, x + k, y + r, x, y + r)
        curveTo(x - k, y + r, x - r, y + k, x - r, y)
        curveTo(x - r, y - k, x - k, y - r, x, y - r)
        curveTo(x + k, y - r, x + r, y - k, x + r, y); close()
    }
}
