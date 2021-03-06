/*
 * {{{ header & license
 * LoggerUtil.java
 * Copyright (c) 2004, 2005 Patrick Wright
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
package com.github.neoflyingsaucer.util;

import java.util.logging.*;


/**
 * Utility class for working with java.logging Logger classes
 *
 * @author   Patrick Wright
 */
public class LoggerUtil {
    /**
     * Instantiate a Logger for debug messages for a given class.
     *
     * @param cls  PARAM
     * @return     The debugLogger value
     */
    public static Logger getDebugLogger( final Class<?> cls ) {
        final Logger l = Logger.getLogger( cls.getName() );
        l.setLevel( Level.ALL );
        return l;
    }

    public static Level parseLogLevel(final String val, final Level defaultLogLevel) {
        if ("ALL".equals(val)) {
            return Level.ALL;
        }
        if ("CONFIG".equals(val)) {
            return Level.CONFIG;
        }
        if ("FINE".equals(val)) {
            return Level.FINE;
        }
        if ("FINER".equals(val)) {
            return Level.FINER;
        }
        if ("FINEST".equals(val)) {
            return Level.FINEST;
        }
        if ("INFO".equals(val)) {
            return Level.INFO;
        }
        if ("OFF".equals(val)) {
            return Level.OFF;
        }
        if ("SEVERE".equals(val)) {
            return Level.SEVERE;
        }
        if ("WARNING".equals(val)) {
            return Level.WARNING;
        }
        return defaultLogLevel;
    }
}
