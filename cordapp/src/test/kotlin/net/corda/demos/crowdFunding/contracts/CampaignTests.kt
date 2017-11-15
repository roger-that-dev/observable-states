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
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.CASH_PROGRAM_ID
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.contracts.DUMMY_PROGRAM_ID
import net.corda.testing.contracts.DummyState
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
    fun `Start new campaign tests`() {
        ledger {
            // Valid start new campaign transaction.
            transaction {
                output(CampaignContract.CONTRACT_REF) { newValidCampaign }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.Start() }
                this.verifies()
            }

            // Signers incorrect.
            transaction {
                output(CampaignContract.CONTRACT_REF) { newValidCampaign }
                command(*partyKeys(B, A).toTypedArray()) { CampaignContract.Start() }
                this.fails()
            }

            // Signers incorrect.
            transaction {
                output(CampaignContract.CONTRACT_REF) { newValidCampaign }
                command(*partyKeys(B, A).toTypedArray()) { CampaignContract.Start() }
                this.fails()
            }

            // Incorrect inputs / outputs.
            transaction {
                input(DUMMY_PROGRAM_ID) { DummyState() }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.Start() }
                this.fails()
            }

            // Incorrect inputs / outputs.
            transaction {
                output(DUMMY_PROGRAM_ID) { DummyState() }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.Start() }
                this.fails()
            }

            // Zero amount.
            transaction {
                output(CampaignContract.CONTRACT_REF) { Campaign("Test", A, 0.POUNDS, oneHourFromNow) }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.Start() }
                this.fails()
            }

            // Deadline not in future.
            transaction {
                output(CampaignContract.CONTRACT_REF) { Campaign("Test", A, 0.POUNDS, Instant.now()) }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.Start() }
                this.fails()
            }

            // No name.
            transaction {
                output(CampaignContract.CONTRACT_REF) { Campaign("", A, 0.POUNDS, Instant.now()) }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.Start() }
                this.fails()
            }

            // Raised so far, not zero.
            transaction {
                output(CampaignContract.CONTRACT_REF) { Campaign("Test", A, 0.POUNDS, Instant.now(), 10.POUNDS) }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.Start() }
                this.fails()
            }
        }
    }

    @Test
    fun `Make a pledge tests`() {
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

            // Amounts don't match up.
            transaction {
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = 200.POUNDS) } // Wrong amount.
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }

            // Wrong currency.
            transaction {
                input(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(target = 1000.DOLLARS) }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = 200.DOLLARS) }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }

            // Missing states.
            transaction {
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = 100.POUNDS) }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }

            transaction {
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = 100.POUNDS) }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }

            // Additional irrelevant states.
            transaction {
                input(DUMMY_PROGRAM_ID) { DummyState() }
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = 100.POUNDS) }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }

            transaction {
                output(DUMMY_PROGRAM_ID) { DummyState() }
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = 100.POUNDS) }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }

            // Changing campaign stuff that shouldn't change.
            transaction {
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = 100.POUNDS, name = "Changed") }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }

            transaction {
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = 100.POUNDS, target = 200.POUNDS) }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }

            transaction {
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = 100.POUNDS, linearId = UniqueIdentifier()) }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }

            transaction {
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = 100.POUNDS, deadline = Instant.now()) }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }

            // Pledge for wrong campaign.
            transaction {
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = 100.POUNDS) }
                output(PledgeContract.CONTRACT_REF) { defaultPledge.copy(campaignReference = UniqueIdentifier()) } // Wrong campaign reference.
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }

            // Pledge after deadline.
            transaction {
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = 100.POUNDS) }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(newValidCampaign.deadline + 1.seconds, 5.seconds)
                this.fails()
            }

            // Wrong keys in accept pledge command.
            transaction {
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = 100.POUNDS) }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(B).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }

            transaction {
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = 100.POUNDS) }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(B, A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(Instant.now(), 5.seconds)
                this.fails()
            }

            // Pledge after deadline.
            transaction {
                input(CampaignContract.CONTRACT_REF) { newValidCampaign }
                output(CampaignContract.CONTRACT_REF) { newValidCampaign.copy(raisedSoFar = 100.POUNDS) }
                output(PledgeContract.CONTRACT_REF) { defaultPledge }
                command(*partyKeys(A, B).toTypedArray()) { PledgeContract.Create() }
                command(*partyKeys(A).toTypedArray()) { CampaignContract.AcceptPledge() }
                timeWindow(newValidCampaign.deadline + 1.seconds, 5.seconds)
                this.fails()
            }
        }
    }

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