/*
 * (C) Copyright 2006-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Florent Guillaume
 *
 * $Id: MultiDirectoryDescriptor.java 24597 2007-09-05 16:04:04Z fguillaume $
 */

package org.nuxeo.ecm.directory.resilient;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XObject;

/**
 * @author Florent Guillaume
 */
@XObject(value = "directory")
public class ResilientDirectoryDescriptor implements Cloneable {

    @XNode("@name")
    public String name;

    @XNode("querySizeLimit")
    public Integer querySizeLimit;

    @XNode("@remove")
    public boolean remove = false;

    @XNodeList(value = "source", type = SubDirectoryDescriptor[].class, componentType = SubDirectoryDescriptor.class)
    protected SubDirectoryDescriptor[] sources;

    public void merge(ResilientDirectoryDescriptor other) {
        merge(other, false);
    }

    public void merge(ResilientDirectoryDescriptor other, boolean overwrite) {

        if (other.querySizeLimit != null || overwrite) {
            querySizeLimit = other.querySizeLimit;
        }
        if (other.sources != null || overwrite) {
            if (sources == null) {
                sources = other.sources;
            } else {
                SubDirectoryDescriptor[] s = new SubDirectoryDescriptor[sources.length
                        + other.sources.length];
                System.arraycopy(sources, 0, s, 0, sources.length);
                System.arraycopy(other.sources, 0, s, sources.length,
                        other.sources.length);
                sources = s;
            }
        }
    }

    /**
     * @since 5.6
     */
    @Override
    public ResilientDirectoryDescriptor clone() {
        ResilientDirectoryDescriptor clone = new ResilientDirectoryDescriptor();
        clone.name = name;
        clone.querySizeLimit = querySizeLimit;
        clone.remove = remove;
        if (sources != null) {
            clone.sources = new SubDirectoryDescriptor[sources.length];
            for (int i = 0; i < sources.length; i++) {
                clone.sources[i] = sources[i].clone();
            }
        }
        return clone;
    }

}
