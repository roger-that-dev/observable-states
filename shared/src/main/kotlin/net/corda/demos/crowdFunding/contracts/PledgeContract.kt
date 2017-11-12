package net.corda.demos.crowdFunding.contracts

import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.transactions.LedgerTransaction

class PledgeContract : Contract {

    companion object {
        @JvmStatic
        val CONTRACT_REF = "net.corda.demos.crowdFunding.contracts.PledgeContract"
    }

    class Create : TypeOnlyCommandData()
    class Cancel : TypeOnlyCommandData()

    override fun verify(tx: LedgerTransaction) {
        // TODO
    }
}