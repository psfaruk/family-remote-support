package com.family.remote.support.ui.tracking

import android.content.Context
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView

// ═══════════════════════════════════════════════════════════════════════
// v0.44.0 (fast-map wave) — the map style registry.
//
// Owner directive: "গুগল ম্যাপ একেবারেই স্লো কাজ করে ম্যাপ কে আরো ফার্স্ট ও
// স্মুথ করতে হবে। 2D 3D ও setelite ম্যাপ যুক্ত করতে হবে।"
//
// What shipped before: ONE hard-wired source — OSM MAPNIK
// (tile.openstreetmap.org). That community server is rate-limited and has
// no edge in South Asia, which is exactly why the map felt "একেবারেই স্লো".
//
// What ships now — THREE styles the user switches with one tap:
//   • 2D (default)   — CARTO "Voyager": the same OpenStreetMap data served
//                      from a fast global CDN. Attribution
//                      © OpenStreetMap contributors · © CARTO.
//   • SATELLITE      — Esri World Imagery (ArcGIS Online): global
//                      satellite photo tiles, no API key, no billing.
//                      Attribution Esri · Maxar · Earthstar Geographics.
//   • TERRAIN (3D)   — Esri World Topographic Map: hillshaded relief that
//                      reads as 3D terrain. Honest note (RELEASE_NOTES):
//                      true photorealistic 3D buildings (Google-Maps-style)
//                      are not possible on a 2D tile engine — this is the
//                      honest relief equivalent.
//
// All three are free, key-less, https, and family-fleet-scale fair use.
// The choice persists in SharedPreferences and applies live (setTileSource
// on the existing MapView — no view rebuild, markers/circles untouched).
// Each source carries its OWN copyright string (the CopyrightOverlay reads
// it from the ACTIVE tile source at draw time), and MapStyles.attribution
// mirrors the same line for the overlay's setCopyrightNotice call.
//
// Verified against the shipped osmdroid-android 6.1.18 bytecode:
// OnlineTileSourceBase.getTileURLString(long) — tiles are addressed by
// org.osmdroid.util.MapTileIndex (getZoom/getX/getY statics), NOT by the
// pre-6.x MapTile class; the copyright-bearing constructor is
// (name, zoomMin, zoomMax, tileSize, fileEnding, baseUrls[], copyright).
// ═══════════════════════════════════════════════════════════════════════

object MapStyles {

    const val STYLE_2D = "2d"
    const val STYLE_SATELLITE = "satellite"
    const val STYLE_TERRAIN = "terrain"

    private const val PREFS = "ui_prefs"
    private const val KEY = "map_style"

    private const val ESRI_BASE = "https://server.arcgisonline.com/ArcGIS/rest/services/"

    /**
     * 2D street map — CARTO Voyager on the standard {z}/{x}/{y} layout, so
     * osmdroid's own XYTileSource builds the URL correctly. The copyright
     * string rides IN the source (the overlay reads it from the active
     * source at draw time).
     */
    private val CARTO_VOYAGER = XYTileSource(
        "CARTO Voyager",
        0, 19, 256, ".png",
        arrayOf("https://basemaps.cartocdn.com/rastertiles/voyager/"),
        "© OpenStreetMap contributors · © CARTO"
    )

    /**
     * Esri ArcGIS REST tiles are {z}/{y}/{x} (y before x) — osmdroid's
     * XYTileSource would emit the wrong order, so this tiny subclass owns
     * the URL shape: MapTileIndex decodes the packed long into z/y/x.
     * 256px tiles, zoom 0..19 (safe global coverage for imagery and topo).
     */
    private class EsriTileSource(name: String, service: String, copyright: String) :
        OnlineTileSourceBase(name, 0, 19, 256, ".png", arrayOf(ESRI_BASE), copyright) {

        private val path = "$ESRI_BASE$service/MapServer/tile/"

        override fun getTileURLString(pMapTileIndex: Long): String =
            path + MapTileIndex.getZoom(pMapTileIndex) +
                "/" + MapTileIndex.getY(pMapTileIndex) +
                "/" + MapTileIndex.getX(pMapTileIndex)
    }

    private val ESRI_IMAGERY = EsriTileSource(
        "Esri World Imagery", "World_Imagery",
        "Imagery © Esri · Maxar · Earthstar Geographics"
    )
    private val ESRI_TOPO = EsriTileSource(
        "Esri World Topo", "World_Topo_Map",
        "© Esri · © OpenStreetMap contributors"
    )

    /** The persisted style id (defaults to the fast 2D street map). */
    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, STYLE_2D) ?: STYLE_2D

    /** Persist a style choice (the chips call this; the map applies it live). */
    fun set(context: Context, style: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, style).apply()
    }

    /** Point the (existing) MapView at the chosen tile source. Cheap and
     *  live — markers, accuracy circles and camera state all survive. */
    fun apply(mv: MapView, style: String) {
        when (style) {
            STYLE_SATELLITE -> mv.setTileSource(ESRI_IMAGERY)
            STYLE_TERRAIN -> mv.setTileSource(ESRI_TOPO)
            else -> mv.setTileSource(CARTO_VOYAGER)
        }
    }

    /** The per-style attribution line (mirrors the sources' own copyright
     *  strings; the map overlay's setCopyrightNotice uses this directly). */
    fun attribution(style: String): String = when (style) {
        STYLE_SATELLITE -> "Imagery © Esri · Maxar · Earthstar Geographics"
        STYLE_TERRAIN -> "© Esri · © OpenStreetMap contributors"
        else -> "© OpenStreetMap contributors · © CARTO"
    }
}
