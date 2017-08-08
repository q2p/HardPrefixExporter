package q2p.prefixexporter.finders;

import q2p.prefixexporter.HardPrefixExporter;

import java.nio.ByteBuffer;

public abstract class Finder implements Runnable {
	private final int priority;
	private final String extension;
	
	protected Finder(final int priority, final String extension) {
		this.priority = priority;
		this.extension = extension;
	}
	
	public abstract void setBuff(final ByteBuffer byteBuffer);
	final void push(final int position){
		HardPrefixExporter.push(priority, extension, position);
	}
}
