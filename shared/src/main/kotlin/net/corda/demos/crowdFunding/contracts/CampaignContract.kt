package net.corda.demos.crowdFunding.contracts

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.demos.crowdFunding.keysFromParticipants
import net.corda.demos.crowdFunding.structures.Campaign
import net.corda.demos.crowdFunding.structures.Pledge
import java.security.PublicKey
import java.time.Instant

class CampaignContract : Contract {

    companion object {
        @JvmStatic
        val CONTRACT_REF = "net.corda.demos.crowdFunding.contracts.CampaignContract"
    }

    interface Commands : CommandData
    class Start : TypeOnlyCommandData(), Commands
    class End : TypeOnlyCommandData(), Commands
    class AcceptPledge : TypeOnlyCommandData(), Commands

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val setOfSigners = command.signers.toSet()

        when (command.value) {
            is Start -> verifyStart(tx, setOfSigners)
            is End -> verifyEnd(tx, setOfSigners)
            is AcceptPledge -> verifyPledge(tx, setOfSigners)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyStart(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        // Assert we have the right amount and type of states.
        "No inputs should be consumed when starting a campaign." using (tx.inputStates.isEmpty())
        "Only one campaign state should be created when starting a campaign." using (tx.outputStates.size == 1)
        val campaign = tx.outputStates.single() as Campaign

        // Assert stuff over the state.
        "A newly issued campaign must have a positive target." using (campaign.target > Amount(0, campaign.target.token))
        "A newly issued campaign must start with no pledges." using (campaign.raisedSoFar == Amount(0, campaign.target.token))
        // TODO assert deadline is in the future.
        // TODO assert there is a campaign name.

        // Assert correct signers.
        "The campaign must be signed by the manager only." using (signers == keysFromParticipants(campaign))
    }

    private fun verifyPledge(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        // Assert we have the right amount and type of states.
        val campaignInput = tx.inputsOfType<Campaign>().single()
        val campaignOutput = tx.outputsOfType<Campaign>().single()
        val pledgeOutput = tx.outputsOfType<Pledge>().single()

        // Assert stuff about the pledge in relation to the campaign state.
        val changeInAmountRaised = campaignOutput.raisedSoFar - campaignInput.raisedSoFar
        "The pledge must be for this campaign." using (pledgeOutput.campaignReference == campaignOutput.linearId)
        "The campaign must be updated by the amount pledged." using (pledgeOutput.amount == changeInAmountRaised)

        // Assert stuff cannot change in the campaign state.
        "The campaign name may not change when accepting a pledge." using (campaignInput.name == campaignOutput.name)
        "The campaign deadline may not change when accepting a pledge." using (campaignInput.deadline == campaignOutput.deadline)
        "The campaign manager may not change when accepting a pledge." using (campaignInput.manager == campaignOutput.manager)
        "The campaign reference (linearId) may not change when accepting a pledge." using (campaignInput.linearId == campaignOutput.linearId)
        "The campaign target may not change when accepting a pledge." using (campaignInput.target == campaignOutput.target)

        // Assert other stuff.
        "No pledges can be accepted after the deadline." using (Instant.now() < campaignOutput.deadline)

        // Assert correct signers.
        "The campaign must be signed by the manager only." using (signers == keysFromParticipants(campaignOutput))
    }

    private fun verifyEnd(tx: LedgerTransaction, signers: Set<PublicKey>) = Unit // TODO
}