package com.gowander.account

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowander.resources.Res
import com.gowander.resources.logo_full
import com.gowander.ui.Button
import com.gowander.ui.ButtonKind
import com.gowander.ui.ExtraIcons
import com.gowander.ui.Header
import com.gowander.ui.Icon
import com.gowander.ui.KitIcons
import com.gowander.ui.Palette
import com.gowander.ui.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.imageResource

/**
 * The account screens of AUTH-0 (A2–A6, A9). They only collect input: the rules live in [AccountStore], which answers
 * with null or a Vietnamese sentence, so every screen shows errors the same way.
 */

@Composable
private fun AccountPage(title: String? = null, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onBack != null) Header(title.orEmpty(), onBack = onBack) else if (title != null) Text(title, size = 28.sp, weight = FontWeight.Bold)
        content()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun BigTitle(text: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, Modifier.semantics { heading() }, size = 27.sp, weight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, size = 14.sp, color = Palette.Muted)
    }
}

// ---------------------------------------------------------------- A2

/** A2 · Chào mừng: logo, one sentence, Google, sign-in by email, create an account, "Để sau". */
@Composable
fun AccountWelcomeScreen(
    googleEnabled: Boolean,
    onBack: () -> Unit,
    onGoogle: () -> Unit,
    onSignIn: () -> Unit,
    onRegister: () -> Unit,
    onSkip: () -> Unit,
) {
    AccountPage(onBack = onBack) {
        Spacer(Modifier.height(6.dp))
        Image(
            imageResource(Res.drawable.logo_full),
            contentDescription = "GoWander",
            Modifier.height(112.dp).align(Alignment.CenterHorizontally),
        )
        Text(
            "Đi trong thành phố,\nhiểu từng con đường.",
            Modifier.fillMaxWidth().padding(top = 10.dp),
            size = 25.sp,
            weight = FontWeight.Bold,
            maxLines = 2,
        )
        Spacer(Modifier.height(6.dp))
        if (googleEnabled) {
            GoogleSignInButton(onGoogle)
            OrDivider()
        }
        Button("Đăng nhập bằng email", Modifier.fillMaxWidth()) { onSignIn() }
        Button("Tạo tài khoản", Modifier.fillMaxWidth(), kind = ButtonKind.Outline) { onRegister() }
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onSkip), contentAlignment = Alignment.Center) {
            Text("Để sau", size = 14.5.sp, color = Palette.Muted, weight = FontWeight.SemiBold)
        }
        LegalLine()
    }
}

// ---------------------------------------------------------------- A3

