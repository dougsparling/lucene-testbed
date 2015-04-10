package ca.dougsparling.luceneblogpost;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;

final class WriteFileToIndexVisitor extends FileVisitorAdapter {

	private final ExecutorService executor = Executors.newFixedThreadPool(8);

	private final IndexWriter writer;

	public WriteFileToIndexVisitor(IndexWriter writer) {
		this.writer = writer;
	}

	@Override
	public FileVisitResult visitFile(Path path, BasicFileAttributes attrs)
			throws IOException {
		
		if (path.toFile().isFile()) {
			executor.submit(() -> indexPath(path));
		}

		return super.visitFile(path, attrs);
	}
	
	public void finish() throws InterruptedException {
		this.executor.shutdown();
		this.executor.awaitTermination(1, TimeUnit.DAYS);
	}
	
	private void indexPath(Path path) {
		String baseFileName = path.getFileName().toString();

		try (InputStream textStream = Files.newInputStream(path)) {

			if (baseFileName.endsWith(".zip")) {

				ZipInputStream zipInputStream = new ZipInputStream(textStream, StandardCharsets.UTF_8) {
					
					@Override
					public void close() throws IOException {
						// Lucene closes streams when it finishes reading, but we want to continue
						// iterating through the archive, and so we must ignore those closes
					}
				};

				for (ZipEntry zippedFile = zipInputStream.getNextEntry(); zippedFile != null; zippedFile = zipInputStream
						.getNextEntry()) {
					String fileName = zippedFile.getName();
					
					if (fileName.endsWith(".txt")) {
						indexFromStream(zipInputStream, baseFileName + ":" + fileName);
					}
				}

			} else if (baseFileName.endsWith(".txt")) {
				indexFromStream(textStream, baseFileName);
			}
		} catch (IOException e) {
			System.err.println("Error indexing: " + e.getMessage());
		}
	}

	private void indexFromStream(InputStream inputStream, String title)
			throws IOException {
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

		Document document = new Document();
		document.add(new StringField("title", title, Store.YES));
		document.add(new TextField("body", reader));

		System.out.println("indexing: " + title);

		writer.addDocument(document);
	}
}