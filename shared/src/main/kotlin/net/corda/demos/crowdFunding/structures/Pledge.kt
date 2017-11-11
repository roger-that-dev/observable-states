package net.corda.demos.crowdFunding.structures

import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import java.util.*

data class Pledge(
        val amount: Amount<Currency>,
        val pledger: AbstractParty,
        val manager: Party,
        val campaignReference: UniqueIdentifier,
        override val participants: List<AbstractParty> = listOf(pledger, manager)
) : ContractState