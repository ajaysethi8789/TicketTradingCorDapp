package com.r3.demo.nodes.commands

import net.corda.core.messaging.startTrackedFlow
import com.r3.ticket.tokens.flows.CreateFestivalTicketFlow
import com.r3.demo.nodes.Main
import com.r3.ticket.tokens.state.FestivalTicketState

/**
 * Implement the issue command
 */
class CreateReport : Command {
    companion object {
        const val COMMAND = "create-report"
    }

    /**
     * Execute the report command. Creates a diamond
     * grading report on the ledger.
     * (issuer, dealer, caret, clarity, colour, cut)
     *
     * @param main execution context
     * @param array list of command plus arguments
     * @param parameters original command line
     */
    override fun execute(main: Main, array: List<String>, parameters: String): Iterator<String> {
        if (array.size < 2){
            return help().listIterator()
        }

        // Get the user who is meant to invoke the command
        val node = main.retrieveNode(array[1])

        if (!node.isCertifier()){
            return listOf("Only graders are allowed to create reports").listIterator()
        }

        val connection = main.getConnection(node)
        val service = connection.proxy
        val report = Utilities.parseReport(main, parameters)

        Utilities.logStart()

        val tx = service.startTrackedFlow(::CreateFestivalTicketFlow, report).returnValue.get()

        Utilities.logFinish()

        val list = tx.tx.outputStates.filterIsInstance(FestivalTicketState::class.java)
                .map { it.linearId }
                .onEach { main.registerState(it.toString(), it) }
                .map { it.toString() }

        return list.listIterator()
    }

    override fun name(): String {
        return COMMAND
    }

    override fun help(): List<String> {
        return listOf("usage: create-report issuer (issuer, dealer, caret, clarity, colour, cut)")
    }

    override fun description(): String {
        return "Create a diamond grade report"
    }
}