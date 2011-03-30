package uk.ac.cam.tfmw2.stegdroid;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

import android.util.Log;

public class BitStream{

	// This class has two modes, for reading and writing bitstreams.
	// It can produces encrypted or unencrypted streams of bits
	
	private static final String TAG = "BitStream";
	
	private static final int MODE_READ = 0;
	private static final int MODE_WRITE = 1;
	
	private  byte[] bytes;
	private int cursor = 0;
	private int length;
	private int mode;
	private boolean encrypted = false;
	
	public BitStream(){
		//constructor for decoding a bit stream
		mode = MODE_READ;
		bytes = new byte[1]; //initially this size until we know the length
	}
	
	public static byte[] padKey(String s){
		//Key length needs to be (128,192 or) 256 bits, so hash with SHA-256
		byte[] key = s.getBytes();
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			key = sha.digest(key);
		} catch (NoSuchAlgorithmException e) {
			//TODO
			e.printStackTrace();
		}
		return key;
	}
	
	public BitStream(String s){
		
		//constructor for encoding an unencrypted BitStream
		Log.v(TAG,"New BitStream: "+s);
		mode = MODE_WRITE;
		length = s.getBytes().length;
		byte lengthByte = (byte)length;
		
		//first byte contains the length, so that it can be decoded correctly
		bytes = new byte[length + 1];
		bytes[0] = lengthByte;
		System.arraycopy(s.getBytes(), 0, bytes, 1, length);
	}
	public BitStream(String s, String keyString){
		
		//constructor for encoding an encrypted BitStream
		Log.v(TAG,"Encrypted BitStream");
		encrypted = true;
		mode = MODE_WRITE;
		byte[] input = s.getBytes();
		
		//pad the key
		SecretKeySpec key = new SecretKeySpec(padKey(keyString), "AES");
		
		Cipher cipher;
		try {
			cipher = Cipher.getInstance("AES/ECB/PKCS7Padding", "BC");
			
			Log.v("Crypto",new String(input));

		    // encryption pass
		    cipher.init(Cipher.ENCRYPT_MODE, key);
		    byte[] cryptoBytes = new byte[cipher.getOutputSize(input.length)];
		    int cryptoBytesLength = cipher.update(input, 0, input.length, cryptoBytes, 0);
		    cryptoBytesLength += cipher.doFinal(cryptoBytes, cryptoBytesLength);

		    //get the length of the new byte[] and assign to first byte
		    length = cryptoBytes.length;
			byte lengthByte = (byte)length;
			bytes = new byte[length + 1];
			//also set the encryption bit (128)
			bytes[0] = (byte) (lengthByte + 128);
			
			//copy into bytes
			System.arraycopy(cryptoBytes, 0, bytes, 1, cryptoBytesLength);
			Log.v(TAG,"length: "+cryptoBytes.length+","+bytes.length+" first byte: "+(int)bytes[0]+" data: "+new String(bytes));
		    
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (NoSuchProviderException e) {
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (ShortBufferException e) {
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
		} catch (BadPaddingException e) {
			e.printStackTrace();
		}
		
	}
	public int getNextBit() throws EndOfBitStreamException {
		try{
			return (bytes[cursor / 8] >> (cursor++ % 8)) & 1;
		}catch(ArrayIndexOutOfBoundsException e){
			throw new EndOfBitStreamException();
		}
	}

	public boolean setNextBit(int bit) {
		//Log.v(TAG,"Got a bit - "+bit);
		
		// if the cursor == 8, the first byte has been decoded, so the length can be obtained
		if(cursor == 8){
			//set length from first byte
			length = (int)bytes[0] & 0xFF;
			
			//check encryption bit
			if(length >= 128){
				encrypted = true;
				length -= 128;
			}
			
			//set bytes to the right size
			bytes = new byte[length + 1];
			bytes[0] = (byte)length;
			Log.d(TAG,"Length decoded: "+length);
		}else if(cursor % 8 == 0 && cursor > 8){
			if(bytes[(cursor/8)-1] == 0) return true;
			//TODO remove this in final
			Log.d(TAG,"Extracted a byte ("+cursor/8+") - "+Byte.toString(bytes[(cursor/8)-1])+" - "+Integer.toBinaryString((int)bytes[(cursor/8)-1] & 0xFF));
		}
		// return true at the end
		if(this.endOfBitStream()) return true;
		
		//assign the bit to the correct position in bytes[]
		byte b = bytes[cursor / 8];
		if((bit & 1) == 1){
			bytes[cursor / 8] = (byte) (b | (1 << (cursor % 8)));
		}else{
			bytes[cursor / 8] = (byte) (b & ~(1 << (cursor % 8)));
		}
		cursor++;
		return false;
		 
	}

	public boolean endOfBitStream() {
		// return true if it is the end of the BitStream
		if(mode == MODE_READ && cursor < 8) return false; //length not set yet!
		return cursor == length * 8 + 8; 
	}
	public String getString(){
		//return unencrypted String
		byte[] stringBytes = new byte[length];
		System.arraycopy(bytes, 1, stringBytes, 0, length);
		return new String(stringBytes);
	}
	public String decryptString(String keyString){
		// return String decrypted with the keyString
		byte[] stringBytes = new byte[length];
		System.arraycopy(bytes, 1, stringBytes, 0, length);
		Cipher cipher;
		
		//pad the key
		SecretKeySpec key = new SecretKeySpec(padKey(keyString), "AES");
		int ctLength = stringBytes.length;
		
		try {
			cipher = Cipher.getInstance("AES/ECB/PKCS7Padding", "BC");
			
			// decryption pass
		    cipher.init(Cipher.DECRYPT_MODE, key);
		    byte[] decryptedBytes = new byte[cipher.getOutputSize(ctLength)];
		    int actualLength = cipher.update(stringBytes, 0, ctLength, decryptedBytes, 0);
		    actualLength += cipher.doFinal(decryptedBytes, actualLength);
		    Log.v("Crypto",new String(decryptedBytes));
		    Log.v("Crypto",actualLength+"");
		    byte[] outputBytes = new byte[actualLength];
		    System.arraycopy(decryptedBytes, 0, outputBytes, 0, actualLength);
			
		    return new String(outputBytes);
		    
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (NoSuchProviderException e) {
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
		} catch (ShortBufferException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
		} catch (BadPaddingException e) {
			e.printStackTrace();
		}
		
		// if we get this far something has gone wrong
		// it's fairly safe to assume that this is due to an incorrect key
		
	    return StegDroid.DECRYPTION_FAIL_MESSAGE;
		
	}
	public boolean isEncrypted(){
		return encrypted;
	}
}
