package com.tetris

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Manages all game sound effects via Android SoundPool.
 * Loads sounds from res/raw/ and provides named play methods.
 */
class SoundManager(context: Context) {

    private val pool: SoundPool
    private val loaded = mutableMapOf<String, Int>()

    enum class Sound(val resName: String) {
        MOVE("move"),
        ROTATE("rotate"),
        SOFT_DROP("softdrop"),
        HARD_DROP("harddrop"),
        LOCK("lock"),
        CLEAR("clear"),
        TETRIS("tetris"),
        LEVEL_UP("levelup"),
        LEVEL_START("levelstart"),
        GAME_OVER("gameover"),
        HOLD("hold")
    }

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()

        // Mark pool ready
        pool.setOnLoadCompleteListener { _, _, status ->
            if (status != 0) {
                android.util.Log.w("SoundManager", "Sound load error: $status")
            }
        }
    }

    /** Preload all sounds — call once after AudioManager focus. */
    fun loadAll() {
        for (sound in Sound.entries) {
            val resId = context.resources.getIdentifier(sound.resName, "raw", context.packageName)
            if (resId != 0) {
                val soundId = pool.load(context, resId, 1)
                loaded[sound.resName] = soundId
            }
        }
    }

    /** Play a sound by enum. Returns false if not loaded. */
    fun play(sound: Sound): Boolean {
        val sid = loaded[sound.resName] ?: return false
        pool.play(sid, 0.7f, 0.7f, 1, 0, 1f)
        return true
    }

    /** Release all resources. */
    fun release() {
        pool.release()
        loaded.clear()
    }
}