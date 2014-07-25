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
 * $Id: ResilientDirectorySession.java 29556 2008-01-23 00:59:39Z jcarsique $
 */

package org.nuxeo.ecm.directory.resilient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.directory.BaseSession;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.runtime.api.Framework;

/**
 * Directory session aggregating entries from different sources.
 * <p>
 * Each source can build an entry aggregating fields from one or several
 * directories.
 *
 * @author Florent Guillaume
 * @author Anahide Tchertchian
 * @author Maxime Hilaire
 */
public class ResilientDirectorySession extends BaseSession {

    private static final Log log = LogFactory.getLog(ResilientDirectorySession.class);

    private final DirectoryService directoryService;

    private final SchemaManager schemaManager;

    private final ResilientDirectory directory;

    private final ResilientDirectoryDescriptor descriptor;

    private final String schemaName;

    private final String schemaIdField;

    private final String schemaPasswordField;

    private SubDirectoryInfo masterSubDirectoryInfo;

    private List<SubDirectoryInfo> slaveSubDirectoryInfos;

    public ResilientDirectorySession(ResilientDirectory directory) {
        directoryService = ResilientDirectoryFactory.getDirectoryService();
        schemaManager = Framework.getLocalService(SchemaManager.class);
        this.directory = directory;
        descriptor = directory.getDescriptor();
        schemaName = directory.getSchema();
        schemaIdField = directory.getIdField();
        schemaPasswordField = directory.getPasswordField();
    }

    protected class SubDirectoryInfo {

        final String dirName;

        final String dirSchemaName;

        final String idField;

        final String passwordField;

        Session session;

        SubDirectoryInfo(String dirName, String dirSchemaName, String idField,
                String passwordField) {
            this.dirName = dirName;
            this.dirSchemaName = dirSchemaName;
            this.idField = idField;
            this.passwordField = passwordField;
        }

        Session getSession() throws DirectoryException {
            if (session == null) {
                session = directoryService.open(dirName);
            }
            return session;
        }

        @Override
        public String toString() {
            return String.format("{directory=%s }", dirName);
        }
    }

    private void init() throws DirectoryException {
        if (masterSubDirectoryInfo == null
                || (slaveSubDirectoryInfos == null || slaveSubDirectoryInfos.size() == 0)) {
            recomputeSubDirectoryInfos();
        }
    }

    /**
     * Recomputes all the info needed for efficient access.
     */
    private void recomputeSubDirectoryInfos() throws DirectoryException {

        List<SubDirectoryInfo> newSlaveSubDirectoryInfos = new ArrayList<SubDirectoryInfo>(
                2);
        for (SubDirectoryDescriptor subDir : descriptor.subDirectories) {

            final String dirName = subDir.name;
            final String dirSchemaName = directoryService.getDirectorySchema(dirName);

            final String dirIdField = directoryService.getDirectoryIdField(dirName);
            final String dirPwdField = directoryService.getDirectoryPasswordField(dirName);

            SubDirectoryInfo subDirectoryInfo = new SubDirectoryInfo(dirName,
                    dirSchemaName, dirIdField, dirPwdField);

            if (subDir.isMaster()) {
                if (masterSubDirectoryInfo == null) {
                    masterSubDirectoryInfo = subDirectoryInfo;
                }
            } else {
                newSlaveSubDirectoryInfos.add(subDirectoryInfo);
            }

        }

        slaveSubDirectoryInfos = newSlaveSubDirectoryInfos;
    }

    @Override
    public void close() throws DirectoryException {
        try {
            DirectoryException exc = null;
            if (masterSubDirectoryInfo != null) {
                exc = closeSource(masterSubDirectoryInfo, exc);
            }
            if (slaveSubDirectoryInfos == null) {
                return;
            }

            for (SubDirectoryInfo SubDirectoryInfo : slaveSubDirectoryInfos) {
                exc = closeSource(SubDirectoryInfo, exc);
            }
            if (exc != null) {
                throw exc;
            }

        } finally {
            directory.removeSession(this);
        }
    }

