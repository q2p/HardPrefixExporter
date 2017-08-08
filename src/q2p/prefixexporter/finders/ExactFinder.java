package q2p.prefixexporter.finders;

import q2p.prefixexporter.Assist;

import java.nio.ByteBuffer;

public class ExactFinder extends Finder {
	protected byte[] marker = null;
	protected void setMarker(final int ... marker) {
		assert this.marker == null;
		this.marker = Assist.assertIntToBytes(marker);
	}
	
	protected ByteBuffer buffer;
	public final void setBuff(final ByteBuffer buffer) {
		this.buffer = buffer;
	}
	
	public ExactFinder(final int priority, final String extension, final int... marker) {
		super(priority, extension);
		setMarker(marker);
	}
	
	protected ExactFinder(final int priority, final String extension) {
		super(priority, extension);
	}
	
	private int buffOffset = 0;
	public final void run() {
		while(buffer.remaining() >= marker.length) {
			boolean failed = false;
			for(final byte b : marker) {
				if(b != buffer.get()) {
					failed = true;
					break;
				}
			}
			
			if(!failed && mark())
				push(buffOffset);
				
			buffer.position(++buffOffset);
		}
	}
	
	protected boolean mark() {
		return true;
	}
	
	protected final boolean nextExact(final int ... bytes) {
		return Assist.nextExact(buffer, bytes);
	}
	protected final boolean skip(final int bytes) {
		return Assist.skip(buffer, bytes);
	}
}