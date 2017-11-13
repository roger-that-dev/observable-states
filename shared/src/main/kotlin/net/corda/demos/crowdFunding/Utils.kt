package net.corda.demos.crowdFunding

import net.corda.core.contracts.StateAndRef
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.demos.crowdFunding.structures.Campaign
import net.corda.demos.crowdFunding.structures.Pledge

/** Pick out all the pledges for this campaign. We need to cancel them whatever happens. */
fun pledgerStateAndRefs(services: ServiceHub, campaign: Campaign): List<StateAndRef<Pledge>> {
    val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = campaign.pledges.toList())
    return services.vaultService.queryBy<Pledge>(queryCriteria).states
}