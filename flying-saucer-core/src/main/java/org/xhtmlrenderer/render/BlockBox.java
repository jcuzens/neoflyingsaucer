/*
 * {{{ header & license
 * Copyright (c) 2004, 2005 Joshua Marinacci
 * Copyright (c) 2006, 2007 Wisconsin Courts System
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
package org.xhtmlrenderer.render;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.xhtmlrenderer.css.constants.CSSName;
import org.xhtmlrenderer.css.constants.IdentValue;
import org.xhtmlrenderer.css.newmatch.CascadedStyle;
import org.xhtmlrenderer.css.parser.FSRGBColor;
import org.xhtmlrenderer.css.style.CalculatedStyle;
import org.xhtmlrenderer.css.style.CssContext;
import org.xhtmlrenderer.css.style.FSDerivedValue;
import org.xhtmlrenderer.css.style.derived.BorderPropertySet;
import org.xhtmlrenderer.css.style.derived.LengthValue;
import org.xhtmlrenderer.css.style.derived.RectPropertySet;
import org.xhtmlrenderer.extend.FSImage;
import org.xhtmlrenderer.extend.ReplacedElement;
import org.xhtmlrenderer.layout.BlockBoxing;
import org.xhtmlrenderer.layout.BlockFormattingContext;
import org.xhtmlrenderer.layout.BoxBuilder;
import org.xhtmlrenderer.layout.BreakAtLineContext;
import org.xhtmlrenderer.layout.CounterFunction;
import org.xhtmlrenderer.layout.FloatManager;
import org.xhtmlrenderer.layout.InlineBoxing;
import org.xhtmlrenderer.layout.InlinePaintable;
import org.xhtmlrenderer.layout.LayoutContext;
import org.xhtmlrenderer.layout.PaintingInfo;
import org.xhtmlrenderer.layout.PersistentBFC;
import org.xhtmlrenderer.layout.Styleable;
import org.xhtmlrenderer.newtable.TableRowBox;
import org.xhtmlrenderer.resource.ImageResource;

/**
 * A block box as defined in the CSS spec.  It also provides a base class for
 * other kinds of block content (for example table rows or cells).
 */
public class BlockBox extends Box implements InlinePaintable {

    public static final int POSITION_VERTICALLY = 1;
    public static final int POSITION_HORIZONTALLY = 2;
    public static final int POSITION_BOTH = POSITION_VERTICALLY | POSITION_HORIZONTALLY;

    public static final int CONTENT_UNKNOWN = 0;
    public static final int CONTENT_INLINE = 1;
    public static final int CONTENT_BLOCK = 2;
    public static final int CONTENT_EMPTY = 4;

    protected static final int NO_BASELINE = Integer.MIN_VALUE;

    private MarkerData _markerData;

    private int _listCounter;

    private PersistentBFC _persistentBFC;

    private Box _staticEquivalent;

    private boolean _needPageClear;

    private ReplacedElement _replacedElement;

    private int _childrenContentType;

    private List<Styleable> _inlineContent;

    private boolean _topMarginCalculated;
    private boolean _bottomMarginCalculated;
    private MarginCollapseResult _pendingCollapseCalculation;

    private int _minWidth;
    private int _maxWidth;
    private boolean _minMaxCalculated;

    private boolean _dimensionsCalculated;
    private boolean _needShrinkToFitCalculatation;

    private CascadedStyle _firstLineStyle;
    private CascadedStyle _firstLetterStyle;

    private FloatedBoxData _floatedBoxData;

    private int _childrenHeight;

    private boolean _fromCaptionedTable;

    public BlockBox() {
        super();
    }

    public BlockBox copyOf() {
        final BlockBox result = new BlockBox();
        result.setStyle(getStyle());
        result.setElement(getElement());

        return result;
    }

    protected String getExtraBoxDescription() {
        return "";
    }

    public String toString() {
        final StringBuilder result = new StringBuilder();
        final String className = getClass().getName();
        result.append(className.substring(className.lastIndexOf('.') + 1));
        result.append(": ");
        if (getElement() != null && ! isAnonymous()) {
            result.append("<");
            result.append(getElement().getNodeName());
            result.append("> ");
        }
        if (isAnonymous()) {
            result.append("(anonymous) ");
        }
        if (getPseudoElementOrClass() != null) {
            result.append(':');
            result.append(getPseudoElementOrClass());
            result.append(' ');
        }
        result.append('(');
        result.append(getStyle().getIdent(CSSName.DISPLAY).toString());
        result.append(") ");

        if (getStyle().isRunning()) {
            result.append("(running) ");
        }

        result.append('(');
        switch (getChildrenContentType()) {
            case CONTENT_BLOCK:
                result.append('B');
                break;
            case CONTENT_INLINE:
                result.append('I');
                break;
            case CONTENT_EMPTY:
                result.append('E');
                break;
        }
        result.append(") ");

        result.append(getExtraBoxDescription());

        appendPositioningInfo(result);
        result.append("(" + getAbsX() + "," + getAbsY() + ")->(" + getWidth() + " x " + getHeight() + ")");
        return result.toString();
    }

    protected void appendPositioningInfo(final StringBuilder result) {
        if (getStyle().isRelative()) {
            result.append("(relative) ");
        }
        if (getStyle().isFixed()) {
            result.append("(fixed) ");
        }
        if (getStyle().isAbsolute()) {
            result.append("(absolute) ");
        }
        if (getStyle().isFloated()) {
            result.append("(floated) ");
        }
    }

    public String dump(final LayoutContext c, final String indent, final int which) {
        final StringBuilder result = new StringBuilder(indent);

        ensureChildren(c);

        result.append(this);

        final RectPropertySet margin = getMargin(c);
        result.append(" effMargin=[" + margin.top() + ", " + margin.right() + ", " +
                margin.bottom() + ", " + margin.right() + "] ");
        final RectPropertySet styleMargin = getStyleMargin(c);
        result.append(" styleMargin=[" + styleMargin.top() + ", " + styleMargin.right() + ", " +
                styleMargin.bottom() + ", " + styleMargin.right() + "] ");

        if (getChildrenContentType() != CONTENT_EMPTY) {
            result.append('\n');
        }

        switch (getChildrenContentType()) {
            case CONTENT_BLOCK:
                dumpBoxes(c, indent, getChildren(), which, result);
                break;
            case CONTENT_INLINE:
                if (which == Box.DUMP_RENDER) {
                    dumpBoxes(c, indent, getChildren(), which, result);
                } else {
                    for (final Iterator<Styleable> i = getInlineContent().iterator(); i.hasNext();) {
                        final Styleable styleable = (Styleable) i.next();
                        if (styleable instanceof BlockBox) {
                            final BlockBox b = (BlockBox) styleable;
                            result.append(b.dump(c, indent + "  ", which));
                            if (result.charAt(result.length() - 1) == '\n') {
                                result.deleteCharAt(result.length() - 1);
                            }
                        } else {
                            result.append(indent + "  ");
                            result.append(styleable.toString());
                        }
                        if (i.hasNext()) {
                            result.append('\n');
                        }
                    }
                }
                break;
        }

        return result.toString();
    }

    public void paintListMarker(final RenderingContext c) {
        if (! getStyle().isVisible()) {
            return;
        }

        if (getStyle().isListItem()) {
            ListItemPainter.paint(c, this);
        }
    }

    public Rectangle getPaintingClipEdge(final CssContext cssCtx) {
        final Rectangle result = super.getPaintingClipEdge(cssCtx);

        // HACK Don't know how wide the list marker is (or even where it is)
        // so extend the bounding box all the way over to the left edge of
        // the canvas
        if (getStyle().isListItem()) {
            final int delta = result.x;
            result.x = 0;
            result.width += delta;
        }

        return result;
    }

    public void paintInline(final RenderingContext c) {
        if (! getStyle().isVisible()) {
            return;
        }

        getContainingLayer().paintAsLayer(c, this);
    }

    public boolean isInline() {
        final Box parent = getParent();
        return parent instanceof LineBox || parent instanceof InlineLayoutBox;
    }

    public LineBox getLineBox() {
        if (! isInline()) {
            return null;
        } else {
            Box b = getParent();
            while (! (b instanceof LineBox)) {
                b = b.getParent();
            }
            return (LineBox) b;
        }
    }

    public void paintDebugOutline(final RenderingContext c) {
        c.getOutputDevice().drawDebugOutline(c, this, FSRGBColor.RED);
    }

    public MarkerData getMarkerData() {
        return _markerData;
    }

    public void setMarkerData(final MarkerData markerData) {
        _markerData = markerData;
    }

