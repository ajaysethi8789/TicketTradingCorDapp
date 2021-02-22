package com.r3.demo.nodes.commands

import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.holderString
import com.r3.corda.lib.tokens.money.FiatCurrency
import net.corda.core.contracts.Amount
import com.r3.demo.nodes.Main
import com.r3.ticket.tokens.state.FestivalTicketState
import net.corda.core.contracts.UniqueIdentifier
import java.lang.IllegalArgumentException
import java.lang.StringBuilder
import java.util.regex.Pattern

object Utilities {
    /**
     * Parse the amount value recorded in the text.
     */
    fun getAmount(text: String): Amount<TokenType> {
        val pattern = Pattern.compile("([a-zA-Z$]+)(\\d+)")
        val matcher = pattern.matcher(text)

        if (!matcher.matches()){
            throw IllegalArgumentException("Cannot parse amount $text")
        }

        try {
            var currency = matcher.group(1)
            val amount = matcher.group(2)

            currency = currency.replace("A$", "AUD")
            currency = currency.replace("S$", "SGD")
            currency = currency.replace("$", "USD")

            return Amount(amount.toLong() * 100, FiatCurrency.getInstance(currency))
        } catch (e: Exception) {
            throw IllegalArgumentException("Cannot parse amount $text")
        }
    }

    private fun parseClarity(parameters: String): FestivalTicketState.ClarityScale {
        val pattern = Pattern.compile("[^\\w](VVS1|VVS2|VS1|VS2|VI1|VI2)[^\\w]")
        val matcher = pattern.matcher(parameters)

        if (matcher.find()){
            val clarity = matcher.group(1)
            return FestivalTicketState.ClarityScale.valueOf(clarity)
        }

        throw IllegalArgumentException("No clarity")
    }

    private fun parseCut(parameters: String): String {
        val pattern = Pattern.compile("[^\\w]('.+')[^\\w]")
        val matcher = pattern.matcher(parameters)

        if (matcher.find()){
            return matcher.group(1)
        }

        throw IllegalArgumentException("No cut")
    }

    private fun parseColour(parameters: String): FestivalTicketState.ColorScale {
        val pattern = Pattern.compile("[^\\w]([D-Q])[^\\w]")
        val matcher = pattern.matcher(parameters)

        if (matcher.find()){
            val clarity = matcher.group(1)
            return FestivalTicketState.ColorScale.valueOf(clarity)
        }

        throw IllegalArgumentException("No colour")
    }

    /**
     * Parse the results from the output state matcher to create a new Node.
     * Output state definitions look like (name, owner, amount).
     */
    fun parseReport(main: Main, parameters: String, linearId: UniqueIdentifier = UniqueIdentifier()): FestivalTicketState {
        val pattern = Pattern.compile("\\(\\s*([-\\w]+),\\s*([-\\w]+),\\s*([\\d.]+),\\s*(.+)\\)")
        val matcher = pattern.matcher(parameters)

        if (!matcher.find()){
            throw IllegalArgumentException("Invalid diamond report: $parameters")
        }
        val issuer = matcher.group(1)
        val requester = matcher.group(2)
        val caret = matcher.group(3)
        val stats = "," + matcher.group(4) + ","

        val issuerParty = main.getWellKnownUser(main.retrieveNode(issuer))
        val requesterParty = main.getWellKnownUser(main.retrieveNode(requester))
        val clarity = parseClarity(stats)
        val colour = parseColour(stats)
        val cut = parseCut(stats)

        return FestivalTicketState(caret, colour, clarity, cut, issuerParty, requesterParty, linearId)
    }

    var logtime: Long = -1

    fun logStart(){
        logtime = System.currentTimeMillis()
    }

    fun logFinish(){
        logtime = System.currentTimeMillis() - logtime
    }

    fun logCancel() {
        logtime = -1
    }
}

fun NonFungibleToken.printReport(): String {
    val text = toString()

    if (text.contains("DiamondGradingReport")){
        val id = (this.token.tokenType as TokenPointer<*>).tokenIdentifier.substring(0, 8)
        return "TokenPointer(DiamondGradingReport, ${id}) issued by ${issuer.name.organisation} held by $holderString"
    }
    return text
}

fun FestivalTicketState.printReport(): String {
    val pattern = Pattern.compile(".*O=([\\w\\d]+),.*")
    val m1 = pattern.matcher(this.assessor.toString())
    val m2 = pattern.matcher(this.requester.toString())
    val assessor = if (m1.matches()) m1.group(1) else this.assessor.toString()
    val requester = if (m2.matches()) m2.group(1) else this.requester.toString()

    val builder = StringBuilder("(")
    builder.append(assessor).append(", ")
    builder.append(requester).append(", ")
    builder.append(this.caratWeight).append(", ")
    builder.append(this.clarity).append(", ")
    builder.append(this.color).append(", ")
    builder.append(this.cut).append(')')

    return builder.toString()
}
