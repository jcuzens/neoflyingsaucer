/*
 * {{{ header & license
 * Copyright (c) 2007 Wisconsin Court System
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
package com.github.neoflyingsaucer.pdfout.form;

import java.awt.Point;

import org.w3c.dom.Element;
import org.xhtmlrenderer.css.parser.FSCMYKColor;
import org.xhtmlrenderer.css.parser.FSColor;
import org.xhtmlrenderer.css.parser.FSRGBColor;
import org.xhtmlrenderer.layout.LayoutContext;
import org.xhtmlrenderer.render.BlockBox;
import org.xhtmlrenderer.util.*;

import com.github.neoflyingsaucer.pdfout.PdfOutputDevice;
import com.github.neoflyingsaucer.pdfout.PdfReplacedElement;
import com.github.pdfstream.PdfAppearanceStream;

public abstract class AbstractFormField implements PdfReplacedElement 
{
    protected static final String DEFAULT_CHECKED_STATE = "Yes";
    protected static final String OFF_STATE = "Off"; // required per the spec

    // By default, the field will be font-size * this value
    private static final float FONT_SIZE_ADJUSTMENT = 0.80f;

    private int _x;
    private int _y;
    private int _width;
    private int _height;

    private String _fieldName;

    protected abstract String getFieldType();

    protected int getX() {
        return _x;
    }

    protected void setX(final int x) {
        _x = x;
    }

    protected int getY() {
        return _y;
    }

    protected void setY(final int y) {
        _y = y;
    }

    protected int getWidth() {
        return _width;
    }

    protected void setWidth(final int width) {
        _width = width;
    }

    protected int getHeight() {
        return _height;
    }

    protected void setHeight(final int height) {
        _height = height;
    }

    protected String getFieldName(final PdfOutputDevice outputDevice, final Element e) {
        if (_fieldName == null) {
            final String result = e.getAttribute("name");

            if (Util.isNullOrEmpty(result)) {
                _fieldName = getFieldType()
                        + outputDevice.getNextFormFieldIndex();
            } else {
                _fieldName = result;
            }
        }

        return _fieldName;
    }

    protected String getValue(final Element e) {
        final String result = e.getAttribute("value");

        if (Util.isNullOrEmpty(result)) {
            return DEFAULT_CHECKED_STATE;
        } else {
            return result;
        }
    }

    protected boolean isChecked(final Element e) {
        return e.hasAttribute("checked");
    }

    protected boolean isReadOnly(final Element e) {
        return e.hasAttribute("readonly");
    }
    
    protected boolean isSelected(final Element e) {
        return e.hasAttribute("selected");
    }

    public void detach(final LayoutContext c) {
    }

    public int getIntrinsicHeight() {
        return getHeight();
    }

    public int getIntrinsicWidth() {
        return getWidth();
    }

    public Point getLocation() {
        return new Point(getX(), getY());
    }

    public boolean isRequiresInteractivePaint() {
        // N/A
        return false;
    }

    public void setLocation(final int x, final int y) {
        setX(x);
        setY(y);
    }

    protected void initDimensions(final LayoutContext c, final BlockBox box, final int cssWidth,
            final int cssHeight) {
        if (cssWidth != -1) {
            setWidth(cssWidth);
        } else {
            if (cssHeight != -1) {
                setWidth(cssHeight);
            } else {
                setWidth((int) (box.getStyle().getFont(c).size * FONT_SIZE_ADJUSTMENT));
            }
        }

        if (cssHeight != -1) {
            setHeight(cssHeight);
        } else {
            if (cssWidth != -1) {
                setHeight(cssWidth);
            } else {
                setHeight((int) (box.getStyle().getFont(c).size * FONT_SIZE_ADJUSTMENT));
            }
        }
    }

    protected String spaces(final int count) {
        final StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(' ');
        }
        return result.toString();
    }
    
    protected void setStrokeColor(final PdfAppearanceStream template, final FSColor color)
    {
        if (color instanceof FSRGBColor)
        {
            final FSRGBColor rgb = (FSRGBColor)color;
            template.setRGBColorStroke(rgb.getRed(), rgb.getGreen(), rgb.getBlue());
        }
        else if (color instanceof FSCMYKColor)
        {
            final FSCMYKColor cmyk = (FSCMYKColor)color;
            template.setCMYKColorStroke(
                    (int)(cmyk.getCyan()*255), (int)(cmyk.getMagenta()*255), 
                    (int)(cmyk.getYellow()*255), (int)(cmyk.getBlack()*255));
        }
    }
    
    protected void setFillColor(final PdfAppearanceStream template, final FSColor color)
    {
        if (color instanceof FSRGBColor)
        {
            final FSRGBColor rgb = (FSRGBColor)color;
            template.setRGBColorFill(rgb.getRed(), rgb.getGreen(), rgb.getBlue());
        }
        else if (color instanceof FSCMYKColor)
        {
            final FSCMYKColor cmyk = (FSCMYKColor)color;
            template.setCMYKColorFill(
                    (int)(cmyk.getCyan()*255), (int)(cmyk.getMagenta()*255), 
                    (int)(cmyk.getYellow()*255), (int)(cmyk.getBlack()*255));
        }
    }
}