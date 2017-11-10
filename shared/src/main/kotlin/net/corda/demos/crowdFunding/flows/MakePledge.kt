package net.corda.demos.crowdFunding.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.demos.crowdFunding.contracts.CampaignContract
import net.corda.demos.crowdFunding.contracts.PledgeContract
import net.corda.demos.crowdFunding.structures.Campaign
import net.corda.demos.crowdFunding.structures.CampaignReference
import net.corda.demos.crowdFunding.structures.Pledge
import java.util.*

object MakePledge {

    @StartableByRPC
    @InitiatingFlow
    class Initiator(val amount: Amount<Currency>, val campaignReference: CampaignReference) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            val pledge = Pledge(amount, ourIdentity, campaignReference)

            val signers = listOf(ourIdentity.owningKey, campaignReference.manager.owningKey)
            val startCampaignCommand = Command(CampaignContract.Pledge(), signers)
            val createPledgeCommand = Command(PledgeContract.Create(), signers)
            val newPledge = StateAndContract(pledge, PledgeContract.CONTRACT_REFERENCE)

            val utx = TransactionBuilder(notary = notary).withItems(
                    newPledge,
                    startCampaignCommand,
                    createPledgeCommand
            )

            val session = initiateFlow(campaignReference.manager)
            val ptx = session.sendAndReceive<SignedTransaction>(utx).unwrap { it }

            val mySignature = serviceHub.createSignature(ptx)
            val stx = ptx + mySignature

            subFlow(FinalityFlow(stx))
        }

    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val utx = otherSession.receive<TransactionBuilder>().unwrap { it }

            val newPledge = utx.outputStates().map { it.data }.filterIsInstance<Pledge>().single()
            val newPledgeId = newPledge.campaignReference.campaignId

            // Get the latest Campaign state for this CampaignReference from the vault.
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(newPledgeId))
            val campaignInputStateAndRef = serviceHub.vaultService.queryBy<Campaign>(queryCriteria).states.single()

            // Update the Campaign state with the new amount pledged and the pledger details.
            val campaignInputState = campaignInputStateAndRef.state.data
            val newRaisedSoFar = campaignInputState.raisedSoFar + newPledge.amount
            val campaignOutputState = campaignInputState.copy(raisedSoFar = newRaisedSoFar)

            utx.addInputState(campaignInputStateAndRef)
            utx.addOutputState(campaignOutputState, CampaignContract.CONTRACT_REFERENCE)

            val ptx = serviceHub.signInitialTransaction(utx)

            otherSession.send(ptx)
        }

    }

}

