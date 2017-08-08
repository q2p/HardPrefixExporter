package q2p.prefixexporter.finders;

public class JfifFamilyFinder extends ExactFinder {
	private final int[] terminating;
	
	public JfifFamilyFinder(final int priority, final String extension, final int significant, final int ... terminating) {
		super(priority, extension);
		int[] marker = { 0xFF, 0xD8, 0xFF, 0 };
		marker[3] = significant;
		setMarker(marker);
		this.terminating = terminating;
	}
	
	protected final boolean mark() {
		return
			skip(2) &&
			nextExact(terminating) &&
			additionalCheck();
	}
	
	protected boolean additionalCheck() {
		return true;
	}
}
