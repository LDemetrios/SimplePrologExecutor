package org.ldemetrios.prolog

import com.github.h0tk3y.betterParse.grammar.parseToEnd
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.*


fun uid() = UUID.randomUUID().toString().map {
    when (it) {
        '-' -> 'g'
        in '0'..'9' -> 'h' + (it - '0')
        else -> it
    }
}.joinToString("")

fun main(args: Array<String>) {
    val file = args.getOrNull(0)?.let(::File) ?: throw IllegalArgumentException("Specify file")
    val content = file.readText()

    val prolog = PrologParser.parseToEnd(content) as PrologFile
    val queries = prolog.declarations.filterIsInstance<Query>()
    val rest = PrologFile(prolog.declarations.filter { it !is Query })

    val sep = uid()
    val rule = uid()
    val auxRule = uid() // , write_term(Goal, [quoted(true)]), write($sep)
    val syntheticQuery = "$rule(Goal), call(Goal), fail; true."
    for (query in queries) {
        println("Query: " + query.clause)
        val newFile = """$rest


$auxRule(X) :- ${query.clause.term.joinToString(", ")}, ${
    query.clause.term.joinToString(", write(\",\"), ") { "write_term($it, [quoted(true)])" }
}, write($sep).
$rule($auxRule(X)).

"""
        val tmp = File.createTempFile("prolog-query-proc", ".pl")
        tmp.writeText(newFile)

        val process = ProcessBuilder("swipl", "-s", tmp.absolutePath).start()
        process.outputStream.bufferedWriter().run {
            write(syntheticQuery)
            close()
        }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            println("\tUnsuccessfully: error code $exitCode")
            process.errorReader().readLines().forEach { println("\t$it") }
        } else {
            val out = process.inputStream.bufferedReader().readText()
            val results = out.split(sep).dropLast(1).joinToString("\n") { "$it." }
//            println(results)
            val res = PrologParser.parseToEnd(results).toString().lines().dropLast(1)
            if (res.size > 0) {
                res.forEach { println("\t$it") }
            } else {
                println("\tfalse")
            }

        }
    }
}

