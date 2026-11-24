package top.mcxiafeng.badger.pages.person.contact

import android.os.Bundle
import top.mcxiafeng.badger.data.PlatformEntry

/** Text only: never put avatar bitmaps into the saved-instance-state Binder payload. */
internal object ContactDraftCodec {
    fun encode(draft: ContactDraft): Bundle = Bundle().apply {
        putString("name", draft.name)
        putString("note", draft.note)
        putStringArrayList("tags", ArrayList(draft.tags))
        putLongArray("collections", draft.collectionIds.toLongArray())
        putStringArrayList("fieldOrder", ArrayList(draft.fields.map { it.id }))
        putBundle("fields", Bundle().apply {
            draft.fields.forEach { field -> putBundle(field.id, encodeField(field)) }
        })
        putBundle("platforms", Bundle().apply {
            draft.platforms.forEach { (key, entry) -> putBundle(key, encodePlatform(entry)) }
        })
    }

    private fun encodeField(field: ContactDraftField): Bundle = Bundle().apply {
        putString("key", field.key)
        field.customFieldId?.let { putLong("customId", it) }
        putString("name", field.name)
        putString("value", field.value)
    }

    private fun encodePlatform(entry: PlatformEntry): Bundle = Bundle().apply {
        putString("value", entry.value)
        putString("displayName", entry.displayName)
        putString("jumpLink", entry.jumpLink)
        putString("originalLink", entry.originalLink)
        putString("avatarUrl", entry.avatarUrl)
    }

    fun decode(bundle: Bundle?): ContactDraft {
        if (bundle == null) return ContactDraft()
        val fields = bundle.getBundle("fields")
        val platforms = bundle.getBundle("platforms")
        val fieldIds = bundle.getStringArrayList("fieldOrder") ?: fields?.keySet()?.toList().orEmpty()
        return ContactDraft(
            name = bundle.getString("name").orEmpty(), note = bundle.getString("note").orEmpty(),
            tags = bundle.getStringArrayList("tags").orEmpty().toSet(),
            collectionIds = bundle.getLongArray("collections")?.toSet().orEmpty(),
            fields = fieldIds.map { id ->
                val value = requireNotNull(fields?.getBundle(id))
                ContactDraftField(id = id, key = value.getString("key"),
                    customFieldId = if (value.containsKey("customId")) value.getLong("customId") else null,
                    name = value.getString("name").orEmpty(), value = value.getString("value").orEmpty())
            },
            platforms = platforms?.keySet().orEmpty().associateWith { key ->
                val entry = requireNotNull(platforms?.getBundle(key))
                PlatformEntry(value = entry.getString("value"), displayName = entry.getString("displayName"),
                    jumpLink = entry.getString("jumpLink").orEmpty(), originalLink = entry.getString("originalLink"),
                    avatarUrl = entry.getString("avatarUrl"))
            },
        )
    }
}
