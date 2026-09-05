package top.mcxiafeng.badger.pages.person.contact.dialogs

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
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.model.CustomField
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.di.KoinComponentBy
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
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft

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

    // [Phase 4 剩余] 平台清单服务端驱动：可添加平台集合（含服务端独有/自定义）来自
    // /api/resolve/platforms 合并结果，离线兜底本地 PLATFORM_FIELDS。
    val manifestRepo = remember { KoinComponentBy.get<PlatformManifestRepository>() }
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

    // 当前选中项的索引用于灰显判断
    val currentItem = selectedItem
    val currentDef = (currentItem as? GridItem.SystemOrPlatform)?.def

    WindowDialog(
        show = true,
        title = if (isGridPhase) "增加联系方式" else "添加 ${currentItem?.displayName ?: ""}",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            if (isGridPhase) {
                // ========== Phase 1: 图标网格 ==========
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gridItems.forEach { item ->
                        val isExisting = when (item) {
                            is GridItem.SystemOrPlatform -> {
                                item.def.fieldKey in existingFieldKeys ||
                                    item.def.fieldKey in existingPlatformKeys
                            }
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
                                        Log.d(TAG, "选择新建自定义字段")
                                    } else {
                                        selectedItem = item
                                        isGridPhase = false
                                        fieldValue = ""
                                        isCreatingCustomField = false
                                        Log.d(TAG, "选择字段: ${item.displayName}")
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            PlatformIcon(
                                fieldKey = iconKey,
                                color = if (isExisting) MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f) else MiuixTheme.colorScheme.primary,
                                sizeDp = 32f
                            )
                            Spacer(modifier = Modifier.height(4.dp))
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
                // ========== Phase 2: 简易表单 ==========
                // 返回按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    IconButton(onClick = {
                        isGridPhase = true
                        fieldValue = ""
                        newCustomFieldName = ""
                        isCreatingCustomField = false
                        Log.d(TAG, "返回图标网格")
                    }) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "添加 ${currentItem?.displayName ?: ""}",
                        style = MiuixTheme.textStyles.title3
                    )
                }

                if (isCreatingCustomField) {
                    // 新建自定义字段：先输入字段名
                    Text(
                        text = "新建平台",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = newCustomFieldName,
                        onValueChange = { newCustomFieldName = it },
                        label = "请输入新平台名称",
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // 正常输入值
                    val hint = when (currentItem) {
                        is GridItem.SystemOrPlatform -> {
                            when (currentItem.def.fieldKey) {
                                "phone" -> "电话号码"
                                "email" -> "邮箱地址"
                                else -> currentItem.def.inputHint.ifBlank { currentItem.displayName }
                            }
                        }
                        is GridItem.CustomFieldItem -> currentItem.field.fieldName
                        else -> "值"
                    }

                    Text(
                        text = currentItem?.displayName ?: "",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = fieldValue,
                        onValueChange = { fieldValue = it },
                        label = hint,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 已有同类型值提示
                    val hasExisting = when (currentItem) {
                        is GridItem.SystemOrPlatform -> {
                            currentItem.def.fieldKey in existingFieldKeys ||
                                currentItem.def.fieldKey in existingPlatformKeys
                        }
                        is GridItem.CustomFieldItem -> currentItem.field.id in existingCustomFieldIds
                        else -> false
                    }
                    if (hasExisting) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "已有该类型字段，将添加为多值",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 按钮行
                if (isCreatingCustomField) {
                    // 自定义字段创建：取消 + 创建并输入值
                    DialogButtonRow(
                        negativeText = "取消",
                        positiveText = if (isSaving) "创建中..." else "创建并输入值",
                        onNegative = {
                            isGridPhase = true
                            selectedItem = null
                            Log.d(TAG, "取消创建自定义字段")
                        },
                        onPositive = {
                            val name = newCustomFieldName.trim()
                            if (name.isBlank()) return@DialogButtonRow
                            isSaving = true
                            Log.d(TAG, "开始创建自定义字段: $name")
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val id = fieldRepository.insertCustomField(
                                        CustomField(fieldName = name, fieldType = "text", options = "")
                                    )
                                    val field = fieldRepository.getCustomFieldById(id)
                                    withContext(Dispatchers.Main) {
                                        isSaving = false
                                        if (field != null) {
                                            selectedItem = GridItem.CustomFieldItem(field)
                                            isCreatingCustomField = false
                                            fieldValue = ""
                                            Log.d(TAG, "自定义字段创建成功: id=$id, name=$name")
                                        } else {
                                            Toast.makeText(context, "创建失败", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isSaving = false
                                        Toast.makeText(context, "创建失败", Toast.LENGTH_SHORT).show()
                                        Log.e(TAG, "创建自定义字段失败", e)
                                    }
                                }
                            }
                        },
                        positiveEnabled = newCustomFieldName.isNotBlank() && !isSaving
                    )
                } else {
                    // 正常保存
                    val canSave = fieldValue.isNotBlank() && !isSaving
                    DialogButtonRow(
                        negativeText = "取消",
                        positiveText = if (isSaving) "保存中..." else "保存",
                        onNegative = {
                            isGridPhase = true
                            selectedItem = null
                            Log.d(TAG, "取消保存")
                        },
                        onPositive = {
                            val value = fieldValue.trim()
                            if (value.isBlank()) return@DialogButtonRow
                            val item = currentItem ?: return@DialogButtonRow
                            isSaving = true
                            Log.d(TAG, "开始保存: ${item.displayName}, value=$value")
                            scope.launch(Dispatchers.IO) {
                                try {
                                    when (item) {
                                        is GridItem.SystemOrPlatform -> {
                                            val key = item.def.fieldKey
                                            if (key in SYSTEM_FIELD_KEYS) {
                                                val field = fieldRepository.getFieldByKey(key) ?: return@launch
                                                fieldRepository.saveContactFieldValues(contactId, mapOf(field.id to value))
                                                Log.d(TAG, "保存系统字段: key=$key, fieldId=${field.id}, value=$value")
                                            } else {
                                                // [Phase 4 剩余] 平台字段：本地 PLATFORM_FIELDS + 服务端清单
                                                // 独有/自定义 key 都走这里 —— 不再依赖 PLATFORM_FIELD_KEYS 白名单，
                                                // 否则服务端自定义平台保存会因 getFieldByKey 命中 null 而静默 no-op。
                                                val entry = PlatformEntry(
                                                    value = value,
                                                    jumpLink = buildPlatformLink(key, value),
                                                    displayName = null
                                                )
                                                repository.updateContactPlatform(contactId, key, entry)
                                                Log.d(TAG, "保存平台字段: key=$key, value=$value")
                                            }
                                        }
                                        is GridItem.CustomFieldItem -> {
                                            fieldRepository.saveContactCustomFieldValues(
                                                contactId, mapOf(item.field.id to value)
                                            )
                                            Log.d(TAG, "保存自定义字段: fieldId=${item.field.id}, fieldName=${item.field.fieldName}, value=$value")
                                        }
                                        is GridItem.NewCustomField -> { /* 不应该到这里 */ }
                                    }
                                    withContext(Dispatchers.Main) {
                                        isSaving = false
                                        onConfirm()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isSaving = false
                                        Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                                        Log.e(TAG, "保存失败", e)
                                    }
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
