package net.corda.demos.crowdFunding.structures

import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table

data class Pledge(
        val amount: Amount<Currency>,
        val pledger: AbstractParty,
        val manager: Party,
        val campaignReference: UniqueIdentifier,
        override val participants: List<AbstractParty> = listOf(pledger, manager),
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState, QueryableState {
    override fun supportedSchemas() = listOf(PledgeSchemaV1)
    override fun generateMappedObject(schema: MappedSchema) = PledgeSchemaV1.PledgeEntity(this)

    object PledgeSchemaV1 : MappedSchema(Pledge::class.java, 1, listOf(PledgeEntity::class.java)) {
        @Entity
        @Table(name = "pledges")
        class PledgeEntity(pledge: Pledge) : PersistentState() {
            @Column
            var currency: String = pledge.amount.token.toString()
            @Column
            var amount: Long = pledge.amount.quantity
            @Column
            @Lob
            var pledger: ByteArray = pledge.pledger.owningKey.encoded
            @Column
            @Lob
            var manager: ByteArray = pledge.manager.owningKey.encoded
            @Column
            var campaign_reference: String = pledge.campaignReference.id.toString()
            @Column
            var linear_id: String = pledge.linearId.id.toString()
        }
    }
}