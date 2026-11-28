/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package buildlogic

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element

/** 编译成类型化的同步文案，UI 与后台共用，避免访问 Compose 内部语言覆盖 API。 */
@CacheableTask
abstract class GenerateAppStrings : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalogs: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    private data class Entry(val forms: Map<String, String>, val plural: Boolean, val array: List<String>? = null)

    private val token = Regex("%(?:([1-9][0-9]*)\\$([sd])|(%))")
    private val identifier = Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")

    private fun Element.elements(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

    private fun read(file: File): Map<String, Entry> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val root = factory.newDocumentBuilder().parse(file).documentElement
        require(root.tagName == "resources") { "$file: expected resources" }
        return buildMap {
            for (item in root.elements()) {
                val name = item.getAttribute("name")
                require(identifier.matches(name) && name !in this) { "$file: invalid or duplicate key $name" }
                require(item.tagName in setOf("string", "plurals", "string-array")) { "$file: unsupported resource ${item.tagName}" }
                if (item.tagName == "string-array") {
                    val values = item.elements().map { child ->
                        require(child.tagName == "item" && child.elements().isEmpty()) { "$file: $name requires plain item elements" }
                        val text = child.textContent.replace("\\n", "\n").replace("\\t", "\t")
                        require(text.isNotBlank() && placeholders(text).isEmpty()) { "$file: $name requires nonblank items without placeholders" }
                        text
                    }
                    require(values.isNotEmpty() && values.distinct().size == values.size) { "$file: $name must have nonempty, unique array items" }
                    put(name, Entry(emptyMap(), plural = false, array = values))
                    continue
                }
                val plural = item.tagName == "plurals"
                val forms = if (plural) {
                    val children = item.elements()
                    val values = children.associate { it.getAttribute("quantity") to it.textContent }
                    require(children.size == values.size && values.keys.all { it == "one" || it == "other" } && "other" in values) {
                        "$file: $name must have unique one/other plural forms"
                    }
                    values
                } else {
                    require(item.elements().isEmpty()) { "$file: escape markup in $name" }
                    mapOf("other" to item.textContent)
                }
                put(name, Entry(forms.mapValues { (_, text) -> text.replace("\\n", "\n").replace("\\t", "\t") }, plural))
            }
        }
    }

    private fun placeholders(text: String): Map<Int, String> = buildMap {
        for (match in token.findAll(text)) {
            if (match.groupValues[3].isNotEmpty()) continue
            val position = match.groupValues[1].toInt()
            val type = match.groupValues[2]
            require(put(position, type) in listOf(null, type)) { "Conflicting placeholder: $text" }
        }
        require(!Regex("%(?:[0-9]+\\$)?[sd]").containsMatchIn(token.replace(text, ""))) { "Use numbered placeholders: $text" }
    }

    private fun camel(name: String): String = name.split('_').let { parts -> parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) } }

    private fun literal(text: String, plural: Boolean): String = buildString {
        append('"')
        var from = 0
        fun appendText(value: String) {
            for (char in value) append(when (char) {
                '\\' -> "\\\\"
                '"' -> "\\\""
                '$' -> "\\$"
                '\n' -> "\\n"
                '\r' -> "\\r"
                '\t' -> "\\t"
                else -> char.toString()
            })
        }
        for (match in token.findAll(text)) {
            appendText(text.substring(from, match.range.first))
            if (match.groupValues[3].isNotEmpty()) {
                append('%')
            } else {
                val position = match.groupValues[1].toInt()
                append("\${").append(if (plural && position == 1) "count" else "arg$position").append('}')
            }
            from = match.range.last + 1
        }
        appendText(text.substring(from))
        append('"')
    }

    @TaskAction
    fun generate() {
        val root = catalogs.get().asFile
        val englishFiles = root.resolve("values").listFiles { f -> f.extension == "xml" }.orEmpty().sortedBy(File::getName)
        val chineseFiles = root.resolve("values-zh").listFiles { f -> f.extension == "xml" }.orEmpty()
        require(englishFiles.isNotEmpty() && englishFiles.map(File::getName).toSet() == chineseFiles.map(File::getName).toSet()) { "English and Chinese catalog files differ" }
        val generated = linkedMapOf<String, String>()
        for (file in englishFiles) {
            require(identifier.matches(file.nameWithoutExtension)) { "Invalid catalog filename: ${file.name}" }
            val english = read(file)
            val chinese = read(root.resolve("values-zh/${file.name}"))
            require(english.keys == chinese.keys) { "${file.name}: language keys differ; English-only=${english.keys - chinese.keys}, Chinese-only=${chinese.keys - english.keys}" }
            val className = camel(file.nameWithoutExtension).replaceFirstChar(Char::uppercaseChar) + "Strings"
            generated["$className.kt"] = buildString {
                appendLine("// Generated from ${file.name}; edit the bilingual catalogs instead.")
                appendLine("package com.github.zly2006.zhihu.i18n")
                appendLine()
                appendLine("object $className {")
                val names = mutableSetOf<String>()
                for ((key, en) in english) {
                    val zh = chinese.getValue(key)
                    require(en.plural == zh.plural && (en.array == null) == (zh.array == null)) { "${file.name}: resource type differs for $key" }
                    val name = camel(key)
                    require(names.add(name)) { "${file.name}: colliding Kotlin key $name" }
                    if (en.array != null) {
                        fun arrayLiteral(entry: Entry) = entry.array!!.joinToString(prefix = "listOf(", postfix = ")") { literal(it, plural = false) }
                        appendLine("    fun $name(language: AppLanguage = AppLocale.current): List<String> =")
                        appendLine("        if (language == AppLanguage.Chinese) ${arrayLiteral(zh)} else ${arrayLiteral(en)}")
                        appendLine()
                        continue
                    }
                    val formats = (en.forms.values + zh.forms.values).map(::placeholders)
                    val params = formats.reduce { all, next ->
                        require(next.all { (position, type) -> all[position] in listOf(null, type) }) { "${file.name}: placeholder type mismatch in $key" }
                        all + next
                    }
                    val positions = params.keys + if (en.plural) setOf(1) else emptySet()
                    require(positions.sorted() == (1..positions.size).toList()) { "${file.name}: nonconsecutive placeholders in $key" }
                    require(en.forms.values.flatMap { placeholders(it).keys }.toSet() == zh.forms.values.flatMap { placeholders(it).keys }.toSet()) { "${file.name}: missing placeholder in $key" }
                    fun expression(entry: Entry): String {
                        val other = literal(entry.forms.getValue("other"), entry.plural)
                        return if (entry.plural && "one" in entry.forms) {
                            "if (count.toDouble() == 1.0) ${literal(entry.forms.getValue("one"), true)} else $other"
                        } else other
                    }
                    if (en.plural || params.isNotEmpty()) {
                        val arguments = buildList {
                            if (en.plural) add("count: Number")
                            params.keys.sorted().filterNot { en.plural && it == 1 }.forEach { add("arg$it: Any?") }
                        }
                        appendLine("    fun $name(${arguments.joinToString()}): String =")
                    } else {
                        appendLine("    val $name: String")
                        append("        get() = ")
                    }
                    appendLine("        if (AppLocale.current == AppLanguage.Chinese) ${expression(zh)} else ${expression(en)}")
                    appendLine()
                }
                appendLine("}")
            }
        }
        val output = outputDirectory.get().asFile
        output.mkdirs()
        for ((name, text) in generated) output.resolve(name).writeText(text)
        output.listFiles { f -> f.extension == "kt" && f.name !in generated }.orEmpty().forEach { check(it.delete()) }
    }
}
