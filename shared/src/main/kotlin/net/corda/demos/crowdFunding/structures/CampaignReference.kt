package net.corda.demos.crowdFunding.structures

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party

data class CampaignReference(val manager: Party, val campaignId: UniqueIdentifier)