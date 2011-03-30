/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: C:\\Users\\tom\\workspace\\StegDroid\\src\\uk\\ac\\cam\\tfmw2\\stegdroid\\IRecordService.aidl
 */
package uk.ac.cam.tfmw2.stegdroid;
public interface IRecordService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements uk.ac.cam.tfmw2.stegdroid.IRecordService
{
private static final java.lang.String DESCRIPTOR = "uk.ac.cam.tfmw2.stegdroid.IRecordService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an uk.ac.cam.tfmw2.stegdroid.IRecordService interface,
 * generating a proxy if needed.
 */
public static uk.ac.cam.tfmw2.stegdroid.IRecordService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = (android.os.IInterface)obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof uk.ac.cam.tfmw2.stegdroid.IRecordService))) {
return ((uk.ac.cam.tfmw2.stegdroid.IRecordService)iin);
}
return new uk.ac.cam.tfmw2.stegdroid.IRecordService.Stub.Proxy(obj);
}
public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_getTimeInRecording:
{
data.enforceInterface(DESCRIPTOR);
long _result = this.getTimeInRecording();
reply.writeNoException();
reply.writeLong(_result);
return true;
}
case TRANSACTION_getTimeInSession:
{
data.enforceInterface(DESCRIPTOR);
long _result = this.getTimeInSession();
reply.writeNoException();
reply.writeLong(_result);
return true;
}
case TRANSACTION_stopRecording:
{
data.enforceInterface(DESCRIPTOR);
this.stopRecording();
reply.writeNoException();
return true;
}
case TRANSACTION_getState:
{
data.enforceInterface(DESCRIPTOR);
int _result = this.getState();
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_toggleRecording:
{
data.enforceInterface(DESCRIPTOR);
long _arg0;
_arg0 = data.readLong();
this.toggleRecording(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_getMaxAmplitude:
{
data.enforceInterface(DESCRIPTOR);
int _result = this.getMaxAmplitude();
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_setSession:
{
data.enforceInterface(DESCRIPTOR);
long _arg0;
_arg0 = data.readLong();
this.setSession(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_startRecording:
{
data.enforceInterface(DESCRIPTOR);
this.startRecording();
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements uk.ac.cam.tfmw2.stegdroid.IRecordService
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
public long getTimeInRecording() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
long _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getTimeInRecording, _data, _reply, 0);
_reply.readException();
_result = _reply.readLong();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public long getTimeInSession() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
long _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getTimeInSession, _data, _reply, 0);
_reply.readException();
_result = _reply.readLong();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public void stopRecording() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_stopRecording, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public int getState() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getState, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public void toggleRecording(long sessionId) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeLong(sessionId);
mRemote.transact(Stub.TRANSACTION_toggleRecording, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public int getMaxAmplitude() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getMaxAmplitude, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public void setSession(long sessionId) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeLong(sessionId);
mRemote.transact(Stub.TRANSACTION_setSession, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void startRecording() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_startRecording, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_getTimeInRecording = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_getTimeInSession = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_stopRecording = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_getState = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
static final int TRANSACTION_toggleRecording = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
static final int TRANSACTION_getMaxAmplitude = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
static final int TRANSACTION_setSession = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
static final int TRANSACTION_startRecording = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
}
public long getTimeInRecording() throws android.os.RemoteException;
public long getTimeInSession() throws android.os.RemoteException;
public void stopRecording() throws android.os.RemoteException;
public int getState() throws android.os.RemoteException;
public void toggleRecording(long sessionId) throws android.os.RemoteException;
public int getMaxAmplitude() throws android.os.RemoteException;
public void setSession(long sessionId) throws android.os.RemoteException;
public void startRecording() throws android.os.RemoteException;
}
