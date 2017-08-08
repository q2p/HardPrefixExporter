package q2p.prefixexporter;

import java.io.Closeable;
import java.nio.ByteBuffer;

public final class Assist {
	public static boolean nextExact(final ByteBuffer buffer, final int ... bytes) {
		if(buffer.remaining() < bytes.length)
			return false;
		
		final int mark = buffer.position();
		
		for(final int b : bytes) {
			assert b <= 255 && b >= 0;
			
			if((byte)b != buffer.get()) {
				buffer.position(mark);
				return false;
			}
		}
		return true;
	}
	public static boolean skip(final ByteBuffer buffer, final int bytes) {
		if(buffer.remaining() < bytes)
			return false;
		
		buffer.position(buffer.position()+bytes);
		return true;
	}
	
	public static byte[] assertIntToBytes(final int ... bytes) {
		byte[] ret = new byte[bytes.length];
		for(int i = ret.length-1; i != -1; i--) {
			assert bytes[i] >=0 && bytes[i] <= 255;
			ret[i] = (byte)bytes[i];
		}
		return ret;
	}
	public static byte assertIntToByte(final int b) {
		assert b >=0 && b <= 255;
		return (byte) b;
	}
	public static void assertIntWithBytes(final int ... bytes) {
		for(final int b : bytes) {
			assert b >=0 && b <= 255;
		}
	}
	public static void assertIntWithBytes(final int b) {
		assert b >=0 && b <= 255;
	}
	
	public static void safeClose(final Closeable closeable) {
		try {
			closeable.close();
		} catch(Throwable ignore) {}
	}
	
	private static final String compactSyms = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	public static String compact(long binary, final int prefix) {
		assert binary >= 0;
		
		final StringBuilder sb = new StringBuilder();
		do {
			sb.append(compactSyms.charAt((int)(binary % compactSyms.length())));
			binary /= compactSyms.length();
		} while(binary != 0);
		
		while(sb.length() < prefix)
			sb.append(compactSyms.charAt(0));
		
		return sb.reverse().toString();
	}
}
