package net.ravendb.client.documents.session;

import com.google.common.collect.Sets;
import net.ravendb.client.Constants;
import net.ravendb.client.documents.indexes.spatial.SpatialRelation;
import net.ravendb.client.documents.indexes.spatial.SpatialUnits;
import net.ravendb.client.documents.queries.*;
import net.ravendb.client.documents.queries.spatial.SpatialCriteria;
import net.ravendb.client.documents.queries.spatial.SpatialCriteriaFactory;
import net.ravendb.client.documents.queries.spatial.SpatialDynamicField;
import net.ravendb.client.documents.session.tokens.DeclareToken;
import net.ravendb.client.documents.session.tokens.FieldsToFetchToken;
import net.ravendb.client.documents.session.tokens.LoadToken;
import net.ravendb.client.primitives.Reference;
import net.ravendb.client.primitives.Tuple;
import net.ravendb.client.util.ReflectionUtil;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class DocumentQuery<T> extends AbstractDocumentQuery<T, DocumentQuery<T>> implements IDocumentQuery<T> {

    public DocumentQuery(Class<T> clazz, InMemoryDocumentSessionOperations session, String indexName, String collectionName, boolean isGroupBy) {
        this(clazz, session, indexName, collectionName, isGroupBy, null, null, null);
    }

    public DocumentQuery(Class<T> clazz, InMemoryDocumentSessionOperations session, String indexName, String collectionName, boolean isGroupBy,
                         DeclareToken declareToken, List<LoadToken> loadTokens, String fromAlias) {
        super(clazz, session, indexName, collectionName, isGroupBy, declareToken, loadTokens, fromAlias);
    }

    @Override
    public <TProjection> IDocumentQuery<TProjection> selectFields(Class<TProjection> projectionClass) {
        /* TODO
         var propertyInfos = ReflectionUtil.GetPropertiesAndFieldsFor<TProjection>(BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Instance).ToList();
            var projections = propertyInfos.Select(x => x.Name).ToArray();
            var identityProperty = Conventions.GetIdentityProperty(typeof(TProjection));
            var fields = propertyInfos.Select(p => p == identityProperty ? Constants.Documents.Indexing.Fields.DocumentIdFieldName : p.Name).ToArray();
            return SelectFields<TProjection>(new QueryData(fields, projections));
         */
        return null;
    }

    @Override
    public IDocumentQuery<T> distinct() {
        _distinct();
        return this;
    }

    @Override
    public IDocumentQuery<T> orderByScore() {
        _orderByScore();
        return this;
    }

    @Override
    public IDocumentQuery<T> orderByScoreDescending() {
        _orderByScoreDescending();
        return this;
    }

    @Override
    public IDocumentQuery<T> explainScores() {
        shouldExplainScores = true;
        return this;
    }

    @Override
    public <TProjection> IDocumentQuery<TProjection> selectFields(Class<TProjection> projectionClass, String... fields) {
        QueryData queryData = new QueryData(fields, fields);
        return selectFields(projectionClass, queryData);
    }

    @Override
    public <TProjection> IDocumentQuery<TProjection> selectFields(Class<TProjection> projectionClass, QueryData queryData) {
        return createDocumentQueryInternal(projectionClass, queryData);
    }

    public IDocumentQuery<T> waitForNonStaleResults(Duration waitTimeout) {
        _waitForNonStaleResults(waitTimeout);
        return this;
    }

    @Override
    public IDocumentQuery<T> addOrder(String fieldName, boolean descending) {
        return addOrder(fieldName, descending, OrderingType.STRING);
    }

    @Override
    public IDocumentQuery<T> addOrder(String fieldName, boolean descending, OrderingType ordering) {
        if (descending) {
            orderByDescending(fieldName, ordering);
        } else {
            orderBy(fieldName, ordering);
        }
        return this;
    }

    //TBD public IDocumentQuery<T> AddOrder<TValue>(Expression<Func<T, TValue>> propertySelector, bool descending, OrderingType ordering)

    @Override
    public IDocumentQuery<T> addAfterQueryExecutedListener(Consumer<QueryResult> action) {
        _addAfterQueryExecutedListener(action);
        return this;
    }

    @Override
    public IDocumentQuery<T> removeAfterQueryExecutedListener(Consumer<QueryResult> action) {
        _removeAfterQueryExecutedListener(action);
        return this;
    }

    //TBD void IQueryBase<T, IDocumentQuery<T>>.AfterStreamExecuted(Action<BlittableJsonReaderObject> action)
    //TBD void IQueryBase<T, IRawDocumentQuery<T>>.AfterStreamExecuted(Action<BlittableJsonReaderObject> action)

    public IDocumentQuery<T> openSubclause() {
        _openSubclause();
        return this;
    }

    @Override
    public IDocumentQuery<T> closeSubclause() {
        _closeSubclause();
        return this;
    }

    @Override
    public IDocumentQuery<T> search(String fieldName, String searchTerms) {
        _search(fieldName, searchTerms);
        return this;
    }

    @Override
    public IDocumentQuery<T> search(String fieldName, String searchTerms, SearchOperator operator) {
        _search(fieldName, searchTerms, operator);
        return this;
    }

    //TBD public IDocumentQuery<T> Search<TValue>(Expression<Func<T, TValue>> propertySelector, string searchTerms, SearchOperator @operator)

    @Override
    public IDocumentQuery<T> cmpXChg(String key, T value) {
        _cmpXchg(key, value);
        return this;
    }

    @Override
    public IDocumentQuery<T> intersect() {
        _intersect();
        return this;
    }

    @Override
    public IDocumentQuery<T> containsAny(String fieldName, Collection<Object> values) {
        _containsAny(fieldName, values);
        return this;
    }

    //TBD public IDocumentQuery<T> ContainsAny<TValue>(Expression<Func<T, TValue>> propertySelector, IEnumerable<TValue> values)

    @Override
    public IDocumentQuery<T> containsAll(String fieldName, Collection<Object> values) {
        _containsAll(fieldName, values);
        return this;
    }

    //TBD public IDocumentQuery<T> ContainsAll<TValue>(Expression<Func<T, TValue>> propertySelector, IEnumerable<TValue> values)

    @Override
    public IDocumentQuery<T> statistics(Reference<QueryStatistics> stats) {
        _statistics(stats);
        return this;
    }

    @Override
    public IDocumentQuery<T> usingDefaultOperator(QueryOperator queryOperator) {
        _usingDefaultOperator(queryOperator);
        return this;
    }

    @Override
    public IDocumentQuery<T> noTracking() {
        _noTracking();
        return this;
    }

    @Override
    public IDocumentQuery<T> noCaching() {
        _noCaching();
        return this;
    }

    //TBD  public IDocumentQuery<T> showTimings()

    @Override
    public IDocumentQuery<T> include(String path) {
        _include(path);
        return this;
    }
    //TBD: IDocumentQuery<T> IDocumentQueryBase<T, IDocumentQuery<T>>.Include(Expression<Func<T, object>> path)

    @Override
    public IDocumentQuery<T> not() {
        negateNext();
        return this;
    }

    @Override
    public IDocumentQuery<T> take(int count) {
        _take(count);
        return this;
    }

    public IDocumentQuery<T> skip(int count) {
        _skip(count);
        return this;
    }

    @Override
    public IDocumentQuery<T> whereLucene(String fieldName, String whereClause) {
        _whereLucene(fieldName, whereClause);
        return this;
    }

    @Override
    public IDocumentQuery<T> whereEquals(String fieldName, Object value) {
        _whereEquals(fieldName, value, false);
        return this;
    }

    @Override
    public IDocumentQuery<T> whereEquals(String fieldName, Object value, boolean exact) {
        _whereEquals(fieldName, value, exact);
        return this;
    }

    //TBD IDocumentQuery<T> IDocumentQueryBase<T, IDocumentQuery<T>>.WhereEquals<TValue>(Expression<Func<T, TValue>> propertySelector, TValue value, bool exact)

    @Override
    public IDocumentQuery<T> whereEquals(WhereParams whereParams) {
        _whereEquals(whereParams);
        return this;
    }

    @Override
    public IDocumentQuery<T> whereNotEquals(String fieldName, Object value) {
        _whereNotEquals(fieldName, value);
        return this;
    }

    @Override
    public IDocumentQuery<T> whereNotEquals(String fieldName, Object value, boolean exact) {
        _whereNotEquals(fieldName, value, exact);
        return this;
    }

    //TBD IDocumentQuery<T> IDocumentQueryBase<T, IDocumentQuery<T>>.WhereNotEquals<TValue>(Expression<Func<T, TValue>> propertySelector, TValue value, bool exact)

    @Override
    public IDocumentQuery<T> whereNotEquals(WhereParams whereParams) {
        _whereNotEquals(whereParams);
        return this;
    }

    @Override
    public IDocumentQuery<T> whereIn(String fieldName, Collection<Object> values) {
        return whereIn(fieldName, values, false);
    }

    @Override
    public IDocumentQuery<T> whereIn(String fieldName, Collection<Object> values, boolean exact) {
        _whereIn(fieldName, values, exact);
        return this;
    }

    //TBD public IDocumentQuery<T> WhereIn<TValue>(Expression<Func<T, TValue>> propertySelector, IEnumerable<TValue> values, bool exact = false)

    @Override
    public IDocumentQuery<T> whereStartsWith(String fieldName, Object value) {
        _whereStartsWith(fieldName, value);
        return this;
    }

    @Override
    public IDocumentQuery<T> whereEndsWith(String fieldName, Object value) {
        _whereEndsWith(fieldName, value);
        return this;
    }

    //TBD: public IDocumentQuery<T> WhereEndsWith<TValue>(Expression<Func<T, TValue>> propertySelector, TValue value)

    @Override
    public IDocumentQuery<T> whereBetween(String fieldName, Object start, Object end) {
        return whereBetween(fieldName, start, end, false);
    }

    @Override
    public IDocumentQuery<T> whereBetween(String fieldName, Object start, Object end, boolean exact) {
        _whereBetween(fieldName, start, end, exact);
        return this;
    }

    //TBD public IDocumentQuery<T> WhereBetween<TValue>(Expression<Func<T, TValue>> propertySelector, TValue start, TValue end, bool exact = false)

    @Override
    public IDocumentQuery<T> whereGreaterThan(String fieldName, Object value) {
        return whereGreaterThan(fieldName, value, false);
    }

    @Override
    public IDocumentQuery<T> whereGreaterThan(String fieldName, Object value, boolean exact) {
        _whereGreaterThan(fieldName, value, exact);
        return this;
    }

    @Override
    public IDocumentQuery<T> whereGreaterThanOrEqual(String fieldName, Object value) {
        return whereGreaterThanOrEqual(fieldName, value, false);
    }

    @Override
    public IDocumentQuery<T> whereGreaterThanOrEqual(String fieldName, Object value, boolean exact) {
        _whereGreaterThanOrEqual(fieldName, value, exact);
        return this;
    }

    //TBD public IDocumentQuery<T> WhereGreaterThan<TValue>(Expression<Func<T, TValue>> propertySelector, TValue value, bool exact = false)
    //TBD public IDocumentQuery<T> WhereGreaterThanOrEqual<TValue>(Expression<Func<T, TValue>> propertySelector, TValue value, bool exact = false)

    public IDocumentQuery<T> whereLessThan(String fieldName, Object value) {
        return whereLessThan(fieldName, value, false);
    }

    public IDocumentQuery<T> whereLessThan(String fieldName, Object value, boolean exact) {
        _whereLessThan(fieldName, value, exact);
        return this;
    }

    //TBD public IDocumentQuery<T> WhereLessThanOrEqual<TValue>(Expression<Func<T, TValue>> propertySelector, TValue value, bool exact = false)

    public IDocumentQuery<T> whereLessThanOrEqual(String fieldName, Object value) {
        return whereLessThanOrEqual(fieldName, value, false);
    }

    public IDocumentQuery<T> whereLessThanOrEqual(String fieldName, Object value, boolean exact) {
        _whereLessThanOrEqual(fieldName, value, exact);
        return this;
    }

    //TBD public IDocumentQuery<T> WhereLessThanOrEqual<TValue>(Expression<Func<T, TValue>> propertySelector, TValue value, bool exact = false)
    //TBD public IDocumentQuery<T> WhereExists<TValue>(Expression<Func<T, TValue>> propertySelector)

    @Override
    public IDocumentQuery<T> whereExists(String fieldName) {
        _whereExists(fieldName);
        return this;
    }

    //TBD IDocumentQuery<T> IFilterDocumentQueryBase<T, IDocumentQuery<T>>.WhereRegex<TValue>(Expression<Func<T, TValue>> propertySelector, string pattern)
    @Override
    public IDocumentQuery<T> whereRegex(String fieldName, String pattern) {
        _whereRegex(fieldName, pattern);
        return this;
    }

    public IDocumentQuery<T> andAlso() {
        _andAlso();
        return this;
    }

    @Override
    public IDocumentQuery<T> orElse() {
        _orElse();
        return this;
    }

    @Override
    public IDocumentQuery<T> boost(double boost) {
        _boost(boost);
        return this;
    }

    @Override
    public IDocumentQuery<T> fuzzy(double fuzzy) {
        _fuzzy(fuzzy);
        return this;
    }

    @Override
    public IDocumentQuery<T> proximity(int proxomity) {
        _proximity(proxomity);
        return this;
    }

    @Override
    public IDocumentQuery<T> randomOrdering() {
        _randomOrdering();
        return this;
    }

    @Override
    public IDocumentQuery<T> randomOrdering(String seed) {
        randomOrdering(seed);
        return this;
    }

    //TBD public IDocumentQuery<T> customSortUsing(String typeName, boolean descending)

    @Override
    public IGroupByDocumentQuery<T> groupBy(String fieldName, String... fieldNames) {
        _groupBy(fieldName, fieldNames);

        return new GroupByDocumentQuery<>(this);
    }

    @Override
    public IGroupByDocumentQuery<T> groupBy(Tuple<String, GroupByMethod> field, Tuple<String, GroupByMethod>... fields) {
        _groupBy(field, fields);

        return new GroupByDocumentQuery<>(this);
    }

    @Override
    public <TResult> IDocumentQuery<TResult> ofType(Class<TResult> tResultClass) {
        return createDocumentQueryInternal(tResultClass);
    }

    public IDocumentQuery<T> orderBy(String field) {
        return orderBy(field, OrderingType.STRING);
    }

    public IDocumentQuery<T> orderBy(String field, OrderingType ordering) {
        _orderBy(field, ordering);
        return this;
    }

    //TBD public IDocumentQuery<T> OrderBy<TValue>(params Expression<Func<T, TValue>>[] propertySelectors)

    public IDocumentQuery<T> orderByDescending(String field) {
        return orderByDescending(field, OrderingType.STRING);
    }

    public IDocumentQuery<T> orderByDescending(String field, OrderingType ordering) {
        _orderByDescending(field, ordering);
        return this;
    }

    //TBD public IDocumentQuery<T> OrderByDescending<TValue>(params Expression<Func<T, TValue>>[] propertySelectors)

    @Override
    public IDocumentQuery<T> waitForNonStaleResultsAsOf(long cutoffEtag) {
        _waitForNonStaleResultsAsOf(cutoffEtag);
        return this;
    }

    @Override
    public IDocumentQuery<T> waitForNonStaleResultsAsOf(long cutOffEtag, Duration waitTimeout) {
        _waitForNonStaleResultsAsOf(cutoffEtag, waitTimeout);
        return this;
    }

    public IDocumentQuery<T> waitForNonStaleResults() {
        _waitForNonStaleResults();
        return this;
    }

    @Override
    public IDocumentQuery<T> addBeforeQueryExecutedListener(Consumer<IndexQuery> action) {
        _addBeforeQueryExecutedListener(action);
        return this;
    }

    @Override
    public IDocumentQuery<T> removeBeforeQueryExecutedListener(Consumer<IndexQuery> action) {
        _removeBeforeQueryExecutedListener(action);
        return this;
    }

    //TBD public Lazy<IEnumerable<T>> Lazily()

    //TBD public Lazy<int> CountLazily()

    //TBD public Lazy<IEnumerable<T>> Lazily(Action<IEnumerable<T>> onEval)

    private <TResult> DocumentQuery<TResult> createDocumentQueryInternal(Class<TResult> resultClass) {
        return createDocumentQueryInternal(resultClass, null);
    }

    private <TResult> DocumentQuery<TResult> createDocumentQueryInternal(Class<TResult> resultClass, QueryData queryData) {
        FieldsToFetchToken newFieldsToFetch = queryData != null && queryData.getFields().length > 0
                ? FieldsToFetchToken.create(queryData.getFields(), queryData.getProjections(), queryData.isCustomFunction())
                : null;

        if (newFieldsToFetch != null) {
            updateFieldsToFetchToken(newFieldsToFetch);
        }

        DocumentQuery query = new DocumentQuery<>(resultClass,
                theSession,
                getIndexName(),
                getCollectionName(),
                isGroupBy,
                queryData != null ? queryData.getDeclareToken() : null,
                queryData != null ? queryData.getLoadTokens() : null,
                queryData != null ? queryData.getFromAlias() : null);

        query.queryRaw = queryRaw;
        query.pageSize = pageSize;
        query.selectTokens = selectTokens;
        query.fieldsToFetchToken = fieldsToFetchToken;
        query.whereTokens = whereTokens;
        query.orderByTokens = orderByTokens;
        query.groupByTokens = groupByTokens;
        query.queryParameters = queryParameters;
        query.start = start;
        query.timeout = timeout;
        query.cutoffEtag = cutoffEtag;
        query.queryStats = queryStats;
        query.theWaitForNonStaleResults = theWaitForNonStaleResults;
        query.negate = negate;
        query.includes = new HashSet(includes);
        query.rootTypes = Sets.newHashSet(clazz);
        query.beforeQueryExecutedCallback = beforeQueryExecutedCallback;
        query.afterQueryExecutedCallback = afterQueryExecutedCallback;
        /* TBD AfterStreamExecutedCallback = AfterStreamExecutedCallback,
        query.HighlightedFields = new List<HighlightedField>(HighlightedFields),
        query.HighlighterPreTags = HighlighterPreTags,
        query.HighlighterPostTags = HighlighterPostTags,
        */
        query.disableEntitiesTracking = disableEntitiesTracking;
        query.disableCaching = disableCaching;
        //TBD ShowQueryTimings = ShowQueryTimings,
        query.lastEquality = lastEquality;
        query.shouldExplainScores = shouldExplainScores;
        query.isIntersect = isIntersect;
        query.defaultOperatator = defaultOperatator;

        return query;
    }

    //TBD public FacetedQueryResult GetFacets(string facetSetupDoc, int facetStart, int? facetPageSize)
    //TBD public FacetedQueryResult GetFacets(List<Facet> facets, int facetStart, int? facetPageSize)
    //TBD public Lazy<FacetedQueryResult> GetFacetsLazy(string facetSetupDoc, int facetStart, int? facetPageSize)
    //TBD public Lazy<FacetedQueryResult> GetFacetsLazy(List<Facet> facets, int facetStart, int? facetPageSize)
    //TBD IDocumentQuery<T> IDocumentQueryBase<T, IDocumentQuery<T>>.Highlight(string fieldName, int fragmentLength, int fragmentCount, string fragmentsField)
    //TBD IDocumentQuery<T> IDocumentQueryBase<T, IDocumentQuery<T>>.Highlight(string fieldName, int fragmentLength, int fragmentCount, out FieldHighlightings highlightings)
    //TBD IDocumentQuery<T> IDocumentQueryBase<T, IDocumentQuery<T>>.Highlight(string fieldName,string fieldKeyName, int fragmentLength,int fragmentCount,out FieldHighlightings highlightings)
    //TBD IDocumentQuery<T> IDocumentQueryBase<T, IDocumentQuery<T>>.Highlight<TValue>(Expression<Func<T, TValue>> propertySelector, int fragmentLength, int fragmentCount, Expression<Func<T, IEnumerable>> fragmentsPropertySelector)
    //TBD IDocumentQuery<T> IDocumentQueryBase<T, IDocumentQuery<T>>.Highlight<TValue>(Expression<Func<T, TValue>> propertySelector, int fragmentLength, int fragmentCount, out FieldHighlightings fieldHighlightings)
    //TBD IDocumentQuery<T> IDocumentQueryBase<T, IDocumentQuery<T>>.Highlight<TValue>(Expression<Func<T, TValue>> propertySelector, Expression<Func<T, TValue>> keyPropertySelector, int fragmentLength, int fragmentCount, out FieldHighlightings fieldHighlightings)
    //TBD IDocumentQuery<T> IDocumentQueryBase<T, IDocumentQuery<T>>.SetHighlighterTags(string preTag, string postTag)
    //TBD IDocumentQuery<T> IDocumentQueryBase<T, IDocumentQuery<T>>.SetHighlighterTags(string[] preTags, string[] postTags)
    //TBD public IDocumentQuery<T> Spatial(Expression<Func<T, object>> path, Func<SpatialCriteriaFactory, SpatialCriteria> clause)

    @Override
    public IDocumentQuery<T> spatial(String fieldName, Function<SpatialCriteriaFactory, SpatialCriteria> clause) {
        SpatialCriteria criteria = clause.apply(SpatialCriteriaFactory.INSTANCE);
        _spatial(fieldName, criteria);
        return this;
    }

    @Override
    public IDocumentQuery<T> spatial(SpatialDynamicField field, Function<SpatialCriteriaFactory, SpatialCriteria> clause) {
        SpatialCriteria criteria = clause.apply(SpatialCriteriaFactory.INSTANCE);
        _spatial(field, criteria);
        return this;
    }

    //TBD public IDocumentQuery<T> Spatial(Func<SpatialDynamicFieldFactory<T>, SpatialDynamicField> field, Func<SpatialCriteriaFactory, SpatialCriteria> clause)
    //TBD IDocumentQuery<T> IDocumentQueryBase<T, IDocumentQuery<T>>.WithinRadiusOf<TValue>(Expression<Func<T, TValue>> propertySelector, double radius, double latitude, double longitude, SpatialUnits? radiusUnits, double distanceErrorPct)

    @Override
    public IDocumentQuery<T> withinRadiusOf(String fieldName, double radius, double latitude, double longitude) {
        return withinRadiusOf(fieldName, radius, latitude, longitude, null, Constants.Documents.Indexing.Spatial.DEFAULT_DISTANCE_ERROR_PCT);
    }

    @Override
    public IDocumentQuery<T> withinRadiusOf(String fieldName, double radius, double latitude, double longitude, SpatialUnits radiusUnits) {
        return withinRadiusOf(fieldName, radius, latitude, longitude, radiusUnits, Constants.Documents.Indexing.Spatial.DEFAULT_DISTANCE_ERROR_PCT);
    }

    @Override
    public IDocumentQuery<T> withinRadiusOf(String fieldName, double radius, double latitude, double longitude, SpatialUnits radiusUnits, double distanceErrorPct) {
        _withinRadiusOf(fieldName, radius, latitude, longitude, radiusUnits, distanceErrorPct);
        return this;
    }

    //TBD IDocumentQuery<T> IDocumentQueryBase<T, IDocumentQuery<T>>.RelatesToShape<TValue>(Expression<Func<T, TValue>> propertySelector, string shapeWKT, SpatialRelation relation, double distanceErrorPct)

    @Override
    public IDocumentQuery<T> relatesToShape(String fieldName, String shapeWKT, SpatialRelation relation) {
        return relatesToShape(fieldName, shapeWKT, relation, Constants.Documents.Indexing.Spatial.DEFAULT_DISTANCE_ERROR_PCT);
    }

    @Override
    public IDocumentQuery<T> relatesToShape(String fieldName, String shapeWKT, SpatialRelation relation, double distanceErrorPct) {
        _spatial(fieldName, shapeWKT, relation, distanceErrorPct);
        return this;
    }

    //TBD IDocumentQuery<T> IDocumentQueryBase<T, IDocumentQuery<T>>.OrderByDistance<TValue>(Expression<Func<T, TValue>> propertySelector, double latitude, double longitude)

    @Override
    public IDocumentQuery<T> orderByDistance(String fieldName, double latitude, double longitude) {
        _orderByDistance(fieldName, latitude, longitude);
        return this;
    }

    //TBD IDocumentQuery<T> IDocumentQueryBase<T, IDocumentQuery<T>>.OrderByDistance<TValue>(Expression<Func<T, TValue>> propertySelector, string shapeWkt)

    @Override
    public IDocumentQuery<T> orderByDistance(String fieldName, String shapeWkt) {
        orderByDistance(fieldName, shapeWkt);
        return this;
    }

    //TBD IDocumentQuery<T> IDocumentQueryBase<T, IDocumentQuery<T>>.OrderByDistanceDescending<TValue>(Expression<Func<T, TValue>> propertySelector, double latitude, double longitude)

    @Override
    public IDocumentQuery<T> orderByDistanceDescending(String fieldName, double latitude, double longitude) {
        _orderByDistanceDescending(fieldName, latitude, longitude);
        return this;
    }

    //TBD IDocumentQuery<T> IDocumentQueryBase<T, IDocumentQuery<T>>.OrderByDistanceDescending<TValue>(Expression<Func<T, TValue>> propertySelector, string shapeWkt)

    @Override
    public IDocumentQuery<T> orderByDistanceDescending(String fieldName, String shapeWkt) {
        _orderByDistanceDescending(fieldName, shapeWkt);
        return this;
    }
}
