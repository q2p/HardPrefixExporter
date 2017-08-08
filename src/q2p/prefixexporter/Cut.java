package q2p.prefixexporter;

final class Cut {
	private int priority;
	private String extension;
	final String getExtension() {
		return extension;
	}
	
	final int position;
	
	Cut(final int priority, final String extension, final int position) {
		this.priority = priority;
		this.extension = extension;
		this.position = position;
	}
	
	final void update(final int priority, final String extension) {
		if(priority > this.priority) {
			this.priority = priority;
			this.extension = extension;
		}
	}
}
