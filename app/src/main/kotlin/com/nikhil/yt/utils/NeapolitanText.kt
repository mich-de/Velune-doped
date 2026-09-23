/*
 * Velune Project
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.nikhil.yt.utils

/**
 * Helps Google Translate with Neapolitan lyrics, a language it does not support. Left alone,
 * Neapolitan lines are detected as Italian (and returned untranslated) or as Corsican.
 * [normalize] rewrites the most common Neapolitan words into Italian, after which the text is
 * translated with Corsican as the source language, the closest one Google knows.
 */
object NeapolitanText {
    /** Source language to send with normalized Neapolitan text. */
    const val PIVOT_LANGUAGE_CODE = "co"

    private val WORDS: Map<String, String> =
        mapOf(
            // articles and elided forms ("na"/"nu" without apostrophe are left alone: "na na na" fillers)
            "'o" to "il", "'a" to "la", "'nu" to "un", "'na" to "una",
            // function words
            "nun" to "non", "cchiù" to "più", "chiù" to "più", "cchiú" to "più",
            "pecché" to "perché", "pecchè" to "perché", "addò" to "dove", "addó" to "dove",
            "quanno" to "quando", "comme" to "come", "accussì" to "così", "accussí" to "così",
            "ccà" to "qua", "llà" to "là", "lloco" to "lì", "mo'" to "ora", "tanno" to "allora",
            "primma" to "prima", "doppo" to "dopo", "ampressa" to "presto", "sempe" to "sempre",
            "maje" to "mai", "dimane" to "domani", "aiere" to "ieri", "ogne" to "ogni",
            "quacche" to "qualche", "quaccosa" to "qualcosa", "nisciuno" to "nessuno",
            "'nzieme" to "insieme", "nzieme" to "insieme", "luntano" to "lontano", "fore" to "fuori",
            "dinto" to "dentro", "dint'" to "dentro", "'ncoppa" to "sopra", "'ncopp'" to "sopra",
            "'nnanze" to "davanti", "arreto" to "dietro", "'mmiezo" to "in mezzo", "'mmiez'" to "in mezzo",
            "pe'" to "per", "cu'" to "con", "cu" to "con", "ca" to "che",
            // pronouns and demonstratives
            "i'" to "io", "je" to "io", "isso" to "lui", "nuje" to "noi", "vuje" to "voi", "lloro" to "loro",
            "chello" to "quello", "chesto" to "questo", "chesta" to "questa", "chisto" to "questo", "chista" to "questa",
            "chisti" to "questi", "chiste" to "queste", "chillo" to "quello", "chella" to "quella",
            "chille" to "quelli", "chelli" to "quelle",
            // verbs ("'o ssaje": the clitic 'o is "lo" and doubles the next consonant)
            "'o ssaje" to "lo sai", "'o ssape" to "lo sa", "'o ssaccio" to "lo so", "ssaje" to "sai",
            "ssape" to "sa", "ssaccio" to "so", "coce" to "brucia", "cuntento" to "contento",
            "cuntenta" to "contenta", "tutt'" to "tutti",
            "aggio" to "ho", "aggia" to "devo", "saccio" to "so", "saje" to "sai", "sape" to "sa",
            "staje" to "stai", "stongo" to "sto", "stammo" to "stiamo", "simmo" to "siamo",
            "songo" to "sono", "so'" to "sono", "si'" to "sei", "tengo" to "ho", "tene" to "ha",
            "tenimmo" to "abbiamo", "avimmo" to "abbiamo", "jammo" to "andiamo", "jate" to "andate",
            "vaco" to "vado", "vaje" to "vai", "veco" to "vedo", "pozzo" to "posso", "faje" to "fai",
            "facimmo" to "facciamo", "dicimmo" to "diciamo", "penzo" to "penso", "pienze" to "pensi",
            "penzà" to "pensare", "chiagne" to "piange", "chiagno" to "piango", "chiagnere" to "piangere",
            "moro" to "muoio", "muorto" to "morto", "perzo" to "perso", "jocano" to "giocano",
            "joca" to "gioca", "scurdà" to "dimenticare", "mangià" to "mangiare", "parlà" to "parlare",
            "turnà" to "tornare", "cantà" to "cantare", "ballà" to "ballare", "durmì" to "dormire",
            "sentì" to "sentire", "siente" to "senti", "chiammà" to "chiamare", "chiamma" to "chiama",
            "vasà" to "baciare", "vaso" to "bacio", "vase" to "baci", "truvà" to "trovare",
            "aspettà" to "aspettare", "guardà" to "guardare", "campà" to "vivere", "'mporta" to "importa",
            // nouns and adjectives
            "capa" to "testa", "guaglione" to "ragazzo", "guagliona" to "ragazza", "guagliune" to "ragazzi",
            "guagliò" to "ragazzo", "piccerillo" to "bambino", "piccerella" to "bambina", "core" to "cuore",
            "ammore" to "amore", "juorno" to "giorno", "munno" to "mondo", "napule" to "Napoli",
            "uocchie" to "occhi", "uocchio" to "occhio", "vocca" to "bocca", "capille" to "capelli",
            "femmena" to "donna", "ommo" to "uomo", "pate" to "padre", "frate" to "fratello",
            "suonno" to "sogno", "dulore" to "dolore", "tammurro" to "tamburo", "'nnammurato" to "innamorato",
            "'nnammurata" to "innamorata", "vico" to "vicolo", "friddo" to "freddo", "cavero" to "caldo",
            "assaje" to "tanto", "vraccio" to "braccio", "vracce" to "braccia", "cuollo" to "collo",
            "pietto" to "petto", "recchie" to "orecchie", "riente" to "denti", "pede" to "piede",
            "'nfaccia" to "in faccia", "niro" to "nero", "nira" to "nera", "nire" to "neri",
            "janco" to "bianco", "janca" to "bianca", "fenesta" to "finestra", "viento" to "vento",
            "sciore" to "fiore", "sciure" to "fiori", "lietto" to "letto", "chiove" to "piove",
            "ciardino" to "giardino", "funtana" to "fontana", "muntagna" to "montagna", "mammà" to "mamma",
            "mugliera" to "moglie", "cumpagno" to "compagno", "surriso" to "sorriso", "apriesso" to "dietro",
        )

