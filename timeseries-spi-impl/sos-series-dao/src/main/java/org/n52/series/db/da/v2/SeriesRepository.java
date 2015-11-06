/**
 * Copyright (C) 2013-2015 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 as publishedby the Free
 * Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of the
 * following licenses, the combination of the program with the linked library is
 * not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed under
 * the aforementioned licenses, is permitted by the copyright holders if the
 * distribution is compliant with both the GNU General Public License version 2
 * and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 */
package org.n52.series.db.da.v2;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.Session;
import org.joda.time.Interval;
import org.n52.io.request.IoParameters;
import org.n52.io.response.ReferenceValueOutput;
import org.n52.io.response.TimeseriesData;
import org.n52.io.response.TimeseriesValue;
import org.n52.io.response.v2.PlatformOutput;
import org.n52.io.response.v2.SeriesMetadataOutput;
import org.n52.sensorweb.spi.SearchResult;
import org.n52.sensorweb.spi.search.v1.TimeseriesSearchResult;
import org.n52.series.db.da.DataAccessException;
import org.n52.series.db.da.DbQuery;
import org.n52.series.db.da.OutputAssembler;
import org.n52.series.db.da.beans.DescribableEntity;
import org.n52.series.db.da.beans.FeatureEntity;
import org.n52.series.db.da.beans.I18nEntity;
import org.n52.series.db.da.beans.ProcedureEntity;
import org.n52.series.db.da.beans.ServiceInfo;
import org.n52.series.db.da.beans.v2.ObservationEntityV2;
import org.n52.series.db.da.beans.v2.SeriesEntityV2;
import org.n52.series.db.da.dao.v2.ObservationDao;
import org.n52.series.db.da.dao.v2.SeriesDao;
import org.n52.web.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SeriesRepository extends ExtendedSessionAwareRepository implements OutputAssembler<SeriesMetadataOutput> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeriesRepository.class);

    public SeriesRepository(ServiceInfo serviceInfo) {
        super(serviceInfo);
    }

    @Override
    public Collection<SearchResult> searchFor(String searchString, String locale) {
        Session session = getSession();
        try {
            SeriesDao seriesDao = new SeriesDao(session);
            DbQuery parameters = DbQueryV2.createFrom(IoParameters.createDefaults(), locale);
            List<SeriesEntityV2> found = seriesDao.find(searchString, parameters);
            return convertToResults(found, locale);
        }
        finally {
            returnSession(session);
        }
    }

    @Override
    protected List<SearchResult> convertToSearchResults(List< ? extends DescribableEntity< ? extends I18nEntity>> found,
                                                        String locale) {
        // not needed, use #convertToResults() instead
        return new ArrayList<SearchResult>();
    }

    private List<SearchResult> convertToResults(List<SeriesEntityV2> found, String locale) {
        List<SearchResult> results = new ArrayList<SearchResult>();
        for (SeriesEntityV2 searchResult : found) {
            String pkid = searchResult.getPkid().toString();
            String phenomenonLabel = getLabelFrom(searchResult.getPhenomenon(), locale);
            String procedureLabel = getLabelFrom(searchResult.getProcedure(), locale);
            String stationLabel = getLabelFrom(searchResult.getFeature(), locale);
            String label = createSeriesLabel(phenomenonLabel, procedureLabel, stationLabel);
            results.add(new TimeseriesSearchResult(pkid, label));
        }
        return results;
    }


    @Override
    public List<SeriesMetadataOutput> getAllCondensed(DbQuery query) throws DataAccessException {
        Session session = getSession();
        try {
            SeriesDao seriesDao = new SeriesDao(session);
            List<SeriesMetadataOutput> results = new ArrayList<SeriesMetadataOutput>();
            for (SeriesEntityV2 series : seriesDao.getAllInstances(query)) {
            	/*
            	 *  ATM, the SWC REST API only supports numeric types
            	 *  We check for a unit to check for them
            	 */
            	if (series.getUnit() != null) {
            		results.add(createCondensed(series, query));
            	}
            }
            return results;

        }
        finally {
            returnSession(session);
        }
    }

    @Override
    public List<SeriesMetadataOutput> getAllExpanded(DbQuery query) throws DataAccessException {
        Session session = getSession();
        try {
            SeriesDao seriesDao = new SeriesDao(session);
            List<SeriesMetadataOutput> results = new ArrayList<SeriesMetadataOutput>();
            for (SeriesEntityV2 series : seriesDao.getAllInstances(query)) {
            	/*
            	 *  ATM, the SWC REST API only supports numeric types
            	 *  We check for a unit to check for them
            	 */
            	if (series.getUnit() != null) {
            		results.add(createExpanded(session, series, query));
            	} else {
                    LOGGER.debug("Series entry '{}' without UOM will be ignored!", series.getPkid());
                }
            }
            return results;
        }
        finally {
            returnSession(session);
        }
    }

    @Override
    public SeriesMetadataOutput getInstance(String seriesId, DbQuery dbQuery) throws DataAccessException {
        Session session = getSession();
        try {
            SeriesDao seriesDao = new SeriesDao(session);
            SeriesEntityV2 result = seriesDao.getInstance(parseId(seriesId), dbQuery);
        	/*
        	 *  ATM, the SWC REST API only supports numeric types
        	 *  We check for a unit to check for them
        	 */
            if (result == null || result.getUnit() == null) {
                LOGGER.debug("Series entry '{}' without UOM will be ignored!", seriesId);
                throw new ResourceNotFoundException("Resource with id '" + seriesId + "' could not be found.");
            }
            return createExpanded(session, result, dbQuery);
        }
        finally {
            returnSession(session);
        }
    }
    
    public boolean checkId(String seriesId, DbQuery dbQuery) throws DataAccessException {
    	Session session = getSession();
        try {
            SeriesDao seriesDao = new SeriesDao(session);
            SeriesEntityV2 result = seriesDao.getInstance(parseId(seriesId), dbQuery);
        	/*
        	 *  ATM, the SWC REST API only supports numeric types
        	 *  We check for a unit to check for them
        	 */
            if (result == null || result.getUnit() == null) {
                LOGGER.debug("Series entry '{}' without UOM will be ignored!", seriesId);
                throw new ResourceNotFoundException("Resource with id '" + seriesId + "' could not be found.");
            }
            return true;
        }
        finally {
            returnSession(session);
        }
    }

    public TimeseriesData getData(String seriesId, DbQuery dbQuery) throws DataAccessException {
        Session session = getSession();
        try {
            SeriesDao seriesDao = new SeriesDao(session);
            SeriesEntityV2 series = seriesDao.getInstance(parseId(seriesId), dbQuery);
            return createTimeseriesData(series, dbQuery, session);
        }
        finally {
            returnSession(session);
        }
    }
    
    public TimeseriesData getDataWithReferenceValues(String seriesId, DbQuery dbQuery) throws DataAccessException {
        Session session = getSession();
        try {
            SeriesDao seriesDao = new SeriesDao(session);
            SeriesEntityV2 series = seriesDao.getInstance(parseId(seriesId), dbQuery);
            TimeseriesData result = createTimeseriesData(series, dbQuery, session);
            Set<SeriesEntityV2> referenceValues = series.getReferenceValues();
            // TODO
//            if (referenceValues != null && !referenceValues.isEmpty()) {
//                TimeseriesDataMetadata metadata = new TimeseriesDataMetadata();
//                metadata.setReferenceValues(assembleReferenceSeries(referenceValues, dbQuery, session));
//                result.setMetadata(metadata);
//            }
            return result;
        }
        finally {
            returnSession(session);
        }
    }

    private SeriesMetadataOutput createExpanded(Session session, SeriesEntityV2 series, DbQuery query) throws DataAccessException {
    	SeriesMetadataOutput output = createCondensed(series, query);
    	// TODOD
        output.setParameters(createSeriesOutput(series, query));
        output.setReferenceValues(createReferenceValueOutputs(series, query));
        output.setFirstValue(queryObservationFor(series.getFirstValue(), series));
        output.setLastValue(queryObservationFor(series.getLastValue(), series));
        return output;
    }

	private ReferenceValueOutput[] createReferenceValueOutputs(SeriesEntityV2 series,
                                                               DbQuery query) throws DataAccessException {
        Set<SeriesEntityV2> referenceValues = series.getReferenceValues();
        List<ReferenceValueOutput> outputs = new ArrayList<ReferenceValueOutput>();
        for (SeriesEntityV2 referenceSeriesEntityV2 : referenceValues) {
            if (referenceSeriesEntityV2.isPublished()) {
                ReferenceValueOutput refenceValueOutput = new ReferenceValueOutput();
                ProcedureEntity procedure = referenceSeriesEntityV2.getProcedure();
                refenceValueOutput.setLabel(procedure.getNameI18n(query.getLocale()));
                refenceValueOutput.setReferenceValueId(referenceSeriesEntityV2.getPkid().toString());

                ObservationEntityV2 lastValue = series.getLastValue();
                refenceValueOutput.setLastValue(createTimeseriesValueFor(lastValue, series));
                outputs.add(refenceValueOutput);
            }
        }
        return outputs.toArray(new ReferenceValueOutput[0]);
    }

    private SeriesMetadataOutput createCondensed(SeriesEntityV2 entity, DbQuery query) throws DataAccessException {
        SeriesMetadataOutput output = new SeriesMetadataOutput();
        String locale = query.getLocale();
        String platformLabel = getLabelFrom(entity.getFeature(), locale);
        String procedureLabel = getLabelFrom(entity.getProcedure(), locale);
        String phenomenonLabel = getLabelFrom(entity.getPhenomenon(), locale);
        output.setLabel(createSeriesLabel(phenomenonLabel, procedureLabel, platformLabel));
        output.setId(entity.getPkid().toString());
        output.setUom(entity.getUnit().getNameI18n(locale));
        output.setPlatform(createCondensedPlatform(entity, query));
        return output;
    }

    private String createSeriesLabel(String phenomenon, String procedure, String station) {
        StringBuilder sb = new StringBuilder();
        sb.append(phenomenon).append(" ");
        sb.append(procedure).append(", ");
        return sb.append(station).toString();
    }

    private PlatformOutput createCondensedPlatform(SeriesEntityV2 entity, DbQuery query) throws DataAccessException {
        FeatureEntity feature = entity.getFeature();
        String featurePkid = feature.getPkid().toString();
        PlatformRepository platformRepository = new PlatformRepository(getServiceInfo());
        return platformRepository.getCondensedInstance(featurePkid, query);
    }

    private Map<String, TimeseriesData> assembleReferenceSeries(Set<SeriesEntityV2> referenceValues,
                                                                DbQuery query,
                                                                Session session) throws DataAccessException {
        Map<String, TimeseriesData> referenceSeries = new HashMap<String, TimeseriesData>();
        for (SeriesEntityV2 referenceSeriesEntityV2 : referenceValues) {
            if (referenceSeriesEntityV2.isPublished()) {
                TimeseriesData referenceSeriesData = createTimeseriesData(referenceSeriesEntityV2, query, session);
                if (haveToExpandReferenceData(referenceSeriesData)) {
                    referenceSeriesData = expandReferenceDataIfNecessary(referenceSeriesEntityV2, query, session);
                }
                referenceSeries.put(referenceSeriesEntityV2.getPkid().toString(), referenceSeriesData);
            }
        }
        return referenceSeries;
    }

    private boolean haveToExpandReferenceData(TimeseriesData referenceSeriesData) {
        return referenceSeriesData.getValues().length <= 1;
    }

    private TimeseriesData expandReferenceDataIfNecessary(SeriesEntityV2 seriesEntity, DbQuery query, Session session) throws DataAccessException {
        TimeseriesData result = new TimeseriesData();
        ObservationDao dao = new ObservationDao(session);
        List<ObservationEntityV2> observations = dao.getObservationsFor(seriesEntity, query);
        if ( !hasValidEntriesWithinRequestedTimespan(observations)) {
            ObservationEntityV2 lastValidEntity = seriesEntity.getLastValue();
            result.addValues(expandToInterval(query.getTimespan(), lastValidEntity, seriesEntity));
        }

        if (hasSingleValidReferenceValue(observations)) {
            ObservationEntityV2 entity = observations.get(0);
            result.addValues(expandToInterval(query.getTimespan(), entity, seriesEntity));
        }
        return result;
    }

    private boolean hasValidEntriesWithinRequestedTimespan(List<ObservationEntityV2> observations) {
        return observations.size() > 0;
    }

    private boolean hasSingleValidReferenceValue(List<ObservationEntityV2> observations) {
        return observations.size() == 1;
    }

    private TimeseriesData createTimeseriesData(SeriesEntityV2 seriesEntity, DbQuery query, Session session) throws DataAccessException {
        TimeseriesData result = new TimeseriesData();
        ObservationDao dao = new ObservationDao(session);
        List<ObservationEntityV2> observations = dao.getAllInstancesFor(seriesEntity, query);
        for (ObservationEntityV2 observation : observations) {
            if (observation != null) {
                result.addValues(createTimeseriesValueFor(observation, seriesEntity));
            }
        }
        return result;
    }

    private TimeseriesValue[] expandToInterval(Interval interval, ObservationEntityV2 entity, SeriesEntityV2 series) {
        ObservationEntityV2 referenceStart = new ObservationEntityV2();
        ObservationEntityV2 referenceEnd = new ObservationEntityV2();
        referenceStart.setTimestamp(interval.getStart().toDate());
        referenceEnd.setTimestamp(interval.getEnd().toDate());
        referenceStart.setValue(entity.getValue());
        referenceEnd.setValue(entity.getValue());
        return new TimeseriesValue[] {createTimeseriesValueFor(referenceStart, series),
                                      createTimeseriesValueFor(referenceEnd, series)};

    }

    private TimeseriesValue createTimeseriesValueFor(ObservationEntityV2 observation, SeriesEntityV2 series) {
        if (observation == null) {
            // do not fail on empty observations
            return null;
        }
        TimeseriesValue value = new TimeseriesValue();
        value.setTimestamp(observation.getTimestamp().getTime());
        value.setValue(formatDecimal(observation.getValue(), series));
        return value;
    }
    
    private TimeseriesValue queryObservationFor(ObservationEntityV2 observation, SeriesEntityV2 series) {
        if (observation == null) {
            // do not fail on empty observations
            return null;
        }
        List<ObservationEntityV2> observations = new ObservationDao(getSession()).getInstancesFor(observation.getTimestamp(), series);
        if (observations != null && !observations.isEmpty()) {
        	 return createTimeseriesValueFor(observations.iterator().next(), series);
        }
        return null;
    }

    private Double formatDecimal(Double value, SeriesEntityV2 series) {
        int scale = series.getNumberOfDecimals();
        return new BigDecimal(value)
            .setScale(scale, HALF_UP)
            .doubleValue();
    }

}