    /**
     * Close a source without throwing exception to let the parent caller deal
     * with the exception returned
     *
     * @param SubDirectoryInfo
     * @param exc
     * @return An exception or null if none
     *
     * @since 5.9
     */
    private DirectoryException closeSource(SubDirectoryInfo subDirectoryInfo,
            DirectoryException exc) {
        Session session = subDirectoryInfo.session;
        subDirectoryInfo.session = null;
        if (session != null) {
            try {
                session.close();
            } catch (DirectoryException e) {
                // remember exception, we want to close all session
                // first
                if (exc == null) {
                    exc = e;
                } else {
                    // we can't reraise both, log this one
                    log.error("Error closing directory "
                            + subDirectoryInfo.dirName, e);
                }
            }
        }
        return exc;

    }

    @Override
    public void commit() throws ClientException {
        if (masterSubDirectoryInfo != null) {
            Session session = masterSubDirectoryInfo.session;
            if (session != null) {
                session.commit();
            }

        }
        if (slaveSubDirectoryInfos == null) {
            return;
        }
        for (SubDirectoryInfo subDirectoryInfo : slaveSubDirectoryInfos) {
            Session session = subDirectoryInfo.session;
            if (session != null) {
                session.commit();
            }
        }
    }

    @Override
    public void rollback() throws ClientException {
        if (masterSubDirectoryInfo != null) {
            Session session = masterSubDirectoryInfo.session;
            if (session != null) {
                session.rollback();
            }
        }
        if (slaveSubDirectoryInfos == null) {
            return;
        }
        for (SubDirectoryInfo subDirectoryInfo : slaveSubDirectoryInfos) {
            Session session = subDirectoryInfo.session;
            if (session != null) {
                session.rollback();
            }
        }
    }

    @Override
    public String getIdField() throws DirectoryException {
        return schemaIdField;
    }

    @Override
    public String getPasswordField() {
        return schemaPasswordField;
    }

    @Override
    public boolean isAuthenticating() {
        return schemaPasswordField != null;
    }

    @Override
    /**
     * Get the read-only value
     *
     * @return The value of the read-only mode of the master directory
     * @throws DirectoryException
     * @throws ClientException
     *
     * @since TODO
     */
    public boolean isReadOnly() {
        // The aim of this resilient directory is to replicate at least one
        // master directory (read-only or not) to at least one slave
        // If the master directory is in read-only any entry may be created on
        // master, but slave will be replicated in any case
        // So return the value of the master directory, to warn the caller if
        // new entry will be created on master or not
        try {
            return masterSubDirectoryInfo.getSession().isReadOnly();
        } catch (ClientException e) {
            log.warn(
                    String.format(
                            "Unable to get the read-only value of the master directory '%s'",
                            masterSubDirectoryInfo.dirName), e);
            // If we are not able to know if the master is in read-only, do not
            // allow to add values into slaves
            return true;
        }
    }

    /**
     * The method try to create/update the entry if master has it, else delete
     * it from slave If any error, but log warn messages, the aim is not to lock
     * operation when slave are not availale The stuff will be done another time
     * The method works on entry ID (mean the idField is the same on master and
     * slave) and slave don't use auto-increment feature (checked in
     * ResilientDirectory constructor)
     *
     * @param entryId
     * @param masterHasEntry
     *
     * @since TODO
     */
    private void updateMasterOnSlaves(String entryId, boolean masterHasEntry) {
        // if master has entry, update entry on slave, else if it does not exist on slave create it
        // If the master does not have this entry anymore delete it from slave

        if (masterHasEntry) {
            DocumentModel docModel = null;
            try {
                docModel = masterSubDirectoryInfo.getSession().getEntry(entryId);

            } catch (ClientException e) {
                log.warn(String.format(
                        "Unable to get the entry id %s on master directory '%s'  while updating slave directory",
                        entryId, masterSubDirectoryInfo.dirName));
            }
            if (docModel != null) {
                for (SubDirectoryInfo subDirInfo : slaveSubDirectoryInfos) {
                    try {
                        if (subDirInfo.getSession().hasEntry(entryId)) {

                            subDirInfo.getSession().updateEntry(docModel);

                        }else
                        {
                            subDirInfo.getSession().createEntry(docModel);
                        }
                    }

                    catch (ClientException e) {
                        log.warn(String.format(
                                "Unable to update the slave directory %s on entry id %s",
                                subDirInfo.dirName, entryId));
                    }
                }
            } else {
                log.warn(String.format(
                        "The master directory %s should contains the entry id %s but return null when getting the object",
                        masterSubDirectoryInfo.dirName, entryId));
            }
        } else {
            for (SubDirectoryInfo subDirInfo : slaveSubDirectoryInfos) {
                try {
                    if (subDirInfo.getSession().hasEntry(entryId)) {
                        subDirInfo.getSession().deleteEntry(entryId);
                    }
                }

                catch (ClientException e) {
                    log.warn(String.format(
                            "Unable to delete the slave directory %s on entry id %s",
                            subDirInfo.dirName, entryId));
                }
            }
        }

    }



