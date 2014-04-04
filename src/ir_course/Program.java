package ir_course;

import java.io.IOException;
import java.text.DecimalFormat;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;


public class Program {

  public static void main(String[] args) {
    new Program().run();
    System.out.println("Bye");
  }

  final RAMDirectory index = new RAMDirectory();
  final Iterable<DocumentInCollection> docs = DocumentLoader.loadDocs();
  final Iterable<String> queries = DocumentLoader.getQueries(docs);

  public Program() {
    System.out.println("Queries: " + queries);
  }

  public void run() {
    indexDocuments();
    performSearches();
  }

  private void indexDocuments() {
    try {
      System.out.println("Indexing documents...");
      Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_42);
      IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_42, analyzer);
      config.setOpenMode(OpenMode.CREATE);

      IndexWriter writer = new IndexWriter(index, config);
      for (DocumentInCollection doc : docs) {
        writer.addDocument(documentToLuceneDoc(doc));
      }

      writer.commit();
      writer.close();
    } catch (Exception e) {
      System.err.println("Index creation failed: " + e.getMessage());
    }
  }


  private void performSearches() {
    try {
      for (String q : queries) {
        System.out.println("\n-- " + q);
        System.out.println("\nBM25");
        Iterable<SearchResult> results_bm25 = search(q, new BM25Similarity());
        for (SearchResult res : results_bm25) {
          System.out.println(res);
        }
        System.out.println("\nTF-IDF");
        Iterable<SearchResult> results_tfidf = search(q, new DefaultSimilarity()); // tf-idf
        for (SearchResult res : results_tfidf) {
          System.out.println(res);
        }
      }
    } catch (Exception e) {
      System.err.println("Search failed! " + e.getMessage());
    }
  }

  private Iterable<SearchResult> search(String q, Similarity similarity) throws Exception {
    IndexReader reader = DirectoryReader.open(index);
    try {
      final IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setSimilarity(similarity);
      TopDocs docs = searcher.search(buildQuery(q), Integer.MAX_VALUE);
      return Lists.newArrayList(Iterables.transform(Lists.newArrayList(docs.scoreDocs), toSearchResult(searcher)));
    } finally {
      reader.close();
    }
  }

  private Query buildQuery(String q) {
    PhraseQuery query = new PhraseQuery();
    for (String term : q.split(" ")) {
      query.add(new Term("abstract", term));
    }
    query.setSlop(15);
    return query;
  }

  private Function<ScoreDoc, SearchResult> toSearchResult(final IndexSearcher searcher) {
    return new Function<ScoreDoc, SearchResult>() {
      @Override
      public SearchResult apply(ScoreDoc d) {
        try {
          Document doc = searcher.doc(d.doc);
          return new SearchResult(doc.get("title"), d.score, Integer.valueOf(doc.get("relevant")) == 1);
        } catch (IOException e) {
          throw new RuntimeException("Conversion failed: " + d);
        }
      }
    };
  }

  private Document documentToLuceneDoc(DocumentInCollection doc) {
    Document luceneDoc = new Document();
    luceneDoc.add(new TextField("title", doc.getTitle(), Field.Store.YES));
    luceneDoc.add(new TextField("query", doc.getQuery(), Field.Store.YES));
    luceneDoc.add(new TextField("abstract", doc.getAbstractText(), Field.Store.YES));
    luceneDoc.add(new IntField("relevant", doc.isRelevant() ? 1 : 0, Field.Store.YES));
    return luceneDoc;
  }

  private static class SearchResult {
    public final String title;
    public final float score;
    public final boolean relevant;
    public SearchResult(String title, float score, boolean relevant) {
      this.title = title;
      this.score = score;
      this.relevant = relevant;
    }
    @Override
    public String toString() {
      return String.format("%s || %b || %s", new DecimalFormat("0.000000").format(score), relevant, title);
    }
  }
}
