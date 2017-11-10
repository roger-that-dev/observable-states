package net.corda.demos.crowdFunding.contracts

import net.corda.core.utilities.getOrThrow
import net.corda.demos.crowdFunding.flows.StartCampaign
import net.corda.finance.POUNDS
import net.corda.node.internal.StartedNode
import net.corda.testing.node.MockNetwork
import org.junit.Before
import org.junit.Test
import java.time.Instant

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

    @Test
    fun `successfullyStartCampaign`() {
        val deadline = Instant.now().plusSeconds(5L)
        val flow = StartCampaign(1000.POUNDS, "Roger's campaign", deadline)
        val campaign = A.start(flow).getOrThrow()
        net.waitQuiescent()
    }

}