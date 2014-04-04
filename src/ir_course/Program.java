package ir_course;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
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
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;


public class Program {

  public static final int MAX_STEPS = 11;

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
      Analyzer analyzer = new EnglishAnalyzer(Version.LUCENE_42);
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
      for (String searchQuery : queries) {
        System.out.println("\n**********************");
        System.out.println("QUERY:      " + searchQuery);
        System.out.println("RELEVANT:   " + getRelevantDocs(searchQuery).size());
        System.out.println("SIMILARITY: BM25");
        performSearchAndPrintResults(searchQuery, new BM25Similarity());
        System.out.println("\nSIMILARITY: TF-IDF");
        performSearchAndPrintResults(searchQuery, new DefaultSimilarity());
      }
    } catch (Exception e) {
      System.err.println("Search failed! " + e.getMessage());
    }
  }

  private List<DocumentInCollection> getRelevantDocs(final String searchQuery) {
    return Lists.newArrayList(Iterables.filter(docs, new Predicate<DocumentInCollection>() {
      @Override
      public boolean apply(DocumentInCollection doc) {
        return searchQuery.equals(doc.getQuery()) && doc.isRelevant();
      }
    }));
  }

  private List<SearchResult> getRelevantResults(Iterable<SearchResult> results) {
    return Lists.newArrayList(Iterables.filter(results, new Predicate<SearchResult>() {
      @Override
      public boolean apply(SearchResult doc) {
        return doc.relevant;
      }
    }));
  }

  private void performSearchAndPrintResults(String query, Similarity similarity) throws Exception {
    int relevantDocsInCollection = getRelevantDocs(query).size();
    ArrayList<SearchResult> results = Lists.newArrayList(search(query, similarity));
    System.out.println("n;precision;recall");
    for (int i = 1; i <= MAX_STEPS; i++) {
      printNStepKeyValues(results.subList(0, i), relevantDocsInCollection);
    }
  }

  private void printNStepKeyValues(List<SearchResult> stepResults, int relevantDocsInCollection) {
    DecimalFormat df = new DecimalFormat("0.000000");
    int itemsRetrieved = stepResults.size();
    int relevantItems = getRelevantResults(stepResults).size();
    System.out.println(String.format("%d;%s;%s", itemsRetrieved, df.format((double)relevantItems / itemsRetrieved),
        df.format((double)relevantItems / relevantDocsInCollection)));
  }

  private Iterable<SearchResult> search(final String q, final Similarity similarity) throws Exception {
    IndexReader reader = DirectoryReader.open(index);
    try {
      final IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setSimilarity(similarity);
      TopDocs docs = searcher.search(buildQuery(q), Integer.MAX_VALUE);
      Iterable<ScoreDoc> docsWithSameQuery = Iterables.filter(Lists.newArrayList(docs.scoreDocs), onlyDocsWithQuery(q, searcher));
      return Lists.newArrayList(Iterables.transform(docsWithSameQuery, toSearchResult(searcher)));
    } finally {
      reader.close();
    }
  }

  private Query buildQuery(String q) {
    BooleanQuery query = new BooleanQuery();
    for (String term : q.split(" ")) {
      query.add(new BooleanClause(new TermQuery(new Term("abstract", term)), BooleanClause.Occur.SHOULD));
    }
    return query;
  }

  private Predicate<ScoreDoc> onlyDocsWithQuery(final String q, final IndexSearcher searcher) {
    return new Predicate<ScoreDoc>() {
      @Override
      public boolean apply(ScoreDoc d) {
        try {
          return searcher.doc(d.doc).get("query").equals(q);
        } catch (IOException e) {
          return false;
        }
      }
    };
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
      return String.format("%s;%b;%s", new DecimalFormat("0.000000").format(score), relevant, title);
    }
  }
}
