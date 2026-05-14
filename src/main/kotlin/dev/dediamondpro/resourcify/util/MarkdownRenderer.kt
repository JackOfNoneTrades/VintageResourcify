/*
 * This file is part of Resourcify
 * Copyright (C) 2024 DeDiamondPro
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License Version 3 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.dediamondpro.resourcify.util

import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.widgets.TextWidget
import net.minecraft.client.Minecraft
import net.minecraft.util.EnumChatFormatting
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/**
 * Parses CommonMark + HTML and emits a vertical stack of [TextWidget]s sized
 * to fit the given column width. Inline styling (bold/italic/code) is mapped
 * onto Minecraft's [EnumChatFormatting] codes. Block-level elements get a
 * trailing blank-line widget so paragraphs separate visually.
 *
 * Markdown rendering is deliberately minimal: no real images, no rich link
 * targets, no nested-list indentation past one level. The goal is to make
 * project descriptions readable, not to be a faithful renderer.
 */
object MarkdownRenderer {

    private val parser = Parser.builder().build()
    private val htmlTag = Regex("<[^>]+>")

    fun render(markdown: String, widthPx: Int): List<IWidget> {
        // commonmark passes raw HTML through as HtmlBlock / HtmlInline. The
        // pragmatic move is to strip tags up front: we lose the heading-ness
        // of <h1> etc. but the visible text remains.
        val cleaned = markdown.replace(htmlTag, "")
        val doc = parser.parse(cleaned)
        val out = mutableListOf<IWidget>()
        doc.accept(BlockEmitter(out, widthPx))
        return out
    }

    private class BlockEmitter(private val out: MutableList<IWidget>, private val width: Int) : AbstractVisitor() {

        override fun visit(heading: Heading) {
            // §e (yellow) for top-level, §6 (gold) for h2, §f (white bold) for h3+.
            val color = when (heading.level) {
                1 -> "§e§l"
                2 -> "§6§l"
                else -> "§f§l"
            }
            val text = collectText(heading)
            emitLines(color + text + "§r")
            if (heading.level <= 2) {
                emitLines("§8" + "-".repeat(60) + "§r")
            }
            spacer()
        }

        override fun visit(paragraph: Paragraph) {
            val text = collectText(paragraph)
            if (text.isNotBlank()) {
                emitLines(text)
                spacer()
            }
        }

        override fun visit(bulletList: BulletList) {
            visitChildren(bulletList)
            spacer()
        }

        override fun visit(orderedList: OrderedList) {
            visitChildren(orderedList)
            spacer()
        }

        override fun visit(listItem: ListItem) {
            val text = "  - " + collectText(listItem)
            emitLines(text)
        }

        override fun visit(blockQuote: BlockQuote) {
            val text = "§7> " + collectText(blockQuote) + "§r"
            emitLines(text)
            spacer()
        }

        override fun visit(fencedCodeBlock: FencedCodeBlock) {
            fencedCodeBlock.literal.lineSequence().forEach { emitLines("§7" + it + "§r") }
            spacer()
        }

        override fun visit(indentedCodeBlock: IndentedCodeBlock) {
            indentedCodeBlock.literal.lineSequence().forEach { emitLines("§7    " + it + "§r") }
            spacer()
        }

        override fun visit(thematicBreak: ThematicBreak) {
            emitLines("§8" + "-".repeat(40) + "§r")
            spacer()
        }

        override fun visit(htmlBlock: HtmlBlock) {
            // Tags already stripped at the top-level entry point, but a stray
            // HtmlBlock can still hold residual whitespace - ignore it.
        }

        private fun emitLines(text: String) {
            val fr = Minecraft.getMinecraft().fontRenderer ?: run {
                out += TextWidget(IKey.str(text)).widthRel(1f)
                return
            }
            @Suppress("UNCHECKED_CAST")
            val wrapped = fr.listFormattedStringToWidth(text, width) as List<String>
            (if (wrapped.isEmpty()) listOf(text) else wrapped).forEach { line ->
                out += TextWidget(IKey.str(line)).widthRel(1f)
            }
        }

        private fun spacer() {
            out += TextWidget(IKey.str("")).widthRel(1f).height(4)
        }
    }

    /** Walk inline children of [node] and concatenate text with §-coded styles. */
    private fun collectText(node: Node): String {
        val sb = StringBuilder()
        var child = node.firstChild
        while (child != null) {
            appendInline(child, sb)
            child = child.next
        }
        return sb.toString()
    }

    private fun appendInline(node: Node, sb: StringBuilder) {
        when (node) {
            is Text -> sb.append(node.literal)
            is StrongEmphasis -> wrap(node, sb, EnumChatFormatting.BOLD)
            is Emphasis -> wrap(node, sb, EnumChatFormatting.ITALIC)
            is Code -> {
                sb.append("§7").append(node.literal).append("§r")
            }
            is Link -> {
                val inner = collectInline(node)
                sb.append("§9§n").append(inner).append("§r")
            }
            is Image -> {
                val alt = collectInline(node)
                if (alt.isNotEmpty()) sb.append("[").append(alt).append("]")
            }
            is SoftLineBreak, is HardLineBreak -> sb.append(' ')
            is HtmlInline -> { /* tag bodies already stripped */ }
            else -> {
                // Recurse into unknown inline containers so nested formatting
                // doesn't get silently dropped.
                var child = node.firstChild
                while (child != null) {
                    appendInline(child, sb)
                    child = child.next
                }
            }
        }
    }

    private fun wrap(node: Node, sb: StringBuilder, fmt: EnumChatFormatting) {
        sb.append("§").append(fmt.formattingCode)
        var child = node.firstChild
        while (child != null) {
            appendInline(child, sb)
            child = child.next
        }
        sb.append("§r")
    }

    private fun collectInline(node: Node): String {
        val sb = StringBuilder()
        var child = node.firstChild
        while (child != null) {
            appendInline(child, sb)
            child = child.next
        }
        return sb.toString()
    }
}
