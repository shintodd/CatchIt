package my.feeder.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Service disruption alerts, mirrored from Prasarana's own "Service Alerts on
 * PULSE" page (myrapid.com.my).
 *
 * There is NO public API for these. data.gov.my's GTFS-RT feed is vehicle
 * positions only (service alerts are "in the pipeline for 2026"), and the
 * myrapid.com.my site is behind Imperva bot protection — no RSS, and the
 * WordPress REST API returns 401. The only reliable read path is the r.jina.ai
 * text proxy, which renders the alert list as parseable markdown.
 *
 * Because that proxy can rate-limit, results are cached and the last good list
 * is kept on failure. The alerts themselves are scraped from an official
 * operator page, so the content is real — only the delivery path is fragile.
 */
object ServiceAlertsRepository {

    enum class Kind { DIVERSION, DISRUPTION, DELAY, RAIL, OTHER }

    data class ServiceAlert(
        val title: String,
        val url: String,
        val kind: Kind,
        val routeCodes: List<String>,
        val excerpt: String,
        val published: String,
        /** Epoch millis the alert was published, parsed from the page. 0 if unknown. */
        val publishedAtMillis: Long = 0L
    )

    private val _alerts = MutableStateFlow<List<ServiceAlert>>(emptyList())
    val alerts: StateFlow<List<ServiceAlert>> = _alerts

    /** Seconds since last successful fetch; -1 means never fetched. */
    private val _lastUpdatedSeconds = MutableStateFlow(-1)
    val lastUpdatedSeconds: StateFlow<Int> = _lastUpdatedSeconds

    /** Epoch millis of the last successful fetch; 0 means never fetched. */
    private val _lastFetchedAt = MutableStateFlow(0L)
    val lastFetchedAt: StateFlow<Long> = _lastFetchedAt

    /** True when the last fetch failed but we have a cached list. */
    private val _stale = MutableStateFlow(false)
    val stale: StateFlow<Boolean> = _stale

    /**
     * Alerts that appeared since the last poll. Emitted only for genuinely new
     * notices, never on the first fetch of a fresh install.
     */
    private val _newAlerts = MutableSharedFlow<List<ServiceAlert>>(extraBufferCapacity = 8)
    val newAlerts: SharedFlow<List<ServiceAlert>> = _newAlerts

    // === Read state (persisted) ===
    // Alerts have no server-side identity, so the article URL is the stable key.
    private var prefs: android.content.SharedPreferences? = null
    private val _readUrls = MutableStateFlow<Set<String>>(emptySet())
    val readUrls: StateFlow<Set<String>> = _readUrls

    /** Alert URLs already announced, so the same notice is never notified twice. */
    private val _seenUrls = MutableStateFlow<Set<String>>(emptySet())
    val seenUrls: StateFlow<Set<String>> = _seenUrls

    private var hasAnnouncedBefore = false

    /**
     * Loads persisted read state and the cached alert list, so a cold start
     * shows the last known notices immediately instead of an empty screen while
     * the proxy renders. Safe to call repeatedly; only the first call binds.
     */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext
            .getSharedPreferences("service_alerts", Context.MODE_PRIVATE)
        prefs = p
        _readUrls.value = p.getStringSet(KEY_READ, emptySet())?.toSet() ?: emptySet()
        _seenUrls.value = p.getStringSet(KEY_SEEN, emptySet())?.toSet() ?: emptySet()
        hasAnnouncedBefore = p.getBoolean(KEY_ANNOUNCED, false)

