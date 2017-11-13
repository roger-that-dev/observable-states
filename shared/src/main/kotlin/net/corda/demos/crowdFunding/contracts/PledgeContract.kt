package net.corda.demos.crowdFunding.contracts

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.demos.crowdFunding.keysFromParticipants
import net.corda.demos.crowdFunding.structures.Pledge
import java.security.PublicKey

class PledgeContract : Contract {

    companion object {
        @JvmStatic
        val CONTRACT_REF = "net.corda.demos.crowdFunding.contracts.PledgeContract"
    }

    interface Commands : CommandData
    class Create : TypeOnlyCommandData(), Commands
    class Cancel : TypeOnlyCommandData(), Commands

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val setOfSigners = command.signers.toSet()

        when (command.value) {
            is Create -> verifyCreate(tx, setOfSigners)
            is Cancel -> verifyCancel(tx, setOfSigners)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        // Group pledges by campaign id.
        val pledgeStates = tx.groupStates(Pledge::class.java, { it.campaignReference })
        "You can only create one pledge at a time." using (pledgeStates.size == 1)

        // Assert we have the right amount and type of states.
        val pledgeStatesGroup = pledgeStates.single()
        "No inputs should be consumed when creating a pledge." using (pledgeStatesGroup.inputs.isEmpty())
        "Only one campaign state should be created when starting a campaign." using (pledgeStatesGroup.outputs.size == 1)
        val pledge = pledgeStatesGroup.outputs.single()

        // Assert stuff over the state.
        "You cannot pledge a zero amount." using (pledge.amount > Amount(0, pledge.amount.token))

        // Assert correct signers.
        "The campaign must be signed by the manager only." using (signers == keysFromParticipants(pledge))
    }

    private fun verifyCancel(tx: LedgerTransaction, signers: Set<PublicKey>) = Unit // TODO
}