    private boolean hasEntryOnSlave(String id) throws ClientException {
        init();
        for (SubDirectoryInfo dirInfo : slaveSubDirectoryInfos) {
            Session session = dirInfo.getSession();
            if (session.hasEntry(id)) {
                return true;
            }
        }
        return false;
    }


    private List<DocumentModel> fetchOnSlave(String query) {
        return null;
    }

    private List<DocumentModel> fetchOnMaster(String query) {
        return null;
    }



    @Override
    public boolean authenticate(String username, String password)
            throws ClientException {
        init();
        // TODO : First check if the master contains the given user. If KO try
        // on slave but don't synchronise both

        // First try to authenticate against the master
        try {
            boolean authenticated = masterSubDirectoryInfo.getSession().authenticate(username,
                    password);
            updateMasterOnSlaves(username, authenticated);
        } catch (DirectoryException e) {
            log.warn(
                    String.format(
                            "Unable to authenticate the user '%s' against the master directory '%s'",
                            username, masterSubDirectoryInfo.dirName), e);
        }

        // If the master is KO, try to authenticate on slaves
        for (SubDirectoryInfo dirInfo : slaveSubDirectoryInfos) {
            if (dirInfo.getSession().authenticate(username, password)) {
                return true;
            }

        }
        return false;
    }

    @Override
    public DocumentModel getEntry(String id) throws DirectoryException {
        return getEntry(id, true);
    }

    @Override
    /**
     * Get entry on master directory first and fall back on slave(s) when needed
     *
     * @param id
     * @param fetchReferences
     * @return
     * @throws DirectoryException
     *
     * @since TODO
     */
    public DocumentModel getEntry(String id, boolean fetchReferences)
            throws DirectoryException {
        init();

        // Try to get the entry in the master first
        // If an exception occurs, catch it, log it and try to get it in the
        // slave

        boolean errorOccurs = false;
        DocumentModel entry = null;
        try {
            entry = masterSubDirectoryInfo.getSession().getEntry(id,
                    fetchReferences);
        } catch (DirectoryException e) {
            log.warn(String.format(
                    "Unable to get the entry id '%s' in the directory '%s' ",
                    id, masterSubDirectoryInfo.dirName), e);
            errorOccurs = true;
        }

        if (entry == null && !errorOccurs) {
            // If the entry is null and no error, remove the entry from
            // slaves
            updateMasterOnSlaves(id, false);
        } else if (entry == null && errorOccurs) {
            // Try to get the entry from slaves
            for (SubDirectoryInfo subDirectoryInfo : slaveSubDirectoryInfos) {
                entry = subDirectoryInfo.getSession().getEntry(id,
                        fetchReferences);
                if (isReadOnly()) {
                    // set readonly the returned entry if the master directory
                    // is in read-only
                    setReadOnlyEntry(entry);
                }
            }

        } else if (entry != null) {
            // Update the entry to the slaves if needed
            updateMasterOnSlaves(entry.getId(), true);
        }

        return entry;

    }

