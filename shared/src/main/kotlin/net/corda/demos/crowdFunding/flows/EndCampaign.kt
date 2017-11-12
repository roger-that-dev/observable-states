package net.corda.demos.crowdFunding.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateRef
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.demos.crowdFunding.contracts.CampaignContract
import net.corda.demos.crowdFunding.contracts.PledgeContract
import net.corda.demos.crowdFunding.structures.Campaign
import net.corda.demos.crowdFunding.structures.Pledge

@SchedulableFlow
class EndCampaign(private val stateRef: StateRef) : FlowLogic<SignedTransaction>() {

    private fun handleFailure(campaign: Campaign): Pair<TransactionBuilder, Set<AbstractParty>> {
        // Pick a notary. Don't care which one.
        val notary: Party = serviceHub.networkMapCache.notaryIdentities.first()

        // Pick out all the pledges for this campaign. We need to cancel them.
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = campaign.pledges.toList())
        val pledgerStateAndRefs = serviceHub.vaultService.queryBy<Pledge>(queryCriteria).states

        val pledgers = pledgerStateAndRefs.map { it.state.data.pledger }.toSet()

        // Pledges can be unilaterally cancelled by the campaign manager.
        val endCampaignCommand = Command(CampaignContract.End(), campaign.manager.owningKey)
        val cancelPledgeCommand = Command(PledgeContract.Cancel(), campaign.manager.owningKey)

        val campaignStateAndRef = serviceHub.toStateAndRef<Campaign>(stateRef)

        val utx = TransactionBuilder(notary = notary)

        utx.withItems(
                endCampaignCommand,
                cancelPledgeCommand,
                campaignStateAndRef
        )

        pledgerStateAndRefs.forEach { utx.addInputState(it) }

        return Pair(utx, pledgers)
    }

    private fun handleSuccess(campaign: Campaign): Pair<TransactionBuilder, Set<AbstractParty>> {
        return Pair(TransactionBuilder(), setOf())
    }

    @Suspendable
    override fun call(): SignedTransaction {
        val campaign = serviceHub.loadState(stateRef).data as Campaign

        if (campaign.manager != ourIdentity) {
            throw FlowException("Only the campaign manager can run this flow.")
        }

        val (utx, pledgers) = when {
            campaign.raisedSoFar < campaign.target -> handleFailure(campaign)
            else -> handleSuccess(campaign)
        }

        val ptx = serviceHub.signInitialTransaction(utx)

        val sessions = pledgers.map { initiateFlow(it as Party) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))
        val ftx = subFlow(FinalityFlow(stx))
        subFlow(BroadcastTransaction(ftx))

        return ftx
    }

}