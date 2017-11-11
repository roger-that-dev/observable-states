package net.corda.demos.crowdFunding.contracts

import net.corda.core.utilities.getOrThrow
import net.corda.demos.crowdFunding.flows.MakePledge
import net.corda.demos.crowdFunding.flows.StartCampaign
import net.corda.demos.crowdFunding.structures.Campaign
import net.corda.finance.POUNDS
import net.corda.node.internal.StartedNode
import net.corda.testing.node.MockNetwork
import org.junit.Before
import org.junit.Test

class MakePledgeTests : CrowdFundingTest(numberOfNodes = 5) {

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
                deadline = tenSecondsFromNow
        )

    @Test
    fun `successfully make a pledge and broadcast the updated campaign state`() {
        val startCampaignFlow = StartCampaign(rogersCampaign)
        val campaign = A.start(startCampaignFlow).getOrThrow()

        val campaignState = campaign.tx.outputs.single().data as Campaign
        val campaignId = campaignState.linearId

        val makePledgeFlow = MakePledge.Initiator(100.POUNDS, campaignId)
        val stx = B.start(makePledgeFlow).getOrThrow()
        println(stx.tx)

        net.waitQuiescent()
    }

}