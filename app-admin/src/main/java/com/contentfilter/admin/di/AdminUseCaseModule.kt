package com.contentfilter.admin.di

import com.contentfilter.core.domain.repository.AccessRequestRepository
import com.contentfilter.core.domain.repository.DailyLimitRepository
import com.contentfilter.core.domain.repository.DeviceRepository
import com.contentfilter.core.domain.repository.ExtraTimeGrantRepository
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.core.domain.usecase.admin.ApproveAccessRequestUseCase
import com.contentfilter.core.domain.usecase.admin.DeletePolicyRuleUseCase
import com.contentfilter.core.domain.usecase.admin.GrantExtraTimeUseCase
import com.contentfilter.core.domain.usecase.admin.ObserveDailyLimitsUseCase
import com.contentfilter.core.domain.usecase.admin.ObserveDevicesUseCase
import com.contentfilter.core.domain.usecase.admin.ObservePolicyRulesUseCase
import com.contentfilter.core.domain.usecase.admin.ObserveRequestsUseCase
import com.contentfilter.core.domain.usecase.admin.SaveDailyLimitUseCase
import com.contentfilter.core.domain.usecase.admin.SavePolicyRuleUseCase
import com.contentfilter.core.domain.usecase.admin.SetRequestStatusUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AdminUseCaseModule {
    @Provides
    fun provideObserveDevicesUseCase(repository: DeviceRepository): ObserveDevicesUseCase =
        ObserveDevicesUseCase(repository)

    @Provides
    fun provideObserveRequestsUseCase(repository: AccessRequestRepository): ObserveRequestsUseCase =
        ObserveRequestsUseCase(repository)

    @Provides
    fun provideSetRequestStatusUseCase(repository: AccessRequestRepository): SetRequestStatusUseCase =
        SetRequestStatusUseCase(repository)

    @Provides
    fun provideApproveAccessRequestUseCase(
        requestRepository: AccessRequestRepository,
        policyRepository: PolicyRepository,
    ): ApproveAccessRequestUseCase =
        ApproveAccessRequestUseCase(requestRepository, policyRepository)

    @Provides
    fun provideGrantExtraTimeUseCase(
        requestRepository: AccessRequestRepository,
        grantRepository: ExtraTimeGrantRepository,
    ): GrantExtraTimeUseCase =
        GrantExtraTimeUseCase(requestRepository, grantRepository)

    @Provides
    fun provideObservePolicyRulesUseCase(repository: PolicyRepository): ObservePolicyRulesUseCase =
        ObservePolicyRulesUseCase(repository)

    @Provides
    fun provideSavePolicyRuleUseCase(repository: PolicyRepository): SavePolicyRuleUseCase =
        SavePolicyRuleUseCase(repository)

    @Provides
    fun provideDeletePolicyRuleUseCase(repository: PolicyRepository): DeletePolicyRuleUseCase =
        DeletePolicyRuleUseCase(repository)

    @Provides
    fun provideObserveDailyLimitsUseCase(repository: DailyLimitRepository): ObserveDailyLimitsUseCase =
        ObserveDailyLimitsUseCase(repository)

    @Provides
    fun provideSaveDailyLimitUseCase(repository: DailyLimitRepository): SaveDailyLimitUseCase =
        SaveDailyLimitUseCase(repository)
}
