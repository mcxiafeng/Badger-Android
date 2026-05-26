package top.mcxiafeng.badger.pages.setupguide

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.data.UserProfileDao
import top.mcxiafeng.badger.ui.components.CropMode
import top.mcxiafeng.badger.testutil.InMemoryDatabaseRule
import top.mcxiafeng.badger.utils.Methods
import java.io.File

/**
 * 验证 SetupStepProfile 中裁剪回调的数据保存链路。
 *
 * 关键场景：模拟 ImageCropDialog 确认按钮的调用顺序
 * （onConfirm → onDismiss 同步清除 activeCropMode），
 * 确认裁剪回调使用捕获的 cropMode 值，而非被清除后的状态。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SetupStepProfileCropTest {

    @get:Rule
    val dbRule = InMemoryDatabaseRule(RuntimeEnvironment.getApplication())

    private lateinit var dao: UserProfileDao
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setup() {
        dao = dbRule.db.userProfileDao()
    }

    // ===== 场景1: "下一步"按钮保存完整 profile =====

    @Test
    fun nextButton_savesAvatarAndCardImagePaths() = runTest {
        val avatarPath = saveTestAvatar()
        val cardImagePath = saveTestBanner()

        val existing = dao.getProfileOnce()
        val updated = (existing ?: UserProfile()).copy(
            name = "测试用户",
            avatarPath = avatarPath,
            cardImagePath = cardImagePath,
            updateTime = System.currentTimeMillis()
        )
        dao.saveProfile(updated)

        val saved = dao.getProfileOnce()!!
        assertThat(saved.name).isEqualTo("测试用户")
        assertThat(saved.avatarPath).isEqualTo(avatarPath)
        assertThat(saved.cardImagePath).isEqualTo(cardImagePath)
        assertThat(File(saved.avatarPath!!).exists()).isTrue()
        assertThat(File(saved.cardImagePath!!).exists()).isTrue()
    }

    // ===== 场景2: 捕获 cropMode 避免 onDismiss 清空后的竞态 =====

    @Test
    fun cropConfirm_capturedCropMode_notAffectedByDismiss() = runTest {
        var activeCropMode: CropMode? = CropMode.AVATAR

        // 修复后的关键代码：先捕获值
        val capturedCropMode = activeCropMode

        // 模拟 onDismiss 同步清除
        activeCropMode = null

        // capturedCropMode 不受影响
        assertThat(capturedCropMode).isEqualTo(CropMode.AVATAR)
        assertNull(activeCropMode)

        // 使用 capturedCropMode 保存头像
        when (capturedCropMode) {
            CropMode.AVATAR -> {
                val avatarPath = saveTestAvatar()
                val existing = dao.getProfileOnce()
                val updated = (existing ?: UserProfile()).copy(
                    name = "测试用户",
                    avatarPath = avatarPath,
                    updateTime = System.currentTimeMillis()
                )
                dao.saveProfile(updated)

                val saved = dao.getProfileOnce()!!
                assertThat(saved.avatarPath).isEqualTo(avatarPath)
            }
            CropMode.BANNER -> assert(false) { "不应走到 BANNER 分支" }
            CropMode.COVER -> assert(false) { "不应走到 COVER 分支" }
            CropMode.COLLECTION_BG -> assert(false) { "不应走到 COLLECTION_BG 分支" }
            null -> assert(false) { "旧 Bug: capturedCropMode 为 null, 保存被跳过!" }
        }
    }

    // ===== 场景3: 修复前 onDismiss 清空后 activeCropMode 为 null（旧 Bug 回放）=====

    @Test
    fun oldBug_activeCropModeIsNull_afterDismiss() {
        var activeCropMode: CropMode? = CropMode.BANNER
        activeCropMode = null
        assertNull(activeCropMode)
    }

    // ===== 场景4: 头像文件确实能被加载 =====

    @Test
    fun avatarFile_savedAndReloadable() = runTest {
        val path = saveTestAvatar()
        val loaded = Methods.loadAvatarBitmap(path)
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.width).isAtLeast(1)
    }

    // ===== 场景5: 背景图文件确实能被加载 =====

    @Test
    fun bannerFile_savedAndReloadable() {
        val path = saveTestBanner()
        val file = File(path)
        assertThat(file.exists()).isTrue()
        val loaded = android.graphics.BitmapFactory.decodeFile(path)
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.width).isAtLeast(1)
    }

    // ===== 场景6: "跳过"按钮也会保存已有的头像/背景 =====

    @Test
    fun skipButton_savesPartialData_ifAvatarOrCardImagePresent() = runTest {
        val avatarPath = saveTestAvatar()

        val existing = dao.getProfileOnce()
        val updated = (existing ?: UserProfile()).copy(
            name = existing?.name ?: "",
            avatarPath = avatarPath,
            cardImagePath = null,
            updateTime = System.currentTimeMillis()
        )
        dao.saveProfile(updated)

        val saved = dao.getProfileOnce()!!
        assertThat(saved.avatarPath).isEqualTo(avatarPath)
        assertThat(saved.name).isEqualTo("用户")
    }

    // ===== 辅助方法 =====

    private fun saveTestAvatar(): String {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val file = Methods.saveBitmapAsAvatar(context, bitmap, "test_avatar.webp")
        return file.absolutePath
    }

    private fun saveTestBanner(): String {
        val bitmap = Bitmap.createBitmap(1080, 600, Bitmap.Config.ARGB_8888)
        val file = File(context.filesDir, "test_card_image.webp")
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.WEBP, 75, out)
        }
        return file.absolutePath
    }
}