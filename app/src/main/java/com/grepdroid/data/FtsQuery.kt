package com.grepdroid.data

/**
 * Sanitizes free-text input for SQLite FTS4 `MATCH` queries.
 *
 * Raw user input breaks FTS4 because several characters are query syntax
 * (`"`, `*`, `(`, `)`, `:`, a leading `-`, ...). The smallest mistyped term
 * raises "malformed MATCH expression" as a [android.database.sqlite.SQLiteException],
 * which — coming from inside a Room Flow — kills the search pipeline until the
 * query changes again.
 *
 * Room's default `simple` tokenizer treats any character that is not a Unicode
 * letter or digit as a token separator, so non-alphanumeric characters carry no
 * search value anyway. We therefore keep only letters/digits per token and
 * append a trailing `*` to the final token so partial typing still prefix-matches.
 *
 *   "hello world"  ->  hello world*
 *   don't          ->  dont*
 *   a*b (c)        ->  ab c*
 */
object FtsQuery {

    /**
     * Returns an injection-safe FTS4 MATCH expression for [query], or null when
     * the query contains no usable term (the caller should emit no results
     * rather than run a meaningless MATCH).
     */
    fun sanitize(query: String): String? {
        val tokens = query
            .split("\\s+".toRegex())
            .mapNotNull { raw -> raw.filter { it.isLetterOrDigit() }.takeIf { it.isNotEmpty() } }

        if (tokens.isEmpty()) return null

        return tokens.mapIndexed { index, token ->
            if (index == tokens.lastIndex) "$token*" else token
        }.joinToString(" ")
    }
}
