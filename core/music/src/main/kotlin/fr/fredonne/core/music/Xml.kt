package fr.fredonne.core.music

/**
 * Lecteur XML minimal, en Kotlin pur, suffisant pour MusicXML : éléments, attributs, texte,
 * commentaires, CDATA et entités usuelles. Pas d'espaces de noms ni de validation.
 * Écrit à la main pour que le moteur reste sans dépendance et portable hors de la JVM.
 */
internal class XmlElement(
    val name: String,
    val attributes: Map<String, String>,
    val children: List<XmlElement>,
    val text: String,
) {
    fun child(name: String): XmlElement? = children.firstOrNull { it.name == name }

    fun children(name: String): List<XmlElement> = children.filter { it.name == name }

    fun textOf(name: String): String? = child(name)?.text?.trim()?.takeIf { it.isNotEmpty() }

    fun intOf(name: String): Int? = textOf(name)?.toIntOrNull()
}

class XmlFormatException(message: String) : Exception(message)

internal object Xml {

    fun parse(source: String): XmlElement = Parser(source).document()

    /** Protège un texte pour l'écrire dans un élément ou un attribut. */
    fun escape(text: String): String = buildString {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun document(): XmlElement {
            // Marque d'ordre des octets éventuelle en tête de fichier.
            if (s.startsWith("﻿")) i = 1
            skipProlog()
            if (i >= s.length || s[i] != '<') fail("élément racine introuvable")
            return element()
        }

        private fun skipProlog() {
            while (true) {
                skipWhitespace()
                when {
                    s.startsWith("<?", i) -> i = after("?>")
                    s.startsWith("<!--", i) -> i = after("-->")
                    s.startsWith("<!", i) -> skipDoctype()
                    else -> return
                }
            }
        }

        private fun skipDoctype() {
            var depth = 0
            while (i < s.length) {
                when (s[i]) {
                    '[' -> depth++
                    ']' -> depth--
                    '>' -> if (depth <= 0) {
                        i++
                        return
                    }
                }
                i++
            }
            fail("DOCTYPE non terminé")
        }

        private fun element(): XmlElement {
            i++ // '<'
            val name = name()
            val attributes = LinkedHashMap<String, String>()
            while (true) {
                skipWhitespace()
                if (i >= s.length) fail("balise <$name> non terminée")
                if (s.startsWith("/>", i)) {
                    i += 2
                    return XmlElement(name, attributes, emptyList(), "")
                }
                if (s[i] == '>') {
                    i++
                    break
                }
                val key = name()
                skipWhitespace()
                if (i >= s.length || s[i] != '=') fail("attribut $key sans valeur dans <$name>")
                i++
                skipWhitespace()
                if (i >= s.length || (s[i] != '"' && s[i] != '\'')) fail("valeur de $key sans guillemets")
                val quote = s[i]
                val end = s.indexOf(quote, i + 1)
                if (end < 0) fail("valeur de $key non terminée")
                attributes[key] = decode(s.substring(i + 1, end))
                i = end + 1
            }

            val children = ArrayList<XmlElement>()
            val text = StringBuilder()
            while (true) {
                if (i >= s.length) fail("élément <$name> non fermé")
                when {
                    s.startsWith("</", i) -> {
                        i = after(">")
                        return XmlElement(name, attributes, children, text.toString())
                    }
                    s.startsWith("<!--", i) -> i = after("-->")
                    s.startsWith("<![CDATA[", i) -> {
                        val end = s.indexOf("]]>", i)
                        if (end < 0) fail("section CDATA non terminée")
                        text.append(s, i + 9, end)
                        i = end + 3
                    }
                    s.startsWith("<?", i) -> i = after("?>")
                    s[i] == '<' -> children += element()
                    else -> {
                        val end = s.indexOf('<', i).let { if (it < 0) s.length else it }
                        text.append(decode(s.substring(i, end)))
                        i = end
                    }
                }
            }
        }

        private fun name(): String {
            val start = i
            while (i < s.length && !s[i].isWhitespace() && s[i] != '>' && s[i] != '/' && s[i] != '=') i++
            if (i == start) fail("nom attendu")
            return s.substring(start, i)
        }

        private fun skipWhitespace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun after(marker: String): Int {
            val end = s.indexOf(marker, i)
            if (end < 0) fail("« $marker » attendu")
            return end + marker.length
        }

        private fun decode(raw: String): String {
            if ('&' !in raw) return raw
            val out = StringBuilder()
            var k = 0
            while (k < raw.length) {
                val c = raw[k]
                val end = if (c == '&') raw.indexOf(';', k) else -1
                if (end < 0) {
                    out.append(c)
                    k++
                    continue
                }
                val entity = raw.substring(k + 1, end)
                val decoded: String? = when {
                    entity == "amp" -> "&"
                    entity == "lt" -> "<"
                    entity == "gt" -> ">"
                    entity == "quot" -> "\""
                    entity == "apos" -> "'"
                    entity.startsWith("#x") -> entity.drop(2).toIntOrNull(16)?.let(::codePoint)
                    entity.startsWith("#") -> entity.drop(1).toIntOrNull()?.let(::codePoint)
                    else -> null
                }
                if (decoded == null) {
                    out.append(c)
                    k++
                } else {
                    out.append(decoded)
                    k = end + 1
                }
            }
            return out.toString()
        }

        private fun codePoint(code: Int): String? = when {
            code in 0..0xFFFF -> code.toChar().toString()
            code in 0x10000..0x10FFFF -> {
                val v = code - 0x10000
                charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
            }
            else -> null
        }

        private fun fail(message: String): Nothing = throw XmlFormatException("XML invalide : $message (position $i)")
    }
}
