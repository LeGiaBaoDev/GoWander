package com.gowander.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.gowander.ui.ExtraIcons
import com.gowander.ui.Header
import com.gowander.ui.Icon
import com.gowander.ui.KitIcons
import com.gowander.ui.Palette
import com.gowander.ui.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A13 · Hoàn tất hồ sơ — the step after a first Google sign-in (ADR-040): Google already gave us a verified email and a
 * name; here the person chooses the sign-in name they like and, if they want, a phone number and a password, so from
 * now on they can enter with email / phone / username + password as well as with Google.
 *
 * Everything here is optional except the sign-in name: "Để sau" leaves a working Google account.
 */
@Composable
fun CompleteProfileScreen(
    account: AccountStore,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val me = account.user
    var name by rememberSaveable { mutableStateOf(me?.name.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(me?.username.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(me?.phone.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var freeName by remember { mutableStateOf<Boolean?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(username) {
        freeName = null
        if (usernameIssue(username) == null && username != me?.username) {
            delay(450)
            freeName = account.usernameAvailable(username)
        }
    }

    val nameIssue = usernameIssue(username)
    val phoneProblem = if (phone.isNotBlank() && normalizePhone(phone) == null) "Số điện thoại chưa đúng, bạn kiểm tra lại nhé." else null
    val passwordProblem = if (password.isNotBlank()) passwordIssue(password, username, me?.email.orEmpty()) else null

    fun save(then: () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            error = account.updateProfile(name.trim(), username.trim(), phone.trim())
            if (error == null && password.isNotBlank()) {
                // A Google-only account has no current password: the server accepts an empty one here.
                error = account.changePassword("", password)
            }
            busy = false
            if (error == null) then()
        }
    }

    Column(
        Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header("Hoàn tất hồ sơ", onBack = onBack)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Soft).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(KitIcons.check, 18.dp, Palette.Primary)
            Text("Đã kết nối Google bằng ${me?.email.orEmpty()}.", Modifier.padding(start = 8.dp), size = 13.sp, weight = FontWeight.SemiBold)
        }
        Text(
            "Thêm vài thông tin để lần sau bạn đăng nhập nhanh hơn — bằng email, số điện thoại hoặc tên đăng nhập, không cần mở Google.",
            size = 13.5.sp,
            color = Palette.Muted,
        )

        AccountField("Tên hiển thị", name, "Ví dụ: Lan Anh", KitIcons.user) { name = it }
        AccountField("Tên đăng nhập", username, "lananh", ExtraIcons.at) { username = it }
        when {
            nameIssue != null && username.isNotBlank() -> AccountHint(nameIssue, ok = false)
            freeName == true -> AccountHint("Tên “$username” còn trống — bạn dùng được nhé")
            freeName == false -> AccountHint("Tên này có người dùng rồi, thử tên khác nhé", ok = false)
        }
        AccountField("Số điện thoại (không bắt buộc)", phone, "0912 345 678", ExtraIcons.phone, KeyboardType.Phone) { phone = it }
        PasswordField("Mật khẩu (không bắt buộc)", password) { password = it }
        if (password.isNotBlank()) StrengthBars(passwordStrength(password))
        Text(
            "Để trống mật khẩu nếu bạn chỉ muốn đăng nhập bằng Google. Đặt mật khẩu thì đăng nhập được cả hai cách.",
            size = 12.5.sp,
            color = Palette.Muted,
        )
        phoneProblem?.let { AccountError(it) }
        passwordProblem?.let { AccountError(it) }
        error?.let { AccountError(it) }

        BusyButton("Lưu và tiếp tục", busy, name.isNotBlank() && username.isNotBlank() && phoneProblem == null && passwordProblem == null) {
            save(onDone)
        }
        // Leaving it for later keeps a working account: Google already vouches for the email.
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onDone), contentAlignment = Alignment.Center) {
            Text("Để sau", size = 14.sp, color = Palette.Muted, weight = FontWeight.SemiBold)
        }
    }
}
