package com.tetris

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Manages all game sound effects via Android SoundPool.
 * Handles lifecycle: pool is recreated on each loadAll().
 */
class SoundManager(private val context: Context) {

    private var pool: SoundPool? = null
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

    private fun createPool(): SoundPool {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()
        p.setOnLoadCompleteListener { _, _, status ->
            if (status != 0) {
                android.util.Log.w("SoundManager", "Sound load error: $status")
            }
        }
        return p
    }

    /** Preload all sounds — safe to call after release(). */
    fun loadAll() {
        release()
        pool = createPool()
        loaded.clear()
        for (sound in Sound.entries) {
            val resId = context.resources.getIdentifier(sound.resName, "raw", context.packageName)
            if (resId != 0) {
                val soundId = pool!!.load(context, resId, 1)
                loaded[sound.resName] = soundId
            }
        }
    }

    /** Play a sound by enum. Returns false if not loaded. */
    fun play(sound: Sound): Boolean {
        val p = pool ?: return false
        val sid = loaded[sound.resName] ?: return false
        p.play(sid, 0.7f, 0.7f, 1, 0, 1f)
        return true
    }

    /** Release all resources. */
    fun release() {
        pool?.release()
        pool = null
        loaded.clear()
    }
}