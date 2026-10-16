package app.folio.core.speech

/**
 * Spells digit runs in [text] as Russian cardinal numbers (nominative), since Silero reads only letters:
 * «в 1812 году» → «в тысяча восемьсот двенадцать году». Decimals read as «три целых четырнадцать»;
 * runs too long for a number are read digit by digit. Case agreement is out of scope.
 */
internal fun spellNumbers(text: String): String =
    NUMBER.replace(text) { m ->
        val (whole, fraction) = m.groupValues[1] to m.groupValues[2]
        val spelled = spellInt(whole)
        if (fraction.isEmpty()) spelled else "$spelled целых ${spellInt(fraction)}"
    }

private val NUMBER = Regex("(\\d+)(?:[.,](\\d+))?")

private fun spellInt(digits: String): String {
    if (digits.length > 12) return digits.map { ONES[it - '0'].ifEmpty { "ноль" } }.joinToString(" ")
    val n = digits.toLong()
    if (n == 0L) return "ноль"
    val parts = mutableListOf<String>()
    var rest = n
    for ((scale, forms, feminine) in SCALES) {
        val group = (rest / scale).toInt()
        rest %= scale
        if (group == 0) continue
        // «тысяча», not «одна тысяча»; «один миллион» keeps its «один».
        if (!(group == 1 && feminine)) parts += hundreds(group, feminine)
        parts += forms[plural(group)]
    }
    if (rest > 0) parts += hundreds(rest.toInt(), feminine = false)
    return parts.joinToString(" ")
}

private fun hundreds(n: Int, feminine: Boolean): String {
    val words = mutableListOf<String>()
    if (n >= 100) words += HUNDREDS[n / 100]
    val r = n % 100
    when {
        r in 10..19 -> words += TEENS[r - 10]
        else -> {
            if (r >= 20) words += TENS[r / 10]
            val one = r % 10
            if (one > 0) words += if (feminine && one <= 2) FEMININE[one] else ONES[one]
        }
    }
    return words.joinToString(" ")
}

/** Form index for a count: 0 «тысяча», 1 «тысячи», 2 «тысяч». */
private fun plural(n: Int): Int {
    val r100 = n % 100
    val r10 = n % 10
    return when {
        r100 in 11..14 -> 2
        r10 == 1 -> 0
        r10 in 2..4 -> 1
        else -> 2
    }
}

private val SCALES = listOf(
    Triple(1_000_000_000L, listOf("миллиард", "миллиарда", "миллиардов"), false),
    Triple(1_000_000L, listOf("миллион", "миллиона", "миллионов"), false),
    Triple(1_000L, listOf("тысяча", "тысячи", "тысяч"), true),
)
private val ONES = listOf("", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять")
private val FEMININE = listOf("", "одна", "две")
private val TEENS = listOf("десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать", "пятнадцать",
    "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать")
private val TENS = listOf("", "", "двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто")
private val HUNDREDS = listOf("", "сто", "двести", "триста", "четыреста", "пятьсот", "шестьсот", "семьсот", "восемьсот", "девятьсот")
