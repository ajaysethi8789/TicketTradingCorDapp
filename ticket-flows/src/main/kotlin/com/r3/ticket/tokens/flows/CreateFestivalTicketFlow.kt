package com.r3.ticket.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens
import com.r3.ticket.tokens.state.FestivalTicketState
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import java.math.BigDecimal

/**
 * Implements the create evolvable token flow.
 */
@InitiatingFlow
@StartableByRPC
class CreateFestivalTicketFlow(private val ticketId: BigDecimal, private val ticketName: String, private val festival: String, private val assessor : Party, private val requester : AbstractParty) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val festivalTicketState = FestivalTicketState(ticketId.toString(),ticketName,festival,assessor, requester as Party);
        val transactionState = festivalTicketState withNotary notary

        return subFlow(CreateEvolvableTokens(transactionState))
    }
}