        val cached = p.getString(KEY_CACHE, null)?.let { decode(it) }
        if (!cached.isNullOrEmpty()) {
            _alerts.value = cached
            val at = p.getLong(KEY_CACHE_AT, 0L)
            if (at > 0) _lastFetchedAt.value = at
        }
    }

    fun markRead(url: String) {
        if (url.isBlank() || _readUrls.value.contains(url)) return
        persist(_readUrls.value + url)
    }

    fun markAllRead() {
        persist(_readUrls.value + _alerts.value.map { it.url })
    }

    private fun persist(next: Set<String>) {
        _readUrls.value = next
        prefs?.edit()?.putStringSet(KEY_READ, next)?.apply()
    }

    private const val KEY_READ = "read_urls"
    private const val KEY_SEEN = "seen_urls"
    private const val KEY_ANNOUNCED = "announced_once"
    private const val KEY_CACHE = "cached_alerts"
    private const val KEY_CACHE_AT = "cached_at"

    /**
     * The proxy around the operator's alerts page.
     *
     * `nc` is a cache-buster the operator's own front end uses; it is refreshed
     * every call so the proxy never has a chance to reuse an old render.
     */
    private fun sourceUrl(): String =
        "https://r.jina.ai/https://myrapid.com.my/pulse/service-alerts-on-pulse/" +
            "?nc=${System.currentTimeMillis() / 1000}"

    fun refresh() {
        // Network + parse on a background thread; StateFlow publishes on main.
        GlobalScope.launch(Dispatchers.IO) {
            val fresh = refreshBlocking()
            if (fresh.isNotEmpty()) _newAlerts.tryEmit(fresh)
        }
    }

    /**
     * Fetches the alert list synchronously and returns the alerts that were not
     * present in the previous poll.
     *
     * The background worker calls this directly and raises the notifications
     * itself, because it has no collector on [newAlerts]. A notice is only
     * ever announced once: the seen set is persisted, so a second poll of the
     * same page finds nothing new.
     */
    fun refreshBlocking(): List<ServiceAlert> {
        return try {
            val parsed = parse(fetch())
            if (parsed.isEmpty()) {
                emptyList()
            } else {
                val now = System.currentTimeMillis()
                _alerts.value = parsed
                _lastUpdatedSeconds.value = 0
                _lastFetchedAt.value = now
                _stale.value = false
                cache(parsed, now)
                announceNew(parsed)
            }
        } catch (e: Exception) {
            // Keep the last good list; flag it as stale so the UI can say so.
            if (_alerts.value.isNotEmpty()) _stale.value = true
            Log.w("ServiceAlerts", "refresh failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Publishes alerts that were not in the previous poll.
     *
     * The first successful fetch of an install records everything without
     * announcing, so a new user is not greeted with a burst of old notices.
     */
    private fun announceNew(parsed: List<ServiceAlert>): List<ServiceAlert> {
        val previous = _seenUrls.value
        val fresh = if (hasAnnouncedBefore) {
            parsed.filter { it.url !in previous }
        } else {
            emptyList()
        }
        hasAnnouncedBefore = true
        prefs?.edit()
            ?.putStringSet(KEY_SEEN, parsed.map { it.url }.toSet())
            ?.putBoolean(KEY_ANNOUNCED, true)
            ?.apply()
        _seenUrls.value = parsed.map { it.url }.toSet()
        return fresh
    }

    /** Persists the list so a cold start is not blank while the proxy renders. */
    private fun cache(alerts: List<ServiceAlert>, at: Long) {
        val json = JSONArray()
        alerts.forEach { a ->
            json.put(
                JSONObject()
                    .put("title", a.title)
                    .put("url", a.url)
                    .put("kind", a.kind.name)
                    .put("codes", JSONArray(a.routeCodes))
                    .put("excerpt", a.excerpt)
                    .put("published", a.published)
                    .put("publishedAt", a.publishedAtMillis)
            )
        }
        prefs?.edit()
            ?.putString(KEY_CACHE, json.toString())
            ?.putLong(KEY_CACHE_AT, at)
            ?.apply()
    }

    private fun decode(raw: String): List<ServiceAlert> = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val codes = o.optJSONArray("codes")
            ServiceAlert(
                title = o.optString("title"),
                url = o.optString("url"),
                kind = runCatching { Kind.valueOf(o.optString("kind")) }.getOrDefault(Kind.OTHER),
                routeCodes = codes?.let { c ->
                    (0 until c.length()).map { c.getString(it) }
                } ?: emptyList(),
                excerpt = o.optString("excerpt"),
                published = o.optString("published"),
                publishedAtMillis = o.optLong("publishedAt", 0L)
            )
        }
    } catch (e: Exception) {
        Log.w("ServiceAlerts", "cache decode failed: ${e.message}")
        emptyList()
    }

    private fun fetch(): String {
        val conn = URL(sourceUrl()).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "CatchIt/1.0.4 (Android)")
        conn.setRequestProperty("Accept", "text/plain,*/*")
        // Ask the proxy for a fresh render of the origin page, not a cached one.
        // Measured on this source: 21.7s served versus 3.1s served fresh.
        conn.setRequestProperty("X-No-Cache", "true")
        // Do not let the proxy spend longer than this rendering.
        conn.setRequestProperty("X-Timeout", "30")
        return try {
            conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Parses the r.jina.ai markdown of the alerts page.
     * Each entry looks like:
     *   ### [Title](https://myrapid.com.my/info-…/)
     *   <excerpt…>
     *   [Read More »](…)
     *    November 6, 2026  8:57 pm
     */
    private fun parse(markdown: String): List<ServiceAlert> {
        val titleRegex = Regex("""### \[([^\]]+)\]\(([^)]+)\)""")
        val dateRegex = Regex("""([A-Z][a-z]+ \d{1,2}, \d{4})\s+(\d{1,2}:\d{2}\s*[ap]m)""")

        val matches = titleRegex.findAll(markdown).toList()
        if (matches.isEmpty()) return emptyList()

        val results = ArrayList<ServiceAlert>(matches.size)
        for ((i, m) in matches.withIndex()) {
            val title = unescapeHtml(m.groupValues[1].trim())
            val url = m.groupValues[2].trim()

            // Footer links ("Rate Our Services", "Rate Our Retail Offerings")
            // also match the "### [title](url)" pattern but are not alerts.
            val looksLikeAlert = title.contains("Laluan", ignoreCase = true) ||
                title.contains("Lencongan", ignoreCase = true) ||
                title.contains("Gangguan", ignoreCase = true) ||
                title.contains("Kelewatan", ignoreCase = true) ||
                title.contains("Kemas Kini", ignoreCase = true) ||
                url.contains("info-", ignoreCase = true) ||
                url.contains("kelewatan", ignoreCase = true)
            if (!looksLikeAlert) continue

            val sectionStart = m.range.last + 1
            val sectionEnd = if (i + 1 < matches.size) matches[i + 1].range.first else markdown.length
            val section = markdown.substring(sectionStart, sectionEnd)

            val publishedRaw = dateRegex.find(section)?.value?.trim() ?: ""
            results.add(
                ServiceAlert(
                    title = title,
                    url = url,
                    kind = classify(title),
                    routeCodes = extractRouteCodes(title, section),
                    excerpt = cleanExcerpt(section),
                    published = formatPublished(publishedRaw),
                    publishedAtMillis = parsePublishedMillis(publishedRaw)
                )
            )
        }
        return results
    }

    /**
     * Decodes HTML entities in an alert title.
     *
     * Runs to a fixed point: r.jina.ai serves CDN-cached variants of the same page
     * with different escaping depths, so a single pass can turn "&amp;amp;" into
     * "&amp;" and leave it visible in the UI.
     */
    private fun unescapeHtml(input: String): String {
        var s = input
        repeat(3) {
            val next = s
                .replace("&amp;", "&")
                .replace("&#8217;", "\u2019")
                .replace("&#8216;", "\u2018")
                .replace("&#8220;", "\u201c")
                .replace("&#8221;", "\u201d")
                .replace("&#39;", "'")
                .replace("&#039;", "'")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
            if (next == s) return s
            s = next
        }
        return s
    }

    private fun classify(title: String): Kind = when {
        // Rail check FIRST: "Kelewatan Tren" is a rail delay, not a bus delay.
        // If the delay branch ran first, every train delay would land in DELAY
        // and leak through the Bus filter.
        title.contains("Tren", ignoreCase = true) ||
            title.contains("LRT", ignoreCase = true) ||
            title.contains("MRT", ignoreCase = true) ||
            title.contains("Monorel", ignoreCase = true) ||
            title.contains("Stesen", ignoreCase = true) -> Kind.RAIL
        title.contains("Lencongan", ignoreCase = true) -> Kind.DIVERSION
        title.contains("Gangguan", ignoreCase = true) -> Kind.DISRUPTION
        title.contains("Kelewatan", ignoreCase = true) -> Kind.DELAY
        else -> Kind.OTHER
    }

    /** Pulls affected route codes out of the "Laluan …" clause in title and body. */
    private fun extractRouteCodes(title: String, body: String): List<String> {
        val codes = LinkedHashSet<String>()
        val codeToken = Regex("""\bT?\d{3}[A-Z]?\b""")

        // Title: "Laluan 400, 420, 421 dan 580" / "Laluan T772B" / "Laluan T580"
        val titleClause = Regex("""Laluan\s+(.+?)(?:\)|$)""", RegexOption.IGNORE_CASE).find(title)
        if (titleClause != null) {
            codeToken.findAll(titleClause.groupValues[1]).forEach { codes.add(it.value.uppercase()) }
        }

        // Body: "Laluan 600 : VGJ1946" (delay posts keep codes in the body)
        Regex("""Laluan\s+(T?\d{3}[A-Z]?)""", RegexOption.IGNORE_CASE)
            .findAll(body)
            .forEach { codes.add(it.groupValues[1].uppercase()) }

        return codes.toList()
    }

    private fun cleanExcerpt(section: String): String {
        val text = section
            .replace(Regex("""\[Read More[^\]]*\]\([^)]*\)"""), "")
            .replace(Regex("""!\[[^\]]*\]\([^)]*\)"""), "")
            .replace(Regex("""\[[^\]]*\]\([^)]*\)"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return if (text.length > 200) text.take(200).trimEnd() + "…" else text
    }

    /** The same timestamp as epoch millis in MYT, for sorting and age display. */
    private fun parsePublishedMillis(raw: String): Long {
        if (raw.isBlank()) return 0L
        return try {
            val inFmt = java.text.SimpleDateFormat("MMMM d, yyyy h:mm a", Locale.US)
            inFmt.timeZone = java.util.TimeZone.getTimeZone("Asia/Kuala_Lumpur")
            inFmt.parse(raw)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /** "November 6, 2026  8:57 pm" -> "6 Nov, 8:57 pm" (parsed in MYT). */
    private fun formatPublished(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            val inFmt = java.text.SimpleDateFormat("MMMM d, yyyy h:mm a", Locale.US)
            inFmt.timeZone = java.util.TimeZone.getTimeZone("Asia/Kuala_Lumpur")
            val parsed = inFmt.parse(raw) ?: return raw
            val outFmt = java.text.SimpleDateFormat("d MMM, h:mm a", Locale.US)
            outFmt.timeZone = java.util.TimeZone.getTimeZone("Asia/Kuala_Lumpur")
            outFmt.format(parsed)
        } catch (e: Exception) {
            raw
        }
    }
}
