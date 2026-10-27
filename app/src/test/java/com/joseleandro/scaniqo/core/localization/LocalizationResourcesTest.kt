package com.joseleandro.scaniqo.core.localization

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class LocalizationResourcesTest {
    private val resources = File("src/main/res")
    private val directories = listOf("values-pt", "values-en", "values-es", "values-fr",
        "values-hi", "values-in", "values-bn", "values-zh-rCN", "values-ar", "values-ru")
    private val placeholders = Regex("%(?:[0-9]+\\$)?[-#+ 0,(]*[0-9]*(?:\\.[0-9]+)?[a-zA-Z]|\\{(?:pagina|total)\\}")
    private data class Entry(val type: String, val text: String, val quantities: Map<String, String>,
        val formatted: Boolean)

    private fun read(directory: String): Map<String, Entry> {
        val result = mutableMapOf<String, Entry>()
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        requireNotNull(File(resources, directory).listFiles()).filter { it.extension == "xml" }.forEach { file ->
            val children = factory.newDocumentBuilder().parse(file).documentElement.childNodes
            for (i in 0 until children.length) {
                val element = children.item(i) as? Element ?: continue
                if (element.tagName !in setOf("string", "plurals") || element.getAttribute("translatable") == "false") continue
                val name = element.getAttribute("name")
                val quantities = mutableMapOf<String, String>()
                if (element.tagName == "plurals") {
                    val items = element.getElementsByTagName("item")
                    for (j in 0 until items.length) {
                        val item = items.item(j) as Element
                        quantities[item.getAttribute("quantity")] = item.textContent
                    }
                }
                assertNull("Duplicate $name in $directory", result.put(name,
                    Entry(element.tagName, element.textContent, quantities,
                        element.getAttribute("formatted") != "false")))
            }
        }
        return result
    }

    private fun tokens(text: String) =
        placeholders.findAll(text.replace("%%", "")).map { it.value }.sorted().toList()

    @Test fun everyLanguageHasAllResourcesAndPreservesFormattingArguments() {
        val base = read("values")
        assertTrue("Expected the complete application catalog", base.size > 1100)
        directories.forEach { directory ->
            val translated = read(directory)
            assertTrue("$directory missing: ${base.keys - translated.keys}; extra: ${translated.keys - base.keys}",
                base.keys == translated.keys)
            base.forEach { (key, original) ->
                val local = translated.getValue(key)
                assertEquals("$directory/$key type", original.type, local.type)
                assertTrue("$directory/$key is blank", local.text.isNotBlank())
                assertEquals("$directory/$key formatted", original.formatted, local.formatted)
                if (original.type == "string") {
                    if (original.formatted) {
                        assertEquals("$directory/$key arguments", tokens(original.text), tokens(local.text))
                    }
                } else {
                    val expected = tokens(original.quantities.getValue("other"))
                    assertTrue("$directory/$key must define other", "other" in local.quantities)
                    local.quantities.forEach { (quantity, text) ->
                        // Counts can be implicit in grammatical singular/dual forms.
                        val actual = tokens(text)
                        assertTrue("$directory/$key/$quantity unexpected argument", actual.all { it in expected })
                        assertEquals("$directory/$key/$quantity non-count arguments",
                            expected.filterNot { it.endsWith("d") }, actual.filterNot { it.endsWith("d") })
                    }
                }
            }
        }
    }

    @Test fun pluralCategoriesCoverArabicRussianAndRomanceLanguages() {
        mapOf(
            "values-ar" to setOf("zero", "one", "two", "few", "many", "other"),
            "values-ru" to setOf("one", "few", "many", "other"),
            "values-es" to setOf("one", "many", "other"),
            "values-fr" to setOf("one", "many", "other"),
            "values-pt" to setOf("one", "many", "other"),
        ).forEach { (directory, required) ->
            read(directory).filterValues { it.type == "plurals" }.forEach { (key, entry) ->
                assertTrue("$directory/$key missing $required", entry.quantities.keys.containsAll(required))
            }
        }
    }

    @Test fun pickerAndSystemSettingsExposeTheSameTenRegions() {
        val entries = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resources, "xml/locales_config.xml")).getElementsByTagName("locale")
        val tags = (0 until entries.length).map { (entries.item(it) as Element).getAttribute("android:name") }
        assertEquals(10, tags.size)
        assertEquals(tags.toSet(), AppLanguages.supported.map { it.tag }.toSet())
        AppLanguages.supported.forEach {
            assertTrue(it.nativeName.isNotBlank())
            assertEquals(2, it.flag.codePointCount(0, it.flag.length))
            assertEquals(it, AppLanguages.find(it.tag))
        }
        assertNull(AppLanguages.find(""))
        assertNull(AppLanguages.find("ja-JP"))
    }
}
