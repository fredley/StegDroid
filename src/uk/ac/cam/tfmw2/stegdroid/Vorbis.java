package uk.ac.cam.tfmw2.stegdroid;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;

import org.xiph.libogg.ogg_packet;
import org.xiph.libogg.ogg_page;
import org.xiph.libogg.ogg_stream_state;
import org.xiph.libogg.ogg_sync_state;
import org.xiph.libvorbis.vorbis_block;
import org.xiph.libvorbis.vorbis_comment;
import org.xiph.libvorbis.vorbis_dsp_state;
import org.xiph.libvorbis.vorbis_info;
import org.xiph.libvorbis.vorbisenc;

import android.util.Log;

import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;

public class Vorbis {
	static vorbisenc 			encoder;
	
	static ogg_sync_state		oggSyncState;
	static ogg_stream_state 	oggStreamState;	// take physical pages, weld into a logical stream of packets

	static ogg_page				page;	// one Ogg bitstream page.  Vorbis packets are inside
	static ogg_packet			packet;	// one raw packet of data for decode
  
	static vorbis_info			vorbisInfo;	// struct that stores all the static vorbis bitstream settings

	static vorbis_comment		comment;	// struct that stores all the user comments
	static vorbis_dsp_state		dspState;	// central working state for the packet->PCM decoder
	static vorbis_block			block;	// local working space for packet->PCM decode
	
	static int READ = 1024;
	static byte[] readbuffer = new byte[READ*4+44];
	
	static int page_count = 0;
	static int block_count = 0;

	private String TAG = "VorbisEncoder";
	
	/** The conversion buffer size */
	private int convsize = 4096 * 2;
	/** The buffer used to read OGG file */
	private byte[] convbuffer = new byte[convsize]; // take 8k out of the data segment, not the stack

	
	
	
	public Vorbis(){
	}
	
	
public void decode(InputStream input, File output) throws IOException{
		// the following code come from an example in the Java OGG library.
		// Its extremely complicated and a good example of a library 
		// thats potentially to low level for average users. I'd suggest
		// accepting this code as working and not thinking too hard
		// on what its actually doing
		
		
		SyncState oy = new SyncState(); // sync and verify incoming physical bitstream
		StreamState os = new StreamState(); // take physical pages, weld into a logical stream of packets
		Page og = new Page(); // one Ogg bitstream page.  Vorbis packets are inside
		Packet op = new Packet(); // one raw packet of data for decode

		Info vi = new Info(); // struct that stores all the static vorbis bitstream settings
		Comment vc = new Comment(); // struct that stores all the bitstream user comments
		DspState vd = new DspState(); // central working state for the packet->PCM decoder
		Block vb = new Block(vd); // local working space for packet->PCM decode
		RandomAccessFile fWriter = new RandomAccessFile(output,"rw");

		byte[] buffer;
		int bytes = 0;
		int payloadSize = 0;
		boolean writeNow = true;

		boolean bigEndian = ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN);
		// Decode setup

		oy.init(); // Now we can read pages

