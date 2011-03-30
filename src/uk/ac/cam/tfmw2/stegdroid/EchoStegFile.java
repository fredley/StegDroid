package uk.ac.cam.tfmw2.stegdroid;

import dff.minim.analysis.FFT;
import android.util.Log;


public class EchoStegFile {

	private int readPointer;
	private int writePointer;
	private int bitsWritten = 0;
	
	private boolean endOfFile = false;

	private static final int WINDOW_SIZE = 64;
	private static final int SEGMENTATION_LENGTH = 2048; 
	private static double TWOPI = 3.14159265358979323846 * 2.0;

	//Delays (in frames) for embedding a 1/0

	private static final int ECHO_1A = 103;
	private static final int ECHO_1B = 100;
	private static final int ECHO_0A = 113;
	private static final int ECHO_0B = 110;
	private static final int MAX_ECHO = ECHO_0A;

	//for keeping track of audioBuffers
	private short[] currentAudioBuffer;
	private short[] previousAudioBuffer;

	final double VOLUME_REDUCER = 0.8; 
	final double ALPHA = 0.175; 

	private BitStream data;

	 private static short getSample(byte[] buffer, int position) {
	    return (short) (((buffer[position + 1] & 0xff) << 8) | (buffer[position] & 0xff));
	  }

	  private static void setSample(byte[] buffer, int position, short sample) {
	    buffer[position] = (byte) (sample & 0xff);
	    buffer[position + 1] = (byte) ((sample >> 8) & 0xff);
	  }
	
	public EchoStegFile(BitStream bs) {
		data = bs;
	}
	
	public EchoStegFile() {
	}
	public byte[] embed(byte[] samples, int offset, int length) {
		
		//Allocate space for buffers

		short[] audioBuffer0 = new short[WINDOW_SIZE + SEGMENTATION_LENGTH];
		short[] audioBuffer1 = new short[WINDOW_SIZE + SEGMENTATION_LENGTH];
		short[] inputBuffer  = new short[WINDOW_SIZE + SEGMENTATION_LENGTH + 2*MAX_ECHO];
		
		readPointer = 0;
		writePointer = 0;
		
		// Read in the first MAX_ECHO frames
		// Can't add an echo to these as they are at the beginning of the file
		// so just volume normalise them and write them out to file

		// read MAX_ECHO bytes into inputbuffer from the input file and volume reduce
		while(readPointer<MAX_ECHO){
			short oldSample = getSample(samples, 2*readPointer++);
			short newSample = (short) (oldSample * VOLUME_REDUCER);
			setSample(samples, 2*writePointer++, newSample);
		}
		
		// Read in another MAX_ECHO frames which would normally be copied over in the loop
		while(readPointer < MAX_ECHO * 2){
			inputBuffer[readPointer] = getSample(samples, 2*readPointer++);
		}
		writePointer = readPointer;
		// Embed the file.

		currentAudioBuffer = audioBuffer0;

		// can get rid of while loop, we have a finite, known number of bytes in samples

		//need to keep track of remaining samples, reading into inputbuffer each time...

		while ( true ) //already have all the data
		{
			int read = 0;
			for(int i = 0;i<WINDOW_SIZE + SEGMENTATION_LENGTH;i++){
				//read in samples
				try{
					inputBuffer[2*MAX_ECHO + i] = getSample(samples,2*readPointer++);
					read++;
				}catch(ArrayIndexOutOfBoundsException e){
					endOfFile = true;
					break; //end of file
				}

			}
			// Termination condition - we've reached the end of the input audio file.

			if ( endOfFile ) {
				
				int toWrite = read;
				for(int i = 0;i < toWrite;i++){
					setSample(samples,2*writePointer++,(short)(inputBuffer[MAX_ECHO+i]*VOLUME_REDUCER));
				}
				Log.v("EchoStegFile",bitsWritten+" bits written");
				return samples;

			}


			// Add the echoes to the buffers
			// Loop from audio[MAX_ECHO] for WINDOW_SIZE + SEGMENTATION_LENGTH frames.
			for ( int i = MAX_ECHO, j = 0; i < ( MAX_ECHO + WINDOW_SIZE + SEGMENTATION_LENGTH ); i++, j++ ) {

				// Actually add the echos using a double back-forwards kernel: y[n] = x[n] + a*x[n-d] + a*x[n+d] + a*x[n-e] + a*x[n+e]
				audioBuffer0[j] = (short) (VOLUME_REDUCER * ( inputBuffer[i] 
				                   + ALPHA * inputBuffer[i - ECHO_0A] 
				                   + ALPHA * inputBuffer[i + ECHO_0A]
				                   - ALPHA * inputBuffer[i - ECHO_0B] 
				                   - ALPHA * inputBuffer[i + ECHO_0B] ));

				audioBuffer1[j] = (short) (VOLUME_REDUCER * ( inputBuffer[i] 
				                   + ALPHA * inputBuffer[i - ECHO_1A] 
				                   + ALPHA * inputBuffer[i + ECHO_1A]
				                   - ALPHA * inputBuffer[i - ECHO_1B] 
				                   - ALPHA * inputBuffer[i + ECHO_1B] ));
			}
			// Transition window.
			previousAudioBuffer = currentAudioBuffer;
			try {
				int nextBit = data.getNextBit();
				currentAudioBuffer = (nextBit == 1) ? audioBuffer1 : audioBuffer0;
				bitsWritten++;
				//Log.v("EchoStegFile","Embedding a bit: "+nextBit);
				
			} catch (EndOfBitStreamException e) {
				currentAudioBuffer = audioBuffer0;
			}

			for ( int i = 0; i < WINDOW_SIZE; i++ ) {
				double mixer =  (double)i / (double)WINDOW_SIZE ;
				setSample(samples,2*writePointer++,(short)(mixer * currentAudioBuffer[i] + ( 1.0 - mixer ) * previousAudioBuffer[i]));
			}

			// Write out data for the current bit.
			for(int i=0;i<SEGMENTATION_LENGTH;i++){
				setSample(samples,2*writePointer++,currentAudioBuffer[WINDOW_SIZE + i]);
			}

			// Copy over data at the end of the buffer into the beginning section.
			for ( int i = 0; i < 2 * MAX_ECHO; i++ ) {
				inputBuffer[i] = inputBuffer[ ( ( WINDOW_SIZE + SEGMENTATION_LENGTH ) ) + i ];
			}
		}
	}

