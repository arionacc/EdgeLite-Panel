package com.edgelite.panel

import android.content.Context
import android.widget.LinearLayout

/**
 * LinearLayout untuk item panel (ikon dan label yang tidak saling menimpa). Dengan
 * [hasOverlappingRendering] bernilai false, memudarkan item tidak perlu menggambarnya dulu ke lapisan
 * terpisah di setiap frame, jadi animasi item berurutan terasa lebih mulus.
 */
class FlatLinearLayout(context: Context) : LinearLayout(context) {
    override fun hasOverlappingRendering(): Boolean = false
}