    public void createMarkerData(final LayoutContext c) {
        if (getMarkerData() != null)
        {
            return;
        }

        final StrutMetrics strutMetrics = InlineBoxing.createDefaultStrutMetrics(c, this);

        boolean imageMarker = false;

        final MarkerData result = new MarkerData();
        result.setStructMetrics(strutMetrics);

        final CalculatedStyle style = getStyle();
        final IdentValue listStyle = style.getIdent(CSSName.LIST_STYLE_TYPE);

        final String image = style.getStringProperty(CSSName.LIST_STYLE_IMAGE);
        if (! image.equals("none")) {
            result.setImageMarker(makeImageMarker(c, strutMetrics, image));
            imageMarker = result.getImageMarker() != null;
        }

        if (listStyle != IdentValue.NONE && ! imageMarker) {
            if (listStyle == IdentValue.CIRCLE || listStyle == IdentValue.SQUARE ||
                    listStyle == IdentValue.DISC) {
                result.setGlyphMarker(makeGlyphMarker(strutMetrics));
            } else {
                result.setTextMarker(makeTextMarker(c, listStyle));
            }
        }

        setMarkerData(result);
    }

    private MarkerData.GlyphMarker makeGlyphMarker(final StrutMetrics strutMetrics) {
        final int diameter = (int) ((strutMetrics.getAscent() + strutMetrics.getDescent()) / 3);

        final MarkerData.GlyphMarker result = new MarkerData.GlyphMarker();
        result.setDiameter(diameter);
        result.setLayoutWidth(diameter * 3);

        return result;
    }


    private MarkerData.ImageMarker makeImageMarker(
            final LayoutContext c, final StrutMetrics structMetrics, final String image) {
        FSImage img = null;
        if (! image.equals("none")) 
        {
            Optional<ImageResource> resource= c.getUac().getImageResource(image);
        	
            if (!resource.isPresent())
            	return null;
            
        	img = resource.get().getImage();

            if (img != null) {
                final StrutMetrics strutMetrics = structMetrics;
                if (img.getHeight() > strutMetrics.getAscent()) {
                    img.scale(-1, (int) strutMetrics.getAscent());
                }
                final MarkerData.ImageMarker result = new MarkerData.ImageMarker();
                result.setImage(img);
                result.setLayoutWidth(img.getWidth() * 2);
                return result;
            }
        }
        return null;
    }

    private MarkerData.TextMarker makeTextMarker(final LayoutContext c, final IdentValue listStyle) {
        String text;

        final int listCounter = getListCounter();
        text = CounterFunction.createCounterText(listStyle, listCounter);

        text += ".  ";

        final int w = c.getTextRenderer().getWidth(
                c.getFontContext(),
                getStyle().getFSFont(c),
                text);

        final MarkerData.TextMarker result = new MarkerData.TextMarker();
        result.setText(text);
        result.setLayoutWidth(w);

        return result;
    }

    public int getListCounter() {
        return _listCounter;
    }

    public void setListCounter(final int listCounter) {
        _listCounter = listCounter;
    }

    public PersistentBFC getPersistentBFC() {
        return _persistentBFC;
    }

    public void setPersistentBFC(final PersistentBFC persistentBFC) {
        _persistentBFC = persistentBFC;
    }

    public Box getStaticEquivalent() {
        return _staticEquivalent;
    }

    public void setStaticEquivalent(final Box staticEquivalent) {
        _staticEquivalent = staticEquivalent;
    }

    public boolean isReplaced() {
        return _replacedElement != null;
    }

    public void calcCanvasLocation() {
        if (isFloated()) {
            final FloatManager manager = _floatedBoxData.getManager();
            final Point offset = manager.getOffset(this);
            setAbsX(manager.getMaster().getAbsX() + getX() - offset.x);
            setAbsY(manager.getMaster().getAbsY() + getY() - offset.y);
        }

        final LineBox lineBox = getLineBox();
        if (lineBox == null) {
            final Box parent = getParent();
            if (parent != null) {
                setAbsX(parent.getAbsX() + parent.getTx() + getX());
                setAbsY(parent.getAbsY() + parent.getTy() + getY());
            } else if (isStyled() && getStyle().isAbsFixedOrInlineBlockEquiv()) {
                final Box cb = getContainingBlock();
                if (cb != null) {
                    setAbsX(cb.getAbsX() + getX());
                    setAbsY(cb.getAbsY() + getY());
                }
            }
        } else {
            setAbsX(lineBox.getAbsX() + getX());
            setAbsY(lineBox.getAbsY() + getY());
        }

        if (isReplaced()) {
            final Point location = getReplacedElement().getLocation();
            if (location.x != getAbsX() || location.y != getAbsY()) {
                getReplacedElement().setLocation(getAbsX(), getAbsY());
            }
        }
    }

    public void calcInitialFloatedCanvasLocation(final LayoutContext c) {
        final Point offset = c.getBlockFormattingContext().getOffset();
        final FloatManager manager = c.getBlockFormattingContext().getFloatManager();
        setAbsX(manager.getMaster().getAbsX() + getX() - offset.x);
        setAbsY(manager.getMaster().getAbsY() + getY() - offset.y);
    }

    public void calcChildLocations() {
        super.calcChildLocations();

        if (_persistentBFC != null) {
            _persistentBFC.getFloatManager().calcFloatLocations();
        }
    }

    public boolean isNeedPageClear() {
        return _needPageClear;
    }

    public void setNeedPageClear(final boolean needPageClear) {
        _needPageClear = needPageClear;
    }


    private void alignToStaticEquivalent() {
        if (_staticEquivalent.getAbsY() != getAbsY()) {
            setY(_staticEquivalent.getAbsY() - getAbsY());
            setAbsY(_staticEquivalent.getAbsY());
        }
    }

    public void positionAbsolute(final CssContext cssCtx, final int direction) {
        final CalculatedStyle style = getStyle();

        Rectangle boundingBox = null;

        final int cbContentHeight = getContainingBlock().getContentAreaEdge(0, 0, cssCtx).height;

        if (getContainingBlock() instanceof BlockBox) {
            boundingBox = getContainingBlock().getPaddingEdge(0, 0, cssCtx);
        } else {
            boundingBox = getContainingBlock().getContentAreaEdge(0, 0, cssCtx);
        }

        if ((direction & POSITION_HORIZONTALLY) != 0) {
            setX(0);
            if (!style.isIdent(CSSName.LEFT, IdentValue.AUTO)) {
                setX((int) style.getFloatPropertyProportionalWidth(CSSName.LEFT, getContainingBlock().getContentWidth(), cssCtx));
            } else if (!style.isIdent(CSSName.RIGHT, IdentValue.AUTO)) {
                setX(boundingBox.width -
                        (int) style.getFloatPropertyProportionalWidth(CSSName.RIGHT, getContainingBlock().getContentWidth(), cssCtx) - getWidth());
            }
            setX(getX() + boundingBox.x);
        }

        if ((direction & POSITION_VERTICALLY) != 0) {
            setY(0);
            if (!style.isIdent(CSSName.TOP, IdentValue.AUTO)) {
                setY((int) style.getFloatPropertyProportionalHeight(CSSName.TOP, cbContentHeight, cssCtx));
            } else if (!style.isIdent(CSSName.BOTTOM, IdentValue.AUTO)) {
                setY(boundingBox.height -
                        (int) style.getFloatPropertyProportionalWidth(CSSName.BOTTOM, cbContentHeight, cssCtx) - getHeight());
            }

            // Can't do this before now because our containing block
            // must be completed layed out
            final int pinnedHeight = calcPinnedHeight(cssCtx);
            if (pinnedHeight != -1 && getCSSHeight(cssCtx) == -1) {
                setHeight(pinnedHeight);
                applyCSSMinMaxHeight(cssCtx);
            }

            setY(getY() + boundingBox.y);
        }

        calcCanvasLocation();

        if ((direction & POSITION_VERTICALLY) != 0 &&
                getStyle().isTopAuto() && getStyle().isBottomAuto()) {
            alignToStaticEquivalent();
        }

        calcChildLocations();
    }

    public void positionAbsoluteOnPage(final LayoutContext c) {
        if (c.isPrint() &&
                (getStyle().isForcePageBreakBefore() || isNeedPageClear())) {
            forcePageBreakBefore(c, getStyle().getIdent(CSSName.PAGE_BREAK_BEFORE), false);
            calcCanvasLocation();
            calcChildLocations();

            setNeedPageClear(false);
        }
    }

    public ReplacedElement getReplacedElement() {
        return _replacedElement;
    }

    public void setReplacedElement(final ReplacedElement replacedElement) {
        _replacedElement = replacedElement;
    }

    public void reset(final LayoutContext c) {
        super.reset(c);
        setTopMarginCalculated(false);
        setBottomMarginCalculated(false);
        setDimensionsCalculated(false);
        setMinMaxCalculated(false);
        setChildrenHeight(0);
        if (isReplaced()) {
            getReplacedElement().detach(c);
            setReplacedElement(null);
        }
        if (getChildrenContentType() == BlockBox.CONTENT_INLINE) {
            removeAllChildren();
        }

        if (isFloated()) {
            _floatedBoxData.getManager().removeFloat(this);
            _floatedBoxData.getDrawingLayer().removeFloat(this);
        }

        if (getStyle().isRunning()) {
            c.getRootLayer().removeRunningBlock(this);
        }
    }

