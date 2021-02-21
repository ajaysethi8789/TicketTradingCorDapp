package com.r3.ticket.tokens.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.internal.flows.createKeyForAccount
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.issue.addIssueTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.utilities.heldBy
import com.r3.corda.lib.tokens.workflows.utilities.ourSigningKeys
import com.r3.ticket.tokens.state.FestivalTicketState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

/**
 * Implements the purchase token flow.
 * The buyer wants buy a token from a dealer and provides the payment.
 * The flow is initiated on the dealer's node.
 * The buyer creates the transaction with payment which is then verified by the dealer.
 *
 * To stop accidental double issue of the report a token marker is created. This token is issued
 * and held by the dealer, so only appears in their vault. When the diamond token is redeemed the
 * flow redeems the token marker too.
 */
@InitiatingFlow
@StartableByRPC
class PurchaseFestivalTicketFlow(
        private val linearId: UniqueIdentifier,
        private val dealer: AccountInfo,
        private val buyer: AccountInfo,
        private val amount: Amount<TokenType>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        requireThat { "Dealer not hosted on this node" using (dealer.host == ourIdentity) }

        val ticketReportRef = getStateReference(serviceHub, FestivalTicketState::class.java, linearId)
        val ticketReportData = ticketReportRef.state.data
        val ticketPointer = ticketReportData.toPointer<FestivalTicketState>()

        // Create a marker token type that is parallel to the diamond report token type
        val markerType = TokenType("Marker$linearId", 0)

        // Check that the marker is not already present
        requireThat { "Report already in use" using (hasNoToken(serviceHub, markerType, ourIdentity)) }

        if (buyer.host == ourIdentity){
            return PurchaseFestivalTicketFlowWithinNode().call()
        }

        val token = ticketPointer issuedBy ourIdentity
        val marker = markerType issuedBy ourIdentity heldBy ourIdentity
        val other = initiateFlow(buyer.host)

        // Send the full TokenPointer details to the buyer, this must be
        // available in the buyer's vault
        val tx = serviceHub.validatedTransactions.getTransaction(ticketReportRef.ref.txhash)!!

        subFlow(SendTransactionFlow(other, tx))

        // Send trade details
        other.send(TradeInfo(dealer, buyer, amount, token, marker))

        val signedTransactionFlow = object : SignTransactionFlow(other, tracker()) {
            override fun checkTransaction(stx: SignedTransaction) {

            }
        }
        val txId = subFlow(signedTransactionFlow)

        subFlow(IssueTokensFlowHandler(other))

        return txId
    }

    @Suppress("unused")
    @InitiatedBy(PurchaseFestivalTicketFlow::class)
    class PurchaseFestivalTicketFlowResponse (private val flowSession: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // Receive and record the TokenPointer
            subFlow(ReceiveTransactionFlow(flowSession, statesToRecord = StatesToRecord.ALL_VISIBLE))

            // Receive the trade details
            val tradeInfo = flowSession.receive<TradeInfo>().unwrap { it }

            val dealer = tradeInfo.dealer
            val buyer = tradeInfo.buyer

            requireThat {"Buyer not hosted on this node" using (buyer.host == ourIdentity) }

            val dealerParty = subFlow(RequestKeyForAccount(dealer))
            val buyerParty = serviceHub.createKeyForAccount(buyer)

            // Define criteria to retrieve only cash from payer
            val criteria = QueryCriteria.VaultQueryCriteria(
                    status = Vault.StateStatus.UNCONSUMED,
                    externalIds = listOf(buyer.identifier.id)
            )

            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val builder = TransactionBuilder(notary)

            // Add the money for the transaction
            addMoveFungibleTokensWithFlowException(builder, serviceHub, tradeInfo.price, dealerParty, buyerParty, criteria)

            // Issue the tokens
            addIssueTokens(builder, listOf(tradeInfo.token heldBy buyerParty, tradeInfo.marker))

            // Create a list of local signatures for the command
            val signers = builder.toLedgerTransaction(serviceHub).ourSigningKeys(serviceHub) + ourIdentity.owningKey

            // Sign off the transaction
            val selfSignedTransaction = serviceHub.signInitialTransaction(builder, signers)

            val fullySignedTransaction = subFlow(CollectSignaturesFlow(selfSignedTransaction, listOf(flowSession)))

            // Notify the notary
            val finalityTransaction = subFlow(ObserverAwareFinalityFlow(fullySignedTransaction, listOf(flowSession)))

            subFlow(UpdateDistributionListFlow(finalityTransaction))
        }
    }

    /**
     * Use case where both dealer and buyer are on the same node
     */
    inner class PurchaseFestivalTicketFlowWithinNode {
        @Suspendable
        fun call(): SignedTransaction {
            // Create a dealer party for the transaction
            val dealerParty = serviceHub.createKeyForAccount(dealer)

            // Create a buyer party for the transaction
            val buyerParty = serviceHub.createKeyForAccount(buyer)

            val ticketRef = getStateReference(serviceHub, FestivalTicketState::class.java, linearId)
            val ticketReport = ticketRef.state.data
            val ticketPointer = ticketReport.toPointer<FestivalTicketState>()

            val markerType = TokenType("Marker$linearId", 0)
            val marker = markerType issuedBy ourIdentity heldBy ourIdentity

            val token = ticketPointer issuedBy ourIdentity heldBy buyerParty

            val criteria = QueryCriteria.VaultQueryCriteria(
                    status = Vault.StateStatus.UNCONSUMED,
                    externalIds = listOf(buyer.identifier.id)
            )

            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val builder = TransactionBuilder(notary)

            // Add the money for the transaction
            addMoveFungibleTokens(builder, serviceHub, amount, dealerParty, buyerParty, criteria)

            // Issue the token
            addIssueTokens(builder, listOf(token, marker))

            // Create a list of local signatures for the command
            val signers = builder.toLedgerTransaction(serviceHub).ourSigningKeys(serviceHub) + ourIdentity.owningKey

            // Sign off the transaction
            val selfSignedTransaction = serviceHub.signInitialTransaction(builder, signers)

            subFlow(ObserverAwareFinalityFlow(selfSignedTransaction, emptyList()))

            return selfSignedTransaction
        }
    }

    @CordaSerializable
    data class TradeInfo(
            val dealer: AccountInfo,
            val buyer: AccountInfo,
            val price: Amount<TokenType>,
            val token: IssuedTokenType,
            val marker: NonFungibleToken
    )
}