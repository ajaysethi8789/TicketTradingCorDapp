package com.r3.demo.nodes.commands

import net.corda.core.messaging.startTrackedFlow
import com.r3.demo.nodes.Main
import com.r3.ticket.tokens.flows.RedeemFestivalTicketFlow

/**
 * Implement the redeem command
 */
class RedeemDiamond : Command {
    companion object {
        const val COMMAND = "redeem"
    }

    /**
     * Execute the move command. Moves an issue between users.
     *
     * @param main execution context
     * @param array list of command plus arguments
     * @param parameters original command line
     */
    override fun execute(main: Main, array: kotlin.collections.List<String>, parameters: String): Iterator<String> {
        if (array.size < 5){
            return help().listIterator()
        }

        // Get the user who is meant to invoke the command
        val owner = main.retrieveAccount(array[1])
        val dealer = main.retrieveAccount(array[2])
        val node = main.retrieveNode(owner)
        val connection = main.getConnection(node)
        val service = connection.proxy
        val tokenId = main.retrieveState(array[3]) ?: throw IllegalArgumentException("Token ID ${array[3]} not found")

        val amount = Utilities.getAmount(array[4])

        Utilities.logStart()

        service.startTrackedFlow(::RedeemFestivalTicketFlow, tokenId, owner, dealer, amount).returnValue.get()

        Utilities.logFinish()

        // Display the new list of unconsumed states
        val nodes = ListAccount()
        val text = "list ${array[1]}"

        return nodes.execute(main, text.split(" "), text)
    }

    override fun name(): String {
        return COMMAND
    }

    override fun description(): String {
        return "Redeem a diamond token for cash"
    }

    override fun help(): kotlin.collections.List<String> {
        return listOf("usage: redeem owner dealer token-id payment")
    }
}