    @Override
    @SuppressWarnings("boxing")
    public DocumentModelList getEntries() throws ClientException {
        init();

        // list of entries
        final DocumentModelList results = new DocumentModelListImpl();
        // entry ids already seen (mapped to the source name)
        final Map<String, String> seen = new HashMap<String, String>();
        Set<String> readOnlyEntries = new HashSet<String>();
        //
        // for (SubDirectoryInfo SubDirectoryInfo : SubDirectoryInfos) {
        // // accumulated map for each entry
        // final Map<String, Map<String, Object>> maps = new HashMap<String,
        // Map<String, Object>>();
        // // number of dirs seen for each entry
        // final Map<String, Integer> counts = new HashMap<String, Integer>();
        // SubDirectoryInfo dirInfo = SubDirectoryInfo.subDirectoryInfo;
        // final DocumentModelList entries = dirInfo.getSession().getEntries();
        // for (DocumentModel entry : entries) {
        // final String id = entry.getId();
        // // find or create map for this entry
        // Map<String, Object> map = maps.get(id);
        // if (map == null) {
        // map = new HashMap<String, Object>();
        // maps.put(id, map);
        // counts.put(id, 1);
        // } else {
        // counts.put(id, counts.get(id) + 1);
        // }
        // // put entry data in map
        // for (Entry<String, String> e : dirInfo.toSource.entrySet()) {
        // map.put(e.getValue(),
        // entry.getProperty(dirInfo.dirSchemaName, e.getKey()));
        // }
        // if (BaseSession.isReadOnlyEntry(entry)) {
        // readOnlyEntries.add(id);
        // }
        // }
        //
        // // TODO : deal with the code below...
        // // now create entries for all full maps
        // int numdirs = 1;
        // ((ArrayList<?>) results).ensureCapacity(results.size()
        // + maps.size());
        // for (Entry<String, Map<String, Object>> e : maps.entrySet()) {
        // final String id = e.getKey();
        // if (seen.containsKey(id)) {
        // log.warn(String.format(
        // "Entry '%s' is present in source '%s' but also in source '%s'. "
        // + "The second one will be ignored.", id,
        // seen.get(id), SubDirectoryInfo.source.name));
        // continue;
        // }
        // final Map<String, Object> map = e.getValue();
        // if (counts.get(id) != numdirs) {
        // log.warn(String.format(
        // "Entry '%s' for source '%s' is not present in all directories. "
        // + "It will be skipped.", id,
        // SubDirectoryInfo.source.name));
        // continue;
        // }
        // seen.put(id, SubDirectoryInfo.source.name);
        // final DocumentModel entry = BaseSession.createEntryModel(null,
        // schemaName, id, map, readOnlyEntries.contains(id));
        // results.add(entry);
        // }
        // }
        return results;
    }

    @Override
    public DocumentModel createEntry(Map<String, Object> fieldMap)
            throws ClientException {
        init();

        if (isReadOnly()) {
            return null;
        }

        final Object rawid = fieldMap.get(schemaIdField);
        if (rawid == null) {
            throw new DirectoryException(String.format(
                    "Entry is missing id field '%s'", schemaIdField));
        }
        final String id = String.valueOf(rawid); // XXX allow longs too

        if (masterSubDirectoryInfo.getSession().hasEntry(id)) {
            // if master has the entry make sure slave get it too
            updateMasterOnSlaves(id, true);
        }

        return masterSubDirectoryInfo.getSession().createEntry(fieldMap);

    }

    @Override
    public void deleteEntry(DocumentModel docModel) throws ClientException {
        deleteEntry(docModel.getId());
    }

    @Override
    public void deleteEntry(String id) throws ClientException {
        init();
        // If we are removing a entry from the master, update the slave(s)
        // even if the master is in read-only mode
        masterSubDirectoryInfo.getSession().deleteEntry(id);
        updateMasterOnSlaves(id, false);
    }

    @Override
    public void deleteEntry(String id, Map<String, String> map)
            throws DirectoryException {
        log.warn("Calling deleteEntry extended on resilient directory");
        try {
            deleteEntry(id);
        } catch (DirectoryException e) {
            throw e;
        } catch (ClientException e) {
            throw new DirectoryException(e);
        }
    }

    private static void updateSubDirectoryEntry(SubDirectoryInfo dirInfo,
            Map<String, Object> fieldMap, String id, boolean canCreateIfOptional)
            throws ClientException {
        // DocumentModel dirEntry = dirInfo.getSession().getEntry(id);
        // if (dirInfo.getSession().isReadOnly()
        // || (dirEntry != null && isReadOnlyEntry(dirEntry))) {
        // return;
        // }
        // if (dirEntry == null && !canCreateIfOptional) {
        // // entry to update doesn't belong to this directory
        // return;
        // }
        // Map<String, Object> map = new HashMap<String, Object>();
        // map.put(dirInfo.idField, id);
        // for (Entry<String, String> e : dirInfo.fromSource.entrySet()) {
        // map.put(e.getValue(), fieldMap.get(e.getKey()));
        // }
        // if (map.size() > 1) {
        // if (canCreateIfOptional && dirEntry == null) {
        // // if entry does not exist, create it
        // dirInfo.getSession().createEntry(map);
        // } else {
        // final DocumentModel entry = BaseSession.createEntryModel(null,
        // dirInfo.dirSchemaName, id, null);
        // // Do not set dataModel values with constructor to force fields
        // // dirty
        // entry.getDataModel(dirInfo.dirSchemaName).setMap(map);
        // dirInfo.getSession().updateEntry(entry);
        // }
        // }
    }