    private int calcPinnedContentWidth(final CssContext c) {
        if (! getStyle().isIdent(CSSName.LEFT, IdentValue.AUTO) &&
                ! getStyle().isIdent(CSSName.RIGHT, IdentValue.AUTO)) {
            final Rectangle paddingEdge = getContainingBlock().getPaddingEdge(0, 0, c);

            final int left = (int) getStyle().getFloatPropertyProportionalTo(
                    CSSName.LEFT, paddingEdge.width, c);
            final int right = (int) getStyle().getFloatPropertyProportionalTo(
                    CSSName.RIGHT, paddingEdge.width, c);

            final int result = paddingEdge.width - left - right - getLeftMBP() - getRightMBP();
            return result < 0 ? 0 : result;
        }

        return -1;
    }

    private int calcPinnedHeight(final CssContext c) {
        if (! getStyle().isIdent(CSSName.TOP, IdentValue.AUTO) &&
                ! getStyle().isIdent(CSSName.BOTTOM, IdentValue.AUTO)) {
            final Rectangle paddingEdge = getContainingBlock().getPaddingEdge(0, 0, c);

            final int top = (int) getStyle().getFloatPropertyProportionalTo(
                    CSSName.TOP, paddingEdge.height, c);
            final int bottom = (int) getStyle().getFloatPropertyProportionalTo(
                    CSSName.BOTTOM, paddingEdge.height, c);


            final int result = paddingEdge.height - top - bottom;
            return result < 0 ? 0 : result;
        }

        return -1;
    }

    protected void resolveAutoMargins(
            final LayoutContext c, final int cssWidth,
            final RectPropertySet padding, final BorderPropertySet border) {
        final int withoutMargins =
                (int) border.left() + (int) padding.left() +
                        cssWidth +
                        (int) padding.right() + (int) border.right();
        if (withoutMargins < getContainingBlockWidth()) {
            final int available = getContainingBlockWidth() - withoutMargins;

            final boolean autoLeft = getStyle().isAutoLeftMargin();
            final boolean autoRight = getStyle().isAutoRightMargin();

            if (autoLeft && autoRight) {
                setMarginLeft(c, available / 2);
                setMarginRight(c, available / 2);
            } else if (autoLeft) {
                setMarginLeft(c, available);
            } else if (autoRight) {
                setMarginRight(c, available);
            }
        }
    }

    private int calcEffPageRelativeWidth(final LayoutContext c) {
        int totalLeftMBP = 0;
        int totalRightMBP = 0;

        boolean usePageRelativeWidth = true;

        Box current = this;
        while (true) {
            final CalculatedStyle style = current.getStyle();
            if (style.isAutoWidth() && ! style.isCanBeShrunkToFit()) {
                totalLeftMBP += current.getLeftMBP();
                totalRightMBP += current.getRightMBP();
            } else {
                usePageRelativeWidth = false;
                break;
            }

            if (current.getContainingBlock().isInitialContainingBlock()) {
                break;
            } else {
                current = current.getContainingBlock();
            }
        }

        if (usePageRelativeWidth) {
            final PageBox currentPage = c.getRootLayer().getFirstPage(c, this);
            return currentPage.getContentWidth(c) - totalLeftMBP - totalRightMBP;
        } else {
            return getContainingBlockWidth() - getLeftMBP() - getRightMBP();
        }
    }

    public void calcDimensions(final LayoutContext c) {
        calcDimensions(c, getCSSWidth(c));
    }

    protected void calcDimensions(final LayoutContext c, final int cssWidth) {
        if (! isDimensionsCalculated()) {
            final CalculatedStyle style = getStyle();

            final RectPropertySet padding = getPadding(c);
            final BorderPropertySet border = getBorder(c);

            if (cssWidth != -1 && !isAnonymous() &&
                    (getStyle().isIdent(CSSName.MARGIN_LEFT, IdentValue.AUTO) ||
                            getStyle().isIdent(CSSName.MARGIN_RIGHT, IdentValue.AUTO)) &&
                    getStyle().isNeedAutoMarginResolution()) {
                resolveAutoMargins(c, cssWidth, padding, border);
            }

            recalcMargin(c);
            final RectPropertySet margin = getMargin(c);

            // CLEAN: cast to int
            setLeftMBP((int) margin.left() + (int) border.left() + (int) padding.left());
            setRightMBP((int) padding.right() + (int) border.right() + (int) margin.right());
            if (c.isPrint() && getStyle().isDynamicAutoWidth()) {
                setContentWidth(calcEffPageRelativeWidth(c));
            } else {
                setContentWidth((getContainingBlockWidth() - getLeftMBP() - getRightMBP()));
            }
            setHeight(0);

            if (! isAnonymous() || (isFromCaptionedTable() && isFloated())) {
                int pinnedContentWidth = -1;

                if (cssWidth != -1) {
                    setContentWidth(cssWidth);
                } else if (getStyle().isAbsolute() || getStyle().isFixed()) {
                    pinnedContentWidth = calcPinnedContentWidth(c);
                    if (pinnedContentWidth != -1) {
                        setContentWidth(pinnedContentWidth);
                    }
                }

                final int cssHeight = getCSSHeight(c);
                if (cssHeight != -1) {
                    setHeight(cssHeight);
                }

                //check if replaced
                ReplacedElement re = getReplacedElement();
                if (re == null) {
                    re = c.getReplacedElementFactory().createReplacedElement(
                            c, this, c.getUac(), cssWidth, cssHeight);
                    if (re != null){
                        re = fitReplacedElement(c, re);
                    }
                }
                if (re != null) {
                    setContentWidth(re.getIntrinsicWidth());
                    setHeight(re.getIntrinsicHeight());
                    setReplacedElement(re);
                } else if (cssWidth == -1 && pinnedContentWidth == -1 &&
                        style.isCanBeShrunkToFit()) {
                    setNeedShrinkToFitCalculatation(true);
                }

                if (! isReplaced()) {
                    applyCSSMinMaxWidth(c);
                }
            }

            setDimensionsCalculated(true);
        }
    }

    private void calcClearance(final LayoutContext c) {
        if (getStyle().isCleared() && ! getStyle().isFloated()) {
            c.translate(0, -getY());
            c.getBlockFormattingContext().clear(c, this);
            c.translate(0, getY());
            calcCanvasLocation();
        }
    }

    private void calcExtraPageClearance(final LayoutContext c) {
        if (c.isPageBreaksAllowed() &&
                c.getExtraSpaceTop() > 0 && (getStyle().isSpecifiedAsBlock() || getStyle().isListItem())) {
            final PageBox first = c.getRootLayer().getFirstPage(c, this);
            if (first != null && first.getTop() + c.getExtraSpaceTop() > getAbsY()) {
                final int diff = first.getTop() + c.getExtraSpaceTop() - getAbsY();
                setY(getY() + diff);
                c.translate(0, diff);
                calcCanvasLocation();
            }
        }
    }

    private void addBoxID(final LayoutContext c) {
        if (! isAnonymous()) {
            final Optional<String> name = c.getNamespaceHandler().getAnchorName(getElement());
            if (name.isPresent()) {
                c.addBoxId(name.get(), this);
            }

            final Optional<String> id = c.getNamespaceHandler().getID(getElement());
            if (id.isPresent()) {
            	c.addBoxId(id.get(), this);
            }
        }
    }

    public void layout(final LayoutContext c) {
        layout(c, 0);
    }

