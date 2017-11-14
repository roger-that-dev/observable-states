package net.corda.demos.crowdFunding.flows

import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.utilities.getOrThrow
import net.corda.demos.crowdFunding.structures.Campaign
import net.corda.demos.crowdFunding.structures.Pledge
import net.corda.finance.POUNDS
import net.corda.node.internal.StartedNode
import net.corda.testing.node.MockNetwork
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @Test
    fun `successfully make a pledge and broadcast the updated campaign state to all parties`() {
        // Campaign.
        val rogersCampaign = Campaign(
                name = "Roger's Campaign",
                target = 1000.POUNDS,
                manager = A.legalIdentity(),
                deadline = fiveSecondsFromNow
        )

        // Start a new campaign.
        val startCampaignFlow = StartCampaign(rogersCampaign)
        val createCampaignTransaction = A.start(startCampaignFlow).getOrThrow()

        // Extract the state from the transaction.
        val campaignState = createCampaignTransaction.tx.outputs.single().data as Campaign
        val campaignId = campaignState.linearId

        // Make a pledge from PartyB to PartyA for £100.
        val makePledgeFlow = MakePledge.Initiator(100.POUNDS, campaignId, broadcastToObservers = true)
        val acceptPledgeTransaction = B.start(makePledgeFlow).getOrThrow()

        println("New campaign started")
        println(createCampaignTransaction)
        println(createCampaignTransaction.tx)
        println("PartyB pledges £100 to PartyA")
        println(acceptPledgeTransaction)
        println(acceptPledgeTransaction.tx)

        //Extract the states from the transaction.
        val campaignStateRefAfterPledge = acceptPledgeTransaction.tx.outRefsOfType<Campaign>().single().ref
        val campaignAfterPledge = acceptPledgeTransaction.tx.outputsOfType<Campaign>().single()
        val newPledgeStateRef = acceptPledgeTransaction.tx.outRefsOfType<Pledge>().single().ref
        val newPledge = acceptPledgeTransaction.tx.outputsOfType<Pledge>().single()

        val aCampaignAfterPledge = A.database.transaction { A.services.loadState(campaignStateRefAfterPledge).data }
        val bCampaignAfterPledge = B.database.transaction { B.services.loadState(campaignStateRefAfterPledge).data }
        val cCampaignAfterPledge = C.database.transaction { C.services.loadState(campaignStateRefAfterPledge).data }
        val dCampaignAfterPledge = D.database.transaction { D.services.loadState(campaignStateRefAfterPledge).data }
        val eCampaignAfterPledge = E.database.transaction { E.services.loadState(campaignStateRefAfterPledge).data }

        // All parties should have the same updated Campaign state.
        assertEquals(1,
                setOf(
                        campaignAfterPledge,
                        aCampaignAfterPledge,
                        bCampaignAfterPledge,
                        cCampaignAfterPledge,
                        dCampaignAfterPledge,
                        eCampaignAfterPledge
                ).size
        )

        val aNewPledge = A.database.transaction { A.services.loadState(newPledgeStateRef).data } as Pledge
        val bNewPledge = B.database.transaction { B.services.loadState(newPledgeStateRef).data } as Pledge
        val cNewPledge = C.database.transaction { C.services.loadState(newPledgeStateRef).data } as Pledge
        val dNewPledge = D.database.transaction { D.services.loadState(newPledgeStateRef).data } as Pledge
        val eNewPledge = E.database.transaction { E.services.loadState(newPledgeStateRef).data } as Pledge

        // All parties should have the same Pledge state.
        assertEquals(1,
                setOf(
                        newPledge,
                        aNewPledge,
                        bNewPledge,
                        cNewPledge,
                        dNewPledge,
                        eNewPledge
                ).size
        )

        // Only A and B should know the identity of the pledger (who is B in this case).
        assertEquals(B.legalIdentity(), A.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger))
        assertEquals(B.legalIdentity(), B.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger))
        assertEquals(null, C.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger))
        assertEquals(null, D.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger))
        assertEquals(null, E.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger))

        net.waitQuiescent()
    }

    @Test
    fun `successfully make a pledge without broadcasting the updated campaign state to all parties`() {
        // Campaign.
        val rogersCampaign = Campaign(
                name = "Roger's Campaign",
                target = 1000.POUNDS,
                manager = A.legalIdentity(),
                deadline = fiveSecondsFromNow // We shut the nodes down before the EndCampaignFlow is run though.
        )

        // Start a new campaign.
        val startCampaignFlow = StartCampaign(rogersCampaign)
        val createCampaignTransaction = A.start(startCampaignFlow).getOrThrow()

        // Extract the state from the transaction.
        val campaignState = createCampaignTransaction.tx.outputs.single().data as Campaign
        val campaignId = campaignState.linearId

        // Make a pledge from PartyB to PartyA for £100 but don't broadcast it to everyone else.
        val makePledgeFlow = MakePledge.Initiator(100.POUNDS, campaignId, broadcastToObservers = false)
        val acceptPledgeTransaction = B.start(makePledgeFlow).getOrThrow()

        println("New campaign started")
        println(createCampaignTransaction)
        println(createCampaignTransaction.tx)
        println("PartyB pledges £100 to PartyA")
        println(acceptPledgeTransaction)
        println(acceptPledgeTransaction.tx)

        //Extract the states from the transaction.
        val campaignStateRefAfterPledge = acceptPledgeTransaction.tx.outRefsOfType<Campaign>().single().ref
        val campaignAfterPledge = acceptPledgeTransaction.tx.outputsOfType<Campaign>().single()
        val newPledgeStateRef = acceptPledgeTransaction.tx.outRefsOfType<Pledge>().single().ref
        val newPledge = acceptPledgeTransaction.tx.outputsOfType<Pledge>().single()

        val aCampaignAfterPledge = A.database.transaction { A.services.loadState(campaignStateRefAfterPledge).data }
        val bCampaignAfterPledge = B.database.transaction { B.services.loadState(campaignStateRefAfterPledge).data }
        assertFailsWith(TransactionResolutionException::class) { C.database.transaction { C.services.loadState(campaignStateRefAfterPledge) } }
        assertFailsWith(TransactionResolutionException::class) { D.database.transaction { D.services.loadState(campaignStateRefAfterPledge) } }
        assertFailsWith(TransactionResolutionException::class) { E.database.transaction { E.services.loadState(campaignStateRefAfterPledge) } }

        // Only PartyA and PartyB should have the updated campaign state.
        assertEquals(1, setOf(campaignAfterPledge, aCampaignAfterPledge, bCampaignAfterPledge).size)

        val aNewPledge = A.database.transaction { A.services.loadState(newPledgeStateRef).data } as Pledge
        val bNewPledge = B.database.transaction { B.services.loadState(newPledgeStateRef).data } as Pledge
        assertFailsWith(TransactionResolutionException::class) { C.database.transaction { C.services.loadState(newPledgeStateRef) } }
        assertFailsWith(TransactionResolutionException::class) { D.database.transaction { D.services.loadState(newPledgeStateRef) } }
        assertFailsWith(TransactionResolutionException::class) { E.database.transaction { E.services.loadState(newPledgeStateRef) } }

        // Only PartyA and PartyB should have the updated campaign state.
        assertEquals(1, setOf(newPledge, aNewPledge, bNewPledge).size)

        // Only A and B should know the identity of the pledger (who is B in this case). Of course, the others won't know.
        assertEquals(B.legalIdentity(), A.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger))
        assertEquals(B.legalIdentity(), B.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger))
        assertEquals(null, C.database.transaction { C.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger) })
        assertEquals(null, D.database.transaction { D.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger) })
        assertEquals(null, E.database.transaction { E.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger) })

        net.waitQuiescent()
    }

}