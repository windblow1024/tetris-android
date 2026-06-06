package com.tetris

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.KeyEvent
import android.view.View

/**
 * Entry point — wraps TetrisView, manages lifecycle and high-score persistence.
 */
class MainActivity : Activity(), TetrisView.Listener {

    private lateinit var tetrisView: TetrisView
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = "tetris_prefs"
        private const val KEY_HIGH_SCORE = "high_score"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.apply {
            systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        }

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        tetrisView = TetrisView(this)
        tetrisView.listener = this
        tetrisView.game.loadHighScore(prefs.getInt(KEY_HIGH_SCORE, 0))

        setContentView(tetrisView)
    }

    override fun onResume() {
        super.onResume()
        tetrisView.startLoop()
    }

    override fun onPause() {
        super.onPause()
        tetrisView.stopLoop()
    }

    override fun onHighScoreUpdated(score: Int) {
        prefs.edit().putInt(KEY_HIGH_SCORE, score).apply()
    }

    // ── Hardware key support (for keyboards / emulators) ──
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val game = tetrisView.game
        if (game.state == GameState.READY) {
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> { game.startGame(); return true }
            }
        }
        if (game.state != GameState.PLAYING) {
            when (keyCode) {
                KeyEvent.KEYCODE_P -> { game.togglePause(); return true }
                KeyEvent.KEYCODE_R -> { game.restart(); return true }
            }
            return super.onKeyDown(keyCode, event)
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { game.moveLeft(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { game.moveRight(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { game.softDrop(); true }
            KeyEvent.KEYCODE_DPAD_UP -> { game.rotate(); true }
            KeyEvent.KEYCODE_SPACE -> { game.hardDrop(); true }
            KeyEvent.KEYCODE_C -> { game.hold(); true }
            KeyEvent.KEYCODE_P -> { game.togglePause(); true }
            KeyEvent.KEYCODE_R -> { game.restart(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