    public void layout(final LayoutContext c, final int contentStart) {
        final CalculatedStyle style = getStyle();

        boolean pushedLayer = false;
        if (isRoot() || style.requiresLayer()) {
            pushedLayer = true;
            if (getLayer() == null) {
                c.pushLayer(this);
            } else {
                c.pushLayer(getLayer());
            }
        }

        if (style.isFixedBackground()) {
            c.getRootLayer().setFixedBackground(true);
        }

        calcClearance(c);

        if (isRoot() || getStyle().establishesBFC() || isMarginAreaRoot()) {
            final BlockFormattingContext bfc = new BlockFormattingContext(this, c);
            c.pushBFC(bfc);
        }

        addBoxID(c);

        if (c.isPrint() && getStyle().isIdent(CSSName.FS_PAGE_SEQUENCE, IdentValue.START)) {
            c.getRootLayer().addPageSequence(this);
        }

        calcDimensions(c);
        calcShrinkToFitWidthIfNeeded(c);
        collapseMargins(c);

        calcExtraPageClearance(c);

        if (c.isPrint()) {
            final PageBox firstPage = c.getRootLayer().getFirstPage(c, this);
            if (firstPage != null && firstPage.getTop() == getAbsY() - getPageClearance()) {
                resetTopMargin(c);
            }
        }

        final BorderPropertySet border = getBorder(c);
        final RectPropertySet margin = getMargin(c);
        final RectPropertySet padding = getPadding(c);

        // save height in case fixed height
        final int originalHeight = getHeight();

        if (! isReplaced()) {
            setHeight(0);
        }

        boolean didSetMarkerData = false;
        if (getStyle().isListItem()) {
            createMarkerData(c);
            c.setCurrentMarkerData(getMarkerData());
            didSetMarkerData = true;
        }

        // do children's layout
        final int tx = (int) margin.left() + (int) border.left() + (int) padding.left();
        final int ty = (int) margin.top() + (int) border.top() + (int) padding.top();
        setTx(tx);
        setTy(ty);
        c.translate(getTx(), getTy());
        if (! isReplaced())
            layoutChildren(c, contentStart);
        else {
            setState(Box.DONE);
        }
        c.translate(-getTx(), -getTy());

        setChildrenHeight(getHeight());

        if (! isReplaced()) {
            if (! isAutoHeight()) {
                final int delta = originalHeight - getHeight();
                if (delta > 0 || isAllowHeightToShrink()) {
                    setHeight(originalHeight);
                }
            }

            applyCSSMinMaxHeight(c);
        }

        if (isRoot() || getStyle().establishesBFC()) {
            if (getStyle().isAutoHeight()) {
                final int delta =
                        c.getBlockFormattingContext().getFloatManager().getClearDelta(
                                c, getTy() + getHeight());
                if (delta > 0) {
                    setHeight(getHeight() + delta);
                    setChildrenHeight(getChildrenHeight() + delta);
                }
            }
        }

        if (didSetMarkerData) {
            c.setCurrentMarkerData(null);
        }

        calcLayoutHeight(c, border, margin, padding);

        if (isRoot() || getStyle().establishesBFC()) {
            c.popBFC();
        }

        if (pushedLayer) {
            c.popLayer();
        }
    }

    protected boolean isAllowHeightToShrink() {
        return true;
    }

    protected int getPageClearance() {
        return 0;
    }

    protected void calcLayoutHeight(
            final LayoutContext c, final BorderPropertySet border,
            final RectPropertySet margin, final RectPropertySet padding) {
        setHeight(getHeight() + ((int) margin.top() + (int) border.top() + (int) padding.top() +
                (int) padding.bottom() + (int) border.bottom() + (int) margin.bottom()));
        setChildrenHeight(getChildrenHeight() + ((int) margin.top() + (int) border.top() + (int) padding.top() +
                (int) padding.bottom() + (int) border.bottom() + (int) margin.bottom()));
    }


    private void calcShrinkToFitWidthIfNeeded(final LayoutContext c) {
        if (isNeedShrinkToFitCalculatation()) {
            setContentWidth(calcShrinkToFitWidth(c) - getLeftMBP() - getRightMBP());
            applyCSSMinMaxWidth(c);
            setNeedShrinkToFitCalculatation(false);
        }
    }

    private void applyCSSMinMaxWidth(final CssContext c) {
        if (! getStyle().isMaxWidthNone()) {
            final int cssMaxWidth = getCSSMaxWidth(c);
            if (getContentWidth() > cssMaxWidth) {
                setContentWidth(cssMaxWidth);
            }
        }
        final int cssMinWidth = getCSSMinWidth(c);
        if (cssMinWidth > 0 && getContentWidth() < cssMinWidth) {
            setContentWidth(cssMinWidth);
        }
    }

    private void applyCSSMinMaxHeight(final CssContext c) {
        if (! getStyle().isMaxHeightNone()) {
            final int cssMaxHeight = getCSSMaxHeight(c);
            if (getHeight() > cssMaxHeight) {
                setHeight(cssMaxHeight);
            }
        }
        final int cssMinHeight = getCSSMinHeight(c);
        if (cssMinHeight > 0 && getHeight() < cssMinHeight) {
            setHeight(cssMinHeight);
        }
    }

    public void ensureChildren(final LayoutContext c) {
        if (getChildrenContentType() == CONTENT_UNKNOWN) {
            BoxBuilder.createChildren(c, this);
        }
    }

    protected void layoutChildren(final LayoutContext c, final int contentStart) {
        setState(Box.CHILDREN_FLUX);
        ensureChildren(c);

        if (getFirstLetterStyle() != null) {
            c.getFirstLettersTracker().addStyle(getFirstLetterStyle());
        }
        if (getFirstLineStyle() != null) {
            c.getFirstLinesTracker().addStyle(getFirstLineStyle());
        }

        switch (getChildrenContentType()) {
            case CONTENT_INLINE:
                layoutInlineChildren(c, contentStart, calcInitialBreakAtLine(c), true);
                break;
            case CONTENT_BLOCK:
                BlockBoxing.layoutContent(c, this, contentStart);
                break;
        }

        if (getFirstLetterStyle() != null) {
            c.getFirstLettersTracker().removeLast();
        }
        if (getFirstLineStyle() != null) {
            c.getFirstLinesTracker().removeLast();
        }

        setState(Box.DONE);
    }

    protected void layoutInlineChildren(
            final LayoutContext c, final int contentStart, final int breakAtLine, final boolean tryAgain) {
        InlineBoxing.layoutContent(c, this, contentStart, breakAtLine);

        if (c.isPrint() && c.isPageBreaksAllowed() && getChildCount() > 1) {
            satisfyWidowsAndOrphans(c, contentStart, tryAgain);
        }

        if (tryAgain && getStyle().isTextJustify()) {
            justifyText();
        }
    }

    private void justifyText() {
        for (final Iterator<Box> i = getChildIterator(); i.hasNext(); ) {
            final LineBox line = (LineBox)i.next();
            line.justify();
        }
    }

    private void satisfyWidowsAndOrphans(final LayoutContext c, final int contentStart, final boolean tryAgain) {
        final LineBox firstLineBox = (LineBox)getChild(0);
        final PageBox firstPage = c.getRootLayer().getFirstPage(c, firstLineBox);

        if (firstPage == null) {
            return;
        }

        int noContentLBs = 0;
        int i = 0;
        final int cCount = getChildCount();
        while (i < cCount) {
            final LineBox lB = (LineBox)getChild(i);
            if (lB.getAbsY() >= firstPage.getBottom()) {
                break;
            }
            if (! lB.isContainsContent()) {
                noContentLBs++;
            }
            i++;
        }

        if (i != cCount) {
            final int orphans = (int)getStyle().asFloat(CSSName.ORPHANS);
            if (i - noContentLBs < orphans) {
                setNeedPageClear(true);
            } else {
                final LineBox lastLineBox = (LineBox)getChild(cCount-1);
                final List<PageBox> pages = c.getRootLayer().getPages();
                PageBox lastPage = (PageBox)pages.get(firstPage.getPageNo()+1);
                while (lastPage.getPageNo() != pages.size() - 1 &&
                        lastPage.getBottom() < lastLineBox.getAbsY()) {
                    lastPage = (PageBox)pages.get(lastPage.getPageNo()+1);
                }

                noContentLBs = 0;
                i = cCount-1;
                while (i >= 0 && ((LineBox)getChild(i)).getAbsY() >= lastPage.getTop()) {
                    final LineBox lB = (LineBox)getChild(i);
                    if (lB.getAbsY() < lastPage.getTop()) {
                        break;
                    }
                    if (! lB.isContainsContent()) {
                        noContentLBs++;
                    }
                    i--;
                }

                final int widows = (int)getStyle().asFloat(CSSName.WIDOWS);
                if (cCount - 1 - i - noContentLBs < widows) {
                    if (cCount - 1 - widows < orphans) {
                        setNeedPageClear(true);
                    } else if (tryAgain) {
                        final int breakAtLine = cCount - 1 - widows;

                        resetChildren(c);
                        removeAllChildren();

                        layoutInlineChildren(c, contentStart, breakAtLine, false);
                    }
                }
            }
        }
    }

    public int getChildrenContentType() {
        return _childrenContentType;
    }

    public void setChildrenContentType(final int contentType) {
        _childrenContentType = contentType;
    }

    public List<Styleable> getInlineContent() {
        return _inlineContent;
    }

    public void setInlineContent(final List<Styleable> inlineContent) {
        _inlineContent = inlineContent;
        if (inlineContent != null) {
            for (final Iterator<Styleable> i = inlineContent.iterator(); i.hasNext();) {
                final Styleable child = (Styleable) i.next();
                if (child instanceof Box) {
                    ((Box) child).setContainingBlock(this);
                }
            }
        }
    }

    protected boolean isSkipWhenCollapsingMargins() {
        return false;
    }

    protected boolean isMayCollapseMarginsWithChildren() {
        return (! isRoot()) && getStyle().isMayCollapseMarginsWithChildren();
    }

