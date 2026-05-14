/*
 * This file is part of Resourcify
 * Copyright (C) 2023-2024 DeDiamondPro
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

package dev.dediamondpro.resourcify.gui.browsepage

import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.drawable.Rectangle
import com.cleanroommc.modularui.factory.ClientGUI
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.ModularScreen
import com.cleanroommc.modularui.widgets.ButtonWidget
import com.cleanroommc.modularui.widgets.ListWidget
import com.cleanroommc.modularui.widgets.TextWidget
import com.cleanroommc.modularui.widget.ParentWidget
import com.cleanroommc.modularui.widgets.layout.Flow
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget
import dev.dediamondpro.resourcify.VintageResourcify
import dev.dediamondpro.resourcify.config.Config
import dev.dediamondpro.resourcify.gui.projectpage.ProjectScreen
import dev.dediamondpro.resourcify.platform.Platform
import dev.dediamondpro.resourcify.services.IProject
import dev.dediamondpro.resourcify.services.ProjectType
import dev.dediamondpro.resourcify.services.ServiceRegistry
import dev.dediamondpro.resourcify.util.AsyncIcon
import dev.dediamondpro.resourcify.util.IrisHelper
import dev.dediamondpro.resourcify.util.MultiThreading
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.util.EnumChatFormatting
import org.lwjgl.input.Keyboard
import java.io.File

// F-bounded self-types in Kotlin need a concrete subclass.
private class SimpleButton : ButtonWidget<SimpleButton>()
private class SimpleList : ListWidget<IWidget, SimpleList>()
private class Container : ParentWidget<Container>()

// Card layout constants. Sized so a 1080p window shows 6-7 cards above the
// fold and small windows still render readable text.
private const val CARD_HEIGHT = 52
private const val CARD_GAP = 4
private const val ICON_SIZE = 44
private const val INNER_PAD = 6

class BrowseScreen(
    initialType: ProjectType,
    initialFolder: File,
    sourceParent: GuiScreen?,
) : ModularScreen(VintageResourcify.MODID, { _ ->
    // Type, service, sort and folder are all "var" because the user can flip
    // between resource packs and shaders via the top tab row without leaving
    // the screen. Cards capture the current type/folder at construction time;
    // switching tabs clears the results and rebuilds cards with new values.
    var currentType = initialType
    var service = ServiceRegistry.getDefaultService(currentType)
    var defaultSortKey = service.getSortOptions().keys.firstOrNull() ?: ""
    var packsFolder = initialFolder

    val isLight = Config.instance.markdownTheme.equals("light", ignoreCase = true)
    val cardBg = if (isLight) 0x14000000 else 0x28FFFFFF
    val textPrimary = if (isLight) 0xFF1F2328.toInt() else 0xFFF0F6FC.toInt()
    val textSecondary = if (isLight) 0xFF59636E.toInt() else 0xFF9198A1.toInt()

    var triggerSearch: (() -> Unit)? = null
    val searchBox = object : TextFieldWidget() {
        override fun onKeyPressed(character: Char, keyCode: Int): com.cleanroommc.modularui.api.widget.Interactable.Result {
            if (isFocused && (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER)) {
                triggerSearch?.invoke()
                return com.cleanroommc.modularui.api.widget.Interactable.Result.SUCCESS
            }
            return super.onKeyPressed(character, keyCode)
        }
    }
    val resultsList = SimpleList()
    // Pagination state. Modrinth and CurseForge both return one page per
    // search call (Modrinth: 20 per page); we append additional pages on
    // demand instead of fetching them all up front so an empty/broad query
    // doesn't hammer the API.
    var loadedCount = 0
    var totalCount = 0
    var loadingPage = false
    var currentQuery = ""
    var loadMoreBtn: IWidget? = null

    fun loadPage(append: Boolean) {
        if (loadingPage) return
        loadingPage = true
        if (!append) {
            resultsList.removeAll()
            loadedCount = 0
            totalCount = 0
            currentQuery = searchBox.text ?: ""
            resultsList.child(TextWidget(IKey.str("Searching...")).color(textSecondary))
        } else {
            loadMoreBtn?.let { resultsList.remove(it) }
            loadMoreBtn = null
            val loadingMore = TextWidget(IKey.str("Loading more...")).color(textSecondary)
            resultsList.child(loadingMore)
            loadMoreBtn = loadingMore
        }
        val query = currentQuery
        val offset = loadedCount
        val typeAtRequest = currentType
        val folderAtRequest = packsFolder
        MultiThreading.supplyAsync {
            try {
                service.search(query, defaultSortKey, listOf(Platform.getMcVersion()), emptyList(), offset, typeAtRequest)
            } catch (e: Exception) {
                VintageResourcify.LOG.warn("Search failed", e)
                null
            }
        }.thenAccept { result ->
            Minecraft.getMinecraft().func_152344_a {
                // If the user switched tabs while the request was in flight,
                // ignore the stale results entirely.
                if (typeAtRequest != currentType) return@func_152344_a
                loadingPage = false
                if (!append) resultsList.removeAll()
                else loadMoreBtn?.let { resultsList.remove(it); loadMoreBtn = null }

                val projects: List<IProject> = result?.projects ?: emptyList()
                totalCount = result?.totalCount ?: loadedCount
                if (loadedCount == 0 && projects.isEmpty()) {
                    resultsList.child(TextWidget(IKey.str("No results")).color(textSecondary))
                    return@func_152344_a
                }
                val platformIdAtRequest = service.getPlatformId()
                projects.forEach { project ->
                    resultsList.child(buildCard(project, typeAtRequest, folderAtRequest, sourceParent, platformIdAtRequest, cardBg, textPrimary, textSecondary))
                }
                loadedCount += projects.size
                if (loadedCount < totalCount && projects.isNotEmpty()) {
                    val btn = SimpleButton()
                        // Right margin clears the ListWidget scrollbar. Cards
                        // don't need this because their content uses
                        // right(INNER_PAD); the button is a single
                        // edge-to-edge overlay and would otherwise paint
                        // under the scrollbar track.
                        .widthRel(1f).height(20).margin(0, 10, 0, CARD_GAP)
                        .overlay(IKey.str("Load more (${loadedCount}/${totalCount})"))
                        .onMousePressed { b -> if (b == 0) { loadPage(append = true); true } else false }
                    resultsList.child(btn)
                    loadMoreBtn = btn
                }
            }
        }
    }

    // Type switcher: two buttons, one per supported pack type. Hidden tab
    // for shaders when Iris/Angelica isn't loaded. Width is set wide enough
    // that "Resource Packs" fits on a single line at default font scale.
    val shadersAvailable = IrisHelper.isPresent()
    val packsTab = SimpleButton().size(112, 18)
    val shadersTab = SimpleButton().size(112, 18)

    fun tabLabel(label: String, selected: Boolean): String =
        if (selected) "§f§n$label§r" else "§7$label§r"

    fun refreshTabLabels() {
        packsTab.overlay(IKey.str(tabLabel("Resource Packs", currentType == ProjectType.RESOURCE_PACK)))
        shadersTab.overlay(IKey.str(tabLabel("Shaders", currentType == ProjectType.IRIS_SHADER)))
    }
    refreshTabLabels()

    fun switchType(t: ProjectType) {
        if (t == currentType) return
        currentType = t
        service = ServiceRegistry.getDefaultService(t)
        defaultSortKey = service.getSortOptions().keys.firstOrNull() ?: ""
        packsFolder = when (t) {
            ProjectType.IRIS_SHADER -> IrisHelper.getShaderpacksFolder()
            else -> Minecraft.getMinecraft().resourcePackRepository.dirResourcepacks
        }
        refreshTabLabels()
        loadPage(append = false)
    }
    packsTab.onMousePressed { b -> if (b == 0) { switchType(ProjectType.RESOURCE_PACK); true } else false }
    shadersTab.onMousePressed { b -> if (b == 0) { switchType(ProjectType.IRIS_SHADER); true } else false }

    val tabRow = Flow.row().top(6).left(10).height(18)
        .child(packsTab.margin(0, 4, 0, 0))
        .apply { if (shadersAvailable) child(shadersTab) }

    val searchRow = Flow.row().top(28).left(10).right(10).height(16)
        .child(searchBox.heightRel(1f).widthRel(1f).margin(0, 60, 0, 0))
        .child(
            SimpleButton()
                .size(54, 16).right(0)
                .overlay(IKey.str("Search"))
                .onMousePressed { btn -> if (btn == 0) { loadPage(append = false); true } else false }
        )

    triggerSearch = { loadPage(append = false) }
    resultsList.top(50).left(10).right(10).bottom(10)
        .child(TextWidget(IKey.str("Loading...")).color(textSecondary))
    loadPage(append = false)

    ModularPanel.defaultPanel("vintage-resourcify-browse")
        .full()
        .child(tabRow)
        .child(searchRow)
        .child(resultsList)
})

/** Project result card: thumbnail + title + author + summary, clickable. */
private fun buildCard(
    project: IProject,
    type: ProjectType,
    packsFolder: File,
    sourceParent: GuiScreen?,
    platformId: String,
    cardBg: Int,
    textPrimary: Int,
    textSecondary: Int,
): IWidget {
    val click = { btn: Int ->
        if (btn == 0) {
            VintageResourcify.LOG.info(
                "BrowseScreen card clicked: {} (id={})",
                project.getName(), project.getId(),
            )
            // Defer to next tick: ClientGUI.open immediately calls
            // displayGuiScreen, which mid-MUI2-event-dispatch leaves the
            // wrapper in a half-displayed state and never actually paints.
            Minecraft.getMinecraft().func_152344_a {
                val mcBefore = Minecraft.getMinecraft().currentScreen
                val muiBefore = try {
                    val cls = Class.forName("com.cleanroommc.modularui.screen.ClientScreenHandler")
                    val f = cls.getDeclaredField("currentScreen")
                    f.isAccessible = true
                    f.get(null)
                } catch (e: Throwable) {
                    "<reflection failed: ${e.message}>"
                }
                VintageResourcify.LOG.info(
                    "Pre-open mc.currentScreen={}@{} mui.currentScreen={}@{}",
                    mcBefore?.javaClass?.simpleName,
                    mcBefore?.let { Integer.toHexString(System.identityHashCode(it)) },
                    muiBefore?.javaClass?.simpleName,
                    muiBefore?.let { Integer.toHexString(System.identityHashCode(it)) },
                )
                try {
                    val newScreen = ProjectScreen(project, type, packsFolder, sourceParent, platformId)
                    // Go through MUI2's official entry point so UISettings,
                    // recipe-viewer integration, etc. get wired up. Calling
                    // displayGuiScreen on a hand-constructed wrapper skips
                    // that and crashes on the first mouse press (NEI ghost
                    // ingredient check reads UISettings).
                    ClientGUI.open(newScreen)
                    val mcAfter = Minecraft.getMinecraft().currentScreen
                    VintageResourcify.LOG.info(
                        "ClientGUI.open returned cleanly. newProjectScreen={} currentScreen now={}@{}",
                        Integer.toHexString(System.identityHashCode(newScreen)),
                        mcAfter?.javaClass?.simpleName,
                        mcAfter?.let { Integer.toHexString(System.identityHashCode(it)) },
                    )
                } catch (t: Throwable) {
                    VintageResourcify.LOG.error("Opening ProjectScreen threw", t)
                }
            }
            true
        } else false
    }
    val textLeft = INNER_PAD + ICON_SIZE + 10
    // Truncate to two visual lines at the card's actual rendered width.
    // listFormattedStringToWidth wraps to the column width; if it returns
    // more than 2 lines, keep the first two and ellipsize the second.
    val rawSummary = project.getSummary()
    val mc = Minecraft.getMinecraft()
    val sr = ScaledResolution(mc, mc.displayWidth, mc.displayHeight)
    // Panel uses .full(); list has 10px L/R, scrollbar ~12, card uses
    // INNER_PAD on each side, icon column + 10px gap.
    val summaryW = sr.scaledWidth - 32 - INNER_PAD * 2 - ICON_SIZE - 10
    val fr = mc.fontRenderer
    val summary = if (fr != null) {
        @Suppress("UNCHECKED_CAST")
        val wrapped = fr.listFormattedStringToWidth(rawSummary, summaryW.coerceAtLeast(40)) as List<String>
        if (wrapped.size <= 2) {
            wrapped.joinToString(" ")
        } else {
            val first = wrapped[0]
            val secondTrimmed = fr.trimStringToWidth(
                wrapped[1] + " " + wrapped[2],
                (summaryW - fr.getStringWidth("...")).coerceAtLeast(20),
            )
            "$first $secondTrimmed..."
        }
    } else {
        if (rawSummary.length > 130) rawSummary.take(127) + "..." else rawSummary
    }
    // Vertically center the icon and right-side text column inside the
    // CARD_HEIGHT box. The text column is 36px tall (title 10 + author 9 +
    // summary 17), centered.
    val iconTop = (CARD_HEIGHT - ICON_SIZE) / 2
    val textBlockH = 36
    val textTop = (CARD_HEIGHT - textBlockH) / 2
    val content = Container()
        .widthRel(1f).heightRel(1f)
        .child(
            AsyncIcon(project.getIconUrl(), ICON_SIZE)
                .top(iconTop).left(INNER_PAD)
        )
        .child(
            TextWidget(IKey.str(project.getName()).style(EnumChatFormatting.BOLD))
                .top(textTop).left(textLeft).right(INNER_PAD).height(10)
                .color(textPrimary)
                .alignment(com.cleanroommc.modularui.utils.Alignment.CenterLeft)
        )
        .child(
            TextWidget(IKey.str("by ${project.getAuthor()}"))
                .top(textTop + 11).left(textLeft).right(INNER_PAD).height(9)
                .color(textSecondary)
                .alignment(com.cleanroommc.modularui.utils.Alignment.CenterLeft)
        )
        .child(
            TextWidget(IKey.str(summary))
                .top(textTop + 22).left(textLeft).right(INNER_PAD).height(textBlockH - 22)
                .color(textPrimary)
                .alignment(com.cleanroommc.modularui.utils.Alignment.TopLeft)
        )

    return SimpleButton()
        .widthRel(1f).height(CARD_HEIGHT).margin(0, 0, 0, CARD_GAP)
        .background(Rectangle().color(cardBg))
        .overlay()
        .child(content)
        .onMousePressed(click)
}
