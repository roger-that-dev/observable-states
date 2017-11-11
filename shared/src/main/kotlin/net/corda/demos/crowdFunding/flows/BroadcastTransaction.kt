package net.corda.demos.crowdFunding.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.SendTransactionFlow
import net.corda.core.transactions.SignedTransaction

@InitiatingFlow
class BroadcastTransaction(val stx: SignedTransaction) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        // Get a list of all identities.
        val everyone = serviceHub.networkMapCache.allNodes.flatMap { it.legalIdentities }

        // Filter out the notaries and remove us.
        val everyoneButMeAndNotary = everyone.filter { serviceHub.networkMapCache.isNotary(it).not() } - ourIdentity

        // Send the transaction to the rest.
        everyoneButMeAndNotary.map { initiateFlow(it) }.forEach { subFlow(SendTransactionFlow(it, stx)) }
    }

}