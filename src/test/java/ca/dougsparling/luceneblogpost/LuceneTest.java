package ca.dougsparling.luceneblogpost;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.nio.file.Files;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PositiveScoresOnlyCollector;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.payloads.MinPayloadFunction;
import org.apache.lucene.search.payloads.PayloadTermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Test;

import ca.dougsparling.luceneblogpost.search.DialogueAwareSimilarity;

/**
 * Unit test for simple App.
 */
public class LuceneTest {
	@Test
	public void testDialogueAnalyzer() throws IOException, ParseException {
		Analyzer dialogue = CustomAnalyzers.dialogue();

		TokenStream stream = dialogue
				.tokenStream(
						"test",
						"Here is a \"phrase that has been quoted\", plus extra stuff \"also quoted\" after it!");

		stream.reset();
		while (stream.incrementToken())
			;
		stream.end();
		stream.close();
	}

	@Test
	public void testSearch() throws IOException {
		RAMDirectory inMemIndex = new RAMDirectory();

		Analyzer indexAnalyzer = CustomAnalyzers.dialogue();

		IndexWriterConfig writerConfig = new IndexWriterConfig(indexAnalyzer);
		writerConfig.setOpenMode(OpenMode.CREATE);
		writerConfig.setRAMBufferSizeMB(1.0);

		try (IndexWriter writer = new IndexWriter(inMemIndex, writerConfig)) {

			Document test = new Document();
			test.add(new TextField(
					"test",
					"Here is a \"phrase that has been quoted\", plus extra stuff \"also quoted\" after it!",
					Store.NO));
			writer.addDocument(test);

			writer.forceMerge(1);
		}

		IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(inMemIndex));
		searcher.setSimilarity(new DialogueAwareSimilarity());
		
		Query query = new PayloadTermQuery(new Term("test", "phrase"), new MinPayloadFunction());
		TopDocs topDocs = findTop10Docs(searcher, query);
		
		//assertThat(results.totalHits, is(1));
		
		for (ScoreDoc doc : topDocs.scoreDocs) {
			System.out.println("Doc: " + doc.toString());
			System.out.println("Explain: " + searcher.explain(query, doc.doc));
		}

		query = new PayloadTermQuery(new Term("test", "extra"), new MinPayloadFunction());
		topDocs = findTop10Docs(searcher, query);
		
		//assertThat(results.totalHits, is(0));	
		
		for (ScoreDoc doc : topDocs.scoreDocs) {
			System.out.println("Doc: " + doc.toString());
			System.out.println("Explain: " + searcher.explain(query, doc.doc));
		}
	}

	private TopDocs findTop10Docs(IndexSearcher searcher, Query query) throws IOException {
		TopScoreDocCollector collector = TopScoreDocCollector.create(10);
		searcher.search(query, new PositiveScoresOnlyCollector(collector));
		TopDocs topDocs = collector.topDocs();
		return topDocs;
	}
}
