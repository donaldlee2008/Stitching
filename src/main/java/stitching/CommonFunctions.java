/*
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2017 Fiji
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * An execption is the FFT implementation of Dave Hale which we use as a library,
 * wich is released under the terms of the Common Public License - v1.0, which is 
 * available at http://www.eclipse.org/legal/cpl-v10.html  
 *
 * @author Stephan Preibisch
 */
package stitching;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.MultiLineLabel;
import ij.io.Opener;
import ij.plugin.BrowserLauncher;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ShortProcessor;
import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.meta.MetadataRetrieve;
import ome.units.quantity.Length;
import stitching.utils.Log;

public class CommonFunctions
{
	public static String[] methodList = {"Average", "Linear Blending", "Max. Intensity", "Min. Intensity", "Red-Cyan Overlay"};	
	public final static int AVG = 0, LIN_BLEND = 1,  MAX = 2, MIN = 3, RED_CYAN = 4, NONE = 5;

	public static String[] methodListCollection = {"Average", "Linear Blending", "Max. Intensity", "Min. Intensity", "None"};	
	public static String[] rgbTypes = {"rgb", "rbg", "grb", "gbr", "brg", "bgr"}; 
	public static String[] colorList = { "Red", "Green", "Blue", "Red and Green", "Red and Blue", "Green and Blue", "Red, Green and Blue" };

	public static String[] fusionMethodList = { "Linear Blending", "Average", "Median", "Max. Intensity", "Min. Intensity", "Intensity of random input tile", "Overlay into composite image", "Do not fuse images" };	
	public static String[] fusionMethodListSimple = { "Overlay into composite image", "Do not fuse images" };	
	public static String[] fusionMethodListGrid = { "Linear Blending", "Average", "Median", "Max. Intensity", "Min. Intensity", "Intensity of random input tile", /* "Overlay into composite image", */ "Do not fuse images (only write TileConfiguration)" };	
	public static String[] timeSelect = { "Apply registration of first time-point to all other time-points", "Register images adjacently over time", "Register all images over all time-points globally (expensive!)" };
	public static String[] cpuMemSelect = { "Save memory (but be slower)", "Save computation time (but use more RAM)" };
	
	public static ImagePlus loadImage(String directory, String file, int seriesNumber) { return loadImage(directory, file, seriesNumber, "rgb"); }
	public static ImagePlus loadImage(String directory, String file, int seriesNumber, String rgb)
	{
		ImagePlus imp = null;
		
		String smallFile = file.toLowerCase();
		
		if (smallFile.endsWith("tif") || smallFile.endsWith("tiff") || smallFile.endsWith("jpg") || smallFile.endsWith("png") || smallFile.endsWith("bmp") || 
			smallFile.endsWith("gif") || smallFile.endsWith("jpeg"))
		{
			imp = new Opener().openImage((new File(directory, file)).getPath());
		}
		else
		{
			imp = openLOCIImagePlus(directory, file, seriesNumber, rgb);
			if (imp == null)
				imp = new Opener().openImage((new File(directory, file)).getPath());
		}

		
		return imp;
	}