	public void extract(byte[] input, BitStream output){
//		first of all need to get float[] from byte[]...
		
		float[] timeDomain = new float[SEGMENTATION_LENGTH];
		
		FFT fourier = new FFT(SEGMENTATION_LENGTH, 44100); //needs to be a power of 2, should be due to pre-encode padding
		
//		
//		// We need to separate out channels, so:	
		double[] frameBuffer = new double[SEGMENTATION_LENGTH];
//		
//		// Skip unused frames at the beginning
		int readPointer = MAX_ECHO + WINDOW_SIZE;
//		
//
//		// Extract file.
		boolean finished = false;
		
		while(!finished){
			//read into frameBuffer
			for(int i = 0;i < SEGMENTATION_LENGTH;i++){
				frameBuffer[i] = (float)getSample(input,(readPointer + i)*2) / (float)Short.MAX_VALUE;
			}
			readPointer += SEGMENTATION_LENGTH;
		
//			
//			/* Read out the first channel and apply Hamming window */
//			// To prevent odd sounding artefacts (and easy steganalysis) we have to 
//			// give the same echo to all channels. But for analysis, we only want to 
//			// consider one, because the two may not be correlated.
//			
			for(int i = 0; i < SEGMENTATION_LENGTH;i++){
				timeDomain[i] = (float) (( 0.53836 - ( 0.46164 * Math.cos( TWOPI * (double)i  / (double)( SEGMENTATION_LENGTH - 1 ) ) ) ) * frameBuffer[i]);
			}
//			
//			/* Calculate the ceptstrum */
			
			//do the forwards transform
			fourier.forward(timeDomain);
//
//			// Calculate complex ln(n) for each of the frequency domain coefficients.
			float[] realFreq = fourier.getRealPart();
			float[] imagFreq = fourier.getImaginaryPart();
//			}
			for (int i=0;i < SEGMENTATION_LENGTH / 2 + 1;i++){
				//perform complex log
				Complex c = new Complex(realFreq[i],imagFreq[i]);
				c.log();
				realFreq[i] = c.real;
				imagFreq[i] = c.imag;
			}
			fourier.inverse(realFreq, imagFreq, timeDomain);
			
			double ceptstrum1 = Math.abs( timeDomain[ECHO_1A] ) + Math.abs( timeDomain[ECHO_1B] );
			double ceptstrum0 = Math.abs( timeDomain[ECHO_0A] ) + Math.abs( timeDomain[ECHO_0B] );
			//Log.v("EchoStegFilter","c1: "+ceptstrum1+" c0: "+ceptstrum0);
//			
//			/* Compare the two points corresponding to the delays and see which has
//			 * the more power. Presume this was the input bit. */
			finished = output.setNextBit( ceptstrum1 > ceptstrum0 ? 1 : 0 );
//			// Skip next transition window:
			readPointer += WINDOW_SIZE;
		}
	}
	public class Complex{ //public to allow testing
		public float real;
		public float imag;
		public Complex(float r, float i){
			real = r;
			imag = i;
		}
		public void log(){
			double mod = Math.sqrt(real * real + imag * imag);
			double arg = Math.atan2(imag, real);
			real = (float) Math.log(mod);
			imag = (float)arg;
		}
	};
}
