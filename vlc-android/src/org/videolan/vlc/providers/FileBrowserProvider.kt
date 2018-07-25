/*****************************************************************************
 * FileBrowserProvider.kt
 *****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.providers

import android.arch.lifecycle.Observer
import android.hardware.usb.UsbDevice
import android.net.Uri
import android.text.TextUtils
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.withContext
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.gui.helpers.hf.getDocumentFiles
import org.videolan.vlc.util.*
import java.io.File

open class FileBrowserProvider(dataset: LiveDataset<MediaLibraryItem>, url: String?, private val filePicker: Boolean = false, showHiddenFiles: Boolean) : BrowserProvider(dataset, url, showHiddenFiles), Observer<MutableList<UsbDevice>> {

    private var storagePosition = -1
    private var otgPosition = -1
    init {
        if (url == null) ExternalMonitor.devices.observeForever(this)
    }

    override fun browseRoot() {
        val internalmemoryTitle = VLCApplication.getAppResources().getString(R.string.internal_memory)
        val browserStorage = VLCApplication.getAppResources().getString(R.string.browser_storages)
        val quickAccess = VLCApplication.getAppResources().getString(R.string.browser_quick_access)
        val storages = AndroidDevices.getMediaDirectories()
        val devices = mutableListOf<MediaLibraryItem>()
        if (!filePicker) devices.add(DummyItem(browserStorage))
        for (mediaDirLocation in storages) {
            if (!File(mediaDirLocation).exists()) continue
            val directory = MediaWrapper(AndroidUtil.PathToUri(mediaDirLocation))
            directory.type = MediaWrapper.TYPE_DIR
            if (TextUtils.equals(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, mediaDirLocation)) {
                directory.setDisplayTitle(internalmemoryTitle)
                storagePosition = devices.size
            } else {
                val deviceName = FileUtils.getStorageTag(directory.title)
                if (deviceName != null) directory.setDisplayTitle(deviceName)
            }
            devices.add(directory)
        }
        if (AndroidUtil.isLolliPopOrLater && !ExternalMonitor.devices.value.isEmpty()) {
            val otg = MediaWrapper(Uri.parse("otg://")).apply {
                title = "OTG Device"
                type = MediaWrapper.TYPE_DIR
            }
            otgPosition = devices.size
            devices.add(otg)
        }
        // Set folders shortcuts
        if (!filePicker) {
            devices.add(DummyItem(quickAccess))
        }
        if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_MOVIES_DIRECTORY_FILE.exists()) {
            val movies = MediaWrapper(AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_MOVIES_DIRECTORY_URI)
            movies.type = MediaWrapper.TYPE_DIR
            devices.add(movies)
        }
        if (!filePicker) {
            if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_MUSIC_DIRECTORY_FILE.exists()) {
                val music = MediaWrapper(AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_MUSIC_DIRECTORY_URI)
                music.type = MediaWrapper.TYPE_DIR
                devices.add(music)
            }
            if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_PODCAST_DIRECTORY_FILE.exists()) {
                val podcasts = MediaWrapper(AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_PODCAST_DIRECTORY_URI)
                podcasts.type = MediaWrapper.TYPE_DIR
                devices.add(podcasts)
            }
            if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_FILE.exists()) {
                val downloads = MediaWrapper(AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_URI)
                downloads.type = MediaWrapper.TYPE_DIR
                devices.add(downloads)
            }
            if (AndroidDevices.MediaFolders.WHATSAPP_VIDEOS_FILE.exists()) {
                val whatsapp = MediaWrapper(AndroidDevices.MediaFolders.WHATSAPP_VIDEOS_FILE_URI)
                whatsapp.type = MediaWrapper.TYPE_DIR
                devices.add(whatsapp)
            }
        }
        dataset.value = devices
    }

    override fun browse(url: String?) {
        when {
            url == "otg://" || url?.startsWith("content:") == true -> uiJob {
                dataset.value = withContext(VLCIO) { getDocumentFiles(VLCApplication.getAppContext(), Uri.parse(url).path.substringAfterLast(':')) as? MutableList<MediaLibraryItem> ?: mutableListOf() }
            }
            else -> super.browse(url)
        }
    }

    override fun refresh() = true

    override fun release(): Job {
        ExternalMonitor.devices.removeObserver(this)
        return super.release()
    }

    override fun onChanged(list: MutableList<UsbDevice>?) {
        if (list?.isEmpty() != false) {
            if (otgPosition != -1) {
                dataset.remove(otgPosition)
                otgPosition = -1
            }
        } else {
            if (otgPosition == -1) {
                val otg = MediaWrapper(Uri.parse("otg://")).apply {
                    title = VLCApplication.getAppResources().getString(R.string.otg_device_title)
                    type = MediaWrapper.TYPE_DIR
                }
                otgPosition = storagePosition+1
                dataset.add(otgPosition, otg)
            }
        }
    }

}