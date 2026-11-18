package dev.howard.colorosenhance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.howard.colorosenhance.icons.IconOrder
import dev.howard.colorosenhance.icons.IconOrderEditor
import dev.howard.colorosenhance.icons.StatusIconCatalog
import dev.howard.colorosenhance.icons.StatusIconConfig
import dev.howard.colorosenhance.installer.InstallerChoices
import dev.howard.colorosenhance.settings.NativeNumberInput
import dev.howard.colorosenhance.ui.WaveBandAxisView
import dev.lsposed.fluidcloud.MediaWaveBands
import java.math.BigDecimal
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.window.WindowDialog

/** Only the standalone Activity references Compose. Native Settings uses the Java presentation routes. */
object MiuixSettings {
    @JvmStatic fun install(activity: MainActivity, route: String): Runnable {
        val updates = mutableIntStateOf(0)
        activity.enableEdgeToEdge()
        activity.setContent { SettingsApp(activity, route, updates.intValue) }
        return Runnable { updates.intValue++ }
    }
}

private sealed interface SettingsModal {
    data class Number(val group: String, val item: SettingsCatalog.Item, val revision: Long, val value: Int) : SettingsModal
    data class Bands(val revision: Long, val bands: MediaWaveBands) : SettingsModal
    data class Choice(val title: String, val labels: List<String>, val selected: Int, val choose: (Int) -> Unit) : SettingsModal
    data class Confirm(val title: String, val message: String, val confirm: () -> Unit) : SettingsModal
    data class Order(val revision: Long, val order: IconOrder) : SettingsModal
    data object Licenses : SettingsModal
}

