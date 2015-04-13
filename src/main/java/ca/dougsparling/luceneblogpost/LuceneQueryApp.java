package ca.dougsparling.luceneblogpost;

import static java.util.Collections.singleton;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PositiveScoresOnlyCollector;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.payloads.AveragePayloadFunction;
import org.apache.lucene.search.payloads.PayloadTermQuery;
import org.apache.lucene.store.FSDirectory;

import ca.dougsparling.luceneblogpost.search.DialogueAwareSimilarity;

public class LuceneQueryApp {

	private final IndexReader reader;
	private final IndexSearcher searcher;
	
	private Scanner stdin = new Scanner(System.in);

	public LuceneQueryApp(Path indexPath) throws IOException {
		reader = DirectoryReader.open(FSDirectory.open(indexPath));
		
		this.searcher = new IndexSearcher(reader);
		this.searcher.setSimilarity(new DialogueAwareSimilarity());
	}
	
	private void loop() throws IOException, ParseException {
		String queryText = askForNextQuery();
		while(queryText != null) {
			
			Query query = buildQuery(queryText);
			
			TopDocs results = findTopDocs(query, 10);
			
			printQueryResults(query, results);
			
			queryText = askForNextQuery();
		}
	}

	private String askForNextQuery() {
		System.out.print("Query: ");
		
		String queryText = stdin.nextLine();
		
		if (queryText.isEmpty()) {
			return null;
		}
		
		return queryText;
	}

	private TopDocs findTopDocs(Query query, int topN) throws IOException {
		TopScoreDocCollector collector = TopScoreDocCollector.create(topN);
		searcher.search(query, new PositiveScoresOnlyCollector(collector));
		return collector.topDocs();
	}

	private Query buildQuery(String queryText) throws IOException, ParseException {	
		BooleanQuery allTermsInDialogue = new BooleanQuery();
		String[] terms = queryText.split("\\W+");
		for (String term : terms) {
			PayloadTermQuery termInDialogueSubquery = new PayloadTermQuery(new Term("body", term), new AveragePayloadFunction());
			allTermsInDialogue.add(termInDialogueSubquery, Occur.MUST);
		}		
		return allTermsInDialogue;
	}
	
	private void printQueryResults(Query query, TopDocs results) throws IOException {
		for (ScoreDoc result : results.scoreDocs) {
			Document doc = searcher.doc(result.doc, singleton("title"));
			
			System.out.println("--- Document " + doc.getField("title").stringValue() + " ---");
			
			Explanation explanation = this.searcher.explain(query, result.doc);
			System.out.println(explanation);
		}
	}

	public static void main(String[] args) throws IOException, ParseException {
		if (args.length != 1) {
			System.err.println("Usage: LuceneQueryApp pathToExistingIndex");
			System.exit(1);
		}
		LuceneQueryApp queryApp = new LuceneQueryApp(Paths.get(args[0]));
		queryApp.loop();
	}
}
