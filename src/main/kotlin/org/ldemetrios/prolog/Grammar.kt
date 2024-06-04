package org.ldemetrios.prolog

interface Declaration
data class PrologFile(val declarations: List<Declaration>) {
    override fun toString(): String = declarations.joinToString("\n", "", "\n\n")
}

data class Directive(val term: Term) : Declaration {
    override fun toString(): String = ":- $term."
}

data class Query(val clause: Clause) : Declaration {
    override fun toString(): String = "%?- $clause"
}

data class Clause(val term: Term) : Declaration {
    override fun toString(): String = "$term."
}

interface Term

data class Variable(val name : String) : Term {
    override fun toString(): String = name
}

data class BracedTerm(val term: Term) : Term {
    override fun toString(): String = "($term)"
}

data class IntegerTerm(val value : Long) : Term {
    override fun toString(): String = value.toString()
}

data class FloatTerm(val value : Double) : Term {
    override fun toString(): String = value.toString()
}

data class CompoundTerm(val name: Atom, val args: List<Term>) : Term {
    override fun toString(): String = "$name(${args.joinToString(", ")})"
}

data class BinaryOperator(val left: Term, val op: String, val right: Term) : Term {
    override fun toString(): String = "$left $op $right"
}

data class UnaryOperator(val op: String, val arg: Term) : Term {
    override fun toString(): String = "$op $arg"
}

data class ListTerm(val head : List<Term>, val tail: Term?) : Term {
    override fun toString(): String = if (tail == null) "[${head.joinToString(", ")}]" else "[$head | $tail]"
}

data class CurlyBracketedTerm(val terms: List<Term>) : Term {
    override fun toString(): String = "{ ${terms.joinToString(", ")} }"
}

interface Atom: Term

data object EmptyList : Atom{
    override fun toString(): String = "[]"
}
data object EmptyBraces : Atom{
    override fun toString(): String = "{}"
}
data class Name(val el : String) : Atom{
    override fun toString(): String = el
}
data class Graphic(val el: String) : Atom{
    override fun toString(): String = el
}
data class QuotedString(val el: String) : Atom{
    override fun toString(): String = "'" + el + "'"
}
data class DqString(val el: String) : Atom{
    override fun toString(): String = "\"" + el + "\""
}
data class BackqString(val el:String) : Atom{
    override fun toString(): String = "`" + el + "`"
}
data object Semicolon : Atom{
    override fun toString(): String = ";"
}
data object Cut: Atom{
    override fun toString(): String = "!"
}
