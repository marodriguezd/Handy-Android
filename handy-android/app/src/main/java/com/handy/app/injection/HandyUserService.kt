package com.handy.app.injection

import android.app.Service
import android.content.Intent
import android.os.IBinder

class HandyUserService : Service() {

    private val binder = object : IHandyUserService.Stub() {
        override fun getInputServiceBinder(): IBinder {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String::class.java)
            return getServiceMethod.invoke(null, "input") as IBinder
        }
    }

    override fun onBind(intent: Intent?): IBinder? = binder
}
