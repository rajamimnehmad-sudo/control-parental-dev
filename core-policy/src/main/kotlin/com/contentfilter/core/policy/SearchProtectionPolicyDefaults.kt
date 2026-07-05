package com.contentfilter.core.policy

import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.SearchEngineCatalog
import java.util.UUID

object SearchProtectionPolicyDefaults {
    const val SafeDefaultPolicyId = "vpn-safe-default"

    private const val SafeDefaultPriority = 3_000

    fun safeDefaultSnapshot(): PolicySnapshot =
        PolicySnapshot(
            id = SafeDefaultPolicyId,
            version = 0L,
            rules =
                (SearchEngineCatalog.searchEngineDomains + SearchEngineCatalog.secureDnsDomains)
                    .distinct()
                    .map { domain ->
                        PolicyRule(
                            id = "safe-default-${UUID.nameUUIDFromBytes(domain.toByteArray())}",
                            level = PolicyLevel.Device,
                            scope = RuleScope.Domain,
                            target = domain,
                            action = RuleAction.Block,
                            priority = SafeDefaultPriority,
                            enabled = true,
                        )
                    },
        )
}
