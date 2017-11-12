package net.corda.demos.crowdFunding.contracts

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.demos.crowdFunding.flows.MakePledge
import net.corda.demos.crowdFunding.flows.StartCampaign
import net.corda.demos.crowdFunding.structures.Campaign
import net.corda.demos.crowdFunding.structures.Pledge
import net.corda.finance.POUNDS
import net.corda.node.internal.StartedNode
import net.corda.testing.node.MockNetwork
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class IntegrationTest : CrowdFundingTest(numberOfNodes = 5) {

    lateinit var A: StartedNode<MockNetwork.MockNode>
    lateinit var B: StartedNode<MockNetwork.MockNode>
    lateinit var C: StartedNode<MockNetwork.MockNode>
    lateinit var D: StartedNode<MockNetwork.MockNode>
    lateinit var E: StartedNode<MockNetwork.MockNode>

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        B = nodes[1]
        C = nodes[2]
        D = nodes[3]
        E = nodes[4]
    }

    private val rogersCampaign
        get() = Campaign(
                name = "Roger's Campaign",
                target = 1000.POUNDS,
                manager = A.legalIdentity(),
                deadline = fiveSecondsFromNow
        )

    private fun checkUpdatesAreCommitted(
            party: StartedNode<MockNetwork.MockNode>,
            campaignId: UniqueIdentifier,
            campaignState: Campaign
    ) {
        // Check that the EndCampaign transaction is committed by B and the Pledge/Campaign states are consumed.
        party.database.transaction {
            val (_, observable) = party.services.validatedTransactions.track()
            observable.first { it.tx.outputStates.isEmpty() }.subscribe { logger.info(it.tx.toString()) }

            val campaignQuery = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(campaignId))
            assertEquals(emptyList(), party.services.vaultService.queryBy<Campaign>(campaignQuery).states)

            val pledgeQuery = QueryCriteria.LinearStateQueryCriteria(linearId = campaignState.pledges.toList())
            assertEquals(emptyList(), party.services.vaultService.queryBy<Pledge>(pledgeQuery).states)
        }
    }

    @Test
    fun `start campaign, make a pledge, don't raise enough, then end the campaign with a failure`() {
        // Start a campaign on PartyA.
        val startCampaignFlow = StartCampaign(rogersCampaign)
        val newCampaign = A.start(startCampaignFlow).getOrThrow()
        val newCampaignState = newCampaign.tx.outputs.single().data as Campaign
        val newCampaignId = newCampaignState.linearId

        // B makes a pledge to A's campaign.
        val makePledgeFlow = MakePledge.Initiator(100.POUNDS, newCampaignId)
        val campaignAfterFirstPledge = B.start(makePledgeFlow).getOrThrow()
        val campaignStateAfterFirstPledge = campaignAfterFirstPledge.tx.outputsOfType<Campaign>().single()

        net.waitQuiescent()

        checkUpdatesAreCommitted(A, newCampaignId, campaignStateAfterFirstPledge)
        checkUpdatesAreCommitted(B, newCampaignId, campaignStateAfterFirstPledge)
        checkUpdatesAreCommitted(C, newCampaignId, campaignStateAfterFirstPledge)
        checkUpdatesAreCommitted(D, newCampaignId, campaignStateAfterFirstPledge)
        checkUpdatesAreCommitted(E, newCampaignId, campaignStateAfterFirstPledge)
    }

}