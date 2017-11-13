package net.corda.demos.crowdFunding.structures

import net.corda.core.contracts.StateAndRef
import net.corda.core.serialization.CordaSerializable
import net.corda.finance.contracts.asset.Cash
import java.security.PublicKey

@CordaSerializable
class CashStatesPayload(
        val inputs: List<StateAndRef<Cash.State>>,
        val outputs: List<Cash.State>,
        val signingKeys: List<PublicKey>
)