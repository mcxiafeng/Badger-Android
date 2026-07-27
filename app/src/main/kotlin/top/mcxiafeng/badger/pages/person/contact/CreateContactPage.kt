package top.mcxiafeng.badger.pages.person.contact

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "CreateContactPage"

@Composable
fun CreateContactPage(
    targetCollectionId: Long? = null,
    onBack: () -> Unit = {},
    onNavigateToContactDetail: (Long) -> Unit = {},
    viewModel: CreateContactViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    var contactName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "新建联系人",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "输入联系人姓名",
                style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = contactName,
                onValueChange = { contactName = it },
                label = "姓名",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val name = contactName.trim()
                    if (name.isBlank()) return@Button
                    scope.launch(Dispatchers.IO) {
                        val id = viewModel.createMinimalContact(name, targetCollectionId)
                        Log.d(TAG, "联系人创建成功: id=$id, name=$name")
                        withContext(Dispatchers.Main) {
                            onNavigateToContactDetail(id)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = contactName.trim().isNotBlank(),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Text(text = "创建")
            }
        }
    }
}
