package net.corda.demos.crowdFunding.contracts

import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.transactions.LedgerTransaction

class CampaignContract : Contract {

    companion object {
        @JvmStatic
        val CONTRACT_REFERENCE = "net.corda.demos.crowdFunding.contracts.CampaignContract"
    }

    class Start : TypeOnlyCommandData()
    class End : TypeOnlyCommandData()
    class Pledge : TypeOnlyCommandData()

    override fun verify(tx: LedgerTransaction) {
        // TODO
    }
}