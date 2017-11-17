package net.corda.demos.crowdFunding.contracts

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.hours
import net.corda.core.utilities.seconds
import net.corda.demos.crowdFunding.structures.Campaign
import net.corda.demos.crowdFunding.structures.Pledge
import net.corda.finance.POUNDS
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
class PledgeTests {

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
                "net.corda.testing.contracts",
                "net.corda.demos.crowdFunding",
                "net.corda.finance"
        )
    }

    @After
    fun after() {
        unsetCordappPackages()
    }

    private val oneHourFromNow = Instant.now() + 1.hours

    /** A failed campaign with no pledges that ends now. */
    private val failedCampaignWithNoPledges = Campaign(
            name = "Roger's Campaign",
            manager = A,
            target = 1000.POUNDS,
            deadline = oneHourFromNow
    )

    private val newValidCampaign = Campaign(
            name = "Roger's Campaign",
            manager = A,
            target = 1000.POUNDS,
            deadline = oneHourFromNow
    )

    @Test
    fun `Make pledge tests`() {
        val defaultPledge = Pledge(100.POUNDS, B, A, newValidCampaign.linearId)

        ledger {
            // Valid make pledge transaction.
            transaction {
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = newValidCampaign.raisedSoFar + defaultPledge.amount) }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.verifies()
            }

            // Pledging a zero amount.
            transaction {
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy() }
                output(PledgeContract.CONTRACT_REF) { defaultPledge.copy(amount = 0.POUNDS) }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }

            // Creating more than one pledge at a time.
            transaction {
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = newValidCampaign.raisedSoFar + defaultPledge.amount) }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }

            // States in the wrong place.
            transaction {
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = 200.POUNDS) }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                input(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }

            // Missing campaign states.
            transaction {
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }

            // Incorrect signers.
            transaction {
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = newValidCampaign.raisedSoFar + defaultPledge.amount) }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }

            transaction {
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = newValidCampaign.raisedSoFar + defaultPledge.amount) }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }
        }

    }

    @Test
    fun `Cancel pledge tests`() {
        val defaultPledge = Pledge(100.POUNDS, B, A, newValidCampaign.linearId)

        val endedCampaign = newValidCampaign.copy(deadline = Instant.now().minusSeconds(1))

        ledger {
            // Valid make pledge transaction.
            transaction {
                input(CampaignContract.CONTRACT_REF) { endedCampaign }
                input(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A).toTypedArray()) { PledgeContract.Cancel() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.End() }
                this.verifies()
            }

            // Has pledge outputs.
            transaction {
                input(CampaignContract.CONTRACT_REF) { endedCampaign }
                input(PledgeContract.CONTRACT_REF) { defaultPledge }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Cancel() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.End() }
                this.fails()
            }

            // Wrong public key.
            transaction {
                input(CampaignContract.CONTRACT_REF) { endedCampaign }
                input(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(B).toTypedArray()) { PledgeContract.Cancel() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.End() }
                this.fails()
            }

            // No campaign state present.
            transaction {
                input(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(B).toTypedArray()) { PledgeContract.Cancel() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.End() }
                this.fails()
            }

            // Cancelling a pledge for a different campaign.
            transaction {
                input(CampaignContract.CONTRACT_REF) { endedCampaign }
                input(PledgeContract.CONTRACT_REF) { defaultPledge.copy(campaignReference = UniqueIdentifier()) }
                command(*partyKeys(A).toTypedArray()) { PledgeContract.Cancel() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.End() }
                this.fails()
            }
        }
    }

}