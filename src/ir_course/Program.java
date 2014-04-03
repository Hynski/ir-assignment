package ir_course;

import java.io.IOException;

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
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
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
        Iterable<DocumentInCollection> results = search(q);
        for (DocumentInCollection doc : results) {
          System.out.println(doc);
        }
      }
    } catch (Exception e) {
      System.err.println("Search failed! " + e.getMessage());
    }
  }

  private Iterable<DocumentInCollection> search(String q) throws Exception {
    PhraseQuery query = new PhraseQuery();
    for (String term : q.split(" ")) {
      query.add(new Term("abstract", term));
    }
    query.setSlop(15);

    IndexReader reader = DirectoryReader.open(index);
    try {
      final IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setSimilarity(new BM25Similarity());  // <<<<<<<<

      TopDocs docs = searcher.search(query, Integer.MAX_VALUE);
      return Lists.newArrayList(Iterables.transform(Lists.newArrayList(docs.scoreDocs), new Function<ScoreDoc, DocumentInCollection>() {
        @Override
        public DocumentInCollection apply(ScoreDoc d) {
          try {
            Document doc = searcher.doc(d.doc);
            return new DocumentInCollection(doc.get("title"), doc.get("abstract"),
                DocumentLoader.COMPARISON_SCENARIO, doc.get("query"), false);
          } catch (IOException e) {
            throw new RuntimeException("Conversion failed: " + d);
          }
        }
      }));
    } finally {
      reader.close();
    }
  }

  private Document documentToLuceneDoc(DocumentInCollection doc) {
    Document luceneDoc = new Document();
    luceneDoc.add(new TextField("title", doc.getTitle(), Field.Store.YES));
    luceneDoc.add(new TextField("query", doc.getQuery(), Field.Store.YES));
    luceneDoc.add(new TextField("abstract", doc.getAbstractText(), Field.Store.YES));
    luceneDoc.add(new IntField("relevant", doc.isRelevant() ? 1 : 0, Field.Store.YES));
    return luceneDoc;
  }
}
