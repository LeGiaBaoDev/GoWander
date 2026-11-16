package com.gowander.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gowander.ui.Button
import com.gowander.ui.ButtonKind
import com.gowander.ui.ExtraIcons
import com.gowander.ui.Header
import com.gowander.ui.Icon
import com.gowander.ui.KitIcons
import com.gowander.ui.Palette
import com.gowander.ui.Text
import kotlinx.coroutines.launch

/** What an A8 row is editing; null closes the dialog. */
private enum class Edit { Name, Username, Phone, Password, LogoutAll }

/**
 * A8 · Cài đặt tài khoản: the details behind the account row of Hồ sơ. Rows show the value and open a small dialog;
 * every change goes through [AccountStore], so the state on every screen follows.
 */
@Composable
fun AccountSettingsScreen(
    account: AccountStore,
    googleEnabled: Boolean,
    onBack: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val me = account.user ?: return
    var editing by remember { mutableStateOf<Edit?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun close() { editing = null; error = null; account.clearError() }

    Column(
        Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header("Cài đặt tài khoản", onBack = onBack)
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialAvatar(me.name, 56)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(me.name, size = 18.sp, weight = FontWeight.Bold, maxLines = 1)
                Text("@" + (me.username ?: ""), size = 13.sp, color = Palette.Muted, maxLines = 1)
            }
        }

        Text("Thông tin", Modifier.semantics { heading() }, size = 13.sp, weight = FontWeight.Bold, color = Palette.Muted)
        Column(Modifier.clip(RoundedCornerShape(18.dp)).border(1.dp, Palette.Line, RoundedCornerShape(18.dp))) {
            SettingsRow("Tên hiển thị", me.name, KitIcons.user) { editing = Edit.Name }
            SettingsRow("Tên đăng nhập", "@" + (me.username ?: ""), ExtraIcons.at) { editing = Edit.Username }
            SettingsRow(
                "Email",
                me.email ?: "",
                ExtraIcons.mail,
                badge = if (me.emailVerified) "Đã xác minh" else "Chưa xác minh",
                badgeOk = me.emailVerified,
            )
            SettingsRow(
                "Số điện thoại",
                me.phone ?: "Chưa có",
                ExtraIcons.phone,
                badge = if (me.phone.isNullOrBlank()) "" else if (me.phoneVerified) "Đã xác minh" else "Chưa xác minh",
                badgeOk = me.phoneVerified,
            ) { editing = Edit.Phone }
            SettingsRow("Mật khẩu", if (me.hasPassword) "Đã đặt" else "Chưa đặt", KitIcons.lock) { editing = Edit.Password }
        }
        if (!me.emailVerified) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Warn).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(KitIcons.bell, 18.dp, Palette.Ink)
                Text("Email chưa xác minh", Modifier.weight(1f).padding(start = 10.dp), size = 13.sp, weight = FontWeight.SemiBold)
            }
        }

        Text("Đăng nhập", Modifier.semantics { heading() }, size = 13.sp, weight = FontWeight.Bold, color = Palette.Muted)
        Column(Modifier.clip(RoundedCornerShape(18.dp)).border(1.dp, Palette.Line, RoundedCornerShape(18.dp))) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(KitIcons.globe, 18.dp, Palette.Primary)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Google", size = 15.sp, weight = FontWeight.SemiBold)
                    Text(
                        when {
                            me.google -> "Đã liên kết"
                            googleEnabled -> "Chưa liên kết — bấm để liên kết"
                            else -> "Sẽ có ở bước đăng nhập Google"
                        },
                        size = 12.5.sp,
                        color = Palette.Muted,
                    )
                }
                if (googleEnabled) {
                    Text(if (me.google) "Huỷ liên kết" else "Liên kết", size = 13.sp, color = Palette.Primary, weight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(ExtraIcons.device, 18.dp, Palette.Primary)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Thiết bị hiện tại", size = 15.sp, weight = FontWeight.SemiBold)
                    Text(deviceName, size = 12.5.sp, color = Palette.Muted)
                }
            }
            ActionRow("Đăng xuất mọi thiết bị", "Thu hồi mọi phiên, kể cả máy này", ExtraIcons.logout) { editing = Edit.LogoutAll }
            ActionRow("Đăng xuất", "Máy này thôi", ExtraIcons.logout) {
                scope.launch {
                    account.signOut()
                    onSignedOut()
                }
            }
        }

        Box(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp))
                .border(1.dp, Palette.AccentSoft, RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onDeleteAccount)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ExtraIcons.close, 18.dp, Palette.Accent)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Xoá tài khoản", size = 15.sp, weight = FontWeight.SemiBold, color = Palette.Accent)
                    Text("Xoá vĩnh viễn, không khôi phục", size = 12.5.sp, color = Palette.Muted)
                }
            }
        }
        Text("Phiên đăng nhập 60 ngày, tự gia hạn khi dùng. Đổi mật khẩu sẽ đăng xuất các máy khác.", size = 12.5.sp, color = Palette.Muted)
        Spacer(Modifier.height(16.dp))
    }

    when (editing) {
        Edit.Name -> EditDialog(
            "Tên hiển thị", me.name, error, ::close,
            onSave = { value ->
                scope.launch {
                    error = account.updateProfile(value, null, null)
                    if (error == null) close()
                }
            },
        ) { value, change -> FieldInDialog(value, change, "Ví dụ: Lan Anh") }
        Edit.Username -> EditDialog(
            "Tên đăng nhập", me.username ?: "", error, ::close,
            onSave = { value ->
                scope.launch {
                    error = account.updateProfile(null, value, null)
                    if (error == null) close()
                }
            },
        ) { value, change -> FieldInDialog(value, change, "lananh", ExtraIcons.at) }
        Edit.Phone -> EditDialog(
            "Số điện thoại", me.phone ?: "", error, ::close,
            onSave = { value ->
                scope.launch {
                    error = account.updateProfile(null, null, value)
                    if (error == null) close()
                }
            },
        ) { value, change -> FieldInDialog(value, change, "0912 345 678", ExtraIcons.phone, KeyboardType.Phone) }
        Edit.Password -> PasswordDialog(account, error, onError = { error = it }, onDone = ::close)
        Edit.LogoutAll -> ConfirmDialog(
            "Đăng xuất mọi thiết bị",
            "Mọi máy, kể cả máy này, sẽ phải đăng nhập lại. Tiếp tục?",
            error, onDismiss = ::close,
        ) {
            scope.launch {
                account.signOut(everywhere = true)
                close()
                onSignedOut()
            }
        }
        null -> Unit
    }
}

