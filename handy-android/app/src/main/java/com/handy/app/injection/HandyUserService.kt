package com.handy.app.injection

import android.os.Binder
import android.os.IBinder
import android.os.Parcel

class HandyUserService : Binder() {

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == FIRST_CALL_TRANSACTION) {
            data.enforceInterface("com.handy.app.injection.IHandyUserService")
            val inputBinder = getInputServiceBinder()
            reply?.writeNoException()
            reply?.writeStrongBinder(inputBinder)
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    private fun getInputServiceBinder(): IBinder {
        val serviceManagerClass = Class.forName("android.os.ServiceManager")
        val getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String::class.java)
        return getServiceMethod.invoke(null, "input") as IBinder
    }
}
