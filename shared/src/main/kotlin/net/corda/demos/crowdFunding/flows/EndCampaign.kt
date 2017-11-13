package net.corda.demos.crowdFunding.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.demos.crowdFunding.contracts.CampaignContract
import net.corda.demos.crowdFunding.contracts.PledgeContract
import net.corda.demos.crowdFunding.pledgerStateAndRefs
import net.corda.demos.crowdFunding.structures.Campaign
import net.corda.demos.crowdFunding.structures.CampaignResult
import net.corda.demos.crowdFunding.structures.CashStatesPayload
import net.corda.finance.contracts.asset.CASH_PROGRAM_ID
import net.corda.finance.contracts.asset.Cash

object EndCampaign {

    @SchedulableFlow
    @InitiatingFlow
    class Initiator(private val stateRef: StateRef) : FlowLogic<SignedTransaction>() {

        @Suspendable
        fun requestPledgedCash(sessions: List<FlowSession>): CashStatesPayload {
            // Send a request to each pledger and get the dependency transactions as well.
            val cashStates = sessions.map { session ->
                session.send(CampaignResult.Success(stateRef))
                subFlow(ReceiveStateAndRefFlow<ContractState>(session))
                session.receive<CashStatesPayload>().unwrap { it }
            }

            // Return all of the collected states and keys.
            return CashStatesPayload(
                    cashStates.flatMap { it.inputs },
                    cashStates.flatMap { it.outputs },
                    cashStates.flatMap { it.signingKeys }
            )
        }

        /** Common stuff that happens whether we meet the target of not. */
        private fun cancelPledges(campaign: Campaign): TransactionBuilder {
            // Pick a notary. Don't care which one.
            val notary: Party = serviceHub.networkMapCache.notaryIdentities.first()
            val utx = TransactionBuilder(notary = notary)

            // Create inputs.
            val pledgerStateAndRefs = pledgerStateAndRefs(serviceHub, campaign)
            val campaignInputStateAndRef = serviceHub.toStateAndRef<Campaign>(stateRef)

            // Create commands.
            val endCampaignCommand = Command(CampaignContract.End(), campaign.manager.owningKey)
            val cancelPledgeCommand = Command(PledgeContract.Cancel(), campaign.manager.owningKey)

            // Add the above
            pledgerStateAndRefs.forEach { utx.addInputState(it) }
            utx.addInputState(campaignInputStateAndRef)
            utx.addCommand(endCampaignCommand)
            utx.addCommand(cancelPledgeCommand)

            return utx
        }

        @Suspendable
        fun handleSuccess(campaign: Campaign, sessions: List<FlowSession>): TransactionBuilder {
            // Do the stuff we must do anyway.
            val utx = cancelPledges(campaign)

            // Gather the cash states from the pledgers.
            val cashStates = requestPledgedCash(sessions)

            // Add the cash inputs, outputs and command.
            cashStates.inputs.forEach { utx.addInputState(it) }
            cashStates.outputs.forEach { utx.addOutputState(it, CASH_PROGRAM_ID) }
            utx.addCommand(Cash.Commands.Move(), cashStates.signingKeys)

            return utx
        }

        @Suspendable
        override fun call(): SignedTransaction {
            // Get the actual state from the ref.
            val campaign = serviceHub.loadState(stateRef).data as Campaign

            // Get the pledges for this campaign. Remember, everyone has a copy of them.
            val pledgerStateAndRefs = pledgerStateAndRefs(serviceHub, campaign)

            // As all nodes have the campaign state, all will try to start this flow. Abort for all but the manger.
            if (campaign.manager != ourIdentity) {
                throw FlowException("Only the campaign manager can run this flow.")
            }

            // Create flow sessions for all pledgers.
            val sessions = pledgerStateAndRefs.map { (state) ->
                val pledger = serviceHub.identityService.requireWellKnownPartyFromAnonymous(state.data.pledger)
                initiateFlow(pledger)
            }

            // Do different things depending on whether we've raised enough, or not.
            val utx = when {
                campaign.raisedSoFar < campaign.target -> {
                    sessions.forEach { session -> session.send(CampaignResult.Failure()) }
                    cancelPledges(campaign)
                }
                else -> handleSuccess(campaign, sessions)
            }

            // Sign, finalise and distribute the transaction.
            val ptx = serviceHub.signInitialTransaction(utx)
            val stx = subFlow(CollectSignaturesFlow(ptx, sessions.map { it }))
            val ftx = subFlow(FinalityFlow(stx))
            subFlow(BroadcastTransaction(ftx))

            return ftx
        }

    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        fun handleSuccess(campaignRef: StateRef) {
            // Get the pledger states. One of them will be ours.
            val campaign = serviceHub.loadState(campaignRef).data as Campaign
            val pledgerStates = pledgerStateAndRefs(serviceHub, campaign).map { it.state.data }

            // Find our pledge. We have to do this as we have ALL the pledges for this campaign in our vault.
            val amount = pledgerStates.single { pledge ->
                serviceHub.identityService.requireWellKnownPartyFromAnonymous(pledge.pledger) == ourIdentity
            }.amount

            // Using generate spend is the best way to get cash states to spend.
            val (utx, _) = Cash.generateSpend(serviceHub, TransactionBuilder(), amount, otherSession.counterparty)

            // The Cash contract design won't allow more than one move command per transaction. As, we are collecting
            // cash from potentially multiple parties, we have pull out the items from the transaction builder so we
            // can add them back to the OTHER transaction builder but with only one move command.
            val inputStateAndRefs = utx.inputStates().map { serviceHub.toStateAndRef<Cash.State>(it) }
            val outputStates = utx.outputStates().map { it.data as Cash.State }
            val signingKeys = utx.commands().flatMap { it.signers }

            // We need to send the cash state dependency transactions so the manager can verify the tx proposal.
            subFlow(SendStateAndRefFlow(otherSession, inputStateAndRefs))

            // Send the payload back to the campaign manager.
            val pledgedCashStates = CashStatesPayload(inputStateAndRefs, outputStates, signingKeys)
            otherSession.send(pledgedCashStates)
        }

        @Suspendable
        override fun call() {
            val campaignResult = otherSession.receive<CampaignResult>().unwrap { it }

            when (campaignResult) {
                is CampaignResult.Success -> handleSuccess(campaignResult.campaignRef)
                is CampaignResult.Failure -> return
            }

            val flow = object : SignTransactionFlow(otherSession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    // TODO
                }
            }

            subFlow(flow)
        }

    }

}