    @Override
    public void updateEntry(DocumentModel docModel) throws ClientException {
        // if (isReadOnly() || isReadOnlyEntry(docModel)) {
        // return;
        // }
        // init();
        // final String id = docModel.getId();
        // Map<String, Object> fieldMap =
        // docModel.getDataModel(schemaName).getMap();
        // for (SubDirectoryInfo SubDirectoryInfo : SubDirectoryInfos) {
        // // check if entry exists in this source, in case it can be created
        // // in optional subdirectories
        // boolean canCreateIfOptional = false;
        // SubDirectoryInfo dirInfo = SubDirectoryInfo.subDirectoryInfo;
        // if (!canCreateIfOptional) {
        // canCreateIfOptional = dirInfo.getSession().getEntry(id) != null;
        // }
        // updateSubDirectoryEntry(dirInfo, fieldMap, id, false);
        //
        // }
    }

    @Override
    public DocumentModelList query(Map<String, Serializable> filter)
            throws ClientException {
        return query(filter, Collections.<String> emptySet());
    }

    @Override
    public DocumentModelList query(Map<String, Serializable> filter,
            Set<String> fulltext) throws ClientException {
        return query(filter, fulltext, Collections.<String, String> emptyMap());
    }

    @Override
    @SuppressWarnings("boxing")
    public DocumentModelList query(Map<String, Serializable> filter,
            Set<String> fulltext, Map<String, String> orderBy)
            throws ClientException {
        return query(filter, fulltext, orderBy, false);
    }

    @Override
    @SuppressWarnings("boxing")
    public DocumentModelList query(Map<String, Serializable> filter,
            Set<String> fulltext, Map<String, String> orderBy,
            boolean fetchReferences) throws ClientException {
        // init();
        // // list of entries
        // final DocumentModelList results = new DocumentModelListImpl();
        // // entry ids already seen (mapped to the source name)
        // final Map<String, String> seen = new HashMap<String, String>();
        // if (fulltext == null) {
        // fulltext = Collections.emptySet();
        // }
        // Set<String> readOnlyEntries = new HashSet<String>();
        //
        // for (SubDirectoryInfo SubDirectoryInfo : SubDirectoryInfos) {
        // // accumulated map for each entry
        // final Map<String, Map<String, Object>> maps = new HashMap<String,
        // Map<String, Object>>();
        // // number of dirs seen for each entry
        // final Map<String, Integer> counts = new HashMap<String, Integer>();
        //
        // // list of optional dirs where filter matches default values
        // List<SubDirectoryInfo> optionalDirsMatching = new
        // ArrayList<SubDirectoryInfo>();
        // for (SubDirectoryInfo dirInfo : SubDirectoryInfo.subDirectoryInfos) {
        // // compute filter
        // final Map<String, Serializable> dirFilter = new HashMap<String,
        // Serializable>();
        // for (Entry<String, Serializable> e : filter.entrySet()) {
        // final String fieldName = dirInfo.fromSource.get(e.getKey());
        // if (fieldName == null) {
        // continue;
        // }
        // dirFilter.put(fieldName, e.getValue());
        // }
        // if (dirInfo.isOptional) {
        // // check if filter matches directory default values
        // boolean matches = true;
        // for (Map.Entry<String, Serializable> dirFilterEntry :
        // dirFilter.entrySet()) {
        // Object defaultValue =
        // dirInfo.defaultEntry.get(dirFilterEntry.getKey());
        // Object filterValue = dirFilterEntry.getValue();
        // if (defaultValue == null && filterValue != null) {
        // matches = false;
        // } else if (defaultValue != null
        // && !defaultValue.equals(filterValue)) {
        // matches = false;
        // }
        // }
        // if (matches) {
        // optionalDirsMatching.add(dirInfo);
        // }
        // }
        // // compute fulltext
        // Set<String> dirFulltext = new HashSet<String>();
        // for (String sourceFieldName : fulltext) {
        // final String fieldName = dirInfo.fromSource.get(sourceFieldName);
        // if (fieldName != null) {
        // dirFulltext.add(fieldName);
        // }
        // }
        // // make query to subdirectory
        // DocumentModelList l = dirInfo.getSession().query(dirFilter,
        // dirFulltext, null, fetchReferences);
        // for (DocumentModel entry : l) {
        // final String id = entry.getId();
        // Map<String, Object> map = maps.get(id);
        // if (map == null) {
        // map = new HashMap<String, Object>();
        // maps.put(id, map);
        // counts.put(id, 1);
        // } else {
        // counts.put(id, counts.get(id) + 1);
        // }
        // for (Entry<String, String> e : dirInfo.toSource.entrySet()) {
        // map.put(e.getValue(),
        // entry.getProperty(dirInfo.dirSchemaName,
        // e.getKey()));
        // }
        // if (BaseSession.isReadOnlyEntry(entry)) {
        // readOnlyEntries.add(id);
        // }
        // }
        // }
        // // add default entry values for optional dirs
        // for (SubDirectoryInfo dirInfo : optionalDirsMatching) {
        // // add entry for every data found in other dirs
        // Set<String> existingIds = new HashSet<String>(
        // dirInfo.getSession().getProjection(
        // Collections.<String, Serializable> emptyMap(),
        // dirInfo.idField));
        // for (Entry<String, Map<String, Object>> result : maps.entrySet()) {
        // final String id = result.getKey();
        // if (!existingIds.contains(id)) {
        // counts.put(id, counts.get(id) + 1);
        // final Map<String, Object> map = result.getValue();
        // for (Entry<String, String> e : dirInfo.toSource.entrySet()) {
        // String value = e.getValue();
        // if (!map.containsKey(value)) {
        // map.put(value,
        // dirInfo.defaultEntry.get(e.getKey()));
        // }
        // }
        // }
        // }
        // }
        // // intersection, ignore entries not in all subdirectories
        // final int numdirs = SubDirectoryInfo.subDirectoryInfos.size();
        // for (Iterator<String> it = maps.keySet().iterator(); it.hasNext();) {
        // final String id = it.next();
        // if (counts.get(id) != numdirs) {
        // it.remove();
        // }
        // }
        // // now create entries
        // ((ArrayList<?>) results).ensureCapacity(results.size()
        // + maps.size());
        // for (Entry<String, Map<String, Object>> e : maps.entrySet()) {
        // final String id = e.getKey();
        // if (seen.containsKey(id)) {
        // log.warn(String.format(
        // "Entry '%s' is present in source '%s' but also in source '%s'. "
        // + "The second one will be ignored.", id,
        // seen.get(id), SubDirectoryInfo.source.name));
        // continue;
        // }
        // final Map<String, Object> map = e.getValue();
        // seen.put(id, SubDirectoryInfo.source.name);
        // final DocumentModel entry = BaseSession.createEntryModel(null,
        // schemaName, id, map, readOnlyEntries.contains(id));
        // results.add(entry);
        // }
        // }
        // if (orderBy != null && !orderBy.isEmpty()) {
        // directory.orderEntries(results, orderBy);
        // }
        // return results;
        return null;
    }

