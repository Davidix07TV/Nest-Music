package com.nestmusic.desktop

import java.awt.*
import java.net.URI
import javax.swing.*
import javax.swing.border.EmptyBorder

private val bg = Color(0x0D0D12)
private val panel = Color(0x17171F)
private val panel2 = Color(0x22222D)
private val text = Color(0xF4F1F8)
private val muted = Color(0xAAA6B4)
private val accent = Color(0xD7B5FF)

fun main() = SwingUtilities.invokeLater { NestMusicWindow().isVisible = true }

private class NestMusicWindow : JFrame("Nest Music") {
    private val content = JPanel(BorderLayout(18, 0))
    private val title = JLabel("Welcome back")
    private val search = JTextField()

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        minimumSize = Dimension(980, 650)
        size = Dimension(1180, 760)
        locationRelativeTo(null)
        content.background = bg
        content.border = EmptyBorder(22, 22, 18, 22)
        setContentPane(content)
        content.add(sidebar(), BorderLayout.WEST)
        content.add(home(), BorderLayout.CENTER)
    }

    private fun sidebar(): JPanel = JPanel().apply {
        preferredSize = Dimension(190, 0); background = bg
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(label("NEST MUSIC", 18, accent)); add(Box.createVerticalStrut(34))
        listOf("⌂   Home", "⌕   Explore", "♫   Library").forEachIndexed { i, s ->
            add(navButton(s, i == 0)); add(Box.createVerticalStrut(8))
        }
        add(Box.createVerticalGlue())
        add(navButton("⚙   Settings", false))
    }

    private fun home(): JPanel = JPanel(BorderLayout(0, 20)).apply {
        background = bg
        val top = JPanel(BorderLayout(18, 0)); top.background = bg
        title.font = title.font.deriveFont(Font.BOLD, 30f); title.foreground = text
        top.add(title, BorderLayout.WEST)
        search.preferredSize = Dimension(330, 40); search.background = panel2; search.foreground = text
        search.caretColor = accent; search.border = EmptyBorder(0, 14, 0, 14)
        search.toolTipText = "Search YouTube Music"
        search.addActionListener { openSearch() }
        top.add(search, BorderLayout.EAST); add(top, BorderLayout.NORTH)
        val body = JPanel(); body.background = bg; body.layout = BoxLayout(body, BoxLayout.Y_AXIS)
        body.add(section("Quick picks", arrayOf("Your Mix", "Discover Mix", "Liked Music", "Recently played")))
        body.add(Box.createVerticalStrut(26)); body.add(section("Made for you", arrayOf("Chill evening", "Focus flow", "New releases", "Throwback")))
        add(JScrollPane(body).apply { border = null; viewport.background = bg; verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED }, BorderLayout.CENTER)
        add(player(), BorderLayout.SOUTH)
    }

    private fun section(name: String, items: Array<String>) = JPanel(BorderLayout(0, 12)).apply {
        background = bg; maximumSize = Dimension(Int.MAX_VALUE, 185)
        add(label(name, 20, text), BorderLayout.NORTH)
        val cards = JPanel(GridLayout(1, items.size, 14, 0)); cards.background = bg
        items.forEach { cards.add(card(it)) }; add(cards, BorderLayout.CENTER)
    }

    private fun card(name: String) = JButton("\n\n  $name\n").apply {
        foreground = text; background = panel; isFocusPainted = false; border = EmptyBorder(14, 8, 14, 8)
        font = font.deriveFont(Font.BOLD, 14f); toolTipText = "Open $name"
        addActionListener { openSearch(name) }
    }

    private fun player() = JPanel(BorderLayout(12, 0)).apply {
        background = panel; border = EmptyBorder(12, 16, 12, 16); preferredSize = Dimension(0, 64)
        add(label("Nothing playing", 14, muted), BorderLayout.WEST)
        val controls = JPanel(FlowLayout(FlowLayout.CENTER, 12, 0)); controls.background = panel
        listOf("⏮", "▶", "⏭").forEach { b -> controls.add(JButton(b).apply { foreground = text; background = panel2; isFocusPainted = false }) }
        add(controls, BorderLayout.CENTER); add(label("Nest Music for desktop", 12, muted), BorderLayout.EAST)
    }

    private fun navButton(value: String, selected: Boolean) = JButton(value).apply {
        alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(190, 42)
        horizontalAlignment = SwingConstants.LEFT; foreground = if (selected) text else muted
        background = if (selected) panel2 else bg; isFocusPainted = false; border = EmptyBorder(11, 14, 11, 8)
    }

    private fun label(value: String, size: Int, color: Color) = JLabel(value).apply { foreground = color; font = font.deriveFont(size.toFloat()) }
    private fun openSearch(term: String = search.text) { if (term.isNotBlank()) Desktop.getDesktop().browse(URI("https://music.youtube.com/search?q=${java.net.URLEncoder.encode(term, "UTF-8")}")) }
}
