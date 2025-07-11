package io.github.edsuns.adfilter.impl

import android.content.Context
import android.net.Uri
import com.tencent.smtt.export.external.interfaces.WebResourceRequest
import com.tencent.smtt.export.external.interfaces.WebResourceResponse
import com.tencent.smtt.sdk.WebView
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkInfo
import io.github.edsuns.adblockclient.ResourceType
import io.github.edsuns.adfilter.*
import io.github.edsuns.adfilter.impl.Constants.FILE_STORE_DIR
import io.github.edsuns.adfilter.impl.Constants.KEY_ALREADY_UP_TO_DATE
import io.github.edsuns.adfilter.impl.Constants.KEY_FILTERS_COUNT
import io.github.edsuns.adfilter.impl.Constants.KEY_FILTER_NAME
import io.github.edsuns.adfilter.impl.Constants.KEY_RAW_CHECKSUM
import io.github.edsuns.adfilter.impl.Constants.TAG_INSTALLATION
import io.github.edsuns.adfilter.script.ElementHiding
import io.github.edsuns.adfilter.script.ScriptInjection
import io.github.edsuns.adfilter.script.Scriptlet
import io.github.edsuns.adfilter.util.None
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

internal class AdFilterImpl constructor(appContext: Context) : AdFilter {

    private val detector: Detector = DetectorImpl()
    internal val binaryDataStore: BinaryDataStore =
        BinaryDataStore(File(appContext.filesDir, FILE_STORE_DIR))
    private val filterDataLoader: FilterDataLoader = FilterDataLoader(detector, binaryDataStore)

    private val elementHiding: ElementHiding = ElementHiding(detector)
    private val scriptlet: Scriptlet = Scriptlet(detector)

    override val customFilter = filterDataLoader.getCustomFilter()

    override val viewModel = FilterViewModelImpl(appContext, filterDataLoader)

    override val hasInstallation: Boolean
        get() = viewModel.sharedPreferences.hasInstallation

    init {
        viewModel.isEnabled.observeForever { enable ->
            if (enable) {
                viewModel.filters.value?.values?.forEach {
                    if (it.isEnabled && it.hasDownloaded()) {
                        viewModel.enableFilter(it)
                    }
                }
                filterDataLoader.load(FilterDataLoader.ID_CUSTOM)
            } else {
                filterDataLoader.unloadAll()
                filterDataLoader.unloadCustomFilter()
            }
            viewModel.updateEnabledFilterCount()
            viewModel.sharedPreferences.isEnabled = enable
            // notify onDirty
            (viewModel.onDirty as MutableLiveData).value = None.Value
        }
        viewModel.workInfo.observeForever { list -> processWorkInfo(list) }
    }

    private fun processWorkInfo(workInfoList: List<WorkInfo>) {
        workInfoList.forEach { workInfo ->
            val filterId = viewModel.downloadFilterIdMap[workInfo.id.toString()]
            viewModel.filters.value?.get(filterId)?.let {
                updateFilter(it, workInfo)
            }
        }
    }

    private fun updateFilter(filter: Filter, workInfo: WorkInfo) {
        val state = workInfo.state
        val isInstallation = workInfo.tags.contains(TAG_INSTALLATION)
        var downloadState = filter.downloadState
        if (isInstallation) {
            downloadState =
                when (state) {
                    WorkInfo.State.RUNNING -> DownloadState.INSTALLING
                    WorkInfo.State.SUCCEEDED -> {
                        val alreadyUpToDate =
                            workInfo.outputData.getBoolean(KEY_ALREADY_UP_TO_DATE, false)
                        if (!alreadyUpToDate) {
                            filter.filtersCount =
                                workInfo.outputData.getInt(KEY_FILTERS_COUNT, 0)
                            workInfo.outputData.getString(KEY_RAW_CHECKSUM)
                                ?.let { filter.checksum = it }
                            if (filter.isEnabled || !filter.hasDownloaded()) {
                                viewModel.enableFilter(filter)
                            }
                        }
                        if (filter.name.isBlank()) {
                            workInfo.outputData.getString(KEY_FILTER_NAME)
                                ?.let { filter.name = it }
                        }
                        filter.updateTime = System.currentTimeMillis()
                        DownloadState.SUCCESS
                    }
                    WorkInfo.State.FAILED -> DownloadState.FAILED
                    WorkInfo.State.CANCELLED -> DownloadState.CANCELLED
                    else -> downloadState
                }
        } else {
            downloadState = when (state) {
                WorkInfo.State.ENQUEUED -> DownloadState.ENQUEUED
                WorkInfo.State.RUNNING -> DownloadState.DOWNLOADING
                WorkInfo.State.FAILED -> DownloadState.FAILED
                else -> downloadState
            }
        }
        if (state.isFinished) {
            viewModel.downloadFilterIdMap.remove(workInfo.id.toString())
            // save shared preferences
            viewModel.sharedPreferences.downloadFilterIdMap = viewModel.downloadFilterIdMap
            // notify download work removed
            (viewModel.workToFilterMap as MutableLiveData).postValue(viewModel.downloadFilterIdMap)
        }
        if (downloadState != filter.downloadState) {
            filter.downloadState = downloadState
            viewModel.flushFilter()
        }
    }

    /**
     * Notify the application of a resource request and allow the application to return the data.
     *
     * If the return value is null, the WebView will continue to load the resource as usual.
     * Otherwise, the return response and data will be used.
     *
     * NOTE: This method is called on a thread other than the UI thread so clients should exercise
     * caution when accessing private data or the view system.
     */
    override fun shouldIntercept(
        webView: WebView,
        request: WebResourceRequest
    ): FilterResult = runBlocking {
        val url = request.url.toString()
        if (request.isForMainFrame) {
            return@runBlocking FilterResult(null, url, null)
        }

        val documentUrl = withContext(Dispatchers.Main) { webView.url }
            ?: return@runBlocking FilterResult(null, url, null)

        val resourceType = ResourceType.from(request)

        val result = shouldIntercept(url, documentUrl, resourceType)
        if (result.shouldBlock && resourceType.isVisibleResource()) {
            elementHiding.elemhideBlockedResource(webView, url)
        }

        return@runBlocking result
    }

    override fun shouldIntercept(
        url: String,
        documentUrl: String,
        resourceType: ResourceType?
    ): FilterResult {
        val type = resourceType ?: ResourceType.from(Uri.parse(url)) ?: ResourceType.UNKNOWN
        val rule = detector.shouldBlock(url, documentUrl, type)

        return if (rule != null) {
            FilterResult(rule, url, WebResourceResponse(null, null, null))
        } else {
            FilterResult(null, url, null)
        }
    }

    private fun ResourceType.isVisibleResource(): Boolean =
        this === ResourceType.IMAGE || this === ResourceType.MEDIA || this === ResourceType.SUBDOCUMENT

    override fun setupWebView(webView: WebView) {
        webView.addJavascriptInterface(elementHiding, ScriptInjection.bridgeNameFor(elementHiding))
        webView.addJavascriptInterface(scriptlet, ScriptInjection.bridgeNameFor(scriptlet))
    }

    override fun performScript(webView: WebView?, url: String?) {
        if (viewModel.isEnabled.value == true) {
            elementHiding.perform(webView, url)
            scriptlet.perform(webView, url)
        }
    }
}
