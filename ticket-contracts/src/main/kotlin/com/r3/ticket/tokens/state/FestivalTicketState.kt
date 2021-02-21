package com.r3.ticket.tokens.state

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.ticket.tokens.contract.FestivalTicketContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import java.math.BigDecimal

/**
 * Represents a FestivalTicketState that can evolve over time.
 */
@BelongsToContract(FestivalTicketContract::class)
data class FestivalTicketState(
        val ticketId: BigDecimal,
        val ticketName: String,
        val festival: String,
        val assessor: Party,
        val requester: AbstractParty,
        override val linearId: UniqueIdentifier
) : EvolvableTokenType() {
    @Suppress("unused")
    constructor(
            ticketId: String,
            ticketName: String,
            festival: String,
            assessor: Party,
            requester: Party) : this(BigDecimal(ticketId), ticketName, festival,assessor, requester, UniqueIdentifier())
    @Suppress("unused")
    constructor(
            ticketId: String,
            ticketName: String,
            festival: String,
            assessor: Party,
            requester: Party,
            linearId: UniqueIdentifier) : this(BigDecimal(ticketId),ticketName,festival, assessor, requester, linearId)

    override val maintainers: List<Party>
        get() = listOf(assessor)
    override val participants: List<AbstractParty>
        get() = setOf(assessor, requester).toList()
    override val fractionDigits = 0
}
