package ca.dougsparling.luceneblogpost;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;

final class AsyncWriteFileToIndexVisitor extends SimpleFileVisitor<Path> {

	private final IndexWriter writer;
	private final Executor executor;

	public AsyncWriteFileToIndexVisitor(IndexWriter writer, Executor executor) {
		this.writer = writer;
		this.executor = executor;
	}

	@Override
	public FileVisitResult visitFile(Path path, BasicFileAttributes attrs)
			throws IOException {
		
		executor.execute(() -> indexPath(path));
		
		return FileVisitResult.CONTINUE;
	}
	
	private void indexPath(Path path) {
		String baseFileName = path.getFileName().toString();

		try (InputStream textStream = Files.newInputStream(path)) {
			if (isZipFile(baseFileName)) {
				indexZipFile(baseFileName, textStream);
			} else if (isTextFile(baseFileName)) {
				indexStream(textStream, baseFileName);
			}
		} catch (IOException e) {
			System.err.println("Error indexing (" + path + "): " + e.getMessage());
		}
	}

	private void indexZipFile(String baseFileName, InputStream textStream) throws IOException {
		
		ZipInputStream zipInputStream = new ZipInputStream(textStream, StandardCharsets.UTF_8) {
			
			@Override
			public void close() throws IOException {
				// Lucene closes streams when it finishes reading, but we want to continue
				// iterating through the archive, and so we must ignore those closes
			}
		};

		for (ZipEntry zippedFile = zipInputStream.getNextEntry(); zippedFile != null; zippedFile = zipInputStream.getNextEntry()) {
			String fileName = zippedFile.getName();
			
			if (isTextFile(fileName)) {
				indexStream(zipInputStream, baseFileName + ":" + fileName);
			}
		}
	}

	private void indexStream(InputStream inputStream, String title) throws IOException {
		
		System.out.printf("Indexing %s\n", title);
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

		Document document = new Document();
		document.add(new StringField("title", title, Store.YES));
		document.add(new TextField("body", reader));

		writer.addDocument(document);
	}
	
	private boolean isTextFile(String fileName) {
		return fileName.endsWith(".txt");
	}

	private boolean isZipFile(String baseFileName) {
		return baseFileName.endsWith(".zip");
	}
}