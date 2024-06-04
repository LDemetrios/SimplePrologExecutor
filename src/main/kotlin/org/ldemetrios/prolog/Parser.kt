package org.ldemetrios.prolog

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.*
import com.github.h0tk3y.betterParse.parser.Parser
import com.github.h0tk3y.betterParse.parser.parseToEnd

const val WS = "[ \\t\\r\\n]+"
const val SOLO = "[!(),;\\[\\]{}|%]"
const val HEX_DIGIT = "[0-9a-fA-F]"
const val DIGIT = "[0-9]"
const val CAPITAL_LETTER = "[A-Z]"
const val SMALL_LETTER = "[a-z_]"
const val ALPHA = "_|($SMALL_LETTER)|($CAPITAL_LETTER)"
const val ALPHANUMERIC = "($ALPHA)|($DIGIT)"
const val CONTINUATION_ESCAPE = "\\Q\\\n\\E" // Regex.escape("\\\n")
const val HEX_ESCAPE = "\\\\x($HEX_DIGIT)+\\\\"
const val OCTAL_ESCAPE = "\\\\[0-7]+\\\\"
const val CONTROL_ESCAPE = "\\\\[abcrftnv]"
const val META_ESCAPE = "\\\\['\"`]"
const val GRAPHIC = "[#\$&*+./:<=>?@^~-]"
const val NON_QUOTE_CHAR =
    "($GRAPHIC)|($ALPHANUMERIC)|($SOLO)| |($META_ESCAPE)|($CONTROL_ESCAPE)|($OCTAL_ESCAPE)|($HEX_ESCAPE)"
const val BACK_QUOTED_CHARACTER = "($NON_QUOTE_CHAR)|['\"]|``"
const val DOUBLE_QUOTED_CHARACTER = "($NON_QUOTE_CHAR)|['`]|\"\""
const val SINGLE_QUOTED_CHARACTER = "($NON_QUOTE_CHAR)|[\"`]|``"
const val GRAPHIC_TOKEN = ("(($GRAPHIC)|\\\\)+")
const val FLOAT = "[0-9]+\\.[0-9]+([eE][+-]?[0-9])?"
const val CHARACTER_CODE_CONSTANT = "0'($SINGLE_QUOTED_CHARACTER)"
const val HEX = "0x($HEX_DIGIT)+"
const val OCTAL = "0o[0-7]+"
const val BINARY = "0b[01]+"
const val DECIMAL = "[0-9]+"
const val VARIABLE = "($CAPITAL_LETTER)($ALPHANUMERIC)*|_($ALPHANUMERIC)+|_"
const val LETTER_DIGIT = "($SMALL_LETTER)($ALPHANUMERIC)*"

const val QUOTED = "'(($CONTINUATION_ESCAPE)|($SINGLE_QUOTED_CHARACTER))*?'"
const val DOUBLE_QUOTED_LIST = "\"(($CONTINUATION_ESCAPE)|($DOUBLE_QUOTED_CHARACTER))*?\""
const val BACK_QUOTED_STRING = "`(($CONTINUATION_ESCAPE)|($BACK_QUOTED_CHARACTER))*?`"

object PrologParser : Grammar<PrologFile>() {
    val queryPref by regexToken("%\\?-")
    val lBr by literalToken("[")
    val rBr by literalToken("]")
    val `{` by literalToken("{")
    val `}` by literalToken("}")
    val `(` by literalToken("(")
    val `)` by literalToken(")")
    val semicolon by literalToken(";")
    val `!` by literalToken("!")
    val `,` by literalToken(",")
    val bar by literalToken("|")
    val dot by literalToken(".")
    val hbSep by literalToken(":-")

    val decimal by regexToken(DECIMAL)
    val charCodeConst by regexToken(CHARACTER_CODE_CONSTANT)
    val binary by regexToken(BINARY)
    val octal by regexToken(OCTAL)
    val hex by regexToken(HEX)

    val floatRegex by regexToken(FLOAT)

    val otherOperator by listOf(
        "-->", "?-", "dynamic", "multifile", "discontiguous", "public",
        "->", "\\+", "\\==", "==", "=", "\\=", "@<", "@=<", "@>", "@>=", "=..",
        "is", "=:=", "=\\=", "=<", ">=", ":", "+", "/\\", "\\/",
        "//", "/", "rem", "mod", "<<", "<", ">>", ">", "**", "*", "^", "\\"
    ).map(Regex::escape).joinToString("|").let(::regexToken)

    val `-` by literalToken("-")

    val integer by (decimal use { text.toLong() }) or
            (charCodeConst use { QuotedGrammar.parseToEnd(text.substring(2))[0].code.toLong() }) or
            (binary use { text.substring(2).toLong(2) }) or
            (octal use { text.substring(1).toLong(8) }) or
            (hex use { text.substring(2).toLong(16) })

    val float by floatRegex use { text.toDouble() }

    val operatorWithoutCommas by (otherOperator or hbSep or semicolon or `-`) use { text }
    val operator by (operatorWithoutCommas or (`,` use { text }))

    val variable by regexToken(VARIABLE)

    val letterDigit by regexToken(LETTER_DIGIT);
    val graphicToken by regexToken(GRAPHIC_TOKEN);

