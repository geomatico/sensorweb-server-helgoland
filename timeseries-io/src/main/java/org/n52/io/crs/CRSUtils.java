/**
 * Copyright (C) 2013-2014 52°North Initiative for Geospatial Open Source
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
package org.n52.io.crs;

import static com.vividsolutions.jts.geom.PrecisionModel.FLOATING;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.geotools.factory.Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER;
import static org.geotools.referencing.ReferencingFactoryFinder.getCRSAuthorityFactory;
import static org.n52.io.geojson.GeojsonCrs.createNamedCRS;
import static org.n52.io.geojson.GeojsonPoint.createWithCoordinates;

import org.geotools.factory.Hints;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.CRS.AxisOrder;
import org.n52.io.geojson.GeojsonPoint;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.PrecisionModel;

public final class CRSUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(CRSUtils.class);

    /**
     * Default is CRS:84 (EPSG:4326 with lon/lat ordering).
     */
    public static final String DEFAULT_CRS = "CRS:84";

    /**
     * Internally used spatial reference frame.
     */
    private static CoordinateReferenceSystem internCrs;

    protected CRSAuthorityFactory crsFactory;

    /**
     * use static constructors to create an instance.
     * 
     * @param crsFactory
     *        a factory used to create authorized reference systems.
     * @throws IllegalStateException
     *         if creating {@link #internCrs} fails.
     */
    private CRSUtils(CRSAuthorityFactory crsFactory) {
        try {
            internCrs = CRS.decode("CRS:84");
            this.crsFactory = crsFactory;
        }
        catch (FactoryException e) {
            throw new IllegalStateException("Could not create intern CRS!", e);
        }
    }

    public Point convertToPointFrom(GeojsonPoint geometry) {
        return convertToPointFrom(geometry, DEFAULT_CRS);
    }

    public Point convertToPointFrom(GeojsonPoint geometry, String crs) {
        Double[] coordinates = geometry.getCoordinates();
        return createPoint(coordinates[0], coordinates[1], crs);
    }

    /**
     * Creates a GeoJSON representation for the given point.
     * 
     * @param point
     *        the point to convert.
     * @return a GeoJSON representation of the given point.
     */
    public GeojsonPoint convertToGeojsonFrom(Point point) {
        return createWithCoordinates(new Double[] {point.getX(), point.getY()});
    }

    /**
     * Creates a GeoJSON representation of the given point. Adds a named <code>crs</code> member if it is
     * different to the internally used CRS:84.
     * 
     * @param point
     *        the point to be converted to GeoJSON.
     * @param targetCrs
     *        the target CRS.
     * @return a GeoJSON representation of the given point.
     * @throws TransformException
     *         if transforming point fails.
     * @throws FactoryException
     *         if creating the target CRS fails.
     */
    public GeojsonPoint convertToGeojsonFrom(Point point, String targetCrs) throws TransformException, FactoryException {
        Point transformedPoint = transformInnerToOuter(point, targetCrs);
        double x = transformedPoint.getX();
        double y = transformedPoint.getY();
        GeojsonPoint asGeoJSON = createWithCoordinates(new Double[] {x, y});
        if ( !DEFAULT_CRS.equalsIgnoreCase(targetCrs)) {
            asGeoJSON.setCrs(createNamedCRS(targetCrs));
        }
        return asGeoJSON;
    }

    /**
     * Creates a 2D point geometry within the given reference system.
     * 
     * @param x
     *        the coordinate's x value.
     * @param y
     *        the coordinate's y value.
     * @param srs
     *        an authoritive spatial reference system code, e.g. <code>EPSG:4326</code> or <code>CRS:84</code>
     *        .
     * @return a point referenced by the given spatial reference system.
     */
    public Point createPoint(double x, double y, String srs) {
        return createPoint(x, y, Double.NaN, srs);
    }

    /**
     * Creates a 2D point geometry with respect to its height and given reference system.
     * 
     * @param x
     *        the point's x value.
     * @param y
     *        the point's y value.
     * @param z
     *        the height or <code>null</code> if coordinate is 2D.
     * @param srs
     *        an authoritive spatial reference system code, e.g. <code>EPSG:4326</code> or <code>CRS:84</code>
     *        .
     * @return a point referenced by the given spatial reference system.
     */
    public Point createPoint(double x, double y, double z, String srs) {
        Coordinate coordinate = new Coordinate(x, y, z);
        GeometryFactory factory = createGeometryFactory(srs);
        return factory.createPoint(coordinate);
    }

    GeometryFactory createGeometryFactory(String srs) {
        return createGeometryFactory(getSrsIdFrom(srs));
    }

    GeometryFactory createGeometryFactory(int srsId) {
        PrecisionModel pm = new PrecisionModel(FLOATING);
        return new GeometryFactory(pm, srsId);
    }

    /**
     * Extracts the SRS number of the incoming SRS definition string. This can be either an HTTP URL (like
     * <code>http://www.opengis.net/def/crs/EPSG/0/4326</code>) or a URN (like
     * <code>urn:ogc:def:crs:EPSG::31466</code>).
     * 
     * @param srs
     *        the SRS definition string, either as URL ('<code>/</code>'-separated) or as URN ('<code>:</code>
     *        '-separated).
     * @return the SRS number, e.g. 4326
     */
    public int getSrsIdFrom(String srs) {
        return getSrsIdFromEPSG(extractSRSCode(srs));
    }

    /**
     * @param srs
     *        the SRS definition string, either as URL ('<code>/</code>'-separated) or as URN ('<code>:</code>
     *        '-separated).
     * @return SRS string in the form of for example 'EPSG:4326' or 'EPSG:31466'.
     */
    public String extractSRSCode(String srs) {
        if (isSrsUrlDefinition(srs)) {
            return "EPSG:" + srs.substring(srs.lastIndexOf("/") + 1);
        }
        else {
            String[] srsParts = srs.split(":");
            return "EPSG:" + srsParts[srsParts.length - 1];
        }
    }

    private boolean isSrsUrlDefinition(String srs) {
        return srs.startsWith("http");
    }

    public int getSrsIdFromEPSG(String srs) {
        String[] epsgParts = srs.split(":");
        if (epsgParts.length > 1) {
            return Integer.parseInt(epsgParts[epsgParts.length - 1]);
        }
        return Integer.parseInt(srs);
    }

    /**
     * Transforms a given point from a given reference to inner reference, which is WGS84 (CRS:84).
     * 
     * @param point
     *        the point to transform.
     * @param srcFrame
     *        the CRS authority code the given point is referenced in.
     * @return a point referenced in WGS84
     * @throws FactoryException
     *         if the creation of {@link CoordinateReferenceSystem} fails or no appropriate
     *         {@link MathTransform} could be created.
     * @throws TransformException
     *         if transformation fails for any other reason.
     */
    public Point transformOuterToInner(Point point, String srcFrame) throws FactoryException, TransformException {
        return (Point) transform(point, getCrsFor(srcFrame), internCrs);
    }

    /**
     * Transforms a given point from its inner reference (which is WGS84 (CRS:84)) to a given reference.
     * 
     * @param point
     *        the point to transform.
     * @param destFrame
     *        the CRS authority code the given point shall be transformed to.
     * @return a transformed point with dest reference.
     * @throws FactoryException
     *         if the creation of {@link CoordinateReferenceSystem} fails or no appropriate
     *         {@link MathTransform} could be created.
     * @throws TransformException
     *         if transformation fails for any other reason.
     */
    public Point transformInnerToOuter(Point point, String destFrame) throws FactoryException, TransformException {
        return (Point) transform(point, internCrs, getCrsFor(destFrame));
    }

    /**
     * Transforms a given point from a given reference to a destinated reference.
     * 
     * @param point
     *        the point to transform.
     * @param srcFrame
     *        the reference the given point is in.
     * @param destFrame
     *        the reference frame the point shall be transformed to.
     * @return a transformed point.
     * @throws FactoryException
     *         if the creation of {@link CoordinateReferenceSystem} fails or no appropriate
     *         {@link MathTransform} could be created.
     * @throws TransformException
     *         if transformation fails for any other reason.
     */
    public Point transform(Point point, String srcFrame, String destFrame) throws FactoryException, TransformException {
        return (Point) transform(point, getCrsFor(srcFrame), getCrsFor(destFrame));
    }

    /**
     * Transforms a given geometry from a given reference to a destinated reference.
     * 
     * @param geometry
     *        the geometry to transform.
     * @param srcFrame
     *        the reference the given point is in.
     * @param destFrame
     *        the reference frame the point shall be transformed to.
     * @return a transformed point.
     * @throws FactoryException
     *         if the creation of {@link CoordinateReferenceSystem} fails or no appropriate
     *         {@link MathTransform} could be created.
     * @throws TransformException
     *         if transformation fails for any other reason.
     */
    public Geometry transform(Geometry geometry, String srcFrame, String destFrame) throws FactoryException,
            TransformException {
        return transform(geometry, getCrsFor(srcFrame), getCrsFor(destFrame));
    }

    private Geometry transform(Geometry point, CoordinateReferenceSystem srs, CoordinateReferenceSystem dest) throws FactoryException,
            TransformException {
        return JTS.transform(point, CRS.findMathTransform(srs, dest));
    }

    /**
     * Indicates if the given reference frame has switched axes compared to the inner default (lon/lat).
     * 
     * @param outer
     *        the given reference frame code to check.
     * @return <code>true</code> if axes order is switched compared to the inner default.
     * @throws FactoryException
     *         if no proper CRS could be created.
     */
    public boolean isLatLonAxesOrder(String outer) throws FactoryException {
        return isAxesSwitched(internCrs, getCrsFor(outer));
    }

    /**
     * Gets the propert coordinate reference system defined for the given authority code. If no matching CRS
     * could be found the default {@link #internCrs} is being returned.
     * 
     * @param authorityCode
     *        the CRS code, like <code>EPSG:4326</code> or <code>CRS:84</code>.
     * @return the CRS instance for the given code or {@link #internCrs} if either no matching CRS could be
     *         found or an error occured during resolving code.
     * @throws FactoryException
     *         if creating CRS failed.
     */
    private CoordinateReferenceSystem getCrsFor(String authorityCode) throws FactoryException {
        if (authorityCode == null || DEFAULT_CRS.equalsIgnoreCase(authorityCode)) {
            return internCrs;
        }
        return crsFactory.createCoordinateReferenceSystem(authorityCode);
    }

    /**
     * @param first
     *        the first CRS.
     * @param second
     *        the second CRS.
     * @return <code>true</code> if the first axes of both given CRS do not point in the same direction,
     *         <code>false</code> otherwise.
     */
    private boolean isAxesSwitched(CoordinateReferenceSystem first, CoordinateReferenceSystem second) {
        AxisOrder axisOrderFirst = CRS.getAxisOrder(first);
        AxisOrder axisOrderSecond = CRS.getAxisOrder(second);
        if (axisOrderFirst == AxisOrder.INAPPLICABLE || axisOrderSecond == AxisOrder.INAPPLICABLE) {
            LOGGER.warn("Could not determine if axes ordering is switched.");
            return false;
        }
        return axisOrderFirst != axisOrderSecond;

        // AxisDirection sourceFirstAxis = first.getCoordinateSystem().getAxis(0).getDirection();
        // AxisDirection targetFirstAxis = second.getCoordinateSystem().getAxis(0).getDirection();
        // return sourceFirstAxis.equals(AxisDirection.NORTH) && !targetFirstAxis.equals(AxisDirection.NORTH)
        // || !sourceFirstAxis.equals(AxisDirection.NORTH) && targetFirstAxis.equals(AxisDirection.NORTH);
    }

    /**
     * Creates an {@link CRSUtils} which offers assistance when doing spatial opererations. Strict means that
     * all CRS defined with lat/lon axis ordering will be handled as defined.
     * 
     * @return creates a reference helper which (strictly) handles referencing operations.
     * @throws IllegalStateException
     *         if decoding default CRS fails.
     */
    public static CRSUtils createEpsgStrictAxisOrder() {
        /*
         * Setting FORCE_LONGITUDE_FIRST_AXIS_ORDER to FALSE seems to be unnecessary as this is geotools
         * default value for this. It becomes necessary, when property org.geotools.referencing.forceXY was
         * set as System property which silently switches axis order when running in the same JVM environment
         * (as within an Apache Tomcat).
         * 
         * FORCE_LONGITUDE_FIRST_AXIS_ORDER parameter is preferred to org.geotools.referencing.forceXY so we
         * have to set it explicitly to find the correct CRS factory.
         */
        Hints hints = new Hints(FORCE_LONGITUDE_FIRST_AXIS_ORDER, FALSE);
        return createEpsgReferenceHelper(hints);
    }

    /**
     * Creates a {@link CRSUtils} which offers assistance when doing spatial opererations. Forcing XY means
     * that CRS axis ordering is considered lon/lat ordering, even if defined lat/lon.
     * 
     * @return creates a reference helper which (strictly) handles referencing operations.
     * @throws IllegalStateException
     *         if decoding default CRS fails.
     */
    public static CRSUtils createEpsgForcedXYAxisOrder() {
        Hints hints = new Hints(FORCE_LONGITUDE_FIRST_AXIS_ORDER, TRUE);
        return createEpsgReferenceHelper(hints);
    }

    /**
     * Creates a {@link CRSUtils} which offers assistance when doing spatial opererations.
     * 
     * @param hints
     *        Some Geotools {@link Hints} which set behavior and special considerations regarding to the
     *        spatial operations.
     * @throws FactoryException
     *         if decoding default CRS fails.
     */
    public static CRSUtils createEpsgReferenceHelper(Hints hints) throws IllegalStateException {
        return new CRSUtils(getCRSAuthorityFactory("EPSG", hints));
    }

}
