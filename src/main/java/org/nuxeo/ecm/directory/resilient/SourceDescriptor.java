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
 * $Id: SourceDescriptor.java 24597 2007-09-05 16:04:04Z fguillaume $
 */

package org.nuxeo.ecm.directory.resilient;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.directory.multi.SubDirectoryDescriptor;

/**
 * @author Florent Guillaume
 */
@XObject("source")
public class SourceDescriptor {

    @XNode("@name")
    public String name;

    @XNode("@master")
    public boolean master;

    @XNode("@creation")
    public boolean creation;

    @XNode(value = "subDirectory")
    public SubDirectoryDescriptor subDirectory;

    @Override
    public String toString() {
        return String.format("{source name=%s subDirectory=%s", name,
                subDirectory);
    }

    /**
     * @since 5.6
     */
    @Override
    public SourceDescriptor clone() {
        SourceDescriptor clone = new SourceDescriptor();
        clone.name = name;
        clone.creation = creation;
        clone.master = master;
        clone.subDirectory = subDirectory;

        return clone;
    }

}
