package top.mcxiafeng.badger.stress

import android.os.Debug
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import top.mcxiafeng.badger.data.*
import top.mcxiafeng.badger.domain.FilterContactsUseCase
import top.mcxiafeng.badger.testutil.InMemoryDatabaseRule
import top.mcxiafeng.badger.utils.PinyinUtils
import java.util.Locale
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Badger 压力测试 — Robolectric (JVM, 内存数据库)
 *
 * 测试维度:
 * 1. 批量插入性能 (1K / 10K / 50K / 100K)
 * 2. 全表查询性能
 * 3. 搜索性能 (LIKE '%query%')
 * 4. 拼音排序性能 (FilterContactsUseCase)
 * 5. 重复检测性能
 * 6. 名片夹查询性能
 * 7. 内存占用
 * 8. 导入/导出性能
 *
 * 运行: ./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.stress.DatabaseStressTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DatabaseStressTest {

    @get:Rule
    val dbRule = InMemoryDatabaseRule(RuntimeEnvironment.getApplication())

    private lateinit var contactDao: ContactDao
    private lateinit var contactFieldDao: ContactFieldDao
    private lateinit var contactFieldValueDao: ContactFieldValueDao
    private lateinit var cardCollectionDao: CardCollectionDao
    private lateinit var scanResultDao: ScanResultDao
    private lateinit var filterUseCase: FilterContactsUseCase

    // 测试结果收集
    private val results = mutableListOf<TestResult>()

    data class TestResult(
        val testName: String,
        val dataSize: Int,
        val elapsedMs: Long,
        val memoryUsedMB: Long = 0,
        val notes: String = ""
    )

    @Before
    fun setup() {
        contactDao = dbRule.db.contactDao()
        contactFieldDao = dbRule.db.contactFieldDao()
        contactFieldValueDao = dbRule.db.contactFieldValueDao()
        cardCollectionDao = dbRule.db.cardCollectionDao()
        scanResultDao = dbRule.db.scanResultDao()
        filterUseCase = FilterContactsUseCase()
    }

    @After
    fun printReport() {
        println("\n" + "=".repeat(80))
        println("BADGER STRESS TEST REPORT")
        println("=".repeat(80))
        println(String.format("%-30s %10s %10s %10s %s", "Test", "DataSize", "Time(ms)", "Mem(MB)", "Notes"))
        println("-".repeat(80))
        for (r in results) {
            println(String.format("%-30s %10d %10d %10d %s",
                r.testName, r.dataSize, r.elapsedMs, r.memoryUsedMB, r.notes))
        }
        println("=".repeat(80))
    }

    // ==================== 1. 批量插入性能 ====================

    @Test
    fun test01_insert_1000_contacts() = runTest {
        testBatchInsert(1_000)
    }

    @Test
    fun test02_insert_10000_contacts() = runTest {
        testBatchInsert(10_000)
    }

    @Test
    fun test03_insert_50000_contacts() = runTest {
        testBatchInsert(50_000)
    }

    @Test
    fun test04_insert_100000_contacts() = kotlinx.coroutines.runBlocking {
        testBatchInsert(100_000)
    }

    private suspend fun testBatchInsert(count: Int) {
        val memBefore = getUsedMemoryMB()
        val fields = contactFieldDao.getAllFieldsOnce()

        val elapsed = measureTimeMillis {
            val batchSize = 1000
            for (batchStart in 0 until count step batchSize) {
                val batchEnd = minOf(batchStart + batchSize, count)
                dbRule.db.runInTransaction {
                    runBlocking {
                        for (i in batchStart until batchEnd) {
                            val contact = Contact(
                                name = generateName(i),
                                note = if (i % 10 == 0) "备注$i" else null
                            )
                            val contactId = contactDao.insertContact(contact)
                            // 给部分联系人添加字段值
                            if (fields.isNotEmpty() && i % 3 != 0) {
                                val fieldValues = generateFieldValues(contactId, i, fields)
                                contactFieldValueDao.insertOrUpdateFieldValues(fieldValues)
                            }
                        }
                    }
                }
            }
        }
        val memAfter = getUsedMemoryMB()
        val allContacts = contactDao.getAllContacts().first()
        val actualCount = allContacts.size

        results.add(TestResult("批量插入", count, elapsed, memAfter - memBefore,
            "实际=$actualCount, ${count * 1000 / maxOf(elapsed, 1)} rec/s"))

        assertThat(actualCount).isGreaterThan(count - 1)
        println("[PASS] 插入 $count 联系人: ${elapsed}ms")
    }

    // ==================== 2. 全表查询性能 ====================

    @Test
    fun test05_full_table_query_100k() = kotlinx.coroutines.runBlocking {
        seedContacts(100_000)
        val memBefore = getUsedMemoryMB()

        val elapsed = measureTimeMillis {
            val contacts = contactDao.getAllContacts().first()
            assertThat(contacts).hasSize(100_000)
        }
        val memAfter = getUsedMemoryMB()

        results.add(TestResult("全表查询(100K)", 100_000, elapsed, memAfter - memBefore))
        println("[PASS] 全表查询 100K: ${elapsed}ms")
    }

    // ==================== 3. 搜索性能 ====================

    @Test
    fun test06_search_like_100k() = kotlinx.coroutines.runBlocking {
        seedContacts(100_000)

        // 测试不同长度的搜索词
        val queries = listOf("张", "李", "王", "138", "wx_50000")

        for (query in queries) {
            val elapsed = measureTimeMillis {
                val results = contactDao.searchContacts(query).first()
                println("  搜索 '$query': 命中 ${results.size} 条")
            }
            results.add(TestResult("搜索LIKE '%$query%'", 100_000, elapsed))
            println("[PASS] 搜索 '$query' (100K): ${elapsed}ms")
        }
    }

    @Test
    fun test06b_search_10k_contacts() = runTest {
        seedContacts(10_000)

        val queries = listOf("张", "李", "138")
        for (query in queries) {
            val elapsed = measureTimeMillis {
                contactDao.searchContacts(query).first()
            }
            results.add(TestResult("搜索LIKE '%$query%'", 10_000, elapsed))
        }
    }

    // ==================== 4. 拼音排序性能 ====================

    @Test
    fun test07_pinyin_sort_100k() = kotlinx.coroutines.runBlocking {
        seedContacts(100_000)
        val contacts = contactDao.getAllContacts().first()

        val memBefore = getUsedMemoryMB()
        val elapsed = measureTimeMillis {
            val sorted = filterUseCase(contacts, "", 0)
            assertThat(sorted).hasSize(100_000)
        }
        val memAfter = getUsedMemoryMB()

        results.add(TestResult("拼音排序(100K)", 100_000, elapsed, memAfter - memBefore))
        println("[PASS] 拼音排序 100K: ${elapsed}ms")
    }

    @Test
    fun test07b_pinyin_sort_with_filter_100k() = kotlinx.coroutines.runBlocking {
        seedContacts(100_000)
        val contacts = contactDao.getAllContacts().first()

        val elapsed = measureTimeMillis {
            val filtered = filterUseCase(contacts, "张", 0)
            println("  过滤 '张': ${filtered.size} 条")
        }

        results.add(TestResult("拼音排序+过滤(100K)", 100_000, elapsed))
        println("[PASS] 拼音排序+过滤 '张' (100K): ${elapsed}ms")
    }

    // ==================== 5. 重复检测性能 ====================

    @Test
    fun test08_duplicate_detection_100k() = kotlinx.coroutines.runBlocking {
        seedContacts(100_000)
        // 同时插入字段值用于重复检测
        seedFieldValues(100_000)

        // 测试 findPotentialDuplicates
        val queries = listOf("13850000", "wx_50000", "test@example.com")

        for (query in queries) {
            val elapsed = measureTimeMillis {
                val duplicates = scanResultDao.findPotentialDuplicates(query)
                println("  重复检测 '$query': 找到 ${duplicates.size} 条")
            }
            results.add(TestResult("重复检测'$query'", 100_000, elapsed))
            println("[PASS] 重复检测 '$query' (100K): ${elapsed}ms")
        }
    }

    // ==================== 6. 名片夹查询性能 ====================

    @Test
    fun test09_collection_query_with_many_collections() = runTest {
        // 创建 50 个名片夹
        val collectionIds = mutableListOf<Long>()
        for (i in 1..50) {
            val id = cardCollectionDao.insertCollection(
                CardCollection(name = "测试名片夹$i", description = "描述$i")
            )
            collectionIds.add(id)
        }

        // 插入 10000 联系人并分配到名片夹
        seedContacts(10_000)
        val batchSize = 500
        for (batchStart in 0 until 10_000 step batchSize) {
            val batchEnd = minOf(batchStart + batchSize, 10_000)
            dbRule.db.runInTransaction {
                runBlocking {
                    for (i in batchStart until batchEnd) {
                        val contactId = i.toLong() + 1
                        val numCollections = Random.nextInt(1, 4)
                        val shuffledCollections = collectionIds.shuffled().take(numCollections)
                        for (collId in shuffledCollections) {
                            scanResultDao.insertScanResult(
                                ScanResult(
                                    contactId = contactId,
                                    collectionId = collId,
                                    sourceType = "scan"
                                )
                            )
                        }
                    }
                }
            }
        }

        // 测试名片夹查询
        for (collId in collectionIds.take(5)) {
            val elapsed = measureTimeMillis {
                val contacts = contactDao.getContactsByCollection(collId).first()
                println("  名片夹 $collId: ${contacts.size} 联系人")
            }
            results.add(TestResult("名片夹查询", 10_000, elapsed))
        }

        // 测试 getCollectionsWithCount
        val countElapsed = measureTimeMillis {
            val collectionsWithCount = cardCollectionDao.getCollectionsWithCount().first()
            assertThat(collectionsWithCount.size).isGreaterThan(49)
            println("  名片夹总数: ${collectionsWithCount.size}")
        }
        results.add(TestResult("名片夹计数查询", 50, countElapsed))
    }

    // ==================== 7. 内存占用测试 ====================

    @Test
    fun test10_memory_usage_100k() = kotlinx.coroutines.runBlocking {
        val memBaseline = getUsedMemoryMB()
        println("内存基线: ${memBaseline}MB")

        seedContacts(100_000)
        val memAfterInsert = getUsedMemoryMB()
        println("插入后内存: ${memAfterInsert}MB (增量: ${memAfterInsert - memBaseline}MB)")

        // 加载全表到内存
        val contacts = contactDao.getAllContacts().first()
        val memAfterLoad = getUsedMemoryMB()
        println("加载后内存: ${memAfterLoad}MB (增量: ${memAfterLoad - memBaseline}MB)")

        // 过滤排序
        val sorted = filterUseCase(contacts, "张", 0)
        val memAfterSort = getUsedMemoryMB()
        println("排序后内存: ${memAfterSort}MB (增量: ${memAfterSort - memBaseline}MB)")

        results.add(TestResult("内存:DB插入", 100_000, 0, memAfterInsert - memBaseline))
        results.add(TestResult("内存:全表加载", 100_000, 0, memAfterLoad - memBaseline))
        results.add(TestResult("内存:排序后", 100_000, 0, memAfterSort - memBaseline))

        assertThat(contacts).hasSize(100_000)
        println("[PASS] 内存测试完成")
    }

    // ==================== 8. 级联删除性能 ====================

    @Test
    fun test11_cascade_delete_100k() = kotlinx.coroutines.runBlocking {
        seedContacts(100_000)
        seedFieldValues(100_000)

        val beforeCount = contactDao.getAllContacts().first().size
        assertThat(beforeCount).isGreaterThan(99_999)

        // 逐个删除前 1000 个联系人
        val elapsed = measureTimeMillis {
            for (i in 1..1000) {
                val contact = contactDao.getContactById(i.toLong())
                if (contact != null) {
                    contactDao.deleteContact(contact)
                }
            }
        }

        val afterCount = contactDao.getAllContacts().first().size
        results.add(TestResult("级联删除(1000条)", 1000, elapsed,
            notes = "删前=$beforeCount, 删后=$afterCount"))
        println("[PASS] 级联删除 1000 条: ${elapsed}ms")
    }

    // ==================== 9. 并发读写测试 ====================

    @Test
    fun test12_concurrent_read_write() = runTest {
        seedContacts(10_000)

        // 模拟并发: 一边插入一边查询
        val insertCount = 1000
        val queryCount = 50

        val elapsed = measureTimeMillis {
            repeat(queryCount) { i ->
                contactDao.getAllContacts().first()
                if (i % 10 == 0) {
                    contactDao.insertContact(Contact(name = "并发测试$i"))
                }
            }
        }

        val finalCount = contactDao.getAllContacts().first().size
        results.add(TestResult("并发读写", finalCount, elapsed,
            notes = "读${queryCount}次, 写${queryCount / 10}次"))
        println("[PASS] 并发读写: ${elapsed}ms, 最终 $finalCount 条")
    }

    // ==================== 10. PinyinUtils 性能 ====================

    @Test
    fun test13_pinyin_conversion_100k() = runTest {
        val names = (0 until 100_000).map { generateName(it) }

        val elapsed = measureTimeMillis {
            for (name in names) {
                PinyinUtils.getContactPinyinInitial(name)
            }
        }

        results.add(TestResult("拼音转换(100K)", 100_000, elapsed,
            notes = "${100_000 * 1000 / maxOf(elapsed, 1)} name/s"))
        println("[PASS] 拼音转换 100K: ${elapsed}ms")
    }

    // ==================== 辅助方法 ====================

    private suspend fun seedContacts(count: Int) {
        val fields = contactFieldDao.getAllFieldsOnce()
        val batchSize = 1000
        for (batchStart in 0 until count step batchSize) {
            val batchEnd = minOf(batchStart + batchSize, count)
            dbRule.db.runInTransaction {
                runBlocking {
                    for (i in batchStart until batchEnd) {
                        val contact = Contact(
                            name = generateName(i),
                            note = if (i % 10 == 0) "备注$i" else null
                        )
                        val contactId = contactDao.insertContact(contact)
                        if (fields.isNotEmpty() && i % 3 != 0) {
                            val fieldValues = generateFieldValues(contactId, i, fields)
                            contactFieldValueDao.insertOrUpdateFieldValues(fieldValues)
                        }
                    }
                }
            }
        }
    }

    private suspend fun seedFieldValues(count: Int) {
        val fields = contactFieldDao.getAllFieldsOnce()
        if (fields.isEmpty()) return

        val phoneField = fields.find { it.fieldKey == "phone" }
        val emailField = fields.find { it.fieldKey == "email" }

        val batchSize = 1000
        for (batchStart in 0 until count step batchSize) {
            val batchEnd = minOf(batchStart + batchSize, count)
            val values = mutableListOf<ContactFieldValue>()
            for (i in batchStart until batchEnd) {
                val contactId = i.toLong() + 1
                if (phoneField != null) {
                    values.add(ContactFieldValue(
                        contactId = contactId,
                        fieldId = phoneField.id,
                        value = "138${String.format("%08d", i)}"
                    ))
                }
                if (emailField != null && i % 5 == 0) {
                    values.add(ContactFieldValue(
                        contactId = contactId,
                        fieldId = emailField.id,
                        value = "user$i@example.com"
                    ))
                }
            }
            if (values.isNotEmpty()) {
                dbRule.db.runInTransaction {
                    runBlocking {
                        contactFieldValueDao.insertOrUpdateFieldValues(values)
                    }
                }
            }
        }
    }

    private fun generateName(idx: Int): String {
        val surnames = arrayOf("赵","钱","孙","李","周","吴","郑","王","冯","陈",
            "褚","卫","蒋","沈","韩","杨","朱","秦","尤","许","何","吕","施","张",
            "孔","曹","严","华","金","魏","陶","姜","戚","谢","邹","喻","柏","水",
            "窦","章","云","苏","潘","葛","奚","范","彭","郎","鲁","韦","昌","马")
        val nameChars = "伟芳娜秀英敏静丽强磊洋勇艳杰娟涛明超华刚平".toCharArray()

        val surname = surnames[idx % surnames.size]
        val givenLen = if (Random.nextInt(10) < 3) 2 else 1
        val given = buildString {
            repeat(givenLen) { append(nameChars[Random.nextInt(nameChars.size)]) }
        }
        return if (idx < 500) "$surname$given" else "$surname$given$idx"
    }

    private fun generateFieldValues(contactId: Long, idx: Int, fields: List<ContactField>): List<ContactFieldValue> {
        val numValues = Random.nextInt(1, minOf(4, fields.size + 1))
        return fields.shuffled().take(numValues).map { field ->
            val value = when (field.fieldKey) {
                "phone" -> "138${String.format("%08d", idx)}"
                "email" -> "user$idx@example.com"
                "wechat" -> "wx_$idx"
                "qq" -> "${10000 + idx}"
                "github" -> "gh_user$idx"
                else -> "val_$idx"
            }
            ContactFieldValue(contactId = contactId, fieldId = field.id, value = value)
        }
    }

    private fun getUsedMemoryMB(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    private fun runBlocking(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }
}
