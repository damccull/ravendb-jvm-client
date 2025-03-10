package net.ravendb.client.documents.session;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Defaults;
import net.ravendb.client.Constants;
import net.ravendb.client.Parameters;
import net.ravendb.client.documents.Lazy;
import net.ravendb.client.documents.commands.QueryCommand;
import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.documents.indexes.spatial.SpatialRelation;
import net.ravendb.client.documents.indexes.spatial.SpatialUnits;
import net.ravendb.client.documents.operations.timeSeries.AbstractTimeSeriesRange;
import net.ravendb.client.documents.queries.*;
import net.ravendb.client.documents.queries.explanation.ExplanationOptions;
import net.ravendb.client.documents.queries.explanation.Explanations;
import net.ravendb.client.documents.queries.facets.FacetBase;
import net.ravendb.client.documents.queries.highlighting.HighlightingOptions;
import net.ravendb.client.documents.queries.highlighting.Highlightings;
import net.ravendb.client.documents.queries.highlighting.QueryHighlightings;
import net.ravendb.client.documents.queries.moreLikeThis.MoreLikeThisScope;
import net.ravendb.client.documents.queries.spatial.SpatialCriteria;
import net.ravendb.client.documents.queries.spatial.DynamicSpatialField;
import net.ravendb.client.documents.queries.suggestions.SuggestionBase;
import net.ravendb.client.documents.queries.suggestions.SuggestionOptions;
import net.ravendb.client.documents.queries.suggestions.SuggestionWithTerm;
import net.ravendb.client.documents.queries.suggestions.SuggestionWithTerms;
import net.ravendb.client.documents.queries.timeSeries.ITimeSeriesQueryBuilder;
import net.ravendb.client.documents.queries.timeSeries.TimeSeriesQueryBuilder;
import net.ravendb.client.documents.queries.timings.QueryTimings;
import net.ravendb.client.documents.session.loaders.IncludeBuilderBase;
import net.ravendb.client.documents.session.operations.QueryOperation;
import net.ravendb.client.documents.session.operations.lazy.LazyQueryOperation;
import net.ravendb.client.documents.session.tokens.*;
import net.ravendb.client.exceptions.InvalidQueryException;
import net.ravendb.client.extensions.JsonExtensions;
import net.ravendb.client.primitives.CleanCloseable;
import net.ravendb.client.primitives.EventHelper;
import net.ravendb.client.primitives.Reference;
import net.ravendb.client.primitives.Tuple;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/**
 * A query against a Raven index
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractDocumentQuery<T, TSelf extends AbstractDocumentQuery<T, TSelf>> implements IAbstractDocumentQuery<T> {

    protected final Class<T> clazz;

    private final Map<String, String> _aliasToGroupByFieldName = new HashMap<>();

    protected QueryOperator defaultOperator = QueryOperator.AND;

    protected Set<Class> rootTypes = new HashSet<>();

    /**
     * Whether to negate the next operation
     */
    protected boolean negate;

    /**
     *  Whether to negate the next operation in Filter
     */
    protected boolean negateFilter;

    private final String indexName;
    private final String collectionName;
    private int _currentClauseDepth;

    protected String queryRaw;

    public String getIndexName() {
        return indexName;
    }

    public String getCollectionName() {
        return collectionName;
    }

    protected Stack<Boolean> filterModeStack = new Stack<>();

    protected Parameters queryParameters = new Parameters();

    protected boolean isIntersect;

    protected boolean isGroupBy;

    protected final InMemoryDocumentSessionOperations theSession;

    protected Integer pageSize;

    protected List<QueryToken> selectTokens = new LinkedList<>();

    protected final FromToken fromToken;
    protected final List<DeclareToken> declareTokens;
    protected final List<LoadToken> loadTokens;
    protected FieldsToFetchToken fieldsToFetchToken;

    public boolean isProjectInto;

    protected List<QueryToken> whereTokens = new LinkedList<>();

    protected List<QueryToken> groupByTokens = new LinkedList<>();

    protected List<QueryToken> orderByTokens = new LinkedList<>();

    protected List<QueryToken> withTokens = new LinkedList<>();

    protected List<QueryToken> filterTokens = new LinkedList<>();

    protected QueryToken graphRawQuery;

    protected int start;

    private final DocumentConventions _conventions;

    /**
     * Limits filter clause.
     */
    protected Integer filterLimit;

    protected Duration timeout;

    protected boolean theWaitForNonStaleResults;

    protected Set<String> documentIncludes = new HashSet<>();

    /**
     * Holds the query stats
     */
    protected QueryStatistics queryStats = new QueryStatistics();

    protected boolean disableEntitiesTracking;

    protected boolean disableCaching;

    protected ProjectionBehavior projectionBehavior;

    private String parameterPrefix = "p";

    protected boolean isFilterActive() {
        return !filterModeStack.empty() && filterModeStack.peek();
    }

    public boolean isDistinct() {
        return !selectTokens.isEmpty() && selectTokens.get(0) instanceof DistinctToken;
    }

    public FieldsToFetchToken getFieldsToFetchToken() {
        return fieldsToFetchToken;
    }

    public void setFieldsToFetchToken(FieldsToFetchToken fieldsToFetchToken) {
        this.fieldsToFetchToken = fieldsToFetchToken;
    }

    public boolean isProjectInto() {
        return isProjectInto;
    }

    public void setProjectInto(boolean projectInto) {
        isProjectInto = projectInto;
    }

    /**
     * Gets the document convention from the query session
     */
    @Override
    public DocumentConventions getConventions() {
        return _conventions;
    }

    /**
     * Gets the session associated with this document query
     * @return session
     */
    public IDocumentSession getSession() {
        return (IDocumentSession) theSession;
    }

    @Override
    public boolean isDynamicMapReduce() {
        return !groupByTokens.isEmpty();
    }

    private boolean _isInMoreLikeThis;

    private String _includesAlias;

    private Duration getDefaultTimeout() {
        return _conventions.getWaitForNonStaleResultsTimeout();
    }

    protected AbstractDocumentQuery(Class<T> clazz, InMemoryDocumentSessionOperations session, String indexName,
                                    String collectionName, boolean isGroupBy, List<DeclareToken> declareTokens,
                                    List<LoadToken> loadTokens) {
        this(clazz, session, indexName, collectionName, isGroupBy, declareTokens, loadTokens, null, false);
    }

    protected AbstractDocumentQuery(Class<T> clazz, InMemoryDocumentSessionOperations session, String indexName,
                                    String collectionName, boolean isGroupBy, List<DeclareToken> declareTokens,
                                    List<LoadToken> loadTokens, String fromAlias, Boolean isProjectInto) {
        this.clazz = clazz;
        rootTypes.add(clazz);
        this.isGroupBy = isGroupBy;
        this.indexName = indexName;
        this.collectionName = collectionName;
        this.fromToken = FromToken.create(indexName, collectionName, fromAlias);
        this.declareTokens = declareTokens;
        this.loadTokens = loadTokens;
        theSession = session;
        _addAfterQueryExecutedListener(this::updateStatsHighlightingsAndExplanations);
        _conventions = session == null ? new DocumentConventions() : session.getConventions();
        this.isProjectInto = isProjectInto != null ? isProjectInto : false;
    }

    public Class<T> getQueryClass() {
        return clazz;
    }

    public QueryToken getGraphRawQuery() {
        return graphRawQuery;
    }

    public void _usingDefaultOperator(QueryOperator operator) {
        if (!getCurrentWhereTokens().isEmpty()) {
            throw new IllegalStateException("Default operator can only be set before any where clause is added.");
        }

        defaultOperator = operator;
    }

    /**
     * Instruct the query to wait for non stale result for the specified wait timeout.
     * This shouldn't be used outside of unit tests unless you are well aware of the implications
     * @param waitTimeout Wait timeout
     */
    @Override
    public void _waitForNonStaleResults(Duration waitTimeout) {
        //Graph queries may set this property multiple times
        if (theWaitForNonStaleResults) {
            if (timeout == null || waitTimeout != null && timeout.getSeconds() < waitTimeout.getSeconds()) {
                timeout = waitTimeout;
            }
            return;
        }

        theWaitForNonStaleResults = true;
        timeout = ObjectUtils.firstNonNull(waitTimeout, getDefaultTimeout());
    }

    protected LazyQueryOperation<T> getLazyQueryOperation() {
        if (queryOperation == null) {
            queryOperation = initializeQueryOperation();
        }

        return new LazyQueryOperation<>(clazz, theSession, queryOperation, afterQueryExecutedCallback);
    }

    public QueryOperation initializeQueryOperation() {
        BeforeQueryEventArgs beforeQueryExecutedEventArgs = new BeforeQueryEventArgs(theSession, new DocumentQueryCustomizationDelegate(this));
        theSession.onBeforeQueryInvoke(beforeQueryExecutedEventArgs);

        IndexQuery indexQuery = getIndexQuery();

        return new QueryOperation(theSession, indexName, indexQuery, fieldsToFetchToken, disableEntitiesTracking, false, false, isProjectInto);
    }

    public IndexQuery getIndexQuery() {
        String serverVersion = null;
        if (theSession != null && theSession.getRequestExecutor() != null) {
            serverVersion = theSession.getRequestExecutor().getLastServerVersion();
        }

        boolean compatibilityMode = serverVersion != null && serverVersion.compareTo("4.2") < 0;

        String query = toString(compatibilityMode);
        IndexQuery indexQuery = generateIndexQuery(query);
        invokeBeforeQueryExecuted(indexQuery);
        return indexQuery;
    }

    /**
     * Gets the fields for projection
     * @return list of projected fields
     */
    @Override
    public List<String> getProjectionFields() {
        return fieldsToFetchToken != null && fieldsToFetchToken.projections != null ? Arrays.asList(fieldsToFetchToken.projections) : Collections.emptyList();
    }

    /**
     * Order the search results randomly
     */
    @Override
    public void _randomOrdering() {
        assertNoRawQuery();

        _noCaching();
        orderByTokens.add(OrderByToken.random);
    }

    /**
     * Order the search results randomly using the specified seed
     * this is useful if you want to have repeatable random queries
     * @param seed Seed to use
     */
    @Override
    public void _randomOrdering(String seed) {
        assertNoRawQuery();

        if (StringUtils.isBlank(seed)) {
            _randomOrdering();
            return;
        }

        _noCaching();
        orderByTokens.add(OrderByToken.createRandom(seed));
    }

    //TBD 4.1 public void _customSortUsing(String typeName)
    //TBD 4.1 public void _customSortUsing(String typeName, boolean descending)


    protected void _projection(ProjectionBehavior projectionBehavior) {
        this.projectionBehavior = projectionBehavior;
    }

    @SuppressWarnings("unused")
    protected void addGroupByAlias(String fieldName, String projectedName) {
        _aliasToGroupByFieldName.put(projectedName, fieldName);
    }

    private void assertNoRawQuery() {
        if (queryRaw != null) {
            throw new IllegalStateException("RawQuery was called, cannot modify this query by calling on operations that would modify the query (such as Where, Select, OrderBy, GroupBy, etc)");
        }
    }

    public void _graphQuery(String query) {
        graphRawQuery = new GraphQueryToken(query);
    }

    public void _addParameter(String name, Object value) {
        name = StringUtils.stripStart(name, "$");
        if (queryParameters.containsKey(name)) {
            throw new IllegalStateException("The parameter " + name + " was already added");
        }

        queryParameters.put(name, value);
    }

    @Override
    public void _groupBy(String fieldName, String... fieldNames) {
        GroupBy[] mapping = Arrays.stream(fieldNames)
                .map(GroupBy::field)
                .toArray(GroupBy[]::new);

        _groupBy(GroupBy.field(fieldName), mapping);
    }

    @Override
    public void _groupBy(GroupBy field, GroupBy... fields) {
        if (!fromToken.isDynamic()) {
            throw new IllegalStateException("groupBy only works with dynamic queries");
        }

        assertNoRawQuery();
        isGroupBy = true;

        String fieldName = ensureValidFieldName(field.getField(), false);

        groupByTokens.add(GroupByToken.create(fieldName, field.getMethod()));

        if (fields == null || fields.length <= 0) {
            return;
        }

        for (GroupBy item : fields) {
            fieldName = ensureValidFieldName(item.getField(), false);
            groupByTokens.add(GroupByToken.create(fieldName, item.getMethod()));
        }
    }

    @Override
    public void _groupByKey(String fieldName) {
        _groupByKey(fieldName, null);
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    @Override
    public void _groupByKey(String fieldName, String projectedName) {
        assertNoRawQuery();
        isGroupBy = true;

        if (projectedName != null && _aliasToGroupByFieldName.containsKey(projectedName)) {
            String aliasedFieldName = _aliasToGroupByFieldName.get(projectedName);
            if (fieldName == null || fieldName.equalsIgnoreCase(projectedName)) {
                fieldName = aliasedFieldName;
            }
        } else if (fieldName != null && _aliasToGroupByFieldName.containsValue(fieldName)) {
            String aliasedFieldName = _aliasToGroupByFieldName.get(fieldName);
            fieldName = aliasedFieldName;
        }

        selectTokens.add(GroupByKeyToken.create(fieldName, projectedName));
    }

    @Override
    public void _groupBySum(String fieldName) {
        _groupBySum(fieldName, null);
    }

    @Override
    public void _groupBySum(String fieldName, String projectedName) {
        assertNoRawQuery();
        isGroupBy = true;

        fieldName = ensureValidFieldName(fieldName, false);
        selectTokens.add(GroupBySumToken.create(fieldName, projectedName));
    }

    @Override
    public void _groupByCount() {
        _groupByCount(null);
    }

    @Override
    public void _groupByCount(String projectedName) {
        assertNoRawQuery();
        isGroupBy = true;

        selectTokens.add(GroupByCountToken.create(projectedName));
    }

    @Override
    public void _whereTrue() {
        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);
        negateIfNeeded(tokens, null);

        tokens.add(TrueToken.INSTANCE);
    }


    public MoreLikeThisScope _moreLikeThis() {
        appendOperatorIfNeeded(whereTokens);

        MoreLikeThisToken token = new MoreLikeThisToken();
        whereTokens.add(token);

        _isInMoreLikeThis = true;
        return new MoreLikeThisScope(token, this::addQueryParameter, () -> _isInMoreLikeThis = false);
    }

    /**
     * Includes the specified path in the query, loading the document specified in that path
     * @param path Path to include
     */
    @Override
    public void _include(String path) {
        documentIncludes.add(path);
    }

    //TBD expr public void Include(Expression<Func<T, object>> path)

    public void _include(IncludeBuilderBase includes) {
        if (includes == null) {
            return;
        }

        if (includes.documentsToInclude != null) {
            documentIncludes.addAll(includes.documentsToInclude);
        }

        _includeCounters(includes.alias, includes.countersToIncludeBySourcePath);
        if (includes.timeSeriesToIncludeBySourceAlias != null) {
            _includeTimeSeries(includes.alias, includes.timeSeriesToIncludeBySourceAlias);
        }

        if (includes.revisionsToIncludeByDateTime != null) {
            _includeRevisions(includes.revisionsToIncludeByDateTime);
        }

        if (includes.revisionsToIncludeByChangeVector != null) {
            _includeRevisions(includes.revisionsToIncludeByChangeVector);
        }

        if (includes.compareExchangeValuesToInclude != null) {
            compareExchangeValueIncludesTokens = new ArrayList<>();

            for (String compareExchangeValue : includes.compareExchangeValuesToInclude) {
                compareExchangeValueIncludesTokens.add(CompareExchangeValueIncludesToken.create(compareExchangeValue));
            }
        }
    }

    @Override
    public void _take(int count) {
        pageSize = count;
    }

    @Override
    public void _skip(int count) {
        start = count;
    }

    /**
     * Filter the results from the index using the specified where clause.
     * @param fieldName Field name
     * @param whereClause Where clause
     * @param exact Use exact matcher
     */
    @SuppressWarnings("ConstantConditions")
    public void _whereLucene(String fieldName, String whereClause, boolean exact) {
        fieldName = ensureValidFieldName(fieldName, false);

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);
        negateIfNeeded(tokens, fieldName);

        WhereToken.WhereOptions options = exact ? new WhereToken.WhereOptions(exact) : null;
        WhereToken whereToken = WhereToken.create(WhereOperator.LUCENE, fieldName, addQueryParameter(whereClause), options);
        tokens.add(whereToken);
    }

    /**
     * Simplified method for opening a new clause within the query
     */
    @Override
    public void _openSubclause() {
        _currentClauseDepth++;

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);
        negateIfNeeded(tokens, null);

        tokens.add(OpenSubclauseToken.create());
    }

    /**
     * Simplified method for closing a clause within the query
     */
    @Override
    public void _closeSubclause() {
        _currentClauseDepth--;

        List<QueryToken> tokens = getCurrentWhereTokens();
        tokens.add(CloseSubclauseToken.create());
    }

    @Override
    public void _whereEquals(String fieldName, Object value) {
        _whereEquals(fieldName, value, false);
    }

    @Override
    public void _whereEquals(String fieldName, Object value, boolean exact) {
        WhereParams params = new WhereParams();
        params.setFieldName(fieldName);
        params.setValue(value);
        params.setExact(exact);
        _whereEquals(params);
    }

    @Override
    public void _whereEquals(String fieldName, MethodCall method) {
        _whereEquals(fieldName, method, false);
    }

    @Override
    public void _whereEquals(String fieldName, MethodCall method, boolean exact) {
        _whereEquals(fieldName, (Object) method, exact);
    }

    public void _whereEquals(WhereParams whereParams) {
        if (negate) {
            negate = false;
            _whereNotEquals(whereParams);
            return;
        }

        whereParams.setFieldName(ensureValidFieldName(whereParams.getFieldName(), whereParams.isNestedPath()));

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);

        if (ifValueIsMethod(WhereOperator.EQUALS, whereParams, tokens)) {
            return;
        }

        Object transformToEqualValue = transformValue(whereParams);
        String addQueryParameter = addQueryParameter(transformToEqualValue);
        WhereToken whereToken = WhereToken.create(WhereOperator.EQUALS, whereParams.getFieldName(), addQueryParameter, new WhereToken.WhereOptions(whereParams.isExact()));
        tokens.add(whereToken);
    }

    private boolean ifValueIsMethod(WhereOperator op, WhereParams whereParams, List<QueryToken> tokens) {
        if (whereParams.getValue() instanceof MethodCall) {
            MethodCall mc = (MethodCall) whereParams.getValue();

            String[] args = new String[mc.args.length];
            for (int i = 0; i < mc.args.length; i++) {
                args[i] = addQueryParameter(mc.args[i]);
            }

            WhereToken token;
            Class<? extends MethodCall> type = mc.getClass();
            if (CmpXchg.class.equals(type)) {
                token = WhereToken.create(op, whereParams.getFieldName(), null, new WhereToken.WhereOptions(WhereToken.MethodsType.CMP_X_CHG, args, mc.accessPath, whereParams.isExact()));
            } else {
                throw new IllegalArgumentException("Unknown method " + type);
            }

            tokens.add(token);
            return true;
        }

        return false;
    }

    public void _whereNotEquals(String fieldName, Object value) {
        _whereNotEquals(fieldName, value, false);
    }

    public void _whereNotEquals(String fieldName, Object value, boolean exact) {
        WhereParams params = new WhereParams();
        params.setFieldName(fieldName);
        params.setValue(value);
        params.setExact(exact);

        _whereNotEquals(params);
    }

    @Override
    public void _whereNotEquals(String fieldName, MethodCall method) {
        _whereNotEquals(fieldName, (Object) method);
    }

    @Override
    public void _whereNotEquals(String fieldName, MethodCall method, boolean exact) {
        _whereNotEquals(fieldName, (Object) method, exact);
    }

    public void _whereNotEquals(WhereParams whereParams) {
        if (negate) {
            negate = false;
            _whereEquals(whereParams);
            return;
        }

        Object transformToEqualValue = transformValue(whereParams);

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);

        whereParams.setFieldName(ensureValidFieldName(whereParams.getFieldName(), whereParams.isNestedPath()));

        if (ifValueIsMethod(WhereOperator.NOT_EQUALS, whereParams, tokens)) {
            return;
        }

        WhereToken whereToken = WhereToken.create(WhereOperator.NOT_EQUALS, whereParams.getFieldName(), addQueryParameter(transformToEqualValue), new WhereToken.WhereOptions(whereParams.isExact()));
        tokens.add(whereToken);
    }

    @Override
    public void _negateNext() {
        negate = !negate;
    }

    /**
     * Check that the field has one of the specified value
     * @param fieldName Field name to use
     * @param values Values to find
     */
    @Override
    public void _whereIn(String fieldName, Collection<?> values) {
        _whereIn(fieldName, values, false);
    }

    /**
     * Check that the field has one of the specified value
     * @param fieldName Field name to use
     * @param values Values to find
     * @param exact Use exact matcher
     */
    @Override
    public void _whereIn(String fieldName, Collection<?> values, boolean exact) {
        assertMethodIsCurrentlySupported("whereIn");

        fieldName = ensureValidFieldName(fieldName, false);

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);
        negateIfNeeded(tokens, fieldName);

        WhereToken whereToken = WhereToken.create(WhereOperator.IN, fieldName, addQueryParameter(transformCollection(fieldName, unpackCollection(values))));
        tokens.add(whereToken);
    }

    public void _whereStartsWith(String fieldName, Object value) {
        _whereStartsWith(fieldName, value, false);
    }

    public void _whereStartsWith(String fieldName, Object value, boolean exact) {
        assertMethodIsCurrentlySupported("whereStartsWith");

        WhereParams whereParams = new WhereParams();
        whereParams.setFieldName(fieldName);
        whereParams.setValue(value);
        whereParams.setAllowWildcards(true);

        Object transformToEqualValue = transformValue(whereParams);

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);

        whereParams.setFieldName(ensureValidFieldName(whereParams.getFieldName(), whereParams.isNestedPath()));
        negateIfNeeded(tokens, whereParams.getFieldName());

        WhereToken whereToken = WhereToken.create(WhereOperator.STARTS_WITH, whereParams.getFieldName(), addQueryParameter(transformToEqualValue), new WhereToken.WhereOptions(exact));
        tokens.add(whereToken);
    }

    public void _whereEndsWith(String fieldName, Object value) {
        _whereEndsWith(fieldName, value, false);
    }

    /**
     * Matches fields which ends with the specified value.
     * @param fieldName Field name to use
     * @param value Values to find
     */
    public void _whereEndsWith(String fieldName, Object value, boolean exact) {
        assertMethodIsCurrentlySupported("whereEndsWith");

        WhereParams whereParams = new WhereParams();
        whereParams.setFieldName(fieldName);
        whereParams.setValue(value);
        whereParams.setAllowWildcards(true);

        Object transformToEqualValue = transformValue(whereParams);

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);

        whereParams.setFieldName(ensureValidFieldName(whereParams.getFieldName(), whereParams.isNestedPath()));
        negateIfNeeded(tokens, whereParams.getFieldName());

        WhereToken whereToken = WhereToken.create(WhereOperator.ENDS_WITH, whereParams.getFieldName(), addQueryParameter(transformToEqualValue), new WhereToken.WhereOptions(exact));
        tokens.add(whereToken);
    }

    @Override
    public void _whereBetween(String fieldName, Object start, Object end) {
        _whereBetween(fieldName, start, end, false);
    }

    /**
     * Matches fields where the value is between the specified start and end, inclusive
     * @param fieldName Field name to use
     * @param start Range start
     * @param end Range end
     * @param exact Use exact matcher
     */
    @Override
    public void _whereBetween(String fieldName, Object start, Object end, boolean exact) {
        assertMethodIsCurrentlySupported("whereBetween");
        fieldName = ensureValidFieldName(fieldName, false);

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);
        negateIfNeeded(tokens, fieldName);

        WhereParams startParams = new WhereParams();
        startParams.setValue(start);
        startParams.setFieldName(fieldName);

        WhereParams endParams = new WhereParams();
        endParams.setValue(end);
        endParams.setFieldName(fieldName);

        String fromParameterName = addQueryParameter(start == null ? "*" : transformValue(startParams, true));
        String toParameterName = addQueryParameter(end == null ? "NULL" : transformValue(endParams, true));

        WhereToken whereToken = WhereToken.create(WhereOperator.BETWEEN, fieldName, null, new WhereToken.WhereOptions(exact, fromParameterName, toParameterName));
        tokens.add(whereToken);
    }

    public void _whereGreaterThan(String fieldName, Object value) {
        _whereGreaterThan(fieldName, value, false);
    }

    /**
     * Matches fields where the value is greater than the specified value
     * @param fieldName Field name to use
     * @param value Value to compare
     * @param exact Use exact matcher
     */
    public void _whereGreaterThan(String fieldName, Object value, boolean exact) {
        fieldName = ensureValidFieldName(fieldName, false);

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);
        negateIfNeeded(tokens, fieldName);
        WhereParams whereParams = new WhereParams();
        whereParams.setValue(value);
        whereParams.setFieldName(fieldName);

        String parameter = addQueryParameter(value == null ? "*" : transformValue(whereParams, true));
        WhereToken whereToken = WhereToken.create(WhereOperator.GREATER_THAN, fieldName, parameter, new WhereToken.WhereOptions(exact));
        tokens.add(whereToken);
    }

    public void _whereGreaterThanOrEqual(String fieldName, Object value) {
        _whereGreaterThanOrEqual(fieldName, value, false);
    }

    /**
     * Matches fields where the value is greater than or equal to the specified value
     * @param fieldName Field name to use
     * @param value Value to compare
     * @param exact Use exact matcher
     */
    public void _whereGreaterThanOrEqual(String fieldName, Object value, boolean exact) {
        fieldName = ensureValidFieldName(fieldName, false);

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);
        negateIfNeeded(tokens, fieldName);
        WhereParams whereParams = new WhereParams();
        whereParams.setValue(value);
        whereParams.setFieldName(fieldName);

        String parameter = addQueryParameter(value == null ? "*" : transformValue(whereParams, true));
        WhereToken whereToken = WhereToken.create(WhereOperator.GREATER_THAN_OR_EQUAL, fieldName, parameter, new WhereToken.WhereOptions(exact));
        tokens.add(whereToken);
    }

    public void _whereLessThan(String fieldName, Object value) {
        _whereLessThan(fieldName, value, false);
    }

    public void _whereLessThan(String fieldName, Object value, boolean exact) {
        fieldName = ensureValidFieldName(fieldName, false);

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);
        negateIfNeeded(tokens, fieldName);

        WhereParams whereParams = new WhereParams();
        whereParams.setValue(value);
        whereParams.setFieldName(fieldName);

        String parameter = addQueryParameter(value == null ? "NULL" : transformValue(whereParams, true));
        WhereToken whereToken = WhereToken.create(WhereOperator.LESS_THAN, fieldName, parameter, new WhereToken.WhereOptions(exact));
        tokens.add(whereToken);
    }

    public void _whereLessThanOrEqual(String fieldName, Object value) {
        _whereLessThanOrEqual(fieldName, value, false);
    }

    public void _whereLessThanOrEqual(String fieldName, Object value, boolean exact) {
        fieldName = ensureValidFieldName(fieldName, false);

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);
        negateIfNeeded(tokens, fieldName);

        WhereParams whereParams = new WhereParams();
        whereParams.setValue(value);
        whereParams.setFieldName(fieldName);

        String parameter = addQueryParameter(value == null ? "NULL" : transformValue(whereParams, true));
        WhereToken whereToken = WhereToken.create(WhereOperator.LESS_THAN_OR_EQUAL, fieldName, parameter, new WhereToken.WhereOptions(exact));
        tokens.add(whereToken);
    }

    /**
     * Matches fields where Regex.IsMatch(filedName, pattern)
     * @param fieldName Field name to use
     * @param pattern Regexp pattern
     */
    @Override
    public void _whereRegex(String fieldName, String pattern) {
        assertMethodIsCurrentlySupported("whereRegex");
        fieldName = ensureValidFieldName(fieldName, false);

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);
        negateIfNeeded(tokens, fieldName);

        WhereParams whereParams = new WhereParams();
        whereParams.setValue(pattern);
        whereParams.setFieldName(fieldName);

        String parameter = addQueryParameter(transformValue(whereParams));

        WhereToken whereToken = WhereToken.create(WhereOperator.REGEX, fieldName, parameter);
        tokens.add(whereToken);
    }

    public void _andAlso() {
       _andAlso(false);
    }

    public void _andAlso(boolean wrapPreviousQueryClauses) {
        List<QueryToken> tokens = getCurrentWhereTokens();
        if (tokens.isEmpty()) {
            return;
        }

        if (tokens.get(tokens.size() - 1) instanceof QueryOperatorToken) {
            throw new IllegalStateException("Cannot add AND, previous token was already an operator token.");
        }

        if (wrapPreviousQueryClauses) {
            tokens.add(0, OpenSubclauseToken.create());
            tokens.add(CloseSubclauseToken.create());
            tokens.add(QueryOperatorToken.AND);
        } else {
            tokens.add(QueryOperatorToken.AND);
        }
    }

    /**
     * Add an OR to the query
     */
    public void _orElse() {
        List<QueryToken> tokens = getCurrentWhereTokens();
        if (tokens.isEmpty()) {
            return;
        }

        if (tokens.get(tokens.size() - 1) instanceof QueryOperatorToken) {
            throw new IllegalStateException("Cannot add OR, previous token was already an operator token.");
        }

        tokens.add(QueryOperatorToken.OR);
    }

    protected CleanCloseable setFilterMode(boolean on) {
        return new FilterModeScope(filterModeStack, on);
    }

    private static class FilterModeScope implements CleanCloseable {
        private final Stack<Boolean> _modeStack;

        public FilterModeScope(Stack<Boolean> modeStack, boolean on) {
            _modeStack = modeStack;
            _modeStack.add(on);
        }

        public void close() {
            _modeStack.pop();
        }
    }

    /**
     * Specifies a boost weight to the last where clause.
     * The higher the boost factor, the more relevant the term will be.
     * <p>
     * boosting factor where 1.0 is default, less than 1.0 is lower weight, greater than 1.0 is higher weight
     * <p>
     * http://lucene.apache.org/java/2_4_0/queryparsersyntax.html#Boosting%20a%20Term
     *
     * @param boost Boost value
     */
    @Override
    public void _boost(double boost) {
        assertMethodIsCurrentlySupported("boost");

        if (boost == 1.0) {
            return;
        }

        if (boost < 0.0) {
            throw new IllegalArgumentException("Boost factor must be a non-negative number");
        }

        List<QueryToken> tokens = getCurrentWhereTokens();

        QueryToken last = tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);

        if (last instanceof WhereToken) {
            WhereToken whereToken = (WhereToken) last;
            whereToken.getOptions().setBoost(boost);
        } else if (last instanceof CloseSubclauseToken) {
            CloseSubclauseToken close = (CloseSubclauseToken) last;

            String parameter = addQueryParameter(boost);

            int index = tokens.indexOf(last);

            while (last != null && index > 0) {
                index--;
                last = tokens.get(index); // find the previous option

                if (last instanceof OpenSubclauseToken) {
                    OpenSubclauseToken open = (OpenSubclauseToken) last;

                    open.setBoostParameterName(parameter);
                    close.setBoostParameterName(parameter);
                    return;
                }
            }
        } else {
            throw new IllegalStateException("Cannot apply boost");
        }
    }

    /**
     * Specifies a fuzziness factor to the single word term in the last where clause
     * <p>
     * 0.0 to 1.0 where 1.0 means closer match
     * <p>
     * http://lucene.apache.org/java/2_4_0/queryparsersyntax.html#Fuzzy%20Searches
     * @param fuzzy Fuzzy value
     */
    @Override
    public void _fuzzy(double fuzzy) {
        assertMethodIsCurrentlySupported("fuzzy");

        List<QueryToken> tokens = getCurrentWhereTokens();
        if (tokens.isEmpty()) {
            throw new IllegalStateException("Fuzzy can only be used right after where clause");
        }

        QueryToken whereToken = tokens.get(tokens.size() - 1);
        if (!(whereToken instanceof WhereToken)) {
            throw new IllegalStateException("Fuzzy can only be used right after where clause");
        }

        if (((WhereToken) whereToken).getWhereOperator() != WhereOperator.EQUALS) {
            throw new IllegalStateException("Fuzzy can only be used right after where clause with equals operator");
        }

        if (fuzzy < 0.0 || fuzzy > 1.0) {
            throw new IllegalArgumentException("Fuzzy distance must be between 0.0 and 1.0");
        }

        ((WhereToken) whereToken).getOptions().setFuzzy(fuzzy);
    }

    /**
     * Specifies a proximity distance for the phrase in the last search clause
     * <p>
     * http://lucene.apache.org/java/2_4_0/queryparsersyntax.html#Proximity%20Searches
     * @param proximity Proximity value
     */
    @Override
    public void _proximity(int proximity) {
        assertMethodIsCurrentlySupported("proximity");

        List<QueryToken> tokens = getCurrentWhereTokens();
        if (tokens.isEmpty()) {
            throw new IllegalStateException("Proximity can only be used right after search clause");
        }

        QueryToken whereToken = tokens.get(tokens.size() - 1);
        if (!(whereToken instanceof WhereToken)) {
            throw new IllegalStateException("Proximity can only be used right after search clause");
        }

        if (((WhereToken) whereToken).getWhereOperator() != WhereOperator.SEARCH) {
            throw new IllegalStateException("Proximity can only be used right after search clause");
        }

        if (proximity < 1) {
            throw new IllegalArgumentException("Proximity distance must be a positive number");
        }

        ((WhereToken) whereToken).getOptions().setProximity(proximity);
    }

    public void _orderBy(String field, String sorterName) {
        if (StringUtils.isBlank(sorterName)) {
            throw new IllegalArgumentException("SorterName cannot be null or whitespace.");
        }

        assertNoRawQuery();
        String f = ensureValidFieldName(field, false);
        orderByTokens.add(OrderByToken.createAscending(f, sorterName));
    }

    /**
     * Order the results by the specified fields
     * The fields are the names of the fields to sort, defaulting to sorting by ascending.
     * You can prefix a field name with '-' to indicate sorting by descending or '+' to sort by ascending
     * @param field field to use in order
     */
    public void _orderBy(String field) {
        _orderBy(field, OrderingType.STRING);
    }

    /**
     * Order the results by the specified fields
     * The fields are the names of the fields to sort, defaulting to sorting by ascending.
     * You can prefix a field name with '-' to indicate sorting by descending or '+' to sort by ascending
     * @param field field to use in order
     * @param ordering Ordering type
     */
    public void _orderBy(String field, OrderingType ordering) {
        assertNoRawQuery();
        String f = ensureValidFieldName(field, false);
        orderByTokens.add(OrderByToken.createAscending(f, ordering));
    }

    public void _orderByDescending(String field, String sorterName) {
        if (StringUtils.isBlank(sorterName)) {
            throw new IllegalArgumentException("SorterName cannot be null or whitespace.");
        }

        assertNoRawQuery();
        String f = ensureValidFieldName(field, false);
        orderByTokens.add(OrderByToken.createDescending(f, sorterName));
    }

    /**
     * Order the results by the specified fields
     * The fields are the names of the fields to sort, defaulting to sorting by descending.
     * You can prefix a field name with '-' to indicate sorting by descending or '+' to sort by ascending
     * @param field Field to use
     */
    public void _orderByDescending(String field) {
        _orderByDescending(field, OrderingType.STRING);
    }

    /**
     * Order the results by the specified fields
     * The fields are the names of the fields to sort, defaulting to sorting by descending.
     * You can prefix a field name with '-' to indicate sorting by descending or '+' to sort by ascending
     * @param field Field to use
     * @param ordering Ordering type
     */
    public void _orderByDescending(String field, OrderingType ordering) {
        assertNoRawQuery();
        String f = ensureValidFieldName(field, false);
        orderByTokens.add(OrderByToken.createDescending(f, ordering));
    }

    public void _orderByScore() {
        assertNoRawQuery();

        orderByTokens.add(OrderByToken.scoreAscending);
    }

    public void _orderByScoreDescending() {
        assertNoRawQuery();
        orderByTokens.add(OrderByToken.scoreDescending);
    }

    /**
     * Provide statistics about the query, such as total count of matching records
     * @param stats Output parameter for query statistics
     */
    public void _statistics(Reference<QueryStatistics> stats) {
        stats.value = queryStats;
    }

    /**
     * Called externally to raise the after query executed callback
     * @param result Query result
     */
    public void invokeAfterQueryExecuted(QueryResult result) {
        EventHelper.invoke(afterQueryExecutedCallback, result);
    }

    public void invokeBeforeQueryExecuted(IndexQuery query) {
        EventHelper.invoke(beforeQueryExecutedCallback, query);
    }

    public void invokeAfterStreamExecuted(ObjectNode result) {
        EventHelper.invoke(afterStreamExecutedCallback, result);
    }

    /**
     * Generates the index query.
     * @param query Query
     * @return Index query
     */
    @SuppressWarnings("deprecation")
    protected IndexQuery generateIndexQuery(String query) {
        IndexQuery indexQuery = new IndexQuery();
        indexQuery.setQuery(query);
        indexQuery.setStart(start);
        indexQuery.setWaitForNonStaleResults(theWaitForNonStaleResults);
        indexQuery.setWaitForNonStaleResultsTimeout(timeout);
        indexQuery.setQueryParameters(queryParameters);
        indexQuery.setDisableCaching(disableCaching);
        indexQuery.setProjectionBehavior(projectionBehavior);

        if (pageSize != null) {
            indexQuery.setPageSize(pageSize);
        }
        return indexQuery;
    }

    /**
     * Perform a search for documents which fields that match the searchTerms.
     * If there is more than a single term, each of them will be checked independently.
     * @param fieldName Field name
     * @param searchTerms Search terms
     */
    @Override
    public void _search(String fieldName, String searchTerms) {
        _search(fieldName, searchTerms, SearchOperator.OR);
    }

    /**
     * Perform a search for documents which fields that match the searchTerms.
     * If there is more than a single term, each of them will be checked independently.
     * @param fieldName Field name
     * @param searchTerms Search terms
     * @param operator Search operator
     */
    @Override
    public void _search(String fieldName, String searchTerms, SearchOperator operator) {
        assertMethodIsCurrentlySupported("search");

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);

        fieldName = ensureValidFieldName(fieldName, false);
        negateIfNeeded(tokens, fieldName);

        WhereToken whereToken = WhereToken.create(WhereOperator.SEARCH, fieldName, addQueryParameter(searchTerms), new WhereToken.WhereOptions(operator));
        tokens.add(whereToken);
    }

    private String toString(boolean compatibilityMode) {
        if (queryRaw != null) {
            return queryRaw;
        }

        if (_currentClauseDepth != 0) {
            throw new IllegalStateException("A clause was not closed correctly within this query, current clause depth = " + _currentClauseDepth);
        }

        StringBuilder queryText = new StringBuilder();

        buildDeclare(queryText);
        if (graphRawQuery != null) {
            buildWith(queryText);
            buildGraphQuery(queryText);
        } else {
            buildFrom(queryText);
        }
        buildGroupBy(queryText);
        buildWhere(queryText);
        buildOrderBy(queryText);

        buildLoad(queryText);
        buildFilter(queryText);
        buildSelect(queryText);
        buildInclude(queryText);

        if (!compatibilityMode) {
            buildPagination(queryText);
        }

        return queryText.toString();
    }

    private void buildGraphQuery(StringBuilder queryText) {
        graphRawQuery.writeTo(queryText);
    }

    private void buildWith(StringBuilder queryText) {
        for (QueryToken with : withTokens) {
            with.writeTo(queryText);
            queryText.append(System.lineSeparator());
        }
    }

    @Override
    public String toString() {
        return toString(false);
    }

    private void buildPagination(StringBuilder queryText) {
        if (start > 0 || pageSize != null) {
            queryText
                    .append(" limit $")
                    .append(addQueryParameter(start))
                    .append(", $")
                    .append(addQueryParameter(pageSize));
        }
        if (!filterTokens.isEmpty() && filterLimit != null) {
            queryText
                    .append(" filter_limit $")
                    .append(addQueryParameter(filterLimit));
        }
    }

    @SuppressWarnings("UnusedAssignment")
    private void buildInclude(StringBuilder queryText) {
        if (documentIncludes.isEmpty() &&
                highlightingTokens.isEmpty() &&
                explanationToken == null &&
                queryTimings == null &&
                counterIncludesTokens == null &&
                revisionsIncludesTokens == null &&
                timeSeriesIncludesTokens == null &&
                compareExchangeValueIncludesTokens == null) {
            return;
        }

        queryText.append(" include ");
        Reference<Boolean> firstRef = new Reference<>(true);
        for (String include : documentIncludes) {
            if (!firstRef.value) {
                queryText.append(",");
            }
            firstRef.value = false;

            Reference<String> escapedIncludeRef = new Reference<>();

            if (IncludesUtil.requiresQuotes(include, escapedIncludeRef)) {
                queryText
                        .append("'")
                        .append(escapedIncludeRef.value)
                        .append("'");
            } else {
                queryText.append(include);
            }
        }

        writeIncludeTokens(counterIncludesTokens, firstRef, queryText);
        writeIncludeTokens(timeSeriesIncludesTokens, firstRef, queryText);
        writeIncludeTokens(revisionsIncludesTokens, firstRef, queryText);
        writeIncludeTokens(compareExchangeValueIncludesTokens, firstRef, queryText);
        writeIncludeTokens(highlightingTokens, firstRef, queryText);

        if (explanationToken != null) {
            if (!firstRef.value) {
                queryText.append(",");
            }

            firstRef.value = false;
            explanationToken.writeTo(queryText);
        }

        if (queryTimings != null) {
            if (!firstRef.value) {
                queryText.append(",");
            }
            firstRef.value = false;


            TimingsToken.INSTANCE.writeTo(queryText);
        }
    }

    <TToken extends QueryToken> void writeIncludeTokens(Collection<TToken> tokens, Reference<Boolean> firstRef, StringBuilder queryText) {
        if (tokens == null) {
            return;
        }

        for (TToken token : tokens) {
            if (!firstRef.value) {
                queryText.append(",");
            }
            firstRef.value = false;

            token.writeTo(queryText);
        }
    }

    @Override
    public void _intersect() {
        List<QueryToken> tokens = getCurrentWhereTokens();
        if (tokens.size() > 0) {
            QueryToken last = tokens.get(tokens.size() - 1);
            if (last instanceof WhereToken || last instanceof CloseSubclauseToken) {
                isIntersect = true;

                tokens.add(IntersectMarkerToken.INSTANCE);
                return;
            }
        }

        throw new IllegalStateException("Cannot add INTERSECT at this point.");
    }

    public void _whereExists(String fieldName) {
        fieldName = ensureValidFieldName(fieldName, false);

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);
        negateIfNeeded(tokens, null);

        tokens.add(WhereToken.create(WhereOperator.EXISTS, fieldName, null));
    }

    @Override
    public void _containsAny(String fieldName, Collection<?> values) {
        assertMethodIsCurrentlySupported("containsAny");

        fieldName = ensureValidFieldName(fieldName, false);

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);
        negateIfNeeded(tokens, fieldName);

        Collection<?> array = transformCollection(fieldName, unpackCollection(values));
        WhereToken whereToken = WhereToken.create(WhereOperator.IN, fieldName, addQueryParameter(array), new WhereToken.WhereOptions(false));
        tokens.add(whereToken);
    }

    @Override
    public void _containsAll(String fieldName, Collection<?> values) {
        assertMethodIsCurrentlySupported("containsAll");
        fieldName = ensureValidFieldName(fieldName, false);

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);
        negateIfNeeded(tokens, fieldName);

        Collection<?> array = transformCollection(fieldName, unpackCollection(values));

        if (array.isEmpty()) {
            tokens.add(TrueToken.INSTANCE);
            return;
        }

        WhereToken whereToken = WhereToken.create(WhereOperator.ALL_IN, fieldName, addQueryParameter(array));
        tokens.add(whereToken);
    }

    @Override
    public void _addRootType(Class clazz) {
        rootTypes.add(clazz);
    }

    //TBD expr public string GetMemberQueryPathForOrderBy(Expression expression)
    //TBD expr public string GetMemberQueryPath(Expression expression)


    @Override
    public void _distinct() {
        if (isDistinct()) {
            throw new IllegalStateException("The is already a distinct query");
        }

        if (selectTokens.isEmpty()) {
            selectTokens.add(DistinctToken.INSTANCE);
        } else {
            selectTokens.add(0, DistinctToken.INSTANCE);
        }
    }

    private void updateStatsHighlightingsAndExplanations(QueryResult queryResult) {
        queryStats.updateQueryStats(queryResult);
        queryHighlightings.update(queryResult);
        if (explanations != null) {
            explanations.update(queryResult);
        }
        if (queryTimings != null) {
            queryTimings.update(queryResult);
        }
    }

    private void buildSelect(StringBuilder writer) {
        if (selectTokens.isEmpty()) {
            return;
        }

        writer.append(" select ");
        if (selectTokens.size() == 1 && selectTokens.get(0) instanceof DistinctToken) {
            selectTokens.get(0).writeTo(writer);
            writer.append(" *");

            return;
        }

        for (int i = 0; i < selectTokens.size(); i++) {
            QueryToken token = selectTokens.get(i);
            if (i > 0 && !(selectTokens.get(i - 1) instanceof DistinctToken)) {
                writer.append(",");
            }

            DocumentQueryHelper.addSpaceIfNeeded(i > 0 ? selectTokens.get(i - 1) : null, token, writer);

            token.writeTo(writer);
        }
    }

    private void buildFrom(StringBuilder writer) {
        fromToken.writeTo(writer);
    }

    private void buildDeclare(StringBuilder writer) {
        if (declareTokens == null) {
            return;
        }

        for (DeclareToken token : declareTokens) {
            token.writeTo(writer);
        }
    }

    private void buildLoad(StringBuilder writer) {
        if (loadTokens == null || loadTokens.isEmpty()) {
            return;
        }

        writer.append(" load ");

        for (int i = 0; i < loadTokens.size(); i++) {
            if (i != 0) {
                writer.append(", ");
            }

            loadTokens.get(i).writeTo(writer);
        }
    }

    private void buildWhere(StringBuilder writer) {
        if (whereTokens.isEmpty()) {
            return;
        }

        writer
                .append(" where ");

        if (isIntersect) {
            writer
                    .append("intersect(");
        }

        for (int i = 0; i < whereTokens.size(); i++) {
            DocumentQueryHelper.addSpaceIfNeeded(i > 0 ? whereTokens.get(i - 1) : null, whereTokens.get(i), writer);
            whereTokens.get(i).writeTo(writer);
        }

        if (isIntersect) {
            writer.append(") ");
        }
    }

    private void buildGroupBy(StringBuilder writer) {
        if (groupByTokens.isEmpty()) {
            return;
        }

        writer
                .append(" group by ");

        boolean isFirst = true;

        for (QueryToken token : groupByTokens) {
            if (!isFirst) {
                writer.append(", ");
            }

            token.writeTo(writer);
            isFirst = false;
        }
    }

    private void buildFilter(StringBuilder writer) {
        if (filterTokens.isEmpty()) {
            return;
        }

        writer
                .append(" filter ");

        for (int i = 0; i < filterTokens.size(); i++) {
            DocumentQueryHelper.addSpaceIfNeeded(i > 0 ? filterTokens.get(i - 1) : null, filterTokens.get(i), writer);
            filterTokens.get(i).writeTo(writer);
        }
    }

    private void buildOrderBy(StringBuilder writer) {
        if (orderByTokens.isEmpty()) {
            return;
        }

        writer
                .append(" order by ");

        boolean isFirst = true;

        for (QueryToken token : orderByTokens) {
            if (!isFirst) {
                writer.append(", ");
            }

            token.writeTo(writer);
            isFirst = false;
        }
    }

    private void appendOperatorIfNeeded(List<QueryToken> tokens) {
        assertNoRawQuery();

        if (tokens.isEmpty()) {
            return;
        }

        QueryToken lastToken = tokens.get(tokens.size() - 1);
        if (!(lastToken instanceof WhereToken) && !(lastToken instanceof CloseSubclauseToken)) {
            return;
        }

        WhereToken lastWhere = null;

        for (int i = tokens.size() - 1; i >= 0; i--) {
            if (tokens.get(i) instanceof WhereToken) {
                lastWhere = (WhereToken) tokens.get(i);
                break;
            }
        }

        QueryOperatorToken token = defaultOperator == QueryOperator.AND ? QueryOperatorToken.AND : QueryOperatorToken.OR;

        if (lastWhere != null && lastWhere.getOptions().getSearchOperator() != null) {
            token = QueryOperatorToken.OR; // default to OR operator after search if AND was not specified explicitly
        }

        tokens.add(token);
    }

    private Collection<?> transformCollection(String fieldName, Collection<?> values) {
        List<Object> result = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof Collection) {
                result.addAll(transformCollection(fieldName, (Collection<?>) value));
            } else {
                WhereParams nestedWhereParams = new WhereParams();
                nestedWhereParams.setAllowWildcards(true);
                nestedWhereParams.setFieldName(fieldName);
                nestedWhereParams.setValue(value);

                result.add(transformValue(nestedWhereParams));
            }
        }
        return result;
    }

    private void negateIfNeeded(List<QueryToken> tokens, String fieldName) {
        if (!negate) {
            return;
        }

        negate = false;

        if (tokens.isEmpty() || tokens.get(tokens.size() - 1) instanceof OpenSubclauseToken) {
            if (fieldName != null) {
                _whereExists(fieldName);
            } else {
                _whereTrue();
            }
            _andAlso();
        }

        tokens.add(NegateToken.INSTANCE);
    }

    private static Collection<?> unpackCollection(Collection<?> items) {
        List<Object> results = new ArrayList<>();

        for (Object item : items) {
            if (item instanceof Collection) {
                results.addAll(unpackCollection((Collection<?>) item));
            } else {
                results.add(item);
            }
        }

        return results;
    }

    private String ensureValidFieldName(String fieldName, boolean isNestedPath) {
        if (theSession == null || theSession.getConventions() == null || isNestedPath || isGroupBy) {
            return QueryFieldUtil.escapeIfNecessary(fieldName, isNestedPath);
        }

        for (Class rootType : rootTypes) {
            Field identityProperty = theSession.getConventions().getIdentityProperty(rootType);
            if (identityProperty != null && identityProperty.getName().equals(fieldName)) {
                return Constants.Documents.Indexing.Fields.DOCUMENT_ID_FIELD_NAME;
            }
        }

        return QueryFieldUtil.escapeIfNecessary(fieldName);
    }

    private Object transformValue(WhereParams whereParams) {
        return transformValue(whereParams, false);
    }

    private Object transformValue(WhereParams whereParams, boolean forRange) {
        if (whereParams.getValue() == null) {
            return null;
        }

        if ("".equals(whereParams.getValue())) {
            return "";
        }

        Reference<Object> objValueReference = new Reference<>();
        if (_conventions.tryConvertValueToObjectForQuery(whereParams.getFieldName(), whereParams.getValue(), forRange, objValueReference)) {
            return objValueReference.value;
        }

        Class<?> clazz = whereParams.getValue().getClass();
        if (Date.class.equals(clazz)) {
            return whereParams.getValue();
        }

        if (String.class.equals(clazz)) {
            return whereParams.getValue();
        }

        if (Integer.class.equals(clazz)) {
            return whereParams.getValue();
        }

        if (Long.class.equals(clazz)) {
            return whereParams.getValue();
        }

        if (Float.class.equals(clazz)) {
            return whereParams.getValue();
        }

        if (Double.class.equals(clazz)) {
            return whereParams.getValue();
        }

        if (Duration.class.equals(clazz)) {
            return ((Duration) whereParams.getValue()).toNanos() / 100;
        }

        if (Boolean.class.equals(clazz)) {
            return whereParams.getValue();
        }

        if (clazz.isEnum()) {
            return whereParams.getValue();
        }

        return whereParams.getValue();
    }

    private String addQueryParameter(Object value) {
        String parameterName = getParameterPrefix() + queryParameters.size();
        queryParameters.put(parameterName, value);
        return parameterName;
    }

    private void assertMethodIsCurrentlySupported(String methodName) {
        if (!isFilterActive()) {
            return;
        }

        throw new InvalidQueryException(methodName + " is currently unsupported for 'filter'. If you want to use "
                + methodName + " in where method you have to put it before 'filter'");
    }

    private List<QueryToken> getCurrentWhereTokens() {
        if (isFilterActive()) {
            return filterTokens;
        }

        if (!_isInMoreLikeThis) {
            return whereTokens;
        }

        if (whereTokens.isEmpty()) {
            throw new IllegalStateException("Cannot get MoreLikeThisToken because there are no where token specified.");
        }

        QueryToken lastToken = whereTokens.get(whereTokens.size() - 1);

        if (lastToken instanceof MoreLikeThisToken) {
            MoreLikeThisToken moreLikeThisToken = (MoreLikeThisToken) lastToken;
            return moreLikeThisToken.whereTokens;
        } else {
            throw new IllegalStateException("Last token is not MoreLikeThisToken");
        }
    }

    private List<QueryToken> getCurrentFilterTokens() {
        return filterTokens;
    }

    protected void updateFieldsToFetchToken(FieldsToFetchToken fieldsToFetch) {
        this.fieldsToFetchToken = fieldsToFetch;

        if (selectTokens.isEmpty()) {
            selectTokens.add(fieldsToFetch);
        } else {
            Optional<QueryToken> fetchToken = selectTokens.stream()
                    .filter(x -> x instanceof FieldsToFetchToken)
                    .findFirst();

            if (fetchToken.isPresent()) {
                int idx = selectTokens.indexOf(fetchToken.get());
                selectTokens.set(idx, fieldsToFetch);
            } else {
                selectTokens.add(fieldsToFetch);
            }
        }
    }

    public void addFromAliasToWhereTokens(String fromAlias) {
        if (StringUtils.isEmpty(fromAlias)) {
            throw new IllegalArgumentException("Alias cannot be null or empty");
        }

        List<QueryToken> tokens = getCurrentWhereTokens();

        for (QueryToken token : tokens) {
            if (token instanceof WhereToken) {
                ((WhereToken) token).addAlias(fromAlias);
            }
        }
    }

    public String addAliasToIncludesTokens(String fromAlias) {
        if (_includesAlias == null) {
            return fromAlias;
        }

        if (fromAlias == null) {
            fromAlias = _includesAlias;
            addFromAliasToWhereTokens(fromAlias);
        }

        if (counterIncludesTokens != null) {
            for (CounterIncludesToken counterIncludesToken : counterIncludesTokens) {
                counterIncludesToken.addAliasToPath(fromAlias);
            }
        }

        if (timeSeriesIncludesTokens != null) {
            for (TimeSeriesIncludesToken token : timeSeriesIncludesTokens) {
                token.addAliasToPath(fromAlias);
            }
        }

        return fromAlias;
    }

    @SuppressWarnings("unused")
    protected static <T> void getSourceAliasIfExists(Class<T> clazz, QueryData queryData, String[] fields, Reference<String> sourceAlias) {
        sourceAlias.value = null;

        if (fields.length != 1 || fields[0] == null) {
            return;
        }

        int indexOf = fields[0].indexOf(".");
        if (indexOf == -1) {
            return;
        }

        String possibleAlias = fields[0].substring(0, indexOf);
        if (queryData.getFromAlias() != null && queryData.getFromAlias().equals(possibleAlias)) {
            sourceAlias.value = possibleAlias;
            return;
        }

        if (queryData.getLoadTokens() == null || queryData.getLoadTokens().size() == 0) {
            return;
        }

        if (queryData.getLoadTokens().stream().noneMatch(x -> x.alias.equals(possibleAlias))) {
            return;
        }

        sourceAlias.value = possibleAlias;
    }

    protected QueryData createTimeSeriesQueryData(Consumer<ITimeSeriesQueryBuilder> timeSeriesQuery) {
        TimeSeriesQueryBuilder builder = new TimeSeriesQueryBuilder();
        timeSeriesQuery.accept(builder);

        String[] fields = new String[] { Constants.TimeSeries.SELECT_FIELD_NAME + "(" + builder.getQueryText() + ")" };
        String[] projections = new String[] { Constants.TimeSeries.QUERY_FUNCTION } ;
        return new QueryData(fields, projections);
    }

    public void _addFilterLimit(int filterLimit) {
        if (filterLimit <= 0) {
            throw new IllegalArgumentException("filter_limit need to be positive and bigger than 0.");
        }

        if (filterLimit != Integer.MAX_VALUE) {
            this.filterLimit = filterLimit;
        }
    }

    protected List<Consumer<IndexQuery>> beforeQueryExecutedCallback = new ArrayList<>();

    protected List<Consumer<QueryResult>> afterQueryExecutedCallback = new ArrayList<>();

    protected List<Consumer<ObjectNode>> afterStreamExecutedCallback = new ArrayList<>();

    protected QueryOperation queryOperation;

    public QueryOperation getQueryOperation() {
        return queryOperation;
    }

    public void _addBeforeQueryExecutedListener(Consumer<IndexQuery> action) {
        beforeQueryExecutedCallback.add(action);
    }

    public void _removeBeforeQueryExecutedListener(Consumer<IndexQuery> action) {
        beforeQueryExecutedCallback.remove(action);
    }

    public void _addAfterQueryExecutedListener(Consumer<QueryResult> action) {
        afterQueryExecutedCallback.add(action);
    }

    public void _removeAfterQueryExecutedListener(Consumer<QueryResult> action) {
        afterQueryExecutedCallback.remove(action);
    }

    public void _addAfterStreamExecutedListener(Consumer<ObjectNode> action) {
        afterStreamExecutedCallback.add(action);
    }

    public void _removeAfterStreamExecutedListener(Consumer<ObjectNode> action) {
        afterStreamExecutedCallback.remove(action);
    }

    public void _noTracking() {
        disableEntitiesTracking = true;
    }

    public void _noCaching() {
        disableCaching = true;
    }

    protected QueryTimings queryTimings;

    public void _includeTimings(Reference<QueryTimings> timingsReference) {
        if (queryTimings != null) {
            timingsReference.value = queryTimings;
            return;
        }

        queryTimings = timingsReference.value = new QueryTimings();
    }

    protected List<HighlightingToken> highlightingTokens = new ArrayList<>();

    protected QueryHighlightings queryHighlightings = new QueryHighlightings();

    public void _highlight(String fieldName, int fragmentLength, int fragmentCount, HighlightingOptions options, Reference<Highlightings> highlightingsReference) {
        highlightingsReference.value = queryHighlightings.add(fieldName);

        String optionsParameterName = options != null ? addQueryParameter(JsonExtensions.getDefaultMapper().valueToTree(options)) : null;
        highlightingTokens.add(HighlightingToken.create(fieldName, fragmentLength, fragmentCount, optionsParameterName));
    }

    protected void _withinRadiusOf(String fieldName, double radius, double latitude, double longitude, SpatialUnits radiusUnits, double distErrorPercent) {
        fieldName = ensureValidFieldName(fieldName, false);

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);
        negateIfNeeded(tokens, fieldName);

        WhereToken whereToken = WhereToken.create(WhereOperator.SPATIAL_WITHIN, fieldName, null, new WhereToken.WhereOptions(ShapeToken.circle(addQueryParameter(radius), addQueryParameter(latitude), addQueryParameter(longitude), radiusUnits), distErrorPercent));
        tokens.add(whereToken);
    }

    protected void _spatial(String fieldName, String shapeWkt, SpatialRelation relation, SpatialUnits units, double distErrorPercent) {
        fieldName = ensureValidFieldName(fieldName, false);

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);
        negateIfNeeded(tokens, fieldName);

        ShapeToken wktToken = ShapeToken.wkt(addQueryParameter(shapeWkt), units);

        WhereOperator whereOperator;
        switch (relation) {
            case WITHIN:
                whereOperator = WhereOperator.SPATIAL_WITHIN;
                break;
            case CONTAINS:
                whereOperator = WhereOperator.SPATIAL_CONTAINS;
                break;
            case DISJOINT:
                whereOperator = WhereOperator.SPATIAL_DISJOINT;
                break;
            case INTERSECTS:
                whereOperator = WhereOperator.SPATIAL_INTERSECTS;
                break;
            default:
                throw new IllegalArgumentException();
        }

        tokens.add(WhereToken.create(whereOperator, fieldName, null, new WhereToken.WhereOptions(wktToken, distErrorPercent)));
    }

    @Override
    public void _spatial(DynamicSpatialField dynamicField, SpatialCriteria criteria) {
        assertIsDynamicQuery(dynamicField, "spatial");

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);
        negateIfNeeded(tokens, null);

        tokens.add(criteria.toQueryToken(dynamicField.toField(this::ensureValidFieldName), this::addQueryParameter));
    }

    @Override
    public void _spatial(String fieldName, SpatialCriteria criteria) {
        fieldName = ensureValidFieldName(fieldName, false);

        List<QueryToken> tokens = getCurrentWhereTokens();
        appendOperatorIfNeeded(tokens);
        negateIfNeeded(tokens, fieldName);

        tokens.add(criteria.toQueryToken(fieldName, this::addQueryParameter));
    }

    @Override
    public void _orderByDistance(DynamicSpatialField field, double latitude, double longitude) {
        if (field == null) {
            throw new IllegalArgumentException("Field cannot be null");
        }
        assertIsDynamicQuery(field, "orderByDistance");

        _orderByDistance("'" + field.toField(this::ensureValidFieldName) + "'", latitude, longitude, field.getRoundFactor());
    }

    @Override
    public void _orderByDistance(String fieldName, double latitude, double longitude) {
        _orderByDistance(fieldName, latitude, longitude, 0);
    }

    @Override
    public void _orderByDistance(String fieldName, double latitude, double longitude, double roundFactor) {
        String roundFactorParameterName = roundFactor == 0 ? null : addQueryParameter(roundFactor);
        orderByTokens.add(OrderByToken.createDistanceAscending(fieldName, addQueryParameter(latitude), addQueryParameter(longitude), roundFactorParameterName));
    }

    @Override
    public void _orderByDistance(DynamicSpatialField field, String shapeWkt) {
        if (field == null) {
            throw new IllegalArgumentException("Field cannot be null");
        }
        assertIsDynamicQuery(field, "orderByDistance");

        _orderByDistance("'" + field.toField(this::ensureValidFieldName) + "'", shapeWkt, field.getRoundFactor());
    }

    @Override
    public void _orderByDistance(String fieldName, String shapeWkt) {
        _orderByDistance(fieldName, shapeWkt, 0);
    }

    @Override
    public void _orderByDistance(String fieldName, String shapeWkt, double roundFactor) {
        String roundFactorParameterName = roundFactor == 0 ? null : addQueryParameter(roundFactor);
        orderByTokens.add(OrderByToken.createDistanceAscending(fieldName, addQueryParameter(shapeWkt), roundFactorParameterName));
    }

    @Override
    public void _orderByDistanceDescending(DynamicSpatialField field, double latitude, double longitude) {
        if (field == null) {
            throw new IllegalArgumentException("Field cannot be null");
        }
        assertIsDynamicQuery(field, "orderByDistanceDescending");
        _orderByDistanceDescending("'" + field.toField(this::ensureValidFieldName) + "'", latitude, longitude, field.getRoundFactor());
    }

    @Override
    public void _orderByDistanceDescending(String fieldName, double latitude, double longitude) {
        _orderByDistanceDescending(fieldName, latitude, longitude, 0);
    }

    @Override
    public void _orderByDistanceDescending(String fieldName, double latitude, double longitude, double roundFactor) {
        String roundFactorParameterName = roundFactor == 0 ? null : addQueryParameter(roundFactor);
        orderByTokens.add(OrderByToken.createDistanceDescending(fieldName, addQueryParameter(latitude), addQueryParameter(longitude), roundFactorParameterName));
    }

    @Override
    public void _orderByDistanceDescending(DynamicSpatialField field, String shapeWkt) {
        if (field == null) {
            throw new IllegalArgumentException("Field cannot be null");
        }
        assertIsDynamicQuery(field, "orderByDistanceDescending");
        _orderByDistanceDescending("'" + field.toField(this::ensureValidFieldName) + "'", shapeWkt, field.getRoundFactor());
    }

    @Override
    public void _orderByDistanceDescending(String fieldName, String shapeWkt) {
        _orderByDistanceDescending(fieldName, shapeWkt, 0);
    }

    @Override
    public void _orderByDistanceDescending(String fieldName, String shapeWkt, double roundFactor) {
        String factorParamName = roundFactor == 0 ? null : addQueryParameter(roundFactor);
        orderByTokens.add(OrderByToken.createDistanceDescending(fieldName, addQueryParameter(shapeWkt), factorParamName));
    }

    private void assertIsDynamicQuery(DynamicSpatialField dynamicField, String methodName) {
        if (!fromToken.isDynamic()) {
            throw new IllegalStateException("Cannot execute query method '" + methodName + "'. Field '"
                    + dynamicField.toField(this::ensureValidFieldName) + "' cannot be used when static index '" + fromToken.getIndexName()
                    + "' is queried. Dynamic spatial fields can only be used with dynamic queries, " +
                    "for static index queries please use valid spatial fields defined in index definition.");
        }
    }

    protected void initSync() {
        if (queryOperation != null) {
            return;
        }

        queryOperation = initializeQueryOperation();
        executeActualQuery();
    }

    @SuppressWarnings("unused")
    private void executeActualQuery() {
        try (CleanCloseable context = queryOperation.enterQueryContext()) {
            QueryCommand command = queryOperation.createRequest();
            theSession.getRequestExecutor().execute(command, theSession.sessionInfo);
            queryOperation.setResult(command.getResult());
        }
        invokeAfterQueryExecuted(queryOperation.getCurrentQueryResults());
    }

    @Override
    public Iterator<T> iterator() {
        return executeQueryOperation(null).iterator();
    }

    public List<T> toList() {
        return executeQueryOperation(null);
    }

    public T[] toArray() {
        return executeQueryOperationAsArray(null);
    }

    public QueryResult getQueryResult() {
        initSync();

        return queryOperation.getCurrentQueryResults().createSnapshot();
    }

    public T first() {
        Collection<T> result = executeQueryOperation(1);
        if (result.isEmpty()) {
            throw new IllegalStateException("Expected at least one result");
        }
        return result.stream().findFirst().get();
    }

    public T firstOrDefault() {
        Collection<T> result = executeQueryOperation(1);
        return result.stream().findFirst().orElseGet(() -> Defaults.defaultValue(clazz));
    }

    public T single() {
        Collection<T> result = executeQueryOperation(2);
        if (result.size() != 1) {
            throw new IllegalStateException("Expected single result, got: " + result.size());
        }
        return result.stream().findFirst().get();
    }

    public T singleOrDefault() {
        Collection<T> result = executeQueryOperation(2);
        if (result.size() > 1) {
            throw new IllegalStateException("Expected single result, got: " + result.size());
        }
        if (result.isEmpty()) {
            return Defaults.defaultValue(clazz);
        }
        return result.stream().findFirst().get();
    }

    public int count() {
        _take(0);
        QueryResult queryResult = getQueryResult();
        return queryResult.getTotalResults();
    }

    public long longCount() {
        _take(0);
        QueryResult queryResult = getQueryResult();
        return queryResult.getLongTotalResults();
    }

    public boolean any() {
        if (isDistinct()) {
            // for distinct it is cheaper to do count 1
            return executeQueryOperation(1).iterator().hasNext();
        }

        _take(0);
        QueryResult queryResult = getQueryResult();
        return queryResult.getTotalResults() > 0;
    }

    private List<T> executeQueryOperation(Integer take) {
        executeQueryOperationInternal(take);

        return queryOperation.complete(clazz);
    }

    private T[] executeQueryOperationAsArray(Integer take) {
        executeQueryOperationInternal(take);

        return queryOperation.completeAsArray(clazz);
    }

    private void executeQueryOperationInternal(Integer take) {
        if (take != null && (pageSize == null || pageSize > take)) {
            _take(take);
        }

        initSync();
    }

    public void _aggregateBy(FacetBase facet) {
        for (QueryToken token : selectTokens) {
            if (token instanceof FacetToken) {
                continue;
            }

            throw new IllegalStateException("Aggregation query can select only facets while it got " + token.getClass().getSimpleName() + " token");
        }

        selectTokens.add(FacetToken.create(facet, this::addQueryParameter));
    }

    public void _aggregateUsing(String facetSetupDocumentId) {
        selectTokens.add(FacetToken.create(facetSetupDocumentId));
    }

    public Lazy<List<T>> lazily() {
        return lazily(null);
    }

    @SuppressWarnings("unchecked")
    public Lazy<List<T>> lazily(Consumer<List<T>> onEval) {
        LazyQueryOperation<T> lazyQueryOperation = getLazyQueryOperation();

        return ((DocumentSession)theSession).addLazyOperation((Class<List<T>>) (Class<?>)List.class, lazyQueryOperation, onEval);
    }

    public Lazy<Integer> countLazily() {
        if (queryOperation == null) {
            _take(0);
            queryOperation = initializeQueryOperation();
        }

        LazyQueryOperation<T> lazyQueryOperation = new LazyQueryOperation<>(clazz, theSession, queryOperation, afterQueryExecutedCallback);
        return ((DocumentSession)theSession).addLazyCountOperation(lazyQueryOperation);
    }

    @Override
    public void _suggestUsing(SuggestionBase suggestion) {
        if (suggestion == null) {
            throw new IllegalArgumentException("suggestion cannot be null");
        }

        assertCanSuggest(suggestion);

        SuggestToken token;

        if (suggestion instanceof SuggestionWithTerm) {
            SuggestionWithTerm term = (SuggestionWithTerm) suggestion;
            token = SuggestToken.create(term.getField(), term.getDisplayField(), addQueryParameter(term.getTerm()), getOptionsParameterName(term.getOptions()));
        } else if (suggestion instanceof SuggestionWithTerms) {
            SuggestionWithTerms terms = (SuggestionWithTerms) suggestion;
            token = SuggestToken.create(terms.getField(), terms.getDisplayField(), addQueryParameter(terms.getTerms()), getOptionsParameterName(terms.getOptions()));
        } else {
            throw new UnsupportedOperationException("Unknown type of suggestion: " + suggestion.getClass());
        }

        selectTokens.add(token);
    }

    private String getOptionsParameterName(SuggestionOptions options) {
        String optionsParameterName = null;
        if (options != null && options != SuggestionOptions.defaultOptions) {
            optionsParameterName = addQueryParameter(options);
        }

        return optionsParameterName;
    }

    private void assertCanSuggest(SuggestionBase suggestion) {
        if (!whereTokens.isEmpty()) {
            throw new IllegalStateException("Cannot add suggest when WHERE statements are present.");
        }

        if (!selectTokens.isEmpty()) {
            QueryToken lastToken = selectTokens.get(selectTokens.size() - 1);
            if (lastToken instanceof SuggestToken) {
                SuggestToken st = (SuggestToken) lastToken;
                if (st.getFieldName().equals(suggestion.getField())) {
                    throw new IllegalStateException("Cannot add suggest for the same field again.");
                }
            } else {
                throw new IllegalStateException("Cannot add suggest when SELECT statements are present.");
            }
        }

        if (!orderByTokens.isEmpty()) {
            throw new IllegalStateException("Cannot add suggest when ORDER BY statements are present.");
        }
    }

    protected Explanations explanations;

    protected ExplanationToken explanationToken;

    public void _includeExplanations(ExplanationOptions options, Reference<Explanations> explanationsReference) {
        if (explanationToken != null) {
            throw new IllegalStateException("Duplicate IncludeExplanations method calls are forbidden.");
        }

        String optionsParameterName = options != null ? addQueryParameter(options) : null;
        explanationToken = ExplanationToken.create(optionsParameterName);
        this.explanations = explanationsReference.value = new Explanations();
    }

    protected List<TimeSeriesIncludesToken> timeSeriesIncludesTokens;

    protected List<CounterIncludesToken> counterIncludesTokens;

    protected List<CompareExchangeValueIncludesToken> compareExchangeValueIncludesTokens;

    protected List<RevisionIncludesToken> revisionsIncludesTokens;

    protected void _includeCounters(String alias, Map<String, Tuple<Boolean, Set<String>>> counterToIncludeByDocId) {
        if (counterToIncludeByDocId == null || counterToIncludeByDocId.isEmpty()) {
            return;
        }

        counterIncludesTokens = new ArrayList<>();
        _includesAlias = alias;

        for (Map.Entry<String, Tuple<Boolean, Set<String>>> kvp : counterToIncludeByDocId.entrySet()) {
            if (kvp.getValue().first) {
                counterIncludesTokens.add(CounterIncludesToken.all(kvp.getKey()));
                continue;
            }

            if (kvp.getValue().second == null || kvp.getValue().second.isEmpty()) {
                continue;
            }

            for (String name : kvp.getValue().second) {
                counterIncludesTokens.add(CounterIncludesToken.create(kvp.getKey(), name));
            }
        }
    }

    private void _includeTimeSeries(String alias, Map<String, Set<AbstractTimeSeriesRange>> timeSeriesToInclude) {
        if (timeSeriesToInclude == null || timeSeriesToInclude.isEmpty()) {
            return;
        }

        timeSeriesIncludesTokens = new ArrayList<>();
        if (_includesAlias == null) {
            _includesAlias = alias;
        }

        for (Map.Entry<String, Set<AbstractTimeSeriesRange>> kvp : timeSeriesToInclude.entrySet()) {
            for (AbstractTimeSeriesRange range : kvp.getValue()) {
                timeSeriesIncludesTokens.add(TimeSeriesIncludesToken.create(kvp.getKey(), range));
            }
        }
    }

    private void _includeRevisions(Date dateTime) {
        if (revisionsIncludesTokens == null) {
            revisionsIncludesTokens = new ArrayList<>();
        }

        revisionsIncludesTokens.add(RevisionIncludesToken.create(dateTime));
    }

    private void _includeRevisions(Set<String> revisionsToIncludeByChangeVector) {
        if (revisionsIncludesTokens == null) {
            revisionsIncludesTokens = new ArrayList<>();
        }

        for (String changeVector : revisionsToIncludeByChangeVector) {
            revisionsIncludesTokens.add(RevisionIncludesToken.create(changeVector));
        }
    }

    @Override
    public String getParameterPrefix() {
        return parameterPrefix;
    }

    @Override
    public void setParameterPrefix(String parameterPrefix) {
        this.parameterPrefix = parameterPrefix;
    }
}
