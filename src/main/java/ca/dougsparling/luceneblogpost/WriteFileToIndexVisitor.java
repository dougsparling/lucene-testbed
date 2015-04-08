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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;

final class WriteFileToIndexVisitor extends FileVisitorAdapter {
	private final IndexWriter writer;

	public WriteFileToIndexVisitor(IndexWriter writer) {
		this.writer = writer;
	}

	@Override
	public FileVisitResult visitFile(Path path, BasicFileAttributes attrs)
			throws IOException {
		if (path.toFile().isFile()) {
			String baseFileName = path.getFileName().toString();

			try (InputStream textStream = Files.newInputStream(path)) {

				boolean isZipFile = baseFileName.endsWith(".zip");

				if (isZipFile) {

					ZipInputStream zipInputStream = new ZipInputStream(
							textStream, StandardCharsets.UTF_8) {
						@Override
						public void close() throws IOException {
							// dammit lucene... stop closing my streams
						}
					};

					for (ZipEntry zippedFile = zipInputStream.getNextEntry(); zippedFile != null; zippedFile = zipInputStream
							.getNextEntry()) {
						String fileName = zippedFile.getName();
						indexFromStream(zipInputStream, baseFileName + ":"
								+ fileName);
					}

				} else {
					indexFromStream(textStream, baseFileName);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		return super.visitFile(path, attrs);
	}

	private void indexFromStream(InputStream inputStream, String title)
			throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				inputStream));

		Document document = new Document();
		document.add(new StringField("title", title, Store.YES));
		document.add(new TextField("body", reader));

		System.out.println("indexing: " + title);

		writer.addDocument(document);
	}
}