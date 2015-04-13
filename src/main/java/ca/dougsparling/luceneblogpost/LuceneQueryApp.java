package ca.dougsparling.luceneblogpost;

import static java.util.Collections.singleton;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermsEnum;
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

	private IndexReader reader;
	private final IndexSearcher searcher;
	
	private Scanner stdin = new Scanner(System.in);

	public LuceneQueryApp(Path indexPath) throws IOException {
		reader = DirectoryReader.open(FSDirectory.open(indexPath));
		
		this.searcher = new IndexSearcher(reader);
		this.searcher.setSimilarity(new DialogueAwareSimilarity());
	}
	
	private void loop() throws IOException, ParseException {
		while(true) {
			System.out.print("query: ");
			
			String queryText = stdin.nextLine();
			
			if (queryText.isEmpty()) {
				break;
			}
			
			Query query = buildQuery(queryText);
			
			TopDocs results = findTop10Docs(query);
			
			for (ScoreDoc result : results.scoreDocs) {
				Document doc = searcher.doc(result.doc, singleton("title"));
				
				System.out.println("--- document " + doc.getField("title").stringValue() + " ---");
				
				Explanation explanation = this.searcher.explain(query, result.doc);
				System.out.println(explanation);
			}
		}
	}
	

	private TopDocs findTop10Docs(Query query) throws IOException {
		TopScoreDocCollector collector = TopScoreDocCollector.create(10);
		searcher.search(query, new PositiveScoresOnlyCollector(collector));
		TopDocs topDocs = collector.topDocs();
		return topDocs;
	}

	private void printDocDebugInfo(ScoreDoc result) throws IOException {
		TermsEnum iterator = reader.getTermVector(result.doc, "body").iterator(null);
		
		while(iterator.next() != null) {
			DocsAndPositionsEnum pos = null;
			pos = iterator.docsAndPositions(null, pos, DocsAndPositionsEnum.FLAG_OFFSETS | DocsAndPositionsEnum.FLAG_PAYLOADS);
			
			System.out.print("term: " + pos);
			for (int ti = 0; ti < pos.freq(); ti++) {
			}
		}
	}

	private Query buildQuery(String queryText) throws IOException, ParseException {
//		Analyzer queryAnalyzer = new StandardAnalyzer();
//		QueryParser parser = new QueryParser("body", queryAnalyzer);
//		Query query = parser.parse(queryText);
//		return searcher.search(query, 5);
		
		BooleanQuery allTermsInDialogue = new BooleanQuery();
		String[] terms = queryText.split("\\W+");
		for (String term : terms) {
			PayloadTermQuery termInDialogueSubquery = new PayloadTermQuery(new Term("body", term), new AveragePayloadFunction());
			allTermsInDialogue.add(termInDialogueSubquery, Occur.MUST);
		}		
		return allTermsInDialogue;
	}

	public static void main(String[] args) throws IOException, ParseException {
		LuceneQueryApp queryApp = new LuceneQueryApp(Paths.get("./index"));
		queryApp.loop();
	}
}
