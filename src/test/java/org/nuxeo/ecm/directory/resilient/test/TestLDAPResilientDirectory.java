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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.directory.ldap.LDAPDirectory;
import org.nuxeo.ecm.directory.ldap.LDAPDirectoryTestCase;
import org.nuxeo.ecm.directory.memory.MemoryDirectory;
import org.nuxeo.ecm.directory.memory.MemoryDirectoryFactory;
import org.nuxeo.ecm.directory.resilient.ResilientDirectory;
import org.nuxeo.ecm.directory.resilient.ResilientDirectorySession;
import org.nuxeo.ecm.directory.sql.SQLDirectoryProxy;
import org.nuxeo.runtime.api.Framework;

/**
 * @author Florent Guillaume
 * @author Maxime Hilaire
 *
 */
public class TestLDAPResilientDirectory extends LDAPDirectoryTestCase {

    private static final String TEST_BUNDLE = "org.nuxeo.ecm.directory.resilient.tests";

    DirectoryService directoryService;

    MemoryDirectoryFactory memoryDirectoryFactory;

    MemoryDirectory memdir1;

    MemoryDirectory memdir2;

    ResilientDirectory resilientDir;

    LDAPDirectory ldapDir;
    Session ldapSession ;

    SQLDirectoryProxy sqlDir;
    Session sqlSession ;

    ResilientDirectorySession resDirSession;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        // platform dependencies
        //deployBundle("org.nuxeo.ecm.core.schema");
        //deployBundle("org.nuxeo.ecm.directory");


        directoryService = Framework.getLocalService(DirectoryService.class);

        Map<String, Object> e;

        // Define the schema used for queries
        Set<String> schema1Set = new HashSet<String>(Arrays.asList("uid",
                "foo", "bar"));


        // Bundle to be tested
        deployBundle("org.nuxeo.ecm.directory.resilient");

        // Config for the tested bundle
        deployContrib(TEST_BUNDLE, "sql-directories-config.xml");
        deployContrib(TEST_BUNDLE, "resilient-ldap-sql-directories-config.xml");


        // the resilient directory
        resilientDir = (ResilientDirectory) directoryService.getDirectory("resilient");
        resDirSession = (ResilientDirectorySession) resilientDir.getSession();

        ldapDir = getLDAPDirectory("userDirectory");
        ldapSession = ldapDir.getSession();

        sqlDir = (SQLDirectoryProxy) ( directoryService.getDirectory("sqlDirectory"));
        sqlSession = sqlDir.getSession();

    }



    @Override
    @After
    public void tearDown() throws Exception {
        //memoryDirectoryFactory.unregisterDirectory(memdir1);
        //memoryDirectoryFactory.unregisterDirectory(memdir2);
        //directoryService.unregisterDirectory("memdirs", memoryDirectoryFactory);
        super.tearDown();
    }



    @Test
    public void testGetEntries() throws Exception {
        DocumentModelList l;
        l = resDirSession.getEntries();
        assertEquals(4, l.size());
        DocumentModel entry = null;
        for (DocumentModel e : l) {
            if (e.getId().equals("user1")) {
                entry = e;
                break;
            }
        }
        assertNotNull(entry);

        //Check why some props are null
        //assertEquals("uid=user1,ou=people,dc=example,dc=com", entry.getProperty("user", "dn"));
        Map<String, Object> propsLDAP = ldapSession.getEntry("user1").getProperties("user");
        shutdownLdapServer();
        l = resDirSession.getEntries();
        assertEquals(4, l.size());
        Map<String, Object> propsSQL = sqlSession.getEntry("user1").getProperties("user");
        assertEquals(propsLDAP, propsSQL);

    }

    @Test
    public void testAuthenticate() throws Exception {
        assertTrue(resDirSession.authenticate("user1", "user1"));
        shutdownLdapServer();
        assertTrue(resDirSession.authenticate("user1", "user1"));

    }



}
