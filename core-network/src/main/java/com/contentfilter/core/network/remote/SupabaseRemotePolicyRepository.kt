package com.contentfilter.core.network.remote

import com.contentfilter.core.network.dto.RemotePolicyDto
import com.contentfilter.core.network.dto.RemotePolicyRuleDto
import org.json.JSONObject
import javax.inject.Inject

class SupabaseRemotePolicyRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
    ) : RemotePolicyRepository {
        override suspend fun pullPolicies(updatedAfterIso: String?): RemoteResult<List<RemotePolicyDto>> =
            client.selectUpdatedSince(SupabaseTable.Policies, updatedAfterIso).mapArray(RemotePolicyDto::fromJson)

        override suspend fun pullPolicyRules(updatedAfterIso: String?): RemoteResult<List<RemotePolicyRuleDto>> =
            client.selectUpdatedSince(
                SupabaseTable.PolicyRules,
                updatedAfterIso,
            ).mapArray(RemotePolicyRuleDto::fromJson)

        override suspend fun pullPoliciesForDevice(deviceId: String): RemoteResult<List<RemotePolicyDto>> =
            client.selectByEquals(
                table = SupabaseTable.Policies,
                filters = mapOf("device_id" to deviceId),
                orderColumn = "version",
                ascending = false,
            ).mapArray(RemotePolicyDto::fromJson)

        override suspend fun pullPolicyById(policyId: String): RemoteResult<List<RemotePolicyDto>> =
            client.selectByEquals(
                table = SupabaseTable.Policies,
                filters = mapOf("id" to policyId),
            ).mapArray(RemotePolicyDto::fromJson)

        override suspend fun pullPolicyRulesForPolicy(policyId: String): RemoteResult<List<RemotePolicyRuleDto>> =
            client.selectByEquals(
                table = SupabaseTable.PolicyRules,
                filters = mapOf("policy_id" to policyId),
            ).mapArray(RemotePolicyRuleDto::fromJson)

        override suspend fun upsertPolicy(policy: RemotePolicyDto): RemoteResult<Unit> =
            client.upsert(SupabaseTable.Policies, policy.toJson())

        override suspend fun upsertPolicyRule(rule: RemotePolicyRuleDto): RemoteResult<Unit> =
            client.upsert(SupabaseTable.PolicyRules, rule.toJson())

        override suspend fun notifyPolicyChanged(
            requestId: String,
            deviceId: String,
            policyId: String,
            revision: Long,
        ): RemoteResult<Unit> =
            client.broadcast(
                topic = "policy:$deviceId",
                event = "policy_revision",
                payload =
                    JSONObject()
                        .put("request_id", requestId)
                        .put("device_id", deviceId)
                        .put("policy_id", policyId)
                        .put("revision", revision),
            )
    }
