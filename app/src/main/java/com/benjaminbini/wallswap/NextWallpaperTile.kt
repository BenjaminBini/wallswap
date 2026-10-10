package com.benjaminbini.wallswap

import android.service.quicksettings.TileService

/** Quick Settings tile: new photo on the screen in use. */
class NextWallpaperTile : TileService() {
    override fun onClick() {
        Schedule.changeActiveNow(this)
    }
}