    // This will require a rethink if we ever truly layout incrementally
    // Should only ever collapse top margin and pick up collapsable
    // bottom margins by looking back up the tree.
    private void collapseMargins(final LayoutContext c) {
        if (! isTopMarginCalculated() || ! isBottomMarginCalculated()) {
            recalcMargin(c);
            final RectPropertySet margin = getMargin(c);

            if (! isTopMarginCalculated() && ! isBottomMarginCalculated() && isVerticalMarginsAdjoin(c)) {
                final MarginCollapseResult collapsedMargin =
                        _pendingCollapseCalculation != null ?
                                _pendingCollapseCalculation : new MarginCollapseResult();
                collapseEmptySubtreeMargins(c, collapsedMargin);
                setCollapsedBottomMargin(c, margin, collapsedMargin);
            } else {
                if (! isTopMarginCalculated()) {
                    final MarginCollapseResult collapsedMargin =
                            _pendingCollapseCalculation != null ?
                                    _pendingCollapseCalculation : new MarginCollapseResult();

                    collapseTopMargin(c, true, collapsedMargin);
                    if ((int) margin.top() != collapsedMargin.getMargin()) {
                        setMarginTop(c, collapsedMargin.getMargin());
                    }
                }

                if (! isBottomMarginCalculated()) {
                    final MarginCollapseResult collapsedMargin = new MarginCollapseResult();
                    collapseBottomMargin(c, true, collapsedMargin);

                    setCollapsedBottomMargin(c, margin, collapsedMargin);
                }
            }
        }
    }

    private void setCollapsedBottomMargin(final LayoutContext c, final RectPropertySet margin, final MarginCollapseResult collapsedMargin) {
        BlockBox next = null;
        if (! isInline()) {
            next = getNextCollapsableSibling(collapsedMargin);
        }
        if (! (next == null || next instanceof AnonymousBlockBox) &&
                collapsedMargin.hasMargin()) {
            next._pendingCollapseCalculation = collapsedMargin;
            setMarginBottom(c, 0);
        } else if ((int) margin.bottom() != collapsedMargin.getMargin()) {
            setMarginBottom(c, collapsedMargin.getMargin());
        }
    }

    private BlockBox getNextCollapsableSibling(final MarginCollapseResult collapsedMargin) {
        BlockBox next = (BlockBox) getNextSibling();
        while (next != null) {
            if (next instanceof AnonymousBlockBox) {
                ((AnonymousBlockBox) next).provideSiblingMarginToFloats(
                        collapsedMargin.getMargin());
            }
            if (! next.isSkipWhenCollapsingMargins()) {
                break;
            } else {
                next = (BlockBox) next.getNextSibling();
            }
        }
        return next;
    }

    private void collapseTopMargin(
            final LayoutContext c, final boolean calculationRoot, final MarginCollapseResult result) {
        if (! isTopMarginCalculated()) {
            if (! isSkipWhenCollapsingMargins()) {
                calcDimensions(c);
                if (c.isPrint() && getStyle().isDynamicAutoWidthApplicable()) {
                    // Force recalculation once box is positioned
                    setDimensionsCalculated(false);
                }
                final RectPropertySet margin = getMargin(c);
                result.update((int) margin.top());

                if (! calculationRoot && (int) margin.top() != 0) {
                    setMarginTop(c, 0);
                }

                if (isMayCollapseMarginsWithChildren() && isNoTopPaddingOrBorder(c)) {
                    ensureChildren(c);
                    if (getChildrenContentType() == CONTENT_BLOCK) {
                        for (final Iterator<Box> i = getChildIterator(); i.hasNext();) {
                            final BlockBox child = (BlockBox) i.next();
                            child.collapseTopMargin(c, false, result);

                            if (child.isSkipWhenCollapsingMargins()) {
                                continue;
                            }

                            break;
                        }
                    }
                }
            }

            setTopMarginCalculated(true);
        }
    }

    private void collapseBottomMargin(
            final LayoutContext c, final boolean calculationRoot, final MarginCollapseResult result) {
        if (! isBottomMarginCalculated()) {
            if (! isSkipWhenCollapsingMargins()) {
                calcDimensions(c);
                if (c.isPrint() && getStyle().isDynamicAutoWidthApplicable()) {
                    // Force recalculation once box is positioned
                    setDimensionsCalculated(false);
                }
                final RectPropertySet margin = getMargin(c);
                result.update((int) margin.bottom());

                if (! calculationRoot && (int) margin.bottom() != 0) {
                    setMarginBottom(c, 0);
                }

                if (isMayCollapseMarginsWithChildren() &&
                        ! getStyle().isTable() && isNoBottomPaddingOrBorder(c)) {
                    ensureChildren(c);
                    if (getChildrenContentType() == CONTENT_BLOCK) {
                        for (int i = getChildCount() - 1; i >= 0; i--) {
                            final BlockBox child = (BlockBox) getChild(i);

                            if (child.isSkipWhenCollapsingMargins()) {
                                continue;
                            }

                            child.collapseBottomMargin(c, false, result);

                            break;
                        }
                    }
                }
            }

            setBottomMarginCalculated(true);
        }
    }

    private boolean isNoTopPaddingOrBorder(final LayoutContext c) {
        final RectPropertySet padding = getPadding(c);
        final BorderPropertySet border = getBorder(c);

        return (int) padding.top() == 0 && (int) border.top() == 0;
    }

    private boolean isNoBottomPaddingOrBorder(final LayoutContext c) {
        final RectPropertySet padding = getPadding(c);
        final BorderPropertySet border = getBorder(c);

        return (int) padding.bottom() == 0 && (int) border.bottom() == 0;
    }

    private void collapseEmptySubtreeMargins(final LayoutContext c, final MarginCollapseResult result) {
        final RectPropertySet margin = getMargin(c);
        result.update((int) margin.top());
        result.update((int) margin.bottom());

        setMarginTop(c, 0);
        setTopMarginCalculated(true);
        setMarginBottom(c, 0);
        setBottomMarginCalculated(true);

        ensureChildren(c);
        if (getChildrenContentType() == CONTENT_BLOCK) {
            for (final Iterator<Box> i = getChildIterator(); i.hasNext();) {
                final BlockBox child = (BlockBox) i.next();
                child.collapseEmptySubtreeMargins(c, result);
            }
        }
    }

    private boolean isVerticalMarginsAdjoin(final LayoutContext c) {
        final CalculatedStyle style = getStyle();

        final BorderPropertySet borderWidth = style.getBorder(c);
        final RectPropertySet padding = getPadding(c);

        final boolean bordersOrPadding =
                (int) borderWidth.top() != 0 || (int) borderWidth.bottom() != 0 ||
                        (int) padding.top() != 0 || (int) padding.bottom() != 0;

        if (bordersOrPadding) {
            return false;
        }

        ensureChildren(c);

        if (getChildrenContentType() == CONTENT_INLINE) 
        {
            return false;
        }
        else if (getChildrenContentType() == CONTENT_BLOCK) 
        {
        	for (Box child : getChildren())
        	{
        		BlockBox bb = (BlockBox) child;
        		
        		if (bb.isSkipWhenCollapsingMargins() || 
        			!bb.isVerticalMarginsAdjoin(c))
        			return false;
        	}
        }

        return style.asFloat(CSSName.MIN_HEIGHT) == 0 &&
                (isAutoHeight() || style.asFloat(CSSName.HEIGHT) == 0);
    }

    public boolean isTopMarginCalculated() {
        return _topMarginCalculated;
    }

    public void setTopMarginCalculated(final boolean topMarginCalculated) {
        _topMarginCalculated = topMarginCalculated;
    }

    public boolean isBottomMarginCalculated() {
        return _bottomMarginCalculated;
    }

    public void setBottomMarginCalculated(final boolean bottomMarginCalculated) {
        _bottomMarginCalculated = bottomMarginCalculated;
    }

    protected int getCSSWidth(final CssContext c) {
        return getCSSWidth(c, false);
    }

    protected int getCSSWidth(final CssContext c, final boolean shrinkingToFit) {
        if (! isAnonymous()) {
            if (! getStyle().isAutoWidth()) {
                if (shrinkingToFit && ! getStyle().isAbsoluteWidth()) {
                    return -1;
                } else {
                    final int result = (int) getStyle().getFloatPropertyProportionalWidth(
                            CSSName.WIDTH, getContainingBlock().getContentWidth(), c);
                    return result >= 0 ? result : -1;
                }
            }
        }

        return -1;
    }

    protected int getCSSFitToWidth(final CssContext c) {
        if (! isAnonymous()) {
            if (! getStyle().isIdent(CSSName.FS_FIT_IMAGES_TO_WIDTH, IdentValue.AUTO))
            {
                final int result = (int) getStyle().getFloatPropertyProportionalWidth(
                        CSSName.FS_FIT_IMAGES_TO_WIDTH, getContainingBlock().getContentWidth(), c);
                return result >= 0 ? result : -1;
            }
        }

        return -1;
    }

