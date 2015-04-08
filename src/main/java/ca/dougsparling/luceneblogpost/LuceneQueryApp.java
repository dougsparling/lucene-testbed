package ca.dougsparling.luceneblogpost;

import static java.util.Collections.singleton;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

public class LuceneQueryApp {

	private final IndexSearcher searcher;
	private Scanner stdin = new Scanner(System.in);

	public LuceneQueryApp(Path indexPath) throws IOException {
		IndexReader reader = DirectoryReader.open(FSDirectory.open(indexPath));
		this.searcher = new IndexSearcher(reader);
	}
	
	private void loop() throws IOException, ParseException {
		while(true) {
			System.out.print("query: ");
			
			String query = stdin.nextLine();
			
			if (query.isEmpty()) {
				break;
			}
			
			TopDocs results = query(query);
			
			for (ScoreDoc result : results.scoreDocs) {
				Document doc = searcher.doc(result.doc, singleton("title"));
				System.out.printf("Result: %s (score %s)\n", doc.get("title"), result.score);
			}
		}
	}

	private TopDocs query(String queryText) throws IOException, ParseException {
		Analyzer queryAnalyzer = new StandardAnalyzer();
		QueryParser parser = new QueryParser("body", queryAnalyzer);
		Query query = parser.parse(queryText);
		return searcher.search(query, 5);
	}

	public static void main(String[] args) throws IOException, ParseException {
		LuceneQueryApp queryApp = new LuceneQueryApp(Paths.get("./index"));
		queryApp.loop();
	}
}
