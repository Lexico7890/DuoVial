package com.anonymous.DuoVial.frameprocessor

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager
import com.mrousavy.camera.frameprocessors.FrameProcessorPluginRegistry
import com.mrousavy.camera.frameprocessors.VisionCameraProxy

class CircularBufferPluginPackage : ReactPackage {
    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return emptyList()
    }

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        FrameProcessorPluginRegistry.addFrameProcessorPlugin("circularBuffer") { proxy: VisionCameraProxy, options: Map<String, Any>? ->
            CircularBufferPlugin(proxy, options)
        }
        return emptyList()
    }
}
