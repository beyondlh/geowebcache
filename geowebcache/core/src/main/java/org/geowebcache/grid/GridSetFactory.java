/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.grid;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;

public class GridSetFactory {
    /**
     * Default pixel size in meters, producing a default of 90.7 DPI
     *
     * @see GridSubset#getDotsPerInch()
     */
    public static final double DEFAULT_PIXEL_SIZE_METER = 0.00028;
    /*6378137.0  假设的地球赤道半径，
    * 6378137.0 * 2.0 * Math.PI 周长
    * 周长除以360°得到一度代表的长度
    * */
    public final static double EPSG4326_TO_METERS       = 6378137.0 * 2.0 * Math.PI / 360.0;
    public final static double EPSG3857_TO_METERS       = 1;
    public static       int    DEFAULT_LEVELS           = 22;
    private static      Log    log                      = LogFactory.getLog(GridSetFactory.class);

    private static GridSet baseGridSet(String name, SRS srs, int tileWidth, int tileHeight) {
        GridSet gridSet = new GridSet();

        gridSet.setName(name);
        gridSet.setSrs(srs);

        gridSet.setTileWidth(tileWidth);
        gridSet.setTileHeight(tileHeight);

        return gridSet;
    }

    /**
     * Note that you should provide EITHER resolutions or scales. Providing both will cause a
     * precondition violation exception.
     *
     * @param name
     * @param srs
     * @param extent
     * @param resolutions
     * @param scaleDenoms
     * @param tileWidth
     * @param tileHeight
     * @param pixelSize
     * @param yCoordinateFirst
     * @return
     */
//    重要方法，要理解
    public static GridSet createGridSet(final String name, final SRS srs, final BoundingBox extent,
                                        boolean alignTopLeft, double[] resolutions, double[] scaleDenoms, Double metersPerUnit,
                                        double pixelSize, String[] scaleNames, int tileWidth, int tileHeight,
                                        boolean yCoordinateFirst) {

        Assert.notNull(name, "name is null");
        Assert.notNull(srs, "srs is null");
        Assert.notNull(extent, "extent is null");
        Assert.isTrue(!extent.isNull() && extent.isSane(), "Extent is invalid: " + extent);
        Assert.isTrue(resolutions != null || scaleDenoms != null);
        Assert.isTrue(resolutions == null || scaleDenoms == null,
                "Only one of resolutions or scaleDenoms should be provided, not both");

        for (int i = 1; resolutions != null && i < resolutions.length; i++) {
            if (resolutions[i] >= resolutions[i - 1]) {
                throw new IllegalArgumentException(
                        "Each resolution should be lower than it's prior one. Res[" + i + "] == "
                                + resolutions[i] + ", Res[" + (i - 1) + "] == "
                                + resolutions[i - 1] + ".");
            }
        }

        for (int i = 1; scaleDenoms != null && i < scaleDenoms.length; i++) {
            if (scaleDenoms[i] >= scaleDenoms[i - 1]) {
                throw new IllegalArgumentException(
                        "Each scale denominator should be lower than it's prior one. Scale[" + i
                                + "] == " + scaleDenoms[i] + ", Scale[" + (i - 1) + "] == "
                                + scaleDenoms[i - 1] + ".");
            }
        }

        GridSet gridSet = baseGridSet(name, srs, tileWidth, tileHeight);

        gridSet.setResolutionsPreserved(resolutions != null);

        gridSet.setPixelSize(pixelSize);

        gridSet.setOriginalExtent(extent);
        gridSet.yBaseToggle = alignTopLeft;

        gridSet.setyCoordinateFirst(yCoordinateFirst);

        if (metersPerUnit == null) {
            if (srs.equals(SRS.getEPSG4326())) {
                gridSet.setMetersPerUnit(EPSG4326_TO_METERS);
            } else if (srs.equals(SRS.getEPSG3857())) {
                gridSet.setMetersPerUnit(EPSG3857_TO_METERS);
            } else {
                if (resolutions == null) {
                    log.warn("GridSet " + name
                            + " was defined without metersPerUnit, assuming 1m/unit."
                            + " All scales will be off if this is incorrect.");
                } else {
                    log.warn("GridSet " + name + " was defined without metersPerUnit. "
                            + "Assuming 1m per SRS unit for WMTS scale output.");

                    gridSet.setScaleWarning(true);
                }
                gridSet.setMetersPerUnit(1.0);
            }
        } else {
            gridSet.setMetersPerUnit(metersPerUnit);
        }

        if (resolutions == null) {
            gridSet.setGridLevels(new Grid[scaleDenoms.length]);
        } else {
            gridSet.setGridLevels(new Grid[resolutions.length]);
        }

        /*
        http://www.cnblogs.com/naaoveGIS/p/3898607.html
        Scale表示的是比例尺，即地图上的一厘米代表着实际上的多少厘米。例如地图上1厘米代表实地距离500千米，可写成：1 ∶ 50,000,000或写成：1/50,000,000。

Resolution表示的是分辨率。Resolution 的实际含义代表当前地图范围内，1像素代表多少地图单位（X地图单位/像素），地图单位取决于数据本身的空间参考。可见Resolution跟 dpi有关系（dpi代表每英寸的像素数），跟地图的单位也有关系。
        * */
        for (int i = 0; i < gridSet.getGridLevels().length; i++) {
            Grid curGrid = new Grid();
//          根据resolution与比例尺之间的关系，计算resolution
            if (scaleDenoms != null) {
                curGrid.setScaleDenominator(scaleDenoms[i]);
                curGrid.setResolution(pixelSize * (scaleDenoms[i] / gridSet.getMetersPerUnit()));
            } else {
                curGrid.setResolution(resolutions[i]);
                /*
                resolutions[i]*gridSet.getMetersPerUnit()表示的是该分辨率下一个像素代表的长度，
                resolution的实际含义代表当前地图范围内，1像素代表多少地图单位（X地图单位/像素），地图单位取决于数据本身的空间参考，4326的是度，3857是米
                * */
                curGrid.setScaleDenominator((resolutions[i] * gridSet.getMetersPerUnit()) / DEFAULT_PIXEL_SIZE_METER);
            }

            /*一个瓦片宽度代表的地图单位*/
            final double mapUnitWidth = tileWidth * curGrid.getResolution();
            /*一个瓦片高度代表的地图单位*/
            final double mapUnitHeight = tileHeight * curGrid.getResolution();
//          向上取整Math.ceil(2.2)结果为3
//          为什么要这么算？
            final long tilesWide = (long) Math.ceil((extent.getWidth() - mapUnitWidth * 0.01) / mapUnitWidth);
            final long tilesHigh = (long) Math.ceil((extent.getHeight() - mapUnitHeight * 0.01) / mapUnitHeight);

            curGrid.setNumTilesWide(tilesWide);
            curGrid.setNumTilesHigh(tilesHigh);

            if (scaleNames == null || scaleNames[i] == null) {
                curGrid.setName(gridSet.getName() + ":" + i);
            } else {
                curGrid.setName(scaleNames[i]);
            }

            gridSet.getGridLevels()[i] = curGrid;
        }

        return gridSet;
    }


