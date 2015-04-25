package org.xhtmlrenderer.css.style.derived;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.xhtmlrenderer.css.constants.CSSName;
import org.xhtmlrenderer.css.constants.CSSPrimitiveUnit;
import org.xhtmlrenderer.css.constants.Idents;
import org.xhtmlrenderer.css.parser.FSColor;
import org.xhtmlrenderer.css.parser.FSFunction;
import org.xhtmlrenderer.css.parser.FSRGBColor;
import org.xhtmlrenderer.css.parser.PropertyValue;
import org.xhtmlrenderer.css.parser.property.BuilderUtil;
import org.xhtmlrenderer.css.parser.property.Conversions;
import org.xhtmlrenderer.css.style.CalculatedStyle;
import org.xhtmlrenderer.css.style.CssContext;
import org.xhtmlrenderer.util.GeneralUtil;

import com.github.neoflyingsaucer.extend.controller.error.LangId;

public class FSLinearGradient
{
	private int x1, y1, x2, y2;
	private final List<StopValue> stopPoints = new ArrayList<StopValue>(2);

	public static class StopValue implements Comparable<StopValue>
	{
		private final FSColor color;
		private final CSSPrimitiveUnit lengthType;
		private final Float length;
		private Float dotsValue;
		
		private StopValue(final FSColor color, final Float value, final CSSPrimitiveUnit lengthType)
		{
			this.color = color;
			this.length = value;
			this.lengthType = lengthType;
		}
		
		private StopValue(final FSColor color)
		{
			this.color = color;
			this.length = null;
			this.lengthType = null;
		}
		
		public FSColor getColor()
		{
			return this.color;
		}
		
		public float getLength()
		{
			return this.dotsValue;
		}
		
		@Override
		public String toString() 
		{
			return "[" + this.color.toString() + "](" + this.dotsValue + ")";
		}

		@Override
		public int compareTo(final StopValue arg0) 
		{
			if (this.dotsValue == arg0.dotsValue)
				return 0;
			if (this.dotsValue < arg0.dotsValue)
				return -1;

			return 1;
		}
	}
	
	public List<StopValue> getStopPoints()
	{
		return stopPoints;
	}
	
	private float deg2rad(final float deg)
	{
		return (float) Math.toRadians(deg);
	}
	
	private float rad2deg(final float rad)
	{
		return (float) Math.toDegrees(rad);
	}
	
	// Compute the endpoints so that a gradient of the given angle
	// covers a box of the given size.
	// From: https://github.com/WebKit/webkit/blob/master/Source/WebCore/css/CSSGradientValue.cpp
	private void endPointsFromAngle(float angleDeg, final int w, final int h)
	{
	    angleDeg = angleDeg % 360;
	    if (angleDeg < 0)
	        angleDeg += 360;

	    if (angleDeg == 0) {
	    	x1 = 0;
	    	y1 = h;
	    	
	    	x2 = 0;
	    	y2 = 0;
	        return;
	    }

	    if (angleDeg == 90) {
	    	x1 = 0;
	    	y1 = 0;
	    	
	    	x2 = w;
	    	y2 = 0;
	        return;
	    }

	    if (angleDeg == 180) {
	    	x1 = 0;
	    	y1 = 0;
	    	
	    	x2 = 0;
	    	y2 = h;
	        return;
	    }

	    if (angleDeg == 270) {
	    	x1 = w;
	    	y1 = 0;
	    	
	    	x2 = 0;
	    	y2 = 0;
	        return;
	    }

	    // angleDeg is a "bearing angle" (0deg = N, 90deg = E),
	    // but tan expects 0deg = E, 90deg = N.
	    final float slope = (float) Math.tan(deg2rad(90 - angleDeg));

	    // We find the endpoint by computing the intersection of the line formed by the slope,
	    // and a line perpendicular to it that intersects the corner.
	    final float perpendicularSlope = -1 / slope;

	    // Compute start corner relative to center, in Cartesian space (+y = up).
	    final float halfHeight = h / 2;
	    final float halfWidth = w / 2;
	    float xEnd, yEnd;
	    
	    if (angleDeg < 90)
	    {
	    	xEnd = halfWidth;
	    	yEnd = halfHeight;
	    }
	    else if (angleDeg < 180)
	    {
	    	xEnd = halfWidth;
	    	yEnd = -halfHeight;
	    }
	    else if (angleDeg < 270)
	    {
	    	xEnd = -halfWidth;
	    	yEnd = -halfHeight;
	    }
	    else
	    {
	    	xEnd = -halfWidth;
	    	yEnd = halfHeight;
	    }

	    // Compute c (of y = mx + c) using the corner point.
	    final float c = yEnd - perpendicularSlope * xEnd;
	    final float endX = c / (slope - perpendicularSlope);
	    final float endY = perpendicularSlope * endX + c;

	    // We computed the end point, so set the second point,
	    // taking into account the moved origin and the fact that we're in drawing space (+y = down).
	    x2 = (int) (halfWidth + endX);
	    y2 = (int) (halfHeight - endY);

	    // Reflect around the center for the start point.
	    x1 = (int) (halfWidth - endX);
	    y1 = (int) (halfHeight + endY);
	}
	
