package top.mcxiafeng.badger.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import top.mcxiafeng.badger.ai.AiTagGenerator
import javax.inject.Singleton

/**
 * AI 相关依赖注入。
 *
 * AiTagGenerator 复用项目内 [top.mcxiafeng.badger.ocr.AiOcrService] 的 OpenAI 兼容
 * chat completions 通道，不新建 OkHttp / Retrofit。
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideAiTagGenerator(@ApplicationContext context: Context): AiTagGenerator {
        return AiTagGenerator(context)
    }
}
