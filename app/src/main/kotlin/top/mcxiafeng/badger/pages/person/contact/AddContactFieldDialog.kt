package top.mcxiafeng.badger.pages.person.contact

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinInject
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.CustomField
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.network.PlatformManifestRepository
import top.mcxiafeng.badger.ocr.PlatformFieldDef
import top.mcxiafeng.badger.ocr.SYSTEM_FIELDS
import top.mcxiafeng.badger.ocr.SYSTEM_FIELD_KEYS
import top.mcxiafeng.badger.ocr.buildPlatformLink
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.components.PlatformIcon
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private const val TAG = "AddContactFieldDialog"

private sealed class GridItem {
    abstract val displayName: String
    abstract val fieldKey: String?

    data class SystemOrPlatform(val def: PlatformFieldDef) : GridItem() {
        override val displayName = def.displayName
        override val fieldKey = def.fieldKey
    }

    data class CustomFieldItem(val field: CustomField) : GridItem() {
        override val displayName = field.fieldName
        override val fieldKey = null
    }

    data object NewCustomField : GridItem() {
        override val displayName = "添加新平台"
        override val fieldKey = null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddContactFieldDialog(
    repository: ContactRepository,
    fieldRepository: FieldRepository,
    contactId: Long,
    existingFieldKeys: Set<String>,
    existingCustomFieldIds: Set<Long>,
    existingPlatformKeys: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val manifestRepo: PlatformManifestRepository = koinInject()
    val addableDefs by manifestRepo.addable.collectAsState()
    LaunchedEffect(Unit) { manifestRepo.ensureLoaded() }

    val customFields by fieldRepository.getAllEnabledCustomFields().collectAsState(initial = emptyList())

    val gridItems = remember(customFields, addableDefs) {
        buildList {
            SYSTEM_FIELDS.forEach { add(GridItem.SystemOrPlatform(it)) }
            addableDefs.forEach { add(GridItem.SystemOrPlatform(it)) }
            customFields.forEach { add(GridItem.CustomFieldItem(it)) }
            add(GridItem.NewCustomField)
        }
    }

    var isGridPhase by remember { mutableStateOf(true) }
    var selectedItem by remember { mutableStateOf<GridItem?>(null) }
    var fieldValue by rememberSaveable { mutableStateOf("") }
    var isCreatingCustomField by remember { mutableStateOf(false) }
    var newCustomFieldName by rememberSaveable { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val currentItem = selectedItem
    val currentDef = (currentItem as? GridItem.SystemOrPlatform)?.def

    WindowDialog(
        show = true,
        title = if (isGridPhase) "增加联系方式" else "添加 ${currentItem?.displayName ?: ""}",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        ) {
            if (isGridPhase) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gridItems.forEach { item ->
                        val isExisting = when (item) {
                            is GridItem.SystemOrPlatform -> item.def.fieldKey in existingFieldKeys || item.def.fieldKey in existingPlatformKeys
                            is GridItem.CustomFieldItem -> item.field.id in existingCustomFieldIds
                            is GridItem.NewCustomField -> false
                        }
                        val iconKey = when (item) {
                            is GridItem.SystemOrPlatform -> item.def.fieldKey
                            is GridItem.CustomFieldItem -> "website"
                            is GridItem.NewCustomField -> "website"
                        }
                        val label = when (item) {
                            is GridItem.SystemOrPlatform -> item.def.displayName
                            is GridItem.CustomFieldItem -> item.field.fieldName
                            is GridItem.NewCustomField -> "自定义"
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable(enabled = !isExisting) {
                                    if (item is GridItem.NewCustomField) {
                                        isCreatingCustomField = true
                                        newCustomFieldName = ""
                                        fieldValue = ""
                                        selectedItem = item
                                        isGridPhase = false
                                    } else {
                                        selectedItem = item
                                        isGridPhase = false
                                        fieldValue = ""
                                        isCreatingCustomField = false
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            PlatformIcon(
                                fieldKey = iconKey,
                                color = if (isExisting) MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f) else MiuixTheme.colorScheme.primary,
                                sizeDp = 32f
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = label,
                                style = MiuixTheme.textStyles.footnote2,
                                color = if (isExisting) MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f) else MiuixTheme.colorScheme.onBackground,
                                maxLines = 1
                            )
                        }
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    IconButton(onClick = {
                        isGridPhase = true
                        fieldValue = ""
                        newCustomFieldName = ""
                        isCreatingCustomField = false
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MiuixTheme.colorScheme.onBackground)
                    }
                    Text(text = "添加 ${currentItem?.displayName ?: ""}", style = MiuixTheme.textStyles.title3)
                }

                if (isCreatingCustomField) {
                    Text(text = "新建平台", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(Modifier.height(4.dp))
                    TextField(
                        value = newCustomFieldName,
                        onValueChange = { newCustomFieldName = it },
                        label = "请输入新平台名称",
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    val hint = when (currentItem) {
                        is GridItem.SystemOrPlatform -> when (currentItem.def.fieldKey) {
                            "phone" -> "电话号码"
                            "email" -> "邮箱地址"
                            else -> currentItem.def.inputHint.ifBlank { currentItem.displayName }
                        }
                        is GridItem.CustomFieldItem -> currentItem.field.fieldName
                        else -> "值"
                    }
                    Text(text = currentItem?.displayName ?: "", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(Modifier.height(4.dp))
                    TextField(
                        value = fieldValue,
                        onValueChange = { fieldValue = it },
                        label = hint,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val hasExisting = when (currentItem) {
                        is GridItem.SystemOrPlatform -> currentItem.def.fieldKey in existingFieldKeys || currentItem.def.fieldKey in existingPlatformKeys
                        is GridItem.CustomFieldItem -> currentItem.field.id in existingCustomFieldIds
                        else -> false
                    }
                    if (hasExisting) {
                        Spacer(Modifier.height(4.dp))
                        Text(text = "已有该类型字段，将添加为多值", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (isCreatingCustomField) {
                    DialogButtonRow(
                        negativeText = "取消",
                        positiveText = if (isSaving) "创建中..." else "创建并输入值",
                        onNegative = {
                            isGridPhase = true
                            selectedItem = null
                        },
                        onPositive = {
                            val name = newCustomFieldName.trim()
                            if (name.isBlank()) return@DialogButtonRow
                            isSaving = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val id = fieldRepository.insertCustomField(CustomField(fieldName = name, fieldType = "text", options = ""))
                                    val field = fieldRepository.getCustomFieldById(id)
                                    withContext(Dispatchers.Main) {
                                        isSaving = false
                                        if (field != null) {
                                            selectedItem = GridItem.CustomFieldItem(field)
                                            isCreatingCustomField = false
                                            fieldValue = ""
                                        } else Toast.makeText(context, "创建失败", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isSaving = false
                                        Toast.makeText(context, "创建失败", Toast.LENGTH_SHORT).show()
                                    }
                                    Log.e(TAG, "创建自定义字段失败", e)
                                }
                            }
                        },
                        positiveEnabled = newCustomFieldName.isNotBlank() && !isSaving
                    )
                } else {
                    val canSave = fieldValue.isNotBlank() && !isSaving
                    DialogButtonRow(
                        negativeText = "取消",
                        positiveText = if (isSaving) "保存中..." else "保存",
                        onNegative = {
                            isGridPhase = true
                            selectedItem = null
                        },
                        onPositive = {
                            val value = fieldValue.trim()
                            if (value.isBlank()) return@DialogButtonRow
                            val item = currentItem ?: return@DialogButtonRow
                            isSaving = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    when (item) {
                                        is GridItem.SystemOrPlatform -> {
                                            val key = item.def.fieldKey
                                            if (key in SYSTEM_FIELD_KEYS) {
                                                val field = fieldRepository.getFieldByKey(key) ?: return@launch
                                                fieldRepository.saveContactFieldValues(contactId, mapOf(field.id to value))
                                            } else {
                                                repository.updateContactPlatform(
                                                    contactId,
                                                    key,
                                                    PlatformEntry(value = value, jumpLink = buildPlatformLink(key, value), displayName = null),
                                                )
                                            }
                                        }
                                        is GridItem.CustomFieldItem -> fieldRepository.saveContactCustomFieldValues(contactId, mapOf(item.field.id to value))
                                        is GridItem.NewCustomField -> Unit
                                    }
                                    withContext(Dispatchers.Main) {
                                        isSaving = false
                                        onConfirm()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isSaving = false
                                        Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                                    }
                                    Log.e(TAG, "保存失败", e)
                                }
                            }
                        },
                        positiveEnabled = canSave
                    )
                }
            }
        }
    }
}