    protected int getCSSHeight(final CssContext c) {
        if (! isAnonymous()) {
            if (! isAutoHeight()) {
                if (getStyle().hasAbsoluteUnit(CSSName.HEIGHT)) {
                    return (int)getStyle().getFloatPropertyProportionalHeight(CSSName.HEIGHT, 0, c);
                } else {
                    return (int)getStyle().getFloatPropertyProportionalHeight(
                            CSSName.HEIGHT,
                            ((BlockBox)getContainingBlock()).getCSSHeight(c),
                            c);
                }
            }
        }

        return -1;
    }

    public boolean isAutoHeight() {
        if (getStyle().isAutoHeight()) {
            return true;
        } else if (getStyle().hasAbsoluteUnit(CSSName.HEIGHT)) {
            return false;
        } else {
            // We have a percentage height, defer to our block parent (if applicable)
            final Box cb = getContainingBlock();
            if (cb.isStyled() && (cb instanceof BlockBox)) {
                return ((BlockBox)cb).isAutoHeight();
            } else if (cb instanceof BlockBox && ((BlockBox)cb).isInitialContainingBlock()) {
                return false;
            } else {
                return true;
            }
        }
    }

    private int getCSSMinWidth(final CssContext c) {
        return getStyle().getMinWidth(c, getContainingBlockWidth());
    }

    private int getCSSMaxWidth(final CssContext c) {
        return getStyle().getMaxWidth(c, getContainingBlockWidth());
    }

    private int getCSSMinHeight(final CssContext c) {
        return getStyle().getMinHeight(c, getContainingBlockCSSHeight(c));
    }

    private int getCSSMaxHeight(final CssContext c) {
        return getStyle().getMaxHeight(c, getContainingBlockCSSHeight(c));
    }

    // Use only when the height of the containing block is required for
    // resolving percentage values.  Does not represent the actual (resolved) height
    // of the containing block.
    private int getContainingBlockCSSHeight(final CssContext c) {
        if (! getContainingBlock().isStyled() ||
                getContainingBlock().getStyle().isAutoHeight()) {
            return 0;
        } else {
            if (getContainingBlock().getStyle().hasAbsoluteUnit(CSSName.HEIGHT)) {
                return (int) getContainingBlock().getStyle().getFloatPropertyProportionalTo(
                        CSSName.HEIGHT, 0, c);
            } else {
                return 0;
            }
        }
    }

    private int calcShrinkToFitWidth(final LayoutContext c) {
        calcMinMaxWidth(c);

        return Math.min(Math.max(getMinWidth(), getAvailableWidth(c)), getMaxWidth());
    }

    protected int getAvailableWidth(final LayoutContext c) {
        if (! getStyle().isAbsolute()) {
            return getContainingBlockWidth();
        } else {
            int left = 0;
            int right = 0;
            if (! getStyle().isIdent(CSSName.LEFT, IdentValue.AUTO)) {
                left =
                        (int) getStyle().getFloatPropertyProportionalTo(CSSName.LEFT,
                                getContainingBlock().getContentWidth(), c);
            }

            if (! getStyle().isIdent(CSSName.RIGHT, IdentValue.AUTO)) {
                right =
                        (int) getStyle().getFloatPropertyProportionalTo(CSSName.RIGHT,
                                getContainingBlock().getContentWidth(), c);
            }

            return getContainingBlock().getPaddingWidth(c) - left - right;
        }
    }

    protected boolean isFixedWidthAdvisoryOnly() {
        return false;
    }


    private void recalcMargin(final LayoutContext c) {
        if (isTopMarginCalculated() && isBottomMarginCalculated()) {
            return;
        }

        // Check if we're a potential candidate upfront to avoid expensive
        // getStyleMargin(c, false) call
        final FSDerivedValue topMargin = getStyle().valueByName(CSSName.MARGIN_TOP);
        final boolean resetTop = topMargin instanceof LengthValue && ! topMargin.hasAbsoluteUnit();

        final FSDerivedValue bottomMargin = getStyle().valueByName(CSSName.MARGIN_BOTTOM);
        final boolean resetBottom = bottomMargin instanceof LengthValue && ! bottomMargin.hasAbsoluteUnit();

        if (! resetTop && ! resetBottom) {
            return;
        }

        final RectPropertySet styleMargin = getStyleMargin(c, false);
        final RectPropertySet workingMargin = getMargin(c);

        // A shrink-to-fit calculation may have set incorrect values for
        // percentage margins (as the containing block width
        // hasn't been calculated yet).  Reset top and bottom margins
        // in this case.
        if (! isTopMarginCalculated() &&
                styleMargin.top() != workingMargin.top()) {
            setMarginTop(c, (int) styleMargin.top());
        }

        if (! isBottomMarginCalculated() &&
                styleMargin.bottom() != workingMargin.bottom()) {
            setMarginBottom(c, (int) styleMargin.bottom());
        }
    }

    public void calcMinMaxWidth(final LayoutContext c) {
        if (! isMinMaxCalculated()) {
            final RectPropertySet margin = getMargin(c);
            final BorderPropertySet border = getBorder(c);
            final RectPropertySet padding = getPadding(c);

            int width = getCSSWidth(c, true);

            if (width == -1) {
                if (isReplaced()) {
                    width = getReplacedElement().getIntrinsicWidth();
                } else {
                    final int height = getCSSHeight(c);
                    ReplacedElement re = c.getReplacedElementFactory().createReplacedElement(
                            c, this, c.getUac(), width, height);
                    if (re != null) {
                        re = fitReplacedElement(c, re);
                        setReplacedElement(re);
                        width = getReplacedElement().getIntrinsicWidth();
                    }
                }
            }

            if (isReplaced() || (width != -1 && ! isFixedWidthAdvisoryOnly())) {
                _minWidth = _maxWidth =
                        (int) margin.left() + (int) border.left() + (int) padding.left() +
                                width +
                                (int) margin.right() + (int) border.right() + (int) padding.right();
            } else {
                int cw = -1;
                if (width != -1) {
                    // Set a provisional content width on table cells so
                    // percentage values resolve correctly (but save and reset
                    // the existing value)
                    cw = getContentWidth();
                    setContentWidth(width);
                }

                _minWidth = _maxWidth =
                        (int) margin.left() + (int) border.left() + (int) padding.left() +
                                (int) margin.right() + (int) border.right() + (int) padding.right();

                int minimumMaxWidth = _maxWidth;
                if (width != -1) {
                    minimumMaxWidth += width;
                }

                ensureChildren(c);

                if (getChildrenContentType() == CONTENT_BLOCK ||
                        getChildrenContentType() == CONTENT_INLINE) {
                    switch (getChildrenContentType()) {
                        case CONTENT_BLOCK:
                            calcMinMaxWidthBlockChildren(c);
                            break;
                        case CONTENT_INLINE:
                            calcMinMaxWidthInlineChildren(c);
                            break;
                    }
                }

                if (minimumMaxWidth > _maxWidth) {
                    _maxWidth = minimumMaxWidth;
                }

                if (cw != -1) {
                    setContentWidth(cw);
                }
            }

            if (! isReplaced()) {
                calcMinMaxCSSMinMaxWidth(c, margin, border, padding);
            }

            setMinMaxCalculated(true);
        }
    }

    private ReplacedElement fitReplacedElement(final LayoutContext c,
            ReplacedElement re)
    {
        final int maxImageWidth = getCSSFitToWidth(c);
        if (maxImageWidth > -1 && re.getIntrinsicWidth() > maxImageWidth)
        {
            final double oldWidth = (double)re.getIntrinsicWidth();
            final double scale = ((double)maxImageWidth)/oldWidth;
            re = c.getReplacedElementFactory().createReplacedElement(
                    c, this, c.getUac(), maxImageWidth, (int)Math.rint(scale * (double)re.getIntrinsicHeight()));
        }
        return re;
    }

    private void calcMinMaxCSSMinMaxWidth(
            final LayoutContext c, final RectPropertySet margin, final BorderPropertySet border,
            final RectPropertySet padding) {
        int cssMinWidth = getCSSMinWidth(c);
        if (cssMinWidth > 0) {
            cssMinWidth +=
                    (int) margin.left() + (int) border.left() + (int) padding.left() +
                            (int) margin.right() + (int) border.right() + (int) padding.right();
            if (_minWidth < cssMinWidth) {
                _minWidth = cssMinWidth;
            }
        }
        if (! getStyle().isMaxWidthNone()) {
            int cssMaxWidth = getCSSMaxWidth(c);
            cssMaxWidth +=
                    (int) margin.left() + (int) border.left() + (int) padding.left() +
                            (int) margin.right() + (int) border.right() + (int) padding.right();
            if (_maxWidth > cssMaxWidth) {
                if (cssMaxWidth > _minWidth) {
                    _maxWidth = cssMaxWidth;
                } else {
                    _maxWidth = _minWidth;
                }
            }
        }
    }