/** A3 · Đăng nhập: one field for email / phone / username, password, forgot, Google. */
@Composable
fun SignInScreen(
    account: AccountStore,
    googleEnabled: Boolean,
    onBack: () -> Unit,
    onGoogle: () -> Unit,
    onForgot: () -> Unit,
    onRegister: () -> Unit,
    onSignedIn: () -> Unit,
) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (account.busy) return
        scope.launch {
            error = account.signIn(identifier, password)
            if (error == null) onSignedIn()
        }
    }

    AccountPage(onBack = onBack) {
        BigTitle("Đăng nhập", "Chào mừng bạn quay lại! Đăng nhập bằng email, số điện thoại hoặc tên đăng nhập.")
        AccountField("Email, số điện thoại hoặc tên đăng nhập", identifier, "lananh@example.com", ExtraIcons.at) { identifier = it }
        PasswordField("Mật khẩu", password) { password = it }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(Modifier.heightIn(min = 40.dp).clickable(role = Role.Button, onClick = onForgot), contentAlignment = Alignment.Center) {
                Text("Quên mật khẩu?", size = 13.sp, color = Palette.Primary, weight = FontWeight.SemiBold)
            }
        }
        error?.let { AccountError(it) }
        BusyButton("Đăng nhập", account.busy, identifier.isNotBlank() && password.isNotBlank()) { submit() }
        if (googleEnabled) {
            OrDivider()
            GoogleSignInButton(onGoogle)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
            Text("Chưa có tài khoản? ", size = 13.5.sp, color = Palette.Muted)
            Box(Modifier.clickable(role = Role.Button, onClick = onRegister)) {
                Text("Tạo tài khoản", size = 13.5.sp, color = Palette.Primary, weight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------- A4

/** A4 · Tạo tài khoản: name, username (checked as you type), email, optional phone, password, terms. */
@Composable
fun RegisterScreen(
    account: AccountStore,
    googleEnabled: Boolean,
    onBack: () -> Unit,
    onGoogle: () -> Unit,
    onSignedIn: () -> Unit,
    onSignIn: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var agreed by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var freeName by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(username) {
        freeName = null
        if (usernameIssue(username) == null) {
            delay(450) // typing a name should not send a request per letter
            freeName = account.usernameAvailable(username)
        }
    }

    val nameIssue = usernameIssue(username)
    val issue = nameIssue ?: passwordIssue(password, username, email)
        ?: if (email.isNotBlank() && normalizeEmail(email) == null) "Email chưa đúng, bạn kiểm tra lại nhé." else null
        ?: if (phone.isNotBlank() && normalizePhone(phone) == null) "Số điện thoại chưa đúng, bạn kiểm tra lại nhé." else null
    val ready = name.isNotBlank() && username.isNotBlank() && email.isNotBlank() && password.isNotBlank() && agreed

    fun submit() {
        if (account.busy || !ready) return
        scope.launch {
            error = account.register(name, username, email, phone, password)
            if (error == null) onSignedIn()
        }
    }

    AccountPage(onBack = onBack) {
        BigTitle("Tạo tài khoản", "Chỉ mất một phút. Thiếu gì bạn bổ sung sau cũng được.")
        AccountField("Tên hiển thị", name, "Ví dụ: Lan Anh", KitIcons.user) { name = it }
        AccountField("Tên đăng nhập", username, "lananh", ExtraIcons.at) { username = it }
        when {
            nameIssue != null && username.isNotBlank() -> AccountHint(nameIssue, ok = false)
            freeName == true -> AccountHint("Tên “$username” còn trống — bạn dùng được nhé")
            freeName == false -> AccountHint("Tên này có người dùng rồi, thử tên khác nhé", ok = false)
        }
        AccountField("Email", email, "ban@example.com", ExtraIcons.mail, KeyboardType.Email) { email = it }
        AccountField("Số điện thoại (không bắt buộc)", phone, "0912 345 678", ExtraIcons.phone, KeyboardType.Phone) { phone = it }
        PasswordField("Mật khẩu", password) { password = it }
        StrengthBars(passwordStrength(password))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Checkbox) { agreed = !agreed },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (agreed) Palette.Primary else Palette.Bg)
                    .then(if (agreed) Modifier else Modifier.border(1.5.dp, Palette.Line, RoundedCornerShape(6.dp))),
                contentAlignment = Alignment.Center,
            ) { if (agreed) Icon(KitIcons.check, 14.dp, Palette.Bg) }
            Spacer(Modifier.width(10.dp))
            Text("Đồng ý với Điều khoản và Chính sách quyền riêng tư.", size = 13.sp, color = Palette.Muted, maxLines = 2)
        }
        error?.let { AccountError(it) }
        BusyButton("Tạo tài khoản", account.busy, ready) { submit() }
        if (googleEnabled) {
            OrDivider()
            GoogleSignInButton(onGoogle)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
            Text("Đã có tài khoản? ", size = 13.5.sp, color = Palette.Muted)
            Box(Modifier.clickable(role = Role.Button, onClick = onSignIn)) {
                Text("Đăng nhập", size = 13.5.sp, color = Palette.Primary, weight = FontWeight.Bold)
            }
        }
        LegalLine()
    }
}

// ---------------------------------------------------------------- A5

