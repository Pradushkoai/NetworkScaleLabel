package com.pradushkoai.networkscalelabel.di

import com.pradushkoai.networkscalelabel.data.local.ConnectionSettingsStore
import com.pradushkoai.networkscalelabel.data.remote.ScaleTransportFactory
import com.pradushkoai.networkscalelabel.data.remote.UdpScaleTransportFactory
import com.pradushkoai.networkscalelabel.data.repository.ScalesRepositoryImpl
import com.pradushkoai.networkscalelabel.domain.repository.ConnectionSettingsRepository
import com.pradushkoai.networkscalelabel.domain.repository.ScalesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DI-модули обмена с весами.
 */
@Module
@InstallIn(SingletonComponent::class)
object ScalesProvidesModule {

    @Provides
    @Singleton
    fun provideTransportFactory(): ScaleTransportFactory =
        UdpScaleTransportFactory()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ScalesBindsModule {

    @Binds
    @Singleton
    abstract fun bindScalesRepository(impl: ScalesRepositoryImpl): ScalesRepository

    @Binds
    @Singleton
    abstract fun bindConnectionSettings(impl: ConnectionSettingsStore): ConnectionSettingsRepository
}
