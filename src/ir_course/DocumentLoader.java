package ir_course;

import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class DocumentLoader {

  public static final int COMPARISON_SCENARIO = 13;

  public static Iterable<DocumentInCollection> loadDocs() {
    DocumentCollectionParser parser = new DocumentCollectionParser();
    System.out.println("Loading documents...");
    parser.parse("corpus_part2.xml");
    List<DocumentInCollection> allDocs = parser.getDocuments();
    return Iterables.filter(allDocs, new Predicate<DocumentInCollection>() {
      @Override
      public boolean apply(DocumentInCollection doc) {
        return doc.getSearchTaskNumber() == COMPARISON_SCENARIO;
      }
    });
  }

  public static Iterable<String> getQueries(Iterable<DocumentInCollection> docs) {
    Iterable<String> allQueries = Iterables.transform(docs, new Function<DocumentInCollection, String>() {
      @Override
      public String apply(DocumentInCollection doc) {
        return doc.getQuery();
      }
    });
    return Sets.newHashSet(allQueries);
  }

}