    /** Words that practically never appear in standard Italian. */
    private val MARKERS: Set<String> =
        setOf(
            "'o", "'a", "'nu", "'na", "nun", "cchiù", "chiù", "pecché", "pecchè", "addò", "quanno", "comme",
            "accussì", "ccà", "llà", "aggio", "saccio", "staje", "stongo", "simmo", "songo", "jammo",
            "chesto", "chesta", "chisto", "chista", "chillo", "chella", "guaglione", "guagliona", "guagliune",
            "guagliò", "ammore", "juorno", "munno", "napule", "assaje", "nuje", "vuje", "sempe", "maje",
            "uocchie", "'nzieme", "'ncoppa", "'mmiezo", "'mmiez'", "perzo", "penzo", "chiagne", "pe'", "i'",
        )

    // Longest keys first, so "'nnammurato" wins over shorter overlapping keys.
    private val WORD_PATTERN =
        Regex(
            "(?<![\\p{L}\\p{N}'])(" +
                WORDS.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } +
                ")(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE,
        )

    private val TOKEN_PATTERN = Regex("'?[\\p{L}]+'?")

    // "vede'a", "pe'o": an article glued to the previous word by the apostrophe.
    private val GLUED_ELISION = Regex("(\\p{L}{2,})'([aoe])(?![\\p{L}\\p{N}])")

    /** True when enough lines contain words that only Neapolitan uses. */
    fun looksNeapolitan(lines: List<String>): Boolean {
        val textLines = lines.filter { it.isNotBlank() }
        if (textLines.isEmpty()) return false
        val linesWithMarkers =
            textLines.count { line ->
                TOKEN_PATTERN.findAll(unifyApostrophes(line)).any { it.value.lowercase() in MARKERS }
            }
        return linesWithMarkers >= 2 && linesWithMarkers * 5 >= textLines.size
    }

    /** Rewrites common Neapolitan words of [line] into Italian, keeping a leading capital letter. */
    fun normalize(line: String): String =
        WORD_PATTERN.replace(splitGluedElisions(unifyApostrophes(line))) { match ->
            val word = match.value
            val italian = WORDS[word.lowercase()] ?: return@replace word
            val capitalized = word.firstOrNull { it.isLetter() }?.isUpperCase() == true
            if (capitalized) italian.replaceFirstChar { it.uppercaseChar() } else italian
        }

    private fun unifyApostrophes(text: String): String = text.replace('’', '\'').replace('‘', '\'')

    /** Separates glued articles, keeping the apostrophe of known truncated words ("pe'a" -> "pe' 'a"). */
    private fun splitGluedElisions(text: String): String =
        GLUED_ELISION.replace(text) { match ->
            val word = match.groupValues[1]
            val head = if ("${word.lowercase()}'" in WORDS) "$word'" else word
            "$head '${match.groupValues[2]}"
        }
}