    private void calcMinMaxWidthBlockChildren(final LayoutContext c) {
        int childMinWidth = 0;
        int childMaxWidth = 0;

        for (final Iterator<Box> i = getChildIterator(); i.hasNext();) {
            final BlockBox child = (BlockBox) i.next();
            child.calcMinMaxWidth(c);
            if (child.getMinWidth() > childMinWidth) {
                childMinWidth = child.getMinWidth();
            }
            if (child.getMaxWidth() > childMaxWidth) {
                childMaxWidth = child.getMaxWidth();
            }
        }

        _minWidth += childMinWidth;
        _maxWidth += childMaxWidth;
    }

    private void calcMinMaxWidthInlineChildren(final LayoutContext c) {
        int textIndent = (int) getStyle().getFloatPropertyProportionalWidth(
                CSSName.TEXT_INDENT, getContentWidth(), c);

        if (getStyle().isListItem() && getStyle().isListMarkerInside()) {
            createMarkerData(c);
            textIndent += getMarkerData().getLayoutWidth();
        }

        int childMinWidth = 0;
        int childMaxWidth = 0;
        int lineWidth = 0;

        InlineBox trimmableIB = null;

        for (final Iterator<Styleable> i = _inlineContent.iterator(); i.hasNext();) {
            final Styleable child = (Styleable) i.next();

            if (child.getStyle().isAbsolute() || child.getStyle().isFixed() || child.getStyle().isRunning()) {
                continue;
            }

            if (child.getStyle().isFloated() || child.getStyle().isInlineBlock() ||
                    child.getStyle().isInlineTable()) {
                if (child.getStyle().isFloated() && child.getStyle().isCleared()) {
                    if (trimmableIB != null) {
                        lineWidth -= trimmableIB.getTrailingSpaceWidth(c);
                    }
                    if (lineWidth > childMaxWidth) {
                        childMaxWidth = lineWidth;
                    }
                    lineWidth = 0;
                }
                trimmableIB = null;
                final BlockBox block = (BlockBox) child;
                block.calcMinMaxWidth(c);
                lineWidth += block.getMaxWidth();
                if (block.getMinWidth() > childMinWidth) {
                    childMinWidth = block.getMinWidth();
                }
            } else { /* child.getStyle().isInline() */
                final InlineBox iB = (InlineBox) child;
                final IdentValue whitespace = iB.getStyle().getWhitespace();

                iB.calcMinMaxWidth(c, getContentWidth(), lineWidth == 0);

                if (whitespace == IdentValue.NOWRAP) {
                    lineWidth += textIndent + iB.getMaxWidth();
                    if (iB.getMinWidth() > childMinWidth) {
                        childMinWidth = iB.getMinWidth();
                    }
                    trimmableIB = iB;
                } else if (whitespace == IdentValue.PRE) {
                    if (trimmableIB != null) {
                        lineWidth -= trimmableIB.getTrailingSpaceWidth(c);
                    }
                    trimmableIB = null;
                    if (lineWidth > childMaxWidth) {
                        childMaxWidth = lineWidth;
                    }
                    lineWidth = textIndent + iB.getFirstLineWidth();
                    if (lineWidth > childMinWidth) {
                        childMinWidth = lineWidth;
                    }
                    lineWidth = iB.getMaxWidth();
                    if (lineWidth > childMinWidth) {
                        childMinWidth = lineWidth;
                    }
                    if (childMinWidth > childMaxWidth) {
                        childMaxWidth = childMinWidth;
                    }
                    lineWidth = 0;
                } else if (whitespace == IdentValue.PRE_WRAP || whitespace == IdentValue.PRE_LINE) {
                    lineWidth += textIndent + iB.getFirstLineWidth();
                    if (trimmableIB != null) {
                        lineWidth -= trimmableIB.getTrailingSpaceWidth(c);
                    }
                    if (lineWidth > childMaxWidth) {
                        childMaxWidth = lineWidth;
                    }

                    if (iB.getMaxWidth() > childMaxWidth) {
                        childMaxWidth = iB.getMaxWidth();
                    }
                    if (iB.getMinWidth() > childMinWidth) {
                        childMinWidth = iB.getMinWidth();
                    }
                    if (whitespace == IdentValue.PRE_LINE) {
                        trimmableIB = iB;
                    } else {
                        trimmableIB = null;
                    }
                    lineWidth = 0;
                } else /* if (whitespace == IdentValue.NORMAL) */ {
                    lineWidth += textIndent + iB.getMaxWidth();
                    if (iB.getMinWidth() > childMinWidth) {
                        childMinWidth = textIndent + iB.getMinWidth();
                    }
                    trimmableIB = iB;
                }

                if (textIndent > 0) {
                    textIndent = 0;
                }
            }
        }

        if (trimmableIB != null) {
            lineWidth -= trimmableIB.getTrailingSpaceWidth(c);
        }
        if (lineWidth > childMaxWidth) {
            childMaxWidth = lineWidth;
        }

        _minWidth += childMinWidth;
        _maxWidth += childMaxWidth;
    }

    public int getMaxWidth() {
        return _maxWidth;
    }

    protected void setMaxWidth(final int maxWidth) {
        _maxWidth = maxWidth;
    }

    public int getMinWidth() {
        return _minWidth;
    }

    protected void setMinWidth(final int minWidth) {
        _minWidth = minWidth;
    }

    public void styleText(final LayoutContext c) {
        styleText(c, getStyle());
    }

    // FIXME Should be expanded into generic restyle facility
    public void styleText(final LayoutContext c, final CalculatedStyle style) {
        if (getChildrenContentType() == CONTENT_INLINE) {
            final LinkedList<CalculatedStyle> styles = new LinkedList<CalculatedStyle>();
            styles.add(style);
            for (final Iterator<Styleable> i = _inlineContent.iterator(); i.hasNext();) {
                final Styleable child = (Styleable) i.next();
                if (child instanceof InlineBox) {
                    final InlineBox iB = (InlineBox) child;

                    if (iB.isStartsHere()) {
                        CascadedStyle cs = null;
                        if (iB.getElement() != null) {
                            if (iB.getPseudoElementOrClass() == null) {
                                cs = c.getCss().getCascadedStyle(c.getSharedContext().getBaseURL(), iB.getElement(), false);
                            } else {
                                cs = c.getCss().getPseudoElementStyle(
                                        iB.getElement(), iB.getPseudoElementOrClass());
                            }
                            styles.add(styles.getLast().deriveStyle(cs));
                        } else {
                            styles.add(style.createAnonymousStyle(IdentValue.INLINE));
                        }
                    }

                    iB.setStyle(styles.getLast());
                    iB.applyTextTransform();

                    if (iB.isEndsHere()) {
                        styles.removeLast();
                    }
                }
            }
        }
    }

    protected void calcChildPaintingInfo(
            final CssContext c, final PaintingInfo result, final boolean useCache) {
        if (getPersistentBFC() != null) {
            (this).getPersistentBFC().getFloatManager().performFloatOperation(
                    new FloatManager.FloatOperation() {
                        public void operate(final Box floater) {
                            final PaintingInfo info = floater.calcPaintingInfo(c, useCache);
                            moveIfGreater(
                                    result.getOuterMarginCorner(),
                                    info.getOuterMarginCorner());
                        }
                    });
        }
        super.calcChildPaintingInfo(c, result, useCache);
    }

    public CascadedStyle getFirstLetterStyle() {
        return _firstLetterStyle;
    }

    public void setFirstLetterStyle(final CascadedStyle firstLetterStyle) {
        _firstLetterStyle = firstLetterStyle;
    }

    public CascadedStyle getFirstLineStyle() {
        return _firstLineStyle;
    }

    public void setFirstLineStyle(final CascadedStyle firstLineStyle) {
        _firstLineStyle = firstLineStyle;
    }

    protected boolean isMinMaxCalculated() {
        return _minMaxCalculated;
    }

    protected void setMinMaxCalculated(final boolean minMaxCalculated) {
        _minMaxCalculated = minMaxCalculated;
    }

    protected void setDimensionsCalculated(final boolean dimensionsCalculated) {
        _dimensionsCalculated = dimensionsCalculated;
    }

    private boolean isDimensionsCalculated() {
        return _dimensionsCalculated;
    }

    protected void setNeedShrinkToFitCalculatation(final boolean needShrinkToFitCalculatation) {
        _needShrinkToFitCalculatation = needShrinkToFitCalculatation;
    }

    private boolean isNeedShrinkToFitCalculatation() {
        return _needShrinkToFitCalculatation;
    }

    public void initStaticPos(final LayoutContext c, final BlockBox parent, final int childOffset) {
        setX(0);
        setY(childOffset);
    }

    public int calcBaseline(final LayoutContext c) {
        for (int i = 0; i < getChildCount(); i++) {
            final Box b = getChild(i);
            if (b instanceof LineBox) {
                return b.getAbsY() + ((LineBox) b).getBaseline();
            } else {
                if (b instanceof TableRowBox) {
                    return b.getAbsY() + ((TableRowBox) b).getBaseline();
                } else {
                    final int result = ((BlockBox) b).calcBaseline(c);
                    if (result != NO_BASELINE) {
                        return result;
                    }
                }
            }
        }

        return NO_BASELINE;
    }

