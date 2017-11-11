package net.corda.demos.crowdFunding.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.demos.crowdFunding.contracts.CampaignContract
import net.corda.demos.crowdFunding.contracts.PledgeContract
import net.corda.demos.crowdFunding.structures.Campaign
import net.corda.demos.crowdFunding.structures.Pledge
import java.util.*

object MakePledge {

    @StartableByRPC
    @InitiatingFlow
    class Initiator(
            private val amount: Amount<Currency>,
            private val campaignReference: UniqueIdentifier
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            // Pick a notary. Don't care which one.
            val notary: Party = serviceHub.networkMapCache.notaryIdentities.first()

            // Get the Campaign state corresponding to the provided ID from our vault.
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(campaignReference))
            val campaignInputStateAndRef = serviceHub.vaultService.queryBy<Campaign>(queryCriteria).states.single()
            val campaignState = campaignInputStateAndRef.state.data

            // Create a new pledge for the requested amount.
            val pledgeOutputState = Pledge(amount, ourIdentity, campaignState.manager, campaignReference)

            // Assemble the other transaction components. We need a Create Pledge command and a Campaign Pledge
            // command, as well as the Campaign input + output and the new Pledge output state.
            // Commands:
            val signers = listOf(ourIdentity.owningKey, campaignState.manager.owningKey)
            val startCampaignCommand = Command(CampaignContract.Pledge(), signers)
            val createPledgeCommand = Command(PledgeContract.Create(), signers)

            // Output states:
            val pledgeOutputStateAndContract = StateAndContract(pledgeOutputState, PledgeContract.CONTRACT_REFERENCE)
            val newRaisedSoFar = campaignState.raisedSoFar + amount
            val campaignOutputState = campaignState.copy(raisedSoFar = newRaisedSoFar)
            val campaignOutputStateAndContract = StateAndContract(campaignOutputState, CampaignContract.CONTRACT_REFERENCE)

            // Build the transaction.
            val utx = TransactionBuilder(notary = notary).withItems(
                    pledgeOutputStateAndContract,
                    campaignOutputStateAndContract,
                    campaignInputStateAndRef,
                    startCampaignCommand,
                    createPledgeCommand
            )

            // Sign, finalise and record the transaction.
            val ptx = serviceHub.signInitialTransaction(utx)
            val session = initiateFlow(campaignState.manager)
            val stx = subFlow(CollectSignaturesFlow(ptx, setOf(session)))
            return subFlow(FinalityFlow(stx))
        }

    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val flow = object : SignTransactionFlow(otherSession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    // TODO: Add some checks here.
                }
            }

            // Wait for the transaction to be committed.
            val stx = subFlow(flow)
            val ftx = waitForLedgerCommit(stx.id)

            // We then broadcast from the manager so we don't compromise the confidentiality of the pledging identities.
            subFlow(BroadcastTransaction(ftx))
        }

    }

}

