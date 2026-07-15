package top.mcxiafeng.badger.pages.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Login form. Calls [AuthViewModel.signIn] which goes through
 * [top.mcxiafeng.badger.data.repository.UserAuthRepository]. On success
 * the parent's auth-state flow turns to SignedIn and the navigator routes
 * to MainTabs.
 */
@Composable
fun LoginScreen(
    onAuthed: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel<AuthViewModel>(key = "login"),
) {
    LaunchedEffect(Unit) { viewModel.reset() }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state) {
        if (state is AuthUiState.SignedIn) onAuthed()
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("登录 Badger", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = viewModel.username.value,
            onValueChange = viewModel.onUsername,
            label = { Text("用户名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = viewModel.password.value,
            onValueChange = viewModel.onPassword,
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.signIn() },
            enabled = state !is AuthUiState.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state is AuthUiState.Loading) "登录中…" else "登录") }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onNavigateToRegister, modifier = Modifier.fillMaxWidth()) {
            Text("还没有账号? 注册")
        }
        (state as? AuthUiState.Error)?.let {
            Spacer(Modifier.height(8.dp))
            Text(it.message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun RegisterScreen(
    onAuthed: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel<AuthViewModel>(key = "register"),
) {
    LaunchedEffect(Unit) { viewModel.reset() }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state) {
        if (state is AuthUiState.SignedIn) onAuthed()
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("注册账号", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = viewModel.username.value,
            onValueChange = viewModel.onUsername,
            label = { Text("用户名(3-32 字符)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = viewModel.email.value,
            onValueChange = viewModel.onEmail,
            label = { Text("邮箱(可选)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = viewModel.password.value,
            onValueChange = viewModel.onPassword,
            label = { Text("密码(>=8 字符)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.register() },
            enabled = state !is AuthUiState.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state is AuthUiState.Loading) "注册中…" else "注册") }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onNavigateToLogin, modifier = Modifier.fillMaxWidth()) {
            Text("已有账号? 登录")
        }
        (state as? AuthUiState.Error)?.let {
            Spacer(Modifier.height(8.dp))
            Text(it.message, color = MaterialTheme.colorScheme.error)
        }
    }
}
