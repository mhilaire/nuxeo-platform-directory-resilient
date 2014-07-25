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
 * $Id: TestMultiDirectory.java 30378 2008-02-20 17:37:26Z gracinet $
 */

package org.nuxeo.ecm.directory.resilient.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.directory.memory.MemoryDirectory;
import org.nuxeo.ecm.directory.memory.MemoryDirectoryFactory;
import org.nuxeo.ecm.directory.resilient.ResilientDirectory;
import org.nuxeo.ecm.directory.resilient.ResilientDirectorySession;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.NXRuntimeTestCase;

/**
 * @author Florent Guillaume
 * @author Maxime Hilaire
 *
 */
public class TestResilientDirectory extends NXRuntimeTestCase {

    private static final String TEST_BUNDLE = "org.nuxeo.ecm.directory.resilient.tests";

    DirectoryService directoryService;

    MemoryDirectoryFactory memoryDirectoryFactory;

    MemoryDirectory memdir1;

    MemoryDirectory memdir2;

    ResilientDirectory multiDir;

    ResilientDirectorySession dir;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        // platform dependencies
        deployBundle("org.nuxeo.ecm.core.schema");
        deployBundle("org.nuxeo.ecm.directory");

        deployContrib(TEST_BUNDLE, "schemas-config.xml");

        // mem dir factory
        directoryService = Framework.getLocalService(DirectoryService.class);
        memoryDirectoryFactory = new MemoryDirectoryFactory();
        directoryService.registerDirectory("memdirs", memoryDirectoryFactory);

        // create and register mem directories
        Map<String, Object> e;

        // dir 1
        Set<String> schema1Set = new HashSet<String>(
                Arrays.asList("uid", "foo"));
        //Define here the in-memory directory as :
        //
        //<directory name="dir1">
        //  <schema>schema1</schema>
        //  <idField>uid</idField>
        //  <passwordField>foo</passwordField>
        //</directory>

        memdir1 = new MemoryDirectory("dir1", "schema1", schema1Set, "uid",
                "foo");
        memoryDirectoryFactory.registerDirectory(memdir1);

        Session dir1 = memdir1.getSession();
        e = new HashMap<String, Object>();
        e.put("uid", "1");
        e.put("foo", "foo1");
        e.put("bar", "bar1");
        dir1.createEntry(e);


        // dir 2
        memdir2 = new MemoryDirectory("dir2", "schema1", schema1Set, "uid",
                "foo");
        memoryDirectoryFactory.registerDirectory(memdir2);

        Session dir2 = memdir2.getSession();
        e = new HashMap<String, Object>();
        e.put("uid", "2");
        e.put("foo", "foo2");
        e.put("bar", "bar2");
        dir2.createEntry(e);

        // Bundle to be tested
        deployBundle("org.nuxeo.ecm.directory.resilient");

        // Config for the tested bundle
        deployContrib(TEST_BUNDLE, "directories-config.xml");


        // the multi directory
        multiDir = (ResilientDirectory) directoryService.getDirectory("resilient");
        dir = (ResilientDirectorySession) multiDir.getSession();


    }

    @Override
    @After
    public void tearDown() throws Exception {
        memoryDirectoryFactory.unregisterDirectory(memdir1);
        memoryDirectoryFactory.unregisterDirectory(memdir2);
        directoryService.unregisterDirectory("memdirs", memoryDirectoryFactory);
        super.tearDown();
    }

    @Test
    public void testCreateEntry() throws Exception {
        Session dir1 = memdir1.getSession();
        Session dir2 = memdir2.getSession();

        Map<String, Object> e;

        assertEquals("foo2", dir2.getEntry("2").getProperty("schema1", "foo"));

        e = new HashMap<String, Object>();
        e.put("uid", "2");
        e.put("foo", "foo3");
        e.put("bar", "bar3");
        DocumentModel doc =  dir.createEntry(e);



        assertFalse(dir1.getEntry("2") == null);
        assertFalse(dir2.getEntry("2") == null);
        assertEquals("bar3", doc.getProperty("schema1", "bar"));
        assertEquals("foo3", dir1.getEntry("2").getProperty("schema1", "foo"));
        assertEquals("foo3", dir2.getEntry("2").getProperty("schema1", "foo"));
    }

    @Test
    public void testGetEntry() throws Exception {
        DocumentModel entry;
        entry = dir.getEntry("1");
        assertEquals("1", entry.getProperty("schema1", "uid"));
        assertEquals("foo1", entry.getProperty("schema1", "foo"));
        entry = dir.getEntry("no-such-entry");
        assertNull(entry);
    }

    @Test
    public void testReplicateOnGetEntry() throws Exception {
        DocumentModel entry;
        Map<String, Object> e;

        Session dir2 = memdir2.getSession();
        entry = dir2.getEntry("1") ;
        assertNull(entry);

        entry = dir.getEntry("1");
        assertEquals("1", entry.getProperty("schema1", "uid"));

        entry = dir2.getEntry("1") ;
        assertFalse(entry == null);
    }

    @Test
    public void testDeleteOnGetEntry() throws Exception {
        DocumentModel entry;

        Session dir2 = memdir2.getSession();
        entry = dir2.getEntry("2") ;
        assertFalse(entry == null);

        entry = dir.getEntry("2");
        assertNull(entry);

        entry = dir2.getEntry("2") ;
        assertNull(entry);
    }

    @Test
    public void testGetEntries() throws Exception {
//        DocumentModelList l;
//        l = dir.getEntries();
//        assertEquals(3, l.size());
//        DocumentModel entry = null;
//        for (DocumentModel e : l) {
//            if (e.getId().equals("1")) {
//                entry = e;
//                break;
//            }
//        }
//        assertNotNull(entry);
//        assertEquals("foo1", entry.getProperty("schema1", "foo"));
    }


    @Test
    public void testAuthenticate() throws Exception {
        // sub dirs
        Session dir1 = memdir1.getSession();
        Session dir2 = memdir2.getSession();
        assertTrue(dir.authenticate("1", "foo1"));
        assertFalse(dir.authenticate("1", "haha"));
        assertFalse(dir.authenticate("2", "foo2"));

    }

    @Test
    public void testDeleteEntry() throws Exception {
        Session dir1 = memdir1.getSession();
        Session dir2 = memdir2.getSession();
        dir.deleteEntry("no-such-entry");
//        assertEquals(4, dir.getEntries().size());
//        assertEquals(2, dir1.getEntries().size());
//        assertEquals(2, dir2.getEntries().size());
        dir.deleteEntry("1");
        assertNull(dir.getEntry("1"));
        assertNull(dir1.getEntry("1"));
        assertNull(dir2.getEntry("1"));
//        assertEquals(3, dir.getEntries().size());
//        assertEquals(1, dir1.getEntries().size());
//        assertEquals(1, dir2.getEntries().size());
//        dir.deleteEntry("3");
//        assertNull(dir.getEntry("3"));
//        assertEquals(2, dir.getEntries().size());
//        assertEquals(1, dir1.getEntries().size());
//        assertEquals(1, dir2.getEntries().size());
    }

    @Test
    public void testHasEntry() throws Exception {
        assertTrue(dir.hasEntry("1"));
        assertFalse(dir.hasEntry("foo"));
    }




}