@Composable
private fun SettingsApp(activity: MainActivity, route: String, update: Int) {
    val app = activity.application as EnhanceApplication
    val controller = remember { SettingsUiController(app.settings()) }
    val preferences = remember { app.getSharedPreferences("ui_presentation", Context.MODE_PRIVATE) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var glass by rememberSaveable { mutableStateOf(preferences.getBoolean("liquid_navigation", true)) }
    var appearance by rememberSaveable { mutableIntStateOf(preferences.getInt("appearance", 0).coerceIn(0, 2)) }
    var modal by remember { mutableStateOf<SettingsModal?>(null) }
    var localUpdate by remember { mutableIntStateOf(0) }
    val revisionSignal = update + localUpdate
    val launchRelease = if (activity.canShowUpdatePrompt() && modal == null) app.launchUpdates().pending() else null
    val lightBars = when (appearance) { 1 -> true; 2 -> false; else -> !isSystemInDarkTheme() }
    SideEffect {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }
    val theme = remember(appearance) {
        ThemeController(when (appearance) { 1 -> ColorSchemeMode.MonetLight; 2 -> ColorSchemeMode.MonetDark; else -> ColorSchemeMode.MonetSystem })
    }
    val root = route == "home"
    val tabStates = rememberSaveableStateHolder()
    val page = remember(route) { SettingsLayout.page(route) }
    val title = if (!root) page?.title() ?: "流体云增强" else listOf("流体云增强", "功能", "管理")[tab]
    fun open(target: String) { activity.startActivity(Intent(activity, MainActivity::class.java).putExtra("group", target)) }
    fun feedback(status: String) {
        // Read acknowledged data in this same composition after a commit, so slider release
        // cannot briefly reveal the old parent snapshot while the app observer is still queued.
        localUpdate++
        if (status != "ok") toast(activity, resultMessage(status))
    }
    MiuixTheme(controller = theme) {
        val backdrop = rememberLayerBackdrop()
        val colors = MiuixTheme.colorScheme
        val pageDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
        Box(Modifier.fillMaxSize().background(colors.surface)) {
            val recording = if (root && glass && LocalView.current.isHardwareAccelerated) Modifier.layerBackdrop(backdrop) else Modifier
            Box(Modifier.fillMaxSize().then(recording)) {
                AnimatedContent(
                    targetState = if (root) tab else -1,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        if (initialState < 0 || targetState < 0) EnterTransition.None togetherWith ExitTransition.None
                        else {
                            val direction = (if (targetState > initialState) 1 else -1) * pageDirection
                            ((slideInHorizontally(spring(dampingRatio = .88f, stiffness = 600f)) { (it * .06f * direction).roundToInt() } + fadeIn(tween(200)))
                                togetherWith (slideOutHorizontally(tween(200)) { -(it * .035f * direction).roundToInt() } + fadeOut(tween(130))))
                                .using(SizeTransform(clip = false))
                        }
                    }, label = "rootPageTransition"
                ) { displayTab ->
                Scaffold(
                    topBar = {
                        TopAppBar(title = if (root) listOf("流体云增强", "功能", "管理")[displayTab] else title, navigationIcon = {
                            if (!root) Box(Modifier.size(48.dp).clickable { activity.finish() }, contentAlignment = Alignment.Center) {
                                Image(SettingsIcons.icon("back"), "返回", Modifier.size(24.dp), colorFilter = ColorFilter.tint(colors.onSurface))
                            }
                        })
                    },
                    contentWindowInsets = WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                ) { padding ->
                    val modifier = Modifier.fillMaxSize().padding(padding)
                    if (root) {
                        tabStates.SaveableStateProvider(displayTab) { when (displayTab) {
                            0 -> Overview(app, controller, revisionSignal, modifier, ::open)
                            1 -> FeatureLibrary(activity, controller, revisionSignal, modifier, ::open)
                            else -> Management(app, controller, revisionSignal, glass, appearance, modifier,
                                onGlass = { enabled ->
                                    if (preferences.edit().putBoolean("liquid_navigation", enabled).commit()) glass = enabled
                                    else toast(activity, "外观设置未保存，请重试。")
                                },
                                onAppearance = {
                                    modal = SettingsModal.Choice("应用外观", listOf("跟随系统", "浅色", "深色"), appearance) { mode ->
                                        if (preferences.edit().putInt("appearance", mode).commit()) appearance = mode
                                    }
                                }, onModal = { modal = it }, onStatus = ::feedback)
                        } }
                    } else if (route == "sim_slots") {
                        LazyColumn(modifier.imePadding(), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 40.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
                            item("sim_slots") { SimSettingsContent(app) }
                        }
                    } else if (page != null) {
                        FeaturePage(activity, app, page, controller, revisionSignal, modifier, ::open, { modal = it }, ::feedback)
                    }
                }
                }
            }
            if (root && WindowInsets.ime.getBottom(LocalDensity.current) == 0) {
                GlassNavigation(tab, glass, backdrop, { tab = it },
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp))
            }
        }
        when (val current = modal) {
            null -> Unit
            is SettingsModal.Number -> NumberDialog(current, controller, { modal = null }, ::feedback)
            is SettingsModal.Bands -> WaveBandsDialog(current, controller, { modal = null }, ::feedback)
            is SettingsModal.Choice -> WindowDialog(show = true, title = current.title, onDismissRequest = { modal = null }) {
                Column(Modifier.heightIn(max = 440.dp).verticalScrollCompat()) {
                    current.labels.forEachIndexed { index, label ->
                        BasicComponent(title = label, endActions = {
                            if (index == current.selected) Text("✓", color = colors.primary, fontSize = 20.sp)
                        }, onClick = { current.choose(index); modal = null })
                    }
                    TextButton("取消", { modal = null }, Modifier.fillMaxWidth())
                }
            }
            is SettingsModal.Confirm -> WindowDialog(show = true, title = current.title, summary = current.message, onDismissRequest = { modal = null }) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton("取消", { modal = null }, Modifier.weight(1f))
                    TextButton("确定", { modal = null; current.confirm() }, Modifier.weight(1f))
                }
            }
            is SettingsModal.Order -> OrderDialog(activity, current, controller, { modal = null }, ::feedback)
            SettingsModal.Licenses -> WindowDialog(show = true, title = "开源许可", onDismissRequest = { modal = null }) {
                Column(Modifier.heightIn(max = 420.dp).verticalScrollCompat()) {
                    Text(remember { activity.assets.open("licenses/NOTICE.txt").bufferedReader().use { it.readText() } }, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(remember { activity.assets.open("licenses/project-mit.txt").bufferedReader().use { it.readText() } }, fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                    Spacer(Modifier.height(16.dp))
                    Text(remember { activity.assets.open("licenses/materialkolor-mit.txt").bufferedReader().use { it.readText() } }, fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                    Spacer(Modifier.height(16.dp))
                    Text(remember { activity.assets.open("licenses/ui-apache-2.0.txt").bufferedReader().use { it.readText() } }, fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                    TextButton("知道了", { modal = null }, Modifier.fillMaxWidth())
                }
            }
        }
        if (launchRelease != null) WindowDialog(show = true, title = "发现新版本 ${launchRelease.versionName()}",
            summary = "当前版本 ${BuildConfig.VERSION_NAME}", onDismissRequest = { app.launchUpdates().dismiss() }) {
            if (launchRelease.notes().isNotBlank()) Column(Modifier.heightIn(max = 320.dp).verticalScrollCompat()) {
                Text(launchRelease.notes(), Modifier.padding(top = 12.dp), fontSize = 14.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("稍后", { app.launchUpdates().dismiss() }, Modifier.weight(1f))
                TextButton("查看更新", {
                    app.launchUpdates().dismiss()
                    dev.howard.colorosenhance.updates.UpdateLinks.check(activity)
                }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Overview(app: EnhanceApplication, controller: SettingsUiController, update: Int, modifier: Modifier, open: (String) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val connected = app.frameworkService() != null
    val states = remember(update) { SettingsCatalog.GROUPS.map { controller.state(it.key()) } }
    val enabled = states.count { it.writable() && it.values()["enabled"] == true }
    val uncertain = states.count { it.revision() >= 0 && !it.writable() }
    LazyColumn(modifier, state = rememberLazyListState(), contentPadding = rootPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item("overview") {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(colors.primary.copy(alpha = .16f), colors.surfaceContainer))).padding(24.dp)) {
                Column {
                    Text(if (uncertain > 0) "已确认开启的增强" else "已开启的增强", fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(if (connected) "$enabled" else "—", fontSize = 52.sp, fontWeight = FontWeight.Medium)
                        Text(" / ${SettingsCatalog.GROUPS.size}", Modifier.padding(bottom = 10.dp), fontSize = 19.sp, color = colors.onSurfaceVariantSummary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(if (connected) Color(0xff2cac7d) else colors.onSurfaceVariantSummary))
                        Text(if (connected) "配置服务已连接" else "等待连接配置服务", fontSize = 13.sp)
                    }
                    if (uncertain > 0) Text("另有 $uncertain 组保存状态未确认", Modifier.padding(top = 6.dp), fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                }
                Image(SettingsIcons.icon("cloud"), null, Modifier.align(Alignment.TopEnd).size(78.dp), colorFilter = ColorFilter.tint(colors.primary.copy(alpha = .35f)))
            }
        }
        if (!connected) item("connection") {
            Card(Modifier.fillMaxWidth()) {
                BasicComponent(title = "连接 LSPosed 后配置功能", summary = "在 LSPosed 启用本模块并确认作用域，按提示重启。连接状态仅表示配置服务可用。")
            }
        }
        item("heading") { SectionHeading("常用入口") }
        items(SettingsLayout.HOME_GROUPS.chunked(2), key = { it.joinToString() }) { keys ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                keys.forEach { key ->
                    val group = SettingsCatalog.find(key)
                    Card(Modifier.weight(1f), insideMargin = PaddingValues(18.dp), onClick = { open(key) }) {
                        FeatureGlyph(key)
                        Text(group.title(), Modifier.padding(top = 16.dp), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Text(featureStatus(controller.state(key)), Modifier.padding(top = 5.dp), fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    }
                }
            }
        }
        item("tip") {
            Text("设置自动保存。更新模块或首次启用部分增强后，按页面说明重启对应作用域。", Modifier.padding(horizontal = 8.dp, vertical = 12.dp), fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun FeatureLibrary(context: Context, controller: SettingsUiController, update: Int, modifier: Modifier, open: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query) { SettingsSearch.search(context, query) }
    LazyColumn(modifier, contentPadding = rootPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("search") { TextField(value = query, onValueChange = { query = it }, label = "搜索功能", singleLine = true, modifier = Modifier.fillMaxWidth().testTag("feature_search")) }
        if (query.isNotBlank()) {
            item("results_title") { SectionHeading("搜索结果 · ${matches.size}") }
            items(matches, key = { "${it.route()}:${it.title()}:${it.summary()}" }) { result ->
                Card { NavigationRow(result.title(), result.summary(), { open(result.route()) }) }
            }
            if (matches.isEmpty()) item("empty") { Text("没有找到匹配的功能", Modifier.padding(18.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
        } else {
            listOf("界面与显示" to SettingsLayout.DISPLAY_GROUPS, "系统与安全" to SettingsLayout.SYSTEM_GROUPS).filter { it.second.isNotEmpty() }.forEach { (title, keys) ->
                item("heading:$title") { SectionHeading(title) }
                item("category:$title") {
                    Card(Modifier.fillMaxWidth()) {
                        keys.forEach { key ->
                            val group = SettingsCatalog.find(key)
                            NavigationRow(group.title(), group.summary(), { open(key) }, key,
                                featureStatus(controller.state(key)).takeIf { it != "未开启" })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Management(app: EnhanceApplication, controller: SettingsUiController, update: Int, glass: Boolean, appearance: Int,
                       modifier: Modifier, onGlass: (Boolean) -> Unit, onAppearance: () -> Unit,
                       onModal: (SettingsModal) -> Unit, onStatus: (String) -> Unit) {
    val activity = LocalActivity.current as? MainActivity ?: return
    val management = remember(update) { app.managementState() }
    LazyColumn(modifier, contentPadding = rootPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("appearance_title") { SectionHeading("应用外观") }
        item("appearance") {
            Card(Modifier.fillMaxWidth()) {
                NavigationRow("外观", listOf("跟随系统", "浅色", "深色")[appearance], onAppearance)
                SwitchPreference(glass, onGlass, "液态玻璃底栏", summary = "通透折射、流动选中高光与按压形变，支持横向拖动切换。关闭后使用实色背景。")
            }
        }
        item("entry_title") { SectionHeading("入口与显示") }
        item("entries") {
            Card(Modifier.fillMaxWidth()) {
                SwitchPreference(management.getBoolean("launcher_visible", true), { onStatus(if (app.setLauncherVisible(it)) "ok" else "failed") }, "显示桌面图标",
                    summary = "隐藏后仍可从 LSPosed 打开。", modifier = Modifier.testTag("launcher_visible"))
            }
        }
        item("apply_title") { SectionHeading("应用更改") }
        item("restart") {
            Card(Modifier.fillMaxWidth()) {
                BasicComponent(title = if (app.restarting) "正在重启…" else "重启系统界面", summary = "状态栏与锁屏将短暂重新加载。需要 root 授权。",
                    enabled = !app.anyRestarting(), onClick = {
                        onModal(SettingsModal.Confirm("重启系统界面", "状态栏与锁屏会短暂重新加载。首次使用需在 KernelSU 中允许本应用 root 权限。", app::restartSystemUi))
                    })
                val message = management.getString("restart_message", "").orEmpty()
                if (message.isNotBlank()) Text(message, Modifier.padding(16.dp), fontSize = 13.sp)
            }
        }
        item("restart_help") { Text("「应用转场时保持胶囊显示」首次使用需要整机重启，重启系统界面不能代替。",Modifier.padding(horizontal = 10.dp), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
        item("about_title") { SectionHeading("关于") }
        item("about") {
            Card(Modifier.fillMaxWidth()) {
                BasicComponent(title = "流体云增强", summary = "${BuildConfig.VERSION_NAME} · 基于 Howard 的 ColorOS 增强精简，仅保留流体云")
                NavigationRow("开源许可", "MIT · 第三方许可", { onModal(SettingsModal.Licenses) })
            }
        }
    }
}

@Composable
private fun FeaturePage(activity: MainActivity, app: EnhanceApplication, page: SettingsLayout.Page, controller: SettingsUiController,
                        update: Int, modifier: Modifier, open: (String) -> Unit, onModal: (SettingsModal) -> Unit, onStatus: (String) -> Unit) {
    val state = remember(update) { controller.state(page.group()) }
    val catalog = remember(page.route()) { SettingsCatalog.find(page.group(), activity) }
    val allItems = remember(page.route()) { SettingsLayout.items(activity, page.route()) }
    var filter by rememberSaveable { mutableStateOf("") }
    val visibleItems = allItems.filter { it.visible(state.values()) && (filter.isBlank() || (it.title() + it.description()).contains(filter, true)) }
    val sections = visibleItems.groupBy { it.section() }
    val children = SettingsLayout.children(page.route()).filter { it.route() != "sim_slots" && (it.route() != "cloud_landscape" || state.values()["landscape_follow_portrait"] != true) }
    val listState = rememberLazyListState()
    LazyColumn(modifier, state = listState, contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (page.demo().isNotBlank()) item("demo:${page.route()}") { DemoCard(page, state.values()) }
        if (page.route() == page.group()) item("master") {
            Card(Modifier.fillMaxWidth()) {
                SwitchPreference(state.values()["enabled"] == true, { onStatus(controller.toggle(page.group(), "enabled", it)) },
                    SettingsCatalog.masterTitle(catalog), summary = catalog.summary(), enabled = state.writable(), modifier = Modifier.testTag("enabled"))
            }
        } else item("feature_state") {
            Text("${catalog.title()} · ${featureStatus(state)}", Modifier.padding(horizontal = 10.dp),
                fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        if (!state.writable()) item("readonly") {
            Text(if (state.revision() < 0) "配置服务未连接，当前仅供查看。" else "保存状态未确认，已暂停修改。请重新连接配置服务后重试。",
                Modifier.padding(horizontal = 10.dp), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        if (children.isNotEmpty()) item("subpages") {
            Card(Modifier.fillMaxWidth()) {
                children.forEach { child -> NavigationRow(child.title(), child.summary(), { open(child.route()) }) }
            }
        }
        if (page.route() == "icons") item("icon_actions") {
            Card(Modifier.fillMaxWidth()) {
                val config = StatusIconConfig.from(state.values())
                NavigationRow("蓝牙显示规则", StatusIconConfig.BLUETOOTH_LABELS[config.bluetoothMode()], {
                    onModal(SettingsModal.Choice("蓝牙显示规则", StatusIconConfig.BLUETOOTH_LABELS.toList(), config.bluetoothMode()) { index ->
                        onStatus(controller.put("icons", "bluetooth_mode", index, state.revision()))
                    })
                }, enabled = state.writable() && config.enabled())
                NavigationRow("编辑图标顺序", "长按拖动，支持左右区域之间移动", {
                    onModal(SettingsModal.Order(state.revision(), config.order()))
                }, enabled = state.writable())
            }
        }
        if (page.route() == "icons_visibility") item("icon_filter") {
            TextField(value = filter, onValueChange = { filter = it }, label = "筛选图标", singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (page.route() == "installer") item("installer_target") {
            Card(Modifier.fillMaxWidth()) {
                val selected = state.values()["target_package"] as? String ?: ""
                NavigationRow("选择安装器", InstallerChoices.describe(activity, selected), {
                    try {
                        val choices = InstallerChoices.list(activity)
                        onModal(SettingsModal.Choice("选择安装器", listOf("跟随系统（不替换）") + choices.map { it.display() },
                            choices.indexOfFirst { it.packageName() == selected }.let { if (it < 0) 0 else it + 1 }) { index ->
                            onStatus(controller.put("installer", "target_package", if (index == 0) "" else choices[index - 1].packageName(), state.revision()))
                        })
                    } catch (_: RuntimeException) { toast(activity, "无法读取安装器列表，请重试。") }
                }, enabled = state.writable())
            }
        }
        sections.forEach { (section, items) ->
            item("heading:$section") { SectionHeading(section) }
            item("section:$section") {
                Card(Modifier.fillMaxWidth()) {
                    items.forEach { item -> key(item.key()) { SettingItem(page.group(), item, state, controller, onModal, onStatus) } }
                    if (page.route() == "cloud_media" && section == "媒体实时律动参数") {
                        val editable = state.writable() && SettingsCatalog.controlEnabled("cloud", MediaWaveBands.KEY, state.values())
                        BasicComponent(title = SettingsCatalog.WAVE_BANDS_TITLE,
                            summary = SettingsCatalog.waveBandsSummary(MediaWaveBands.from(state.values())),
                            enabled = editable, modifier = Modifier.testTag(MediaWaveBands.KEY), onClick = {
                                val latest = controller.state("cloud")
                                onModal(SettingsModal.Bands(latest.revision(), MediaWaveBands.from(latest.values())))
                            })
                        BasicComponent(title = SettingsCatalog.WAVE_RESET_TITLE, summary = SettingsCatalog.WAVE_RESET_HELP,
                            enabled = editable, modifier = Modifier.testTag("reset_wave"),
                            onClick = { onStatus(controller.resetWave(state.revision())) })
                    }
                }
            }
        }
        if (page.route() == "sim") item("sim_management") {
            Card(Modifier.fillMaxWidth()) { NavigationRow("卡槽与 PIN", "两张卡分别保存与启用", { open("sim_slots") }) }
            Text("只在手机上输入真实 PIN；每张卡每次开机最多自动尝试一次，失败时保留手动输入。", Modifier.padding(12.dp), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        if (page.route() == "installer") item("installer_help") { Text(SettingsCatalog.INSTALLER_HELP, Modifier.padding(10.dp), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
        if (page.route() == "cloud_scenes") item("scene_help") {
            Text("保持显示优先于动画补全；两项关闭时跟随系统。更改解绑总开关后，请按说明重启系统界面。锁屏、息屏、隐私状态与迷你胶囊保持系统处理。", Modifier.padding(10.dp), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        if (page.route() == page.group() && page.group() != "sim") item("reset") {
            Spacer(Modifier.height(10.dp))
            TextButton("恢复本组默认设置", {
                onModal(SettingsModal.Confirm("重置${catalog.title()}", "关闭本组增强并恢复参数默认值，保留其他分组。") {
                    onStatus(controller.reset(page.group(), state.revision()))
                })
            }, Modifier.fillMaxWidth(), enabled = state.writable())
        }
    }
}

@Composable
private fun SettingItem(group: String, item: SettingsCatalog.Item, state: SettingsCoordinator.State,
                        controller: SettingsUiController, onModal: (SettingsModal) -> Unit, onStatus: (String) -> Unit) {
    val enabled = state.writable() && SettingsCatalog.controlEnabled(group, item.key(), state.values())
    val bottom: (@Composable () -> Unit)? = if (item.adjustable()) {
        { NumericControl(group, item, state, controller, onModal, onStatus) }
    } else null
    if (item.valueOnly()) {
        BasicComponent(title = item.title(), summary = item.summary(state.values()), enabled = enabled, bottomAction = bottom,
            modifier = Modifier.testTag(item.key()))
    } else {
        SwitchPreference(state.values()[item.key()] as? Boolean ?: item.defaultFlag(),
            { onStatus(controller.toggle(group, item.key(), it)) }, item.title(), summary = item.summary(state.values()),
            enabled = enabled, bottomAction = bottom, modifier = Modifier.testTag(item.key()))
    }
}

@Composable
private fun NumericControl(group: String, item: SettingsCatalog.Item, state: SettingsCoordinator.State,
                           controller: SettingsUiController, onModal: (SettingsModal) -> Unit, onStatus: (String) -> Unit) {
    val confirmed = alignNumber(item, (state.values()[item.valueKey()] as? Number)?.toInt() ?: item.valueDefault())
    val editable = state.writable() && SettingsCatalog.controlEnabled(group, item.key(), state.values()) && item.valueEnabled(state.values())
    var dragging by remember(item.key()) { mutableStateOf(false) }
    var draft by remember(item.key()) { mutableIntStateOf(confirmed) }
    var revision by remember(item.key()) { mutableLongStateOf(-1L) }
    LaunchedEffect(editable, confirmed) { if (!editable) dragging = false; if (!dragging) draft = confirmed }
    val displayed = if (dragging) draft else confirmed
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    fun semanticCommit(value: Int): Boolean {
        if (!editable || dragging) return false
        val current = controller.state(group)
        val target = alignNumber(item, value)
        if (current.writable() && ((current.values()[item.valueKey()] as? Number)?.toInt() ?: item.valueDefault()) == target) return true
        val status = controller.number(group, item, target, current.revision())
        onStatus(status)
        return status == "ok"
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).testTag("value:${item.valueKey()}").semantics {
            contentDescription = item.title()
            progressBarRangeInfo = ProgressBarRangeInfo(displayed.toFloat(), item.min().toFloat()..item.max().toFloat(), ((item.max() - item.min()) / item.step() - 1).coerceAtLeast(0))
            if (!editable) disabled() else setProgress { semanticCommit(it.roundToInt()) }
        }.onPreviewKeyEvent { event ->
            val direction = when (event.key) { Key.DirectionRight -> if (rtl) -1 else 1; Key.DirectionLeft -> if (rtl) 1 else -1; else -> 0 }
            if (event.type != KeyEventType.KeyDown || direction == 0) false
            else {
                val current = controller.state(group)
                semanticCommit(((current.values()[item.valueKey()] as? Number)?.toInt() ?: confirmed) + direction * item.step())
            }
        }) {
        Slider(value = displayed.toFloat(), onValueChange = { value ->
            if (editable) {
                if (!dragging) { revision = controller.state(group).revision(); dragging = true }
                draft = alignNumber(item, value.roundToInt())
            }
        }, modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            enabled = editable, valueRange = item.min().toFloat()..item.max().toFloat(), steps = ((item.max() - item.min()) / item.step() - 1).coerceAtLeast(0),
            onValueChangeFinished = {
                if (dragging) { dragging = false; onStatus(controller.number(group, item, draft, revision)) }
            })
        }
        Text(formatNumber(item, displayed), Modifier.widthIn(min = 68.dp).clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.secondaryContainer).combinedClickable(
                enabled = editable,
                onClickLabel = "输入数值",
                onLongClickLabel = SettingsCatalog.RESET_NUMBER_LABEL,
                onLongClick = { semanticCommit(item.valueDefault()) },
                onClick = {
                    val latest = controller.state(group)
                    onModal(SettingsModal.Number(group, item, latest.revision(), (latest.values()[item.valueKey()] as? Number)?.toInt() ?: item.valueDefault()))
                }
            ).padding(horizontal = 10.dp, vertical = 13.dp), fontSize = 13.sp,
            color = if (editable) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun NumberDialog(dialog: SettingsModal.Number, controller: SettingsUiController, dismiss: () -> Unit, feedback: (String) -> Unit) {
    var input by remember { mutableStateOf(formatDecimal(dialog.value, dialog.item.divisor())) }
    var error by remember { mutableStateOf("") }
    WindowDialog(show = true, title = dialog.item.title(), summary = "${formatNumber(dialog.item, dialog.item.min())} – ${formatNumber(dialog.item, dialog.item.max())}，步长 ${formatDecimal(dialog.item.step(), dialog.item.divisor())}", onDismissRequest = dismiss) {
        Column(Modifier.imePadding()) {
            TextField(value = input, onValueChange = { input = it; error = "" }, label = "数值", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (dialog.item.min() < 0) KeyboardType.Ascii else KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, Modifier.padding(top = 8.dp), fontSize = 12.sp, color = Color(0xffc24747))
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("取消", dismiss, Modifier.weight(1f))
                TextButton("保存", {
                    val item = dialog.item
                    val value = NativeNumberInput.parse(input, item.min(), item.max(), item.step(), item.divisor())
                    if (value == null) error = "请输入范围内且符合步长的数值"
                    else { feedback(controller.number(dialog.group, item, value, dialog.revision)); dismiss() }
                }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WaveBandsDialog(dialog: SettingsModal.Bands, controller: SettingsUiController,
                            dismiss: () -> Unit, feedback: (String) -> Unit) {
    val inputs = remember(dialog) {
        List(MediaWaveBands.EDGE_COUNT) { index -> dialog.bands.edgeHz(index).toString() }.toMutableStateList()
    }
    var error by remember(dialog) { mutableStateOf(false) }
    val maxDigits = MediaWaveBands.MAX_HZ.toString().length
    val parsed = MediaWaveBands.parse(inputs.joinToString(","))
    val colors = MiuixTheme.colorScheme
    fun save(bands: MediaWaveBands) {
        feedback(controller.put("cloud", MediaWaveBands.KEY, bands.encode(), dialog.revision))
        dismiss()
    }
    WindowDialog(show = true, title = SettingsCatalog.WAVE_BANDS_TITLE, summary = SettingsCatalog.WAVE_BANDS_HELP,
        onDismissRequest = dismiss) {
        Column(Modifier.imePadding()) {
            Column(Modifier.heightIn(max = 320.dp).verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AndroidView(factory = { context ->
                    WaveBandAxisView(context).apply { setBands(dialog.bands) }
                }, modifier = Modifier.fillMaxWidth().testTag("wave_band_axis"), update = { axis ->
                    axis.setPalette(colors.onSurface.toArgb(), colors.onSurfaceVariantSummary.toArgb(), colors.primary.toArgb())
                    axis.setOnBandsChanged { bands ->
                        for (index in inputs.indices) inputs[index] = bands.edgeHz(index).toString()
                        error = false
                    }
                    if (parsed != null) axis.setBands(parsed)
                    axis.isEnabled = parsed != null
                })
                inputs.forEachIndexed { index, input ->
                    TextField(value = input, onValueChange = { value ->
                        if (value.length <= maxDigits && value.all { it in '0'..'9' }) {
                            inputs[index] = value
                            error = false
                        }
                    }, label = SettingsCatalog.waveBandEdgeTitle(index), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("wave_edge_$index"))
                }
            }
            if (error || parsed == null) Text(SettingsCatalog.WAVE_BANDS_ERROR, Modifier.padding(top = 8.dp), fontSize = 12.sp, color = Color(0xffc24747))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton("恢复默认", { save(MediaWaveBands.DEFAULTS) }, Modifier.weight(1.6f))
                TextButton("取消", dismiss, Modifier.weight(1f))
                TextButton("保存", {
                    val bands = MediaWaveBands.parse(inputs.joinToString(","))
                    if (bands == null) error = true else save(bands)
                }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OrderDialog(activity: MainActivity, dialog: SettingsModal.Order, controller: SettingsUiController, dismiss: () -> Unit, feedback: (String) -> Unit) {
    val editor = remember { IconOrderEditor(activity, dialog.order, StatusIconCatalog.slots(activity)) }
    WindowDialog(show = true, title = "状态栏图标排序", summary = "长按拖动图标，可跨左右区域移动。确认后保存。", onDismissRequest = dismiss) {
        AndroidView(factory = { editor }, modifier = Modifier.fillMaxWidth().heightIn(max = 410.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("取消", dismiss, Modifier.weight(1f))
            TextButton("保存", { feedback(controller.put("icons", IconOrder.KEY, editor.encodedValue(), dialog.revision)); dismiss() }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavigationRow(title: String, summary: String, action: () -> Unit, icon: String? = null, badge: String? = null, enabled: Boolean = true) {
    BasicComponent(title = title, summary = summary, onClick = action, enabled = enabled,
        startAction = if (icon == null) null else { { FeatureGlyph(icon) } },
        endActions = {
            if (badge != null) Text(badge, fontSize = 11.sp, color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
            Text("›", fontSize = 23.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        })
}

@Composable private fun rootPadding() = PaddingValues(20.dp, 8.dp, 20.dp, 112.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
@Composable private fun Modifier.verticalScrollCompat() = this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))
private fun alignNumber(item: SettingsCatalog.Item, value: Int) = (item.min() + ((value - item.min()) / item.step().toFloat()).roundToInt() * item.step()).coerceIn(item.min(), item.max())
private fun formatDecimal(value: Int, divisor: Int) = BigDecimal.valueOf(value.toLong()).divide(BigDecimal.valueOf(divisor.toLong())).stripTrailingZeros().toPlainString()
private fun formatNumber(item: SettingsCatalog.Item, value: Int) = formatDecimal(value, item.divisor()) + if (item.unit().isBlank()) "" else " ${item.unit()}"
private fun toast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
private fun resultMessage(status: String) = when (status) {
    "conflict" -> "配置已在另一页面更改，请重新操作。"
    "uncertain" -> "保存状态未确认，请重新连接配置服务后重试。"
    "unavailable" -> "配置服务未连接，设置未保存。"
    "invalid" -> "配置已更改或数值无效，请重新操作。"
    else -> "设置未保存，请重试。"
}
private fun featureStatus(state: SettingsCoordinator.State) = when {
    state.revision() < 0 -> "未连接"
    !state.writable() -> "状态未确认"
    state.values()["enabled"] == true -> "已开启"
    else -> "未开启"
}