    protected int calcInitialBreakAtLine(final LayoutContext c) {
        final BreakAtLineContext bContext = c.getBreakAtLineContext();
        if (bContext != null && bContext.getBlock() == this) {
            return bContext.getLine();
        }
        return 0;
    }

    public boolean isCurrentBreakAtLineContext(final LayoutContext c) {
        final BreakAtLineContext bContext = c.getBreakAtLineContext();
        return bContext != null && bContext.getBlock() == this;
    }

    public BreakAtLineContext calcBreakAtLineContext(final LayoutContext c) {
        if (! c.isPrint() || ! getStyle().isKeepWithInline()) {
            return null;
        }

        final LineBox breakLine = findLastNthLineBox((int)getStyle().asFloat(CSSName.WIDOWS));
        if (breakLine != null) {
            final PageBox linePage = c.getRootLayer().getLastPage(c, breakLine);
            final PageBox ourPage = c.getRootLayer().getLastPage(c, this);
            if (linePage != null && ourPage != null && linePage.getPageNo() + 1 == ourPage.getPageNo()) {
                final BlockBox breakBox = (BlockBox)breakLine.getParent();
                return new BreakAtLineContext(breakBox, breakBox.findOffset(breakLine));
            }
        }

        return null;
    }

    public int calcInlineBaseline(final CssContext c) {
        if (isReplaced() && getReplacedElement().hasBaseline()) {
            final Rectangle bounds = getContentAreaEdge(getAbsX(), getAbsY(), c);
            return bounds.y + getReplacedElement().getBaseline() - getAbsY();
        } else {
            final LineBox lastLine = findLastLineBox();
            if (lastLine == null) {
                return getHeight();
            } else {
                return lastLine.getAbsY() + lastLine.getBaseline() - getAbsY();
            }
        }
    }

    public int findOffset(final Box box) {
        final int ccount = getChildCount();
        for (int i = 0; i < ccount; i++) {
            if (getChild(i) == box) {
                return i;
            }
        }
        return -1;
    }

    public LineBox findLastNthLineBox(final int count) {
        final LastLineBoxContext context = new LastLineBoxContext(count);
        findLastLineBox(context);
        return context.line;
    }

    private static class LastLineBoxContext {
        public int current;
        public LineBox line;

        public LastLineBoxContext(final int i) {
            this.current = i;
        }
    }

    private void findLastLineBox(final LastLineBoxContext context) {
        final int type = getChildrenContentType();
        final int ccount = getChildCount();
        if (ccount > 0) {
            if (type == CONTENT_INLINE) {
                for (int i = ccount - 1; i >= 0; i--) {
                    final LineBox child = (LineBox) getChild(i);
                    if (child.getHeight() > 0) {
                        context.line = child;
                        if (--context.current == 0) {
                            return;
                        }
                    }
                }
            } else if (type == CONTENT_BLOCK) {
                for (int i = ccount - 1; i >= 0; i--) {
                    ((BlockBox) getChild(i)).findLastLineBox(context);
                    if (context.current == 0) {
                        break;
                    }
                }
            }
        }
    }

    private LineBox findLastLineBox() {
        final int type = getChildrenContentType();
        final int ccount = getChildCount();
        if (ccount > 0) {
            if (type == CONTENT_INLINE) {
                for (int i = ccount - 1; i >= 0; i--) {
                    final LineBox result = (LineBox) getChild(i);
                    if (result.getHeight() > 0) {
                        return result;
                    }
                }
            } else if (type == CONTENT_BLOCK) {
                for (int i = ccount - 1; i >= 0; i--) {
                    final LineBox result = ((BlockBox) getChild(i)).findLastLineBox();
                    if (result != null) {
                        return result;
                    }
                }
            }
        }

        return null;
    }

    private LineBox findFirstLineBox() {
        final int type = getChildrenContentType();
        final int ccount = getChildCount();
        if (ccount > 0) {
            if (type == CONTENT_INLINE) {
                for (int i = 0; i < ccount; i++) {
                    final LineBox result = (LineBox) getChild(i);
                    if (result.getHeight() > 0) {
                        return result;
                    }
                }
            } else if (type == CONTENT_BLOCK) {
                for (int i = 0; i < ccount; i++) {
                    final LineBox result = ((BlockBox) getChild(i)).findFirstLineBox();
                    if (result != null) {
                        return result;
                    }
                }
            }
        }

        return null;
    }

    public boolean isNeedsKeepWithInline(final LayoutContext c) {
        if (c.isPrint() && getStyle().isKeepWithInline()) {
            final LineBox line = findFirstLineBox();
            if (line != null) {
                final PageBox linePage = c.getRootLayer().getFirstPage(c, line);
                final PageBox ourPage = c.getRootLayer().getFirstPage(c, this);
                return linePage != null && ourPage != null && linePage.getPageNo() == ourPage.getPageNo()+1;
            }
        }

        return false;
    }

    public boolean isFloated() {
        return _floatedBoxData != null;
    }

    public FloatedBoxData getFloatedBoxData() {
        return _floatedBoxData;
    }

    public void setFloatedBoxData(final FloatedBoxData floatedBoxData) {
        _floatedBoxData = floatedBoxData;
    }

    public int getChildrenHeight() {
        return _childrenHeight;
    }

    protected void setChildrenHeight(final int childrenHeight) {
        _childrenHeight = childrenHeight;
    }

    public boolean isFromCaptionedTable() {
        return _fromCaptionedTable;
    }

    public void setFromCaptionedTable(final boolean fromTable) {
        _fromCaptionedTable = fromTable;
    }

    @Override
    protected boolean isInlineBlock() {
        return isInline();
    }

    public boolean isInMainFlow() {
        Box flowRoot = this;
        while (flowRoot.getParent() != null) {
            flowRoot = flowRoot.getParent();
        }

        return flowRoot.isRoot();
    }

    @Override
    public Box getDocumentParent() {
        final Box staticEquivalent = getStaticEquivalent();
        if (staticEquivalent != null) {
            return staticEquivalent;
        } else {
            return getParent();
        }
    }

    public boolean isContainsInlineContent(final LayoutContext c) 
    {
        ensureChildren(c);

        switch (getChildrenContentType()) {
            case CONTENT_INLINE:
            {
            	return true;
            }
            case CONTENT_EMPTY:
            {
            	return false;
            }
            case CONTENT_BLOCK:
            {
            	for (Box child : getChildren())
            	{
            		BlockBox box = (BlockBox) child;
            		
            		if (box.isContainsInlineContent(c))
            			return true;
            	}

            	return false;
            }
        }
        
        assert(false);
        throw new RuntimeException("internal error: no children");
    }

    public boolean checkPageContext(final LayoutContext c) {
        if (! getStyle().isIdent(CSSName.PAGE, IdentValue.AUTO)) {
            final String pageName = getStyle().getStringProperty(CSSName.PAGE);
            if ( (! pageName.equals(c.getPageName())) && isInDocumentFlow() &&
                    isContainsInlineContent(c)) {
                c.setPendingPageName(pageName);
                return true;
            }
        } else if (c.getPageName() != null && isInDocumentFlow()) {
            c.setPendingPageName(null);
            return true;
        }

        return false;
    }

    public boolean isNeedsClipOnPaint(final RenderingContext c) {
        return ! isReplaced() &&
            getStyle().isIdent(CSSName.OVERFLOW, IdentValue.HIDDEN) &&
            getStyle().isOverflowApplies();
    }


    protected void propagateExtraSpace(
            final LayoutContext c,
            final ContentLimitContainer parentContainer, final ContentLimitContainer currentContainer,
            final int extraTop, final int extraBottom) {
        final int start = currentContainer.getInitialPageNo();
        final int end = currentContainer.getLastPageNo();
        int current = start;

        while (current <= end) {
            final ContentLimit contentLimit =
                currentContainer.getContentLimit(current);

            if (current != start) {
                final int top = contentLimit.getTop();
                if (top != ContentLimit.UNDEFINED) {
                    parentContainer.updateTop(c, top - extraTop);
                }
            }

            if (current != end) {
                final int bottom = contentLimit.getBottom();
                if (bottom != ContentLimit.UNDEFINED) {
                    parentContainer.updateBottom(c, bottom + extraBottom);
                }
            }

            current++;
        }
    }

    private static class MarginCollapseResult {
        private int maxPositive;
        private int maxNegative;

        public void update(final int value) {
            if (value < 0 && value < maxNegative) {
                maxNegative = value;
            }

            if (value > 0 && value > maxPositive) {
                maxPositive = value;
            }
        }

        public int getMargin() {
            return maxPositive + maxNegative;
        }

        public boolean hasMargin() {
            return maxPositive != 0 || maxNegative != 0;
        }
    }
}
