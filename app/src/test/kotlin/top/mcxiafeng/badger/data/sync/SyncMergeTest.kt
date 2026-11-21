package top.mcxiafeng.badger.data.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.mcxiafeng.badger.network.sync.SyncJson

class SyncMergeTest {
    @Test fun independentEditsKeepBothChanges() {
        val base = SyncJson.parse("""{"notes":"旧备注","bio":"旧简介"}""")
        val local = SyncJson.parse("""{"notes":"新备注","bio":"旧简介"}""")
        val remote = SyncJson.parse("""{"notes":"旧备注","bio":"新简介"}""")
        val result = SyncMerge.merge(base, local, remote)
        assertThat(result.conflicts).isEmpty()
        assertThat(result.value?.asJsonObject?.get("notes")?.asString).isEqualTo("新备注")
        assertThat(result.value?.asJsonObject?.get("bio")?.asString).isEqualTo("新简介")
    }

    @Test fun divergentFieldWaitsForAChoice() {
        val base = SyncJson.parse("""{"notes":"原文"}""")
        val local = SyncJson.parse("""{"notes":"本机修改"}""")
        val remote = SyncJson.parse("""{"notes":"云端修改"}""")
        val result = SyncMerge.merge(base, local, remote)
        assertThat(result.conflicts).containsExactly("notes")
        assertThat(result.value).isEqualTo(local)
        assertThat(SyncMerge.merge(base, local, remote, mapOf("notes" to false)).value).isEqualTo(remote)
    }

    @Test fun deletionCannotEraseAnEditedChild() {
        val base = SyncJson.parse("""{"fields":[{"id":"one","value":"旧值"}]}""")
        val local = SyncJson.parse("""{"fields":[{"id":"one","value":"重要修改"}]}""")
        val remote = SyncJson.parse("""{"fields":[]}""")
        val conflict = SyncMerge.merge(base, local, remote)
        assertThat(conflict.conflicts).containsExactly("fields[one]")
        assertThat(conflict.value).isEqualTo(local)
        assertThat(SyncMerge.merge(base, local, remote, mapOf("fields[one]" to false)).value).isEqualTo(remote)
    }

    @Test fun sameNamesDoNotMergeDifferentIdentities() {
        val base = SyncJson.parse("""{"records":[]}""")
        val local = SyncJson.parse("""{"records":[{"id":"A","name":"同名"}]}""")
        val remote = SyncJson.parse("""{"records":[{"id":"B","name":"同名"}]}""")
        val merged = SyncMerge.merge(base, local, remote)
        assertThat(merged.conflicts).isEmpty()
        assertThat(merged.value?.asJsonObject?.getAsJsonArray("records")?.size()).isEqualTo(2)
    }

    @Test fun explicitClearSurvivesAnUnrelatedRemoteEdit() {
        val base = SyncJson.parse("""{"notes":"删除这段","bio":"旧简介"}""")
        val local = SyncJson.parse("""{"notes":null,"bio":"旧简介"}""")
        val remote = SyncJson.parse("""{"notes":"删除这段","bio":"新简介"}""")
        val merged = SyncMerge.merge(base, local, remote)
        assertThat(merged.conflicts).isEmpty()
        assertThat(merged.value?.asJsonObject?.get("notes")?.isJsonNull).isTrue()
        assertThat(merged.value?.asJsonObject?.get("bio")?.asString).isEqualTo("新简介")
    }

    @Test fun deviceClockCannotChooseTheWinningBusinessField() {
        val base = SyncJson.parse("""{"notes":"原文","updateTime":10}""")
        val local = SyncJson.parse("""{"notes":"本机","updateTime":999999999}""")
        val remote = SyncJson.parse("""{"notes":"云端","updateTime":11}""")
        assertThat(SyncMerge.merge(base, local, remote).conflicts).containsExactly("notes")
    }

    @Test fun updateTimestampsDoNotBlockIndependentBusinessEdits() {
        val base = SyncJson.parse("""{"notes":"旧备注","bio":"旧简介","updateTime":10}""")
        val local = SyncJson.parse("""{"notes":"新备注","bio":"旧简介","updateTime":20}""")
        val remote = SyncJson.parse("""{"notes":"旧备注","bio":"新简介","updateTime":30}""")
        val merged = SyncMerge.merge(base, local, remote)
        assertThat(merged.conflicts).isEmpty()
        assertThat(merged.value?.asJsonObject?.get("updateTime")?.asLong).isEqualTo(30)
    }

    @Test fun initialImportDoesNotTreatMissingValuesAsClearCommands() {
        val incoming = SyncJson.parse("""{"notes":null,"profile":{"accounts":[]},"createTime":1}""")
        val target = SyncJson.parse("""{"notes":"云端重要备注","profile":{"accounts":[{"id":"A"}]},"createTime":2}""")
        assertThat(ImportRemapper.supplement(incoming, target)).isEqualTo(target)
    }

    @Test fun emptyCloudFieldsAcceptInitialLocalDataWithoutConflict() {
        val incoming = SyncJson.parse("""{"notes":"本地备注","profile":{"circleName":"我的名片"}}""")
        val target = SyncJson.parse("""{"notes":null,"profile":{"circleName":null}}""")
        val merged = SyncMerge.merge(null, ImportRemapper.supplement(incoming, target), target)
        assertThat(merged.conflicts).isEmpty()
        assertThat(merged.value).isEqualTo(incoming)
    }

    @Test fun initialImportWithDifferentValuesStillNeedsAChoice() {
        val incoming = SyncJson.parse("""{"notes":"本地重要备注"}""")
        val target = SyncJson.parse("""{"notes":"云端重要备注"}""")
        val merged = SyncMerge.merge(null, ImportRemapper.supplement(incoming, target), target)
        assertThat(merged.conflicts).containsExactly("notes")
    }
}
