package net.corda.demos.crowdFunding.flows

import net.corda.core.utilities.getOrThrow
import net.corda.demos.crowdFunding.structures.Campaign
import net.corda.finance.POUNDS
import net.corda.node.internal.StartedNode
import net.corda.testing.node.MockNetwork
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class StartCampaignTests : CrowdFundingTest(numberOfNodes = 5) {

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

    @Test
    fun `successfully start and broadcast campaign to all nodes`() {
        // Start a new Campaign.
        val flow = StartCampaign(rogersCampaign)
        val campaign = A.start(flow).getOrThrow()

        // Extract the state from the transaction.
        val campaignStateRef = campaign.tx.outRef<Campaign>(0).ref
        val campaignState = campaign.tx.outputs.single()

        // Get the Campaign state from the observer node vaults.
        val aCampaign = A.database.transaction { A.services.loadState(campaignStateRef) }
        val bCampaign = B.database.transaction { B.services.loadState(campaignStateRef) }
        val cCampaign = C.database.transaction { C.services.loadState(campaignStateRef) }
        val dCampaign = D.database.transaction { D.services.loadState(campaignStateRef) }
        val eCampaign = E.database.transaction { E.services.loadState(campaignStateRef) }

        // All the states should be equal.
        assertEquals(1, setOf(campaignState, aCampaign, bCampaign, cCampaign, dCampaign, eCampaign).size)

        logger.info("Even though PartyA is the only participant in the Campaign, all other parties should have a copy of it.")
        logger.info("The Campaign state does not include any information about the observers.")
        logger.info("PartyA: $campaignState")
        logger.info("PartyB: $bCampaign")
        logger.info("PartyC: $cCampaign")
        logger.info("PartyD: $dCampaign")
        logger.info("PartyE: $eCampaign")

        // We just shut down the nodes now - no need to wait for the nextScheduledActivity.
    }

}