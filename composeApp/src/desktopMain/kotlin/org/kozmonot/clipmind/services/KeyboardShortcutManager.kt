package org.kozmonot.clipmind.services

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeHookException
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.awt.MouseInfo
import java.awt.Point
import java.util.logging.Level
import java.util.logging.Logger

class KeyboardShortcutManager : NativeKeyListener {
    private var callback: (() -> Unit)? = null
    private var isCommandOrCtrlPressed = false
    private var isOptionPressed = false

    fun initialize() {
        try {
            // Disable verbose logging from JNativeHook
            Logger.getLogger(GlobalScreen::class.java.getPackage().name).apply {
                level = Level.WARNING
                useParentHandlers = false
            }

            GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeKeyListener(this)
        } catch (e: NativeHookException) {
            println("Failed to register native hook: ${e.message}")
        }
    }

    fun setShortcutCallback(callback: () -> Unit) {
        this.callback = callback
    }

    fun shutdown() {
        try {
            GlobalScreen.removeNativeKeyListener(this)
            GlobalScreen.unregisterNativeHook()
        } catch (e: NativeHookException) {
            println("Failed to unregister native hook: ${e.message}")
        }
    }

    override fun nativeKeyPressed(e: NativeKeyEvent) {
        when (e.keyCode) {
            NativeKeyEvent.VC_META, NativeKeyEvent.VC_CONTROL -> isCommandOrCtrlPressed = true
            NativeKeyEvent.VC_ALT -> isOptionPressed = true
            NativeKeyEvent.VC_V -> {
                if (isCommandOrCtrlPressed && isOptionPressed) {
                    callback?.invoke()
                }
            }
        }
    }

    override fun nativeKeyReleased(e: NativeKeyEvent) {
        when (e.keyCode) {
            NativeKeyEvent.VC_META, NativeKeyEvent.VC_CONTROL -> isCommandOrCtrlPressed = false
            NativeKeyEvent.VC_ALT -> isOptionPressed = false
        }
    }

    fun getCurrentMousePosition(): Point {
        return MouseInfo.getPointerInfo().location
    }
}