package net.corda.demos.crowdFunding.flows

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.demos.crowdFunding.structures.Campaign
import net.corda.demos.crowdFunding.structures.Pledge
import net.corda.finance.GBP
import net.corda.finance.POUNDS
import net.corda.finance.contracts.getCashBalance
import net.corda.node.internal.StartedNode
import net.corda.testing.node.MockNetwork
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

// TODO: Refactor repeated tests.
class EndCampaignTests : CrowdFundingTest(numberOfNodes = 5) {

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
        }
    }

    // TODO: Finish this unit test.
    @Test
    fun `start campaign, make a pledge, don't raise enough, then end the campaign with a failure`() {
        // Start a campaign on PartyA.
        val startCampaignFlow = StartCampaign(rogersCampaign)
        val newCampaign = A.start(startCampaignFlow).getOrThrow()
        val newCampaignState = newCampaign.tx.outputs.single().data as Campaign
        val newCampaignId = newCampaignState.linearId

        // B makes a pledge to A's campaign.
        val makePledgeFlow = MakePledge.Initiator(100.POUNDS, newCampaignId, broadcastToObservers = true)
        val campaignAfterFirstPledge = B.start(makePledgeFlow).getOrThrow()
        val campaignStateAfterFirstPledge = campaignAfterFirstPledge.tx.outputsOfType<Campaign>().single()

        // Wait for the campaign to end...
        net.waitQuiescent()

        checkUpdatesAreCommitted(A, newCampaignId, campaignStateAfterFirstPledge)
        checkUpdatesAreCommitted(B, newCampaignId, campaignStateAfterFirstPledge)
        checkUpdatesAreCommitted(C, newCampaignId, campaignStateAfterFirstPledge)
        checkUpdatesAreCommitted(D, newCampaignId, campaignStateAfterFirstPledge)
        checkUpdatesAreCommitted(E, newCampaignId, campaignStateAfterFirstPledge)
    }

    @Test
    fun `start campaign, make a pledge, raise enough, then end the campaign with a success`() {
        // Issue cash to begin with.
        val bCash = selfIssueCash(B, 500.POUNDS)
        val cCash = selfIssueCash(C, 500.POUNDS)
        // Start a campaign on PartyA.
        val startCampaignFlow = StartCampaign(rogersCampaign)
        val newCampaign = A.start(startCampaignFlow).getOrThrow()
        val newCampaignState = newCampaign.tx.outputs.single().data as Campaign
        val newCampaignStateRef = newCampaign.tx.outRef<Campaign>(0).ref
        val newCampaignId = newCampaignState.linearId

        logger.info("New campaign started")
        logger.info(newCampaign.toString())
        logger.info(newCampaign.tx.toString())

        // B makes a pledge to A's campaign.
        val bMakePledgeFlow = MakePledge.Initiator(500.POUNDS, newCampaignId, broadcastToObservers = true)
        val campaignAfterFirstPledge = B.start(bMakePledgeFlow).getOrThrow()
        val campaignStateAfterFirstPledge = campaignAfterFirstPledge.tx.outputsOfType<Campaign>().single()
        val campaignStateRefAfterFirstPledge = campaignAfterFirstPledge.tx.outRefsOfType<Campaign>().single().ref
        val firstPledge = campaignAfterFirstPledge.tx.outputsOfType<Pledge>().single()

        logger.info("PartyB pledges £500 to PartyA")
        logger.info(campaignAfterFirstPledge.toString())
        logger.info(campaignAfterFirstPledge.tx.toString())

        // We need this to avoid double spend exceptions.
        Thread.sleep(1000)

        // C makes a pledge to A's campaign.
        val cMakePledgeFlow = MakePledge.Initiator(500.POUNDS, newCampaignId, broadcastToObservers = true)
        val campaignAfterSecondPledge = C.start(cMakePledgeFlow).getOrThrow()
        val campaignStateAfterSecondPledge = campaignAfterSecondPledge.tx.outputsOfType<Campaign>().single()
        val campaignStateRefAfterSecondPledge = campaignAfterSecondPledge.tx.outRefsOfType<Campaign>().single().ref
        val secondPledge = campaignAfterSecondPledge.tx.outputsOfType<Pledge>().single()

        logger.info("PartyC pledges £500 to PartyA")
        logger.info(campaignAfterSecondPledge.toString())
        logger.info(campaignAfterSecondPledge.tx.toString())

        logger.info("PartyA runs the EndCampaign flow and requests cash from the pledgers (PartyB and PartyC).")
        A.database.transaction {
            val (_, observable) = A.services.validatedTransactions.track()
            observable.subscribe { tx ->
                // Don't log dependency transactions.
                val myKeys = A.services.keyManagementService.filterMyKeys(tx.tx.requiredSigningKeys).toList()
                if (myKeys.isNotEmpty()) {
                    logger.info(tx.tx.toString())
                }
            }
        }

        net.waitQuiescent()

        // Now perform the tests to check everyone has the correct data.

        // See that everyone gets the new campaign.
        val aNewCampaign = A.database.transaction { A.services.loadState(newCampaignStateRef).data }
        val bNewCampaign = B.database.transaction { B.services.loadState(newCampaignStateRef).data }
        val cNewCampaign = C.database.transaction { C.services.loadState(newCampaignStateRef).data }
        val dNewCampaign = D.database.transaction { D.services.loadState(newCampaignStateRef).data }
        val eNewCampaign = E.database.transaction { E.services.loadState(newCampaignStateRef).data }

        assertEquals(1, setOf(newCampaignState, aNewCampaign, bNewCampaign, cNewCampaign, dNewCampaign, eNewCampaign).size)

        // See that everyone gets the updated campaign after the first pledge.
        val aCampaignAfterPledge = A.database.transaction { A.services.loadState(campaignStateRefAfterFirstPledge).data }
        val bCampaignAfterPledge = B.database.transaction { B.services.loadState(campaignStateRefAfterFirstPledge).data }
        val cCampaignAfterPledge = C.database.transaction { C.services.loadState(campaignStateRefAfterFirstPledge).data }
        val dCampaignAfterPledge = D.database.transaction { D.services.loadState(campaignStateRefAfterFirstPledge).data }
        val eCampaignAfterPledge = E.database.transaction { E.services.loadState(campaignStateRefAfterFirstPledge).data }

        // All parties should have the same updated Campaign state.
        assertEquals(1, setOf(campaignStateAfterFirstPledge, aCampaignAfterPledge, bCampaignAfterPledge, cCampaignAfterPledge, dCampaignAfterPledge, eCampaignAfterPledge).size)

        // See that confidentiality is maintained.
        assertEquals(B.legalIdentity(), A.services.identityService.wellKnownPartyFromAnonymous(firstPledge.pledger))
        assertEquals(B.legalIdentity(), B.services.identityService.wellKnownPartyFromAnonymous(firstPledge.pledger))
        assertEquals(null, C.database.transaction { C.services.identityService.wellKnownPartyFromAnonymous(firstPledge.pledger) })
        assertEquals(null, D.database.transaction { D.services.identityService.wellKnownPartyFromAnonymous(firstPledge.pledger) })
        assertEquals(null, E.database.transaction { E.services.identityService.wellKnownPartyFromAnonymous(firstPledge.pledger) })

        // See that everyone gets the updated campaign after the second pledge.
        val aCampaignAfterSecondPledge = A.database.transaction { A.services.loadState(campaignStateRefAfterSecondPledge).data }
        val bCampaignAfterSecondPledge = B.database.transaction { B.services.loadState(campaignStateRefAfterSecondPledge).data }
        val cCampaignAfterSecondPledge = C.database.transaction { C.services.loadState(campaignStateRefAfterSecondPledge).data }
        val dCampaignAfterSecondPledge = D.database.transaction { D.services.loadState(campaignStateRefAfterSecondPledge).data }
        val eCampaignAfterSecondPledge = E.database.transaction { E.services.loadState(campaignStateRefAfterSecondPledge).data }

        // All parties should have the same updated Campaign state.
        assertEquals(1, setOf(campaignStateAfterSecondPledge, aCampaignAfterSecondPledge, bCampaignAfterSecondPledge, cCampaignAfterSecondPledge, dCampaignAfterSecondPledge, eCampaignAfterSecondPledge).size)

        // See that confidentiality is maintained.
        assertEquals(C.legalIdentity(), A.services.identityService.wellKnownPartyFromAnonymous(secondPledge.pledger))
        assertEquals(null, B.database.transaction { B.services.identityService.wellKnownPartyFromAnonymous(secondPledge.pledger) })
        assertEquals(C.legalIdentity(), C.services.identityService.wellKnownPartyFromAnonymous(secondPledge.pledger))
        assertEquals(null, D.database.transaction { D.services.identityService.wellKnownPartyFromAnonymous(secondPledge.pledger) })
        assertEquals(null, E.database.transaction { E.services.identityService.wellKnownPartyFromAnonymous(secondPledge.pledger) })

        // WARNING: The nodes which were not involved in the pledging or the campaign get to see the transferred cash in their vaults!!!!!!!!
        // This is not a bug but a consequence of storing ALL output states in a transaction.
        // We need to change this such that a filtered transaction can be recorded instead of a full SignedTransaction.
        // The other option is not to broadcast the pledge transactions.
        A.database.transaction { logger.info(A.services.getCashBalance(GBP).toString()) }
        B.database.transaction { logger.info(B.services.getCashBalance(GBP).toString()) }
        C.database.transaction { logger.info(C.services.getCashBalance(GBP).toString()) }
        D.database.transaction { logger.info(D.services.getCashBalance(GBP).toString()) }
        E.database.transaction { logger.info(E.services.getCashBalance(GBP).toString()) }
    }

}