/** A5 · Nhập mã: six boxes, a countdown before another code, "Đổi email". */
@Composable
fun VerifyCodeScreen(
    account: AccountStore,
    email: String?,
    onBack: () -> Unit,
    onVerified: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var seconds by remember { mutableStateOf(60) }
    var resending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(1000)
            seconds--
        }
    }

    fun submit() {
        if (account.busy || code.length != 6) return
        scope.launch {
            error = account.verifyEmail(code)
            if (error == null) onVerified()
        }
    }

    AccountPage(onBack = onBack) {
        BigTitle("Nhập mã xác minh", "GoWander vừa gửi mã 6 số tới ${maskEmail(email)}. Mã dùng được trong 10 phút nhé.")
        CodeField(code) { code = it; error = null }
        error?.let { AccountError(it) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (seconds > 0) {
                Text("Gửi lại sau 0:${seconds.toString().padStart(2, '0')}", Modifier.weight(1f), size = 13.sp, color = Palette.Muted)
            } else {
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp).clickable(role = Role.Button) {
                        if (resending) return@clickable
                        scope.launch {
                            resending = true
                            error = account.resendCode()
                            if (error == null) { seconds = 60; code = "" }
                            resending = false
                        }
                    },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(if (resending) "Đang gửi…" else "Gửi lại mã", size = 13.5.sp, color = Palette.Primary, weight = FontWeight.SemiBold)
                }
            }
            Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Text("Đổi email", size = 13.5.sp, color = Palette.Primary, weight = FontWeight.SemiBold)
            }
        }
        BusyButton("Xác minh", account.busy, code.length == 6) { submit() }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Subtle).padding(14.dp)) {
            Text("Không thấy thư?", size = 13.5.sp, weight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Xem mục Spam/Quảng cáo. Thư gửi từ GoWander, không trả lời được. Mỗi email nhận tối đa 5 mã/giờ.", size = 12.5.sp, color = Palette.Muted)
        }
    }
}

/** l•••••@example.com: enough to recognise the address, not enough to leak it to someone looking. */
internal fun maskEmail(email: String?): String {
    if (email.isNullOrBlank()) return "email của bạn"
    val at = email.indexOf('@')
    if (at <= 1) return email
    return email.first() + "•".repeat((at - 1).coerceAtMost(6)) + email.substring(at)
}

// ---------------------------------------------------------------- A6

/** A6 · Quên mật khẩu: email → code → new password, then this device is signed in. */
@Composable
fun ForgotPasswordScreen(
    account: AccountStore,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(0) }
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var seconds by remember { mutableStateOf(60) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(step) {
        if (step == 1) {
            seconds = 60
            while (seconds > 0) {
                delay(1000)
                seconds--
            }
        }
    }

    AccountPage(onBack = onBack) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Email", "Mã", "Mật khẩu").forEachIndexed { i, label ->
                if (i > 0) Box(Modifier.weight(1f).height(2.dp).background(Palette.Line))
                Text(
                    "$label",
                    Modifier.clip(RoundedCornerShape(999.dp)).background(if (i <= step) Palette.Primary else Palette.Subtle)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    size = 11.5.sp,
                    weight = FontWeight.Bold,
                    color = if (i <= step) Palette.Bg else Palette.Muted,
                )
            }
        }
        when (step) {
            0 -> {
                BigTitle("Quên mật khẩu", "Nhập email bạn đã dùng để tạo tài khoản, GoWander gửi mã 6 số để bạn đặt mật khẩu mới.")
                AccountField("Email", email, "ban@example.com", ExtraIcons.mail, KeyboardType.Email) { email = it }
                error?.let { AccountError(it) }
                BusyButton("Gửi mã", account.busy, email.isNotBlank()) {
                    scope.launch {
                        error = account.forgotPassword(email)
                        if (error == null) step = 1
                    }
                }
                Text("Dù email có tài khoản hay không, GoWander luôn báo “Nếu email này có tài khoản, mã đã được gửi.”", size = 12.5.sp, color = Palette.Muted)
            }
            1 -> {
                BigTitle("Nhập mã", "Mã 6 số đã gửi tới ${maskEmail(email)}. Mã dùng được trong 10 phút.")
                CodeField(code) { code = it; error = null }
                error?.let { AccountError(it) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (seconds > 0) {
                        Text("Gửi lại sau 0:${seconds.toString().padStart(2, '0')}", Modifier.weight(1f), size = 13.sp, color = Palette.Muted)
                    } else {
                        Box(
                            Modifier.weight(1f).heightIn(min = 44.dp).clickable(role = Role.Button) {
                                scope.launch {
                                    error = account.forgotPassword(email)
                                    if (error == null) { seconds = 60; code = "" }
                                }
                            },
                            contentAlignment = Alignment.CenterStart,
                        ) { Text("Gửi lại mã", size = 13.5.sp, color = Palette.Primary, weight = FontWeight.SemiBold) }
                    }
                }
                Button("Tiếp tục", Modifier.fillMaxWidth()) { if (code.length == 6) { error = null; step = 2 } }
            }
            else -> {
                BigTitle("Mật khẩu mới", "Chọn mật khẩu mới cho tài khoản ${maskEmail(email)}.")
                PasswordField("Mật khẩu mới", password) { password = it }
                StrengthBars(passwordStrength(password))
                PasswordField("Nhập lại mật khẩu mới", confirm) { confirm = it }
                if (confirm.isNotEmpty() && confirm != password) AccountError("Hai lần nhập chưa giống nhau.")
                error?.let { AccountError(it) }
                BusyButton("Đặt mật khẩu mới", account.busy, password.isNotBlank() && password == confirm) {
                    scope.launch {
                        error = account.resetPassword(email, code, password)
                        if (error == null) onDone()
                    }
                }
                Text("Đổi xong, mọi thiết bị khác bị đăng xuất; máy này đăng nhập luôn.", size = 12.5.sp, color = Palette.Muted)
            }
        }
    }
}

