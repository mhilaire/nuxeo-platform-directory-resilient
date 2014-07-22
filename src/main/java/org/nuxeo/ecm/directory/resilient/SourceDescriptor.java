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

import java.util.Arrays;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
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

    @XNode("@slaveOf")
    public String slaveOf ;

    @XNode("@creation")
    public boolean creation;

    @XNodeList(value = "subDirectory", type = SubDirectoryDescriptor[].class, componentType = SubDirectoryDescriptor.class)
    public SubDirectoryDescriptor[] subDirectories;

    @Override
    public String toString() {
        return String.format("{source name=%s subDirectories=%s", name,
                Arrays.toString(subDirectories));
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
        clone.slaveOf = slaveOf;
        if (subDirectories != null) {
            clone.subDirectories = new SubDirectoryDescriptor[subDirectories.length];
            for (int i = 0; i < subDirectories.length; i++) {
                clone.subDirectories[i] = subDirectories[i].clone();
            }
        }
        return clone;
    }

}
