package ca.dougsparling.luceneblogpost;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class LuceneIndexerApp {
	
	private final Path indexPath;

	public LuceneIndexerApp(Path indexPath) {
		this.indexPath = indexPath;
	}

	private void addToIndex(Path docPath) throws IOException {
		Directory indexDir = FSDirectory.open(this.indexPath);
		
		Analyzer indexAnalyzer = CustomAnalyzers.dialogue();
		
		IndexWriterConfig writerConfig = new IndexWriterConfig(indexAnalyzer);
		writerConfig.setOpenMode(OpenMode.CREATE);
		
		try (IndexWriter writer = new IndexWriter(indexDir, writerConfig)) {
			Files.walkFileTree(docPath, new WriteFileToIndexVisitor(writer));
			writer.forceMerge(1);
		}
	}
	
	public static void main(String... args) throws IOException, ParseException {
		new LuceneIndexerApp(Paths.get("./index")).addToIndex(Paths.get("F:\\6\\0\\2\\6023"));
	}
}
