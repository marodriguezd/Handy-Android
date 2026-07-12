package com.handy.app.injection

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel

class HandyUserService : Service(), IBinder {

    private val inputServiceBinder: IBinder by lazy {
        val serviceManagerClass = Class.forName("android.os.ServiceManager")
        val getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String::class.java)
        getServiceMethod.invoke(null, "input") as IBinder
    }

    override fun onBind(intent: Intent?): IBinder? = this

    override fun queryLocalInterface(descriptor: String): android.os.IInterface? = null

    override fun getInterfaceDescriptor(): String? = "com.handy.app.injection.IHandyUserService"

    override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        data.enforceInterface("com.handy.app.injection.IHandyUserService")
        if (code == android.os.IBinder.FIRST_CALL_TRANSACTION) {
            reply?.writeStrongBinder(inputServiceBinder)
            return true
        }
        return false
    }

    override fun pingBinder(): Boolean = true

    override fun isBinderAlive(): Boolean = true

    override fun linkToDeath(recipient: android.os.IBinder.DeathRecipient, flags: Int) = Unit

    override fun unlinkToDeath(recipient: android.os.IBinder.DeathRecipient, flags: Int): Boolean = true

    override fun dump(fd: java.io.FileDescriptor?, args: Array<out String>?) {}

    override fun dumpAsync(fd: java.io.FileDescriptor?, args: Array<out String>?) {}

    override fun getExtension(): String? = null
}
