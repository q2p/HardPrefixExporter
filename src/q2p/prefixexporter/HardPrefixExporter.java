package q2p.prefixexporter;

import q2p.prefixexporter.finders.ExactFinder;
import q2p.prefixexporter.finders.Finder;
import q2p.prefixexporter.finders.JfifFamilyFinder;
import q2p.prefixexporter.finders.PureJfifFinder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.ListIterator;

public final class HardPrefixExporter {
	private static byte[] buffer = null;
	
	private static final LinkedList<Cut> cuts = new LinkedList<>();
	
	private static final Finder[] finders = {
		new PureJfifFinder(0, "jfif", 0xE0, 0x4A, 0x46, 0x49, 0x46, 0x00), // E0 JFIF
		
		new JfifFamilyFinder(0, "jfif", 0xE0, 0x4A, 0x46, 0x49, 0x46, 0x00), // E0 JFIF
		new JfifFamilyFinder(0, "jpg", 0xE1, 0x45, 0x78, 0x69, 0x66, 0x00), // E1 EXIF
		new JfifFamilyFinder(0, "jpg", 0xE8, 0x53, 0x50, 0x49, 0x46, 0x46, 0x00), // E8 SPIFF
		
		new ExactFinder(0, "gif", 0x47, 0x49, 0x46, 0x38, 0x37, 0x61), // GIF87a
		new ExactFinder(0, "gif", 0x47, 0x49, 0x46, 0x38, 0x39, 0x61), // GIF89a
		
		new ExactFinder(0, "png", 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
	};
	
	public static void main(final String[] args) {
		if(!fill())
			return;
		
		startThreads();
		
		cutFiles();
	}
	
	private static void cutFiles() {
		if(cuts.isEmpty()) {
			System.out.println("Не было найдено ни одного файла.");
			return;
		}
		
		final File dir = new File("out");
		if(dir.isFile()) {
			System.out.println("Файл \"out\" должен являться дирректорией.");
			return;
		}
		dir.mkdirs();
		
		final ByteBuffer wrapper = ByteBuffer.wrap(buffer);
		
		final ListIterator<Cut> iterator = cuts.listIterator();
		
		final int nameLength = Assist.compact(cuts.size(), 0).length();
		
		Cut cut = iterator.next();
		for(int id = 0; cut != null; id++) {
			wrapper.position(cut.position);
			final String extension = cut.getExtension();
			if(iterator.hasNext()) {
				cut = iterator.next();
				wrapper.limit(cut.position);
			} else {
				cut = null;
				wrapper.limit(buffer.length);
			}
			
			 if(!cutFile(Assist.compact(id, nameLength)+'.'+extension, wrapper))
				return;
		}
		
		System.out.println("Запись файлов успешно завершена.");
	}
	
	private static boolean cutFile(final String name, final ByteBuffer buffer) {
		final FileChannel fc;
		try {
			fc = FileChannel.open(Paths.get("out", name), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
		} catch(final IOException e) {
			return failedWrite(name, e);
		}
		final FileLock lock;
		try {
			lock = fc.lock();
		} catch(final IOException e) {
			Assist.safeClose(fc);
			return failedWrite(name, e);
		}
		try {
			while(buffer.hasRemaining())
				fc.write(buffer);
			
			return true;
		} catch(final IOException e) {
			return failedWrite(name, e);
		} finally {
			try {
				lock.release();
			} catch(final IOException ignore) {}
			Assist.safeClose(fc);
		}
	}
	
	private static boolean failedWrite(final String name, final Throwable e) {
		System.out.println("Не удалось записать в файл \"out/"+name+"\".");
		System.out.println(e.getMessage());
		return false;
	}
	
	private static void startThreads() {
		final Thread[] threads = new Thread[finders.length];
		for(int i = threads.length-1; i != -1; i--) {
			finders[i].setBuff(ByteBuffer.wrap(buffer).asReadOnlyBuffer());
			threads[i] = new Thread(finders[i]);
			threads[i].start();
		}
		for(final Thread thread : threads) {
			try {
				thread.join();
			} catch(final InterruptedException ignore) {}
		}
	}
	
	private static boolean fill() {
		FileChannel fc;
		try {
			fc = FileChannel.open(Paths.get("in"), StandardOpenOption.READ);
		} catch(final IOException e) {
			System.out.println("Не удалось открыть файл \"in\".");
			System.out.println(e.getMessage());
			return false;
		}
		try {
			final long size = fc.size();
			if(size > 1024*1024*1024) {
				throw new OutOfMemoryError();
			}
			buffer = new byte[(int)size];
			ByteBuffer wrapper = ByteBuffer.wrap(buffer);
			while(wrapper.hasRemaining()) {
				if(fc.read(wrapper) == -1 && wrapper.hasRemaining()) {
					throw new IOException("Преждевременный EOF");
				}
			}
			return true;
		} catch(final IOException e) {
			System.out.println("Ошибка чтения файла \"in\".");
			System.out.println(e.getMessage());
			return false;
		} catch(final OutOfMemoryError e) {
			System.out.println("Файл \"in\" слишком большой");
			return false;
		} finally {
			try {
				fc.close();
			} catch(final Exception ignore) {}
		}
	}
	
	public static synchronized void push(final int priority, final String extension, final int position) {
		assert position >= 0 && position <= buffer.length;
		
		if(position == 1_992_919)
			System.out.println(position);
		
		final ListIterator<Cut> iterator = cuts.listIterator();
		
		while(iterator.hasNext()) {
			Cut cut = iterator.next();
			if(cut.position == position) {
				cut.update(priority, extension);
				return;
			}
			if(cut.position > position) {
				iterator.previous();
				iterator.add(new Cut(priority, extension, position));
				return;
			}
		}
		iterator.add(new Cut(priority, extension, position));
	}
}