		while (true) { // we repeat if the bitstream is chained
			int eos = 0;

			// grab some data at the head of the stream.  We want the first page
			// (which is guaranteed to be small and only contain the Vorbis
			// stream initial header) We need the first page to get the stream
			// serialno.

			// submit a 4k block to libvorbis' Ogg layer
			int index = oy.buffer(4096);
			buffer = oy.data;
			try {
				bytes = input.read(buffer, index, 4096);
			} catch (Exception e) {
				throw new IOException(e.getMessage());
			}
			oy.wrote(bytes);

			// Get the first page.
			if (oy.pageout(og) != 1) {
				// have we simply run out of data?  If so, we're done.
				if (bytes < 4096)
					break;

				// error case.  Must not be Vorbis data
				throw new IOException("Input does not appear to be an Ogg bitstream.");
			}

			// Get the serial number and set up the rest of decode.
			// serialno first; use it to set up a logical stream
			os.init(og.serialno());

			// extract the initial header from the first page and verify that the
			// Ogg bitstream is in fact Vorbis data

			// I handle the initial header first instead of just having the code
			// read all three Vorbis headers at once because reading the initial
			// header is an easy way to identify a Vorbis bitstream and it's
			// useful to see that functionality seperated out.

			vi.init();
			vc.init();
			if (os.pagein(og) < 0) {
				// error; stream version mismatch perhaps
				throw new IOException("Error reading first page of Ogg bitstream data.");
			}

			if (os.packetout(op) != 1) {
				// no page? must not be vorbis
				throw new IOException("Error reading initial header packet.");
			}

			if (vi.synthesis_headerin(vc, op) < 0) {
				// error case; not a vorbis header
				throw new IOException("This Ogg bitstream does not contain Vorbis audio data.");
			}

			// At this point, we're sure we're Vorbis.  We've set up the logical
			// (Ogg) bitstream decoder.  Get the comment and codebook headers and
			// set up the Vorbis decoder

			// The next two packets in order are the comment and codebook headers.
			// They're likely large and may span multiple pages.  Thus we reead
			// and submit data until we get our two pacakets, watching that no
			// pages are missing.  If a page is missing, error out; losing a
			// header page is the only place where missing data is fatal. */

			int i = 0;
			while (i < 2) {
				while (i < 2) {

					int result = oy.pageout(og);
					if (result == 0)
						break; // Need more data
					// Don't complain about missing or corrupt data yet.  We'll
					// catch it at the packet output phase

					if (result == 1) {
						os.pagein(og); // we can ignore any errors here
						// as they'll also become apparent
						// at packetout
						while (i < 2) {
							result = os.packetout(op);
							if (result == 0)
								break;
							if (result == -1) {
								// Uh oh; data at some point was corrupted or missing!
								// We can't tolerate that in a header.  Die.
								throw new IOException("Corrupt secondary header.  Exiting.");
							}
							vi.synthesis_headerin(vc, op);
							i++;
						}
					}
				}
				// no harm in not checking before adding more
				index = oy.buffer(4096);
				buffer = oy.data;
				try {
					bytes = input.read(buffer, index, 4096);
				} catch (Exception e) {
					throw new IOException(e.getMessage());
				}
				if (bytes == 0 && i < 2) {
					throw new IOException("End of file before finding all Vorbis headers!");
				}
				oy.wrote(bytes);
			}
			
			Log.v(TAG,"Channels: "+vi.channels);
			
			//prepare wav file

			fWriter.setLength(0); // Set file length to 0, to prevent unexpected behavior in case the file already existed
			fWriter.writeBytes("RIFF");
			fWriter.writeInt(0); // Final file size not known yet, write 0 
			fWriter.writeBytes("WAVE");
			fWriter.writeBytes("fmt ");
			fWriter.writeInt(Integer.reverseBytes(16)); // Sub-chunk size, 16 for PCM
			fWriter.writeShort(Short.reverseBytes((short) 1)); // AudioFormat, 1 for PCM
			fWriter.writeShort(Short.reverseBytes((short) 1));// Number of channels, 1 for mono, 2 for stereo
			fWriter.writeInt(Integer.reverseBytes(44100)); // Sample rate
			fWriter.writeInt(Integer.reverseBytes(44100*16*1/8)); // Byte rate, SampleRate*NumberOfChannels*BitsPerSample/8
			fWriter.writeShort(Short.reverseBytes((short)(1*16/8))); // Block align, NumberOfChannels*BitsPerSample/8
			fWriter.writeShort(Short.reverseBytes((short)16)); // Bits per sample
			fWriter.writeBytes("data");
			fWriter.writeInt(0);
			
			convsize = 4096 / vi.channels;

			// OK, got and parsed all three headers. Initialize the Vorbis
			//  packet->PCM decoder.
			vd.synthesis_init(vi); // central decode state
			vb.init(vd); // local state for most of the decode
			// so multiple block decodes can
			// proceed in parallel.  We could init
			// multiple vorbis_block structures
			// for vd here

			float[][][] _pcm = new float[1][][];
			int[] _index = new int[vi.channels];
			// The rest is just a straight decode loop until end of stream
			while (eos == 0) {
				while (eos == 0) {

					int result = oy.pageout(og);
					if (result == 0)
						break; // need more data
					if (result == -1) { // missing or corrupt data at this page position
						Log.e(TAG,"Corrupt or missing data in bitstream; continuing...");
					} else {
						os.pagein(og); // can safely ignore errors at
						// this point
						while (true) {
							result = os.packetout(op);

							if (result == 0)
								break; // need more data
							if (result == -1) { // missing or corrupt data at this page position
								// no reason to complain; already complained above
							} else {
								// we have a packet.  Decode it
								int samples;
								if (vb.synthesis(op) == 0) { // test for success!
									vd.synthesis_blockin(vb);
								}

								// **pcm is a multichannel float vector.  In stereo, for
								// example, pcm[0] is left, and pcm[1] is right.  samples is
								// the size of each channel.  Convert the float values
								// (-1.<=range<=1.) to whatever PCM format and write it out

								while ((samples = vd.synthesis_pcmout(_pcm,
										_index)) > 0) {
									float[][] pcm = _pcm[0];
									int bout = (samples < convsize ? samples
											: convsize);

									// convert floats to 16 bit signed ints (host order) and
									// interleave
									for (i = 0; i < vi.channels; i++) {
										int ptr = i * 2;
										//int ptr=i;
										int mono = _index[i];
										for (int j = 0; j < bout; j++) {
											int val = (int) (pcm[i][mono + j] * 32767.);
											//			      short val=(short)(pcm[i][mono+j]*32767.);
											//			      int val=(int)Math.round(pcm[i][mono+j]*32767.);
											// might as well guard against clipping
											if (val > 32767) {
												val = 32767;
											}
											if (val < -32768) {
												val = -32768;
											}
											if (val < 0)
												val = val | 0x8000;
				
											if (bigEndian) {
												convbuffer[ptr] = (byte) (val >>> 8);
												convbuffer[ptr + 1] = (byte) (val);
											} else {
												convbuffer[ptr] = (byte) (val);
												convbuffer[ptr + 1] = (byte) (val >>> 8);
											}
											ptr += 2 * (vi.channels);
										}
									}
									
									for(int j = 0; j < 2 * vi.channels * bout; j+=2){
										if(writeNow){
											fWriter.write(convbuffer, j, 2);
											payloadSize += 2;
										}
										writeNow = !writeNow;
									}
									
									vd.synthesis_read(bout); // tell libvorbis how
									// many samples we
									// actually consumed
								}
							}
						}
						if (og.eos() != 0)
							eos = 1;
					}
				}
				if (eos == 0) {
					index = oy.buffer(4096);
					buffer = oy.data;
					try {
						//Log.v(TAG,".");
						bytes = input.read(buffer, index, 4096);
					} catch (Exception e) {
						throw new IOException(e.getMessage());
					}
					oy.wrote(bytes);
					if (bytes == 0)
						eos = 1;
				}
			}

			// clean up this logical bitstream; before exit we see if we're
			// followed by another [chained]

			os.clear();

			// ogg_page and ogg_packet structs always point to storage in
			// libvorbis.  They're never freed or manipulated directly

			vb.clear();
			vd.clear();
			vi.clear(); // must be called last
		}

