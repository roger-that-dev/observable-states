package net.corda.demos.crowdFunding.flows

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.internal.StartedNode
import net.corda.testing.node.MockNetwork
import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.junit.After
import org.junit.Before
import org.slf4j.Logger
import java.time.Instant
import java.util.*

abstract class CrowdFundingTest(val numberOfNodes: Int) {

    lateinit protected var net: MockNetwork
    lateinit protected var nodes: List<StartedNode<MockNetwork.MockNode>>

    @Before
    abstract fun initialiseNodes()

    @Before
    fun setupNetwork() {
        setCordappPackages(
                "net.corda.demos.crowdFunding",
                "net.corda.finance"
        )
        net = MockNetwork(threadPerNode = true)
        nodes = createSomeNodes(numberOfNodes)
        nodes.forEach { node -> registerFlowsAndServices(node) }
    }

    @After
    fun tearDownNetwork() {
        net.stopNodes()
        unsetCordappPackages()
    }

    companion object {
        val logger: Logger = loggerFor<CrowdFundingTest>()
    }

    private fun calculateDeadlineInSeconds(interval: Long) = Instant.now().plusSeconds(interval)
    protected val oneSecondFromNow: Instant get() = calculateDeadlineInSeconds(1L)
    protected val fiveSecondsFromNow: Instant get() = calculateDeadlineInSeconds(5L)
    protected val tenSecondsFromNow: Instant get() = calculateDeadlineInSeconds(10L)
    protected val oneMinuteFromNow: Instant get() = calculateDeadlineInSeconds(60L)

    protected fun registerFlowsAndServices(node: StartedNode<MockNetwork.MockNode>) {
        val mockNode = node.internals
        mockNode.registerInitiatedFlow(RecordTransactionAsObserver::class.java)
        mockNode.registerInitiatedFlow(MakePledge.Responder::class.java)
        mockNode.registerInitiatedFlow(EndCampaign.Responder::class.java)
    }

    protected fun createSomeNodes(numberOfNodes: Int = 2): List<StartedNode<MockNetwork.MockNode>> {
        val notary = net.createNotaryNode(legalName = CordaX500Name("Notary", "London", "GB"))
        return (1..numberOfNodes).map { current ->
            val char = current.toChar() + 64
            val name = CordaX500Name("Party$char", "London", "GB")
            net.createPartyNode(notary.network.myAddress, name)
        }
    }

    fun <T : Any> StartedNode<MockNetwork.MockNode>.start(logic: FlowLogic<T>): CordaFuture<T> {
        return this.services.startFlow(logic).resultFuture
    }

    fun StartedNode<MockNetwork.MockNode>.legalIdentity(): Party {
        return this.services.myInfo.legalIdentities.first()
    }

    protected fun selfIssueCash(party: StartedNode<MockNetwork.MockNode>,
                                amount: Amount<Currency>): SignedTransaction {
        val notary = party.services.networkMapCache.notaryIdentities.firstOrNull()
                ?: throw IllegalStateException("Could not find a notary.")
        val issueRef = OpaqueBytes.of(0)
        val issueRequest = CashIssueFlow.IssueRequest(amount, issueRef, notary)
        val flow = CashIssueFlow(issueRequest)
        return party.services.startFlow(flow).resultFuture.getOrThrow().stx
    }

}
