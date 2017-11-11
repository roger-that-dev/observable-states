package net.corda.demos.crowdFunding.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.SchedulableFlow
import net.corda.demos.crowdFunding.structures.Campaign

@SchedulableFlow
class EndCampaign(private val stateRef: StateRef) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val campaign = serviceHub.loadState(stateRef).data as Campaign

        if (campaign.manager != ourIdentity) {
            return
        }

        logger.info("TODO: Handling the end of the campaign.")

        // If manager than figure out if we have collected enough pledges or not.

        // If we have then we collect all the pledges.

        // If we don't then we cancel the pledges and the campaign.
    }

}