	private void constructZero()
	{
		// TODO
		//BuilderUtil.cssNoThrowError(LangId.FUNCTION_GENERAL, "linear-gradient");
		
		// Just return a 1px wide (nearly) transparent gradient.
		x1 = 0;
		y1 = 0;
		
		x2 = 1;
		y2 = 0;
		
		stopPoints.clear();
		stopPoints.add(new StopValue(new FSRGBColor(0, 0, 0, 0)));
		stopPoints.add(new StopValue(new FSRGBColor(0, 0, 0, 0.0001f)));
	
		stopPoints.get(0).dotsValue = 0f;
		stopPoints.get(1).dotsValue = 1f;
		return;
	}
	
	public FSLinearGradient(final FSFunction func, final CalculatedStyle style, final int width, final int height, final CssContext ctx)
	{
		final List<PropertyValue> params = func.getParameters();
		int i = 1;
		
		if (params.isEmpty())
		{
			constructZero();
			return;
		}
		
		if (GeneralUtil.ciEquals(params.get(0).getStringValue(), "to"))
		{
			// The to keyword is followed by one or two position
			// idents (in any order).
			// linear-gradient( to left top, blue, red);
			// linear-gradient( to top right, blue, red);
			for ( ; i < params.size(); i++)
			{
				if (params.get(i).getStringValue() == null || !Idents.looksLikeABGPosition(params.get(i).getStringValue()))
					break;
			}
			
			List<String> positions = Collections.emptyList();
			
			if (i == 2)
			{
				positions = Collections.singletonList(params.get(1).getStringValue().toLowerCase(Locale.US));
			}
			else if (i == 3)
			{
				positions = Arrays.asList(
						params.get(1).getStringValue().toLowerCase(Locale.US),
						params.get(2).getStringValue().toLowerCase(Locale.US));
			}
			
			if (positions.contains("top") && positions.contains("left"))
			{
				x1 = width;
				y1 = height;
				
				x2 = 0;
				y2 = 0;
			}
			else if (positions.contains("top") && positions.contains("right"))
			{
				x1 = 0;
				y1 = height;
				
				x2 = width;
				y2 = 0;
			}
			else if (positions.contains("bottom") && positions.contains("left"))
			{
				x1 = width;
				y1 = 0;
				
				x2 = 0;
				y2 = height;
			}
			else if (positions.contains("bottom") && positions.contains("right"))
			{
				x1 = 0;
				y1 = 0;
				
				x2 = width;
				y2 = height;
			}
			else if (positions.contains("bottom"))
			{
				x1 = 0;
				y1 = 0;
				
				x2 = 0;
				y2 = height;
			}
			else if (positions.contains("top"))
			{
				x1 = 0;
				y1 = height;
				
				x2 = 0;
				y2 = 0;
			}
			else if (positions.contains("left"))
			{
				x1 = width;
				y1 = 0;
				
				x2 = 0;
				y2 = 0;
			}
			else if (positions.contains("right"))
			{
				x1 = 0;
				y1 = 0;
				
				x2 = width;
				y2 = 0;
			}
			else
			{
				constructZero();
				return;
			}
		}
		else if (params.get(0).getPrimitiveTypeN() == CSSPrimitiveUnit.CSS_DEG)
		{
			// linear-gradient(45deg, ...)
			endPointsFromAngle(params.get(0).getFloatValue(), width, height);
		}
		else if (params.get(0).getPrimitiveTypeN() == CSSPrimitiveUnit.CSS_RAD)
		{
			// linear-gradient(2rad, ...)
			endPointsFromAngle(rad2deg(params.get(0).getFloatValue()), width, height);
		}
		else
		{
			// linear-gradient function must begin with the word 'to' or an angle.
			constructZero();
			return;
		}
		
		if (params.size() - i < 2)
		{
			// Less than two color stops provided.
			constructZero();
			return;
		}
		
		
		for (; i < params.size(); i++)
		{
			// Each stop point can have a color and optionally a length.
			final PropertyValue value = params.get(i);
			FSRGBColor color = null;
			
			if (value.getPrimitiveTypeN() == CSSPrimitiveUnit.CSS_IDENT)
			{
                color = Conversions.getColor(value.getStringValue());
			}
			else 
			{
				color = (FSRGBColor) value.getFSColor();
			}

            if (color == null)
            {
            	// Invalid color.
            	constructZero();
            	return;
            }
			
            if (i + 1 < params.size() &&
              	(BuilderUtil.isLength(params.get(i + 1)) || 
               	params.get(i + 1).getPrimitiveTypeN() == CSSPrimitiveUnit.CSS_PERCENTAGE))
            {
              	final PropertyValue val2 = params.get(i + 1);
              	stopPoints.add(new StopValue(color, val2.getFloatValue(), val2.getPrimitiveTypeN()));
              	i++;
            }
            else
            {
              	stopPoints.add(new StopValue(color));
            }
		}

		// Normalize lengths into dots values.
		for (int m = 0; m < stopPoints.size(); m++)
		{
			final StopValue pt = stopPoints.get(m);
			if (pt.length != null)
			{
				pt.dotsValue = 
            			LengthValue.calcFloatProportionalValue(style, CSSName.BACKGROUND_IMAGE, "", pt.length, pt.lengthType, width, ctx);
			}
			else if (m == 0)
			{
				// First value is zero.
				pt.dotsValue = 0f;
			}
			else if (m == stopPoints.size() - 1)
			{
				// Last value is 100%.
				pt.dotsValue = 
           			LengthValue.calcFloatProportionalValue(style, CSSName.BACKGROUND_IMAGE, "100%", 100f, CSSPrimitiveUnit.CSS_PERCENTAGE, width, ctx);
			}
		}
		
		float lastValue = 0f;
		float nextValue = 100f;
		float increment = 0f;

		// TODO: Confirm below is correct, no divide by zero and 
		// no endless loop.

		// Now normalize those stop points without a length.
		for (int j = 1; j < stopPoints.size(); j++)
		{
			if (j + 1 < stopPoints.size() &&
				stopPoints.get(j).dotsValue == null &&
				increment == 0f)
			{
				int k = j + 1;
				
				for (; k < stopPoints.size(); k++)
				{
					if (stopPoints.get(k).dotsValue != null)
					{
						nextValue = stopPoints.get(k).dotsValue;
						break;
					}
				}

				// k now contains the number of values that we had to skip to find a provided
				// value. We use this to get the increment for unprovided values.
				increment = (nextValue - lastValue) / k;
			}

			if (stopPoints.get(j).dotsValue != null)
			{
				increment = 0;
				lastValue = stopPoints.get(j).dotsValue;
			}
			else
			{
				stopPoints.get(j).dotsValue = lastValue + increment;
				lastValue = stopPoints.get(j).dotsValue;
			}
		}
		
		Collections.sort(stopPoints);

		for (int b = 0; b < stopPoints.size() - 1; b++)
		{
			if (stopPoints.get(b).dotsValue.equals(
				stopPoints.get(b + 1).dotsValue))
			{
				// Duplicate lengths.
				constructZero();
				return;
			}
		}
	}
	
	// These function get the x, y of the starting and ending points of the gradient.
	// They assume a start at zero, so should be offset when used.
	
	public int getStartX()
	{
		return x1;
	}

	public int getEndX()
	{
		return x2;
	}
	
	public int getStartY()
	{
		return y1;
	}
	
	public int getEndY()
	{
		return y2;
	}
	
	@Override
	public String toString() 
	{
		return "[" + x1 + ", " + y1 + "] to [" + x2 + ", " + y2 + "](" + stopPoints.toString() + ")";
	}
}
