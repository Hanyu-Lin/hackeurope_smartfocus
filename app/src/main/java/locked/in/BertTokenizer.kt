package locked.`in`

import android.content.Context
import org.json.JSONObject
import kotlin.text.iterator

class BertTokenizer(context: Context, tokenizerJsonPath: String = "tokenizer.json") {

    private val vocab: Map<String, Int>
    private val unkTokenId: Int = 100
    private val clsTokenId: Int = 101
    private val sepTokenId: Int = 102
    private val padTokenId: Int = 0
    private val maxLength: Int = 128

    init {
        val json = context.assets.open(tokenizerJsonPath)
            .bufferedReader().readText()
        val root = JSONObject(json)
        val vocabJson = root.getJSONObject("model").getJSONObject("vocab")
        vocab = buildMap {
            vocabJson.keys().forEach { key -> put(key, vocabJson.getInt(key)) }
        }
    }

    // --- Normalizer (BertNormalizer: clean + lowercase) ---

    private fun normalize(text: String): String {
        return buildString {
            for (char in text) {
                val cp = char.code
                if (cp == 0 || cp == 0xfffd || isControl(char)) continue
                if (isChineseCjk(cp)) {
                    append(' '); append(char); append(' ')
                } else if (char.isWhitespace()) {
                    append(' ')
                } else {
                    append(char)
                }
            }
        }.lowercase()
    }

    private fun isControl(char: Char): Boolean {
        if (char == '\t' || char == '\n' || char == '\r') return false
        return char.category in setOf(
            CharCategory.CONTROL, CharCategory.FORMAT,
            CharCategory.SURROGATE, CharCategory.PRIVATE_USE
        )
    }

    private fun isChineseCjk(cp: Int): Boolean {
        return (cp in 0x4E00..0x9FFF) || (cp in 0x3400..0x4DBF) ||
                (cp in 0x20000..0x2A6DF) || (cp in 0x2A700..0x2B73F) ||
                (cp in 0x2B740..0x2B81F) || (cp in 0x2B820..0x2CEAF) ||
                (cp in 0xF900..0xFAFF) || (cp in 0x2F800..0x2FA1F)
    }

    // --- Pre-tokenizer (BertPreTokenizer: whitespace + punctuation split) ---

    private fun pretokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        for (char in text) {
            when {
                char.isWhitespace() -> {
                    if (current.isNotEmpty()) { tokens.add(current.toString()); current.clear() }
                }
                isPunctuation(char) -> {
                    if (current.isNotEmpty()) { tokens.add(current.toString()); current.clear() }
                    tokens.add(char.toString())
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }

    private fun isPunctuation(char: Char): Boolean {
        val cp = char.code
        if (cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126) return true
        return char.category in setOf(
            CharCategory.CONNECTOR_PUNCTUATION, CharCategory.DASH_PUNCTUATION,
            CharCategory.START_PUNCTUATION, CharCategory.END_PUNCTUATION,
            CharCategory.INITIAL_QUOTE_PUNCTUATION, CharCategory.FINAL_QUOTE_PUNCTUATION,
            CharCategory.OTHER_PUNCTUATION, CharCategory.MATH_SYMBOL,
            CharCategory.CURRENCY_SYMBOL, CharCategory.MODIFIER_SYMBOL, CharCategory.OTHER_SYMBOL
        )
    }

    // --- WordPiece model ---

    private fun wordpieceTokenize(word: String): List<Int> {
        if (word.length > 100) return listOf(unkTokenId)
        val result = mutableListOf<Int>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var found: Int? = null
            while (start < end) {
                val substr = (if (start > 0) "##" else "") + word.substring(start, end)
                val id = vocab[substr]
                if (id != null) { found = id; break }
                end--
            }
            if (found == null) return listOf(unkTokenId)
            result.add(found)
            start = end
        }
        return result
    }

    // --- Public API ---

    data class Encoding(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray
    )

    fun encode(text: String): Encoding {
        val normalized = normalize(text)
        val words = pretokenize(normalized)

        val tokenIds = mutableListOf<Int>()
        for (word in words) {
            tokenIds.addAll(wordpieceTokenize(word))
        }

        // Truncate to maxLength - 2 (for [CLS] and [SEP])
        val truncated = tokenIds.take(maxLength - 2)

        // Post-process: [CLS] + tokens + [SEP]
        val finalIds = mutableListOf(clsTokenId) + truncated + listOf(sepTokenId)

        // Pad to maxLength
        val inputIds = LongArray(maxLength) { i ->
            if (i < finalIds.size) finalIds[i].toLong() else padTokenId.toLong()
        }
        val attentionMask = LongArray(maxLength) { i ->
            if (i < finalIds.size) 1L else 0L
        }
        val tokenTypeIds = LongArray(maxLength) { 0L }

        return Encoding(inputIds, attentionMask, tokenTypeIds)
    }
}