    @Override
    public List<String> getProjection(Map<String, Serializable> filter,
            String columnName) throws ClientException {
        return getProjection(filter, Collections.<String> emptySet(),
                columnName);
    }

    @Override
    public List<String> getProjection(Map<String, Serializable> filter,
            Set<String> fulltext, String columnName) throws ClientException {

        // There's no way to do an efficient getProjection to a source with
        // multiple subdirectories given the current API (we'd need an API that
        // passes several columns).
        // So just do a non-optimal implementation for now.

        final DocumentModelList entries = query(filter, fulltext);
        final List<String> results = new ArrayList<String>(entries.size());
        for (DocumentModel entry : entries) {
            final Object value = entry.getProperty(schemaName, columnName);
            if (value == null) {
                results.add(null);
            } else {
                results.add(value.toString());
            }
        }
        return results;
    }

    @Override
    public DocumentModel createEntry(DocumentModel entry)
            throws ClientException {
        Map<String, Object> fieldMap = entry.getProperties(schemaName);
        return createEntry(fieldMap);
    }

    @Override
    public boolean hasEntry(String id) throws ClientException {
        init();
        try {
            boolean masterHasEntry = masterSubDirectoryInfo.getSession().hasEntry(
                    id);
            updateMasterOnSlaves(id, masterHasEntry);
            return masterHasEntry;
        } catch (DirectoryException e) {
            log.warn(
                    String.format(
                            "Unable to check if master directory '%s' has entry id '%s', check on slaves ...",
                            masterSubDirectoryInfo.dirName, id), e);
            return hasEntryOnSlave(id);
        }
    }

}