    val quotedRegex by regexToken(QUOTED)
    val doubleQuotedListRegex by regexToken(DOUBLE_QUOTED_LIST)
    val backQuotedStringRegex by regexToken(BACK_QUOTED_STRING)
    val quoted by quotedRegex use { QuotedString(QuotedGrammar.parseToEnd(text.substring(1, text.length - 1))) }
    val doubleQuotedList by doubleQuotedListRegex use {
        DqString(DoubleQuotedListGrammar.parseToEnd(text.substring(1, text.length - 1)))
    }
    val backQuotedString by backQuotedStringRegex use {
        BackqString(BackQuotedStringGrammar.parseToEnd(text.substring(1, text.length - 1)))
    }

    val atom by
//    ((lBr * rBr) use { EmptyList }) or
//            ((`{` * `}`) use { EmptyBraces }) or
            (letterDigit use { Name(text) }) or
            (graphicToken use { Graphic(text) }) or
            quoted or doubleQuotedList or backQuotedString or
            (semicolon use { Semicolon }) or
            (`!` use { Cut })

    val termOperand: Parser<Term> by (variable use { Variable(text) }) or
            ((-`(` * parser(::term) * -`)`) use ::BracedTerm) or
            ((optional(`-`) * integer) use { IntegerTerm(if (t1 == null) t2 else -t2) }) or
            ((optional(`-`) * float) use { FloatTerm(if (t1 == null) t2 else -t2) }) or
            (atom * -`(` * parser(::termlist) * -`)` use { CompoundTerm(t1, t2) }) or
            ((operator * parser(::term)) use { UnaryOperator(t1, t2) }) or
            ((-lBr * parser(::termlist) * optional(-bar * parser(::term)) * -rBr) use { ListTerm(t1, t2) }) or
            ((-`{` * parser(::termlist) * -`}`) use ::CurlyBracketedTerm) or
            atom

    val term by rightAssociative(parser(::termOperand), operator, ::BinaryOperator)
    val termWithoutCommas by rightAssociative(parser(::termOperand), operatorWithoutCommas, ::BinaryOperator)

    val termlist by separated(termWithoutCommas, `,`, acceptZero = true) use { terms }

    val clause by term * -dot use ::Clause
    val directive by -hbSep * term * -dot use ::Directive
    val query by -queryPref * clause use ::Query

    override val rootParser: Parser<PrologFile> by zeroOrMore(directive or clause or query) use ::PrologFile


    val comment by regexToken("%([^\n\r?]|\\?[^\n\r-])[^\n\r]*[\r\n]", ignore = true)
    val ws by regexToken(WS, ignore = true)

//    override val rootParser by termOperand
}

fun quotedGrammar(forSingle: String, forDouble: String, forBack: String) = object : Grammar<String>() {
    val continuationEscape by literalToken("\\\n")
    val graphic by regexToken(GRAPHIC)
    val alphanumeric by regexToken(ALPHANUMERIC)
    val solo by regexToken(SOLO)
    val space by literalToken(" ")
    val metaDouble by literalToken("\\\"")
    val metaQuote by literalToken("\\'")
    val metaBacktick by literalToken("\\`")
    val ctrlA by literalToken("\\a")
    val ctrlB by literalToken("\\b")
    val ctrlC by regexToken("\\\\c[ \r\n\t]+")
    val ctrlR by literalToken("\\r")
    val ctrlF by literalToken("\\f")
    val ctrlT by literalToken("\\t")
    val ctrlN by literalToken("\\n")
    val ctrlV by literalToken("\\v")
    val octalEscape by regexToken(OCTAL_ESCAPE)
    val hexEscape by regexToken(HEX_ESCAPE)

    val doubleQuote by literalToken(forDouble) // " or ""
    val backtick by literalToken(forBack) // ` or ``
    val quoteEscape by literalToken(forSingle) // ' or ''

    val controls by (metaDouble use { "\"" }) or
            (metaQuote use { "'" }) or
            (metaBacktick use { "`" }) or
            (ctrlA use { "\u0007" }) or
            (ctrlB use { "\b" }) or
            (ctrlC use { "" }) or
            (ctrlR use { "\r" }) or
            (ctrlF use { "\u000C" }) or
            (ctrlT use { "\t" }) or
            (ctrlN use { "\n" }) or
            (ctrlV use { "\u000B" }) or
            (octalEscape use { text.substring(1, text.length - 1).toInt(8).toChar() }) or
            (hexEscape use { text.substring(2, text.length - 1).toInt(16).toChar() })

    val nonQuoteChar by (graphic use { text }) or
            (alphanumeric use { text }) or
            (solo use { text }) or
            (space use { " " }) or
            controls

    val char by (continuationEscape use { "\n" }) or
            nonQuoteChar or
            (doubleQuote use { "\"" }) or
            (quoteEscape use { "'" }) or
            (backtick use { "`" })

    override val rootParser: Parser<String> by zeroOrMore(char) use { this.joinToString("") }
}

val QuotedGrammar = quotedGrammar("''", "\"", "`")
val DoubleQuotedListGrammar = quotedGrammar("'", "\"\"", "`")
val BackQuotedStringGrammar = quotedGrammar("'", "\"", "``")

fun main() = println(
    ListTerm(listOf(Variable("H")), Variable("T"))
)