package dev.howard.colorosenhance

import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.howard.simpinautounlock.management.SimManagementController
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** Only credential-free controller state is observed; PIN input is transient and never saveable. */
@Composable
fun SimSettingsContent(app: EnhanceApplication) {
    val controller = remember(app) { app.simManagement() }
    var snapshot by remember(controller) { mutableStateOf(controller.snapshot()) }
    var editingSlot by remember { mutableStateOf<Int?>(null) }
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    // -1 represents both slots; this state contains no credentials.
    var clearTarget by remember { mutableStateOf<Int?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    fun clearDraft() {
        pin = ""
        confirmation = ""
        inputError = null
        editingSlot = null
        keyboard?.hide()
        focus.clearFocus()
    }

    fun saveDraft() {
        val slotId = editingSlot ?: return
        val current = controller.snapshot().slots().firstOrNull { it.slotId() == slotId }
        if (current == null || current.busy()) return
        if (!pin.matches(Regex("[0-9]{4,8}"))) {
            inputError = "请输入 4–8 位数字 PIN。"
            return
        }
        if (confirmation != pin) {
            inputError = "两次输入不一致，请重新确认。"
            return
        }
        val first = pin
        val second = confirmation
        clearDraft()
        // Keep validation, staging, authentication and the single-attempt policy in the controller.
        controller.save(slotId, first, second)
    }

    DisposableEffect(controller, lifecycleOwner) {
        val observer = SimManagementController.Observer { snapshot = it }
        var observing = false
        fun start() {
            if (observing) return
            observing = true
            controller.addObserver(observer)
            controller.refresh()
        }
        fun stop() {
            if (observing) {
                observing = false
                controller.removeObserver(observer)
            }
            clearDraft()
            clearTarget = null
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            stop()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (snapshot.message().isNotBlank()) {
            Text(
                text = snapshot.message(),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
            )
        }
        snapshot.slots().forEach { slot ->
            val presentation = slot.presentation()
            Card(modifier = Modifier.fillMaxWidth().testTag("sim_slot_${slot.slotId()}")) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "卡槽 ${slot.slotId() + 1}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = presentation.displayText(),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    if (presentation.pendingWarningVisible()) {
                        Text(
                            text = "配置已暂存，完成首次导入前请勿换卡或交换卡槽。",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                    if (slot.message().isNotBlank()) {
                        Text(
                            text = if (slot.error()) "未完成：${slot.message()}" else slot.message(),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                SwitchPreference(
                    title = "启用此卡槽",
                    summary = if (slot.busy()) "正在处理…" else "开机时自动验证此 SIM 的 PIN",
                    checked = presentation.toggleState(),
                    onCheckedChange = { controller.setEnabled(slot.slotId(), it) },
                    enabled = presentation.toggleEnabled() && !slot.busy(),
                    modifier = Modifier.testTag("sim_slot_${slot.slotId()}_enabled"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(
                        text = "保存或更新 PIN",
                        onClick = {
                            clearDraft()
                            editingSlot = slot.slotId()
                        },
                        enabled = !slot.busy(),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("sim_slot_${slot.slotId()}_save"),
                    )
                    TextButton(
                        text = "清除此卡",
                        onClick = { clearTarget = slot.slotId() },
                        enabled = presentation.clearEnabled() && !slot.busy(),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("sim_slot_${slot.slotId()}_clear"),
                    )
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "关闭开关不会重新锁定已解锁的 SIM。每张卡每次开机最多自动验证一次，失败或超时后保留手动输入。",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                TextButton(
                    text = "清除所有卡槽配置",
                    onClick = { clearTarget = -1 },
                    enabled = snapshot.slots().none { it.busy() } && snapshot.slots().any { it.presentation().clearEnabled() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sim_clear_all"),
                )
            }
        }
    }

    val editingAvailable = editingSlot != null && snapshot.slots()
        .firstOrNull { it.slotId() == editingSlot }?.busy() == false
    if (editingSlot != null) {
        // Set protection while Compose creates the Window, before any PIN content is shown.
        // MIUIX WindowDialog does not expose a securePolicy, so this sensitive dialog owns its host.
        Dialog(
            onDismissRequest = { clearDraft() },
            properties = DialogProperties(
                securePolicy = SecureFlagPolicy.SecureOn,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            ProtectSimPinContent()
            val dialogFocus = LocalFocusManager.current
            Box(
                modifier = Modifier.fillMaxWidth().safeDrawingPadding().imePadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Card(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "卡槽 ${(editingSlot ?: 0) + 1} · 保存 PIN",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "输入此 SIM 卡当前使用的 PIN，保存后启用。",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        TextField(
                            value = pin,
                            onValueChange = {
                                if (it.length <= 8 && it.all { digit -> digit in '0'..'9' }) {
                                    pin = it
                                    inputError = null
                                } else inputError = "PIN 仅支持 4–8 位数字。"
                            },
                            label = "SIM PIN（4–8 位数字）",
                            enabled = editingAvailable,
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { dialogFocus.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth().testTag("sim_pin"),
                        )
                        TextField(
                            value = confirmation,
                            onValueChange = {
                                if (it.length <= 8 && it.all { digit -> digit in '0'..'9' }) {
                                    confirmation = it
                                    inputError = null
                                } else inputError = "PIN 仅支持 4–8 位数字。"
                            },
                            label = "再次输入 PIN",
                            enabled = editingAvailable,
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { saveDraft() }),
                            modifier = Modifier.fillMaxWidth().testTag("sim_pin_confirmation"),
                        )
                        inputError?.let { Text(text = it, fontSize = 13.sp, color = MiuixTheme.colorScheme.primary) }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(text = "取消", onClick = { clearDraft() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                            TextButton(text = "保存并启用", onClick = { saveDraft() }, enabled = editingAvailable, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("sim_pin_commit"))
                        }
                    }
                }
            }
        }
    }

    WindowDialog(
        show = clearTarget != null,
        title = if (clearTarget == -1) "清除所有卡槽配置" else "清除卡槽 ${(clearTarget ?: 0) + 1} 配置",
        summary = if (clearTarget == -1) "将删除两个卡槽已保存的 PIN 和暂存配置。已解锁的 SIM 不会被重新锁定。"
        else "将删除此卡槽已保存的 PIN 和暂存配置。已解锁的 SIM 不会被重新锁定。",
        onDismissRequest = { clearTarget = null },
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(text = "取消", onClick = { clearTarget = null }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
            TextButton(
                text = "清除",
                onClick = {
                    val target = clearTarget
                    clearTarget = null
                    if (target == -1) {
                        if (controller.snapshot().slots().none { it.busy() }) controller.clearAll()
                    } else if (target != null) {
                        val current = controller.snapshot().slots().firstOrNull { it.slotId() == target }
                        if (current != null && !current.busy() && current.presentation().clearEnabled()) controller.clear(target)
                    }
                },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            )
        }
    }
}

/** Keep the secure dialog's transient input out of autofill and content-capture services. */
@Composable
private fun ProtectSimPinContent() {
    val view = LocalView.current
    DisposableEffect(view) {
        val previousAutofill = view.importantForAutofill
        val previousCapture = view.importantForContentCapture
        view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        view.importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
        onDispose {
            view.importantForAutofill = previousAutofill
            view.importantForContentCapture = previousCapture
        }
    }
}