		// OK, clean up the framer
		oy.clear();
		
		//clean up
		
		fWriter.seek(4); // Write size to RIFF header
		fWriter.writeInt(Integer.reverseBytes(36+payloadSize));

		fWriter.seek(40); // Write size to Subchunk2Size field
		fWriter.writeInt(Integer.reverseBytes(payloadSize));

		fWriter.close();
		
		/*
		WaveFileWriter wfw = new WaveFileWriter();
		AudioFormat format = new AudioFormat(Encoding.PCM_SIGNED, 44100, 16, 1, 2, 44100, false);
		byte[] audioData = dataout.toByteArray();
		int length = audioData.length;
		byte[] monoData = new byte[length/2];
		for(int i = 0; i < length; i+=4){
			monoData[i/2] = audioData[i];
			monoData[1+i/2] = audioData[i+1];
		}
		ByteArrayInputStream bais = new ByteArrayInputStream(monoData);
		AudioInputStream outStream = new AudioInputStream(bais,format,length);
		
		
		
		wfw.write(outStream, Type.WAVE,output);
		*/
		return;
	
}
	
public void encode(InputStream input, File output) {
		
		boolean endOfStream = false;

		vorbisInfo = new vorbis_info();

		encoder = new vorbisenc();

		
		encoder.vorbis_encode_init_vbr( vorbisInfo, 2, 44100, .4f);
		
		comment = new vorbis_comment();
		comment.vorbis_comment_add_tag( "ENCODER", "Java Vorbis Encoder" );

		dspState = new vorbis_dsp_state();

		if ( !dspState.vorbis_analysis_init( vorbisInfo ) ) {
			Log.v(TAG, "Failed to Initialize vorbis_dsp_state" );
			return;
		}

		block = new vorbis_block( dspState );

		java.util.Random generator = new java.util.Random();  // need to randomize seed
		oggStreamState = new ogg_stream_state( generator.nextInt(256) );
		
		Log.v(TAG , "Writing header." );
		ogg_packet header = new ogg_packet();
		ogg_packet header_comm = new ogg_packet();
		ogg_packet header_code = new ogg_packet();
		
		dspState.vorbis_analysis_headerout( comment, header, header_comm, header_code );
		
		oggStreamState.ogg_stream_packetin( header); // automatically placed in its own page
		oggStreamState.ogg_stream_packetin( header_comm );
		oggStreamState.ogg_stream_packetin( header_code );
		
		page = new ogg_page();
		packet = new ogg_packet();
		
		try {
			
			FileOutputStream fos = new FileOutputStream( output );
			
			while( !endOfStream ) {
				
				if ( !oggStreamState.ogg_stream_flush( page ) )
					break;
				
				fos.write( page.header, 0, page.header_len );
				fos.write( page.body, 0, page.body_len );
				//Log.v(TAG, "." );
			}
			Log.v(TAG,  "Done.\n" );
			
			Log.v(TAG, "Encoding." );
			while ( !endOfStream ) {
				
				int i;
				int bytes = input.read( readbuffer, 0, READ*4 ); // stereo hardwired here
				
				int break_count = 0;
				
				if ( bytes==0 ) {
					
					// end of file.  this can be done implicitly in the mainline,
					// but it's easier to see here in non-clever fashion.
					// Tell the library we're at end of stream so that it can handle
					// the last frame and mark end of stream in the output properly
					
					dspState.vorbis_analysis_wrote( 0 );
					
				} else {
					
					// data to encode
					
					// expose the buffer to submit data
					float[][] buffer = dspState.vorbis_analysis_buffer( READ );
					
					// uninterleave samples
					for ( i=0; i < bytes/2; i++ ) {
						buffer[0][dspState.pcm_current + i] = ( (readbuffer[i*2+1]<<8) | (0x00ff&(int)readbuffer[i*2]) ) / 32768.f;
						buffer[1][dspState.pcm_current + i] = ( (readbuffer[i*2+1]<<8) | (0x00ff&(int)readbuffer[i*2]) ) / 32768.f;
					}
					
					// tell the library how much we actually submitted
					dspState.vorbis_analysis_wrote( i );
				}

				// vorbis does some data preanalysis, then divvies up blocks for more involved 
				// (potentially parallel) processing.  Get a single block for encoding now

				while ( block.vorbis_analysis_blockout( dspState ) ) {
	
					// analysis, assume we want to use bitrate management
					
					block.vorbis_analysis( null );
					block.vorbis_bitrate_addblock();
					
					while ( dspState.vorbis_bitrate_flushpacket( packet ) ) {
						
						// weld the packet into the bitstream
						oggStreamState.ogg_stream_packetin( packet );
						
						// write out pages (if any)
						while ( !endOfStream ) {
							
							if ( !oggStreamState.ogg_stream_pageout( page ) ) {
								break_count++;
								break;
							}
							
							fos.write( page.header, 0, page.header_len );
							fos.write( page.body, 0, page.body_len );
							
							// this could be set above, but for illustrative purposes, I do
							// it here (to show that vorbis does know where the stream ends)
							if ( page.ogg_page_eos() > 0 )
								endOfStream = true;
						}
					}
				}
				//Log.v(TAG, "." );
			}
			
			input.close();
			fos.close();
			
			Log.v(TAG, "Done.\n" );
			
		} catch (Exception e) { Log.e(TAG, "\n" + e ); e.printStackTrace(); }
	}
	
}