    public static GridSet createGridSet(final String name, final SRS srs, final BoundingBox extent,
                                        final boolean alignTopLeft, final int levels, final Double metersPerUnit,
                                        final double pixelSize, final int tileWidth, final int tileHeight,
                                        final boolean yCoordinateFirst) {

        final double extentWidth = extent.getWidth();
        final double extentHeight = extent.getHeight();
        //X方向，1个像素代表的extent
        double resX = extentWidth / tileWidth;
        //Y方向，1个像素代表的extent
        double resY = extentHeight / tileHeight;
//分别表示X、Y方向图片的个数
        final int tilesWide, tilesHigh;
//        Math.round(1.2) = 1
//        Math.round(1.7) = 2 四舍五入
        if (resX <= resY) {
//            一个瓦片宽度，N个瓦片高度，类似于苹果5s竖着放
            // use one tile wide by N tiles high
            tilesWide = 1;
            tilesHigh = (int) Math.round(resY / resX);
            // previous resY was assuming 1 tile high, recompute with the actual number of tiles
            // high
            resY = resY / tilesHigh;
        } else {
            // 一个瓦片宽度，N个瓦片高度，类似于苹果5s横着放
//            比如范围是[-180,-90,180,90]
            // use one tile high by N tiles wide
            tilesHigh = 1;
            tilesWide = (int) Math.round(resX / resY);
//            得到真实的X方向的一个像素代表的范围
            // previous resX was assuming 1 tile wide, recompute with the actual number of tiles
            // wide
            resX = resX / tilesWide;
        }

//resX, resY，每个像素所代表的范围
        // the maximum of resX and resY is the one that adjusts better
        final double res = Math.max(resX, resY);
//        tileWidth * res一个图片代表的范围
        final double adjustedExtentWidth = tilesWide * tileWidth * res;
        final double adjustedExtentHeight = tilesHigh * tileHeight * res;
        //根据传进来的extent和tileWidth，tileHeight得到调整后的最合适的extent
        BoundingBox adjExtent = new BoundingBox(extent);
        adjExtent.setMaxX(adjExtent.getMinX() + adjustedExtentWidth);
        // Do we keep the top or the bottom fixed?
        if (alignTopLeft) {
            adjExtent.setMinY(adjExtent.getMaxY() - adjustedExtentHeight);
        } else {
            adjExtent.setMaxY(adjExtent.getMinY() + adjustedExtentHeight);
        }

//        Resolution 的实际含义代表当前地图范围内，1像素代表多少地图单位（X地图单位/像素），地图单位取决于数据本身的空间参考
        double[] resolutions = new double[levels];
        resolutions[0] = res;

        for (int i = 1; i < levels; i++) {
            resolutions[i] = resolutions[i - 1] / 2;
        }

        return createGridSet(name, srs, adjExtent, alignTopLeft, resolutions, null, metersPerUnit,
                pixelSize, null, tileWidth, tileHeight, yCoordinateFirst);
    }
}
