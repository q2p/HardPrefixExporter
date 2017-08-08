package q2p.prefixexporter;

final class Part {
	final String extension;
	
	final int start;
	final int end;
	
	Part(final String extension, final int start, final int end) {
		this.extension = extension;
		
		this.start = start;
		this.end = end;
	}
}
