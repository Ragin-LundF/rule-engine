package ruleengine.cli

/**
 * The argument shape both command-line entry points accept: `--flag value` pairs and bare `--flag`
 * switches, in any order.
 *
 * A flag's value is the next argument unless that argument is itself a `--`-prefixed flag, in which
 * case the flag is a switch and maps to `null`. A repeated flag keeps its last occurrence.
 */
object CliArguments {

    private const val FLAG_PREFIX = "--"

    fun parse(args: Array<String>): Map<String, String?> {
        val valuesByFlag = mutableMapOf<String, String?>()
        var index = 0

        while (index < args.size) {
            val flag = args[index]
            val next = args.getOrNull(index = index + 1)
            val value = if (next != null && !next.startsWith(prefix = FLAG_PREFIX)) next else null

            valuesByFlag[flag] = value
            index += if (value != null) 2 else 1
        }

        return valuesByFlag
    }
}
