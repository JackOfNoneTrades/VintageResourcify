/*
 * This file is part of Resourcify
 * Copyright (C) 2023 DeDiamondPro
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

package dev.dediamondpro.resourcify.gui.pack

import com.cleanroommc.modularui.factory.ClientGUI
import dev.dediamondpro.resourcify.VintageResourcify
import dev.dediamondpro.resourcify.config.Config
import dev.dediamondpro.resourcify.config.UpdateChimeState
import dev.dediamondpro.resourcify.gui.ResourcifyStyle
import dev.dediamondpro.resourcify.gui.browsepage.BrowseScreen
import dev.dediamondpro.resourcify.mixins.early.minecraft.PackScreenAccessor
import dev.dediamondpro.resourcify.platform.Platform
import dev.dediamondpro.resourcify.services.DistributionPolicy
import dev.dediamondpro.resourcify.services.IVersion
import dev.dediamondpro.resourcify.services.ProjectType
import dev.dediamondpro.resourcify.services.ServiceRegistry
import dev.dediamondpro.resourcify.util.DownloadManager
import dev.dediamondpro.resourcify.util.DownloadResult
import dev.dediamondpro.resourcify.util.LocalIndex
import dev.dediamondpro.resourcify.util.ResourcifySounds
import dev.dediamondpro.resourcify.util.ShaderGuiHelper
import dev.dediamondpro.resourcify.util.localize
import net.minecraft.client.Minecraft
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiScreenResourcePacks
import net.minecraft.util.ResourceLocation
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11
import org.fentanylsolutions.fentlib.util.FileUtil
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture

object PackScreensAddition {

    private const val BUTTON_SIZE = 20
    private const val ICON_SIZE = 16
    private const val BUTTON_GAP = 4
    private const val PANEL_WIDTH = 390
    private const val PANEL_HEIGHT = 292
    private const val PANEL_PADDING = 10
    private const val PANEL_MARGIN = 12
    private const val ROW_HEIGHT = 46
    private const val SETTINGS_PANEL_WIDTH = 400
    private const val SETTINGS_PANEL_HEIGHT = 282
    private const val SETTINGS_DROPDOWN_ROW_HEIGHT = 14
    private const val SETTINGS_DROPDOWN_ROWS = 8
    private const val SETTINGS_IGNORED_ROW_HEIGHT = 38
    private const val TEXT_BUTTON_HEIGHT = 22
    private const val BADGE_HEIGHT = 14
    private const val PROGRESS_HEIGHT = 8
    private const val UPDATE_SCREEN_SCRIM = 0xD8000000.toInt()

    private val PLUS_TEXTURE = ResourceLocation(VintageResourcify.MODID, "plus.png")
    private val UPDATE_TEXTURE = ResourceLocation(VintageResourcify.MODID, "update.png")
    private val PICK_FILE_TEXTURE = ResourceLocation(VintageResourcify.MODID, "pick_file.png")
    private val PASSWORD_EYE_TEXTURE = ResourceLocation(VintageResourcify.MODID, "password_eye.png")
    private val BUTTON_PRESS_SOUND = ResourceLocation("gui.button.press")
    private val DOWNLOAD_CHIME_SOUND = ResourceLocation(VintageResourcify.MODID, "download_chime")
    private val IMPORT_CHIME_SOUND = ResourceLocation(VintageResourcify.MODID, "import_chime")
    private val UPDATE_CHIME_SOUND = ResourceLocation(VintageResourcify.MODID, "update_chime")

    @Volatile private var toastText: String? = null
    @Volatile private var toastUntil: Long = 0L
    @Volatile private var checkInProgress = false
    @Volatile private var updatePanelOpen = false
    @Volatile private var updateInProgress = false
    @Volatile private var updateCancelRequested = false
    @Volatile private var panelType: ProjectType? = null
    @Volatile private var panelFolder: File? = null
    @Volatile private var updateStatusText: String = ""
    @Volatile private var updateScroll = 0
    @Volatile private var updateTotal = 0
    @Volatile private var updateCompleted = 0
    @Volatile private var packSettingsOpen = false
    @Volatile private var settingsType: ProjectType? = null
    @Volatile private var settingsFolder: File? = null
    @Volatile private var settingsFile: File? = null
    @Volatile private var settingsEntry: LocalIndex.Entry? = null
    @Volatile private var settingsCandidate: IVersion? = null
    @Volatile private var settingsLoading = false
    @Volatile private var settingsUpdating = false
    @Volatile private var settingsStatus = ""
    @Volatile private var settingsTab = SettingsTab.UPDATES
    @Volatile private var settingsTrackOptions: List<String?> = listOf(null)
    @Volatile private var settingsTrackNames: Map<String, String> = emptyMap()
    @Volatile private var settingsDropdownOpen = false
    @Volatile private var settingsDropdownScroll = 0
    @Volatile private var settingsIgnoredScroll = 0
    @Volatile private var settingsRequestToken = 0
    @Volatile private var directUpdateOpen = false
    @Volatile private var directUpdateType: ProjectType? = null
    @Volatile private var directUpdateFolder: File? = null
    @Volatile private var directUpdateFile: File? = null
    @Volatile private var directUpdateStatus = ""
    @Volatile private var currentDownloadUrl: URL? = null
    @Volatile private var activeDownload: CompletableFuture<DownloadResult>? = null

    private val prunedScreens = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val autoCheckedScreens = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val updateLock = Any()
    private val updateEntries = mutableListOf<UpdateEntry>()

    fun onRender(type: ProjectType, folder: File, screen: GuiScreen, mouseX: Int, mouseY: Int) {
        pruneIfFirstSeen(screen, folder)
        if (type.hasUpdateButton && Config.instance.autoUpdateChecks) {
            autoCheckIfFirstSeen(screen, type, folder)
        }

        val updatePanelActive = updatePanelOpen && matchesPanel(type, folder)
        val settingsActive = packSettingsOpen && matchesSettings(type, folder)
        val directUpdateActive = directUpdateOpen && matchesDirectUpdate(type, folder)
        val panelActive = updatePanelActive || settingsActive || directUpdateActive
        val (topMouseX, topMouseY) = if (panelActive) -1 to -1 else mouseX to mouseY
        val topButtonTooltipEnabled = !panelActive
        val (plusX, plusY) = plusOrigin() ?: return
        drawIconButton(
            plusX,
            plusY,
            PLUS_TEXTURE,
            topMouseX,
            topMouseY,
            enabled = true,
            tooltip = browseTooltip(type).takeIf { topButtonTooltipEnabled },
        )

        if (supportsPackFilePicking(type)) {
            val (pickX, pickY) = pickOrigin() ?: return
            drawIconButton(
                pickX,
                pickY,
                PICK_FILE_TEXTURE,
                topMouseX,
                topMouseY,
                enabled = true,
                tooltip = importTooltip(type).takeIf { topButtonTooltipEnabled },
            )
        }

        if (type.hasUpdateButton) {
            val (upX, upY) = updateOrigin() ?: return
            drawIconButton(
                upX,
                upY,
                UPDATE_TEXTURE,
                topMouseX,
                topMouseY,
                enabled = true,
                tooltip = updateTooltip(type, folder).takeIf { topButtonTooltipEnabled },
            )
            val count = badgeCount(type, folder)
            if (count > 0) {
                drawUpdateBadge(upX, upY, count)
            }
            val reviews = reviewCount(type, folder)
            if (reviews > 0) {
                drawReviewBadge(upX, upY, count > 0)
            }
        }

        if (directUpdateActive) {
            drawDirectUpdatePopup(screen)
        } else if (updatePanelActive) {
            val (panelMouseX, panelMouseY) = scaledMousePosition(screen)
            drawUpdatePanel(type, folder, screen, panelMouseX, panelMouseY)
        } else if (settingsActive) {
            val (panelMouseX, panelMouseY) = scaledMousePosition(screen)
            drawPackSettings(screen, panelMouseX, panelMouseY)
        } else {
            drawToast(plusY + BUTTON_SIZE + 4)
        }

        PackOverlayRenderer.drainPendingTooltip()
    }

    fun onMouseClick(mouseX: Int, mouseY: Int, button: Int, type: ProjectType, folder: File, screen: GuiScreen): Boolean {
        if (button != 0) return false
        if (packSettingsOpen && matchesSettings(type, folder)) {
            return handlePackSettingsClick(mouseX, mouseY)
        }
        if (updatePanelOpen && matchesPanel(type, folder)) {
            return handleUpdatePanelClick(mouseX, mouseY, type, folder)
        }

        val plus = plusOrigin() ?: return false
        if (isInside(mouseX, mouseY, plus.first, plus.second, BUTTON_SIZE, BUTTON_SIZE)) {
            playDefaultButtonSound()
            openBrowser(type, folder, screen)
            return true
        }

        if (supportsPackFilePicking(type)) {
            val pick = pickOrigin() ?: return false
            if (isInside(mouseX, mouseY, pick.first, pick.second, BUTTON_SIZE, BUTTON_SIZE)) {
                playDefaultButtonSound()
                pickAndCopyPackFile(type, folder)
                return true
            }
        }

        if (!type.hasUpdateButton) return false
        val up = updateOrigin() ?: return false
        if (isInside(mouseX, mouseY, up.first, up.second, BUTTON_SIZE, BUTTON_SIZE) ||
            isInsideUpdateBadge(mouseX, mouseY, up.first, up.second, badgeCount(type, folder)) ||
            isInsideReviewBadge(mouseX, mouseY, up.first, up.second, reviewCount(type, folder), badgeCount(type, folder) > 0)) {
            playDefaultButtonSound()
            openUpdatePanel(type, folder)
            if (!checkInProgress && shouldRefreshOnOpen()) {
                startUpdateCheck(type, folder, openPanel = true)
            }
            return true
        }
        return false
    }

    fun onKeyTyped(keyCode: Int): Boolean {
        if (directUpdateOpen) return true
        if (packSettingsOpen) {
            if (!isSettingsVisibleOnCurrentScreen()) {
                closePackSettings()
                return false
            }
            if (keyCode == Keyboard.KEY_ESCAPE) {
                if (settingsDropdownOpen) settingsDropdownOpen = false
                else if (!settingsUpdating) closePackSettings()
                return true
            }
            return false
        }
        if (!updatePanelOpen) return false
        if (!isPanelVisibleOnCurrentScreen()) {
            updatePanelOpen = false
            return false
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (!updateInProgress) {
                updatePanelOpen = false
            }
            return true
        }
        return false
    }

    fun onMouseInput(): Boolean {
        if (directUpdateOpen) return false
        if (packSettingsOpen) return handlePackSettingsMouseInput()
        if (!updatePanelOpen) return false
        if (!isPanelVisibleOnCurrentScreen()) {
            updatePanelOpen = false
            return false
        }
        val wheel = Mouse.getEventDWheel()
        if (wheel == 0) return false

        val mc = Minecraft.getMinecraft()
        val screen = mc.currentScreen ?: return false
        val mouseX = Mouse.getEventX() * screen.width / mc.displayWidth
        val mouseY = screen.height - Mouse.getEventY() * screen.height / mc.displayHeight - 1
        val panelW = PANEL_WIDTH.coerceAtMost(screen.width - PANEL_MARGIN * 2).coerceAtLeast(240)
        val panelH = PANEL_HEIGHT.coerceAtMost(screen.height - PANEL_MARGIN * 2).coerceAtLeast(170)
        val panelX = (screen.width - panelW) / 2
        val panelY = (screen.height - panelH) / 2
        val listX = panelX + PANEL_PADDING
        val listY = panelY + 36
        val listW = panelW - PANEL_PADDING * 2
        val listH = panelH - 78
        val maxScroll = maxScroll(entriesSnapshot().size, listH)
        if (maxScroll > 0 && isInside(mouseX, mouseY, listX, listY, listW, listH)) {
            updateScroll = (updateScroll - wheelDirection(wheel)).coerceIn(0, maxScroll)
        }
        return true
    }

    fun shouldMaskParentMouse(type: ProjectType, folder: File): Boolean {
        return (updatePanelOpen && matchesPanel(type, folder)) ||
            (packSettingsOpen && matchesSettings(type, folder)) ||
            (directUpdateOpen && matchesDirectUpdate(type, folder))
    }

    fun hasOpenPopup(): Boolean = updatePanelOpen || packSettingsOpen || directUpdateOpen

    /**
     * Routes input to the currently visible modal without depending on the
     * host screen to reconstruct the same project type and folder identity.
     */
    fun onOpenPopupClick(mouseX: Int, mouseY: Int, button: Int): Boolean {
        if (!hasOpenPopup()) return false
        if (button != 0) return true
        if (directUpdateOpen) return true
        if (packSettingsOpen) return handlePackSettingsClick(mouseX, mouseY)
        if (updatePanelOpen) {
            val type = panelType ?: return true
            val folder = panelFolder ?: return true
            return handleUpdatePanelClick(mouseX, mouseY, type, folder)
        }
        return true
    }

    fun openPackSettings(folder: File, file: File) {
        val entry = LocalIndex.forFolder(folder).lookupByFileName(file) ?: return
        val type = if (sameFile(folder, Minecraft.getMinecraft().resourcePackRepository.dirResourcepacks)) {
            ProjectType.RESOURCE_PACK
        } else {
            ProjectType.IRIS_SHADER
        }
        updatePanelOpen = false
        directUpdateOpen = false
        packSettingsOpen = true
        settingsType = type
        settingsFolder = safeCanonical(folder)
        settingsFile = file
        settingsEntry = entry
        settingsCandidate = null
        settingsTab = SettingsTab.UPDATES
        settingsDropdownOpen = false
        settingsDropdownScroll = 0
        settingsIgnoredScroll = 0
        settingsStatus = localize("resourcify.pack_settings.loading")
        loadPackSettings()
    }

    fun hasAvailableUpdate(folder: File, file: File): Boolean = synchronized(updateLock) {
        updateEntries.any {
            sameFile(it.oldFile, file) && it.version != null &&
                it.status != UpdateStatus.UPDATED && it.status != UpdateStatus.UPDATING &&
                !isCandidateIgnored(it.localEntry, it.version!!)
        }
    }

    fun openDirectUpdate(folder: File, file: File) {
        val entry = synchronized(updateLock) {
            updateEntries.firstOrNull {
                sameFile(it.oldFile, file) && it.version != null &&
                    it.status != UpdateStatus.UPDATED && it.status != UpdateStatus.UPDATING
            }
        } ?: return
        val type = if (sameFile(folder, Minecraft.getMinecraft().resourcePackRepository.dirResourcepacks)) {
            ProjectType.RESOURCE_PACK
        } else {
            ProjectType.IRIS_SHADER
        }
        playDefaultButtonSound()
        updatePanelOpen = false
        packSettingsOpen = false
        directUpdateOpen = true
        directUpdateType = type
        directUpdateFolder = safeCanonical(folder)
        directUpdateFile = file
        directUpdateStatus = localize("resourcify.pack_updates.status.updating")
        updateCancelRequested = false
        entry.status = UpdateStatus.UPDATING
        val wasEnabled = when (type) {
            ProjectType.RESOURCE_PACK, ProjectType.AYCY_RESOURCE_PACK -> Platform.isResourcePackEnabled(file)
            ProjectType.IRIS_SHADER, ProjectType.OPTIFINE_SHADER -> ShaderGuiHelper.isShaderPackEnabled(file)
            else -> false
        }
        Thread({
            val result = performUpdate(PreparedUpdate(entry, wasEnabled), type, folder)
            entry.status = result
            directUpdateStatus = statusLabel(entry)
            if (result == UpdateStatus.UPDATED) {
                synchronized(updateLock) { updateEntries.remove(entry) }
                runClientSync { playDownloadChime() }
            } else {
                showToast(directUpdateStatus, durationMs = 5_000)
            }
            directUpdateOpen = false
        }, "Resourcify-DirectPackUpdate").apply { isDaemon = true }.start()
    }

    private fun pruneIfFirstSeen(screen: GuiScreen, folder: File) {
        synchronized(prunedScreens) {
            if (!prunedScreens.add(screen)) return
        }
        try {
            val removed = LocalIndex.forFolder(folder).prune()
            if (removed > 0) VintageResourcify.LOG.info("Pruned {} stale install index entries in {}", removed, folder)
        } catch (e: Throwable) {
            VintageResourcify.LOG.warn("Index prune failed for {}", folder, e)
        }
    }

    private fun autoCheckIfFirstSeen(screen: GuiScreen, type: ProjectType, folder: File) {
        synchronized(autoCheckedScreens) {
            if (!autoCheckedScreens.add(screen)) return
        }
        startUpdateCheck(type, folder, openPanel = false)
    }

    private fun openBrowser(type: ProjectType, folder: File, screen: GuiScreen) {
        updatePanelOpen = false
        val grandparent: GuiScreen? = when {
            screen is PackScreenAccessor -> (screen as PackScreenAccessor).parentScreen
            else -> ShaderGuiHelper.getShaderScreenParent(screen)
        }
        ClientGUI.open(BrowseScreen(type, folder, grandparent))
    }

    private fun pickAndCopyPackFile(type: ProjectType, folder: File) {
        showToast(localize("resourcify.pack.select_zip"), durationMs = 4_000)
        Thread({
            val result = try {
                FileUtil.pickFile(localize("resourcify.pack.select_zip_title"), FileUtil.getDefaultFileSelectionDirectory(), "zip")
            } catch (e: Throwable) {
                VintageResourcify.LOG.warn("File picker failed", e)
                showToast(localize("resourcify.pack.file_picker_failed"), durationMs = 5_000)
                return@Thread
            }
            when (result.status) {
                FileUtil.FilePickerResult.Status.SELECTED -> {
                    val source = result.file
                    if (source == null) {
                        showToast(localize("resourcify.pack.no_file_selected"), durationMs = 4_000)
                        return@Thread
                    }
                    copyPickedPackFile(type, resolveImportFolder(type, folder), source)
                }
                FileUtil.FilePickerResult.Status.CANCELLED ->
                    showToast(localize("resourcify.pack.import_cancelled"), durationMs = 3_000)
                FileUtil.FilePickerResult.Status.UNAVAILABLE,
                FileUtil.FilePickerResult.Status.ERROR ->
                    showToast(localize("resourcify.pack.file_picker_failed"), durationMs = 6_000)
            }
        }, "Resourcify-PackFilePicker").apply { isDaemon = true }.start()
    }

    fun importPackFile(type: ProjectType, folder: File, source: File) {
        showToast(localize("resourcify.pack.importing", source.name), durationMs = 5_000)
        Thread({
            copyPickedPackFile(type, resolveImportFolder(type, folder), source)
        }, "Resourcify-PackFileImport").apply { isDaemon = true }.start()
    }

    private fun copyPickedPackFile(type: ProjectType, folder: File, source: File) {
        try {
            if (!source.isFile || !source.canRead()) {
                showToast(localize("resourcify.pack.import_failed"), durationMs = 5_000)
                return
            }
            if (!source.name.endsWith(".zip", ignoreCase = true)) {
                showToast(localize("resourcify.pack.zip_only"), durationMs = 5_000)
                return
            }
            if (!folder.exists()) folder.mkdirs()
            val target = File(folder, targetFileName(source.name))
            if (sameFile(source, target)) {
                showToast(localize("resourcify.pack.already_in_folder"), durationMs = 4_000)
                runClientSync { refreshHostPackScreen(type) }
                return
            }
            if ((type == ProjectType.RESOURCE_PACK || type == ProjectType.AYCY_RESOURCE_PACK) && target.exists()) {
                runClientSync {
                    try {
                        Platform.closeResourcePack(target)
                    } catch (_: Throwable) {
                    }
                }
            }
            VintageResourcify.LOG.info("Importing pack file {} to {}", source, target)
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            showToast(localize("resourcify.pack.imported", target.name), durationMs = 5_000)
            runClientSync {
                playImportChime()
                refreshHostPackScreen(type)
            }
        } catch (e: Throwable) {
            VintageResourcify.LOG.warn("Could not import pack file {}", source, e)
            showToast(localize("resourcify.pack.import_failed"), durationMs = 5_000)
        }
    }

    private fun resolveImportFolder(type: ProjectType, folder: File): File {
        return when (type) {
            ProjectType.RESOURCE_PACK,
            ProjectType.AYCY_RESOURCE_PACK -> Minecraft.getMinecraft().resourcePackRepository.dirResourcepacks
            ProjectType.IRIS_SHADER,
            ProjectType.OPTIFINE_SHADER -> ShaderGuiHelper.getShaderpacksFolder()
            else -> folder
        }
    }

    private fun targetFileName(sourceName: String): String {
        return if (sourceName.endsWith(".zip", ignoreCase = true) && !sourceName.endsWith(".zip")) {
            sourceName.dropLast(4) + ".zip"
        } else {
            sourceName
        }
    }

    private fun startUpdateCheck(type: ProjectType, folder: File, openPanel: Boolean) {
        if (checkInProgress || updateInProgress || settingsUpdating) return
        checkInProgress = true
        setPanelContext(type, folder)
        synchronized(updateLock) {
            updateEntries.clear()
            updateScroll = 0
        }
        if (openPanel) {
            updateStatusText = localize("resourcify.updates.checking")
        }
        Thread({
            try {
                val found = scanUpdates(type, folder)
                synchronized(updateLock) {
                    updateEntries.clear()
                    updateEntries.addAll(found)
                    updateScroll = 0
                }
                val safe = found.count { it.disposition == UpdateDisposition.SAFE }
                val review = found.count { it.disposition == UpdateDisposition.REVIEW }
                updateStatusText = when {
                    safe > 0 && review > 0 -> localize("resourcify.pack_updates.summary", safe, review)
                    safe > 0 -> localize(
                        if (safe == 1) "resourcify.pack_updates.available.one" else "resourcify.pack_updates.available.many",
                        safe,
                    )
                    review > 0 -> localize(
                        if (review == 1) "resourcify.pack_updates.review.one" else "resourcify.pack_updates.review.many",
                        review,
                    )
                    else -> localize("resourcify.pack_updates.all_up_to_date")
                }
                val chimeGroup = updateChimeGroup(type)
                val safeUpdates = found.filter { it.disposition == UpdateDisposition.SAFE }
                val chimeHash = safeUpdates.takeIf { it.isNotEmpty() }?.let { updateSetHash(it) }
                if (chimeGroup != null && UpdateChimeState.shouldPlay(chimeGroup, chimeHash)) {
                    runClientSync { playUpdateChime() }
                }
            } catch (e: Throwable) {
                VintageResourcify.LOG.warn("Update check failed for {}", folder, e)
                updateStatusText = localize("resourcify.pack_updates.check_failed")
                if (openPanel) showToast(localize("resourcify.pack_updates.check_failed"), durationMs = 5_000)
            } finally {
                checkInProgress = false
            }
        }, "Resourcify-UpdateCheck").apply { isDaemon = true }.start()
    }

    private fun scanUpdates(type: ProjectType, folder: File): List<UpdateEntry> {
        val index = LocalIndex.forFolder(folder)
        val tracked = index.listEntries()
            .mapNotNull { entry -> File(folder, entry.fileName).takeIf { it.exists() }?.let { it to entry } }
        if (tracked.isEmpty()) return emptyList()

        val results = mutableListOf<UpdateEntry>()
        val currentMcVersion = Platform.getMcVersion()
        val byQuery = tracked.groupBy { UpdateQuery(it.second.platform, it.second.updateTrack) }
        for ((query, items) in byQuery) {
            val platform = query.platform
            if (!DistributionPolicy.canDownloadFrom(platform)) {
                VintageResourcify.LOG.info("Skipping updates from platform {} because downloads are disabled for this distribution", platform)
                continue
            }
            val service = ServiceRegistry.getAllServices()
                .firstOrNull { it.getPlatformId() == platform && it.isProjectTypeSupported(type) }
            if (service == null) {
                VintageResourcify.LOG.warn("No service registered for platform {}", platform)
                continue
            }
            val map = try {
                service.getUpdates(items.map { it.first }, type, query.minecraftVersion).join()
            } catch (e: Throwable) {
                VintageResourcify.LOG.warn("getUpdates failed for {}", service.getName(), e)
                continue
            }
            for ((file, version) in map) {
                if (version == null) continue
                val localEntry = items.firstOrNull { sameFile(it.first, file) }?.second ?: continue
                if (!isNewerThanInstalled(version, localEntry)) continue
                val signature = LocalIndex.versionSignature(platform, version)
                val ignored = signature == localEntry.ignoredVersion ||
                    localEntry.ignoredUpdates.orEmpty().any { it.signature == signature }
                if (ignored) continue
                val disposition = when {
                    currentMcVersion in version.getMinecraftVersions() -> UpdateDisposition.SAFE
                    else -> UpdateDisposition.REVIEW
                }
                results.add(UpdateEntry(file, version, localEntry, disposition))
            }
        }
        return results.sortedWith(compareBy<UpdateEntry> { it.disposition.sortOrder }
            .thenBy { it.oldFile.name.lowercase(Locale.ROOT) })
    }

    private fun isNewerThanInstalled(version: IVersion, entry: LocalIndex.Entry): Boolean {
        val installedDate = entry.installedReleaseDate ?: return true
        val candidateDate = version.getReleaseDate()
        if (candidateDate.isBlank()) return true
        return try {
            Instant.parse(candidateDate).isAfter(Instant.parse(installedDate))
        } catch (_: Throwable) {
            candidateDate > installedDate
        }
    }

    private fun loadPackSettings() {
        val type = settingsType ?: return
        val folder = settingsFolder ?: return
        val file = settingsFile ?: return
        val entry = LocalIndex.forFolder(folder).lookupByFileName(file) ?: settingsEntry ?: return
        val service = ServiceRegistry.getAllServices()
            .firstOrNull { it.getPlatformId() == entry.platform && it.isProjectTypeSupported(type) }
        val token = ++settingsRequestToken
        settingsEntry = entry
        settingsLoading = true
        settingsCandidate = null
        settingsStatus = localize("resourcify.pack_settings.loading")
        if (service == null || !DistributionPolicy.canDownloadFrom(entry.platform)) {
            settingsLoading = false
            settingsStatus = localize("resourcify.pack_settings.unavailable")
            return
        }
        Thread({
            val versionNames = try {
                service.getMinecraftVersions().join()
            } catch (e: Throwable) {
                VintageResourcify.LOG.warn("Could not load Minecraft versions for pack settings", e)
                emptyMap()
            }
            val candidate = try {
                service.getUpdates(listOf(file), type, entry.updateTrack).join().entries
                    .firstOrNull { sameFile(it.key, file) }?.value
                    ?.takeIf { isNewerThanInstalled(it, entry) }
            } catch (e: Throwable) {
                VintageResourcify.LOG.warn("Could not check updates for {}", file, e)
                null
            }
            if (token != settingsRequestToken || !packSettingsOpen) return@Thread
            val versions = (versionNames.keys + entry.minecraftVersions.orEmpty() +
                listOfNotNull(entry.updateTrack, Platform.getMcVersion())).distinct()
                .sortedWith(Comparator(::compareMinecraftVersionsDesc))
            settingsTrackNames = versionNames
            settingsTrackOptions = listOf<String?>(null) + versions
            settingsDropdownScroll = settingsDropdownScroll.coerceIn(
                0,
                (settingsTrackOptions.size - SETTINGS_DROPDOWN_ROWS).coerceAtLeast(0),
            )
            settingsCandidate = candidate
            settingsLoading = false
            settingsStatus = when {
                candidate == null -> localize("resourcify.pack_settings.up_to_date")
                isCandidateIgnored(entry, candidate) -> localize("resourcify.pack_settings.up_to_date")
                else -> localize("resourcify.pack_settings.update_available")
            }
            syncCachedCandidate(file, entry, candidate)
        }, "Resourcify-PackSettings").apply { isDaemon = true }.start()
    }

    private fun closePackSettings() {
        packSettingsOpen = false
        settingsDropdownOpen = false
        settingsRequestToken++
    }

    private fun drawPackSettings(screen: GuiScreen, mouseX: Int, mouseY: Int) {
        val fr = Minecraft.getMinecraft().fontRenderer ?: return
        val style = ResourcifyStyle.palette("default")
        val layout = settingsLayout(screen)
        Gui.drawRect(0, 0, screen.width, screen.height, UPDATE_SCREEN_SCRIM)
        Gui.drawRect(layout.panelX, layout.panelY, layout.panelX + layout.panelW, layout.panelY + layout.panelH, style.panelRaised)

        val fileName = settingsFile?.name.orEmpty()
        settingsFile?.let { PackOverlayRenderer.drawPackIcon(it, layout.panelX + PANEL_PADDING, layout.panelY + 7, 32) }
        val headingX = layout.panelX + PANEL_PADDING + 40
        fr.drawString(localize("resourcify.pack_settings.title"), headingX, layout.panelY + 9, style.textPrimary, false)
        fr.drawString(trimToWidth(fr, fileName, layout.panelW - PANEL_PADDING * 2 - 40), headingX, layout.panelY + 21, style.textSecondary, false)

        fr.drawString(localize("resourcify.pack_settings.track_label"), layout.panelX + PANEL_PADDING, layout.trackY + 7, style.textSecondary, false)
        val track = settingsEntry?.updateTrack
        val trackText = trackDisplayName(track) + "  v"
        drawTextButton(
            layout.trackX, layout.trackY, layout.trackW, TEXT_BUTTON_HEIGHT,
            trimToWidth(fr, trackText, layout.trackW - 8),
            enabled = !settingsLoading && !settingsUpdating,
            hover = !settingsLoading && !settingsUpdating && isInside(mouseX, mouseY, layout.trackX, layout.trackY, layout.trackW, TEXT_BUTTON_HEIGHT),
            style = style,
        )

        drawTextButton(layout.updatesTabX, layout.tabsY, layout.tabW, TEXT_BUTTON_HEIGHT,
            localize("resourcify.pack_settings.updates_tab"), true,
            settingsTab == SettingsTab.UPDATES || isInside(mouseX, mouseY, layout.updatesTabX, layout.tabsY, layout.tabW, TEXT_BUTTON_HEIGHT), style)
        drawTextButton(layout.ignoredTabX, layout.tabsY, layout.tabW, TEXT_BUTTON_HEIGHT,
            localize("resourcify.pack_settings.ignored_tab_count", settingsIgnoredUpdates().size), true,
            settingsTab == SettingsTab.IGNORED || isInside(mouseX, mouseY, layout.ignoredTabX, layout.tabsY, layout.tabW, TEXT_BUTTON_HEIGHT), style)

        Gui.drawRect(layout.contentX, layout.contentY, layout.contentX + layout.contentW, layout.contentY + layout.contentH, style.panelInset)
        if (settingsTab == SettingsTab.UPDATES) {
            drawSettingsUpdateContent(layout, mouseX, mouseY, style)
        } else {
            drawSettingsIgnoredContent(layout, mouseX, mouseY, style)
        }

        if (settingsUpdating) {
            val progress = currentDownloadUrl?.let { DownloadManager.getProgress(it) } ?: 0f
            drawProgressBar(layout.panelX + PANEL_PADDING, layout.bottomY + 7, layout.panelW - PANEL_PADDING * 2 - 66, progress, style)
        } else {
            drawTextButton(layout.checkX, layout.bottomY, layout.checkW, TEXT_BUTTON_HEIGHT,
                localize("resourcify.pack_updates.check"), !settingsLoading,
                !settingsLoading && isInside(mouseX, mouseY, layout.checkX, layout.bottomY, layout.checkW, TEXT_BUTTON_HEIGHT), style)
        }
        drawTextButton(layout.closeX, layout.bottomY, layout.closeW, TEXT_BUTTON_HEIGHT,
            localize("resourcify.screens.close"), !settingsUpdating,
            !settingsUpdating && isInside(mouseX, mouseY, layout.closeX, layout.bottomY, layout.closeW, TEXT_BUTTON_HEIGHT), style)

        if (settingsDropdownOpen) drawSettingsTrackDropdown(layout, mouseX, mouseY, style)
    }

    private fun drawSettingsUpdateContent(
        layout: SettingsLayout,
        mouseX: Int,
        mouseY: Int,
        style: ResourcifyStyle.Palette,
    ) {
        val fr = Minecraft.getMinecraft().fontRenderer ?: return
        val x = layout.contentX + 8
        val y = layout.contentY + 8
        val candidate = settingsCandidate
        val ignored = candidate != null && settingsEntry?.let { isCandidateIgnored(it, candidate) } == true
        if (settingsLoading || settingsUpdating || candidate == null || ignored) {
            fr.drawString(trimToWidth(fr, settingsStatus, layout.contentW - 16), x, y, style.textSecondary, false)
            return
        }
        fr.drawString(trimToWidth(fr, versionLabel(candidate), layout.contentW - 16), x, y, style.textPrimary, false)
        fr.drawString(
            trimToWidth(
                fr,
                localize("resourcify.pack_settings.marked_for", minecraftVersionsLabel(candidate.getMinecraftVersions())),
                layout.contentW - 16,
            ),
            x,
            y + 14,
            style.textSecondary,
            false,
        )
        val safe = Platform.getMcVersion() in candidate.getMinecraftVersions()
        fr.drawString(
            localize(if (safe) "resourcify.pack_settings.safe_update" else "resourcify.pack_settings.review_update"),
            x,
            y + 29,
            if (safe) style.accent else 0xFFFFC266.toInt(),
            false,
        )
        val buttonY = layout.contentY + layout.contentH - TEXT_BUTTON_HEIGHT - 7
        drawTextButton(x, buttonY, 112, TEXT_BUTTON_HEIGHT, localize("resourcify.pack_updates.ignore_version"), true,
            isInside(mouseX, mouseY, x, buttonY, 112, TEXT_BUTTON_HEIGHT), style)
        val updateW = 96
        val updateX = layout.contentX + layout.contentW - updateW - 8
        drawTextButton(updateX, buttonY, updateW, TEXT_BUTTON_HEIGHT,
            localize(if (safe) "resourcify.updates.update" else "resourcify.pack_updates.update_anyway"), true,
            isInside(mouseX, mouseY, updateX, buttonY, updateW, TEXT_BUTTON_HEIGHT), style)
    }

    private fun drawSettingsIgnoredContent(
        layout: SettingsLayout,
        mouseX: Int,
        mouseY: Int,
        style: ResourcifyStyle.Palette,
    ) {
        val fr = Minecraft.getMinecraft().fontRenderer ?: return
        val ignored = settingsIgnoredUpdates()
        if (ignored.isEmpty()) {
            fr.drawString(localize("resourcify.pack_settings.no_ignored"), layout.contentX + 8, layout.contentY + 8, style.textSecondary, false)
            return
        }
        val visibleRows = (layout.contentH / SETTINGS_IGNORED_ROW_HEIGHT).coerceAtLeast(1)
        settingsIgnoredScroll = settingsIgnoredScroll.coerceIn(0, (ignored.size - visibleRows).coerceAtLeast(0))
        ignored.drop(settingsIgnoredScroll).take(visibleRows).forEachIndexed { index, ignoredUpdate ->
            val rowY = layout.contentY + index * SETTINGS_IGNORED_ROW_HEIGHT
            Gui.drawRect(layout.contentX, rowY, layout.contentX + layout.contentW, rowY + SETTINGS_IGNORED_ROW_HEIGHT - 1,
                if (index % 2 == 0) style.rowIdle else style.rowDisabled)
            val restoreW = 70
            val textW = layout.contentW - restoreW - 22
            fr.drawString(trimToWidth(fr, ignoredVersionLabel(ignoredUpdate), textW), layout.contentX + 7, rowY + 7, style.textPrimary, false)
            fr.drawString(trimToWidth(fr, minecraftVersionsLabel(ignoredUpdate.minecraftVersions), textW), layout.contentX + 7, rowY + 20, style.textSecondary, false)
            val restoreX = layout.contentX + layout.contentW - restoreW - 7
            val restoreY = rowY + 8
            drawTextButton(restoreX, restoreY, restoreW, TEXT_BUTTON_HEIGHT, localize("resourcify.pack_updates.restore"), true,
                isInside(mouseX, mouseY, restoreX, restoreY, restoreW, TEXT_BUTTON_HEIGHT), style)
        }
    }

    private fun drawSettingsTrackDropdown(layout: SettingsLayout, mouseX: Int, mouseY: Int, style: ResourcifyStyle.Palette) {
        val fr = Minecraft.getMinecraft().fontRenderer ?: return
        val options = settingsTrackOptions.drop(settingsDropdownScroll).take(SETTINGS_DROPDOWN_ROWS)
        val height = options.size * SETTINGS_DROPDOWN_ROW_HEIGHT + 2
        val hasScroll = settingsTrackOptions.size > SETTINGS_DROPDOWN_ROWS
        val optionW = layout.trackW - if (hasScroll) 7 else 2
        Gui.drawRect(layout.trackX, layout.trackY + TEXT_BUTTON_HEIGHT, layout.trackX + layout.trackW,
            layout.trackY + TEXT_BUTTON_HEIGHT + height, style.popup)
        options.forEachIndexed { index, track ->
            val y = layout.trackY + TEXT_BUTTON_HEIGHT + 1 + index * SETTINGS_DROPDOWN_ROW_HEIGHT
            drawTextButton(layout.trackX + 1, y, optionW, SETTINGS_DROPDOWN_ROW_HEIGHT,
                trimToWidth(fr, trackDisplayName(track), optionW - 6), true,
                isInside(mouseX, mouseY, layout.trackX + 1, y, optionW, SETTINGS_DROPDOWN_ROW_HEIGHT), style)
        }
        if (hasScroll) {
            val barX = layout.trackX + layout.trackW - 5
            val barY = layout.trackY + TEXT_BUTTON_HEIGHT + 2
            val barH = (height - 4).coerceAtLeast(1)
            Gui.drawRect(barX, barY, barX + 3, barY + barH, ResourcifyStyle.SODIUM_SCROLLBAR_TRACK)
            val thumbH = (barH * SETTINGS_DROPDOWN_ROWS / settingsTrackOptions.size).coerceAtLeast(8)
            val maxScroll = settingsTrackOptions.size - SETTINGS_DROPDOWN_ROWS
            val thumbY = barY + (barH - thumbH) * settingsDropdownScroll / maxScroll
            Gui.drawRect(barX, thumbY, barX + 3, thumbY + thumbH, ResourcifyStyle.SODIUM_SCROLLBAR_THUMB)
        }
    }

    private fun handlePackSettingsClick(mouseX: Int, mouseY: Int): Boolean {
        val screen = Minecraft.getMinecraft().currentScreen ?: return true
        val layout = settingsLayout(screen)
        if (!isInside(mouseX, mouseY, layout.panelX, layout.panelY, layout.panelW, layout.panelH)) {
            if (!settingsUpdating) closePackSettings()
            return true
        }

        if (settingsDropdownOpen) {
            if (isInside(mouseX, mouseY, layout.trackX, layout.trackY, layout.trackW, TEXT_BUTTON_HEIGHT)) {
                playDefaultButtonSound()
                settingsDropdownOpen = false
                return true
            }
            val options = settingsTrackOptions.drop(settingsDropdownScroll).take(SETTINGS_DROPDOWN_ROWS)
            val dropdownY = layout.trackY + TEXT_BUTTON_HEIGHT + 1
            options.forEachIndexed { index, track ->
                val optionY = dropdownY + index * SETTINGS_DROPDOWN_ROW_HEIGHT
                if (isInside(mouseX, mouseY, layout.trackX + 1, optionY, layout.trackW - 2, SETTINGS_DROPDOWN_ROW_HEIGHT)) {
                    playDefaultButtonSound()
                    val file = settingsFile
                    val folder = settingsFolder
                    if (file != null && folder != null) {
                        LocalIndex.forFolder(folder).setUpdateTrack(file.name, track)
                        settingsEntry = settingsEntry?.copy(updateTrack = track)
                        removeCachedCandidate(file)
                    }
                    settingsDropdownOpen = false
                    loadPackSettings()
                    return true
                }
            }
            settingsDropdownOpen = false
            return true
        }

        if (!settingsLoading && !settingsUpdating &&
            isInside(mouseX, mouseY, layout.trackX, layout.trackY, layout.trackW, TEXT_BUTTON_HEIGHT)) {
            playDefaultButtonSound()
            settingsDropdownOpen = true
            val selected = settingsTrackOptions.indexOf(settingsEntry?.updateTrack).coerceAtLeast(0)
            settingsDropdownScroll = (selected - SETTINGS_DROPDOWN_ROWS / 2).coerceIn(
                0,
                (settingsTrackOptions.size - SETTINGS_DROPDOWN_ROWS).coerceAtLeast(0),
            )
            return true
        }

        if (isInside(mouseX, mouseY, layout.updatesTabX, layout.tabsY, layout.tabW, TEXT_BUTTON_HEIGHT)) {
            playDefaultButtonSound()
            settingsTab = SettingsTab.UPDATES
            settingsIgnoredScroll = 0
            return true
        }
        if (isInside(mouseX, mouseY, layout.ignoredTabX, layout.tabsY, layout.tabW, TEXT_BUTTON_HEIGHT)) {
            playDefaultButtonSound()
            settingsTab = SettingsTab.IGNORED
            settingsIgnoredScroll = 0
            return true
        }

        if (!settingsLoading && !settingsUpdating && settingsTab == SettingsTab.UPDATES) {
            val candidate = settingsCandidate
            if (candidate != null && settingsEntry?.let { !isCandidateIgnored(it, candidate) } == true) {
                val buttonY = layout.contentY + layout.contentH - TEXT_BUTTON_HEIGHT - 7
                val ignoreX = layout.contentX + 8
                if (isInside(mouseX, mouseY, ignoreX, buttonY, 112, TEXT_BUTTON_HEIGHT)) {
                    playDefaultButtonSound()
                    ignoreSettingsCandidate(candidate)
                    return true
                }
                val updateW = 96
                val updateX = layout.contentX + layout.contentW - updateW - 8
                if (isInside(mouseX, mouseY, updateX, buttonY, updateW, TEXT_BUTTON_HEIGHT)) {
                    playDefaultButtonSound()
                    startSettingsUpdate(candidate)
                    return true
                }
            }
        }

        if (!settingsUpdating && settingsTab == SettingsTab.IGNORED) {
            val ignored = settingsIgnoredUpdates()
            val visibleRows = (layout.contentH / SETTINGS_IGNORED_ROW_HEIGHT).coerceAtLeast(1)
            ignored.drop(settingsIgnoredScroll).take(visibleRows).forEachIndexed { index, ignoredUpdate ->
                val rowY = layout.contentY + index * SETTINGS_IGNORED_ROW_HEIGHT
                val restoreW = 70
                val restoreX = layout.contentX + layout.contentW - restoreW - 7
                if (isInside(mouseX, mouseY, restoreX, rowY + 8, restoreW, TEXT_BUTTON_HEIGHT)) {
                    playDefaultButtonSound()
                    restoreSettingsUpdate(ignoredUpdate.signature)
                    return true
                }
            }
        }

        if (!settingsLoading && !settingsUpdating &&
            isInside(mouseX, mouseY, layout.checkX, layout.bottomY, layout.checkW, TEXT_BUTTON_HEIGHT)) {
            playDefaultButtonSound()
            loadPackSettings()
            return true
        }
        if (!settingsUpdating && isInside(mouseX, mouseY, layout.closeX, layout.bottomY, layout.closeW, TEXT_BUTTON_HEIGHT)) {
            playDefaultButtonSound()
            closePackSettings()
        }
        return true
    }

    private fun handlePackSettingsMouseInput(): Boolean {
        if (!isSettingsVisibleOnCurrentScreen()) {
            closePackSettings()
            return false
        }
        val wheel = Mouse.getEventDWheel()
        // Let ordinary press/release events continue to GuiScreen.mouseClicked,
        // where the modal routes and consumes them. Only intercept wheel input.
        if (wheel == 0) return false
        if (settingsDropdownOpen) {
            val max = (settingsTrackOptions.size - SETTINGS_DROPDOWN_ROWS).coerceAtLeast(0)
            settingsDropdownScroll = (settingsDropdownScroll - wheelDirection(wheel)).coerceIn(0, max)
        } else if (settingsTab == SettingsTab.IGNORED) {
            val screen = Minecraft.getMinecraft().currentScreen ?: return true
            val visibleRows = (settingsLayout(screen).contentH / SETTINGS_IGNORED_ROW_HEIGHT).coerceAtLeast(1)
            val max = (settingsIgnoredUpdates().size - visibleRows).coerceAtLeast(0)
            settingsIgnoredScroll = (settingsIgnoredScroll - wheelDirection(wheel)).coerceIn(0, max)
        }
        return true
    }

    private fun ignoreSettingsCandidate(candidate: IVersion) {
        val folder = settingsFolder ?: return
        val file = settingsFile ?: return
        val entry = settingsEntry ?: return
        LocalIndex.forFolder(folder).ignoreVersion(file.name, entry.platform, candidate)
        settingsEntry = LocalIndex.forFolder(folder).lookupByFileName(file)
        val signature = LocalIndex.versionSignature(entry.platform, candidate)
        synchronized(updateLock) {
            updateEntries.removeAll {
                it.oldFile.name == file.name && it.version?.let { version ->
                    LocalIndex.versionSignature(entry.platform, version) == signature
                } == true
            }
        }
        settingsStatus = localize("resourcify.pack_settings.up_to_date")
    }

    private fun ignoreCombinedUpdate(entry: UpdateEntry, folder: File) {
        val version = entry.version ?: return
        LocalIndex.forFolder(folder).ignoreVersion(entry.oldFile.name, entry.localEntry.platform, version)
        synchronized(updateLock) { updateEntries.remove(entry) }
        refreshCachedUpdateStatus()
    }

    private fun restoreSettingsUpdate(signature: String) {
        val folder = settingsFolder ?: return
        val file = settingsFile ?: return
        LocalIndex.forFolder(folder).restoreVersion(file.name, signature)
        settingsEntry = LocalIndex.forFolder(folder).lookupByFileName(file)
        settingsStatus = localize("resourcify.pack_settings.update_restored")
        val candidate = settingsCandidate
        val localEntry = settingsEntry
        if (candidate != null && localEntry != null && !isCandidateIgnored(localEntry, candidate)) {
            syncCachedCandidate(file, localEntry, candidate)
        }
    }

    private fun removeCachedCandidate(file: File) {
        synchronized(updateLock) { updateEntries.removeAll { sameFile(it.oldFile, file) } }
        refreshCachedUpdateStatus()
    }

    private fun syncCachedCandidate(file: File, localEntry: LocalIndex.Entry, candidate: IVersion?) {
        synchronized(updateLock) {
            updateEntries.removeAll { sameFile(it.oldFile, file) }
            if (candidate != null && !isCandidateIgnored(localEntry, candidate) && isNewerThanInstalled(candidate, localEntry)) {
                val disposition = if (Platform.getMcVersion() in candidate.getMinecraftVersions()) {
                    UpdateDisposition.SAFE
                } else {
                    UpdateDisposition.REVIEW
                }
                updateEntries.add(UpdateEntry(file, candidate, localEntry, disposition))
                updateEntries.sortWith(compareBy<UpdateEntry> { it.disposition.sortOrder }
                    .thenBy { it.oldFile.name.lowercase(Locale.ROOT) })
            }
        }
        refreshCachedUpdateStatus()
    }

    private fun refreshCachedUpdateStatus() {
        val entries = entriesSnapshot()
        val safe = entries.count { it.disposition == UpdateDisposition.SAFE }
        val review = entries.count { it.disposition == UpdateDisposition.REVIEW }
        updateStatusText = when {
            safe > 0 && review > 0 -> localize("resourcify.pack_updates.summary", safe, review)
            safe > 0 -> localize(
                if (safe == 1) "resourcify.pack_updates.available.one"
                else "resourcify.pack_updates.available.many",
                safe,
            )
            review > 0 -> localize(
                if (review == 1) "resourcify.pack_updates.review.one"
                else "resourcify.pack_updates.review.many",
                review,
            )
            else -> localize("resourcify.pack_updates.all_up_to_date")
        }
    }

    private fun startSettingsUpdate(candidate: IVersion) {
        val type = settingsType ?: return
        val folder = settingsFolder ?: return
        val file = settingsFile ?: return
        val localEntry = settingsEntry ?: return
        settingsUpdating = true
        settingsStatus = localize("resourcify.pack_updates.status.updating")
        val wasEnabled = when (type) {
            ProjectType.RESOURCE_PACK, ProjectType.AYCY_RESOURCE_PACK -> Platform.isResourcePackEnabled(file)
            ProjectType.IRIS_SHADER, ProjectType.OPTIFINE_SHADER -> ShaderGuiHelper.isShaderPackEnabled(file)
            else -> false
        }
        val disposition = if (Platform.getMcVersion() in candidate.getMinecraftVersions()) {
            UpdateDisposition.SAFE
        } else UpdateDisposition.REVIEW
        val updateEntry = UpdateEntry(file, candidate, localEntry, disposition)
        Thread({
            val result = performUpdate(PreparedUpdate(updateEntry, wasEnabled), type, folder)
            settingsUpdating = false
            settingsStatus = when (result) {
                UpdateStatus.UPDATED -> localize("resourcify.pack_updates.status.updated")
                UpdateStatus.FAILED -> localize("resourcify.pack_updates.status.failed")
                UpdateStatus.CANCELLED -> localize("resourcify.pack_updates.status.cancelled")
                else -> settingsStatus
            }
            if (result == UpdateStatus.UPDATED) {
                runClientSync { playDownloadChime() }
                val newFile = File(folder, candidate.getFileName())
                settingsFile = newFile
                settingsEntry = LocalIndex.forFolder(folder).lookupByFileName(newFile)
                synchronized(updateLock) { updateEntries.removeAll { it.oldFile.name == file.name } }
                loadPackSettings()
            }
        }, "Resourcify-PackSettingsUpdate").apply { isDaemon = true }.start()
    }

    private fun settingsIgnoredUpdates(): List<LocalIndex.IgnoredUpdate> {
        val entry = settingsEntry ?: return emptyList()
        val updates = entry.ignoredUpdates.orEmpty().toMutableList()
        val legacy = entry.ignoredVersion
        if (legacy != null && updates.none { it.signature == legacy }) {
            val candidate = settingsCandidate
            if (candidate != null && LocalIndex.versionSignature(entry.platform, candidate) == legacy) {
                updates.add(candidate.toIgnoredUpdate(entry.platform))
            } else {
                updates.add(LocalIndex.IgnoredUpdate(legacy, localize("resourcify.pack_settings.unknown_update"), null, "", emptyList(), ""))
            }
        }
        return updates.sortedByDescending { it.releaseDate }
    }

    private fun IVersion.toIgnoredUpdate(platform: String) = LocalIndex.IgnoredUpdate(
        LocalIndex.versionSignature(platform, this), getName(), getVersionNumber(), getFileName(),
        getMinecraftVersions(), getReleaseDate(),
    )

    private fun isCandidateIgnored(entry: LocalIndex.Entry, candidate: IVersion): Boolean {
        val signature = LocalIndex.versionSignature(entry.platform, candidate)
        return entry.ignoredVersion == signature || entry.ignoredUpdates.orEmpty().any { it.signature == signature }
    }

    private fun ignoredVersionLabel(update: LocalIndex.IgnoredUpdate): String = when {
        update.versionNumber.isNullOrBlank() -> update.name
        update.name.isBlank() || update.name == update.versionNumber -> update.versionNumber
        else -> "${update.name} (${update.versionNumber})"
    }

    private fun trackDisplayName(track: String?): String {
        if (track == null) return localize("resourcify.pack_settings.latest_release")
        return settingsTrackNames[track]?.takeIf { it.isNotBlank() } ?: "Minecraft $track"
    }

    private fun settingsLayout(screen: GuiScreen): SettingsLayout {
        val panelW = SETTINGS_PANEL_WIDTH.coerceAtMost(screen.width - PANEL_MARGIN * 2).coerceAtLeast(250)
        val panelH = SETTINGS_PANEL_HEIGHT.coerceAtMost(screen.height - PANEL_MARGIN * 2).coerceAtLeast(210)
        val panelX = (screen.width - panelW) / 2
        val panelY = (screen.height - panelH) / 2
        val trackLabelW = 105
        val trackX = panelX + PANEL_PADDING + trackLabelW
        val trackW = 190.coerceAtMost(panelW - PANEL_PADDING * 2 - trackLabelW)
        val tabsY = panelY + 68
        val tabW = 90
        val contentX = panelX + PANEL_PADDING
        val contentY = tabsY + TEXT_BUTTON_HEIGHT + 4
        val bottomY = panelY + panelH - TEXT_BUTTON_HEIGHT - PANEL_PADDING
        val contentH = (bottomY - contentY - 6).coerceAtLeast(54)
        return SettingsLayout(
            panelX, panelY, panelW, panelH,
            trackX, panelY + 39, trackW,
            tabsY, panelX + PANEL_PADDING, panelX + PANEL_PADDING + tabW + 4, tabW,
            contentX, contentY, panelW - PANEL_PADDING * 2, contentH,
            panelX + PANEL_PADDING, 58,
            panelX + panelW - PANEL_PADDING - 58, 58,
            bottomY,
        )
    }

    private fun drawDirectUpdatePopup(screen: GuiScreen) {
        val fr = Minecraft.getMinecraft().fontRenderer ?: return
        val style = ResourcifyStyle.palette("default")
        val panelW = 310.coerceAtMost(screen.width - PANEL_MARGIN * 2).coerceAtLeast(230)
        val panelH = 86
        val panelX = (screen.width - panelW) / 2
        val panelY = (screen.height - panelH) / 2
        Gui.drawRect(0, 0, screen.width, screen.height, UPDATE_SCREEN_SCRIM)
        Gui.drawRect(panelX, panelY, panelX + panelW, panelY + panelH, style.panelRaised)
        val file = directUpdateFile
        if (file != null) PackOverlayRenderer.drawPackIcon(file, panelX + 10, panelY + 11, 48)
        val textX = panelX + 68
        val textW = panelW - 78
        fr.drawString(
            trimToWidth(fr, file?.name.orEmpty(), textW),
            textX,
            panelY + 13,
            style.textPrimary,
            false,
        )
        fr.drawString(
            trimToWidth(fr, directUpdateStatus, textW),
            textX,
            panelY + 29,
            style.textSecondary,
            false,
        )
        val progress = currentDownloadUrl?.let { DownloadManager.getProgress(it) } ?: 0f
        drawProgressBar(textX, panelY + 51, textW, progress, style)
    }

    private fun drawUpdatePanel(type: ProjectType, folder: File, screen: GuiScreen, mouseX: Int, mouseY: Int) {
        val mc = Minecraft.getMinecraft()
        val fr = mc.fontRenderer ?: return
        val style = ResourcifyStyle.palette("default")
        val panelW = PANEL_WIDTH.coerceAtMost(screen.width - PANEL_MARGIN * 2).coerceAtLeast(240)
        val panelH = PANEL_HEIGHT.coerceAtMost(screen.height - PANEL_MARGIN * 2).coerceAtLeast(170)
        val panelX = (screen.width - panelW) / 2
        val panelY = (screen.height - panelH) / 2

        Gui.drawRect(0, 0, screen.width, screen.height, UPDATE_SCREEN_SCRIM)
        Gui.drawRect(panelX, panelY, panelX + panelW, panelY + panelH, style.panelRaised)

        fr.drawString(localize("resourcify.pack_updates.title"), panelX + PANEL_PADDING, panelY + 8, style.textPrimary, false)
        val status = when {
            updateInProgress -> updateStatusText
            checkInProgress -> localize("resourcify.updates.checking")
            else -> updateStatusText
        }
        if (status.isNotBlank()) {
            fr.drawString(trimToWidth(fr, status, panelW - PANEL_PADDING * 2 - 120), panelX + PANEL_PADDING, panelY + 20, style.textSecondary, false)
        }

        val listX = panelX + PANEL_PADDING
        val listY = panelY + 36
        val listW = panelW - PANEL_PADDING * 2
        val listH = panelH - 78
        val entries = entriesSnapshot()
        val maxScroll = maxScroll(entries.size, listH)
        updateScroll = updateScroll.coerceIn(0, maxScroll)

        Gui.drawRect(listX, listY, listX + listW, listY + listH, style.panelInset)
        beginScissor(listX, listY, listW, listH, screen)
        try {
            if (entries.isEmpty()) {
                val text = if (checkInProgress) {
                    localize("resourcify.pack_updates.checking_short")
                } else {
                    localize("resourcify.pack_updates.none_available")
                }
                fr.drawString(text, listX + 8, listY + 8, style.textSecondary, false)
            } else {
                val first = updateScroll
                val last = (first + listH / ROW_HEIGHT + 2).coerceAtMost(entries.size)
                for (index in first until last) {
                    drawUpdateEntry(entries[index], index, listX, listY + (index - first) * ROW_HEIGHT, listW, mouseX, mouseY, style)
                }
            }
        } finally {
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
        }
        drawScrollBar(listX, listY, listW, listH, entries.size, maxScroll)

        if (updateInProgress) {
            val cancelW = 74
            val buttonY = panelY + panelH - TEXT_BUTTON_HEIGHT - PANEL_PADDING
            val barX = panelX + PANEL_PADDING
            val barY = buttonY + (TEXT_BUTTON_HEIGHT - PROGRESS_HEIGHT) / 2
            val barW = panelW - PANEL_PADDING * 3 - cancelW - 8
            drawProgressBar(barX, barY, barW, currentProgress(), style)
            drawTextButton(
                panelX + panelW - PANEL_PADDING - cancelW,
                buttonY,
                cancelW,
                TEXT_BUTTON_HEIGHT,
                localize("gui.cancel"),
                enabled = true,
                hover = isInside(mouseX, mouseY, panelX + panelW - PANEL_PADDING - cancelW, buttonY, cancelW, TEXT_BUTTON_HEIGHT),
                style = style,
            )
        } else {
            val buttons = bottomButtons(panelX, panelY, panelW, panelH)
            val canUpdate = updatableEntries().isNotEmpty()
            drawTextButton(
                buttons.updateAllX,
                buttons.y,
                buttons.updateAllW,
                TEXT_BUTTON_HEIGHT,
                localize("resourcify.updates.update_all"),
                enabled = canUpdate,
                hover = canUpdate && isInside(mouseX, mouseY, buttons.updateAllX, buttons.y, buttons.updateAllW, TEXT_BUTTON_HEIGHT),
                style = style,
            )
            drawTextButton(
                buttons.checkX,
                buttons.y,
                buttons.checkW,
                TEXT_BUTTON_HEIGHT,
                buttons.checkLabel,
                enabled = !checkInProgress,
                hover = !checkInProgress && isInside(mouseX, mouseY, buttons.checkX, buttons.y, buttons.checkW, TEXT_BUTTON_HEIGHT),
                style = style,
            )
            drawTextButton(
                buttons.closeX,
                buttons.y,
                buttons.closeW,
                TEXT_BUTTON_HEIGHT,
                localize("resourcify.screens.close"),
                enabled = true,
                hover = isInside(mouseX, mouseY, buttons.closeX, buttons.y, buttons.closeW, TEXT_BUTTON_HEIGHT),
                style = style,
            )
        }
    }

    private fun drawUpdateEntry(entry: UpdateEntry, index: Int, x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int, style: ResourcifyStyle.Palette) {
        val fr = Minecraft.getMinecraft().fontRenderer ?: return
        val hovered = isInside(mouseX, mouseY, x, y, width, ROW_HEIGHT)
        val rowColor = when {
            hovered -> style.rowHover
            index % 2 == 0 -> style.rowIdle
            else -> style.rowDisabled
        }
        Gui.drawRect(x, y, x + width, y + ROW_HEIGHT - 1, rowColor)
        val buttonEnabled = !updateInProgress && entry.version != null &&
            (entry.disposition == UpdateDisposition.SAFE || entry.disposition == UpdateDisposition.REVIEW) &&
            entry.status != UpdateStatus.UPDATED && entry.status != UpdateStatus.UPDATING
        val buttonX = x + width - BUTTON_SIZE - 6
        val ignoreX = buttonX - BUTTON_SIZE - 4
        val buttonY = y + (ROW_HEIGHT - BUTTON_SIZE) / 2
        val iconX = x + 6
        val iconY = y + (ROW_HEIGHT - 32) / 2
        val textX = iconX + 38
        val textW = ignoreX - textX - 6

        PackOverlayRenderer.drawPackIcon(entry.oldFile, iconX, iconY, 32)
        fr.drawString(trimToWidth(fr, entry.oldFile.name, textW), textX, y + 5, style.textPrimary, false)
        val version = entry.version?.let { localize("resourcify.pack_updates.to_version", versionLabel(it)) }
            ?: localize("resourcify.pack_updates.current_version")
        fr.drawString(trimToWidth(fr, version, textW), textX, y + 17, style.textSecondary, false)

        val status = statusLabel(entry)
        if (status.isNotEmpty()) {
            val statusColor = when (entry.status) {
                UpdateStatus.UPDATED -> style.accent
                UpdateStatus.FAILED, UpdateStatus.CANCELLED -> 0xFFFF7777.toInt()
                else -> if (entry.disposition == UpdateDisposition.REVIEW) 0xFFFFC266.toInt() else style.textSecondary
            }
            fr.drawString(trimToWidth(fr, status, textW), textX, y + 30, statusColor, false)
        }
        drawEyeOffButton(
            ignoreX,
            buttonY,
            mouseX,
            mouseY,
            enabled = buttonEnabled,
            tooltip = localize("resourcify.pack_updates.ignore_version").takeIf { buttonEnabled },
            accent = style.accent,
        )
        drawIconButton(
            buttonX,
            buttonY,
            UPDATE_TEXTURE,
            mouseX,
            mouseY,
            enabled = buttonEnabled,
            tooltip = localize(
                if (entry.disposition == UpdateDisposition.REVIEW) "resourcify.pack_updates.update_anyway"
                else "resourcify.updates.update"
            ).takeIf { buttonEnabled },
            accent = style.accent,
        )
    }

    private fun handleUpdatePanelClick(mouseX: Int, mouseY: Int, type: ProjectType, folder: File): Boolean {
        val screen = Minecraft.getMinecraft().currentScreen ?: return true
        val panelW = PANEL_WIDTH.coerceAtMost(screen.width - PANEL_MARGIN * 2).coerceAtLeast(240)
        val panelH = PANEL_HEIGHT.coerceAtMost(screen.height - PANEL_MARGIN * 2).coerceAtLeast(170)
        val panelX = (screen.width - panelW) / 2
        val panelY = (screen.height - panelH) / 2
        if (!isInside(mouseX, mouseY, panelX, panelY, panelW, panelH)) {
            if (!updateInProgress) updatePanelOpen = false
            return true
        }

        if (updateInProgress) {
            val cancelW = 74
            val cancelX = panelX + panelW - PANEL_PADDING - cancelW
            val cancelY = panelY + panelH - TEXT_BUTTON_HEIGHT - PANEL_PADDING
            if (isInside(mouseX, mouseY, cancelX, cancelY, cancelW, TEXT_BUTTON_HEIGHT)) {
                playDefaultButtonSound()
                updateCancelRequested = true
                updateStatusText = localize("resourcify.pack_updates.cancelling")
                currentDownloadUrl?.let { DownloadManager.cancelDownload(it) }
                activeDownload?.cancel(false)
            }
            return true
        }

        val listX = panelX + PANEL_PADDING
        val listY = panelY + 36
        val listW = panelW - PANEL_PADDING * 2
        val listH = panelH - 78
        val entries = entriesSnapshot()
        val first = updateScroll.coerceIn(0, maxScroll(entries.size, listH))
        val last = (first + listH / ROW_HEIGHT + 2).coerceAtMost(entries.size)
        for (index in first until last) {
            val entry = entries[index]
            val rowY = listY + (index - first) * ROW_HEIGHT
            val buttonX = listX + listW - BUTTON_SIZE - 6
            val ignoreX = buttonX - BUTTON_SIZE - 4
            val buttonY = rowY + (ROW_HEIGHT - BUTTON_SIZE) / 2
            if (entry.status != UpdateStatus.UPDATED &&
                entry.status != UpdateStatus.UPDATING &&
                entry.version != null &&
                isInside(mouseX, mouseY, ignoreX, buttonY, BUTTON_SIZE, BUTTON_SIZE)) {
                playDefaultButtonSound()
                ignoreCombinedUpdate(entry, folder)
                return true
            }
            if (entry.status != UpdateStatus.UPDATED &&
                entry.status != UpdateStatus.UPDATING &&
                entry.version != null &&
                (entry.disposition == UpdateDisposition.SAFE || entry.disposition == UpdateDisposition.REVIEW) &&
                isInside(mouseX, mouseY, buttonX, buttonY, BUTTON_SIZE, BUTTON_SIZE)) {
                playDefaultButtonSound()
                startUpdate(listOf(entry), type, folder)
                return true
            }
        }

        val buttons = bottomButtons(panelX, panelY, panelW, panelH)
        if (isInside(mouseX, mouseY, buttons.updateAllX, buttons.y, buttons.updateAllW, TEXT_BUTTON_HEIGHT)) {
            val targets = updatableEntries()
            if (targets.isNotEmpty()) {
                playDefaultButtonSound()
                startUpdate(targets, type, folder)
            }
            return true
        }

        if (isInside(mouseX, mouseY, buttons.checkX, buttons.y, buttons.checkW, TEXT_BUTTON_HEIGHT)) {
            if (!checkInProgress) {
                playDefaultButtonSound()
                startUpdateCheck(type, folder, openPanel = true)
            }
            return true
        }

        if (isInside(mouseX, mouseY, buttons.closeX, buttons.y, buttons.closeW, TEXT_BUTTON_HEIGHT)) {
            playDefaultButtonSound()
            updatePanelOpen = false
            return true
        }

        return true
    }

    private fun startUpdate(entries: List<UpdateEntry>, type: ProjectType, folder: File) {
        if (updateInProgress || entries.isEmpty()) return
        setPanelContext(type, folder)
        updatePanelOpen = true
        updateInProgress = true
        updateCancelRequested = false
        updateTotal = entries.size
        updateCompleted = 0
        updateStatusText = localize("resourcify.pack_updates.progress", 0, entries.size)

        val prepared = entries.map { entry ->
            val wasEnabled = when (type) {
                ProjectType.RESOURCE_PACK, ProjectType.AYCY_RESOURCE_PACK -> Platform.isResourcePackEnabled(entry.oldFile)
                ProjectType.IRIS_SHADER, ProjectType.OPTIFINE_SHADER -> ShaderGuiHelper.isShaderPackEnabled(entry.oldFile)
                else -> false
            }
            PreparedUpdate(entry, wasEnabled)
        }

        Thread({
            var updated = 0
            var failed = 0
            var cancelled = 0
            for (item in prepared) {
                if (updateCancelRequested) break
                item.entry.status = UpdateStatus.UPDATING
                updateStatusText = localize(
                    "resourcify.pack_updates.progress_file",
                    updateCompleted,
                    updateTotal,
                    item.entry.oldFile.name,
                )
                val result = performUpdate(item, type, folder)
                item.entry.status = result
                when (result) {
                    UpdateStatus.UPDATED -> updated++
                    UpdateStatus.CANCELLED -> cancelled++
                    UpdateStatus.FAILED -> failed++
                    else -> {}
                }
                updateCompleted++
                if (result == UpdateStatus.CANCELLED) break
            }
            currentDownloadUrl = null
            activeDownload = null
            updateInProgress = false
            updateStatusText = when {
                cancelled > 0 -> localize("resourcify.pack_updates.cancelled_after", updateCompleted, updateTotal)
                failed > 0 -> localize("resourcify.pack_updates.result_failed", updated, failed)
                updated == 1 -> localize("resourcify.pack_updates.result.one", updated)
                else -> localize("resourcify.pack_updates.result.many", updated)
            }
            if (updated > 0 && (prepared.size == 1 || cancelled == 0)) {
                runClientSync { playDownloadChime() }
            }
        }, "Resourcify-PackUpdate").apply { isDaemon = true }.start()
    }

    private fun performUpdate(item: PreparedUpdate, type: ProjectType, folder: File): UpdateStatus {
        val entry = item.entry
        val version = entry.version ?: return UpdateStatus.FAILED
        val url = version.getDownloadUrl() ?: return UpdateStatus.FAILED
        val newFile = File(folder, version.getFileName())
        val index = LocalIndex.forFolder(folder)
        val oldEntry = index.lookupByFile(entry.oldFile)

        if (type == ProjectType.RESOURCE_PACK || type == ProjectType.AYCY_RESOURCE_PACK) {
            runClientSync {
                if (entry.oldFile.exists()) {
                    try {
                        Platform.closeResourcePack(entry.oldFile)
                    } catch (_: Throwable) {
                    }
                }
            }
        }

        currentDownloadUrl = url
        val future = DownloadManager.download(newFile, version.getSha1(), url, false)
        activeDownload = future
        val result = try {
            future.get()
        } catch (e: Throwable) {
            if (updateCancelRequested) DownloadResult.CANCELLED else {
                VintageResourcify.LOG.warn("Download failed for {}", newFile.name, e)
                DownloadResult.FAILED
            }
        } finally {
            activeDownload = null
            currentDownloadUrl = null
        }
        if (result == DownloadResult.CANCELLED) return UpdateStatus.CANCELLED
        if (result == DownloadResult.FAILED) return UpdateStatus.FAILED

        return try {
            if (entry.oldFile != newFile && entry.oldFile.exists()) {
                if (type == ProjectType.RESOURCE_PACK || type == ProjectType.AYCY_RESOURCE_PACK) {
                    runClientSync {
                        try {
                            Platform.closeResourcePack(entry.oldFile)
                        } catch (_: Throwable) {
                        }
                    }
                }
                if (!entry.oldFile.delete()) {
                    VintageResourcify.LOG.warn("Could not delete old pack {}", entry.oldFile)
                }
                index.remove(entry.oldFile.name)
            }
            if (oldEntry != null) {
                index.record(
                    newFile,
                    oldEntry.platform,
                    oldEntry.projectId,
                    version,
                    oldEntry.updateTrack,
                    oldEntry,
                )
            }
            runClientSync {
                when (type) {
                    ProjectType.RESOURCE_PACK, ProjectType.AYCY_RESOURCE_PACK -> {
                        if (item.wasEnabled) {
                            Platform.replaceEnabledResourcePack(entry.oldFile, newFile)
                        } else {
                            Platform.reloadResourcePack(newFile)
                        }
                        refreshHostPackScreen(type)
                    }
                    ProjectType.IRIS_SHADER, ProjectType.OPTIFINE_SHADER -> {
                        if (item.wasEnabled) {
                            ShaderGuiHelper.enableShaderPack(newFile)
                        }
                        refreshHostPackScreen(type)
                    }
                    else -> {}
                }
            }
            UpdateStatus.UPDATED
        } catch (e: Throwable) {
            VintageResourcify.LOG.warn("Could not finalize update for {}", entry.oldFile.name, e)
            UpdateStatus.FAILED
        }
    }

    private fun drawToast(yTop: Int) {
        val text = toastText ?: return
        if (Minecraft.getSystemTime() > toastUntil) {
            toastText = null
            return
        }
        val mc = Minecraft.getMinecraft()
        val fr = mc.fontRenderer ?: return
        val tw = fr.getStringWidth(text)
        val pad = 4
        val boxW = tw + pad * 2
        val boxH = fr.FONT_HEIGHT + pad * 2
        val (plusX, _) = plusOrigin() ?: return
        val x = plusX + BUTTON_SIZE - boxW
        Gui.drawRect(x, yTop, x + boxW, yTop + boxH, 0xCC000000.toInt())
        fr.drawString(text, x + pad, yTop + pad, 0xFFFFFF, false)
    }

    private fun drawIconButton(
        x: Int,
        y: Int,
        texture: ResourceLocation,
        mouseX: Int,
        mouseY: Int,
        enabled: Boolean,
        tooltip: String? = null,
        accent: Int? = null,
    ) {
        val hover = enabled && isInside(mouseX, mouseY, x, y, BUTTON_SIZE, BUTTON_SIZE)
        val background = when {
            !enabled -> 0x33000000
            hover -> 0x99000000.toInt()
            else -> 0x66000000
        }
        Gui.drawRect(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, background)
        if (hover && accent != null) {
            Gui.drawRect(x, y + BUTTON_SIZE - 1, x + BUTTON_SIZE, y + BUTTON_SIZE, accent)
        }
        Minecraft.getMinecraft().textureManager.bindTexture(texture)
        val alpha = if (enabled) 1f else 0.35f
        GL11.glColor4f(1f, 1f, 1f, alpha)
        val iconX = x + (BUTTON_SIZE - ICON_SIZE) / 2
        val iconY = y + (BUTTON_SIZE - ICON_SIZE) / 2
        Gui.func_152125_a(
            iconX, iconY, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE.toFloat(), ICON_SIZE.toFloat()
        )
        GL11.glColor4f(1f, 1f, 1f, 1f)
        if (hover && tooltip != null) {
            PackOverlayRenderer.queueTooltip(tooltip, mouseX, mouseY)
        }
    }

    private fun drawEyeOffButton(
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int,
        enabled: Boolean,
        tooltip: String? = null,
        accent: Int? = null,
    ) {
        val hover = enabled && isInside(mouseX, mouseY, x, y, BUTTON_SIZE, BUTTON_SIZE)
        Gui.drawRect(
            x,
            y,
            x + BUTTON_SIZE,
            y + BUTTON_SIZE,
            when {
                !enabled -> 0x33000000
                hover -> 0x99000000.toInt()
                else -> 0x66000000
            },
        )
        if (hover && accent != null) {
            Gui.drawRect(x, y + BUTTON_SIZE - 1, x + BUTTON_SIZE, y + BUTTON_SIZE, accent)
        }
        Minecraft.getMinecraft().textureManager.bindTexture(PASSWORD_EYE_TEXTURE)
        GL11.glColor4f(1f, 1f, 1f, if (enabled) 1f else 0.35f)
        val iconX = x + (BUTTON_SIZE - ICON_SIZE) / 2
        val iconY = y + (BUTTON_SIZE - ICON_SIZE) / 2
        Gui.func_152125_a(
            iconX, iconY, 24f, 0f, 24, 24,
            ICON_SIZE, ICON_SIZE, 48f, 24f,
        )
        GL11.glColor4f(1f, 1f, 1f, 1f)
        if (hover && tooltip != null) PackOverlayRenderer.queueTooltip(tooltip, mouseX, mouseY)
    }

    private fun drawTextButton(x: Int, y: Int, width: Int, height: Int, text: String, enabled: Boolean, hover: Boolean, style: ResourcifyStyle.Palette) {
        val fr = Minecraft.getMinecraft().fontRenderer ?: return
        val fill = when {
            !enabled -> style.buttonDisabled
            hover -> style.buttonHover
            else -> style.buttonIdle
        }
        Gui.drawRect(x, y, x + width, y + height, fill)
        if (enabled && hover) {
            Gui.drawRect(x, y + height - 1, x + width, y + height, style.accent)
        }
        val color = if (enabled) style.textPrimary else style.textSecondary
        fr.drawString(text, x + (width - fr.getStringWidth(text)) / 2, y + (height - fr.FONT_HEIGHT) / 2, color, false)
    }

    private fun drawUpdateBadge(buttonX: Int, buttonY: Int, count: Int) {
        val fr = Minecraft.getMinecraft().fontRenderer ?: return
        val text = if (count > 99) "99+" else count.toString()
        val textWidth = fr.getStringWidth(text)
        val width = (textWidth + 6).coerceAtLeast(BADGE_HEIGHT)
        val x = buttonX + BUTTON_SIZE - width + 4
        val y = buttonY - 1
        Gui.drawRect(x, y, x + width, y + BADGE_HEIGHT, 0xFF000000.toInt())
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + BADGE_HEIGHT - 1, 0xFFFFD33D.toInt())
        fr.drawString(text, x + (width - textWidth + 1) / 2, y + (BADGE_HEIGHT - fr.FONT_HEIGHT + 1) / 2, 0x202020, false)
    }

    private fun drawReviewBadge(buttonX: Int, buttonY: Int, hasSafeBadge: Boolean) {
        val fr = Minecraft.getMinecraft().fontRenderer ?: return
        val width = BADGE_HEIGHT
        val x = if (hasSafeBadge) buttonX - width + 3 else buttonX + BUTTON_SIZE - width + 4
        val y = buttonY - 1
        Gui.drawRect(x, y, x + width, y + BADGE_HEIGHT, 0xFF000000.toInt())
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + BADGE_HEIGHT - 1, 0xFFD49A43.toInt())
        fr.drawString("?", x + (width - fr.getStringWidth("?") + 1) / 2, y + (BADGE_HEIGHT - fr.FONT_HEIGHT + 1) / 2, 0x202020, false)
    }

    private fun drawProgressBar(x: Int, y: Int, width: Int, progress: Float, style: ResourcifyStyle.Palette) {
        Gui.drawRect(x, y, x + width, y + PROGRESS_HEIGHT, style.progressBackground)
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + PROGRESS_HEIGHT - 1, style.progressTrack)
        val fillWidth = ((width - 2) * progress.coerceIn(0f, 1f)).toInt()
        if (fillWidth > 0) {
            Gui.drawRect(x + 1, y + 1, x + 1 + fillWidth, y + PROGRESS_HEIGHT - 1, style.progressFill)
        }
    }

    private fun bottomButtons(panelX: Int, panelY: Int, panelW: Int, panelH: Int): BottomButtons {
        val innerW = panelW - PANEL_PADDING * 2
        val compact = innerW < 270
        val updateAllW = if (compact) 78 else 88
        val checkW = if (compact) 58 else 112
        val closeW = if (compact) 50 else 58
        val gap = if (compact) 4 else 8
        val y = panelY + panelH - TEXT_BUTTON_HEIGHT - PANEL_PADDING
        val closeX = panelX + panelW - PANEL_PADDING - closeW
        val checkX = closeX - gap - checkW
        return BottomButtons(
            updateAllX = panelX + PANEL_PADDING,
            updateAllW = updateAllW,
            checkX = checkX,
            checkW = checkW,
            checkLabel = localize(if (compact) "resourcify.pack_updates.check" else "resourcify.pack_updates.check_updates"),
            closeX = closeX,
            closeW = closeW,
            y = y,
        )
    }

    private fun drawScrollBar(x: Int, y: Int, width: Int, height: Int, rowCount: Int, maxScroll: Int) {
        if (maxScroll <= 0 || rowCount <= 0) return
        val barW = ResourcifyStyle.SODIUM_SCROLLBAR_WIDTH
        val barX = x + width - barW
        val thumbH = (height * (height / ROW_HEIGHT).toFloat() / rowCount.toFloat()).toInt().coerceIn(18, height)
        val thumbY = y + ((height - thumbH) * (updateScroll.toFloat() / maxScroll.toFloat())).toInt()
        Gui.drawRect(barX, y, barX + barW, y + height, ResourcifyStyle.SODIUM_SCROLLBAR_TRACK)
        Gui.drawRect(barX, thumbY, barX + barW, thumbY + thumbH, ResourcifyStyle.SODIUM_SCROLLBAR_THUMB)
    }

    private fun beginScissor(x: Int, y: Int, width: Int, height: Int, screen: GuiScreen) {
        val mc = Minecraft.getMinecraft()
        val scaleX = mc.displayWidth.toDouble() / screen.width.toDouble()
        val scaleY = mc.displayHeight.toDouble() / screen.height.toDouble()
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(
            (x * scaleX).toInt(),
            (mc.displayHeight - (y + height) * scaleY).toInt(),
            (width * scaleX).toInt(),
            (height * scaleY).toInt(),
        )
    }

    private fun currentProgress(): Float {
        if (updateTotal <= 0) return 0f
        val current = currentDownloadUrl?.let { DownloadManager.getProgress(it) } ?: 0f
        return ((updateCompleted.toFloat() + current) / updateTotal.toFloat()).coerceIn(0f, 1f)
    }

    private fun entriesSnapshot(): List<UpdateEntry> = synchronized(updateLock) { updateEntries.toList() }

    private fun updatableEntries(): List<UpdateEntry> {
        return synchronized(updateLock) {
            updateEntries.filter {
                it.disposition == UpdateDisposition.SAFE &&
                    it.status != UpdateStatus.UPDATED && it.status != UpdateStatus.UPDATING
            }
        }
    }

    private fun shouldRefreshOnOpen(): Boolean = synchronized(updateLock) {
        updateEntries.isEmpty() || updateEntries.all { it.status == UpdateStatus.UPDATED }
    }

    private fun badgeCount(type: ProjectType, folder: File): Int {
        if (!matchesPanel(type, folder)) return 0
        return synchronized(updateLock) {
            updateEntries.count { it.disposition == UpdateDisposition.SAFE && it.status != UpdateStatus.UPDATED }
        }
    }

    private fun reviewCount(type: ProjectType, folder: File): Int {
        if (!matchesPanel(type, folder)) return 0
        return synchronized(updateLock) {
            updateEntries.count { it.disposition == UpdateDisposition.REVIEW && it.status != UpdateStatus.UPDATED }
        }
    }

    private fun statusLabel(entry: UpdateEntry): String {
        return when (entry.status) {
            UpdateStatus.PENDING -> when (entry.disposition) {
                UpdateDisposition.REVIEW -> localize(
                    "resourcify.pack_updates.needs_review",
                    minecraftVersionsLabel(entry.version?.getMinecraftVersions().orEmpty()),
                )
                UpdateDisposition.SAFE -> fileSizeLabel(entry.version?.getFileSize())
            }
            UpdateStatus.UPDATING -> localize("resourcify.pack_updates.status.updating")
            UpdateStatus.UPDATED -> localize("resourcify.pack_updates.status.updated")
            UpdateStatus.FAILED -> localize("resourcify.pack_updates.status.failed")
            UpdateStatus.CANCELLED -> localize("resourcify.pack_updates.status.cancelled")
        }
    }

    private fun minecraftVersionsLabel(versions: List<String>): String {
        if (versions.isEmpty()) return localize("resourcify.common.unknown")
        if (versions.size <= 3) return versions.joinToString(", ")
        return "${versions.first()} … ${versions.last()}"
    }

    private fun compareMinecraftVersionsDesc(a: String, b: String): Int {
        val aParts = a.split(".", "-").mapNotNull { it.toIntOrNull() }
        val bParts = b.split(".", "-").mapNotNull { it.toIntOrNull() }
        val length = maxOf(aParts.size, bParts.size)
        for (i in 0 until length) {
            val av = aParts.getOrNull(i) ?: 0
            val bv = bParts.getOrNull(i) ?: 0
            if (av != bv) return bv - av
        }
        return b.compareTo(a)
    }

    private fun versionLabel(version: IVersion): String {
        val name = version.getName()
        val number = version.getVersionNumber()
        return when {
            number.isNullOrBlank() -> name
            name.isBlank() -> number
            name == number -> name
            else -> "$name ($number)"
        }
    }

    private fun fileSizeLabel(size: Long?): String {
        if (size == null || size < 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = size.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < units.size - 1) {
            value /= 1024.0
            unit++
        }
        return if (unit == 0) "${value.toLong()} ${units[unit]}" else String.format(Locale.ROOT, "%.1f %s", value, units[unit])
    }

    private fun trimToWidth(fr: net.minecraft.client.gui.FontRenderer, text: String, maxWidth: Int): String {
        if (fr.getStringWidth(text) <= maxWidth) return text
        val suffix = "..."
        val trimmed = fr.trimStringToWidth(text, (maxWidth - fr.getStringWidth(suffix)).coerceAtLeast(0))
        return trimmed + suffix
    }

    private fun maxScroll(rowCount: Int, listH: Int): Int {
        val visibleRows = (listH / ROW_HEIGHT).coerceAtLeast(1)
        return (rowCount - visibleRows).coerceAtLeast(0)
    }

    private fun wheelDirection(wheel: Int): Int = when {
        wheel > 0 -> 1
        wheel < 0 -> -1
        else -> 0
    }

    private fun scaledMousePosition(screen: GuiScreen): Pair<Int, Int> {
        val mc = Minecraft.getMinecraft()
        if (mc.displayWidth <= 0 || mc.displayHeight <= 0) return 0 to 0
        val x = Mouse.getX() * screen.width / mc.displayWidth
        val y = screen.height - Mouse.getY() * screen.height / mc.displayHeight - 1
        return x.coerceIn(0, (screen.width - 1).coerceAtLeast(0)) to y.coerceIn(0, (screen.height - 1).coerceAtLeast(0))
    }

    private fun playDefaultButtonSound() {
        Minecraft.getMinecraft().soundHandler.playSound(
            PositionedSoundRecord.func_147674_a(BUTTON_PRESS_SOUND, 1.0f)
        )
    }

    private fun playUpdateChime() {
        ResourcifySounds.play(UPDATE_CHIME_SOUND)
    }

    private fun playDownloadChime() {
        ResourcifySounds.play(DOWNLOAD_CHIME_SOUND)
    }

    private fun playImportChime() {
        ResourcifySounds.play(IMPORT_CHIME_SOUND)
    }

    private fun showToast(text: String, durationMs: Long) {
        toastText = text
        toastUntil = Minecraft.getSystemTime() + durationMs
    }

    private fun setPanelContext(type: ProjectType, folder: File) {
        val canonicalFolder = safeCanonical(folder)
        val changed = panelType != type || panelFolder?.let { !sameFile(it, canonicalFolder) } ?: true
        panelType = type
        panelFolder = canonicalFolder
        if (changed && !updateInProgress) {
            synchronized(updateLock) {
                updateEntries.clear()
                updateScroll = 0
            }
            updateStatusText = ""
        }
    }

    private fun openUpdatePanel(type: ProjectType, folder: File) {
        setPanelContext(type, folder)
        updatePanelOpen = true
        if (updateStatusText.isBlank()) {
            updateStatusText = localize("resourcify.updates.checking")
        }
    }

    private fun matchesPanel(type: ProjectType, folder: File): Boolean {
        val currentType = panelType ?: return false
        val currentFolder = panelFolder ?: return false
        return currentType == type && sameFile(currentFolder, folder)
    }

    private fun matchesSettings(type: ProjectType, folder: File): Boolean {
        val currentType = settingsType ?: return false
        val currentFolder = settingsFolder ?: return false
        return currentType == type && sameFile(currentFolder, folder)
    }

    private fun matchesDirectUpdate(type: ProjectType, folder: File): Boolean {
        val currentType = directUpdateType ?: return false
        val currentFolder = directUpdateFolder ?: return false
        return currentType == type && sameFile(currentFolder, folder)
    }

    private fun isPanelVisibleOnCurrentScreen(): Boolean {
        val screen = Minecraft.getMinecraft().currentScreen ?: return false
        val type = panelType ?: return false
        val folder = panelFolder ?: return false
        return when (type) {
            ProjectType.RESOURCE_PACK, ProjectType.AYCY_RESOURCE_PACK ->
                screen is GuiScreenResourcePacks &&
                    sameFile(folder, Minecraft.getMinecraft().resourcePackRepository.dirResourcepacks)
            ProjectType.IRIS_SHADER, ProjectType.OPTIFINE_SHADER ->
                ShaderGuiHelper.isShaderPackScreen(screen) && sameFile(folder, ShaderGuiHelper.getShaderpacksFolder(screen))
            else -> false
        }
    }

    private fun isSettingsVisibleOnCurrentScreen(): Boolean {
        val screen = Minecraft.getMinecraft().currentScreen ?: return false
        val type = settingsType ?: return false
        val folder = settingsFolder ?: return false
        return when (type) {
            ProjectType.RESOURCE_PACK, ProjectType.AYCY_RESOURCE_PACK ->
                screen is GuiScreenResourcePacks &&
                    sameFile(folder, Minecraft.getMinecraft().resourcePackRepository.dirResourcepacks)
            ProjectType.IRIS_SHADER, ProjectType.OPTIFINE_SHADER ->
                ShaderGuiHelper.isShaderPackScreen(screen) && sameFile(folder, ShaderGuiHelper.getShaderpacksFolder(screen))
            else -> false
        }
    }

    private fun isInsideUpdateBadge(mouseX: Int, mouseY: Int, buttonX: Int, buttonY: Int, count: Int): Boolean {
        if (count <= 0) return false
        val fr = Minecraft.getMinecraft().fontRenderer ?: return false
        val text = if (count > 99) "99+" else count.toString()
        val width = (fr.getStringWidth(text) + 6).coerceAtLeast(BADGE_HEIGHT)
        val x = buttonX + BUTTON_SIZE - width + 4
        val y = buttonY - 1
        return isInside(mouseX, mouseY, x, y, width, BADGE_HEIGHT)
    }

    private fun isInsideReviewBadge(
        mouseX: Int,
        mouseY: Int,
        buttonX: Int,
        buttonY: Int,
        count: Int,
        hasSafeBadge: Boolean,
    ): Boolean {
        if (count <= 0) return false
        val x = if (hasSafeBadge) buttonX - BADGE_HEIGHT + 3 else buttonX + BUTTON_SIZE - BADGE_HEIGHT + 4
        return isInside(mouseX, mouseY, x, buttonY - 1, BADGE_HEIGHT, BADGE_HEIGHT)
    }

    private fun isInside(mouseX: Int, mouseY: Int, x: Int, y: Int, width: Int, height: Int): Boolean {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
    }

    private fun browseTooltip(type: ProjectType): String {
        return localize(if (isShaderType(type)) "resourcify.pack.browse_shaders" else "resourcify.pack.browse_resource_packs")
    }

    private fun importTooltip(type: ProjectType): String {
        return localize(if (isShaderType(type)) "resourcify.pack.import_shader" else "resourcify.pack.import_resource")
    }

    private fun updateTooltip(type: ProjectType, folder: File): String {
        val safe = badgeCount(type, folder)
        val review = reviewCount(type, folder)
        return when {
            checkInProgress && matchesPanel(type, folder) -> localize("resourcify.pack_updates.checking_updates")
            safe > 0 && review > 0 -> localize("resourcify.pack_updates.tooltip_summary", safe, review)
            safe > 0 -> localize("resourcify.pack_updates.view_updates")
            review > 0 -> localize(
                if (review == 1) "resourcify.pack_updates.review.one" else "resourcify.pack_updates.review.many",
                review,
            )
            else -> localize("resourcify.pack_updates.check_updates")
        }
    }

    private fun isShaderType(type: ProjectType): Boolean {
        return type == ProjectType.IRIS_SHADER || type == ProjectType.OPTIFINE_SHADER
    }

    private fun updateChimeGroup(type: ProjectType): String? {
        return when {
            type == ProjectType.RESOURCE_PACK || type == ProjectType.AYCY_RESOURCE_PACK -> UpdateChimeState.RESOURCE_PACKS
            isShaderType(type) -> UpdateChimeState.SHADER_PACKS
            else -> null
        }
    }

    private fun updateSetHash(entries: List<UpdateEntry>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        entries.map { updateSignature(it) }.sorted().forEach { signature ->
            digest.update(signature.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        return digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    private fun updateSignature(entry: UpdateEntry): String {
        val version = entry.version ?: return entry.oldFile.name
        return listOf(
            entry.oldFile.name,
            version.getProjectId(),
            version.getFileName(),
            version.getSha1(),
            version.getVersionNumber().orEmpty(),
            version.getName(),
        ).joinToString("\u001F")
    }

    private fun plusOrigin(): Pair<Int, Int>? {
        val screen = Minecraft.getMinecraft().currentScreen ?: return null
        return (screen.width - BUTTON_SIZE - 4) to 4
    }

    private fun pickOrigin(): Pair<Int, Int>? {
        val (px, py) = plusOrigin() ?: return null
        return (px - BUTTON_SIZE - BUTTON_GAP) to py
    }

    private fun updateOrigin(): Pair<Int, Int>? {
        val (pickX, pickY) = pickOrigin() ?: return null
        return (pickX - BUTTON_SIZE - BUTTON_GAP) to pickY
    }

    private fun runClientSync(action: () -> Unit) {
        val mc = Minecraft.getMinecraft()
        if (mc.func_152345_ab()) {
            action()
            return
        }
        mc.func_152344_a(Runnable { action() }).get()
    }

    private fun refreshHostPackScreen(type: ProjectType) {
        val mc = Minecraft.getMinecraft()
        val screen = mc.currentScreen
        when (type) {
            ProjectType.RESOURCE_PACK, ProjectType.AYCY_RESOURCE_PACK -> {
                val parent = (screen as? PackScreenAccessor)?.parentScreen
                if (parent != null) {
                    mc.displayGuiScreen(GuiScreenResourcePacks(parent))
                }
            }
            ProjectType.IRIS_SHADER, ProjectType.OPTIFINE_SHADER -> {
                val parent = screen?.let { ShaderGuiHelper.getShaderScreenParent(it) }
                ShaderGuiHelper.openShaderPackScreen(parent)
            }
            else -> {}
        }
    }

    private fun safeCanonical(file: File): File {
        return try {
            file.canonicalFile
        } catch (_: Throwable) {
            file.absoluteFile
        }
    }

    private fun sameFile(a: File, b: File): Boolean = safeCanonical(a) == safeCanonical(b)

    private fun supportsPackFilePicking(type: ProjectType): Boolean {
        return when (type) {
            ProjectType.RESOURCE_PACK,
            ProjectType.AYCY_RESOURCE_PACK,
            ProjectType.IRIS_SHADER,
            ProjectType.OPTIFINE_SHADER -> true
            else -> false
        }
    }

    private data class BottomButtons(
        val updateAllX: Int,
        val updateAllW: Int,
        val checkX: Int,
        val checkW: Int,
        val checkLabel: String,
        val closeX: Int,
        val closeW: Int,
        val y: Int,
    )

    private data class SettingsLayout(
        val panelX: Int,
        val panelY: Int,
        val panelW: Int,
        val panelH: Int,
        val trackX: Int,
        val trackY: Int,
        val trackW: Int,
        val tabsY: Int,
        val updatesTabX: Int,
        val ignoredTabX: Int,
        val tabW: Int,
        val contentX: Int,
        val contentY: Int,
        val contentW: Int,
        val contentH: Int,
        val checkX: Int,
        val checkW: Int,
        val closeX: Int,
        val closeW: Int,
        val bottomY: Int,
    )

    private data class PreparedUpdate(val entry: UpdateEntry, val wasEnabled: Boolean)

    private data class UpdateQuery(val platform: String, val minecraftVersion: String?)

    private data class UpdateEntry(
        val oldFile: File,
        @Volatile var version: IVersion?,
        @Volatile var localEntry: LocalIndex.Entry,
        @Volatile var disposition: UpdateDisposition,
        @Volatile var status: UpdateStatus = UpdateStatus.PENDING,
    )

    private enum class UpdateDisposition(val sortOrder: Int) {
        SAFE(0),
        REVIEW(1),
    }

    private enum class SettingsTab {
        UPDATES,
        IGNORED,
    }

    private enum class UpdateStatus {
        PENDING,
        UPDATING,
        UPDATED,
        FAILED,
        CANCELLED,
    }
}
