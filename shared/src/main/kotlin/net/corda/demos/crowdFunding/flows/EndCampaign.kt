package net.corda.demos.crowdFunding.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.SchedulableFlow

@SchedulableFlow
class EndCampaign(private val stateRef: StateRef) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {

    }

}