package br.italo.italock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Traduções do celular e do relógio conferidas contra o português (padrão): mesmos textos, mesmos campos (%1$s...),
 * apóstrofo escapado e nada de travessão, meia risca ou hífen usado como pontuação.
 */
class TranslationsTest {
    private val modules = listOf(File("src/main/res"), File("../wear/src/main/res"))
    private val placeholder = Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[sdfx]")

    /** nome (plural: nome#quantidade) → texto cru do XML; só os traduzíveis. */
    private fun read(dir: File): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        dir.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }?.sorted()?.forEach { f ->
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f).documentElement
            val strings = root.getElementsByTagName("string")
            for (i in 0 until strings.length) {
                val e = strings.item(i) as Element
                if (e.getAttribute("translatable") == "false") continue
                out[f.name + ":" + e.getAttribute("name")] = e.textContent
            }
            val plurals = root.getElementsByTagName("plurals")
            for (i in 0 until plurals.length) {
                val p = plurals.item(i) as Element
                val items = p.getElementsByTagName("item")
                for (j in 0 until items.length) {
                    val it = items.item(j) as Element
                    out[f.name + ":" + p.getAttribute("name") + "#" + it.getAttribute("quantity")] = it.textContent
                }
            }
        }
        return out
    }

    private fun translations(res: File) = res.listFiles { f -> f.isDirectory && Regex("values-[a-z]{2}(-r[A-Z]{2})?").matches(f.name) }
        ?.filter { d -> d.listFiles { f -> f.name.startsWith("strings") }?.isNotEmpty() == true }.orEmpty().sortedBy { it.name }

    private fun tokens(s: String) = placeholder.findAll(s).map { it.value }.sorted().toList()

    @Test fun allTwelveLanguages() {
        for (res in modules) {
            val names = translations(res).map { it.name }.toSet()
            assertEquals("$res", setOf("values-ar", "values-de", "values-en", "values-es", "values-fr", "values-hi", "values-it",
                "values-ja", "values-ko", "values-pl", "values-ru", "values-zh-rCN"), names)
        }
    }

    @Test fun sameStringsAndPlaceholders() {
        val problems = mutableListOf<String>()
        for (res in modules) {
            val base = read(File(res, "values"))
            val baseSingles = base.keys.filter { '#' !in it }.toSet()
            val basePlurals = base.keys.filter { '#' in it }.map { it.substringBefore('#') }.toSet()
            for (dir in translations(res)) {
                val tr = read(dir)
                val singles = tr.keys.filter { '#' !in it }.toSet()
                val plurals = tr.keys.filter { '#' in it }.map { it.substringBefore('#') }.toSet()
                (baseSingles - singles).forEach { problems += "${dir.name}: falta $it" }
                (singles - baseSingles).forEach { problems += "${dir.name}: sobra $it" }
                (basePlurals - plurals).forEach { problems += "${dir.name}: falta plural $it" }
                for (k in singles intersect baseSingles) {
                    if (tokens(tr.getValue(k)) != tokens(base.getValue(k))) problems += "${dir.name}: campos diferentes em $k"
                }
                // Plural: cada forma tem os mesmos campos da forma "other" do português.
                for (k in tr.keys.filter { '#' in it }) {
                    val ref = base[k.substringBefore('#') + "#other"] ?: continue
                    if (tokens(tr.getValue(k)).toSet() != tokens(ref).toSet()) problems += "${dir.name}: campos diferentes em $k"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun styleRules() {
        val problems = mutableListOf<String>()
        for (res in modules) for (dir in listOf(File(res, "values")) + translations(res)) {
            for ((k, v) in read(dir)) {
                if (v.contains('—') || v.contains('–') || v.contains(" - ")) problems += "${dir.name}: traço em $k: $v"
                if (Regex("(?<!\\\\)'").containsMatchIn(v)) problems += "${dir.name}: apóstrofo sem escape em $k"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
