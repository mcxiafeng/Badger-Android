package top.mcxiafeng.badger.pages.person.contact

import android.os.Bundle

/** Text only: never put avatar bitmaps into the saved-instance-state Binder payload. */
internal object ContactDraftCodec {
    fun encode(draft: ContactDraft): Bundle = Bundle().apply {
        putString("name", draft.name)
        putString("note", draft.note)
        putString("bio", draft.bio)
        putLongArray("collections", draft.collectionIds.toLongArray())
    }

    fun decode(bundle: Bundle?): ContactDraft {
        if (bundle == null) return ContactDraft()
        return ContactDraft(
            name = bundle.getString("name").orEmpty(),
            note = bundle.getString("note").orEmpty(),
            bio = bundle.getString("bio").orEmpty(),
            collectionIds = bundle.getLongArray("collections")?.toSet().orEmpty(),
        )
    }
}
