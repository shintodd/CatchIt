package my.feeder.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import my.feeder.app.data.GtfsStop
import my.feeder.app.data.LiveBusState
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FeederRealMap(
    routeCode: String,
    stops: List<GtfsStop>,
    shapePoints: List<Pair<Double, Double>>,
    activeBuses: List<LiveBusState> = emptyList(),
    selectedStopId: String? = null,
    onStopClick: ((GtfsStop) -> Unit)? = null,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }

    // The last payload signature we pushed to JS. Only re-serialize + re-push when
    // it actually changes — recomposition alone must not rebuild the whole JSON.
    var lastPushSignature by remember { mutableStateOf("") }

    LaunchedEffect(routeCode) {
        lastPushSignature = ""
    }

    fun pushDataToMap(webView: WebView, force: Boolean = false) {
        try {
            // Cheap fingerprint of the inputs. When the buses move, the count or
            // positions change; when they don't, we skip the whole serialization.
            val busSig = activeBuses.joinToString(",") { "${it.plate}:${it.lat}:${it.lon}:${it.speedKmh}" }
            val signature = "$routeCode|${stops.size}|${shapePoints.size}|$busSig"
            if (!force && signature == lastPushSignature) return
            lastPushSignature = signature

            val stopsJson = JSONArray()
            stops.forEach { s ->
                val sObj = JSONObject()
                sObj.put("id", s.id)
                sObj.put("code", s.code)
                sObj.put("name", s.name)
                sObj.put("lat", s.lat)
                sObj.put("lon", s.lon)
                sObj.put("seq", s.seq)
                stopsJson.put(sObj)
            }

            val shapeJson = JSONArray()
            shapePoints.forEach { pt ->
                val coord = JSONArray()
                coord.put(pt.first)
                coord.put(pt.second)
                shapeJson.put(coord)
            }

            val routeObj = JSONObject()
            routeObj.put("code", routeCode)
            routeObj.put("stops", stopsJson)
            routeObj.put("shape", shapeJson)

            val busesJson = JSONArray()
            activeBuses.forEach { b ->
                val bObj = JSONObject()
                bObj.put("plate", b.plate)
                bObj.put("lat", b.lat)
                bObj.put("lon", b.lon)
                bObj.put("heading", b.heading)
                bObj.put("speedKmh", b.speedKmh)
                busesJson.put(bObj)
            }

            val jsCall = "if (window.setRouteData) { window.setRouteData($routeObj); window.updateBuses($busesJson); }"
            android.util.Log.d("FeederRealMap", "Pushing data for $routeCode: stops=${stops.size}, shape=${shapePoints.size}, buses=${activeBuses.size}")
            webView.evaluateJavascript(jsCall) { result ->
                android.util.Log.d("FeederRealMap", "evaluateJavascript result for $routeCode: $result")
            }
        } catch (e: Exception) {
            android.util.Log.e("FeederRealMap", "Error pushing data to map", e)
        }
    }

    LaunchedEffect(routeCode, stops.size, shapePoints.size, activeBuses.size) {
        webViewRef?.let { wv ->
            if (isMapLoaded) {
                pushDataToMap(wv)
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.parseColor("#090C10"))
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        useWideViewPort = true
                        // Overview mode scales the page to fit the viewport,
                        // which leaves the map's layout size out of step with
                        // what is drawn and shifts the whole map sideways.
                        loadWithOverviewMode = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }

                    // Prevent parent Compose scroll from hijacking map pinch/pan gestures
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        false
                    }

                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                            android.util.Log.d("FeederMapJS", "${message?.message()} -- line ${message?.lineNumber()}")
                            return true
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isMapLoaded = true
                            android.util.Log.d("FeederRealMap", "onPageFinished: $url")
                            view?.let { pushDataToMap(it, force = true) }
                        }
                    }

                    // Start the map already looking at the route. Without this
                    // the map opens on a hard-coded Klang Valley centre and
                    // only jumps to the route a second later, which reads as
                    // "it put it in somewhere else".
                    val centre = when {
                        shapePoints.isNotEmpty() -> Pair(
                            shapePoints.sumOf { it.first } / shapePoints.size,
                            shapePoints.sumOf { it.second } / shapePoints.size
                        )
                        stops.isNotEmpty() -> Pair(stops[0].lat, stops[0].lon)
                        else -> null
                    }
                    val url = buildString {
                        append("file:///android_asset/feeder_map.html")
                        if (centre != null) {
                            append("?lat=").append(centre.first)
                            append("&lon=").append(centre.second)
                            append("&zoom=15")
                        }
                    }

                    loadUrl(url)
                    webViewRef = this
                }
            },
            update = { wv ->
                webViewRef = wv
                if (isMapLoaded) {
                    pushDataToMap(wv)
                }
            }
        )
    }
}
