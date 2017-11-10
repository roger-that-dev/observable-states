package net.corda.demos.crowdFunding.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.demos.crowdFunding.contracts.CampaignContract
import net.corda.demos.crowdFunding.structures.Campaign
import java.time.Instant
import java.util.*

/**
 * A flow that unilaterally starts a new campaign. All this does is create a new [Campaign] in the vault of the
 * the node that runs this flow (the manager of the campaign).
 *
 * If other nodes want to pledge to this campaign then they must obtain the linearId for this campaign state as well as
 * the node that is running the campaign. This would be done off-ledger via some sort of aggregator service.
 */
class StartCampaign(
        private val target: Amount<Currency>,
        private val name: String,
        private val deadline: Instant
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        val newCampaign = Campaign(
                name = name,
                target = target,
                manager = ourIdentity,
                deadline = deadline
        )

        val startCommand = Command(CampaignContract.Start(), listOf(ourIdentity.owningKey))
        val outputState = StateAndContract(newCampaign, CampaignContract.CONTRACT_REFERENCE)

        val utx = TransactionBuilder(notary = notary).withItems(outputState, startCommand)
        val stx = serviceHub.signInitialTransaction(utx)
        return subFlow(FinalityFlow(stx))
    }

}