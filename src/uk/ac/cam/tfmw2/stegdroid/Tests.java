package uk.ac.cam.tfmw2.stegdroid;

import imp.javax.sound.sampled.AudioFileFormat.Type;
import imp.javax.sound.sampled.AudioFormat;
import imp.javax.sound.sampled.AudioInputStream;
import imp.javax.sound.sampled.UnsupportedAudioFileException;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

import uk.ac.cam.tfmw2.stegdroid.EchoStegFile.Complex;
import android.util.Log;

import com.sun.media.sound.WaveFileReader;
import com.sun.media.sound.WaveFileWriter;

public class Tests {
	public static boolean complexLogTest(){

		// Tests the complex Log function with a couple of known input/output values

		EchoStegFile esf = new EchoStegFile();
		Complex c = esf.new Complex(1.0f,1.0f);
		c.log();
		Log.v("complexLogTest","Ln(1+i) = "+c.real+" + "+c.imag+"i");
		if(c.real != (float)0.346573590279972654708616060729088284037750067180127627060 || c.imag != (float)0.785398163397448309615660845819875721049292349843776455243 ) return false;
		c = esf.new Complex(2.0f,3.0f);
		c.log();
		Log.v("complexLogTest","Ln(2+3i) = "+c.real+" + "+c.imag+"i");
		if(c.real != (float)1.28247467873076836802674372078265930240263397238010355820 || c.imag != (float)0.982793723247329067985710611014666014496877453631628556761 ) return false;
		return true;
	}
	public static boolean bitStreamTest(){

		// Tests BitStream with unencrypted data. Turns a String into a BitStream and 
		// verifies that the output matches the input

		String testString1 = "Help! Zombie Gingerbread Men are invading my phone!";
		BitStream putIn = new BitStream(testString1);
		int[] output = new int[(testString1.length() + 2) * 8];
		int i = 0;
		while(true){
			try {
				output[i++] = putIn.getNextBit();
			} catch (EndOfBitStreamException e) {
				break;
			}
		}
		BitStream takeOut = new BitStream();
		i = 0;
		boolean finished = false;
		while(!finished){
			finished = takeOut.setNextBit(output[i++]);
		}
		String testStringResult1 = takeOut.getString();
		Log.v("bitStreamTest","input: "+testString1+" output: "+testStringResult1);
		return testString1.equals(testStringResult1);
	}
	public static boolean bitStreamLongCharsTest(){

		// Checks an unencrypted BitStream with multi-byte characters

		String testString1 = "äëö";
		BitStream putIn = new BitStream(testString1);
		int[] output = new int[(testString1.getBytes().length + 2) * 8];
		int i = 0;
		while(true){
			try {
				output[i++] = putIn.getNextBit();
			} catch (EndOfBitStreamException e) {
				break;
			}
		}
		BitStream takeOut = new BitStream();
		i = 0;
		boolean finished = false;
		while(!finished){
			finished = takeOut.setNextBit(output[i++]);
		}
		String testStringResult1 = takeOut.getString();
		Log.v("bitStreamTestLongChars","input: "+testString1+" output: "+testStringResult1);
		return testString1.equals(testStringResult1);
	}
	public static boolean cryptoTest(){

		// Confirms that crypto is working correctly, in particular, this
		// test will fail if the specified crypto library is not available
		// on the device

		byte[] input = new String("Test Data").getBytes();

		SecretKeySpec key = new SecretKeySpec(BitStream.padKey("TestKey"), "AES");

		Cipher cipher;
		try {
			cipher = Cipher.getInstance("AES/ECB/PKCS7Padding", "BC");

			Log.v("cryptoTest",new String(input));

			// encryption pass
			cipher.init(Cipher.ENCRYPT_MODE, key);
			byte[] cryptoBytes = new byte[cipher.getOutputSize(input.length)];
			int cryptoBytesLength = cipher.update(input, 0, input.length, cryptoBytes, 0);
			cryptoBytesLength += cipher.doFinal(cryptoBytes, cryptoBytesLength);

			// decryption pass
			cipher.init(Cipher.DECRYPT_MODE, key);
			byte[] decryptedBytes = new byte[cipher.getOutputSize(cryptoBytesLength)];
			int actualLength = cipher.update(cryptoBytes, 0, cryptoBytesLength, decryptedBytes, 0);
			actualLength += cipher.doFinal(decryptedBytes, actualLength);
			Log.v("cryptoTest",new String(decryptedBytes));
			Log.v("cryptoTest",actualLength+"");
			byte[] outputBytes = new byte[actualLength];
			System.arraycopy(decryptedBytes, 0, outputBytes, 0, actualLength);

			return (new String(outputBytes).equals("Test Data"));


		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return false;
		} catch (NoSuchProviderException e) {
			e.printStackTrace();
			return false;
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
			return false;
		} catch (InvalidKeyException e) {
			e.printStackTrace();
			return false;
		} catch (ShortBufferException e) {
			e.printStackTrace();
			return false;
		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
			return false;
		} catch (BadPaddingException e) {
			e.printStackTrace();
			return false;
		}
	}
	public static float stegReliabilityTest(String rootDir,String unencodedFilePath){

		WaveFileReader wfr = new WaveFileReader();
		File waveFile = new File(rootDir + unencodedFilePath);
		File saveFile = new File(rootDir + "test.wav");

		AudioInputStream stream;
		try {
			stream = wfr.getAudioInputStream(waveFile);
			AudioFormat format = stream.getFormat();
			Log.v("stegTest","Format info: "+format.getEncoding().toString()+format.getSampleRate()+" "+format.getSampleSizeInBits()+" "+format.getChannels()+" "+format.getFrameSize()+" "+format.getFrameRate()+" "+(format.isBigEndian() ? "true" : "false"));
			int length = (int) (stream.getFrameLength() * format.getFrameSize());
			Log.v("stegTest","Encode Length: "+Integer.toString(length));

			// read the entire stream
			byte[] samples = new byte[length];
			DataInputStream dis = new DataInputStream(stream);
			try {
				// This could be improved by using a ByteBuffer
				//TODO
				dis.readFully(samples);
			} catch (IOException ex) {
				//TODO
				ex.printStackTrace();
			}
			
			String input = "longish string for testing";
			
			BitStream bs = new BitStream(input);
			EchoStegFile esf = new EchoStegFile(bs);

			//filter the whole thing in one go
			byte[] newSamples = esf.embed(samples, 0, length);
			ByteArrayInputStream bais = new ByteArrayInputStream(newSamples);

			WaveFileWriter wfw = new WaveFileWriter();
			AudioInputStream oStream = new AudioInputStream(bais,format,newSamples.length / 2);

			wfw.write(oStream, Type.WAVE, saveFile);

			//now read in

			wfr = new WaveFileReader();
			AudioInputStream readStream;

			readStream = wfr.getAudioInputStream(saveFile);


			//get the format information and initialize buffers
			format = readStream.getFormat();
			length = (int) (readStream.getFrameLength() * format.getFrameSize());

			// read the entire stream
			samples = new byte[length];
			dis = new DataInputStream(readStream);


			try {
				dis.readFully(samples);
			} catch (IOException ex) {
			}
			//Set up BitStream, EchoStegFile, Message and String for output
			BitStream sbs = new BitStream();
			esf = new EchoStegFile();
			String result;

			//Perform extraction
			esf.extract(samples, sbs);
			
			result = sbs.getString();
			
			//compare
			
			BitStream instream = new BitStream(input);
			BitStream outstream = new BitStream(result);
			
			int count = 0;
			int correct = 0;
			
			while(!instream.endOfBitStream() && !outstream.endOfBitStream()){
				try {
					if(instream.getNextBit() == outstream.getNextBit()){
						correct++;
					}
				} catch (EndOfBitStreamException e) {
					break;
				}
				count++;
			}
			float percentage = 100 * (float)correct / (float)count;
			
			return percentage;
			
		} catch (UnsupportedAudioFileException e) {
			e.printStackTrace();
			return 0;
		} catch (IOException e) {
			e.printStackTrace();
			return 0;
		}

	}
	public static float vorbisTest(String rootDir,String inputFilePath){
		
		String testFilePath = rootDir + "test.ogg";
		String outputFilePath = rootDir + "oggTestOutput.wav";
		String input = "longish string for testing";
		
		File inputFile = new File(rootDir + inputFilePath);
		
		Vorbis ve = new Vorbis();
		try {
			ve.encode(new FileInputStream(inputFile),new File(testFilePath));
			
			File outputFile = new File(outputFilePath);
			
			ve.decode(new FileInputStream(new File(testFilePath)),outputFile);
			
			//extract
			
			WaveFileReader wfr = new WaveFileReader();
			AudioInputStream readStream;

			readStream = wfr.getAudioInputStream(new File(outputFilePath));


			//get the format information and initialize buffers
			AudioFormat format = readStream.getFormat();
			int length = (int) (readStream.getFrameLength() * format.getFrameSize());

			// read the entire stream
			byte[] samples = new byte[length];
			DataInputStream dis = new DataInputStream(readStream);


			try {
				dis.readFully(samples);
			} catch (IOException ex) {
			}
			//Set up BitStream, EchoStegFile, Message and String for output
			BitStream sbs = new BitStream();
			EchoStegFile esf = new EchoStegFile();
			String result;
			
			//Perform extraction
			esf.extract(samples, sbs);
			
			result = sbs.getString();
			
			//compare
			
			BitStream instream = new BitStream(input);
			BitStream outstream = new BitStream(result);
			
			int count = 0;
			int correct = 0;
			
			while(!instream.endOfBitStream() && !outstream.endOfBitStream()){
				try {
					if(instream.getNextBit() == outstream.getNextBit()){
						correct++;
					}
				} catch (EndOfBitStreamException e) {
					break;
				}
				count++;
			}
			float percentage = 100 * (float)correct / (float)count;
			
			return percentage;
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return 0;
		} catch (IOException e) {
			e.printStackTrace();
			return 0;
		} catch (UnsupportedAudioFileException e){ 
			e.printStackTrace();
			return 0;
		}
	}
}
