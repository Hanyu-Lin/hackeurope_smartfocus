package locked.`in`.domain.classifier

import org.json.JSONObject
import java.io.InputStream
import java.text.Normalizer

class BertTokenizer private constructor(
    private val vocab: Map<String, Int>
) {
    companion object {
        private const val MAX_LENGTH = 128
        private const val PAD_ID = 0
        private const val UNK_ID = 100
        private const val CLS_ID = 101
        private const val SEP_ID = 102
        private const val SUBWORD_PREFIX = "##"
        private const val MAX_CHARS_PER_WORD = 100

        fun fromInputStream(stream: InputStream): BertTokenizer {
            val json = JSONObject(stream.bufferedReader().readText())
            val vocabJson = json.getJSONObject("model").getJSONObject("vocab")
            val vocab = HashMap<String, Int>(vocabJson.length())
            for (key in vocabJson.keys()) {
                vocab[key] = vocabJson.getInt(key)
            }
            return BertTokenizer(vocab)
        }
    }

    data class Output(val ids: LongArray, val attentionMask: LongArray)

    fun encode(text: String): Output {
        val normalized = normalize(text)
        val preTokens = preTokenize(normalized)
        val wordPieceIds = mutableListOf<Int>()

        for (token in preTokens) {
            wordPieceIds.addAll(wordPieceTokenize(token))
        }

        val maxContent = MAX_LENGTH - 2
        val truncated = if (wordPieceIds.size > maxContent) wordPieceIds.subList(0, maxContent) else wordPieceIds

        val ids = LongArray(MAX_LENGTH)
        val mask = LongArray(MAX_LENGTH)

        ids[0] = CLS_ID.toLong()
        mask[0] = 1L

        for (i in truncated.indices) {
            ids[i + 1] = truncated[i].toLong()
            mask[i + 1] = 1L
        }

        val sepPos = truncated.size + 1
        ids[sepPos] = SEP_ID.toLong()
        mask[sepPos] = 1L

        return Output(ids, mask)
    }

    private fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            if (ch == '\u0000' || ch == '\uFFFD' || isControl(ch)) continue
            if (isWhitespace(ch)) {
                sb.append(' ')
            } else if (isCjk(ch)) {
                sb.append(' ').append(ch).append(' ')
            } else {
                sb.append(ch)
            }
        }
        val lowered = sb.toString().lowercase()
        return stripAccents(lowered)
    }

    private fun stripAccents(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun preTokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()

        for (ch in text) {
            if (isWhitespace(ch)) {
                if (current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current.clear()
                }
            } else if (isPunctuation(ch)) {
                if (current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current.clear()
                }
                tokens.add(ch.toString())
            } else {
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        return tokens
    }

    private fun wordPieceTokenize(token: String): List<Int> {
        if (token.length > MAX_CHARS_PER_WORD) return listOf(UNK_ID)

        val result = mutableListOf<Int>()
        var start = 0

        while (start < token.length) {
            var matched = false
            var end = token.length

            while (end > start) {
                val substr = if (start == 0) {
                    token.substring(start, end)
                } else {
                    SUBWORD_PREFIX + token.substring(start, end)
                }

                val id = vocab[substr]
                if (id != null) {
                    result.add(id)
                    start = end
                    matched = true
                    break
                }
                end--
            }

            if (!matched) {
                return listOf(UNK_ID)
            }
        }
        return result
    }

    private fun isWhitespace(ch: Char): Boolean =
        ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' || Character.getType(ch) == Character.SPACE_SEPARATOR.toInt()

    private fun isControl(ch: Char): Boolean {
        if (ch == '\t' || ch == '\n' || ch == '\r') return false
        val type = Character.getType(ch)
        return type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt()
    }

    private fun isPunctuation(ch: Char): Boolean {
        val code = ch.code
        if (code in 33..47 || code in 58..64 || code in 91..96 || code in 123..126) return true
        val type = Character.getType(ch)
        return type == Character.DASH_PUNCTUATION.toInt() ||
            type == Character.START_PUNCTUATION.toInt() ||
            type == Character.END_PUNCTUATION.toInt() ||
            type == Character.CONNECTOR_PUNCTUATION.toInt() ||
            type == Character.OTHER_PUNCTUATION.toInt() ||
            type == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
            type == Character.FINAL_QUOTE_PUNCTUATION.toInt()
    }

    private fun isCjk(ch: Char): Boolean {
        val code = ch.code
        return code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF ||
            code in 0x20000..0x2A6DF || code in 0x2A700..0x2B73F ||
            code in 0x2B740..0x2B81F || code in 0x2B820..0x2CEAF ||
            code in 0xF900..0xFAFF || code in 0x2F800..0x2FA1F
    }
}
