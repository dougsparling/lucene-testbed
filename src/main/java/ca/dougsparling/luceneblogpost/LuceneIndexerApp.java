package ca.dougsparling.luceneblogpost;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Recursively indexes a directory full of text files (or zip files containing
 * text files). The index is written to a directory, which will be overwritten if
 * necessary.
 */
public class LuceneIndexerApp {
	
	private final Path indexPath;

	public LuceneIndexerApp(Path indexPath) {
		this.indexPath = indexPath;
	}

	private void addToIndex(Path docPath) throws IOException, InterruptedException {
		Directory indexDir = FSDirectory.open(this.indexPath);
		
		Analyzer indexAnalyzer = CustomAnalyzers.dialogue();
		
		IndexWriterConfig writerConfig = new IndexWriterConfig(indexAnalyzer);
		writerConfig.setOpenMode(OpenMode.CREATE);
		
		ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		
		try (IndexWriter writer = new IndexWriter(indexDir, writerConfig)) {
			WriteFileToIndexVisitor indexer = new WriteFileToIndexVisitor(writer, threadPoolExecutor);
			Files.walkFileTree(docPath, indexer);
			writer.forceMerge(1);
		} finally {
			threadPoolExecutor.shutdown();
			threadPoolExecutor.awaitTermination(1, TimeUnit.HOURS);
		}
	}
	
	public static void main(String... args) throws IOException, ParseException, InterruptedException {
		if (args.length != 2) {
			System.err.println("Usage: LuceneIndexerApp pathToNewIndex pathToDocuments");
			System.exit(1);
		}
		new LuceneIndexerApp(Paths.get(args[0])).addToIndex(Paths.get(args[1]));
	}
}
