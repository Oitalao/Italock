package br.italo.italock

import br.italo.italock.i18n.L10n
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Textos do app para os testes da JVM (sem Android): lê os XML de res/values* direto do disco e responde no lugar do
 * L10n. [install] usa o português (padrão).
 */
object TestStrings {
    private val resDir = File("src/main/res")

    /** Textos de uma pasta values (ex.: "values", "values-en"): nome → texto; plurais como "nome#one", "nome#other". */
    fun load(folder: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val dir = File(resDir, folder)
        dir.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }?.sorted()?.forEach { f ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val root = doc.documentElement
            val strings = root.getElementsByTagName("string")
            for (i in 0 until strings.length) {
                val e = strings.item(i) as Element
                out[e.getAttribute("name")] = unescape(e.textContent)
            }
            val plurals = root.getElementsByTagName("plurals")
            for (i in 0 until plurals.length) {
                val p = plurals.item(i) as Element
                val items = p.getElementsByTagName("item")
                for (j in 0 until items.length) {
                    val it = items.item(j) as Element
                    out[p.getAttribute("name") + "#" + it.getAttribute("quantity")] = unescape(it.textContent)
                }
            }
        }
        return out
    }

    /** Pastas values com textos (padrão e traduções). */
    fun folders(): List<String> = resDir.listFiles { f -> f.isDirectory && (f.name == "values" || Regex("values-[a-z]{2}(-r[A-Z]{2})?").matches(f.name)) }
        ?.filter { d -> d.listFiles { f -> f.name.startsWith("strings") }?.isNotEmpty() == true }
        ?.map { it.name }?.sorted().orEmpty()

    private fun unescape(raw: String): String {
        val t = raw.trim()
        val s = if (t.length >= 2 && t.startsWith("\"") && t.endsWith("\"")) t.substring(1, t.length - 1) else t
        return unescapeInner(s)
    }

    private fun unescapeInner(s: String) = s.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n").replace("\\@", "@").replace("\\?", "?")

    private fun nameOf(id: Int, plural: Boolean): String {
        val cls = if (plural) R.plurals::class.java else R.string::class.java
        return cls.fields.first { it.getInt(null) == id }.name
    }

    fun install(folder: String = "values") {
        val map = load(folder)
        L10n.testLookup = { id, quantity, args ->
            val text = if (quantity == null) map.getValue(nameOf(id, false))
            else map[nameOf(id, true) + "#" + (if (quantity == 1) "one" else "other")] ?: map.getValue(nameOf(id, true) + "#other")
            if (args.isEmpty()) text else String.format(java.util.Locale.forLanguageTag("pt-BR"), text, *args)
        }
    }
}
