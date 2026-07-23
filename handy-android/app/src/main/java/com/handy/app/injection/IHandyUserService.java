package com.handy.app.injection;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface IHandyUserService extends IInterface {
    public static final String DESCRIPTOR = "com.handy.app.injection.IHandyUserService";

    public IBinder getInputServiceBinder() throws RemoteException;

    public static abstract class Stub extends Binder implements IHandyUserService {
        static final int TRANSACTION_getInputServiceBinder = FIRST_CALL_TRANSACTION + 0;

        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }

        public static IHandyUserService asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && iin instanceof IHandyUserService) {
                return (IHandyUserService) iin;
            }
            return new Proxy(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION: {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                case TRANSACTION_getInputServiceBinder: {
                    data.enforceInterface(DESCRIPTOR);
                    IBinder result = this.getInputServiceBinder();
                    reply.writeNoException();
                    reply.writeStrongBinder(result);
                    return true;
                }
                default: {
                    return super.onTransact(code, data, reply, flags);
                }
            }
        }

        private static class Proxy implements IHandyUserService {
            private final IBinder mRemote;

            Proxy(IBinder remote) {
                mRemote = remote;
            }

            @Override
            public IBinder asBinder() {
                return mRemote;
            }

            @Override
            public IBinder getInputServiceBinder() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                IBinder result;
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_getInputServiceBinder, data, reply, 0);
                    reply.readException();
                    result = reply.readStrongBinder();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
                return result;
            }
        }
    }
}
