package net.corda.demos.crowdFunding.structures

import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import java.util.*

data class Pledge(
        val amount: Amount<Currency>,
        val pledger: AbstractParty,
        val campaignReference: CampaignReference,
        override val participants: List<AbstractParty> = listOf(pledger, campaignReference.manager)
) : ContractState