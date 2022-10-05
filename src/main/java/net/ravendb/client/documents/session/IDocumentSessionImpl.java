package net.ravendb.client.documents.session;

import net.ravendb.client.documents.Lazy;
import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.documents.operations.timeSeries.AbstractTimeSeriesRange;
import net.ravendb.client.documents.session.operations.lazy.IEagerSessionOperations;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface IDocumentSessionImpl extends IDocumentSession, IEagerSessionOperations {

    DocumentConventions getConventions();

    <T> Map<String, T> loadInternal(Class<T> clazz, String[] ids, String[] includes);

    <T> Map<String, T> loadInternal(Class<T> clazz, String[] ids, String[] includes, String[] counterIncludes);

    <T> Map<String, T> loadInternal(Class<T> clazz, String[] ids, String[] includes, String[] counterIncludes,
                                    boolean includeAllCounters);

    <T> Map<String, T> loadInternal(Class<T> clazz, String[] ids, String[] includes, String[] counterIncludes,
                                    boolean includeAllCounters, List<AbstractTimeSeriesRange> timeSeriesIncludes);

    <T> Map<String, T> loadInternal(Class<T> clazz, String[] ids, String[] includes, String[] counterIncludes,
                                    boolean includeAllCounters, List<AbstractTimeSeriesRange> timeSeriesIncludes,
                                    String[] compareExchangeValueIncludes);

    <T> Map<String, T> loadInternal(Class<T> clazz, String[] ids, String[] includes, String[] counterIncludes,
                                    boolean includeAllCounters, List<AbstractTimeSeriesRange> timeSeriesIncludes,
                                    String[] compareExchangeValueIncludes, String[] revisionIncludes,
                                    Date revisionIncludeByDateTimeBefore);

    <T> Lazy<Map<String, T>> lazyLoadInternal(Class<T> clazz, String[] ids, String[] includes, Consumer<Map<String, T>> onEval);
}
