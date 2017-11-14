package net.corda.demos.crowdFunding.contracts

import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import net.corda.demos.crowdFunding.structures.Campaign
import net.corda.demos.crowdFunding.structures.Pledge
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.CASH_PROGRAM_ID
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.ledger
import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.time.Instant

// TODO: Write more tests.
class CampaignTests {

    private val issuer: Party = createParty("Issuer", 0)
    private val A: Party = createParty("A", 1)
    private val B: Party = createParty("B", 2)
    private val C: Party = createParty("C", 3)
    private val D: Party = createParty("D", 4)
    private val E: Party = createParty("E", 5)

    val defaultRef = OpaqueBytes(ByteArray(1, { 1 }))
    val defaultIssuer = issuer.ref(defaultRef)

    private fun createParty(name: String, random: Long): Party {
        val key: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(random)) }
        val identity = CordaX500Name(organisation = "Party$name", locality = "TestLand", country = "GB")
        return Party(identity, key.public)
    }

    private fun partyKeys(vararg parties: Party) = parties.map { it.owningKey }

    @Before
    fun before() {
        setCordappPackages(
                "net.corda.demos.crowdFunding",
                "net.corda.finance"
        )
    }

    @After
    fun after() {
        unsetCordappPackages()
    }

    /** A failed campaign with no pledges that ends now. */
    private val failedCampaignWithNoPledges = Campaign("Roger's Campaign", A, 1000.POUNDS, Instant.now())

    @Test
    fun `End campaign after failure with no pledges`() {
        ledger {
            transaction {
                input(CampaignContract.CONTRACT_REF) { failedCampaignWithNoPledges }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.End() }
                this.verifies()
            }
        }
    }

    @Test
    fun `End campaign in success`() {
        ledger {
            transaction {
                input(CampaignContract.CONTRACT_REF) { failedCampaignWithNoPledges }
                input(PledgeContract.CONTRACT_REF) { Pledge(500.POUNDS, B, A, failedCampaignWithNoPledges.linearId) }
                input(PledgeContract.CONTRACT_REF) { Pledge(300.POUNDS, C, A, failedCampaignWithNoPledges.linearId) }
                input(PledgeContract.CONTRACT_REF) { Pledge(200.POUNDS, D, A, failedCampaignWithNoPledges.linearId) }
                input(CASH_PROGRAM_ID) { Cash.State(defaultIssuer, 500.POUNDS, B) }
                input(CASH_PROGRAM_ID) { Cash.State(defaultIssuer, 300.POUNDS, C) }
                input(CASH_PROGRAM_ID) { Cash.State(defaultIssuer, 200.POUNDS, D) }
                output(CASH_PROGRAM_ID) { Cash.State(defaultIssuer, 500.POUNDS, A) }
                output(CASH_PROGRAM_ID) { Cash.State(defaultIssuer, 300.POUNDS, A) }
                output(CASH_PROGRAM_ID) { Cash.State(defaultIssuer, 200.POUNDS, A) }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.End() }
                command(*partyKeys(A).toTypedArray()) { PledgeContract.Cancel() }
                command(*partyKeys(B, C, D).toTypedArray()) { Cash.Commands.Move() }
                this.verifies()
            }
        }
    }

}