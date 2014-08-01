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

import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.directory.ldap.LDAPDirectory;
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

    ResilientDirectory resilientDir;

    LDAPDirectory ldapDir;

    Session ldapSession;

    SQLDirectoryProxy sqlDir;

    Session sqlSession;

    ResilientDirectorySession resDirSession;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        // platform dependencies
        // deployBundle("org.nuxeo.ecm.core.schema");
        // deployBundle("org.nuxeo.ecm.directory");

        // mem dir factory
        directoryService = Framework.getLocalService(DirectoryService.class);

        // Bundle to be tested
        deployBundle("org.nuxeo.ecm.directory.resilient");

        // Config for the tested bundle
//        deployContrib(TEST_BUNDLE, "sql-directories-config.xml");
        deployContrib(TEST_BUNDLE, "resilient-ldap-sql-directories-config.xml");

        // the resilient directory
        resilientDir = (ResilientDirectory) directoryService.getDirectory("resilient");
        resDirSession = (ResilientDirectorySession) resilientDir.getSession();

        ldapDir = getLDAPDirectory("ldapUserDirectory");
        ldapSession = ldapDir.getSession();

        sqlDir = (SQLDirectoryProxy) (directoryService.getDirectory("sqlDirectory"));
        sqlSession = sqlDir.getSession();

    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testCreateEntry() throws ClientException {
        if (USE_EXTERNAL_TEST_LDAP_SERVER) {
            HashMap<String, Object> e = new HashMap<String, Object>();
            e.put("username", "myUser");
            e.put("password", "secret");
            DocumentModel doc = resDirSession.createEntry(e);
            assertNotNull(doc);
            doc = sqlSession.getEntry("myUser");
            assertNotNull(doc);
        }
    }

    @Test
    public void testGetEntry() throws Exception {
        DocumentModel entry = resDirSession.getEntry("user1");
        assertNotNull(entry);

        Map<String, Object> propsLDAP = ldapSession.getEntry("user1").getProperties(
                "user");
        shutdownLdapServer();
        Map<String, Object> propsSQL = resDirSession.getEntry("user1").getProperties(
                "user");
        assertEquals(propsLDAP, propsSQL);

    }

    @Test
    public void testAuthenticate() throws Exception {
        //Not possible to authenticate against internal ldap server
        if (USE_EXTERNAL_TEST_LDAP_SERVER) {
            assertTrue(ldapSession.authenticate("user1", "secret"));
            assertTrue(resDirSession.authenticate("user1", "secret"));
        }

    }

    // Only for LDAP fallback test purpose
    protected void shutdownLdapServer() {
        if (!USE_EXTERNAL_TEST_LDAP_SERVER) {
            server.shutdownLdapServer();
        }
    }

}