/** A row that does something rather than shows a value: the title is the big line, the sentence under it. */
@Composable
private fun ActionRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, 18.dp, Palette.Muted)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, size = 15.sp, weight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) Text(subtitle, size = 12.5.sp, color = Palette.Muted)
        }
        Icon(KitIcons.chevron, 16.dp, Palette.Muted)
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String? = null,
    badgeOk: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, 18.dp, Palette.Muted)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(label, size = 12.5.sp, color = Palette.Muted)
            Text(value, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 1)
        }
        if (!badge.isNullOrBlank()) {
            Text(
                badge,
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(if (badgeOk) Palette.Soft else Palette.Warn).padding(horizontal = 8.dp, vertical = 3.dp),
                size = 11.5.sp,
                weight = FontWeight.SemiBold,
                color = if (badgeOk) Palette.Primary else Palette.Ink,
            )
        } else if (onClick != null) {
            Icon(KitIcons.chevron, 16.dp, Palette.Muted)
        }
    }
}

@Composable
private fun EditDialog(
    title: String,
    initial: String,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    field: @Composable (String, (String) -> Unit) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Bg).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, size = 17.sp, weight = FontWeight.Bold)
            field(value) { value = it }
            error?.let { AccountError(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button("Huỷ", Modifier.weight(1f), kind = ButtonKind.Secondary) { onDismiss() }
                Button("Lưu", Modifier.weight(1f)) { onSave(value.trim()) }
            }
        }
    }
}

@Composable
private fun FieldInDialog(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    AccountField("", value, hint, icon, keyboard) { onChange(it) }
}

@Composable
private fun PasswordDialog(account: AccountStore, error: String?, onError: (String?) -> Unit, onDone: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDone) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Bg).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (account.user?.hasPassword == true) "Đổi mật khẩu" else "Đặt mật khẩu", size = 17.sp, weight = FontWeight.Bold)
            if (account.user?.hasPassword == true) PasswordField("Mật khẩu hiện tại", current) { current = it }
            PasswordField("Mật khẩu mới", next) { next = it }
            StrengthBars(passwordStrength(next))
            PasswordField("Nhập lại mật khẩu mới", confirm) { confirm = it }
            when {
                confirm.isNotEmpty() && confirm != next -> AccountError("Hai lần nhập chưa giống nhau.")
                error != null -> AccountError(error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button("Huỷ", Modifier.weight(1f), kind = ButtonKind.Secondary) { onDone() }
                Button("Lưu", Modifier.weight(1f)) {
                    scope.launch {
                        val message = account.changePassword(current, next)
                        onError(message)
                        if (message == null) onDone()
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, error: String?, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Bg).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, size = 17.sp, weight = FontWeight.Bold)
            Text(body, size = 13.5.sp, color = Palette.Muted)
            error?.let { AccountError(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button("Huỷ", Modifier.weight(1f), kind = ButtonKind.Secondary) { onDismiss() }
                Button("Đồng ý", Modifier.weight(1f), kind = ButtonKind.Accent) { onConfirm() }
            }
        }
    }
}
