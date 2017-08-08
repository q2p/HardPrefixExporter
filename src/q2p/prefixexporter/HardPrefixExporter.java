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
	private static final int markEveryFound = 5000;
	private static final int markEveryWrite = 50;
	
	private static final Finder[] finders = {
		new PureJfifFinder(0, "jfif", 0xE0, 0x4A, 0x46, 0x49, 0x46, 0x00), // E0 JFIF
		
		new JfifFamilyFinder(0, "jfif", 0xE0, 0x4A, 0x46, 0x49, 0x46, 0x00), // E0 JFIF
		new JfifFamilyFinder(0, "jpg", 0xE1, 0x45, 0x78, 0x69, 0x66, 0x00), // E1 EXIF
		new JfifFamilyFinder(0, "jpg", 0xE8, 0x53, 0x50, 0x49, 0x46, 0x46, 0x00), // E8 SPIFF
		
		new ExactFinder(0, "gif", 0x47, 0x49, 0x46, 0x38, 0x37, 0x61), // GIF87a
		new ExactFinder(0, "gif", 0x47, 0x49, 0x46, 0x38, 0x39, 0x61), // GIF89a
		
		new ExactFinder(0, "png", 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
	};
	
	private static int rememberFor;
	
	private static byte[] buffer = null;
	
	private static final LinkedList<Cut> cuts = new LinkedList<>();
	
	public static void main(final String[] args) {
		if(!initArgs(args))
			return;
		
		if(!fill())
			return;
		
		startThreads();
		
		cutFiles();
	}
	
	private static boolean initArgs(final String[] args) {
		if(args.length == 0) {
			printGuide();
			return false;
		}
		if(args.length > 1) {
			System.out.println("Слишком много аргументов.");
			printGuide();
			return false;
		}
		try {
			rememberFor = Integer.parseInt(args[0]);
		} catch(final NumberFormatException e) {
			System.out.println("Аргумент не является числом.");
			printGuide();
			return false;
		}
		if(rememberFor < 0) {
			System.out.println("Аргумент не может быть меньше 0.");
			printGuide();
			return false;
		}
		return true;
	}
	
	private static void printGuide() {
		System.out.println("Для использования введите количество частей, которые надо объеденить.\n" +
			"1 - Каждая часть сама по себе.\n" +
			"2 - Каждая часть состоит из самой себя и следующей за ней.\n" +
			"3 - Каждая часть состоит из самой себя и двумя следующими за ней.\n" +
			"0 - Каждая часть продолжается до конца файла."
		);
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
		System.out.println("Всего было найдено "+cuts.size()+" файлов.");
	}
	
	public static synchronized void push(final int priority, final String extension, final int position) {
		assert position >= 0 && position <= buffer.length;
		
		if(position == buffer.length)
			return;
		
		final ListIterator<Cut> iterator = cuts.listIterator();
		
		while(iterator.hasNext()) {
			Cut cut = iterator.next();
			if(cut.position == position) {
				cut.update(priority, extension);
				return;
			}
			if(cut.position > position) {
				iterator.previous();
				put(iterator, priority, extension, position);
				return;
			}
		}
		put(iterator, priority, extension, position);
	}
	
	private static void put(final ListIterator<Cut> iterator, final int priority, final String extension, final int position) {
		iterator.add(new Cut(priority, extension, position));
		if(!cuts.isEmpty() && cuts.size() % markEveryFound == 0)
			System.out.println("Нашёл "+cuts.size()+" файлов.");
	}
	
	private static void cutFiles() {
		if(cuts.isEmpty()) {
			System.out.println("Не было найдено ни одного файла.");
			return;
		}
		
		final Part[] parts = getParts();
		
		final File dir = new File("out");
		if(dir.isFile()) {
			System.out.println("Файл \"out\" должен являться дирректорией.");
			return;
		}
		dir.mkdirs();
		
		final ByteBuffer wrapper = ByteBuffer.wrap(buffer);
		
		final int nameLength = Assist.compact(parts.length, 0).length();
		
		for(int i = 0; i != parts.length; i++) {
			wrapper.position(parts[i].start);
			
			if(rememberFor == 0)
				wrapper.limit(buffer.length);
			else
				wrapper.limit(parts[i+Math.min(rememberFor, parts.length-i)-1].end);
			
			if((i+1) % markEveryWrite == 0)
				System.out.println("Записываю файл №"+(i+1));
			
			if(!cutFile(Assist.compact(i, nameLength)+'.'+parts[i].extension, wrapper))
				return;
		}
		
		System.out.println("Запись файлов успешно завершена.");
	}
	
	private static Part[] getParts() {
		final Part[] ret = new Part[cuts.size()];
		
		Cut cut = cuts.removeFirst();
		for(int i = 0; cut != null; i++) {
			final String extension = cut.getExtension();
			final int start = cut.position;
			final int end;
			if(cuts.isEmpty()) {
				cut = null;
				end = buffer.length;
			} else {
				cut = cuts.removeFirst();
				end = cut.position;
			}
			ret[i] = new Part(extension, start, end);
		}
		
		return ret;
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
}