	public static final void addHyperLinkListener(final MultiLineLabel text, final String myURL)
	{
		if ( text != null && myURL != null )
		{
			text.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					try
					{
						BrowserLauncher.openURL(myURL);
					}
					catch (Exception ex)
					{
						IJ.error("" + ex);
					}
				}
	
				@Override
				public void mouseEntered(MouseEvent e)
				{
					text.setForeground(Color.BLUE);
					text.setCursor(new Cursor(Cursor.HAND_CURSOR));
				}
	
				@Override
				public void mouseExited(MouseEvent e)
				{
					text.setForeground(Color.BLACK);
					text.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
				}
			});
		}
	}

	public static ImagePlus openLOCIImagePlus(String path, String fileName, int seriesNumber, String rgb) 
	{
		return openLOCIImagePlus(path, fileName, seriesNumber, rgb, -1, -1);
	}

	public static ImagePlus openLOCIImagePlus(String path, String fileName, int seriesNumber) 
	{
		return openLOCIImagePlus(path, fileName, seriesNumber, "rgb", -1, -1);
	}

	public static ImagePlus openLOCIImagePlus(String path, String fileName, int seriesNumber, String rgb, int from, int to) 
	{
		if (path.length() > 1) 
		{
			path = path.replace('\\', '/');
			if (!path.endsWith("/"))
				path = path + "/";
		}
		
		// parse howto assign channels
		rgb = rgb.toLowerCase().trim();
		final int colorAssign[][] = new int[rgb.length()][];
		final int colorWeight[] = new int[3];
		
		for (int i = 0; i < colorAssign.length; i++)
		{
			if (rgb.charAt(i) == 'r')
			{
				colorAssign[i] = new int[1]; 
				colorAssign[i][0] = 0;
				colorWeight[0]++;
			}
			else if (rgb.charAt(i) == 'b')
			{
				colorAssign[i] = new int[1]; 
				colorAssign[i][0] = 2;
				colorWeight[2]++;
			}
			else if (rgb.charAt(i) == 'g')
			{
				colorAssign[i] = new int[1]; 
				colorAssign[i][0] = 1;
				colorWeight[1]++;
			}
			else //leave out
			{
				colorAssign[i] = new int[0]; 
			}
		}
		
		for (int i = 0; i < colorWeight.length; i++)
			if (colorWeight[i] == 0)
				colorWeight[i] = 1;
		
		ImagePlus imp = null;
		
		final String id = path + fileName;
		final IFormatReader r = new ChannelSeparator();
		
		try 
		{
			r.setId(id);

			// if loaded from a multiple series file (like LSM 710) select the correct series
			if ( seriesNumber >= 0 )
				r.setSeries( seriesNumber );

			//final int num = r.getImageCount();
			final int width = r.getSizeX();
			final int height = r.getSizeY();
			final int depth = r.getSizeZ();
			final int timepoints = r.getSizeT();
			final int channels;
			//final String formatType = r.getFormat();
			final int pixelType = r.getPixelType();
			final int bytesPerPixel = FormatTools.getBytesPerPixel(pixelType); 
			final String pixelTypeString = FormatTools.getPixelTypeString(pixelType);
			//final String dimensionOrder = r.getDimensionOrder();
			
			if (timepoints > 1)
				Log.warn("More than one timepoint. Not implemented yet. Returning first timepoint");
			
			if (r.getSizeC() > 3)
			{
				Log.warn("More than three channels. ImageJ supports only 3 channels, returning the first three channels.");
				channels = 3;
			}
			else
			{
				channels = r.getSizeC();
			}				
			
			if (!(pixelType == FormatTools.UINT8 || pixelType == FormatTools.UINT16))
			{
				Log.error("PixelType " + pixelTypeString + " not supported yet, returning. ");
				return null;
			}
			
			final int start, end;			
			if (from < 0 || to < 0 || to < from)
			{
				start = 0; end = depth;
			}
			else 
			{
				start = from;
				if (to > depth)
					end = depth;
				else 
					end = to;
			}
			
			/*Log.debug("width: " + width);
			Log.debug("height: " + height);
			Log.debug("depth: " + depth);
			Log.debug("timepoints: " + timepoints);
			Log.debug("channels: " + channels);
			Log.debug("images: " + num);
			Log.debug("image format: " + formatType);
			Log.debug("bytes per pixel: " + bytesPerPixel);
			Log.debug("pixel type: " + pixelTypeString);			
			Log.debug("dimensionOrder: " + dimensionOrder);*/

			final ImageStack stack = new ImageStack(width, height);	
			final int t = 0;			
			
			for (int z = start; z < end; z++)
			{				
				byte[][] b = new byte[channels][width * height * bytesPerPixel];
				
				for (int c = 0; c < channels; c++)
				{
					final int index = r.getIndex(z, c, t);
					r.openBytes(index, b[c]);					
					//Log.debug(index);
				}
				
				if (channels == 1)
				{
					if (pixelType == FormatTools.UINT8)
					{
						final ByteProcessor bp = new ByteProcessor(width, height, b[0], null);
						stack.addSlice("" + (z + 1), bp);
					}	
					else if (pixelType == FormatTools.UINT16)
					{
						final short[] data = new short[width * height];
						
						for (int i = 0; i < data.length; i++)
							data[i] = getShortValue(b[0], i * 2);
													
						final ShortProcessor sp = new ShortProcessor(width, height, data, null);
						
						stack.addSlice("" + (z + 1), sp);						
					}						
				}
				else
				{
					final ColorProcessor cp = new ColorProcessor(width, height);
					final int color[] = new int[3];
					                            
					if (pixelType == FormatTools.UINT8)
						for (int y = 0; y < height; y++)
							for (int x = 0; x < width; x++)
							{
								color[0] = color[1] = color[2] = 0;
								
								for (int c = 0; c < channels; c++)
									for (int e = 0; e < colorAssign[c].length; e++)
									color[colorAssign[c][e]] += b[c][x + y*width] & 0xff;
								
								color[0] /= colorWeight[0]; 
								color[1] /= colorWeight[1]; 
								color[2] /= colorWeight[2]; 
									
								cp.putPixel(x, y, color);
							}
					else if (pixelType == FormatTools.UINT16)
						for (int y = 0; y < height; y++)
							for (int x = 0; x < width; x++)
							{
								color[0] = color[1] = color[2] = 0;
								
								for (int c = 0; c < channels; c++)
									color[c] = (byte)(getShortValue(b[c], 2 * (x + y*width))/256);
								
								cp.putPixel(x, y, color);
							}
					stack.addSlice("" + (z + 1), cp);						
				}
			}
			
			imp = new ImagePlus(fileName, stack);
		}
		catch (IOException exc) { IJ.handleException(exc); return null;}
		catch (FormatException exc) { IJ.handleException(exc); return null;}
	                
		return imp;
	}

	private static final short getShortValue(final byte[] b, final int i)
	{
		return (short)getShortValueInt(b, i);
	}

	private static final int getShortValueInt(final byte[] b, final int i)
	{
		return ((((b[i] & 0xff) << 8)) + (b[i+1] & 0xff));
	}
	
	public static float getPixelValueRGB( final int rgb, final int rgbType )
	{
		final int r = (rgb & 0xff0000) >> 16;
		final int g = (rgb & 0xff00) >> 8;
		final int b = rgb & 0xff;

		// colorList = {"Red", "Green", "Blue", "Red and Green", "Red and Blue", "Green and Blue", "Red, Green and Blue"};

		if (rgbType == 0) return r;
		else if (rgbType == 1) return g;
		else if (rgbType == 2) return b;
		else if (rgbType == 3) return (r + g) / 2.0f;
		else if (rgbType == 4) return (r + b) / 2.0f;
		else if (rgbType == 5) return (g + b) / 2.0f;
		else return (r + g + b) / 3.0f;
	}

	public static final double[] getPlanePosition( final IFormatReader r, final MetadataRetrieve retrieve, int series, int t )
	{
		return getPlanePosition(r, retrieve, series, t, false, false, false);
	}

	public static final double[] getPlanePosition( final IFormatReader r, final MetadataRetrieve retrieve, int series, int t, boolean invertX, boolean invertY, boolean ignoreZStage)
	{
		// generate a mapping from native indices to Plane element indices
		final HashMap< Integer, Integer > planeMap = new HashMap< Integer, Integer >();
		final int planeCount = retrieve.getPlaneCount( series );
		for ( int p = 0; p < planeCount; ++p )
		{
			final int theZ = retrieve.getPlaneTheZ( series, p ).getValue();
			final int theC = retrieve.getPlaneTheC( series, p ).getValue();
			final int theT = retrieve.getPlaneTheT( series, p ).getValue();
			final int index = r.getIndex( theZ, theC, theT );
			planeMap.put( index, p );
		}

		// get reader index of time point t
		final int index = r.getIndex( 0, 0, t );

		// convert reader index to plane element index
		final int planeIndex = planeMap.containsKey( index ) ? planeMap.get( index ) : 0;
		final boolean hasPlane = planeIndex < retrieve.getPlaneCount( series );

		// CTR HACK: Recover gracefully when StageLabel element is missing.
		// This avoids a problem with the OMEXMLMetadataImpl implementation,
		// which currently does not check for null on the StageLabel object.
		Length stageLabelX = null, stageLabelY = null, stageLabelZ = null;
		try
		{
			stageLabelX = retrieve.getStageLabelX( series );
			stageLabelY = retrieve.getStageLabelY( series );
			stageLabelZ = retrieve.getStageLabelZ( series );
		}
		catch (final NullPointerException exc)
		{
			// ignore
		}

		// stage coordinates (for the given series and plane)
		final double locationX = getPosition( hasPlane ? retrieve.getPlanePositionX( series, planeIndex ) : null, stageLabelX, invertX );
		final double locationY = getPosition( hasPlane ? retrieve.getPlanePositionY( series, planeIndex ) : null, stageLabelY, invertY );
		final double locationZ = ignoreZStage ? 0 : getPosition( hasPlane ? retrieve.getPlanePositionZ( series, planeIndex ) : null, stageLabelZ, false );

		Log.debug( "locationX:  " + locationX );
		Log.debug( "locationY:  " + locationY );
		Log.debug( "locationZ:  " + locationZ );

		return new double[] { locationX, locationY, locationZ };
	}

	private static double getPosition( final Length planePos, final Length stageLabel, final boolean invert )
	{
		// check plane position
		if ( planePos != null )
			return invert ? -planePos.value().doubleValue() : planePos.value().doubleValue();

		// check global stage label
		if ( stageLabel != null )
			return invert ? -stageLabel.value().doubleValue() : stageLabel.value().doubleValue();

		return 0;
	}

}