// ---------------------------------------------------------------- A9

/** A9 · Xoá tài khoản: what is lost, what stays, a password (or Google) to confirm. */
@Composable
fun DeleteAccountScreen(
    account: AccountStore,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var understood by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val hasPassword = account.user?.hasPassword != false

    AccountPage("Xoá tài khoản", onBack = onBack) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.AccentSoft).padding(16.dp)) {
            Text("Việc này không khôi phục được", size = 16.sp, weight = FontWeight.Bold, color = Palette.Accent)
            Spacer(Modifier.height(6.dp))
            Text("Xoá xong, GoWander xoá ngay tên, email, số điện thoại và mật khẩu của bạn.", size = 12.5.sp, color = Palette.Ink)
        }
        Text("Bạn sẽ mất", size = 14.sp, weight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Subtle).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Tài khoản, không đăng nhập lại được bằng email/số cũ",
                "Portfolio đã đăng và lịch sử tìm kiếm trên máy chủ",
                "Mọi thiết bị bị đăng xuất",
            ).forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(ExtraIcons.close, 15.dp, Palette.Muted)
                    Spacer(Modifier.width(8.dp))
                    Text(line, size = 13.sp)
                }
            }
        }
        Text("Vẫn giữ", size = 14.sp, weight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Subtle).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(KitIcons.check, 15.dp, Palette.Bus)
            Spacer(Modifier.width(8.dp))
            Text("Feedbacks đã gửi: giữ nội dung nhưng gỡ tên/email, hiện “Tài khoản đã xoá”.", size = 13.sp)
        }
        if (hasPassword) {
            PasswordField("Xác nhận bằng mật khẩu", password) { password = it }
        } else {
            Text("Tài khoản này chỉ có Google: khi xoá, bạn xác nhận lại bằng Google.", size = 13.sp, color = Palette.Muted)
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Checkbox) { understood = !understood },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (understood) Palette.Primary else Palette.Bg)
                    .then(if (understood) Modifier else Modifier.border(1.5.dp, Palette.Line, RoundedCornerShape(6.dp))),
                contentAlignment = Alignment.Center,
            ) {
                if (understood) Icon(KitIcons.check, 14.dp, Palette.Bg)
            }
            Spacer(Modifier.width(10.dp))
            Text("Tôi hiểu việc này không thể hoàn tác.", size = 13.sp, color = Palette.Muted)
        }
        error?.let { AccountError(it) }
        val ready = understood && (!hasPassword || password.isNotBlank())
        Button(
            if (account.busy) "Đang xoá…" else "Xoá tài khoản",
            // Always red, paler until the form is complete: a grey button here looks like "Giữ tài khoản".
            Modifier.fillMaxWidth().alpha(if (ready && !account.busy) 1f else 0.55f),
            kind = ButtonKind.Accent,
        ) {
            if (!ready || account.busy) return@Button
            scope.launch {
                error = account.deleteAccount(password, null)
                if (error == null) onDeleted()
            }
        }
        Button("Giữ tài khoản", Modifier.fillMaxWidth(), kind = ButtonKind.Secondary) { onBack() }
    }
}
