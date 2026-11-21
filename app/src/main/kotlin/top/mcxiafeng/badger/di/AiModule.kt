package top.mcxiafeng.badger.di

import android.content.Context
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import top.mcxiafeng.badger.ai.AiTagGenerator
import top.mcxiafeng.badger.data.account.AccountScopedContext

@Module
@InstallIn(ViewModelComponent::class)
object AiModule {
    @Provides
    @ViewModelScoped
    fun provideAiTagGenerator(@AccountScopedContext context: Context): AiTagGenerator {
        Log.d("AiModuleTester", "Creating AI generator with fixed business configuration")
        return AiTagGenerator(context)
    }
}
