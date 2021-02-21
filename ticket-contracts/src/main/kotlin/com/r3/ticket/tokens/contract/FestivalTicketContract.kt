package com.r3.ticket.tokens.contract

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import com.r3.ticket.tokens.state.FestivalTicketState
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class FestivalTicketContract : EvolvableTokenContract(), Contract {

    override fun additionalCreateChecks(tx: LedgerTransaction) {
        val outTicket = tx.outputsOfType<FestivalTicketState>().first()
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) {
        val outTicket = tx.outputsOfType<FestivalTicketState>().first()

    }
}