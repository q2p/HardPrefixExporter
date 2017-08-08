package q2p.prefixexporter.finders;

public final class PureJfifFinder extends JfifFamilyFinder {
	public PureJfifFinder(final int priority, final String extension, final int significant, final int ... terminating) {
		super(priority, extension, significant, terminating);
	}
	
	protected final boolean additionalCheck() {
		if(
			skip(7) &&
			buffer.remaining() >= 2
		) {
			int v = buffer.get();
			if(v < 0)
				return false;
			v *= buffer.get();
			return v >= 0 && buffer.remaining() >= v;
		}
		return false;
	}
}
